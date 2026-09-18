package com.stb6.sap

import org.junit.Assert.*
import org.junit.Test

class PermissionFlowTest {
    @Test fun alreadyGrantedNotificationDoesNotRequireAnAskedRecord() {
        assertFalse(needsNotificationRequest(granted = true, asked = false))
        val flow = PermissionFlow()
        flow.requestStart(nearbyGranted = true)
        assertEquals(PermissionAction.Start, flow.next(true, true, true, false))
        assertEquals(PermissionAction.None, flow.next(true, true, true, false))
    }

    @Test fun unaskedNotificationWithoutGrantRequiresThePermissionFlow() {
        assertTrue(needsNotificationRequest(granted = false, asked = false))
        val flow = PermissionFlow()
        flow.requestStart(nearbyGranted = true)
        assertEquals(PermissionAction.RequestNotifications, flow.next(true, true, false, false))
        assertEquals(PermissionAction.None, flow.next(true, true, false, false))
    }

    @Test fun previouslyDeniedNotificationDoesNotBlockStartButNearbyIsStillRequired() {
        assertFalse(needsNotificationRequest(granted = false, asked = true))
        val flow = PermissionFlow()
        flow.requestStart(nearbyGranted = true)
        assertEquals(PermissionAction.Start, flow.next(true, true, false, true))
        flow.requestStart(nearbyGranted = false)
        assertEquals(PermissionAction.RequestNearby, flow.next(false, true, false, true))
    }

    @Test fun restoredNearbyWithoutResultUsesActualGrantAndContinuesOnce() {
        val flow = PermissionFlow(PermissionStep.WaitNearby, startAfterPermissions = true)
        flow.recoverWaiting(nearbyGranted = true)
        assertEquals(PermissionAction.RecordNearby, flow.next(true, false, true, false))
        assertEquals(PermissionAction.Start, flow.next(true, true, true, false))
        assertEquals(PermissionAction.None, flow.next(true, true, true, false))
        assertFalse(flow.startAfterPermissions)
    }

    @Test fun restoredNearbyDenialDoesNotRelaunchPermissionLoop() {
        val flow = PermissionFlow(PermissionStep.WaitNearby, startAfterPermissions = true)
        flow.recoverWaiting(nearbyGranted = false)
        assertEquals(PermissionAction.NearbyRequired, flow.next(false, false, false, false))
        repeat(3) {
            flow.leftForeground()
            flow.recoverWaiting(false)
            assertEquals(PermissionAction.None, flow.next(false, true, false, false))
        }
        flow.requestStart(false)
        assertEquals(PermissionAction.RequestNearby, flow.next(false, true, false, false))
    }

    @Test fun restoredNotificationWithoutResultDoesNotBlockHotspot() {
        val flow = PermissionFlow(PermissionStep.WaitNotifications, startAfterPermissions = true)
        flow.recoverWaiting(nearbyGranted = true)
        assertEquals(PermissionAction.RecordNotifications, flow.next(true, true, false, false))
        assertEquals(PermissionAction.Start, flow.next(true, true, false, true))
        assertEquals(PermissionAction.None, flow.next(true, true, false, true))
    }

    @Test fun newlyLaunchedDialogIsNotMistakenForLostResultBeforeLeavingForeground() {
        val flow = PermissionFlow()
        flow.requestStart(false)
        assertEquals(PermissionAction.RequestNearby, flow.next(false, false, false, false))
        flow.recoverWaiting(false)
        assertEquals(PermissionStep.WaitNearby, flow.step)
        flow.leftForeground()
        flow.recoverWaiting(true)
        assertEquals(PermissionAction.RecordNearby, flow.next(true, false, true, false))
        flow.nearbyResult(false)
        assertEquals(PermissionAction.Start, flow.next(true, true, true, false))
    }
}
