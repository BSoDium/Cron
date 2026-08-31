# Sleep session lifecycle — bounded active window

> Agent-ready specification for #191 (and a structural fix that also de-risks #179's "whole-day
> stuck session" symptom). Read in full before implementing — this document replaces the
> three-independent-patch proposal originally written into #191; that approach is exactly the
> "hot fixes scattered across three files" pattern this plan avoids. Don't cherry-pick pieces —
> the point is one concept enforced at one chokepoint, not three tuned durations that can drift
> out of sync.
>
> Revision 2 (2026-08-31): fixes five gaps a second, adversarial pass found in revision 1 — a too-
> generous window on lenient-hardLatest days, a missing re-arm hook on mid-session replan, an
> inline (not extracted/testable) gate condition, and two real Android-API mistakes (wrong
> AlarmManager call, wrong receiver) that would have shipped a phantom "next alarm" indicator and
> risked routing through ring-triggering code. Superseded parts are struck through, not deleted —
> see each section for what changed and why.

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

A session's **active window** has two bounds, not one, so it's both safe (never closes before a
real safety-net alarm could still fire) and tight (doesn't stay open for hours after the user is
demonstrably up):

```kotlin
// SessionFsm.kt, alongside the other pure/testable functions
internal fun sessionWindowEnd(session: SleepSession, now: Instant, grace: Duration = SESSION_WINDOW_GRACE): Instant {
    val hardLatestCeiling = session.date.atTime(session.plan.hardLatest).toInstant(TimeZone.of(session.timezone)) + grace
    val lastAwakeAt = session.events.lastOrNull { it.trigger == TriggerType.AlarmDismissed || it.trigger == TriggerType.OutOfBedConfirmed }?.timestamp
    val postWakeCeiling = lastAwakeAt?.plus(grace)
    return listOfNotNull(hardLatestCeiling, postWakeCeiling).min()
}

internal fun isWithinActiveWindow(session: SleepSession, now: Instant): Boolean =
    now <= sessionWindowEnd(session, now)
```

~~`sessionWindowEnd = hardLatest + SESSION_WINDOW_GRACE`, fixed at bootstrap~~ **(revision 1 —
superseded).** A single fixed anchor at `hardLatest + grace` is too generous whenever `hardLatest`
sits late in a lenient wake window (a free/weekend day, say 11am): the window would stay open
until 2pm, so someone who got up at 7am and naps at 1pm would still get misread as "fell back
asleep." Taking the **earlier** of the two ceilings — the original hard-latest safety bound, and
`grace` hours after the session's own last confirmed-awake event — closes the window promptly
once the user is actually up, while still covering the "never got a clean wake signal at all"
case via the hard-latest ceiling. `SESSION_WINDOW_GRACE` (proposed: `3.hours`) is one named
constant either way.

This requires **no new persisted field and no DB migration** — every input above (`hardLatest`,
`date`, `timezone`, `events`) is already on `SleepSession`/`DayPlan`; the window end is a pure
function of data already stored, exactly like the existing `Instruction`-as-JSON-blob precedent
for schema-free additions. Both functions above are pure and unit-testable without Android,
matching this file's existing `transition`/`shouldTriggerAi` convention.

### What gets gated, and what doesn't

Not every trigger should be subject to window expiry. Split `TriggerType` the same way this file
already splits `AI_TRIGGERS`/`THROTTLEABLE_TRIGGERS` — a named `Set<TriggerType>`, not a scattered
per-branch check:

```kotlin
/** Signals that only matter while the session is still live: sensor inferences about the user's
 *  physical state (sleep onset, activity, HC stage, out-of-bed), plus a calendar edit whose
 *  relevance to *this* morning's alarm ends when the morning does. A reading of "dark and still"
 *  or a changed event, hours after the session's window closed, isn't evidence about this
 *  session — it's an unrelated part of the day (a nap, an errand, tomorrow's calendar). */
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

~~Add the guard once, right after fetching `current`, as an inline condition~~ **(revision 1 —
superseded).** Extract it as a named pure predicate instead, consistent with this file's own
`transition`/`shouldTriggerAi` — inlining the condition directly in the suspend `onEvent` body
would have made it the one piece of new logic in this file that *isn't* independently
unit-testable, undercutting the exact property this plan leans on to call itself robust:

```kotlin
internal fun shouldGateEvent(session: SleepSession, trigger: TriggerType, now: Instant): Boolean =
    trigger in WINDOW_GATED_TRIGGERS && !isWithinActiveWindow(session, now)
```

Called from `onEvent`, right after fetching `current` and **after** the existing auto-plan-off
gate (`SessionFsm.kt:50-55`) — two independent, stacked gates run in that order, auto-plan first:

```kotlin
if (current != null && shouldGateEvent(current, event.trigger, event.timestamp)) {
    Log.i(TAG, "Session ${current.id} window expired at ${sessionWindowEnd(current, event.timestamp)} — dropping stale ${event.trigger}")
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
and it's independent of naps entirely. It needs its own timer, because `onEvent`'s gate only runs
when *something* arrives — silence produces no event to gate in the first place.

1. New `SessionExpiryScheduler` (`alarm/SessionExpiryScheduler.kt`), API shape mirroring
   `HardLatestScheduler`'s `arm`/`clear`/`isArmed`, but ~~using `AlarmManager.setAlarmClock`~~
   **(revision 1 — wrong API, superseded)** using `AlarmManager.setExactAndAllowWhileIdle`,
   exactly like `EveningPlanScheduler` (`EveningPlanScheduler.kt:63-67`). `setAlarmClock` is
   user-visible by OS design — it's the whole reason `HardLatestScheduler` uses it, since that
   alarm might genuinely need to ring. This timer must never be user-visible: once the real
   hard-latest alarm fires and `hardLatestScheduler.clear()`s, an internal cleanup timer left on
   `setAlarmClock` would become the system's displayed "next alarm" on the lock screen — a phantom
   alarm time for something the user never set. `setExactAndAllowWhileIdle` is exact and
   Doze-exempt without any UI surface, and (like `EveningPlanScheduler`) needs no new permission —
   the manifest's `USE_EXACT_ALARM` already covers it.
2. ~~New `AlarmConstants.KIND_SESSION_EXPIRY`, handled in `AlarmReceiver.handleAlarmFired`~~
   **(revision 1 — wrong receiver, superseded)**. `AlarmReceiver.handleAlarmFired` exists to make
   an alarm *ring* — `AlarmRingingState.markRinging()`, the notification channel, the sound.
   Routing a silent cleanup check through it risks it actually ringing. Mirror `EveningPlanReceiver`
   instead (`receiver/EveningPlanReceiver.kt`): a small dedicated `BroadcastReceiver` with its own
   `ACTION_FIRE`-equivalent action, `goAsync()` + `CoroutineScope(Dispatchers.IO).launch` (same
   BroadcastReceiver convention as every other receiver in this repo), calling straight into the
   FSM. No ringing code anywhere near it.
3. Give it its own specialized `SessionFsm` entry point, mirroring `onSnooze`'s existing precedent
   of bypassing the general `onEvent` pipeline for a shape that doesn't fit it (this isn't
   something that happened to the user — it's a janitorial "time's up" signal):

   ```kotlin
   /** Fired by [SessionExpiryScheduler]'s exact alarm. A session with no activity since its
    *  active window closed is done, whether or not a clean OutOfBedConfirmed ever arrived. */
   suspend fun completeIfExpired(sessionId: String): Boolean = withContext(Dispatchers.IO) {
       val session = repository.findById(sessionId) ?: return@withContext false
       if (session.status == SessionStatus.Complete) return@withContext false
       val now = Clock.System.now()
       if (now < sessionWindowEnd(session, now)) return@withContext false
       completeSession(session)
       true
   }
   ```
4. Arm it at bootstrap alongside `hardLatestScheduler.arm(...)` (`SessionFsm.kt:110-115`), **and
   re-arm it wherever `hardLatestScheduler.arm(...)` is called again** — `refreshPlanFromSettings`
   (`SessionFsm.kt:125-155`) already re-arms `hardLatestScheduler` when a manual replan changes
   `hardLatest` mid-session, but nothing in revision 1 re-armed the expiry timer at that same spot.
   Left unfixed, a replan pushing `hardLatest` later would leave a stale, too-early expiry alarm
   that could force-complete a session still legitimately active under its new schedule. Same fix
   point, same `if (refreshed.hardLatest != session.plan.hardLatest)` guard already there.
5. Clear it wherever `hardLatestScheduler.clear(...)` already is (inside the new shared
   `completeSession`, below).

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

Note this also means stopping the foreground service (and its sensors) is itself a second,
independent line of defense against a late nap once a session is Complete — the physical sensor
that would emit a stray `SleepOnset` is no longer registered at all. The `onEvent` gate above is
still necessary, not redundant, for the gap between "window closes" and "the scheduled cleanup
alarm actually runs" — a race the gate covers immediately and the timer covers eventually.

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

- `sessionWindowEnd`/`isWithinActiveWindow`: table-driven — inside window via the hard-latest
  ceiling, inside via the post-wake ceiling, outside via each, and the "whichever is earlier"
  case where the two ceilings disagree (the lenient-hardLatest-day scenario revision 1 missed).
- `shouldGateEvent`: a `WINDOW_GATED_TRIGGERS` member outside the window gates; the same trigger
  inside the window doesn't; `AlarmDismissed`/`AlarmSnoozed`/`HardLatestFired` never gate
  regardless of window — regression guard, since these must never be silently dropped.
- `completeIfExpired`: no-ops on an already-`Complete` session and on a session still inside its
  window; completes one that's past it.
- `refreshPlanFromSettings`: changing `hardLatest` mid-session re-arms `sessionExpiryScheduler`,
  not just `hardLatestScheduler` — regression guard for the gap this revision fixed.

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
- One new constant (`SESSION_WINDOW_GRACE`), one new gating set, one new pure gate predicate, one
  new scheduler mirroring `EveningPlanScheduler` (not `HardLatestScheduler` — deliberately silent,
  no phantom alarm indicator), one new dedicated receiver mirroring `EveningPlanReceiver`, one
  consolidated completion helper. No sensor changes, no prompt changes required for correctness,
  no DB migration, no new permission.
