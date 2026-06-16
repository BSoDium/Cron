package fr.bsodium.cron.debug

/** DEBUG-ONLY. The set of scripted response scenarios selectable from the debug settings menu. */
enum class MockScenario(val label: String, val description: String) {
    PLAN_SUCCESS(
        label = "Plan success",
        description = "Thinking block + SUMMARY answer — happy path",
    ),
    TOOL_CALL_ROUND_TRIP(
        label = "Tool call",
        description = "Thinking → read_calendar tool use → scripted answer",
    ),
    STREAMING_ERROR(
        label = "Streaming error",
        description = "Emits a few tokens then throws a retryable 529",
    ),
    EXTENDED_THINKING(
        label = "Extended thinking",
        description = "Long thinking block + multi-paragraph answer",
    ),
}

// ── Scripted content shared across FakeAnthropicClient turns ───────────────

internal const val MOCK_REASONING =
    "Let me read the calendar for the next 24 hours and find the first event you must be ready for. " +
        "All-day markers like Office set the working location; a virtual stand-up is a real anchor with " +
        "no commute. I subtract the travel buffer and preparation time, then nudge into a light-sleep window."

internal const val MOCK_REASONING_LONG =
    "Let me think through your entire schedule carefully. First I'll identify the hard anchor — the " +
        "earliest commitment that cannot move. Then I'll work backwards: subtract your commute estimate, " +
        "your preparation buffer, and any additional lead time you requested. I also need to check " +
        "whether tomorrow is a free day, in which case the normal anchor rules don't apply. " +
        "Once I have the target wake time, I'll snap it to the nearest light-sleep transition using " +
        "the 90-minute cycle heuristic, adjusting by ±15 min to avoid landing in deep sleep. " +
        "Finally I'll verify the result is within your hard earliest/latest alarm bounds before committing."

internal const val MOCK_ANSWER =
    "SUMMARY: Set a 6:40 alarm so you make your 9:00 stand-up\n\n" +
        "Set a **6:40** alarm so you make your 9:00 stand-up. Your first anchor is at the office, about a " +
        "25 min drive. I took the commute plus 45 min of `preparation_time` off the start, then landed on a " +
        "light-sleep moment just before."

internal const val MOCK_ANSWER_LONG =
    "SUMMARY: Set a 6:25 alarm to make your 8:45 on-site meeting\n\n" +
        "Set a **6:25** alarm to make your 8:45 on-site meeting.\n\n" +
        "### How I got there\n\n" +
        "Your first hard anchor tomorrow is an 8:45 in-person meeting at the office (~28 min drive). " +
        "Working backwards:\n\n" +
        "- **8:45** anchor\n" +
        "- **−28 min** drive\n" +
        "- **−45 min** preparation buffer\n" +
        "- **= 6:32** raw wake time\n\n" +
        "The nearest light-sleep window in a standard 90-min cycle lands at **6:25**, which is within " +
        "your ±15 min snap tolerance and above your hard earliest alarm of 5:30. Done."
