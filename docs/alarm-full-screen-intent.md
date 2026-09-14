# The alarm's full-screen intent — why it can double-ring, and what actually fixes it

Live Pixel 7 investigation (2026-09-14 through 2026-09-23, #158/#211/#214). Read this before touching
`AlarmSoundService`, `AlarmActivity`, or anything about how the alarm gets on screen — the "obvious"
fix (call `startActivity()` directly) was tried, live-tested, and reverted; it never once worked.

## The symptom

On a real device, the alarm's ring sometimes audibly doubles a few (up to tens of) seconds in — two
overlapping copies of the alarm tone. Confirmed via `dumpsys audio`: `AlarmSoundService`'s own
`MediaPlayer` (`usage=USAGE_ALARM`) starts correctly at t=0, then a **second** `MediaPlayer` — `package:
com.android.systemui`, also `usage=USAGE_ALARM` — starts some seconds later, layering on top.

## Root cause: two separate, intentional, undocumented-together Android platform behaviors

This is not a bug in this app's code. It's the visible seam between two real Android platform
policies stacking on top of each other:

1. **Android 14+ full-screen-intent permission gating.** `USE_FULL_SCREEN_INTENT` is auto-granted by
   Google Play at install time only for apps declared as alarm/calling apps — but that auto-grant is a
   **Play Store install-time step**, not a manifest-permission grant. A sideloaded build (`adb install`,
   every dev/test install this whole investigation used) never goes through it. Verify the real state
   with `adb shell appops get <package>` — look for `USE_FULL_SCREEN_INTENT`, separate from the plain
   manifest-permission list `dumpsys package` shows (which will say `granted=true` regardless, and is
   not the thing that actually gates this). Granting it for local testing:
   ```sh
   adb shell appops set <package> USE_FULL_SCREEN_INTENT allow
   ```
   This mirrors exactly what a real Play Store install does automatically for a qualifying app — not a
   workaround, not a security bypass.

2. **Android 13+ "don't yank the screen while the device is in active use."** Documented (AOSP source:
   `source.android.com/docs/core/permissions/fsi-limits` and the platform release notes): even with the
   permission fully granted, the system shows the notification as a heads-up peek first — not the full
   screen — while the device is actively in use, only escalating to the real full-screen launch once
   that condition lifts. This is **separate from and additional to** (1); granting the permission alone
   does not make it launch instantly if the device is being actively touched at that moment.

**Both were confirmed independently, live, on this device — neither one explains the whole picture
alone:**
- Granting the `USE_FULL_SCREEN_INTENT` appop (lever 1) made **zero observable difference** — same
  ~10s delay, same doubled sound, both before and after (confirmed via `appops get` showing `allow`
  and a recent `time=` timestamp during the test window that showed no change).
- Every single test this investigation ran had the device in active use at the exact fire moment (we
  always jumped the clock by touching Settings right before), which is precisely the condition lever 2
  suppresses for. This is very likely why doubling reproduced every time in testing, but may not
  reflect genuine overnight use — a phone sitting untouched, locked, for hours, would not be "in use"
  when the real alarm crosses.

## The escalation itself is what plays the second sound — not a launch-speed problem

When systemui finally does escalate to the full screen, `ActivityTaskManager`'s own log shows exactly
who did it:
```
START u0 {cmp=.../AlarmActivity ...} from uid <app> (realCallingUid=10249) (BAL_ALLOW_NON_APP_VISIBLE_WINDOW [realCaller])
```
`realCallingUid=10249` is `com.android.systemui`'s own uid — **the system itself** fires the
`PendingIntent`, not the app. It plays its own alert tone at that exact moment, as part of that
escalation. This ruled out a slow cold Activity launch as the cause early on: the `ActivityTaskManager`
timestamp shows the delay is entirely in *when the START request is issued*, not in how long the
Activity takes to draw once it is (confirmed via the `Displayed ... AlarmActivity: +Nms` log line
landing well under 500ms every time, right after the late `START`).

## What was tried and disproven, in order

1. **Direct `startActivity()` from `AlarmSoundService`**, assuming a foreground service with an
   alarm-category notification gets its own Background-Activity-Launch (BAL) exemption. **Wrong** —
   live-blocked every time:
   ```
   E/ActivityTaskManager: Background activity launch blocked! goo.gle/android-bal
     callingUidHasVisibleActivity: false
     callingUidHasNonAppVisibleWindow: false
     callingUidProcState: FOREGROUND_SERVICE
   ... (BAL_BLOCK) result code=0
   ```
   Being `FOREGROUND_SERVICE` alone isn't in the exemption list. Reverted (#214) — it added real
   complexity (`singleTask`-adjacent signaling, a new service action) for zero benefit and, being a
   silent failure (no exception), needed its own defensive "confirm the activity actually showed before
   trusting it" plumbing just to not make things worse. **AOSP's own DeskClock doesn't attempt this
   either** — `AlarmService.startAlarm()` calls `AlarmNotifications.showAlarmNotification(...)` and
   relies solely on the notification's `setFullScreenIntent`, same as this app. That's strong evidence
   this genuinely isn't bypassable at the app-code level, even by Google's own reference implementation
   (which does additionally run with elevated system-app privileges DeskClock has and this app doesn't —
   so if even DeskClock doesn't bother trying to bypass it, a regular sideloaded/Play-Store app has even
   less chance).
2. **Granting `USE_FULL_SCREEN_INTENT` via appops.** Real, correctly-targeted, confirmed active during
   the test — made no observable difference, because lever 2 above is independent of it.

## What's still worth doing (not yet implemented — see the open GitHub issue for status)

- **Call `NotificationManager.canUseFullScreenIntent()` at runtime** and, if false, guide the user to
  **Settings → Special App Access → Full screen notifications** (`ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT`)
  — this is the real, sanctioned, user-facing lever real alarm apps use, not a code trick. On a genuine
  Play Store install this should already read `true` (Play auto-grants it for a declared alarm app), so
  this is a defensive check for the case it somehow isn't, not an assumed-necessary onboarding step.
- **Leave device-in-use suppression alone.** There is no documented, sanctioned way to opt out of it,
  and neither AOSP's DeskClock nor any third-party alarm-app research found so far (checked: an
  actively-being-researched open issue on a comparable open-source alarm app, still unresolved as of
  this writing) has one either. Trust it to not matter for a genuinely idle, locked, overnight phone —
  the actual target scenario this app is built for.
- **Keep future live-device verification of this specific behavior honest about device state.** A test
  that jumps the clock via active Settings interaction right at fire time is testing the *worst case*
  for lever 2, not the common case. If this needs re-verifying later, prefer a real overnight run (set
  a near-term alarm, lock the phone, leave it alone) over another clock-jump-while-touching-it cycle.

## Related, smaller fixes from the same investigation (already landed)

- **The channel-level sound/vibration doubling** (a completely different, already-fixed bug): the
  notification channel had its own `setSound`/`vibrationPattern` left over from before `AlarmSoundService`
  existed, firing once per notification post *in addition to* the service's own looping `MediaPlayer`/
  `Vibrator` — audible as a second "ding" whenever the notification got re-posted (e.g. the two-alarm
  model's AI-then-hard-latest race). Fixed by moving to a fresh channel id with no sound/vibration of its
  own configured (a channel's sound/vibration can't be changed after creation — Android silently ignores
  updates — hence the new id, not a config change on the old one).
- **Swiping the notification away orphaned the ring.** Android 13+ lets a user swipe away a foreground
  service's notification regardless of `setOngoing(true)` (there's a dedicated system "stop" affordance
  for this). No `deleteIntent` was wired, so that swipe left the notification gone but the sound/
  vibration still running, forever. Fixed by routing the notification's delete intent to the same
  `ACTION_DISMISS` the in-app slide-to-dismiss uses.
- **`AlarmActivity` is `singleTask` with an `onNewIntent` override.** Independent of the full-screen-
  intent investigation: the app's two-alarm model can fire the AI alarm and the hard-latest safety alarm
  ~100ms apart (observed live, repeatedly) — both target this same Activity, and without `singleTask`
  they'd stack duplicate instances rather than routing the second intent to the existing one.
