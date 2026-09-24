# Sleep detection — research and comparison to Cron's current heuristic

Phone-only sleep/wake detection (no wearable, no wrist actigraph, no bedside radar) is a genuinely
hard, actively-researched problem — not something Cron is uniquely bad at. This doc grounds that
claim in the actual literature and the actual mainstream implementations, then compares Cron's
current heuristic (`ScreenStateMonitor.kt`, `ActivityRecognitionMonitor.kt`) against them and gives
concrete, prioritized changes.

For the actual proposed architecture (adaptive, self-correcting, phone-only), see
[`sleep-detection-architecture.md`](sleep-detection-architecture.md).

## 1. Correcting a common misconception: Google Clock does not do phone-only sleep tracking

The "Google Clock uses ML combining brightness, movement, micro-wakes, and snore/cough detection"
framing (from the pasted Gemini summary this doc originated from) does not hold up:

- The Clock app's **Bedtime** feature is a wind-down/Do Not Disturb scheduler. It does not run
  accelerometer- or microphone-based sleep detection.
- Google's actual ML-based sleep tracking — the kind that infers sleep stages — lives on the
  **Pixel Watch / Fitbit** (accelerometer + heart-rate variability, on a device worn on the body,
  which is a fundamentally easier signal than a phone sitting on a nightstand) and previously on
  the **Nest Hub's Soli radar + microphone** ("Sleep Sensing," discontinued). Neither runs on a
  bare phone.
- The **Sleep API** (`com.google.android.gms.location.SleepSegmentEvent` /
  `SleepClassifyEvent`) is a separate, developer-facing Play Services library — it's what
  third-party apps integrate, not what Clock itself uses for in-app tracking. See §2.

So "how does Google Clock do it on just a phone" doesn't have an answer, because it doesn't do
that. The right comparison set is: (a) the Sleep API third-party apps can integrate, (b) the
clinical actigraphy algorithms everything ultimately descends from, and (c) how the actual
mainstream phone-only apps (Sleep as Android, Sleep Cycle) are built. All three follow below.

## 2. The Android Sleep API — what it actually is, and why it's a risky foundation

[developers.google.com/location-context/sleep](https://developers.google.com/location-context/sleep),
[Android Developers Blog, 2021](https://android-developers.googleblog.com/2021/02/low-power-sleep-tracking-on-android.html)

- Backed by `play-services-location`, gated behind the same `ACTIVITY_RECOGNITION` permission
  Cron already requests for `ActivityRecognitionMonitor`.
- Uses an **on-device AI model** fed by the accelerometer and ambient light sensor. Google's own
  framing: brightness dropping + motion stopping + confidence rising together implies sleep onset.
  That's the same signal family as `ScreenStateMonitor` + `AmbientLightReader` already use — Cron's
  approach is not naive relative to Google's, it's a hand-tuned threshold version of the same idea.
- Two event types, and their cadence matters a lot for a "wake me for a replan" use case:
  - `SleepClassifyEvent`: a confidence score every **up to 10 minutes**, with the motion/light
    values that produced it. This is the closest thing to real-time Google offers.
  - `SleepSegmentEvent`: a **daily, retrospective** onset/wake pair, delivered only after a wake-up
    is detected — i.e. hours after the fact. Structurally unusable for "confirm wake now."
- Battery: Google's own claim is that centralizing the model in Play Services (one detector serving
  every subscribed app) is the efficiency win, not a hardware sensor-hub offload. No sampling rate,
  duty-cycle, or benchmark numbers are published anywhere in the docs or the announcement post.
- **Deprecation signal**: the [Sleep API codelab](https://developer.android.com/codelabs/android-sleep-api)
  is explicitly marked *"deprecated and will be removed soon"* with **no replacement named**. The
  API reference page itself carries no deprecation notice, so it's not confirmed dead, but building
  new reliance on it today is a bet on an API Google has visibly stopped investing docs/tooling in.

**Verdict**: worth adding as one more *advisory* input (same pattern Cron already uses for Health
Connect stages — informs, doesn't drive the FSM), cheap to wire up since the permission is already
granted. Not worth making authoritative: too coarse in cadence, and of uncertain long-term support.

## 3. The clinical foundation: actigraphy algorithms, and why they need future data

[Cole-Kripke 1992, Sadeh 1994 — reviewed in "On the Unification of Common Actigraphic Data Scoring
Algorithms," PMC8472753](https://pmc.ncbi.nlm.nih.gov/articles/PMC8472753/)

Every consumer sleep tracker's motion-based classifier is a descendant of wrist-actigraphy research
from clinical sleep medicine. The two most-cited:

**Cole-Kripke** — per 1-minute epoch `n`, using a 7-epoch window centered on `n`:

```
D[n] = P × Σ(i=-2..4) W(i) × X[n-i]
D[n] > 1  →  wake
otherwise →  sleep
```

**Sadeh** — per 1-minute epoch, using an 11-epoch window:

```
PS[n] = C1 + C2×MEANW5 + C3×NAT + C4×SD(-6) + C5×LOG
PS[n] > 0  →  sleep
otherwise  →  wake
```

The detail that matters most for Cron: **both require future data** — Cole-Kripke looks 4 minutes
ahead, Sadeh looks 6 minutes ahead. They are fundamentally *non-causal, retrospective smoothers*,
not real-time classifiers. This is exactly why Google's own daily `SleepSegmentEvent` is
next-day/retrospective, and it's the underlying reason any sleep/wake decision needs a **debounce
window** rather than firing instantly on a single reading — a single minute of stillness or a
single movement is noise; the signal only becomes trustworthy once you can look a few minutes to
either side of it. Cron's `OUT_OF_BED_CONFIRM_THRESHOLD` (90s of continuous interactivity) and the
5-minute onset recheck interval are doing the same job as these algorithms' windowing, just with a
simpler, causal (no-lookahead) approximation.

## 4. Academic phone-only systems: the error bars are wide even in dedicated research

- **Toss 'N' Turn (TNT)** — an Android research app using accelerometer, screen on/off, ambient
  light, microphone, and battery state over a 10-minute sliding window (i.e. the same sensor family
  Cron already reads). Reported errors: **±35 min** for bedtime, **±31 min** for wake time, **±49
  min** for total sleep duration, even with all five signals fused.
- **iSleep** — microphone-only (movement + breathing + cough sounds), a different tradeoff:
  no accelerometer needed, but requires an always-recording mic all night (the same privacy/battery
  cost the original Gemini summary hand-waved away as "you can set privacy aside").

Takeaway: a phone sitting somewhere near a sleeping person, using only motion/light/screen/battery
signals, tops out at **tens-of-minutes accuracy** in the literature — not because the algorithms are
bad, but because a phone is a poor proxy for body movement unless it's physically coupled to the
body or the mattress. This matters for calibrating expectations: some of what reads as "the app is
bad at this" may be the inherent ceiling of phone-only sensing, not a fixable bug.

## 5. Sleep as Android — the closest real "mainstream, phone-only, no wearable" product

[docs.sleep.urbandroid.org](https://docs.sleep.urbandroid.org/sleep/awake_detection.html) — the
single most directly comparable implementation, since it's shipped, widely used, and phone-only by
default (wearables are optional add-ons, not required).

**The load-bearing detail Cron's design doesn't have**: Sleep as Android requires **the phone to be
placed on the mattress**, not the nightstand. That's not an incidental setup instruction — it's the
entire reason its accelerometer signal is usable at all. A phone on a nightstand never feels body
movement; a phone on the mattress does, because the mattress transmits it. Their own docs rank bed
surfaces by how well they transmit movement (wearable > spring mattress > foam), which only makes
sense if the phone's accelerometer is the primary movement sensor, not a secondary heuristic.

**Awake detection is a raw accelerometer magnitude threshold, not a screen-interaction timer**:

| Sensitivity | Phone threshold | Wearable threshold |
|---|---|---|
| Low | ~0.25 G | ~0.4 G |
| Medium | ~0.15 G | ~0.25 G |
| High | ~0.1 G | ~0.2 G |

This is the direct answer to your "unlocks for 10 seconds then goes back in the pocket" complaint:
Sleep as Android doesn't require sustained screen interaction at all — a movement burst above the
G-force threshold *is* the wake signal. Screen-on is a supplementary, independent signal, and even
there they gate it more intelligently than a fixed constant: they require screen-on time to exceed
**the device's own configured display-timeout**, not an app-chosen constant like Cron's fixed 90s —
so a phone with a 15s timeout and one with a 5-minute timeout aren't held to the same bar.

Other signals layered in, each independently gating a wake decision (not required together):
- **Light**: >60 lux at medium sensitivity (same idea as Cron's dark-gate, just used bidirectionally
  — dark confirms sleep, light confirms wake).
- **Heart rate** (wearable only): current HR > ~1.15× the night's median.
- **Sound**: talking/crying detection, deliberately time-boxed to "45 min after tracking starts"
  through "1h before the alarm," specifically to avoid morning-routine noise near the alarm being
  misread as a mid-night wake.

## 6. Low-power architecture patterns Android actually offers

The instinct to avoid "constantly running and listening" is correct, and Android has purpose-built
primitives for it that don't require a live microphone or continuous main-CPU polling:

- **Hardware sensor hub / DSP co-processor**: on most modern SoCs, activity recognition and
  accelerometer batching run on a low-power co-processor (e.g. Hexagon DSP), not the AP — the main
  CPU stays suspended and is only woken when a threshold crosses. This is *why* the Activity
  Recognition Transition API Cron already uses is cheap: it's evaluated off the main CPU on
  supporting hardware, not because of anything Cron itself does.
- **Sensor batching** (`SensorManager.registerListener(..., maxReportLatencyUs)`): the hardware FIFO
  buffers accelerometer samples and delivers them in a burst, waking the AP far less often than
  continuous delivery would. Useful if Cron ever wants raw accelerometer magnitude (à la Sleep as
  Android's G-force threshold) without paying for continuous AP wake-ups.
- **`TYPE_SIGNIFICANT_MOTION`**: a hardware, near-zero-power, **one-shot** trigger that fires once
  when the device experiences motion "significant" enough to indicate the user changed context (not
  just table vibration). It self-disarms after firing and must be re-registered. Coarse — no
  magnitude or duration data — but essentially free, and a good *first-pass gate*: "did something
  happen at all" before spending any more expensive evaluation.

None of these require the microphone, and none require Cron's foreground service to poll
continuously — they're designed specifically for "wake me only when something changes."

## 7. Comparison table

| | Cron today | Android Sleep API | Sleep as Android | Actigraphy (Cole-Kripke/Sadeh) |
|---|---|---|---|---|
| Onset signal | screen-off + dark, 20–40 min | accelerometer + light, on-device ML | accelerometer on mattress + dark | accelerometer counts/epoch |
| Wake signal | unlock held 90s | daily retrospective segment | **accel. magnitude threshold** (no unlock needed) | accel. counts, ±4–6 min lookahead |
| Real-time capable | yes (event-driven) | classify: ~10min; segment: next-day only | yes | no — inherently retrospective |
| Phone placement assumption | nightstand (implicit) | unspecified | **mattress (required)** | worn on body |
| Battery approach | screen/light listeners + AR Transition API | centralized Play Services model | continuous accel. (typically batched) | offline analysis |
| Reported accuracy | — (no measured ground truth in-repo) | undisclosed | not published, but production-hardened | ±tens of min even with 5 fused sensors (TNT) |
| Maintenance risk | fully owned | codelab marked deprecated, no replacement named | third-party, actively maintained | n/a — literature, not an API |

## 8. Recommendations for Cron, prioritized

1. **Fix the structural wake-detection gap first — it's the highest-leverage, lowest-risk change.**
   Add a raw accelerometer-magnitude check (Sleep as Android's model: a threshold in the ~0.1–0.25G
   range depending on desired sensitivity) as an *additional, independent* trigger for
   `OutOfBedConfirmed`, alongside the existing unlock-based one — not replacing it, since unlock is a
   zero-false-positive signal when it does fire. This directly addresses "unlocks for 10s then
   pockets it": a movement burst confirms wake even without a sustained unlock.
2. **Use batched sensor delivery, not continuous polling**, for the new accelerometer check
   (`maxReportLatencyUs`), and consider gating it behind `TYPE_SIGNIFICANT_MOTION` firing first as a
   near-free pre-filter — only pay for a magnitude read after something already moved. This keeps
   the "not power hungry" constraint intact; it does not require the microphone or an always-on
   raw-sample loop.
3. **Reconsider the nightstand-vs-mattress assumption.** If Cron doesn't already say so, tell users
   the accelerometer signal (new or existing) is only meaningful if the phone is on the bed, not a
   nightstand — this is the single largest lever in whether phone-only motion sensing works at all,
   per §5, and it's a documentation/onboarding fix, not a code fix.
4. **Add `SleepClassifyEvent` as one more advisory input**, following the exact pattern already used
   for Health Connect stage overlays (`HcStageUpdate` — informs AI replan timing, never drives FSM
   transitions directly). Cheap since `ACTIVITY_RECOGNITION` is already granted. Don't lean on
   `SleepSegmentEvent` — its next-day cadence is structurally useless for live session state, and the
   API's deprecation signal (§2) argues against depending on it for anything load-bearing.
5. **Calibrate expectations, not just code.** §4's ±30–50 minute error bars from a dedicated
   5-sensor research system are a real ceiling for phone-only sensing without a worn device or a
   phone-on-mattress placement. Some of the "sometimes doesn't detect sleep at all" reports may be
   inherent to the sensing approach rather than a tunable-threshold bug — worth checking Cron's own
   session history (`SessionRepository`) for which failure mode actually dominates before assuming a
   bigger rewrite is warranted.

## Sources

- [Sleep API overview — developers.google.com](https://developers.google.com/location-context/sleep)
- [Low-Power Sleep Tracking on Android — Android Developers Blog, 2021](https://android-developers.googleblog.com/2021/02/low-power-sleep-tracking-on-android.html)
- [(Deprecated) Android Sleep API Codelab](https://developer.android.com/codelabs/android-sleep-api)
- [SleepClassifyEvent reference](https://developers.google.com/android/reference/com/google/android/gms/location/SleepClassifyEvent)
- [On the Unification of Common Actigraphic Data Scoring Algorithms — PMC8472753](https://pmc.ncbi.nlm.nih.gov/articles/PMC8472753/)
- [40 years of actigraphy in sleep medicine — npj Digital Medicine](https://www.nature.com/articles/s41746-023-00802-1)
- [Toss 'N' Turn: Smartphone as sleep and sleep quality detector — ResearchGate](https://www.researchgate.net/publication/266655574_Toss_'N'_turn_Smartphone_as_sleep_and_sleep_quality_detector)
- [iSleep: A Smartphone System for Unobtrusive Sleep Quality Monitoring — ACM](https://dl.acm.org/doi/pdf/10.1145/3392049)
- [Sleep as Android — Automatic sleep tracking](https://docs.sleep.urbandroid.org/sleep/automatic_sleep_tracking.html)
- [Sleep as Android — Awake detection](https://sleep.urbandroid.org/docs/sleep/awake_detection.html)
- [Sleep as Android — Sensors](https://docs.sleep.urbandroid.org/sleep/sensors.html)
- [Android sensor hub / context hub — source.android.com](https://source.android.com/docs/core/interaction/sensors/sensor-stack)
