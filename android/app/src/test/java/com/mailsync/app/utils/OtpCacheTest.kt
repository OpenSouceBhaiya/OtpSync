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

    @Test
    fun testSmsResendIsDetected() {
        val first = OtpCache.recordSmsArrival("445566", "Shein")
        assertEquals(OtpArrivalStatus.NEW, first)

        // Resent SMS with same code
        val second = OtpCache.recordSmsArrival("445566", "Shein")
        assertEquals(OtpArrivalStatus.RESENT_SAME_CODE, second)
    }

    @Test
    fun testSmsAppNotificationSuppression() {
        val first = OtpCache.recordSmsArrival("778899", "Amazon")
        assertEquals(OtpArrivalStatus.NEW, first)

        // Google Messages notification arriving for same SMS
        val second = OtpCache.checkNotificationArrival("778899", "Amazon", "com.google.android.apps.messaging", "SMS")
        assertEquals(OtpArrivalStatus.SAME_SMS_DUPLICATE, second)
    }

    @Test
    fun testDualChannelArrival() {
        val first = OtpCache.recordSmsArrival("334455", "Flipkart")
        assertEquals(OtpArrivalStatus.NEW, first)

        // Gmail notification arriving for same OTP
        val second = OtpCache.checkNotificationArrival("334455", "Flipkart", "com.google.android.gm", "Gmail (test@gmail.com)")
        assertEquals(OtpArrivalStatus.SIMULTANEOUS_BURST, second)
    }
}
