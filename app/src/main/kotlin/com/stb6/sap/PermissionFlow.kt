package com.stb6.sap

internal enum class PermissionStep {
    Initial, RequestNearby, WaitNearby, NearbyGranted, NearbyDenied,
    CheckNotifications, WaitNotifications, NotificationResult, Start, Idle,
}

internal enum class PermissionAction {
    None, RequestNearby, RequestNotifications, RecordNearby, NearbyRequired, RecordNotifications, Start,
}

internal class PermissionFlow(
    var step: PermissionStep = PermissionStep.Initial,
    var startAfterPermissions: Boolean = false,
) {
    private var canRecover = step == PermissionStep.WaitNearby || step == PermissionStep.WaitNotifications

    fun leftForeground() {
        canRecover = step == PermissionStep.WaitNearby || step == PermissionStep.WaitNotifications
    }

    fun recoverWaiting(nearbyGranted: Boolean) {
        if (!canRecover) return
        when (step) {
            PermissionStep.WaitNearby -> nearbyResult(nearbyGranted)
            PermissionStep.WaitNotifications -> notificationResult()
            else -> Unit
        }
        canRecover = false
    }

    fun nearbyResult(granted: Boolean) {
        if (step == PermissionStep.WaitNearby) step = if (granted) PermissionStep.NearbyGranted else PermissionStep.NearbyDenied
    }

    fun notificationResult() {
        if (step == PermissionStep.WaitNotifications) step = PermissionStep.NotificationResult
    }

    fun requestStart(nearbyGranted: Boolean) {
        startAfterPermissions = true
        if (step != PermissionStep.WaitNearby && step != PermissionStep.WaitNotifications) {
            step = if (nearbyGranted) PermissionStep.CheckNotifications else PermissionStep.RequestNearby
        }
    }

    fun next(nearbyGranted: Boolean, nearbyAsked: Boolean, notificationsGranted: Boolean, notificationAsked: Boolean): PermissionAction {
        while (true) {
            when (step) {
                PermissionStep.Initial -> step = if (!nearbyAsked && !nearbyGranted) PermissionStep.RequestNearby else PermissionStep.CheckNotifications
                PermissionStep.RequestNearby -> {
                    canRecover = false
                    step = PermissionStep.WaitNearby
                    return PermissionAction.RequestNearby
                }
                PermissionStep.NearbyGranted, PermissionStep.NearbyDenied -> {
                    if (step == PermissionStep.NearbyDenied && startAfterPermissions) {
                        startAfterPermissions = false
                        step = PermissionStep.Idle
                        return PermissionAction.NearbyRequired
                    }
                    step = PermissionStep.CheckNotifications
                    return PermissionAction.RecordNearby
                }
                PermissionStep.CheckNotifications -> {
                    if (needsNotificationRequest(notificationsGranted, notificationAsked)) {
                        canRecover = false
                        step = PermissionStep.WaitNotifications
                        return PermissionAction.RequestNotifications
                    }
                    step = PermissionStep.Start
                }
                PermissionStep.NotificationResult -> {
                    step = PermissionStep.Start
                    return PermissionAction.RecordNotifications
                }
                PermissionStep.Start -> {
                    step = PermissionStep.Idle
                    if (startAfterPermissions) {
                        startAfterPermissions = false
                        return if (nearbyGranted) PermissionAction.Start else PermissionAction.NearbyRequired
                    }
                }
                PermissionStep.Idle, PermissionStep.WaitNearby, PermissionStep.WaitNotifications -> return PermissionAction.None
            }
        }
    }
}

internal fun needsNotificationRequest(granted: Boolean, asked: Boolean): Boolean = !granted && !asked
