package com.mailsync.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OtpExtractorTest {

    @Test
    fun testSameLineLabeledCode() {
        val email = "Your verification code is 482913. Do not share it."
        val result = OtpExtractor.extractOtp(null, email, null, 0L)
        assertEquals("482913", result?.code)
    }

    @Test
    fun testNextLineIsolatedCode() {
        val email = """
            Hello,
            
            Use this code to sign in:
            
            511570
            
            Thanks!
        """.trimIndent()
        val result = OtpExtractor.extractOtp(null, email, null, 0L)
        assertEquals("511570", result?.code)
    }

    @Test
    fun testSpacedDigits() {
        val email = "Your OTP is 511 570"
        val result = OtpExtractor.extractOtp(null, email, null, 0L)
        assertEquals("511570", result?.code)
    }

    @Test
    fun testDashedDigits() {
        val email = "Your OTP is 482-913"
        val result = OtpExtractor.extractOtp(null, email, null, 0L)
        assertEquals("482913", result?.code)
    }

    @Test
    fun testAlphanumericCode() {
        val email = "Your access code is A3F92K"
        val result = OtpExtractor.extractOtp(null, email, null, 0L)
        assertEquals("A3F92K", result?.code)
    }
    
    @Test
    fun testNetflixStyleUUIDFooter() {
        val email = """
            Enter this code to sign in
            
            4130
            
            ----
            SRC: 653956AC_22a3059a-5986-4458-a371-ef0c09e327ed_en-GB_IN_EVO
            Questions? Call 000-800-919-1743
        """.trimIndent()
        val result = OtpExtractor.extractOtp(null, email, null, 0L)
        assertEquals("4130", result?.code)
    }

    @Test
    fun testPhoneNumberDistraction() {
        val email = """
            Call us at 1-800-555-0199 if you need help.
            
            Your login code is 8841.
        """.trimIndent()
        val result = OtpExtractor.extractOtp(null, email, null, 0L)
        assertEquals("8841", result?.code)
    }

    @Test
    fun testNoLabelFallback() {
        val email = """
            Welcome to the app.
            
            654321
            
            Please use the code above.
        """.trimIndent()
        val result = OtpExtractor.extractOtp(null, email, null, 0L)
        assertEquals("654321", result?.code)
    }

    @Test
    fun testNoValidCodeReturnsNull() {
        val email = """
            Hello,
            Just checking in on your order.
            Thanks!
        """.trimIndent()
        val result = OtpExtractor.extractOtp(null, email, null, 0L)
        assertNull(result)
    }

    @Test
    fun testIgnoreMetadataInFooter() {
        val email = """
            Welcome to our service.
            
            ----
            Order ID: 1234567
            Ref: 98765432
        """.trimIndent()
        val result = OtpExtractor.extractOtp(null, email, null, 0L)
        assertNull(result)
    }

    @Test
    fun testRejectYear2026InMarketingNotification() {
        val text = """
            ✨ Introducing Amazon Seller Assistant on WhatsApp
            
            You can now manage your catalog, track customer queries, and boost your sales.
            To verify your seller profile, please click the link below:
            https://sellercentral.amazon.in/verify
            
            © 2026 Amazon.com, Inc. or its affiliates. All rights reserved.
        """.trimIndent()
        val result = OtpExtractor.extractOtp(null, text, null, 0L)
        assertNull(result)
    }

    @Test
    fun testBankSmsWithMaskedCardAndAmount() {
        val text = "Dear Customer, OTP for transaction of INR 1,500.00 on your SBI Card ending 4821 is 839201. Do not share OTP with anyone."
        val result = OtpExtractor.extractOtp(null, text, null, 0L)
        assertEquals("839201", result?.code)
    }

    @Test
    fun testBankSmsWithRsAndTxn() {
        val text = "Your OTP for Rs 450.00 txn at Zomato is 918234. Ref No: 991823. Valid for 10 mins."
        val result = OtpExtractor.extractOtp(null, text, null, 0L)
        assertEquals("918234", result?.code)
    }

    @Test
    fun testHdfcBankNetbankingOtp() {
        val text = "591823 is the OTP for login to your HDFC Bank NetBanking. Valid for 10 mins. Do not share with anyone."
        val result = OtpExtractor.extractOtp(null, text, null, 0L)
        assertEquals("591823", result?.code)
    }

    @Test
    fun testGoogleVerificationCodeWithPrefix() {
        val text = "G-948210 is your Google verification code."
        val result = OtpExtractor.extractOtp(null, text, null, 0L)
        assertEquals("948210", result?.code)
    }

    @Test
    fun testUberVerificationCode() {
        val text = "4829 is your Uber code. Never share this code."
        val result = OtpExtractor.extractOtp(null, text, null, 0L)
        assertEquals("4829", result?.code)
    }

    @Test
    fun testIndianPincodeIgnored() {
        val text = "Your delivery is arriving at Pincode 560001. Your delivery OTP is 773821."
        val result = OtpExtractor.extractOtp(null, text, null, 0L)
        assertEquals("773821", result?.code)
    }

    // ─── NEW ULTRA-ADVANCED & INTERNATIONAL TESTS ───────────────────────────────

    @Test
    fun testWebOtpStandardFormat() {
        val text = "Your verification code is 849201. @example.com #849201"
        val result = OtpExtractor.extractOtp(null, text, null, 0L)
        assertEquals("849201", result?.code)
    }

    @Test
    fun testEightDigitSecurityPasscode() {
        val text = "Your one-time security passcode is 84920183 for wire transfer. Valid 5 mins."
        val result = OtpExtractor.extractOtp(null, text, null, 0L)
        assertEquals("84920183", result?.code)
    }

    @Test
    fun testSpanishVerificationCode() {
        val text = "Su código de verificación para iniciar sesión es 492018. No lo comparta con nadie."
        val result = OtpExtractor.extractOtp(null, text, null, 0L)
        assertEquals("492018", result?.code)
    }

    @Test
    fun testFrenchSecurityCode() {
        val text = "Votre code de confirmation est le 739102. Valable 10 minutes."
        val result = OtpExtractor.extractOtp(null, text, null, 0L)
        assertEquals("739102", result?.code)
    }

    @Test
    fun testGermanConfirmationCode() {
        val text = "Ihr Bestätigungscode lautet 829104. Dieser Code ist 10 Minuten gültig."
        val result = OtpExtractor.extractOtp(null, text, null, 0L)
        assertEquals("829104", result?.code)
    }

    @Test
    fun testPortugueseVerificationCode() {
        val text = "Seu código de verificação é 618294. Nunca compartilhe este código."
        val result = OtpExtractor.extractOtp(null, text, null, 0L)
        assertEquals("618294", result?.code)
    }

    @Test
    fun testRussianVerificationCode() {
        val text = "Ваш проверочный код: 391820. Никому не сообщайте его."
        val result = OtpExtractor.extractOtp(null, text, null, 0L)
        assertEquals("391820", result?.code)
    }

    @Test
    fun testChineseVerificationCode() {
        val text = "【腾讯科技】您的动态验证码为 582910，请在5分钟内完成验证。"
        val result = OtpExtractor.extractOtp(null, text, null, 0L)
        assertEquals("582910", result?.code)
    }

    @Test
    fun testJapaneseVerificationCode() {
        val text = "認証コードは 928301 です。他人に教えないでください。"
        val result = OtpExtractor.extractOtp(null, text, null, 0L)
        assertEquals("928301", result?.code)
    }

    @Test
    fun testFullWidthDigitsJapanese() {
        val text = "認証コードは ９２８３０１ です。"
        val result = OtpExtractor.extractOtp(null, text, null, 0L)
        assertEquals("928301", result?.code)
    }

    @Test
    fun testArabicVerificationCode() {
        val text = "رمز التحقق الخاص بك هو 719283. لا تشارك هذا الرمز مع أي شخص."
        val result = OtpExtractor.extractOtp(null, text, null, 0L)
        assertEquals("719283", result?.code)
    }

    @Test
    fun testHindiSmsOtp() {
        val text = "प्रिय ग्राहक, आपका लॉगिन ओटीपी 839201 है। इसे किसी के साथ साझा न करें।"
        val result = OtpExtractor.extractOtp(null, text, null, 0L)
        assertEquals("839201", result?.code)
    }

    @Test
    fun testTurkishVerificationCode() {
        val text = "Doğrulama kodu: 829104. Bu kodu kimseyle paylaşmayınız."
        val result = OtpExtractor.extractOtp(null, text, null, 0L)
        assertEquals("829104", result?.code)
    }

    @Test
    fun testAlphanumericSteamGuard() {
        val text = "Here is your Steam Guard code: V8R2K"
        val result = OtpExtractor.extractOtp(null, text, null, 0L)
        assertEquals("V8R2K", result?.code)
    }

    @Test
    fun testAlphanumericEightCharsCode() {
        val text = "Your single-use security code is: 4A8K9M2P. Valid for 15 minutes."
        val result = OtpExtractor.extractOtp(null, text, null, 0L)
        assertEquals("4A8K9M2P", result?.code)
    }

    @Test
    fun testHyphenatedMultiPartCode() {
        val text = "Your confirmation code is 849-201-92. Enter it to verify."
        val result = OtpExtractor.extractOtp(null, text, null, 0L)
        assertEquals("84920192", result?.code)
    }

    @Test
    fun testRejectOrderNumberAndPriceInSpanish() {
        val text = "Su pedido #849201 por un importe de $150.00 ha sido confirmado. Gracias por su compra."
        val result = OtpExtractor.extractOtp(null, text, null, 0L)
        assertNull(result)
    }

    @Test
    fun testRejectPostalCodeAndPhone() {
        val text = "Please visit our store at Postal Code 90210 or call 1-800-555-0199 for assistance."
        val result = OtpExtractor.extractOtp(null, text, null, 0L)
        assertNull(result)
    }
}
