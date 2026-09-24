# Sleep detection — adaptive, self-correcting architecture

Companion to [`sleep-detection-research.md`](sleep-detection-research.md). That doc surveys the
field; this one is the actual proposed design: a phone-only detector that combines cheap signals in
tiers, learns per-user thresholds from the app's own hindsight (not from Google's daily API), and
self-corrects with concrete safeguards against drift.

## 0. Where this disagrees with the original framing

1. **Calibrating after the fact is right. `SleepSegmentEvent` as the calibration source is not.**
   It shares Cron's own blind spots (same accelerometer/light inputs — wrong in the same direction
   when Cron is wrong), it's whole-night only (can't attribute a 25-minute error to any one
   threshold), and it's unreliable/possibly deprecated. The real calibration source is **Cron's own
   full-night observation log, re-read the next morning with hindsight** — it exists every night,
   resolves individual events, and depends on no external API. Health Connect wearable data is a
   better external label when present; an optional one-tap morning confirmation is better still.
   `SleepSegmentEvent` is at most a weak tie-breaker.
2. **Learn distributions of behavior, not timestamps.** "Nudge settings to close the gap between
   real-time and next-day timestamps" has no unique answer — you can't tell which threshold caused a
   30-minute miss. Instead: relabel each night's episodes in hindsight (glance vs. real wake, awake
   vs. asleep), accumulate labeled episodes per user, and recompute thresholds from those
   collections. Timestamp error is used only to monitor quality and trigger safeguards.
3. **The users who need personalization least are the ones who generate the least data for it** —
   a sparse interactor produces almost no glance/wake episodes, so their unlock threshold just stays
   at the default, which is fine because that threshold barely matters for them. What actually helps
   them is placement-awareness and a learned typical bedtime/wake-time window (§4).
4. **Wake should key off what the accelerometer sees in the 1–2 minutes after an unlock, not how
   long the unlock lasted.** "10s unlock then pocketed, walking away" and "10s unlock then back in a
   drawer" are identical up to screen-off; they diverge in the seconds after.
5. **Placement decides what's knowable.** A phone in a drawer gets no light, no motion, no usable
   mic. No threshold recovers this — the design should detect and disclose that state, not paper
   over it.

## 1. Signal set, in tiers (cheap gates expensive)

**Tier 0 — always on while a session runs, near-zero cost:**

| Signal | Status | Role |
|---|---|---|
| Screen on/off/user-present | exists | primary onset timer |
| Ambient light | exists | dark gate — **only meaningful when placement is Open** |
| Charging state + connect/disconnect | partial | unplugging during the night/morning is strong wake evidence |
| Activity Recognition transitions | exists | now logged always, not just post-onset (see F2, §5) |
| `TYPE_SIGNIFICANT_MOTION` | new | free, one-shot, hardware-level "something moved"; primary gate into Tier 1 |
| One proximity + lux read at each screen-off / dismiss | new | placement classifier: a >1s, near-instant collapse to ≈0 lux+covered means Enclosed (pocket/drawer), vs. a gradual room-dimming |
| `SCREEN_OFF_TIMEOUT` setting | new, read once | seeds the default unlock threshold instead of a fixed constant |
| Alarm ring/dismiss/snooze | exists | the single most reliable wake anchor already in the app |
| `SleepClassifyEvent` | new, optional | one more feature in the evidence model (§2) — if it adds nothing, its learned weight decays toward irrelevant |

**Tier 1 — short accelerometer windows, opened only by a Tier 0 trigger** (`MotionProbe`): 25Hz,
batched (`maxReportLatencyUs`≈10s), 60–120s windows, opened on post-onset unlock, significant motion,
AR-WALKING entry, alarm dismiss, or a 20s bedtime placement check. Produces peak acceleration,
variance, a gait-energy score, and orientation change. No continuous overnight sampling by default —
that's reserved for an opt-in "phone on the mattress" smart-wake mode later (Phase 5).

**Tier 2 — microphone, only after alarm dismiss, only where Tiers 0–1 can't see:** Android 11+
blocks a background-started foreground service from using the mic, but Cron's `AlarmActivity` is
already foreground when the alarm rings, so promoting to `FOREGROUND_SERVICE_TYPE_MICROPHONE` at
that moment is legal. 5s RMS+onset-event sampling every 30s for up to 20 minutes post-dismiss,
feature-only (nothing recorded), used only when placement is Enclosed or Tier 1 was ambiguous. This
answers the one question nothing else can for a drawer-phone user: "got up, or dismissed and went
back to sleep?" Not viable all night (service-type restriction + CPU wake cost).

**Label-only inputs**, never drive real-time state: Health Connect sleep sessions (excluding Cron's
own writes — otherwise it's circular against `SleepSessionWriter`), `SleepSegmentEvent`, an optional
morning confirmation tap.

## 2. Per-user personalization

**Stored per user** (`SleepProfile`): capped ring buffers (60 samples, 60-day decay) of glance-unlock
durations, awake-dark-gap durations, sleep-time lux (Open placement only), glance motion-peak noise
floor, and circular stats for typical bedtime/wake clock time — plus Beta-distribution pseudo-counts
per wake-evidence feature (unplugged, significant-motion, gait, AR-walking, light-rise, repeat-unlock,
acoustic activity, low-confidence classify).

**Thresholds are windowed empirical quantiles pulled toward the shipped default**, not an EWMA and
not a parametric Bayesian model:

```
θ = clamp( (k·θ_default + n'·quantile_p(buffer)) / (k + n'), lo, hi ),  n' = min(n, 60),  k = 10
```

Quantiles beat an EWMA here because a threshold is a statement about a distribution's *tail*, and
glance durations are heavy-tailed — one long glance would drag a mean-tracking EWMA for weeks. `k=10`
against a cap of 60 keeps the default at ≥14% permanent weight (safeguard #2, §3).

**Wake-evidence weights are per-feature likelihood ratios from Beta counts, with exponential
forgetting** (λ=0.95/night → ~20-night effective memory, adapts to a routine change in 2–3 weeks
without any single night dominating), clamped to [1/20, 20] so no one feature can decide alone.

**Wake decision**: naive-Bayes log-odds over grouped features (correlated signals like
gait/AR-walking/significant-motion take the max within their group, not a sum, to avoid
double-counting one physical event three ways), fired when P(wake) ≥ 0.9. **That 0.9 cutoff is
fixed, not learned** — learning both the likelihoods and the decision boundary lets the system
converge on a boundary that produces zero apparent errors on its own biased data, which is exactly
the degenerate failure mode to avoid. The existing sustained-unlock path stays as a separate,
near-zero-false-positive direct trigger alongside this.

**Cold start**: day-0 behavior equals today's `SleepTuning` defaults exactly, since the blend formula
gives 0% weight to an empty buffer. Nothing "switches on" at a calibration milestone — personalization
strength ramps continuously as the buffer fills.

## 3. The self-correction loop

Runs once per completed session (`CalibrationWorker`, ~3h after completion so Health Connect/Sleep
API data has time to land), reconstructing hindsight labels from the raw observation log — not from
`SleepSegmentEvent`.

**Label sources, by confidence**: user morning confirmation (1.0) > Health Connect from a real
wearable, excluding Cron's own writes (0.9) > Health Connect from a Medium-trust origin (0.5) >
hindsight "unlock followed by 60+ min of silence" = glance (0.9) > hindsight "unlock followed by more
activity within 30 min" = real wake (0.85) > hindsight night-level onset, upper-bound only since
phone hindsight can't see the falling-asleep delay (0.6) > `SleepSegmentEvent`, successful status
only (0.4, and it only ever adds +0.1 to an existing hindsight label on agreement — disagreement does
nothing). Excluded: episodes within 10 min of an alarm, episodes caused by Cron's own AI replan,
nights with a timezone/DST shift, nights with no 3h+ screen-off gap at all.

**Only episode labels drive learning** (they add samples to the buffers/Beta counts above).
Night-level timestamp error is monitored, and used only to trigger the safeguards below — never
used as the thing being adjusted, since a single night-level error can't be attributed to one setting.

**Safeguards against drift/degeneration:**
1. Hard `[lo, hi]` bounds per setting in `SleepTuning` (e.g. unlock threshold ∈ [30s, 180s]).
2. Permanent ≥14% default weight (from the quantile blend formula itself).
3. Max ±15% change per setting per night.
4. Divergence check: >3h disagreement between hindsight and causal result ⇒ night marked anomalous,
   nothing learned from it; >60min disagreement between two ≥0.5-confidence sources ⇒ that value is
   contested and skipped.
5. **Replay-before-adopt**: every candidate parameter set is replayed against the last 14 nights of
   logged raw observations (possible because the decision rules are pure functions), scored with an
   asymmetric cost (false wake=3, missed wake=1, false onset=2, missed onset=1 — matching the
   existing code's stated false-negative bias). Adopted only if cost ≤ current. This is the main
   protection against "relaxes into noise" or "tightens until it never fires."
6. Rolling 14-night health check on recall/precision against ≥0.8-confidence labels; below
   threshold ⇒ revert to last-good snapshot and freeze learning 7 nights; two freezes in a row ⇒
   revert fully to shipped defaults.
7. No label that night ⇒ nothing changes except the Beta decay still applies (old evidence ages out
   at the same rate regardless of whether new labels arrive).

## 4. The sparse interactor, walked through

Unlock at 22:50 for 10s, phone into a drawer, no contact until a 07:00 alarm dismiss.

- **22:50 screen-off**: proximity covered + lux collapses to ~0 within 1s ⇒ placement = Enclosed. In
  Enclosed placement the dark gate is void (a drawer is dark at 6pm too — this is today's drawer
  false-onset bug). Onset instead requires screen-off ≥1.5×threshold **and** the clock falling inside
  the user's learned bedtime window **and** no significant motion since screen-off.
- **Overnight**: if they get up without touching the phone, this is genuinely undetectable — no
  light, no motion, no mic signal reaches a phone in a drawer. If they carry the phone (e.g. to the
  bathroom), significant motion + gait + AR-walking fires and `OutOfBedConfirmed` correctly triggers,
  then re-arms on return.
- **07:00 dismiss**: already solved today — `AlarmDismissed` → Awake is accurate to the minute
  regardless of any of this.
- **07:01, back in the drawer — today's actual failure case**: currently a rearm + 15min screen-off
  (always dark in a drawer) triggers a false re-onset, risking an AI re-ring while the user is in the
  kitchen. Fixed by: capturing lux/proximity and running a 90s motion probe at the moment of dismiss
  (app is already foreground); walking during that probe confirms up and completes the session.
  If the phone goes straight back in the drawer with no motion, re-onset now needs *positive* sleep
  evidence (the mic's quiet-floor reading, if running) rather than firing on the mere absence of
  contrary evidence — or, without the mic, falls back to the user's own learned
  `postDismissResleepRate`: if hindsight shows this user essentially never goes back to sleep after
  dismissing, re-ring is suppressed for Enclosed placement, and the UI says so.

**Realistic ceiling for this user**: onset ±30–60min (matches the literature's phone-only error
bars, §4 of the research doc); final wake exact (alarm dismiss); got-up-vs-back-to-sleep solvable
with the mic, otherwise inferred from behavioral history; spontaneous pre-alarm wake and mid-night
wake-ups genuinely undetectable. The honest move is surfacing that limitation ("phone was enclosed;
wake-before-alarm detection unavailable tonight"), not pretending a threshold fixes it.

By contrast, the original "10s unlock then pocketed and walked off" case is fully solved by this
design: gait shows up in the post-screen-off probe within ~30–60s, confirmed by AR-walking, firing
`OutOfBedConfirmed` within ~2 minutes — unlock duration plays no role at all.

## 5. Integration notes and code findings this depends on

- **F1**: `DayPlan.detectedSleepWindow()` uses the onset *emission* timestamp (20–40+ min late vs.
  the already-stored `screenOffSince`), and returns `null` whenever a session ends without a clean
  `OutOfBedConfirmed` — which is every sparse interactor's night. Fix: onset = `estimatedOnset ?:
  screenOffSince`; wake = latest of `OutOfBedConfirmed`/final dismiss.
- **F2**: `ActivityRecognitionMonitor` currently drops all transitions before onset, and raw screen
  transitions are never persisted — hindsight reconstruction is impossible until there's a raw log.
- **F3**: `SleepSessionWriter` writes Cron's own detections into Health Connect; any calibration
  reader must exclude `dataOrigin == ownPackage`, not merely down-weight it as `DataOriginClassifier`
  does today.
- **F4**: `AmbientLightReader.isDark()` trivially passes inside a pocket/drawer, causing false onsets
  away from bed.

**`SleepTuning` changes role**: from "the value" to, per setting, a `(default, lo, hi)` triple. Debug
`fastOnset` overrides bypass personalization entirely, keeping the existing fast test loop. A new
`SleepParams` snapshot (default ⊕ profile-pulled-toward-default ⊕ clamp), frozen once per session by
`SleepParamsProvider`, feeds the existing monitor constructors — which already take thresholds as
parameters, so this is neither a multiplier layer nor a wholesale replacement.

**New components** (by responsibility): `ObservationLog` (append-only raw signal log, Room, 30-day
retention), `PlacementClassifier` (pure), `MotionProbe`, `WakeEvidenceMonitor` (the tiered
evidence-combination decision, pure/testable), `SleepApiMonitor`, `AcousticProbe`, `NightReconstructor`
(pure hindsight labeler), `SleepProfile`/`SleepProfileRepository`, `ProfileCalibrator` +
`CalibrationWorker`. `SessionFsm` transitions themselves are unchanged — only who emits
`SleepOnset`/`OutOfBedConfirmed` and with what evidence changes.

## 6. Phased rollout

| Phase | Scope | Standalone value |
|---|---|---|
| 0 — Measurement | `ObservationLog`; stop dropping pre-onset AR events (F2); fix `detectedSleepWindow` (F1); night-timeline debug screen | Yes — more accurate HC writes even with zero new detection logic, and answers which failure mode actually dominates before building further |
| 1 — Tiered wake trigger, fixed defaults | Placement classifier; `MotionProbe` gated by significant motion; `WakeEvidenceMonitor` with shipped-default likelihood ratios; placement-aware onset/re-onset | **Yes — most of the real-world gain is here**, with zero learning involved. Fixes both the pocket-walk case and the drawer false-re-onset case |
| 2 — Hindsight, shadow mode | `NightReconstructor` + `SleepProfile` accumulating + `CalibrationWorker` computing candidates but not applying them | Validates the labeler against real nights before anything depends on it |
| 3 — Apply personalization | `SleepParamsProvider` goes live with all §3 safeguards | Only worth it once Phase 2 shows labels are trustworthy |
| 4 — External labels | Health Connect as a teacher signal (excluding Cron's own writes); `SleepApiMonitor`; optional morning confirmation | Additive to Phase 3; helps wearable users most, Sleep API only marginally |
| 5 — Opt-in expensive sensors | Post-dismiss `AcousticProbe`; continuous on-mattress accelerometer for a smart-wake window | Mic resolves the drawer-user morning ambiguity; on-mattress mode only if light-sleep smart-wake becomes a roadmap item |

**Battery target**: no more than 1% added per night versus today, verified with Battery Historian
after Phase 1 — expected cost is a handful of short batched accelerometer windows plus, in Phase 5,
one bounded post-dismiss microphone window.
