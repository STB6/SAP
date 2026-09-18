package com.stb6.sap.network

import com.stb6.sap.R
import com.stb6.sap.data.HotspotSettings
import com.stb6.sap.data.Settings
import com.stb6.sap.ui.UiText
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HotspotControllerTest {
    @Test fun offRefreshKeepsSwitchUncheckedUntilGroupConfirmed() = runTest {
        val fixture = Fixture(this)
        fixture.controller.refresh()
        runCurrent()
        assertEquals(Phase.Off, fixture.controller.state.value.phase)
        val answer = CompletableDeferred<P2pGroup?>()
        fixture.p2p.query = { answer.await() }
        fixture.controller.refresh()
        runCurrent()
        assertEquals(Phase.Off, fixture.controller.state.value.phase)
        assertFalse(fixture.controller.state.value.switchChecked)
        assertFalse(fixture.controller.state.value.locked)
        answer.complete(null)
        runCurrent()
        assertEquals(Phase.Off, fixture.controller.state.value.phase)
    }

    @Test fun editingDuringOffQueryDoesNotAdoptItsOldCandidate() = runTest {
        val fixture = Fixture(this)
        fixture.controller.refresh()
        runCurrent()
        val oldConfig = fixture.settings.current.value!!
        val answer = CompletableDeferred<P2pGroup?>()
        var first = true
        fixture.p2p.query = {
            if (first) { first = false; answer.await() } else fixture.p2p.current
        }
        fixture.controller.refresh()
        runCurrent()
        fixture.settings.current.value = oldConfig.copy(suffix = "edited")
        fixture.p2p.current = group(oldConfig)
        answer.complete(group(oldConfig))
        runCurrent()
        assertEquals(Phase.Off, fixture.controller.state.value.phase)
        assertFalse(fixture.controller.state.value.switchChecked)
        assertEquals(0, fixture.p2p.holds)
    }

    @Test fun editingWhileAddressLoadsDoesNotAdoptOldCredentials() = runTest {
        val fixture = Fixture(this)
        fixture.controller.refresh()
        runCurrent()
        val oldConfig = fixture.settings.current.value!!
        fixture.p2p.current = group(oldConfig)
        val address = CompletableDeferred<String?>()
        fixture.p2p.readAddress = { address.await() }
        fixture.controller.refresh()
        runCurrent()
        assertEquals(Phase.Off, fixture.controller.state.value.phase)
        fixture.settings.current.value = oldConfig.copy(password = "EditedPassword123")
        address.complete("192.0.2.1")
        runCurrent()
        assertEquals(Phase.Off, fixture.controller.state.value.phase)
        assertEquals(0, fixture.p2p.holds)
    }

    @Test fun failedOffQueryCannotSupplyConfigurationToNewStart() = runTest {
        val fixture = Fixture(this)
        fixture.controller.refresh()
        runCurrent()
        fixture.p2p.query = { throw IllegalStateException("Query failed") }
        fixture.controller.refresh()
        runCurrent()
        val edited = fixture.settings.current.value!!.copy(suffix = "after-failure", band = 5)
        fixture.settings.current.value = edited
        fixture.p2p.query = { fixture.p2p.current }
        launch { fixture.controller.start() }
        runCurrent()
        assertEquals(edited, fixture.p2p.created.single())
        assertEquals(Phase.On, fixture.controller.state.value.phase)
    }

    @Test fun startUsesCurrentInputAndDiscardsOldRecoveryResult() = runTest {
        val fixture = Fixture(this)
        val stale = fixture.settings.current.value!!
        val answer = CompletableDeferred<P2pGroup?>()
        var first = true
        fixture.p2p.query = {
            if (first) { first = false; answer.await() } else fixture.p2p.current
        }
        fixture.controller.refresh()
        runCurrent()
        val edited = stale.copy(suffix = "edited", password = "NewPassword123", band = 5)
        fixture.settings.current.value = edited
        val start = launch { fixture.controller.start() }
        runCurrent()
        assertTrue(start.isCompleted)
        assertEquals(edited, fixture.p2p.created.single())
        answer.complete(group(stale))
        runCurrent()
        assertEquals(Phase.On, fixture.controller.state.value.phase)
        assertEquals(edited.ssid, fixture.controller.state.value.ssid)
        assertEquals(0, fixture.p2p.removals)
    }

    @Test fun activeGroupOwnershipDoesNotFollowEditableSettings() = runTest {
        val fixture = Fixture(this)
        launch { fixture.controller.start() }
        runCurrent()
        fixture.settings.current.value = fixture.settings.current.value!!.copy(suffix = "different")
        launch { fixture.controller.stop() }
        runCurrent()
        assertEquals(1, fixture.p2p.removals)
        assertEquals(Phase.Off, fixture.controller.state.value.phase)
    }

    @Test fun stopCancelsLongObservationButLateOwnedGroupIsStillRemoved() = runTest {
        val fixture = Fixture(this)
        fixture.p2p.formImmediately = false
        val start = launch { fixture.controller.start() }
        advanceTimeBy(10_000)
        runCurrent()
        assertTrue(start.isCompleted)
        assertEquals(Phase.Unknown, fixture.controller.state.value.phase)
        val stop = launch { fixture.controller.stop() }
        runCurrent()
        assertTrue("Stop must not wait for the 125-second observation", stop.isCompleted)
        assertEquals(Phase.Unknown, fixture.controller.state.value.phase)
        val queries = fixture.p2p.queries
        advanceTimeBy(10_000)
        runCurrent()
        assertEquals("Cancelled observation must not keep polling", queries, fixture.p2p.queries)
        fixture.p2p.current = group(fixture.settings.current.value!!)
        fixture.p2p.changed()
        runCurrent()
        assertEquals(1, fixture.p2p.removals)
        assertEquals(Phase.Off, fixture.controller.state.value.phase)
    }

    @Test fun emptyQueriesDoNotProveTimedOutCreationStopped() = runTest {
        val fixture = Fixture(this)
        fixture.p2p.formImmediately = false
        launch { fixture.controller.start() }
        advanceTimeBy(135_000)
        runCurrent()
        assertEquals(Phase.Unknown, fixture.controller.state.value.phase)
        assertTrue(fixture.errors.any { it.id == R.string.stop_failed })
        assertEquals(0, fixture.p2p.removals)
        advanceTimeBy(120_000)
        runCurrent()
        val queries = fixture.p2p.queries
        advanceTimeBy(300_000)
        runCurrent()
        assertEquals("Rechecks must be bounded", queries, fixture.p2p.queries)
    }

    @Test fun lateForeignGroupIsNeverRemoved() = runTest {
        val fixture = Fixture(this)
        fixture.p2p.formImmediately = false
        launch { fixture.controller.start() }
        advanceTimeBy(10_000)
        runCurrent()
        fixture.p2p.current = group(fixture.settings.current.value!!.copy(suffix = "foreign"))
        advanceTimeBy(5_000)
        runCurrent()
        assertEquals(Phase.Off, fixture.controller.state.value.phase)
        assertEquals(0, fixture.p2p.removals)
    }

    @Test fun oldActionResultCannotAffectNewGeneration() = runTest {
        val fixture = Fixture(this)
        fixture.p2p.formImmediately = false
        launch { fixture.controller.start() }
        advanceTimeBy(10_000)
        runCurrent()
        val oldResult = fixture.p2p.lastResult!!
        fixture.p2p.disabled()
        fixture.p2p.formImmediately = true
        fixture.settings.current.value = fixture.settings.current.value!!.copy(suffix = "new")
        launch { fixture.controller.start() }
        runCurrent()
        val queries = fixture.p2p.queries
        oldResult(false)
        runCurrent()
        assertEquals(Phase.On, fixture.controller.state.value.phase)
        assertEquals(fixture.settings.current.value!!.ssid, fixture.controller.state.value.ssid)
        assertEquals(queries, fixture.p2p.queries)
    }

    @Test fun cancelledObservationCannotRemoveNewGroupWhenOldQueryReturnsLate() = runTest {
        val fixture = Fixture(this)
        fixture.p2p.formImmediately = false
        launch { fixture.controller.start() }
        runCurrent()
        val oldConfig = fixture.settings.current.value!!
        val oldQuery = CompletableDeferred<P2pGroup?>()
        var observing = true
        fixture.p2p.query = {
            if (observing) {
                observing = false
                withContext(NonCancellable) { oldQuery.await() }
            } else fixture.p2p.current
        }
        advanceTimeBy(10_000)
        runCurrent()
        fixture.p2p.inactive = true
        launch { fixture.controller.stop() }
        runCurrent()
        assertEquals(Phase.Off, fixture.controller.state.value.phase)
        fixture.p2p.formImmediately = true
        fixture.settings.current.value = oldConfig.copy(suffix = "new-group")
        launch { fixture.controller.start() }
        runCurrent()
        oldQuery.complete(group(oldConfig))
        runCurrent()
        assertEquals(Phase.On, fixture.controller.state.value.phase)
        assertEquals(fixture.settings.current.value!!.ssid, fixture.controller.state.value.ssid)
        assertEquals(0, fixture.p2p.removals)
    }

    @Test fun repeatedStartReportsPendingOperationNotQueryFailure() = runTest {
        val fixture = Fixture(this)
        fixture.p2p.formImmediately = false
        launch { fixture.controller.start() }
        advanceTimeBy(10_000)
        runCurrent()
        launch { fixture.controller.start() }
        runCurrent()
        assertEquals(1, fixture.p2p.created.size)
        assertEquals(R.string.start_requires_stop, fixture.notices.last().id)
    }

    private class Fixture(scope: TestScope) {
        val settings = FakeSettings()
        val p2p = FakeConnection { scope.testScheduler.currentTime }
        val errors = mutableListOf<UiText>()
        val notices = mutableListOf<UiText>()
        val controller = HotspotController(p2p, scope.backgroundScope, settings, object : Feedback {
            override fun notify(text: UiText) { notices += text }
            override fun error(text: UiText, detail: String?) { errors += text }
        })
    }

    private class FakeSettings : HotspotSettings {
        override val current = MutableStateFlow<Settings?>(Settings("test", "Password123"))
        override suspend fun awaitLoaded() = current.value
    }

    private class FakeConnection(private val clock: () -> Long) : P2pConnection {
        override val supported = true
        override var revision = 0L
        var current: P2pGroup? = null
        var formImmediately = true
        var inactive = false
        var changed: () -> Unit = {}
        var disabled: () -> Unit = {}
        var query: suspend () -> P2pGroup? = { current }
        var readAddress: suspend () -> String? = { "192.0.2.1" }
        var holds = 0
        var queries = 0
        var removals = 0
        val created = mutableListOf<Settings>()
        var lastResult: ((Boolean) -> Unit)? = null
        override fun hasPermission() = true
        override fun wifiEnabled() = true
        override fun listen(changed: () -> Unit, disabled: () -> Unit, disconnected: () -> Unit) {
            this.changed = changed
            this.disabled = disabled
        }
        override suspend fun group(): P2pGroup? { ++queries; return query() }
        override suspend fun address() = readAddress()
        override suspend fun creationInactive() = inactive
        override suspend fun create(settings: Settings, result: (Boolean) -> Unit) {
            created += settings
            lastResult = result
            result(true)
            if (formImmediately) current = group(settings)
        }
        override suspend fun remove() { ++removals; current = null }
        override fun close() { ++revision }
        override fun holdGroup() { ++holds }
        override fun now() = clock()
        override fun trace(event: String) = Unit
    }

    private companion object {
        fun group(settings: Settings) = P2pGroup(true, HotspotState(phase = Phase.On,
            ssid = settings.ssid, password = settings.password, frequency = 2412, interfaceName = "p2p-test"))
    }
}
