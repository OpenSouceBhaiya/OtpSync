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

    // ─── JEE ADVANCED LEVEL ADVERSARIAL TEST CASES (THE HARDEST TEST SUITE) ──────

    @Test
    fun testJeeAdvMultiDecoyDeliveryAWBAndOtp() {
        val text = "Order #984210 shipped via AWB 8492019482. Delivery boy will arrive at pincode 110001. Pay Rs 1,499. Share delivery OTP 395128 with delivery executive."
        val result = OtpExtractor.extractOtp(null, text, null, 0L)
        assertEquals("395128", result?.code)
    }

    @Test
    fun testJeeAdvBankingDebitDecoyAccountAndOtp() {
        val text = "INR 45,000 debited from A/c XX4892 on 11-10-2026 14:30:22. For netbanking transfer Ref #83910248 to Beneficiary 9283719, use OTP 604928. Valid 3 mins. Call 1800-222-333 if not requested."
        val result = OtpExtractor.extractOtp(null, text, null, 0L)
        assertEquals("604928", result?.code)
    }

    @Test
    fun testJeeAdvQuotedAndSpecialCharsAroundOtp() {
        val text = "Your one-time authentication code is: \"948201\"! Please do not disclose it."
        val result = OtpExtractor.extractOtp(null, text, null, 0L)
        assertEquals("948201", result?.code)
    }

    @Test
    fun testJeeAdvBracketedCode() {
        val text = "Your login verification code is [849201]. Valid for 5 minutes."
        val result = OtpExtractor.extractOtp(null, text, null, 0L)
        assertEquals("849201", result?.code)
    }

    @Test
    fun testJeeAdvNineDigitPasscode() {
        val text = "Use 739201849 as your one-time verification code to approve login."
        val result = OtpExtractor.extractOtp(null, text, null, 0L)
        assertEquals("739201849", result?.code)
    }

    @Test
    fun testJeeAdvTenDigitPasscode() {
        val text = "Your verification code is 9182736450 to authorize transaction of USD 250.00."
        val result = OtpExtractor.extractOtp(null, text, null, 0L)
        assertEquals("9182736450", result?.code)
    }

    @Test
    fun testJeeAdvFrenchMultiDecoyTransaction() {
        val text = "Votre commande #948201 d'un montant de 129,00 EUR a été validée. Pour autoriser le paiement de votre carte finissant par 4910, votre mot de passe temporaire à usage unique est le 482910. Réf transaction: TR938201."
        val result = OtpExtractor.extractOtp(null, text, null, 0L)
        assertEquals("482910", result?.code)
    }

    @Test
    fun testJeeAdvGermanInvertedSyntaxWithDecoys() {
        val text = "Bestellnummer 849201. Rechnungsbetrag 89,95 EUR für Konto DE893704829. Zur Freigabe lautet Ihr Einmalpasswort 604928. Rufen Sie niemals +49 800 111222 an."
        val result = OtpExtractor.extractOtp(null, text, null, 0L)
        assertEquals("604928", result?.code)
    }

    @Test
    fun testJeeAdvArabicMultiDecoyBanking() {
        val text = "تم خصم 500 ر.س من حسابك المنتهي برقم 4829. مرجع التحويل 928371. رمز التحقق لمرة واحدة هو 839104 لإتمام العملية. لا تشارك الرمز."
        val result = OtpExtractor.extractOtp(null, text, null, 0L)
        assertEquals("839104", result?.code)
    }

    @Test
    fun testJeeAdvRussianBankCardOperation() {
        val text = "Покупка 4500 руб. Карта *4829. Баланс 12500 руб. Для подтверждения операции на сайте Код: 582914. Не сообщайте код никому!"
        val result = OtpExtractor.extractOtp(null, text, null, 0L)
        assertEquals("582914", result?.code)
    }

    @Test
    fun testJeeAdvChineseOrderAndDynamicVerification() {
        val text = "【京东】您的订单号 98402910 已发货，快递单号 7739182049。本次登录动态验证码是 492018（10分钟有效），请勿向任何人泄露。"
        val result = OtpExtractor.extractOtp(null, text, null, 0L)
        assertEquals("492018", result?.code)
    }

    @Test
    fun testJeeAdvSpacedOutDigits() {
        val text = "Your verification code is 5 8 2 9 1 0. Enter this within 5 minutes."
        val result = OtpExtractor.extractOtp(null, text, null, 0L)
        assertEquals("582910", result?.code)
    }

    @Test
    fun testJeeAdvColonImmediatelyFollowedByDigits() {
        val text = "Your One-Time Passcode is:839201"
        val result = OtpExtractor.extractOtp(null, text, null, 0L)
        assertEquals("839201", result?.code)
    }

    @Test
    fun testJeeAdvItalianMultiDecoyBanking() {
        val text = "Gentile cliente, per confermare l'operazione di 150 EUR con carta terminata con 4829, il tuo codice di verifica è 918234. Riferimento operazione 839201."
        val result = OtpExtractor.extractOtp(null, text, null, 0L)
        assertEquals("918234", result?.code)
    }

    @Test
    fun testJeeAdvRejectMarketingTrackingWithNoOtp() {
        val text = "Flat 50% OFF! Use coupon code SALE50 on order 849201. Call 9876543210 for help. Track at example.com/t/928301"
        val result = OtpExtractor.extractOtp(null, text, null, 0L)
        assertNull(result)
    }

    @Test
    fun testJeeAdvRejectTransactionReceiptAlert() {
        val text = "Dear Customer, Txn of INR 2,500.00 on Card ending 4829 was successful at Store 849201 on 10-Oct-26. Avl Bal: INR 15,200.50. Call 1800112211 if not you."
        val result = OtpExtractor.extractOtp(null, text, null, 0L)
        assertNull(result)
    }

    @Test
    fun testJeeAdvRejectDeliveryUpdateWithoutOtp() {
        val text = "Your package with tracking number 948201948231 has been delivered to 123 Main St, Apt 4B. Thank you for shopping with us!"
        val result = OtpExtractor.extractOtp(null, text, null, 0L)
        assertNull(result)
    }

    @Test
    fun testJeeAdvRejectFlightBookingReference() {
        val text = "Booking Confirmed! PNR: W8K2P9, Flight 6E-849 on 15-Oct. Seat 12A. Gate 4. Total Paid: $340.00."
        val result = OtpExtractor.extractOtp(null, text, null, 0L)
        assertNull(result)
    }

    @Test
    fun testJeeAdvHindiComplexMultiDecoy() {
        val text = "खाता संख्या XX8492 से रु 5,000 का लेनदेन संदर्भ संख्या 928301 के लिए आपका लॉगिन ओटीपी 739104 है। वैधता 5 मिनट। सहायता के लिए 18002001 पर संपर्क करें।"
        val result = OtpExtractor.extractOtp(null, text, null, 0L)
        assertEquals("739104", result?.code)
    }

    @Test
    fun testJeeAdvZeroWidthSpacesAndNonBreakingSpaces() {
        val text = "Your\u200B verification\u00A0code\u200C is \u200D849201."
        val result = OtpExtractor.extractOtp(null, text, null, 0L)
        assertEquals("849201", result?.code)
    }

    @Test
    fun testSheinOtpNotificationFormat() {
        val text = "To log in into your SHEIN India account, use one time password 7918, valid for 10 minutes. Make sure you don't share it with anyone for security reasons."
        val result = OtpExtractor.extractOtp(null, text, null, 0L)
        assertEquals("7918", result?.code)
    }

    @Test
    fun testReversePhrasingBrands() {
        val swiggy = "592018 is your Swiggy OTP to log in to your account."
        assertEquals("592018", OtpExtractor.extractOtp(null, swiggy, null, 0L)?.code)

        val amazon = "829104 is your Amazon login code."
        assertEquals("829104", OtpExtractor.extractOtp(null, amazon, null, 0L)?.code)

        val netflix = "192847 is your Netflix verification code."
        assertEquals("192847", OtpExtractor.extractOtp(null, netflix, null, 0L)?.code)
    }

    @Test
    fun testSeparateSubjectAndBodyContext() {
        val subject = "Your verification code"
        val body = "Use 849201 to complete your sign in. Do not share this code with anyone."
        val result = OtpExtractor.extractOtp(null, subject, body, null, 0L)
        assertEquals("849201", result?.code)
    }

    @Test
    fun testGstAlphanumericOtpWithHelplineDecoy() {
        val subject = "One Time Password for EVC signature"
        val body = """
            Dear Sir/Madam,
            POONAM DEVI,

            This mail is in reference to the Application for Amendment of Core fields in Registration particulars (For all types of registered persons) in GST REG-14.
            Your One Time Password (OTP) for Electronic Verification of the Form is 862AAD. The same has also been sent to your registered mobile number.
            This password is valid for 10 minutes till . On expiry, kindly regenerate the password.

            Note: In case you have not generated this OTP, please contact us on 1800-103-4786.

            This is a system generated mail.
        """.trimIndent()
        val result = OtpExtractor.extractOtp(null, subject, body, null, 0L)
        assertEquals("862AAD", result?.code)
    }

    @Test
    fun testIncomeTaxEfilingWithAckNoAndHelpline() {
        val text = "Dear Taxpayer, Your OTP for e-Verification of ITR with Ack No 2910482019482 for AY 2026-27 is 948201. Do not share with anyone. Helpline: 1800-180-1961."
        val result = OtpExtractor.extractOtp(null, text, null, 0L)
        assertEquals("948201", result?.code)
    }

    @Test
    fun testEpfoUanPassbookOtp() {
        val text = "Dear Member, UAN: 100928301928, OTP for generating Member Passbook is 392018. Valid for 10 min. Customer Care: 1800-118-005."
        val result = OtpExtractor.extractOtp(null, text, null, 0L)
        assertEquals("392018", result?.code)
    }

    @Test
    fun testAadhaarUidaiAuthenticationOtp() {
        val text = "192830 is your OTP for Aadhaar (UIDAI: XXXX-XXXX-4820) authentication. Do not share it with anyone. Toll free no: 1947."
        val result = OtpExtractor.extractOtp(null, text, null, 0L)
        assertEquals("192830", result?.code)
    }

    @Test
    fun testIrctcBookingWithPnrAndTxnId() {
        val text = "OTP for IRCTC ticket booking Txn ID 82910482019, PNR: 2491029482 is 482910. Valid for 3 mins. Call 139 for railway enquiry."
        val result = OtpExtractor.extractOtp(null, text, null, 0L)
        assertEquals("482910", result?.code)
    }

    @Test
    fun testMicrosoftAlphanumericSecurityCodeWithTollFreeSupport() {
        val text = "Security info update: Your single-use code is 4A8K9M2P. If you did not request this code, reach us at 1-800-642-7676."
        val result = OtpExtractor.extractOtp(null, text, null, 0L)
        assertEquals("4A8K9M2P", result?.code)
    }

    @Test
    fun testCanaraBankCreditAlertRejectedAsNonOtp() {
        val text = """
            Dear Customer,
            Thanking you for banking with Canara Bank.
            An amount of INR 10,000.00 has been CREDITED on 28/10/26 to your account XXXX9328 from ANUP KUMAR with Mobile Banking Ref No.: 630112888096. Total Available Balance INR 12,396.50. If you are not expecting this credit or suspect any fraudulent activity, please contact: 18001030.
            This is an auto generated mail.
            With Regards,
            Canara Bank
            Toll Free No: 1800 1030.
        """.trimIndent()
        val result = OtpExtractor.extractOtp(null, "UPI/IMPS/MB Transaction Alert", text, 0L)
        assertNull("Completed Canara Bank credit alert must NOT extract an OTP", result)
    }

    @Test
    fun testSbiYonoFundTransferReceiptRejectedAsNonOtp() {
        val text = """
            Dear ANUP KUMAR
            
            Thank you for using YONO SBI for Fund Transfer
            
            The transaction details are as follows:
            
            Description Details
            Transaction Status Successful
            Amount Rs.10,000.00
            Transaction Number 630112888096
            Date of Transaction 28.10.26
            Debit account x5975
            Beneficiary Name ANUP KUMAR
            Beneficiary Account Number x9328
            
            Reach Us at:
            In App/Web feedback: Navigate to Landing Page > Hamburger Menu > Get Support
            Call Us: Toll free number 16001234 / 18002100 / 1800111101
            
            Safe Banking Tip: Never share your Internet Banking User ID, Password, MPIN, OTP, Card Number, CVV, Card PIN or URN with anyone, even if the caller claims to be a bank employee. Sharing these details can lead to unauthorized access to your account.
            
            Warm regards,
            YONO SBI
        """.trimIndent()
        val result = OtpExtractor.extractOtp(null, "Transaction success", text, 0L)
        assertNull("Completed SBI YONO fund transfer receipt must NOT extract an OTP", result)
    }

    @Test
    fun testSheinIndia() {
        val text = "To access/edit your SHEININDIA account use One Time Password 859878 (valid for 10 mins). Make sure you do not share it with anyone for security reasons. 281z5M+dN9a"
        val result = OtpExtractor.extractOtp(context = null, subject = "JD-SHEINI-S", bodyText = text, bodyHtml = null, receivedTimeMs = 0L)
        assertEquals("859878", result?.code)
    }

    @Test
    fun testSheinIndiaThreadedNotification() {
        val fullThreadText = """
            JD-SHEINI-S
            SH20057105340 is out for delivery. Pay Rs. 903.15 for the order. Track your shipment by logging into your SHEININDIA account.
            Hey Piyush! Part of your SHEININDIA order SH2199706778 has been successfully delivered. For details, kindly login to your SHEININDIA account.
            To access/edit your SHEININDIA account use One Time Password 859878 (valid for 10 mins). Make sure you do not share it with anyone for security reasons. 281z5M+dN9a
        """.trimIndent()
        val result = OtpExtractor.extractOtp(context = null, subject = "JD-SHEINI-S", bodyText = fullThreadText, bodyHtml = null, receivedTimeMs = 0L)
        assertEquals("859878", result?.code)
    }

    @Test
    fun testSheinIndiaSecondOtp() {
        val text = "To access/edit your SHEININDIA account use One Time Password 776690 (valid for 10 mins). Make sure you do not share it with anyone for security reasons. 281z5M+dN9a"
        val result = OtpExtractor.extractOtp(context = null, subject = "JD-SHEINI-S", bodyText = text, bodyHtml = null, receivedTimeMs = 0L)
        assertEquals("776690", result?.code)
    }
}

