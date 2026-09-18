package com.stb6.sap.data

import java.security.SecureRandom

data class Settings(val suffix: String, val password: String, val band: Int = 2, val timeoutMinutes: Int = 0) {
    val ssid: String get() = SSID_PREFIX + suffix
    companion object {
        // Wi-Fi Direct requires "DIRECT-xy-"; the two characters are fixed for this app.
        const val SSID_PREFIX = "DIRECT-ap-"
    }
}

object NetworkRules {
    fun password(random: SecureRandom = SecureRandom()): String {
        val groups = listOf("ABCDEFGHIJKLMNOPQRSTUVWXYZ", "abcdefghijklmnopqrstuvwxyz", "0123456789")
        val alphabet = groups.joinToString("")
        val chars = groups.map { it[random.nextInt(it.length)] }.toMutableList()
        repeat(13) { chars.add(alphabet[random.nextInt(alphabet.length)]) }
        for (i in chars.lastIndex downTo 1) {
            val j = random.nextInt(i + 1)
            val old = chars[i]; chars[i] = chars[j]; chars[j] = old
        }
        return chars.joinToString("")
    }
    private val ssidShape = Regex("DIRECT-[A-Za-z0-9]{2}.*")
    fun validSsid(value: String) = value.toByteArray(Charsets.UTF_8).size <= 32 &&
        ssidShape.matches(value) && value.none { it.isISOControl() }
    fun validPassword(value: String) = value.length in 8..63 && value.all { it.code in 32..126 }

}

class IdleDeadline {
    private var emptySince: Long? = null
    private var minutes = 0
    private var retryAt: Long? = null
    fun update(now: Long, active: Boolean, clients: Int, timeout: Int): Long? {
        if (!active || clients > 0 || timeout == 0) {
            emptySince = null
            retryAt = null
        } else if (emptySince == null || timeout != minutes) {
            emptySince = now
            retryAt = null
        }
        minutes = timeout
        return emptySince?.let { retryAt ?: (it + timeout * 60_000L) }
    }

    fun retryAfter(now: Long, delay: Long) { retryAt = now + delay }
}
