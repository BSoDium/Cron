# Handoff — Cron (BSoDium/Cron)

Written 2026-09-12 by a Claude Code cloud session for a local agent picking up the same work.
The cloud session cannot relocate itself onto your laptop — this file plus the live GitHub state
below is the full transfer. Read this, then verify current state yourself (branches/PRs may have
moved since this was written) before acting.

## Immediate priority: merge 4 ready PRs, in this exact order

All four are green, `mergeable_state: clean`, not draft, as of this writing. None has landed yet —
`main` is still at `0166704`. Local build/test was never possible from the cloud session (its
network policy blocks `dl.google.com`/`maven.google.com` — see `docs/perf-profiling-plan.md`), so
**verification so far is CI-only**. Doing a real local `./gradlew :app:assembleDebug
:app:testDebugUnitTest` before merging each one is genuinely valuable now that you have a laptop —
it hasn't been done yet for any of these.

**Merge order matters** — each is stacked/sequenced on the ones before it:

1. **#200** — `fix/flaky-evening-plan-test` → `main`
   https://github.com/BSoDium/Cron/pull/200
   Fixes a real race in `EveningPlanReceiverTest` (asserted on a side effect before the
   `goAsync()` coroutine that sets it had necessarily run). Also adds
   `testLogging { exceptionFormat = FULL }` to `app/build.gradle.kts` so a failing test's stack
   trace prints straight into the CI log instead of only into the (download-only) HTML report.
   This is why #196/#197 stopped being blocked by a flake — see below.

2. **#196** — `fix/serialize-session-fsm-events` → `main` (currently based on `main`, but was
   red until the #200 fix was cherry-picked onto it — it carries that same commit now)
   https://github.com/BSoDium/Cron/pull/196
   Fixes #153. Two session-row race conditions: (a) every `SessionRepository` setter did a
   full-row `findById → copy → update`, so concurrent writers to different columns could clobber
   each other — converted to targeted `@Query` UPDATEs; (b) `SessionFsm.onEvent` had no
   synchronization across the multiple independently-constructed `SessionFsm` instances in the
   app, so near-simultaneous events raced on a stale status snapshot — added a companion-object
   `Mutex`.

3. **#197** — `fix/sleep-session-active-window` → `main` (currently based on `#196`'s branch;
   **its PR base should auto-flip to `main` once #196 merges** — double check this actually
   happened before merging #197, GitHub does this automatically but confirm)
   https://github.com/BSoDium/Cron/pull/197
   Fixes #191, resolves half of #179. Implements `docs/sleep-session-lifecycle-plan.md` (#192):
   gives a sleep session a bounded "active window" (earlier of `hardLatest + grace` or
   last-confirmed-awake + grace) so a daytime nap or an abandoned session can't be
   misinterpreted as still-asleep indefinitely. Adds `SessionExpiryScheduler`/
   `SessionExpiryReceiver` (self-closing exact alarm, mirrors `HardLatestScheduler`). Also carries
   the #200 fix (cherry-picked).

4. **#199** — `fix/alarm-over-keyguard` → `main`
   https://github.com/BSoDium/Cron/pull/199
   Closes #177. `AlarmActivity.onCreate` was calling `KeyguardManager.requestDismissKeyguard`,
   which raises the lock-screen credential prompt *on top of* the ringing alarm on a
   secure-lock device — hiding the dismiss control. Removed; three other mechanisms already
   legitimately show the activity over the keyguard (manifest `showWhenLocked`,
   `setShowWhenLocked`/`setTurnScreenOn`, the deprecated window flags kept for API 26 since
   `minSdk = 26` and those setters are API 27+). Extracted `ALARM_WINDOW_FLAGS` with a test
   asserting `FLAG_DISMISS_KEYGUARD` (the other route to the same bug) stays unset.
   **⚠️ This one needs actual on-device verification** — set a secure lock method, lock the
   screen, let an alarm fire, confirm the alarm is interactive and dismissible without
   authenticating first. No automated test can check this (Robolectric's
   `ShadowKeyguardManager.requestDismissKeyguard` doesn't model observable state change — verified
   this by decompiling the shadow class rather than write a vacuous test).

Independent of the above and **not urgent**, but also open and green:
- **#195** — `fix/timeline-markdown-and-navbar-padding` → `main`. Two small independent fixes
  (#185 nav-bar clipping token, #193 raw `**SUMMARY:**` markdown leak). Not stacked on anything.
  Never merged despite being ready since 2026-09-05 — just fell down the priority list under
  #196/#197/#199/#200. Same "no local verification yet" caveat.

## Known flaky test — NOT fully fixed

**#198** tracks two CI flakes on unrelated pre-existing tests (not caused by any of the above
diffs — verified with `git diff --stat` against each PR at the time):
- `EveningPlanReceiverTest.fires_rearms_tomorrow_and_starts_the_sleep_session_service` — **fixed**
  by #200 (real race, root-caused, see above).
- `HomeViewModelTest.streaming_partial_overrides_db_thread_and_marks_running_then_falls_back` —
  **still unfixed**. It never reproduced during any of the CI runs on #196/#197/#199/#200, so
  there's no fresh evidence to root-cause it against. #200 added `testLogging { exceptionFormat =
  FULL }` specifically so the *next* occurrence prints the failing assertion's stack trace into
  the CI log (previously it only showed `AssertionError at Assert.java:87`, JUnit's internal
  `fail()` frame, useless on its own). If it fires again, read that log — don't guess.

## Two new issues filed this session — not investigated further, no code written

**#202 — Raw deliberation renders as the answer instead of the thinking process**
https://github.com/BSoDium/Cron/issues/202 (bug, area:sleep)
Reported by the user from a live device screenshot: a replan turn's answer pane showed the
model's step-by-step arithmetic and a mid-stream "Actually… let me use…" self-correction instead
of a terse decision. Likely root cause identified by code reading (**not confirmed against real
data** — no device access): `AiThreadMapper.answerStartOf` in
`app/src/main/java/fr/bsodium/cron/ui/screens/home/AiThreadMapper.kt` splits process-vs-answer at
**whole-block** granularity — it finds the block containing the `SUMMARY:` marker and treats
*everything in that block* as the answer, stripping only the marker line itself. If the model's
`SUMMARY:` line isn't the first line of its Text block, any reasoning preceding it in the same
block leaks into the answer verbatim. Every existing `AiThreadMapperTest.kt` case has `SUMMARY:`
as the first line of its block, so this path is untested.
The issue explicitly flags a second, non-exclusive possibility: the model itself may have emitted
deliberation as visible `Text` blocks (rather than `Thinking` blocks) after the marker, which
would be a prompt problem (`SystemPrompts.kt`'s `OVERNIGHT_REPLAN`), not a mapper problem.
**To disambiguate: dump the raw `contentJson` for the offending turn from the `ai_messages` table
on a device** and check whether the leaked text is (a) in the same Text block as `SUMMARY:`,
before the marker → mapper bug, or (b) in separate Text block(s) after the marker → prompt bug.
Needs someone with device/DB access; couldn't be done from the cloud session. Fix the mapper
regardless (split the marker-bearing block at the marker line instead of treating it atomically)
— but whether the prompt also needs fixing depends on this check.

**#201 — Replace the mock-mode FAB chevron popup with a modal bottom sheet**
https://github.com/BSoDium/Cron/issues/201 (enhancement)
User-requested improvement, explicitly low priority (debug-only surface, dev tooling not
user-facing). The chevron next to the Re-plan FAB opens
`app/src/debug/java/fr/bsodium/cron/ui/components/MockModeChevron.kt`'s hand-rolled `Popup` with a
custom `AboveAnchorPositionProvider`, containing two `DropdownMenuItem`s each stuffing a
title+description into a `Column` — cramped by design (`widthIn(min = 166.dp)`). Replace with
`ModalBottomSheet`. Implementation notes already in the issue body (state currently mirrored in 3
places on click; consider a trailing-check list or `ToggleButton` group instead of the current
`secondaryContainer` background-highlight pattern; keep `shadowElevation = 0.dp` per CLAUDE.md's
flat-design rule).

## GitHub project board — could not be done from this session

Per `CLAUDE.md`: every new issue must be added to project #5 ("Open-source development", owner
BSoDium) with Status/Priority/Size/Category, **unless** it closes an issue already on the board
via a closing keyword (then only the issue goes on the board, not the PR — avoids duplicate
tracking). This cloud session has no `gh` CLI and no GitHub Projects v2 MCP tool, so **none of
#198, #201, #202 have been added to the board**. My suggested field values, for whoever does this:

| Issue | Status | Priority | Size | Category |
|---|---|---|---|---|
| #198 | Backlog | P2 | S | Bug |
| #201 | Backlog | P4 | S | Refactor |
| #202 | Backlog | P2 | S | Bug |

## Environment notes for the local agent

- **This cloud session could reach `repo1.maven.org` but not `dl.google.com`/`maven.google.com`**
  — meaning no local Gradle build was possible at any point in this session's work. Every PR
  above was written, reviewed, and pushed on code-reading + CI alone. Your laptop presumably has
  no such restriction — running the actual build/test/lint suite locally before merging each PR is
  strictly more verification than has happened so far and is worth doing.
- CI workflow gotchas already discovered (see `.github/workflows/`): the `build` job is gated
  `if: github.event.pull_request.draft == false` — a draft PR gets **zero** code CI. `pr-apk.yml`
  (builds a debug APK for manual install) has the same gate. Roborazzi screenshot recording
  (`recordRoborazziDebug`) is a separate task from `testDebugUnitTest` and does not run in the
  default `build` job.
- No committed Roborazzi baselines exist yet (tracked in #147) — every screenshot capture today is
  first-look judgment, not a diff against an approved prior state.
- `docs/` has several living plan documents worth reading before touching adjacent code:
  `docs/sleep-session-lifecycle-plan.md` (#197's spec), `docs/perf-profiling-plan.md` (#189, spec
  only — needs a real device/emulator to implement, never done), `docs/screenshot-testing.md`,
  `docs/expressive.md`, `docs/compose-gotchas.md`, `docs/performance.md`, `docs/color-roles.md`.
- `CLAUDE.md` at repo root has the full house style (flat design / no shadows, Expressive motion
  APIs, token-based spacing/type, Kotlin style rules, comment conventions, the branch/commit/PR
  workflow). Read it before writing code — it's enforced in review and partly in CI
  (`checkFileLength`, `checkAnimationPreviews`, lint).

## Everything else open (for context, not urgent)

Long-running backlog, roughly by area — none blocking, none started:
- **area:sleep**: #191 (partially fixed by #197 — the other half, false early onset, is
  untouched), #182 (stop-tracking wording/tracking gap), #181/#180 (notification actions), #179
  (broader sleep-tracking misfire investigation, #197 fixes part of it), #178 (Haiku without
  extended thinking on replan — plausibly related to #202's bad-math symptom, worth cross-checking
  if you pick up #202), #159 (batch of low-confidence audit findings).
- **area:home / type:perf**: #187 (unbounded timeline query), #186 (icon/socket desync during
  animation), #176 (timeline jank, needs #189's profiling infra to properly diagnose), #184 (blank
  PlanDetailScreen on old AI-run tap).
- **area:design**: #183 (PageAppBar title overflow).
- **type:perf**: #188 (spec'd in #189, needs a physical device to implement).
- #158 (alarm sound is one-shot, not looping) — old, unlabeled priority, worth a look.

---
🤖 Generated with [Claude Code](https://claude.com/claude-code)

https://claude.ai/code/session_01VgYGQ1aeK9kisncfRd2cXb
