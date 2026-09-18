package com.stb6.sap.data

import org.junit.Assert.*
import org.junit.Test

class NetworkRulesTest {
    @Test fun ssidUsesBytesRatherThanCharacters() {
        assertTrue(NetworkRules.validSsid("DIRECT-ab-" + "中".repeat(7)))
        assertFalse(NetworkRules.validSsid("DIRECT-ab-" + "中".repeat(8)))
        assertTrue(NetworkRules.validSsid("DIRECT-ab-" + "a".repeat(22)))
        assertFalse(NetworkRules.validSsid("DIRECT-ab-" + "a".repeat(23)))
    }
    @Test fun passwordGenerationAndBoundaries() {
        repeat(40) {
            val value = NetworkRules.password()
            assertEquals(16, value.length)
            assertTrue(value.any(Char::isUpperCase))
            assertTrue(value.any(Char::isLowerCase))
            assertTrue(value.any(Char::isDigit))
            assertTrue(NetworkRules.validPassword(value))
        }
        assertFalse(NetworkRules.validPassword("1234567"))
        assertTrue(NetworkRules.validPassword(" !aZ1234"))
        assertFalse(NetworkRules.validPassword("中12345678"))
        assertFalse(NetworkRules.validPassword("a".repeat(64)))
    }
    @Test fun idleTimeoutResetsOnSettingsAndClientChanges() {
        val timer = IdleDeadline()
        assertNull(timer.update(100, true, 0, 0))
        assertEquals(300_100L, timer.update(100, true, 0, 5))
        assertEquals(300_100L, timer.update(1000, true, 0, 5))
        assertEquals(601_000L, timer.update(1000, true, 0, 10))
        assertNull(timer.update(2000, true, 1, 10))
        assertEquals(603_000L, timer.update(3000, true, 0, 10))
        assertNull(timer.update(4000, true, 0, 0))
        assertNull(timer.update(5000, false, 0, 5))
    }

    @Test fun failedShutdownRetainsShortRetryAcrossUncertainState() {
        val timer = IdleDeadline()
        assertEquals(60_000L, timer.update(0, true, 0, 1))
        timer.retryAfter(60_000, 30_000)
        assertEquals(90_000L, timer.update(60_100, true, 0, 1))
        assertEquals(90_000L, timer.update(65_000, true, 0, 1))
        assertEquals(90_000L, timer.update(90_000, true, 0, 1))
    }

    @Test fun clientArrivalOrTimeoutEditInvalidatesShutdownRetry() {
        val timer = IdleDeadline()
        timer.update(0, true, 0, 1)
        timer.retryAfter(60_000, 30_000)
        assertNull(timer.update(70_000, true, 1, 1))
        assertEquals(140_000L, timer.update(80_000, true, 0, 1))
        timer.retryAfter(140_000, 30_000)
        assertEquals(270_000L, timer.update(150_000, true, 0, 2))
        timer.retryAfter(270_000, 30_000)
        assertNull(timer.update(280_000, true, 0, 0))
        assertEquals(360_000L, timer.update(300_000, true, 0, 1))
        timer.retryAfter(360_000, 30_000)
        assertNull(timer.update(370_000, false, 0, 1))
        assertEquals(440_000L, timer.update(380_000, true, 0, 1))
    }
}
