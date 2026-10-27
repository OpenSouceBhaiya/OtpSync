package com.mailsync.app.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class OtpCacheTest {

    @Before
    fun setUp() {
        OtpCache.clearForTesting()
    }

    @Test
    fun testFirstArrivalIsNew() {
        val status = OtpCache.checkStatus("123456", "Amazon", "SMS")
        assertEquals(OtpArrivalStatus.NEW, status)
    }

    @Test
    fun testSimultaneousBurstIsSuppressed() {
        val first = OtpCache.checkStatus("882190", "HDFC Bank", "SMS")
        assertEquals(OtpArrivalStatus.NEW, first)

        // Instant duplicate (< 2.5 seconds)
        val second = OtpCache.checkStatus("882190", "HDFC Bank", "Notification")
        assertEquals(OtpArrivalStatus.SIMULTANEOUS_BURST, second)
    }

    @Test
    fun testDifferentCodeIsNew() {
        val first = OtpCache.checkStatus("111222", "Google", "SMS")
        assertEquals(OtpArrivalStatus.NEW, first)

        val second = OtpCache.checkStatus("999888", "Google", "SMS")
        assertEquals(OtpArrivalStatus.NEW, second)
    }
}
