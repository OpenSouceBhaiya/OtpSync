package test

fun main() {
    val text1 = "G-583921 is your Google verification code."
    val text2 = "Microsoft account security code: 492091"
    val text3 = "729104 is your Microsoft account verification code"

    val triggerKeywords = listOf(
        "verification code", "one-time code", "one time password", "otp is", "otp", 
        "your code", "security code", "access code", "login code", "sign-in code", 
        "sign in code", "enter this code", "enter the code", "use this code", 
        "code to sign in", "confirmation code", "auth code", "authentication code", 
        "code is", "your pin", "passcode", "pin", "the code", "temporary password",
        "verify your email", "registration", "verify", "aotp", "secret code"
    )

    fun testExtractor(text: String) {
        val numberRegex = Regex("\\b([0-9]{4,8}|[a-zA-Z0-9]{4,8})\\b")
        val matches = numberRegex.findAll(text).toList()
        
        println("--- Testing: $text ---")
        for (match in matches) {
            val candidate = match.groupValues[1]
            println("Candidate found: $candidate")
        }
    }

    testExtractor(text1)
    testExtractor(text2)
    testExtractor(text3)
}
