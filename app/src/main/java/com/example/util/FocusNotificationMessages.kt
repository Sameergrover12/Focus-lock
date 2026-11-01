package com.example.util

import android.content.Context

object FocusNotificationMessages {

    const val PREFS_NAME = "focus_notification_memory"
    private const val KEY_SEEN_PREFIX = "seen_indices_"

    val DAILY_LOWER_MESSAGES = listOf(
        "You clocked {today} today—down {diff} from your daily average. Winning your time back 🙂",
        "{diff} less than your usual average today ({today} total). Small daily wins build real discipline.",
        "Today's screen time: {today} ({diff} lower than your average). Keep locking out the noise 🫡",
        "Down by {diff} compared to your average! Total screen time: {today}. Your focus is getting sharper 🔥",
        "{today} today vs your average: {diff} saved. That's extra time you kept for real life.",
        "Clean discipline today. {today} total screen time ({diff} lower than your usual baseline) ✨",
        "You cut your screen time by {diff} under your average today ({today} total). Keep this streak alive.",
        "Less scrolling, more living. {today} today ({diff} below your daily average) 😌",
        "{diff} drop below your average today ({today} total). That's how habits actually change 🤝",
        "Strong day: only {today} on screen ({diff} lower than your average). You're in control 💪",
        "{today} today—shaved {diff} off your daily average. Love to see that drop 📉",
        "The algorithm tried, you won. {today} today ({diff} less than your average) 😮💨",
        "{diff} lower than your 30-day baseline ({today} total). Quietly stacking good days.",
        "Only {today} on screen today. That's {diff} below your average you didn't feed to the scroll 🙌",
        "Screen time dropped by {diff} under your average today ({today} total). You made that look easy 😎",
        "{today} logged today—down {diff} from your average. Brain rot avoided for today 🧠",
        "Beat your daily average by {diff} ({today} total). That self-control is kicking in.",
        "{diff} less than your average today ({today} total). Actual main character discipline 👑",
        "Today's total: {today}. Down {diff} from your usual average. Slowly breaking the autopilot.",
        "Saved {diff} compared to your baseline ({today} today). Touching grass is paying off 🌱",
        "{today} on screen today (-{diff} vs your average). Solid restraint all day 👏",
        "Cut out {diff} of noise below your average today ({today} total). You didn't let the phone run your day.",
        "{diff} lower than your average today ({today} total). Your attention span thanks you 🧘‍♂️",
        "Clocked {today} today—a clean {diff} drop from your baseline. Keep cooking 🔥",
        "{today} today vs your average: {diff} saved. Real progress you can actually feel.",
        "Down {diff} from your average today ({today} total). Doomscrolling took an L today 💀",
        "{today} total today—{diff} less than your average. Locked in when it mattered 🎯",
        "You kept {diff} below your average out of the screen today ({today} total). Good stuff 🙂",
        "{diff} less time than your average today ({today} total). Momentum is on your side now.",
        "Only {today} today (-{diff} from your average). Distractions didn't stand a chance 🛡️",
        "{today} today—down {diff} from your daily baseline. Proof you don't need the endless scroll 😌",
        "Trimmed {diff} off your average screen time today ({today} total). Keep that bar high.",
        "{diff} saved under your baseline today ({today} total). You chose real life over pixels today ✨",
        "Screen time down {diff} from your average ({today} today). W discipline today 🤝",
        "{today} on screen today, {diff} lower than your daily average. One day at a time."
    )

    val DAILY_HIGHER_MESSAGES = listOf(
        "Clocked {today} today (+{diff} above your daily average) 😭 The scroll won today, let's lock back in tomorrow.",
        "{today} on screen today, up {diff} from your usual average 😅 Happens to the best of us—bounce back tomorrow.",
        "{today} today—up {diff} past your daily average 💀 Those apps worked overtime on you today.",
        "Screen time crept up by {diff} over your average today ({today} total). Tomorrow is a clean slate to run it back.",
        "{diff} higher than your 30-day baseline ({today} today) 🫠 Some days the distractions just hit harder.",
        "Logged {today} today (+{diff} vs your average) 😬 Not the cleanest run, but tomorrow is a new day.",
        "{today} on screen today—{diff} more than your usual average. One heavy day doesn't ruin the streak.",
        "Up by {diff} past your average today ({today} total) 🫣 We'll pretend today didn't happen and reset tomorrow.",
        "{diff} extra screen time over your average today ({today} total) 📈 Let's bring that graph back down tomorrow.",
        "Today hit {today} (+{diff} vs your average). Shake it off and tighten the focus tomorrow.",
        "{today} today, up {diff} from your baseline 🤦‍♂️ The algorithm definitely caught you slipping today.",
        "Screen time went up {diff} past your average today ({today} total) 😕 Not every day is a win—comeback starts tomorrow.",
        "{diff} more than your usual average ({today} total) 👀 Okay, today got out of hand a little bit.",
        "Clocked {today} today—{diff} higher than your daily baseline. You know what to fix tomorrow.",
        "{today} on screen today (+{diff} vs your average) 🥀 The doomscroll got the best of today.",
        "Up {diff} past your average today ({today} total) 😅 Tough day for focus, but you can flip it around tomorrow.",
        "{diff} jump over your baseline today ({today} total). Tomorrow's another shot to beat your average.",
        "{today} logged today—up {diff} from your daily average 💀 Yeah, today was a heavy phone day.",
        "Screen time rose {diff} past your average today ({today} total) 🫠 Reset the mindset for tomorrow.",
        "{diff} higher than your baseline today ({today} total). Don't sweat one bad day—just don't make it two.",
        "Hit {today} on screen today (+{diff} vs your average) 😬 Distractions were loud today.",
        "{today} today vs your daily average: +{diff} 😭 Tomorrow we get our revenge on the scroll.",
        "{diff} more screen time than your average today ({today} total). Acknowledge the slip and lock back in tomorrow.",
        "Clocked {today} today ({diff} higher than your usual baseline) 🫣 Lowkey a rough day for screen time.",
        "Up by {diff} from your daily average ({today} total) 😮💨 Heavy day, let's cut that down tomorrow.",
        "{today} on screen today—up {diff} past your average. Progress isn't always a straight line.",
        "{diff} extra over your average today ({today} total) 😅 The phone definitely had a hold on today.",
        "Screen time hit {today} today (+{diff} vs your average) 😕 Let's make tomorrow look better than today.",
        "{today} today—{diff} more than your daily baseline. Take the L for today and bounce back tomorrow.",
        "Up {diff} past your average today ({today} total) 💀 Those feeds are dangerous, man.",
        "{diff} higher than your daily average ({today} total) 🙂 Not your best numbers, but easy to fix tomorrow.",
        "Logged {today} on screen today (+{diff} vs baseline). Tomorrow is a fresh 24 hours.",
        "{today} today, up {diff} from your average 🫠 Let's get back on track tomorrow.",
        "{diff} more screen time than your baseline today ({today} total) 👀 Caught slipping today—redeem it tomorrow.",
        "Today's total reached {today} (+{diff} over your average). Reset the standard tomorrow."
    )

    val SUNDAY_WEEKLY_MESSAGES = listOf(
        "Weekly Wrap-up: You saved {saved} of screen time this week! Be proud of your self-control—keep pushing next week 🔥",
        "You reclaimed {saved} from your phone this week. That's real life you earned back. Massive respect 🫡",
        "7-Day Milestone: {saved} saved this week! Your discipline is paying off—let's make next week even stronger 🙂",
        "{saved} less screen time this week! Most people just scroll their week away, but you actually took control.",
        "A whole week of focus—and {saved} saved! Consistency beats motivation every single time. Keep going.",
        "You bought back {saved} of your life this week. Imagine what a full month of this discipline will look like 🚀",
        "Weekly Win: {saved} reclaimed from distractions this week. Take pride in that progress and keep the standard high 🏆",
        "Sunday Check-in: {saved} saved in 7 days. Real change takes effort, and you showed up all week 💪",
        "{saved} reclaimed this week! Your focus is getting stronger every day. Don't let up now.",
        "{saved} saved this week! Small daily choices added up to a big win over the last 7 days ✨",
        "You protected {saved} of your time this week. Stay relentless with your focus next week 🫡",
        "Respect the grind: You saved {saved} of screen time this week. Keep your guard up for the week ahead 🔥",
        "Weekly Milestone: {saved} of screen time saved in 7 days. Proud of the effort—let's beat it next week 🙂",
        "You saved {saved} this week! Remember how good it feels to be in control of your time—carry it into Monday ⚡",
        "{saved} saved over the last 7 days 😮💨 Do you realize how much of your life you just took back?",
        "Sunday W: You kept {saved} away from the algorithm this week. Actual top-tier discipline 👑",
        "7 days, {saved} saved. You didn't just set rules this week—you actually lived by them.",
        "Shaved {saved} off your screen time this week 📉 The weekly graph is looking clean.",
        "{saved} reclaimed in a single week 🙌 That's hours of peace of mind you earned for yourself.",
        "A full week locked in: {saved} saved from mindless scrolling. Keep stacking weeks like this.",
        "{saved} less screen time this week 😎 Your future self is going to thank you for this habit.",
        "Sunday Recap: {saved} saved this week! You're proving that your attention isn't for sale 🤝",
        "Cut {saved} of noise out of your week. That's the difference between wishing for focus and having it.",
        "{saved} saved in 7 days 🧠 Brain clarity is way up this week—keep the streak rolling.",
        "Week Complete: {saved} of screen time saved. Take a bow and run it back on Monday 👏"
    )

    /**
     * Persistent Non-Repeating Random Selection (Memory Logic):
     * Uses SharedPreferences set tracking to ensure no message repeats until the entire pool has been exhausted.
     */
    fun getNextRandomMessage(context: Context, categoryKey: String, pool: List<String>): String {
        if (pool.isEmpty()) return ""
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val seenKey = KEY_SEEN_PREFIX + categoryKey
        val seenSet = prefs.getStringSet(seenKey, emptySet())?.toMutableSet() ?: mutableSetOf()

        val availableIndices = pool.indices.filter { it.toString() !in seenSet }
        val chosenIndex = if (availableIndices.isEmpty()) {
            // All messages in this pool have been seen! Reset the pool
            seenSet.clear()
            pool.indices.random()
        } else {
            availableIndices.random()
        }

        seenSet.add(chosenIndex.toString())
        prefs.edit().putStringSet(seenKey, seenSet).apply()

        return pool[chosenIndex]
    }
}
