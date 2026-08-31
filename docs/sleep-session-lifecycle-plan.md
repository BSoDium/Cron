# Sleep session lifecycle — bounded active window

> Agent-ready specification for #191 (and a structural fix that also de-risks #179's "whole-day
> stuck session" symptom). Read in full before implementing — this document replaces the
> three-independent-patch proposal originally written into #191; that approach is exactly the
> "hot fixes scattered across three files" pattern this plan avoids. Don't cherry-pick pieces —
> the point is one concept enforced at one chokepoint, not three tuned durations that can drift
> out of sync.

## The actual root cause, restated

A `SleepSession` currently has no notion of its own valid lifetime. It stays open — and stays
willing to accept new sensor evidence as "this session, still asleep" — for as long as no
`OutOfBedConfirmed` happens to arrive, however long that takes. Nothing bounds it except the
*next* evening's `EveningPlan` bootstrapping and superseding it (`SessionFsm.supersedeIfStale`,
`SessionFsm.kt:163-172`) — a once-a-day backstop, not a real lifecycle.

Patching this at the sensor layer (time-of-day check in `ScreenStateMonitor`), the FSM layer
(an ad hoc rearm-expiry timer), and the AI prompt layer (an elapsed-time condition in
`OVERNIGHT_REPLAN`) independently — the three-fix list originally in #191 — means three
separately-tuned durations in three files that all have to keep agreeing with each other
forever. That's the maintenance-nightmare shape being rejected here.

## The fix: one bounded window, enforced at one chokepoint

A session's **active window** is fixed and known the moment the session bootstraps — it doesn't
need to be recomputed, guessed at, or kept in sync after the fact:

```
sessionWindowEnd = (session.date atTime session.plan.hardLatest).toInstant(timezone) + SESSION_WINDOW_GRACE
```

`hardLatest` is already the point past which nothing about that morning matters — it's the
existing "never exceed" safety floor (`HardLatestScheduler`). `SESSION_WINDOW_GRACE` (proposed:
`3.hours`) covers genuine same-morning lingering (dismiss, doze off again for a bit, actually get
up an hour later) without covering an unrelated afternoon nap. One named constant, one place.

This requires **no new persisted field and no DB migration** — `hardLatest`, `date`, and
`timezone` are already on `SleepSession`/`DayPlan`; the window end is a pure function of data
already stored, exactly like the existing `Instruction`-as-JSON-blob precedent for schema-free
additions.

```kotlin
// SessionFsm.kt, alongside the other pure/testable functions
internal fun sessionWindowEnd(session: SleepSession, grace: Duration = SESSION_WINDOW_GRACE): Instant =
    session.date.atTime(session.plan.hardLatest).toInstant(TimeZone.of(session.timezone)) + grace

internal fun isWithinActiveWindow(session: SleepSession, now: Instant): Boolean =
    now <= sessionWindowEnd(session)
```

Both pure, both unit-testable without Android — matching this file's existing `transition`/
`shouldTriggerAi` convention.

### What gets gated, and what doesn't

Not every trigger should be subject to window expiry. Split `TriggerType` the same way this file
already splits `AI_TRIGGERS`/`THROTTLEABLE_TRIGGERS` — a named `Set<TriggerType>`, not a scattered
per-branch check:

```kotlin
/** Passive, sensor-inferred signals — only meaningful within the session's active window. A
 *  reading of "dark and still" or "walking" outside that window isn't evidence about this
 *  session; it's an unrelated part of the day (a nap, an errand). */
private val WINDOW_GATED_TRIGGERS = setOf(
    TriggerType.SleepOnset,
    TriggerType.MidSleepActivity,
    TriggerType.HcStageUpdate,
    TriggerType.OutOfBedConfirmed,
    TriggerType.WakeWindowOpportunity,
    TriggerType.CalendarChange,
)
```

`AlarmDismissed`, `AlarmSnoozed`, and `HardLatestFired` are direct user actions or the safety net
itself firing — an alarm actually ringing is real-world proof the session is live regardless of
any computed window, so these always bypass the gate. `EveningPlan` bootstraps or supersedes a
session and isn't gated by a window that doesn't exist yet.

### The chokepoint: `SessionFsm.onEvent`

Add the guard once, right after fetching `current`, before `transition()` runs:

```kotlin
if (current != null &&
    event.trigger in WINDOW_GATED_TRIGGERS &&
    !isWithinActiveWindow(current, event.timestamp)
) {
    Log.i(TAG, "Session ${current.id} window expired at ${sessionWindowEnd(current)} — dropping stale ${event.trigger}")
    return@withContext current.id
}
```

This is the entire fix for #191's actual bug: an afternoon nap's `SleepOnset` now never reaches
`transition()`, never flips status to `ReMonitoring`, and never reaches `shouldTriggerAi` — so it
never triggers an AI turn, never gets AI-relabeled "Replanned for the night", and the AI prompt
never even sees it. **The `OVERNIGHT_REPLAN` prompt rule doesn't need to change for correctness**
— defense-in-depth wording there is optional polish, not required, because the architecture
prevents the bad event from ever reaching the AI turn pipeline at all.

## Self-closing: a session shouldn't need a future event to end

The chokepoint above stops a nap from *reopening* a session, but a session that receives no
further events at all (silence all afternoon, no nap, nothing) still sits in `Awake` until
tomorrow's `EveningPlan` supersedes it — this is #179's "whole-day stuck" symptom's other half,
and it's independent of naps entirely. Fix it the same way `HardLatestScheduler` already solves
the analogous "this needs to fire even if the app is killed" problem: an exact alarm, armed once
at bootstrap, mirroring the existing pattern exactly.

1. New `SessionExpiryScheduler` (`alarm/SessionExpiryScheduler.kt`), same shape as
   `HardLatestScheduler`: `arm(sessionWindowEnd, sessionDate, sessionId)` /
   `clear(sessionDate)`, using `AlarmManager.setAlarmClock` and its own request-code namespace.
2. New `AlarmConstants.KIND_SESSION_EXPIRY`, handled in `AlarmReceiver.handleAlarmFired` the same
   way `KIND_HARD_LATEST` already is (`AlarmReceiver.kt:69-85`) — `goAsync()` +
   `Dispatchers.IO`, per this repo's existing BroadcastReceiver convention.
3. Rather than round-tripping through `onEvent`/`TriggerType` (this isn't something that
   happened to the user — it's a janitorial "time's up" signal), give it its own specialized
   entry point mirroring `onSnooze`'s existing precedent of bypassing the general event pipeline:

   ```kotlin
   /** Fired by [SessionExpiryScheduler]'s exact alarm. A session with no activity since its
    *  active window closed is done, whether or not a clean OutOfBedConfirmed ever arrived. */
   suspend fun completeIfExpired(sessionId: String): Boolean = withContext(Dispatchers.IO) {
       val session = repository.findById(sessionId) ?: return@withContext false
       if (session.status == SessionStatus.Complete) return@withContext false
       if (Clock.System.now() < sessionWindowEnd(session)) return@withContext false
       completeSession(session)
       true
   }
   ```
4. Arm it at bootstrap alongside `hardLatestScheduler.arm(...)` (`SessionFsm.kt:110-115`); clear
   it wherever `hardLatestScheduler.clear(...)` already is (`onStatusChange`'s `Complete` branch,
   `supersedeIfStale`).

## Bonus fix uncovered while designing this: one `completeSession`, not three

Writing `completeIfExpired` surfaced a pre-existing small drift worth fixing in the same change:
`supersedeIfStale` (`SessionFsm.kt:163-172`) already hand-rolls a *different*, incomplete version
of "mark this session complete" — it updates status and clears alarms, but unlike
`onStatusChange`'s `Complete` branch it never calls `repository.cancelAiTurn`,
`repository.triggerSleepSessionWrite`, or stops the foreground service for the abandoned session.
Extract one shared helper and have all three completion paths call it, so there's exactly one
place that knows what "complete" means:

```kotlin
private suspend fun completeSession(session: SleepSession) {
    repository.updateStatus(session.id, SessionStatus.Complete)
    alarmScheduler.cancel(session.date)
    hardLatestScheduler.clear(session.date)
    sessionExpiryScheduler.clear(session.date)
    repository.cancelAiTurn(session.id)
    repository.triggerSleepSessionWrite(session.id)
    context.startService(SleepSessionService.stopIntent(context))
    Log.i(TAG, "Session ${session.id} complete")
}
```

Called from: the normal `onEvent`-driven transition to `Complete` (replacing
`onStatusChange`'s `Complete` branch body), `supersedeIfStale`, and `completeIfExpired`. This is
the difference between "three call sites that happen to agree today" and "one call site that
can't drift" — the second is what makes this robust rather than merely correct right now.

## What deliberately does NOT change

- **`ScreenStateMonitor.shouldEmitOnset`** stays exactly as-is. The sensor is a dumb, generic
  "dark and still for N minutes" detector; it has no business knowing about session lifecycles,
  and shouldn't. All the intelligence belongs at the one FSM chokepoint above.
- **The `rearm()` mechanism** (`ScreenStateMonitor.kt:100-106`) is unaffected — it's still exactly
  right for "catch a second sleep onset quickly after this morning's dismiss." The window check
  is what stops a *stale* rearm's onset from mattering once it's hours later; it doesn't touch
  when or how the sensor re-arms.
- **`OVERNIGHT_REPLAN`'s prompt wording** — see above; optional follow-up, not required.

## Testing

All new logic is pure and testable without Android, matching this file's existing convention:

- `isWithinActiveWindow`/`sessionWindowEnd`: table-driven — just inside window, just outside,
  exactly at the boundary.
- `onEvent`'s new guard: a `WINDOW_GATED_TRIGGERS` member arriving after `sessionWindowEnd` is
  dropped (status unchanged, no AI turn) — for at least `SleepOnset` and `CalendarChange`.
- `AlarmDismissed`/`AlarmSnoozed`/`HardLatestFired` still process normally regardless of window —
  regression guard, since these must never be silently dropped.
- `completeIfExpired`: no-ops on an already-`Complete` session (idempotency — see below) and on a
  session still inside its window; completes one that's past it.

## Sequencing note: interaction with #153

`completeIfExpired` writes to the same session row `onEvent` does, from a different entry point
(a broadcast, not the main event pipeline) — the same unguarded-concurrent-write class of problem
#153 already tracks for `onEvent` itself. This plan's writes are idempotent by construction
(`completeSession`'s effects are safe to invoke twice; `completeIfExpired` itself early-returns
on an already-`Complete` session), so it's safe to land independently — but land it after #153's
serialization fix if both are in flight at once, rather than adding a second unguarded writer
while that's still open.

## Why this fully resolves #191 without touching three files

- The reported symptom (a nap gets labeled "Replanned for the night") can't happen — the event
  never reaches `transition()` or the AI turn pipeline.
- #179's "whole-day stuck" sessions get a real, provable end even with zero further sensor
  activity — the session no longer depends on eventually getting lucky with a clean
  `OutOfBedConfirmed`.
- #97 (false *early* onset before real bedtime) is a different mechanism and isn't fixed by this
  — it still needs its own `MidSleepActivity`-driven rearm fix. Not a conflict; this plan doesn't
  block it.
- One new constant (`SESSION_WINDOW_GRACE`), one new gating set, one new scheduler mirroring an
  existing one, one consolidated completion helper. No sensor changes, no prompt changes required
  for correctness, no DB migration.
