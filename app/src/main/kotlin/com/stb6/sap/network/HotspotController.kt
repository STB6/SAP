package com.stb6.sap.network

import android.net.wifi.p2p.WifiP2pManager
import com.stb6.sap.BuildConfig
import com.stb6.sap.R
import com.stb6.sap.data.IdleDeadline
import com.stb6.sap.data.NetworkRules
import com.stb6.sap.data.Settings
import com.stb6.sap.data.HotspotSettings
import com.stb6.sap.ui.UiText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull

interface Feedback {
    fun notify(text: UiText)
    fun error(text: UiText, detail: String?)
}

// State is main-thread confined; the owning scope must use Main.immediate.
class HotspotController(
    private val p2p: P2pConnection,
    private val scope: CoroutineScope,
    private val settings: HotspotSettings,
    private val feedback: Feedback,
) {
    val supported = p2p.supported
    private val mutable = MutableStateFlow(HotspotState(
        phase = if (supported && hasPermission()) Phase.Checking else Phase.Off))
    val state = mutable.asStateFlow()

    private var activeSettings: Settings? = null
    private var pendingStart: StartAttempt? = null
    private val operation = Mutex()
    private var refreshing: Job? = null
    private var rechecking: Job? = null
    private var settling: Job? = null
    private var refreshRevision = 0L
    // Bumped whenever the group identity changes; results carrying an older value are dropped.
    private var generation = 0L
    private var idleTicket = 0L
    private var timer: Job? = null
    private val idle = IdleDeadline()

    init {
        p2p.listen(changed = { refresh() }, disabled = {
            if (state.value.switchChecked) feedback.notify(UiText(R.string.wifi_unavailable))
            setOff()
        }, disconnected = {
            ++generation
            settling?.cancel()
            if (state.value.locked) {
                mutable.value = mutable.value.copy(phase = if (state.value.phase == Phase.Checking) Phase.Checking else Phase.Unknown)
                feedback.notify(UiText(R.string.connection_lost))
                refresh()
            }
        })
        if (BuildConfig.DEBUG) scope.launch { state.collect { trace("state") } }
        scope.launch {
            combine(state, settings.current) { status, config -> status to config }.collect { (status, config) ->
                val deadline = idle.update(p2p.now(), status.locked && status.ssid.isNotEmpty(),
                    status.clients.size, config?.timeoutMinutes ?: 0)
                timer?.cancel()
                ++idleTicket
                if (deadline != null && status.phase == Phase.On) armIdleTimer(deadline - p2p.now())
            }
        }
    }

    fun hasPermission() = p2p.hasPermission()

    fun refresh(notifyFailure: Boolean = true) {
        if (!supported || !hasPermission() || settings.current.value == null) return
        ++refreshRevision
        if (refreshing?.isActive == true) return
        refreshing = scope.launch {
            do {
                val revision = refreshRevision
                val epoch = generation
                val candidate = activeSettings ?: settings.current.value ?: return@launch
                runP2p(notifyFailure) {
                    val group = p2p.group()
                    if (epoch == generation && revision == refreshRevision) {
                        if (group != null && owns(group, candidate)) {
                            if (pendingStart != null && state.value.phase == Phase.Unknown) {
                                val attempt = pendingStart
                                scope.launch {
                                    operation.withLock {
                                        if (epoch == generation && pendingStart === attempt) stopByUser()
                                    }
                                }
                            } else publish(group, candidate)
                        }
                        else if (state.value.phase != Phase.Starting && state.value.phase != Phase.Stopping) {
                            if (group == null && !startupSettled()) return@runP2p
                            if (epoch != generation || revision != refreshRevision) return@runP2p
                            val wasOn = state.value.ssid.isNotEmpty()
                            setOff()
                            if (wasOn) feedback.notify(UiText(R.string.group_stopped))
                        }
                    }
                }
            } while (revision != refreshRevision && hasPermission())
            if (state.value.phase == Phase.Unknown || state.value.phase == Phase.Checking) scheduleRecheck()
        }
    }

    suspend fun start() = operation.withLock {
        trace("start received")
        val config = settings.awaitLoaded() ?: return@withLock
        if (!state.value.canStart) {
            trace("start already managed")
            if (state.value.phase != Phase.On) feedback.notify(UiText(R.string.start_requires_stop))
            return@withLock
        }
        if (!NetworkRules.validSsid(config.ssid) || !NetworkRules.validPassword(config.password)) return@withLock
        if (!supported) { feedback.notify(UiText(R.string.unsupported)); return@withLock }
        if (!hasPermission()) { feedback.notify(UiText(R.string.nearby_required)); return@withLock }
        if (!p2p.wifiEnabled()) {
            feedback.notify(UiText(R.string.wifi_unavailable)); return@withLock
        }
        val epoch = ++generation
        activeSettings = config
        mutable.value = HotspotState(phase = Phase.Starting)
        var attempt: StartAttempt? = null
        try {
            val existing = p2p.group()
            if (existing != null) {
                if (owns(existing)) { publish(existing); return@withLock }
                feedback.notify(UiText(R.string.other_group)); setOff(); return@withLock
            }
            val requestAttempt = StartAttempt()
            attempt = requestAttempt
            trace("create requested")
            withTimeout(START_TIMEOUT_MS) {
                p2p.create(config) {
                    requestAttempt.accepted = it
                    trace("create accepted=$it")
                    if (pendingStart === requestAttempt && state.value.phase == Phase.Unknown) refresh(notifyFailure = false)
                }
                p2p.group()?.let { publish(it) }
                state.first { it.phase == Phase.On || it.phase == Phase.Off }
                check(state.value.phase == Phase.On) { "Group disappeared" }
            }
        } catch (e: TimeoutCancellationException) {
            if (epoch == generation) failed(e, attempt)
        } catch (e: CancellationException) {
            trace("start cancelled")
            throw e
        } catch (e: Exception) {
            if (epoch == generation) failed(e, attempt)
        }
    }

    private class StartAttempt(var accepted: Boolean? = null)
    private data class Observation(val group: P2pGroup?, val revision: Long)

    private fun failed(cause: Exception, attempt: StartAttempt?) {
        feedback.error(describe(cause), cause.toString())
        if (attempt == null) { setOff(); return }
        settling?.cancel()
        pendingStart = attempt
        val epoch = ++generation
        mutable.value = mutable.value.copy(phase = Phase.Unknown)
        settling = scope.launch {
            // AOSP allows 120 seconds for formation; observation never holds the operation mutex.
            val timedOut = cause is TimeoutCancellationException
            val remaining = withTimeoutOrNull(if (timedOut) START_SETTLE_MS else 2 * ACTION_TIMEOUT_MS) {
                while (epoch == generation && pendingStart === attempt) {
                    val revision = refreshRevision
                    val group = runP2p(notifyFailure = false) { p2p.group() }
                    currentCoroutineContext().ensureActive()
                    if (epoch != generation || pendingStart !== attempt) return@withTimeoutOrNull null
                    if (revision != refreshRevision) continue
                    if (group?.value != null) return@withTimeoutOrNull Observation(group.value, revision)
                    if (group != null && runP2p(notifyFailure = false) { startupSettled() }?.value == true) {
                        currentCoroutineContext().ensureActive()
                        if (revision != refreshRevision) continue
                        return@withTimeoutOrNull Observation(null, revision)
                    }
                    if (!timedOut) return@withTimeoutOrNull null
                    delay(START_RECHECK_MS)
                }
                null
            }
            operation.withLock {
                if (epoch != generation || pendingStart !== attempt) return@withLock
                settling = null
                if (remaining != null && remaining.revision != refreshRevision) {
                    refresh(notifyFailure = false)
                    return@withLock
                }
                when {
                    remaining == null -> {
                        feedback.error(UiText(R.string.stop_failed), "Unable to confirm group creation has terminated")
                        scheduleRecheck()
                    }
                    remaining.group?.let { owns(it) } == true -> stopByUser()
                    else -> setOff()
                }
            }
        }
    }

    private fun scheduleRecheck() {
        if (rechecking?.isActive == true) return
        rechecking = scope.launch {
            repeat(RECHECK_ATTEMPTS) {
                delay(IDLE_RETRY_MS)
                if (state.value.phase != Phase.Unknown && state.value.phase != Phase.Checking) return@launch
                refresh(notifyFailure = false)
                refreshing?.join()
            }
        }
    }

    suspend fun stop() = operation.withLock { stopByUser() }

    private suspend fun stopByUser() {
        if (!state.value.locked && (!hasPermission() || settings.current.value == null)) return
        if (activeSettings == null) activeSettings = settings.current.value ?: return
        markStopping()
        val epoch = generation
        try {
            when (removeGroupConfirmed()) {
                Removal.Removed, Removal.Absent -> Unit
                Removal.StillPresent -> feedback.error(UiText(R.string.stop_failed), "Group still present after removeGroup")
            }
        } catch (e: TimeoutCancellationException) {
            if (epoch != generation) return
            mutable.value = mutable.value.copy(phase = Phase.Unknown)
            feedback.error(UiText(R.string.stop_failed), e.toString())
            scheduleRecheck()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (epoch != generation) return
            mutable.value = mutable.value.copy(phase = Phase.Unknown)
            feedback.error(UiText(R.string.stop_failed), e.toString())
            scheduleRecheck()
        }
    }

    private suspend fun stopIfStillIdle(ticket: Long): Boolean {
        if (ticket != idleTicket || state.value.phase != Phase.On) return false
        try {
            val existing = p2p.group()
            if (ticket != idleTicket || state.value.phase != Phase.On) return false
            if (existing != null && existing.state.clients.isNotEmpty()) { publish(existing); return false }
            idle.retryAfter(p2p.now(), IDLE_RETRY_MS)
            markStopping()
            return removeGroupConfirmed(existing) == Removal.Removed
        } catch (e: CancellationException) {
            if (e !is TimeoutCancellationException) throw e
        } catch (_: Exception) {
        }
        if (state.value.phase == Phase.Stopping) {
            mutable.value = mutable.value.copy(phase = Phase.Unknown)
            scheduleRecheck()
        }
        else if (ticket == idleTicket) {
            idle.retryAfter(p2p.now(), IDLE_RETRY_MS)
            armIdleTimer(IDLE_RETRY_MS)
        }
        return false
    }

    private enum class Removal { Removed, Absent, StillPresent }

    private suspend fun removeGroupConfirmed(known: P2pGroup? = null): Removal {
        val existing = known ?: p2p.group() ?: run {
            check(startupSettled()) { "Group creation is still pending" }
            setOff()
            return Removal.Absent
        }
        if (!owns(existing)) {
            setOff()
            feedback.notify(UiText(R.string.other_group))
            return Removal.Absent
        }
        pendingStart = null
        p2p.remove()
        val after = p2p.group()
        if (after == null || !owns(after)) { setOff(); return Removal.Removed }
        mutable.value = mutable.value.copy(phase = Phase.On)
        publish(after)
        return Removal.StillPresent
    }

    private fun armIdleTimer(delayMs: Long) {
        val ticket = idleTicket
        timer = scope.launch {
            delay(delayMs.coerceAtLeast(0))
            // Shutdown changes state and cancels the timer; run it in a sibling job.
            scope.launch {
                operation.withLock {
                    if (stopIfStillIdle(ticket)) feedback.notify(UiText(R.string.auto_closed))
                }
            }
        }
    }

    private class Boxed<T>(val value: T)

    private suspend fun startupSettled(): Boolean {
        val attempt = pendingStart ?: return true
        return when (attempt.accepted) {
            false -> true
            true -> p2p.creationInactive()
            null -> false
        }
    }

    private suspend fun <T> runP2p(notifyFailure: Boolean = true, block: suspend () -> T): Boxed<T>? {
        return try {
            Boxed(block())
        } catch (e: TimeoutCancellationException) {
            if (notifyFailure && state.value.locked) feedback.notify(UiText(R.string.query_failed)); null
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            if (notifyFailure && state.value.locked) feedback.notify(UiText(R.string.query_failed)); null
        }
    }

    private fun owns(group: P2pGroup, config: Settings? = activeSettings): Boolean {
        if (config == null) return false
        return group.isGroupOwner && group.state.ssid == config.ssid && group.state.password == config.password
    }

    private suspend fun publish(group: P2pGroup, candidate: Settings? = activeSettings) {
        val config = activeSettings ?: candidate ?: return
        if (!owns(group, config)) return
        val sourceRevision = p2p.revision
        val epoch = generation
        val revision = refreshRevision
        val address = withTimeoutOrNull(ADDRESS_TIMEOUT_MS) { p2p.address() }
        if (p2p.revision != sourceRevision || epoch != generation || revision != refreshRevision) return
        if (activeSettings == null && !config.sameNetwork(settings.current.value)) {
            refresh(notifyFailure = false)
            return
        }
        val clients = group.state.clients
        val oldOrder = state.value.clients.mapIndexed { index, client -> client.address to index }.toMap()
        val ordered = clients.sortedWith(compareBy<Client> { oldOrder[it.address] ?: Int.MAX_VALUE }.thenBy { it.address })
        val previous = state.value.phase
        val adopt = previous == Phase.Off || previous == Phase.Checking || previous == Phase.Unknown
        activeSettings = config
        mutable.value = group.state.copy(phase = if (previous == Phase.Stopping) Phase.Stopping else Phase.On,
            hostAddress = address, clients = ordered)
        if (adopt) p2p.holdGroup()
    }

    private fun Settings.sameNetwork(other: Settings?) =
        other != null && suffix == other.suffix && password == other.password && band == other.band

    private fun markStopping() {
        settling?.cancel()
        settling = null
        ++generation
        mutable.value = mutable.value.copy(phase = Phase.Stopping)
    }

    private fun setOff() {
        trace("set off")
        ++generation
        ++idleTicket
        mutable.value = HotspotState()
        activeSettings = null
        pendingStart = null
        timer?.cancel()
        rechecking?.cancel()
        settling?.cancel()
        settling = null
        p2p.close()
    }

    private fun describe(error: Exception) = when (error) {
        is P2pFailure -> when (error.reason) {
            WifiP2pManager.BUSY -> UiText(R.string.start_busy)
            WifiP2pManager.P2P_UNSUPPORTED -> UiText(R.string.unsupported)
            WifiP2pManager.ERROR -> UiText(R.string.start_error)
            else -> UiText(R.string.start_failed_code, listOf(error.reason))
        }
        is TimeoutCancellationException -> UiText(R.string.start_timeout)
        else -> UiText(R.string.start_unknown)
    }

    private fun trace(event: String) {
        if (BuildConfig.DEBUG) p2p.trace("$event phase=${state.value.phase} generation=$generation " +
            "groupKnown=${state.value.ssid.isNotEmpty()} pendingStart=${pendingStart != null} refreshing=${refreshing?.isActive == true}")
    }

    private companion object {
        const val ACTION_TIMEOUT_MS = 4_000L
        const val ADDRESS_TIMEOUT_MS = 2_000L
        const val START_TIMEOUT_MS = 10_000L
        const val IDLE_RETRY_MS = 30_000L
        const val START_SETTLE_MS = 125_000L
        const val START_RECHECK_MS = 5_000L
        const val RECHECK_ATTEMPTS = 3
    }
}
