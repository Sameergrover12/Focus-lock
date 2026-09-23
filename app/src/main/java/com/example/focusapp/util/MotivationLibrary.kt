package com.example.focusapp.util

object MotivationLibrary {
    val FULL_SCREEN_QUOTES = listOf(
        "\"No One Knows What The Outcome Will Be. So, Choose Whatever, You'll Regret The Least.\"\n— Levi Ackerman",
        "\"The pain of regret is much more than failure.\"",
        "\"You don't have to be great to start, but you have to start to be great.\"",
        "\"Worrying does not take away today's troubles, but it takes away tomorrow's peace.\"",
        "\"The longer you stay on the wrong train, the more expensive it is to get home.\"",
        "Bees don't waste their time explaining to flies that honey is better than garbage. Focus on yourself.",
        "\"It is a shame for a man to grow old without seeing the beauty and strength of which his body is capable of.\"\n— Socrates",
        "\"A man can be destroyed but not defeated.\"\n— Ernest Hemingway",
        "Life is 'C' between 'B' & 'D': The choices we make between Birth and Death.\n— Jean-Paul Sartre",
        "\"Discipline is choosing between what you want now and what you want most.\"\n— Abraham Lincoln",
        "\"We must all suffer one of two things: the pain of discipline or the pain of regret.\"\n— Jim Rohn",
        "Your screen time looks like a high score in a game you are losing in real life. Put the phone down.",
        "Are you controlling the device, or is a 6-inch piece of glass controlling your destiny?",
        "Short-term dopamine will never buy you long-term respect. Lock back in.",
        "\"The elevator to success is out of order. You'll have to use the stairs... one step at a time.\"\n— Joe Girard",
        "Rome wasn't built in a day, but they were laying bricks every hour. You are currently scrolling.",
        "\"No man is free who is not master of himself.\"\n— Epictetus",
        "\"Wake up to reality! Nothing ever goes as planned in this accursed world.\"\n— Madara Uchiha",
        "\"Push past the pain. Giving up hurts way more.\"\n— Vegeta",
        "Your future self is watching you through memories right now. Don't embarrass them.",
        "You opened this out of muscle memory, didn't you? Take a deep breath. Reset.",
        "The work you are actively avoiding is the exact work that will change your life.",
        "\"Hard choices, easy life. Easy choices, hard life.\"\n— Jerzy Gregorek",
        "Scrolling won't fix whatever you're running away from. Face it.",
        "\"The graveyard is the richest place on earth, full of unfulfilled potential. Don't add yours to it.\"\n— Les Brown",
        "Small disciplines repeated every single day compound into massive achievements. Don't break the chain.",
        "You're trading your real-world ambitions for a 15-second reel created by a stranger. Make that make sense.",
        "\"Don't count the days, make the days count.\"\n— Muhammad Ali",
        "One day or Day One? You decide right now.",
        "You promised yourself this time would be different. Prove it with your actions."
    )

    val QUICK_FEEDBACK_LINES = listOf(
        "Nice try, buddy. Back to work.",
        "Did you really think that would slip through?",
        "Caught you in 4K. Put the phone down.",
        "Denied. Big bro is watching.",
        "Error 404: Willpower not found. Try again.",
        "We both know you shouldn't be doing this.",
        "Touch grass instead.",
        "I saw that thumb movement. Not today.",
        "Sneaky, but my code is faster than your impulses.",
        "Trying to bypass the rules? Nice attempt.",
        "Take three deep breaths and get back to your tasks.",
        "Restricted keyword detected. Keep your mind clean.",
        "That setting is locked for a reason. Don't bargain with weakness.",
        "You set these rules when you were focused. Respect your own word.",
        "Nope! Not letting you ruin your streak today.",
        "Your future self just said: 'Thank you for not giving in.'",
        "System override denied. Return to the mission.",
        "Bro really thought he could sneak past the scanner.",
        "Blocked. Eyes back on the prize.",
        "Bypass failed successfully.",
        "Don't negotiate with impulse.",
        "Settings tampering detected. Access denied.",
        "You're bigger than this cheap trick.",
        "Nothing to see here. Back to the grind.",
        "Rule change rejected. Stay disciplined."
    )

    fun getRandomFullScreenQuote(): String = FULL_SCREEN_QUOTES.random()
    fun getRandomQuickFeedback(reclaimedCommitment: String? = null): String {
        val base = QUICK_FEEDBACK_LINES.random()
        return if (!reclaimedCommitment.isNullOrBlank() && kotlin.random.Random.nextInt(3) == 0) {
            "$base Remember your commitment to $reclaimedCommitment."
        } else {
            base
        }
    }
}
