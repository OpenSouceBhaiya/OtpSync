package com.opensourcebhaiya.otpsync.utils

/**
 * Easter Egg utility containing 105+ hilarious developer/coder quotes, punchlines,
 * and witty jokes triggered when tapping the app version in Settings.
 */
object DeveloperEasterEgg {

    private val shuffledIndices = mutableListOf<Int>()
    private var currentIndex = 0

    @Synchronized
    fun getNextFunnyQuote(): String {
        if (shuffledIndices.isEmpty() || currentIndex >= shuffledIndices.size) {
            shuffledIndices.clear()
            shuffledIndices.addAll(funnyQuotes.indices.shuffled())
            currentIndex = 0
        }
        val quoteIndex = shuffledIndices[currentIndex]
        currentIndex++
        return funnyQuotes[quoteIndex]
    }

    val funnyQuotes = listOf(
        "Really bro? You wanna become a developer of this app? 🧑‍💻",
        "Developer mode unlocked! Your salary is $0.00 and bug tickets are infinite.",
        "Bro, even our lead developer is crying in production right now.",
        "It's not a bug, it's an undocumented feature.",
        "git commit -m 'minor fix' -> git push origin main --force -> 🏃💨",
        "You are now a Senior 10x Developer. You can now blame DNS for everything.",
        "Congratulations! You have unlocked: 200 unread Slack messages and 15 Jira tickets.",
        "There is no developer mode here. Only caffeine, sleepless nights, and git conflicts.",
        "404 Developer Not Found: Have you tried turning the developer off and on again?",
        "Warning: Entering developer mode requires sacrificing 8 hours of sleep.",
        "Developer Mode: Activated. First rule: It worked on my machine.",
        "Wait... you clicked 7 times just to see this? Respect the dedication, bro. 🫡",
        "Error 418: I'm a teapot, not a developer options screen. 🫖",
        "Congratulations! You are officially qualified to copy-paste from StackOverflow.",
        "Why do programmers prefer dark mode? Because light attracts bugs! 🪲",
        "To understand recursion, you must first click this version 7 more times.",
        "There are 10 types of people: those who understand binary, and those who tap this.",
        "99 little bugs in the code. Take one down, patch it around, 127 bugs in the code!",
        "Bro, our backend runs on hopes, dreams, and a single Docker container. 🐳",
        "You are 0 steps away from realizing there is no developer options menu. 😂",
        "Knock knock. Who's there? ...very long pause... Java garbage collection.",
        "A SQL query walks into a bar, sees two tables and asks: 'Can I join you?'",
        "Ship it on Friday at 5:00 PM. What could possibly go wrong?",
        "Debuggers don't sleep. They just wait on breakpoints.",
        "You are now a developer! Remember: A missing semicolon can ruin your entire weekend.",
        "Bro is really trying to inspect the source code through a TextView. 📱",
        "Achievement Unlocked: Master of Pointless Tapping! 🏆",
        "Congratulations! You have been assigned ticket OSB-404: 'Fix the entire universe'.",
        "Bro, if you want developer mode, go open Android Settings -> About Phone!",
        "Wait, did you think this would give you free unlimited OTPs? Nice try! 🔐",
        "Senior dev advice: If it ain't broke, don't touch it. If it is broke, blame legacy code.",
        "Our developers are currently arguing whether tabs or spaces are superior.",
        "Relax bro, no logs were harmed in the making of this easter egg.",
        "Developer mode active! Status: Compiling coffee into code... ☕",
        "Fun fact: 80% of debugging is explaining code to a rubber duck. 🦆",
        "You just unlocked God Mode... for exactly 0.5 seconds.",
        "Hold on, pinging the server... server says: 'Why are you tapping so fast?!'",
        "Real programmers count from 0, but you tapped 7 times. We forgive you.",
        "Keep clicking bro, maybe at 1,000 clicks you'll get a free t-shirt from Open Source Bhaiya!",
        "I told my computer I needed a break, and now it won't stop sending me KitKats.",
        "There's no place like 127.0.0.1 🏠",
        "A programmer's wife: 'Buy bread. If they have eggs, buy 12.' He returns with 12 loaves.",
        "Hardware: The part of the computer you can kick. Software: The part you can only swear at.",
        "Why did the developer go broke? Because they used up all their cache.",
        "Weeks of coding can save you hours of planning.",
        "Rule #1 of programming: If it compiles, ship it!",
        "Rule #2 of programming: If it fails in production, blame the intern.",
        "Rule #3 of programming: If you are the intern, blame the compiler.",
        "You tapped again! Your dedication to procrastination is truly inspiring. ✨",
        "Are you a keyboard? Because you're definitely my type.",
        "git status: Nothing to commit, working tree clean... or so you think.",
        "sudo make me a developer. [Password for user: *******]",
        "User is not in the sudoers file. This incident will be reported to Santa Claus.",
        "How many programmers does it take to change a bulb? None, that's a hardware problem.",
        "Walking on water and developing software from a spec are easy if both are frozen.",
        "Deleted 500 lines of code. App still runs. Best day ever.",
        "Bro, tapping faster won't make the next build compile quicker.",
        "Warning: High caffeine levels detected in this application's source code.",
        "Don't worry, the code has no bugs. It just develops unexpected random features.",
        "My code doesn't work, I don't know why. My code works, I don't know why.",
        "You are now certified in StackOverflow Engineering (BS-SOE).",
        "Optimism: The language is easy to learn. Reality: NullPointerException.",
        "Behind every successful app is an exhausted developer drinking Red Bull at 3 AM.",
        "Bro, you tapped again? Do you want a job at Open Source Bhaiya or what? 💼",
        "Developer Mode Level 2: You now see matrix green code in your dreams.",
        "Did you know? The first computer bug was an actual real moth in 1947.",
        "Please do not feed the developers after midnight.",
        "Code review comment: 'LGTM! (Didn't read a single line)' 👍",
        "Everything is an object... until you hit a null pointer.",
        "Bro, stop tapping, our analytics are getting confused!",
        "Feature request denied: 'User tapped too many times'.",
        "In case of fire: git commit, git push, git out of the building.",
        "CSS is easy: It's like styling a house where painting a door moves the roof to the neighbor.",
        "Developer unlocked! First task: Read 500 pages of legacy documentation.",
        "I would tell you a joke about UDP, but you might never get it.",
        "A TCP packet walks into a bar: 'I'd like a beer.' Bartender: 'You'd like a beer?' 'Yes.'",
        "The secret to clean code: Delete it before anyone else reviews it.",
        "Why do Java developers wear glasses? Because they don't C#.",
        "Bro, you have reached level 99 of curiosity. Here's a virtual cookie: 🍪",
        "Running test suite: 1,995 tests passed. 0 failed. Developer is sleeping peacefully tonight.",
        "Tap detected. AI has analyzed your tap and determined you are awesome.",
        "Hey! Stop poking me, I'm just a version label!",
        "If debugging is removing bugs, then programming is putting them in.",
        "My code works! ... Wait, why does it work?! *starts panicking*",
        "Developer perk unlocked: Unlimited free bugs for life!",
        "Error 200 OK: Everything went fine, which makes us extremely suspicious.",
        "You: *taps version 50 times*. App: *sweating profusely* 😰",
        "Congratulations! You are our favorite user of the day. Don't tell the others.",
        "Bro, go drink some water and stretch your fingers. You've been tapping a lot.",
        "If at first you don't succeed, call it version 1.0 and release it anyway.",
        "Always code as if the maintainer is a violent psychopath who knows where you live.",
        "There are only two hard things in CS: cache invalidation and naming things.",
        "Bro, this easter egg has more lines of code than my first university project.",
        "You're still tapping? Here is a secret: Open Source Bhaiya loves you! ❤️",
        "Welcome to the Matrix, Neo. Which pill do you take? Red or Blue?",
        "Press F to pay respects to all the deleted scratch files.",
        "Status: 100% Kotlin, 0% spaghetti (well, maybe 2% spaghetti).",
        "Loading secret developer tools... [||||||||||] 99% (Estimated time: 42 years)",
        "Developer joke: Two threads walk into a bar. The bartender says, 'Table for who?'",
        "You are now officially a certified OTP Sync Master Tapper! 🎖️",
        "Warning: Prolonged tapping may cause finger cramps and sudden bursts of laughter.",
        "No OTPs were delayed in the creation of this joke.",
        "Bro, if you find any bugs, remember: It's open-source charm!",
        "Developer easter egg complete. You can now return to syncing your OTPs in peace!",
        "Still here? Okay fine, you win. You are the ultimate app tester! 🚀"
    )
}
