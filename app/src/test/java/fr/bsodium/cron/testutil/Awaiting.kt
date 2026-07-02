package fr.bsodium.cron.testutil

/** Polls [condition] until it's true or [timeoutMs] elapses; the goAsync()-launched IO work under test runs on a real thread outside Robolectric's looper, so assertions can't fire immediately after a broadcast. */
fun awaitCondition(timeoutMs: Long = 2_000, intervalMs: Long = 10, condition: () -> Boolean) {
    val deadline = System.currentTimeMillis() + timeoutMs
    while (!condition()) {
        if (System.currentTimeMillis() >= deadline) {
            check(condition()) { "Condition not met within ${timeoutMs}ms" }
        }
        Thread.sleep(intervalMs)
    }
}
