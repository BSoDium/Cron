# Smart Alarm — Implementation Plan (On-Device)

> Agent-ready specification for the Cron revamp. Read in full before writing any code.
> Executable phasing lives at `~/.claude/plans/hey-dude-big-revamp-modular-fountain.md`.

---

## 0. Context & Starting Point

The project is a single Android app at `github.com/BSoDium/Cron`, package `fr.bsodium.cron`, that today:
- Reads Google Calendar via the device's `CalendarContract` ContentProvider (no OAuth — uses the signed-in system account)
- Calls Google Routes API on-device for commute time
- Computes a wake time deterministically (`CronOrchestrator`)
- Fires a local alarm via `AlarmManager.setAlarmClock`

The goal of this revamp: **replace the deterministic brain with Claude**, while keeping everything on-device. No backend, no MCP servers, no FCM. Claude reasons over a live sensor stream (Health Connect sleep stages, Activity Recognition, screen state, location) and uses Anthropic tool-use to drive the existing on-device clients (calendar, routes, geocoding) and schedule the alarm.

Why on-device:
- **Calendar** already works without OAuth via `CalendarContract`
- **Routes API key** is already domain-restricted and lives in `BuildConfig`
- **Anthropic API key** stays local in `EncryptedSharedPreferences`, user-supplied at onboarding (no leak risk in a public APK)
- Single-user app — no multi-device sync to justify a server
- WorkManager + AlarmManager already handle background scheduling without needing a server-driven push

The app retains its current package name `fr.bsodium.cron` to preserve Play Store identity.

---

## 1. Architecture Overview

```
┌─────────────────────────────────────────────────────────────────┐
│  SleepSessionService (foreground, type=specialUse)             │
│  Lifetime: evening trigger → out_of_bed_confirmed | dismissed   │
│                                                                 │
│  ScreenStateMonitor ─┐                                          │
│  ActivityRecognMon. ─┼──→ SessionFsm ──→ SessionRepository ─┐  │
│  HealthConnectPoll  ─┘     (state +        (Room append)     │  │
│  EveningPlanReceiver       transitions)                      │  │
│                                                              ▼  │
│  AlarmReceiver ─────────────────────────────────────→  AiTurnWorker
│  CalendarChangeReceiver ───────────────────────────→  (WorkManager,
│                                                       resumable)  │
│                                                              │  │
│  AnthropicClient ◀── TurnRunner ◀── ToolRegistry ◀───────────┘  │
│                          │              │                       │
│                          ▼              ▼                       │
│                  Anthropic API     Tool executors:              │
│                  (Messages +       ReadCalendar / Geocode /     │
│                   tool_use)        EstimateCommute(MultiMode) / │
│                                    SetAlarm                     │
│                                            │                    │
│                                            ▼                    │
│                                    AlarmScheduler               │
│                                    (mutable AI alarm)           │
│                                                                 │
│                                    HardLatestScheduler          │
│                                    (immutable safety floor)     │
└─────────────────────────────────────────────────────────────────┘
```

Key actors:
- **Sensors** feed events into `SessionFsm` via `SessionRepository.appendEventAndPlan(event)`. Append is atomic (`@Transaction`); AI replan is enqueued in the same transaction via WorkManager `UNIQUE_WORK`.
- **`AiTurnWorker`** runs the tool-use loop with full conversation persistence (`ai_messages` table). Process death mid-turn → on next start, resume from the last persisted message.
- **`SetAlarmTool`** is the only path through which an alarm gets armed. It clamps to `[now + 60s, hardLatest]` client-side; the AI can never push the alarm past the hard latest.
- **Two AlarmManager entries**: AI alarm (requestCode `100_000 + epochDay`, mutable) and hard-latest (requestCode `200_000 + epochDay`, immutable until session ends).

---

## 2. Data Models (Kotlin, kotlinx.serialization)

Under `fr.bsodium.cron.session.model.*`. All models are `@Serializable`.

### Triggers

```kotlin
@Serializable
enum class TriggerType {
    evening_plan,
    sleep_onset,
    hc_stage_update,
    mid_sleep_activity,
    out_of_bed_confirmed,
    wake_window_opportunity,
    alarm_dismissed,
    alarm_snoozed,
    calendar_change,
    hard_latest_fired,
}

@Serializable enum class SleepStage { awake, light, deep, rem }
@Serializable enum class ActivityType { still, walking, running, out_of_bed }
@Serializable enum class SignalConfidence { high, medium, low }
@Serializable enum class LocationSource { gps, last_known, home_address, unavailable }
```

### Events

```kotlin
@Serializable
data class SessionEvent(
    val trigger: TriggerType,
    val timestamp: Instant,
    val data: EventData,
)

@Serializable
@JsonClassDiscriminator("kind")
sealed class EventData {
    @Serializable @SerialName("evening_plan")
    data class EveningPlan(
        val timezone: String,
        val location: LocationPayload,
    ) : EventData()

    @Serializable @SerialName("sleep_onset")
    data class SleepOnset(
        val screenOffSince: Instant,
        val rearm: Boolean,
    ) : EventData()

    @Serializable @SerialName("hc_stage_update")
    data class HcStageUpdate(
        val stage: SleepStage,
        val source: String,
        val confidence: SignalConfidence,
        val recordStart: Instant,
        val recordEnd: Instant,
    ) : EventData()

    @Serializable @SerialName("mid_sleep_activity")
    data class MidSleepActivity(
        val activityType: ActivityType,
        val screenOn: Boolean,
        val durationSeconds: Int,
    ) : EventData()

    @Serializable @SerialName("out_of_bed_confirmed")
    data class OutOfBedConfirmed(val evidence: List<String>) : EventData()

    @Serializable @SerialName("wake_window_opportunity")
    data class WakeWindowOpportunity(
        val currentStage: SleepStage?,
        val windowStart: LocalTime,
        val windowEnd: LocalTime,
    ) : EventData()

    @Serializable @SerialName("alarm_interaction")
    data class AlarmInteraction(
        val snoozeDurationMinutes: Int? = null,
        val snoozeCount: Int,
    ) : EventData()

    @Serializable @SerialName("calendar_change")
    data class CalendarChange(
        val changeType: String,
        val eventId: String,
        val affectsFirstEvent: Boolean,
    ) : EventData()

    @Serializable @SerialName("empty")
    object Empty : EventData()
}

@Serializable
data class LocationPayload(
    val lat: Double,
    val lng: Double,
    val accuracyMeters: Float?,
    val source: LocationSource,
    val capturedAt: Instant,
)
```

### Instructions (AI output, materialized via SetAlarmTool)

```kotlin
@Serializable
enum class ActionType { set_alarm, cancel_alarm, send_brief, do_nothing, notify_warning }

@Serializable
data class Instruction(
    val action: ActionType,
    val alarmTime: LocalTime? = null,
    val wakeWindowStart: LocalTime? = null,
    val wakeWindowEnd: LocalTime? = null,
    val briefContent: String? = null,
    val reason: String,
    val issuedAt: Instant,
)
```

Note: `Instruction` is the AI's *decision*, but the actual alarm is armed via the `set_alarm` **tool call** (see §4.3). We do not parse a final JSON message; the tool-use flow is the contract.

### Session

```kotlin
@Serializable
enum class SessionStatus { planning, monitoring, awake, re_monitoring, complete }

@Serializable
data class DayPlan(
    val hardLatest: LocalTime,         // immutable after T0
    val wakeWindowStart: LocalTime,
    val wakeWindowEnd: LocalTime,
    val firstEventId: String?,
    val firstEventTime: Instant?,
    val firstEventLocation: String?,
    val commuteBufferMinutes: Int,
    val isFreeDayFallback: Boolean,
    val generatedAt: Instant,
)

@Serializable
data class SleepSession(
    val id: String,
    val date: LocalDate,               // calendar date of the MORNING
    val status: SessionStatus,
    val plan: DayPlan,
    val currentInstruction: Instruction,
    val events: List<SessionEvent>,
    val lastAiCallAt: Instant?,
    val snoozeCount: Int,
    val timezone: String,
    val createdAt: Instant,
    val updatedAt: Instant,
)
```

---

## 3. Database (Room)

Three tables. JSON columns hold polymorphic data via kotlinx.serialization.

### `sessions`

| Column | Type | Notes |
|---|---|---|
| `id` | TEXT PK | UUID |
| `date` | TEXT UNIQUE INDEXED | `YYYY-MM-DD`, morning date |
| `status` | TEXT | enum name |
| `plan_json` | TEXT | serialized `DayPlan` |
| `current_instruction_json` | TEXT | serialized `Instruction` |
| `last_ai_call_at` | INT NULLABLE | epoch ms |
| `snooze_count` | INT | default 0 |
| `timezone` | TEXT | IANA zone |
| `cached_first_event_sig` | TEXT NULLABLE | for calendar diff |
| `created_at` | INT | epoch ms |
| `updated_at` | INT | epoch ms |

### `session_events`

| Column | Type | Notes |
|---|---|---|
| `id` | INT PK auto |  |
| `session_id` | TEXT FK INDEXED |  |
| `trigger` | TEXT | enum name |
| `timestamp` | INT | epoch ms |
| `data_json` | TEXT | serialized `EventData` |

Append-only. Read order: by `id` ASC.

### `ai_messages`

| Column | Type | Notes |
|---|---|---|
| `id` | INT PK auto |  |
| `session_id` | TEXT FK INDEXED |  |
| `turn_index` | INT | groups messages of one AI turn |
| `role` | TEXT | `user` / `assistant` |
| `content_json` | TEXT | Anthropic content blocks |
| `created_at` | INT | epoch ms |

Persisted **after every block** during a tool-use loop. `AiTurnWorker` replays the conversation on resume.

---

## 4. AI Integration

### 4.1 Anthropic API

- Endpoint: `POST https://api.anthropic.com/v1/messages`
- Required headers: `x-api-key: <user-provided>`, `anthropic-version: 2023-06-01`, `content-type: application/json`
- Models:
  - **`claude-haiku-4-5`** — overnight replan loop (cheap, fast)
  - **`claude-sonnet-4-6`** — evening_plan turn (one-shot, deeper reasoning)
- `max_tokens`: 2048
- `tool_choice`: `auto` for evening_plan and most overnight turns; `{ "type": "tool", "name": "set_alarm" }` is used to force a final commit when the model is hedging.
- Budget per turn: 12 tool round-trips. After that, the loop emits a synthetic `notify_warning` and terminates.

### 4.2 HTTP client

OkHttp (already a dep) + kotlinx.serialization. Skip the official Anthropic Java SDK — Java-first builders + no kotlinx integration + +1.5 MB transitive deps. Thin wrapper at `ai/AnthropicClient.kt` (~300 LOC).

### 4.3 Tools

All tools return JSON-serializable result blocks. The `SetAlarmTool` is special — its execution arms the alarm and returns `{ "scheduled": true, "alarmTime": "..." }`. The model treats it like any other tool.

| Tool name | Input | Result |
|---|---|---|
| `read_calendar` | `{ start_iso, end_iso }` | `{ events: [{ id, title, start, end, location, allDay }] }` |
| `geocode_address` | `{ address }` | `{ lat, lng, formatted }` |
| `estimate_commute` | `{ origin_lat, origin_lng, destination, mode?, arrival_time_iso? }` | `{ duration_sec, distance_m }` |
| `estimate_commute_multi_mode` | `{ origin_lat, origin_lng, destination, arrival_time_iso }` | `{ transit: { duration_sec }, walk: { duration_sec }, ... }` |
| `set_alarm` | `{ time_iso, label, reason }` | `{ scheduled: true, clamped_to_hard_latest: bool, alarm_time: iso }` |

### 4.4 Tool-use loop

```
1. Build initial messages[] = [{ role: "user", content: prompt }]
2. POST /v1/messages with tools[] + messages[]
3. Receive response with content blocks:
   - if final stop_reason = "end_turn" → done
   - if stop_reason = "tool_use" → for each tool_use block:
        a. Execute tool, get result
        b. Append assistant message (tool_use) + user message (tool_result) to messages[]
        c. Persist both to ai_messages table
   - Loop back to step 2
4. Cap at 12 round-trips; emit notify_warning if exceeded
```

`TurnRunner` is the orchestrator. `ToolRegistry` maps tool names to executors.

### 4.5 System prompts

Two prompts in `ai/SystemPrompts.kt`:

**`EVENING_PLAN_PROMPT`** — for the once-nightly planning turn. Asks Claude to:
1. Call `read_calendar(tomorrow_start, tomorrow_end + 6h)` to inspect events
2. Identify the anchor event (skip all-day, virtual, back-to-back clustering — reason about which one actually requires physical presence)
3. If no anchor: use free-day fallback (`freeDayWakeStart` / `freeDayWakeEnd` from settings)
4. Resolve origin via the event's `location` from the T0 event payload
5. Call `estimate_commute` (or `estimate_commute_multi_mode` if dest is <1km)
6. Add 15-min minimum personal prep buffer
7. Call `set_alarm(time_iso, label, reason)` to commit

**`OVERNIGHT_REPLAN_PROMPT`** — for replans triggered mid-night. Receives the event log and current plan; decides whether to:
- Adjust the wake window (light-stage opportunity → bring alarm forward within window)
- Cancel the alarm (out_of_bed_confirmed before window → user is up)
- Re-arm (second sleep onset after out_of_bed → re-monitor)
- Hold (no-op)

Constraints encoded in both prompts:
- NEVER set `alarmTime` later than `hardLatest`
- Prefer light or REM over deep sleep for wake
- `snoozeCount >= 3` → bypass reasoning, set alarm to now + 5min
- All-day events are markers, not appointments — ignore
- Virtual events don't anchor unless they're the only events
- Location source rules (see §6.4)
- Confidence: high-source HC records (Garmin/Pixel Watch/Samsung Health) outweigh phone heuristics

### 4.6 Process-death resilience

`AiTurnWorker` is a `CoroutineWorker` with `setForegroundAsync` so the OS keeps it alive during the 5–30s call. On every received block, the conversation is persisted to `ai_messages` before the next API call. On resume, the worker:

1. Reads all `ai_messages` for `(sessionId, turn_index)` ordered by id
2. Reconstructs the `messages[]` array
3. If the last block was a `tool_use` without a matching `tool_result` → re-execute the tool, append result, continue
4. Otherwise → call the API again with the full conversation

This means: even if the user swipes the app away mid-replan, the next worker run picks up exactly where it left off.

---

## 5. Sensors

### 5.1 Screen state

Receivers registered **dynamically** in `SleepSessionService.onStartCommand`:
- `ACTION_SCREEN_OFF` → record timestamp
- `ACTION_SCREEN_ON` → compute off-duration, evaluate `SleepOnsetDetector`
- `ACTION_USER_PRESENT` → contributes to `OutOfBedDetector`

Static manifest registration is blocked for these since Android 8.

### 5.2 Activity Recognition

`com.google.android.gms:play-services-location`. Use the **Transition API** (not continuous) for battery cost.

Subscribe to transitions for `STILL`, `WALKING`, `RUNNING`. PendingIntent → `ActivityTransitionReceiver` (internal to the service).

Permission: `ACTIVITY_RECOGNITION` (runtime, requested at onboarding).

### 5.3 Location (T0 only)

Acquired once per session at the evening trigger. Never polled overnight (battery cost is not justified for a once-per-session value).

Fallback chain in `LocationProvider.acquireForEveningPlan()`:

1. `fusedLocationClient.lastLocation` if `< 2h old` → `source: gps`
2. `getCurrentLocation(PRIORITY_BALANCED_POWER_ACCURACY)` with 10s timeout → `source: gps`
3. Stored last-known in DataStore from a previous successful fix → `source: last_known`
4. User's registered home address (onboarding step) → `source: home_address`
5. Nothing available → `source: unavailable`

Every successful fix is persisted to DataStore for future fallback.

### 5.4 Health Connect

Polled, not push. Health Connect has no callback API for sleep stages as of 2026.

- **Availability check:** `HealthConnectClient.getSdkStatus()` — one of `SDK_AVAILABLE`, `SDK_UNAVAILABLE`, `SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED`. On `UNAVAILABLE`, fall back to phone-only mode.
- **Permission flow:** `PermissionController.createRequestPermissionResultContract()` — these are NOT Android runtime permissions. Launched from onboarding.
- **`HealthConnectPollWorker`** — periodic 15-min `WorkManager`, only when session is active. Reads `SleepStageRecord` since last poll.
- **Data origin classification** (`DataOriginClassifier`):
  - `com.garmin.android.apps.connectmobile` → `high`
  - `com.google.android.apps.fitness` (Pixel Watch / Fitbit feed) → `high`
  - `com.samsung.health` → `high`
  - Own package (phone heuristic synthesizing stages) → `low`
  - Other → `medium`

**Phone-only fallback:** if 90 min into the session no high-confidence stage has arrived, the system prompt for replans appends "phone-only mode; rely on screen+activity, do not request stage updates."

### 5.5 Detection logic

```kotlin
// SleepOnsetDetector
val SCREEN_OFF_THRESHOLD_MS = 20 * 60 * 1000L
val REARM_THRESHOLD_MS      = 15 * 60 * 1000L  // lower for re-arm after out_of_bed

fun check(state: SessionStatus, screenOffMs: Long, isStill: Boolean): Boolean {
    val threshold = if (state == SessionStatus.awake) REARM_THRESHOLD_MS else SCREEN_OFF_THRESHOLD_MS
    return screenOffMs >= threshold && isStill
}

// OutOfBedDetector — requires 2 of 3 signals
data class OutOfBedSignals(
    val stepsInLast2Min: Int,
    val walkingDurationSec: Int,
    val screenOnAndActive: Boolean,
)

fun evaluate(s: OutOfBedSignals): Boolean {
    var count = 0
    if (s.stepsInLast2Min > 20) count++
    if (s.walkingDurationSec >= 120) count++
    if (s.screenOnAndActive) count++
    return count >= 2
}
```

---

## 6. Scheduling & Lifecycle

### 6.1 Evening plan trigger

`AlarmManager.setAlarmClock` at the configured evening time (default 22:00 local). `setAlarmClock` is exempt from Doze and from `SCHEDULE_EXACT_ALARM` permission grant requirements (`USE_EXACT_ALARM` covers alarm-clock apps).

`EveningPlanScheduler.armNext()` is called from:
- `BootReceiver` (boot completed)
- `TimeZoneChangedReceiver` (`ACTION_TIMEZONE_CHANGED`)
- After every fire (re-arms next day)
- After settings change

DST is handled correctly because we re-arm using `LocalTime.of(22,0).atZone(systemDefault())` at arm time, so `ZoneRules` resolves spring-forward and fall-back.

On fire (`EveningPlanReceiver`):
1. Start `SleepSessionService`
2. Acquire location (§5.3)
3. Create today's session row with state `planning`
4. Emit `evening_plan` event
5. Kick off `AiTurnWorker`

### 6.2 Sleep session service

`SleepSessionService` (foreground, type `specialUse`).

**Manifest:**
```xml
<service android:name=".service.SleepSessionService"
         android:foregroundServiceType="specialUse"
         android:exported="false">
    <property android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"
              value="overnight sleep stage monitoring for adaptive alarm"/>
</service>
```

**Lifecycle:** start at evening trigger, stop on `out_of_bed_confirmed` OR alarm dismissed OR T+30 min past hard-latest.

**Notification:** low-importance channel `cron_session_channel`. Copy: "Cron is watching for the best moment to wake you." Action: "Stop tracking" → cancels session and disarms AI alarm (hard-latest stays).

**Battery exemption:** request `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` only at the end of onboarding. If denied, show a yellow banner on Home and run in reduced-reliability mode (Health Connect snapshots only, skip mid-sleep activity polling).

### 6.3 AlarmScheduler — two independent entries

```kotlin
object AlarmScheduler {
    fun scheduleAi(context: Context, instant: Instant, session: SleepSession) {
        val clamped = instant.coerceIn(
            Instant.now().plusSeconds(60),
            session.plan.hardLatest.atDate(session.date).atZone(ZoneId.of(session.timezone)).toInstant()
        )
        val pi = PendingIntent.getBroadcast(
            context,
            100_000 + session.date.toEpochDay().toInt(),
            Intent(context, AlarmReceiver::class.java).apply { putExtra(EXTRA_KIND, "ai") },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        am.setAlarmClock(AlarmManager.AlarmClockInfo(clamped.toEpochMilli(), null), pi)
    }
}

object HardLatestScheduler {
    fun arm(context: Context, session: SleepSession) {
        val pi = PendingIntent.getBroadcast(
            context,
            200_000 + session.date.toEpochDay().toInt(),
            Intent(context, AlarmReceiver::class.java).apply { putExtra(EXTRA_KIND, "hard_latest") },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val target = session.plan.hardLatest.atDate(session.date)
            .atZone(ZoneId.of(session.timezone)).toInstant()
        am.setAlarmClock(AlarmManager.AlarmClockInfo(target.toEpochMilli(), null), pi)
    }
}
```

Hard-latest is cleared only on `alarm_dismissed` or session `complete`. Even if the AI cancels the main alarm, hard-latest stays armed.

### 6.4 Location source reasoning (encoded in system prompt)

| `location.source` | `capturedAt` age | AI behavior |
|---|---|---|
| `gps` | any | Use as commute origin |
| `last_known` | < 2h | Use directly |
| `last_known` | 2–12h | Use, pad wake window +10 min, note uncertainty |
| `last_known` | > 12h | Treat as `home_address` fallback |
| `home_address` | n/a | Use, note assumption in `reason` |
| `unavailable` | n/a | Skip commute, apply +30 min buffer, surface in brief |

### 6.5 BootReceiver

Re-arms on `ACTION_BOOT_COMPLETED`:
1. `EveningPlanScheduler.armNext()` — next evening trigger
2. `HardLatestScheduler.rearmTodayIfActive()` — if today's session is in `monitoring` / `re_monitoring` and hard-latest hasn't fired, re-arm it from persisted state

The AI alarm itself does **not** survive reboot unless we re-arm it. Tradeoff: if you reboot at 03:00 with an AI alarm set for 07:00, we lose the AI alarm but the hard-latest is re-armed as a safety floor. On next sensor event, the FSM triggers a replan which re-arms the AI alarm.

### 6.6 Calendar change detection

`CalendarChangeReceiver` remains, with 2-second debounce. On fire:

1. Acquire partial wake lock
2. `CalendarChangeAnalyzer.diffAgainstCachedPlan()` — read current first event, compare to `session.cached_first_event_sig` (hash of id + start + location)
3. If unchanged → drop
4. If changed → append `calendar_change` event with `affectsFirstEvent = true`, trigger AI replan

The in-foreground `ContentObserver` on `HomeViewModel` is removed; UI reads from Room via `Flow`.

---

## 7. Alarm UX

`AlarmReceiver` keeps its existing fire/dismiss/snooze flow. Added behavior:

- On `dismiss` → emit `alarm_dismissed` session event, set status `complete`, clear hard-latest, stop service
- On `snooze` → increment `snoozeCount` in session, emit `alarm_snoozed`. Snooze duration is clamped so the resulting alarm time stays ≤ hard-latest.
- On `snoozeCount >= 3` → bypass AI, schedule alarm at `now + 5min`, mark next dismiss as session-complete.

---

## 8. Permissions

`AndroidManifest.xml`:

```xml
<uses-permission android:name="android.permission.READ_CALENDAR"/>
<uses-permission android:name="android.permission.USE_EXACT_ALARM"/>
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED"/>
<uses-permission android:name="android.permission.POST_NOTIFICATIONS"/>
<uses-permission android:name="android.permission.WAKE_LOCK"/>
<uses-permission android:name="android.permission.VIBRATE"/>
<uses-permission android:name="android.permission.INTERNET"/>
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION"/>
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION"/>
<uses-permission android:name="android.permission.ACTIVITY_RECOGNITION"/>
<uses-permission android:name="android.permission.FOREGROUND_SERVICE"/>
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE"/>
<uses-permission android:name="android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS"/>
<uses-permission android:name="android.permission.health.READ_SLEEP"/>
```

Health Connect queries block for `<package>` visibility (Android 11+):
```xml
<queries>
    <package android:name="com.google.android.apps.healthdata"/>
</queries>
```

---

## 9. Onboarding

Multi-step, blocking. Required for sessions to start:

1. **Welcome** — explain what the app does
2. **Anthropic API key** — paste, validate via 1-token ping, store in EncryptedSharedPreferences
3. **`POST_NOTIFICATIONS`** (Android 13+)
4. **`USE_EXACT_ALARM`** verification (deep-link to system settings if missing)
5. **`ACCESS_COARSE_LOCATION`** (+ `ACCESS_FINE_LOCATION` optional) — rationale: *"Used once each evening to calculate your commute to tomorrow's first appointment. Never tracked in the background."*
6. **Home address** — geocoded via `GeocodingClient`, stored as fallback origin. Skippable if location was granted, recommended otherwise.
7. **`ACTIVITY_RECOGNITION`**
8. **Health Connect** — `getSdkStatus()` check, then permission contract. Skippable; if skipped, app runs in phone-only mode.
9. **Battery optimization exemption** — `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`. Skippable; if skipped, reduced-reliability banner shown on Home.

Sessions cannot start without (2), (4), and one of (5)/(6).

---

## 10. Edge Cases

### 10.1 Cross-midnight session boundary

`SessionRepository.currentSessionDate(now: ZonedDateTime): LocalDate`:
```kotlin
return if (now.toLocalTime().isBefore(LocalTime.of(4, 0)))
    now.toLocalDate()                  // late-night event → today's morning
else
    now.toLocalDate()                  // mid-day event → today
                                       // wait, this is the morning we're targeting
```

Rule: if `now.hour < 4` → session date = today's calendar date. Otherwise → session date = tomorrow's calendar date (we're planning for tomorrow's wake-up).

### 10.2 Cold start (no evening plan)

If a sensor event fires but no session exists:
- Create session with `isFreeDayFallback: true`
- `hardLatest` defaults to `freeDayWakeEnd + 30 min`
- Run AI immediately with whatever calendar data is available

### 10.3 No-sleep timeout

If no `sleep_onset` by 04:00 local:
- Session status forced to `monitoring` so hard-latest still applies
- Log entry; no proactive alarm

### 10.4 Phone-only mode

When no high-confidence Health Connect data after 90 min:
- `hc_stage_update` events synthesized from screen + activity with `confidence: low`
- System prompt for replans appends "phone-only mode; treat stage signals as approximate"

### 10.5 No calendar events (free day)

- AI's `read_calendar` returns empty
- Switch to `freeDayWakeStart` / `freeDayWakeEnd` from settings
- `hardLatest` = `freeDayWakeEnd + 30 min`

### 10.6 Snooze escalation

```
snoozeCount 0–1: AI replan as normal
snoozeCount 2:   AI replan, system prompt notes "second snooze — be firm"
snoozeCount ≥ 3: skip AI, alarm = now + 5min, mark complete on next dismiss
```

### 10.7 Re-arm sleep detection

After `out_of_bed_confirmed`, status moves to `awake`. New `sleep_onset` with threshold lowered to 15 min → status `re_monitoring`, event carries `rearm: true`. AI issues fresh `set_alarm` within original `hardLatest`.

### 10.8 Calendar change during sleep

Receiver-only path (no in-foreground observer). `CalendarChangeAnalyzer` filters to changes affecting the first event before triggering replan.

### 10.9 Location unavailable / stale at T0

See §6.4 table.

### 10.10 `USE_EXACT_ALARM` revoked at runtime

`AlarmScheduler` checks `alarmManager.canScheduleExactAlarms()` before every arm. If revoked:
- Persistent notification: re-grant required
- UI banner on Home
- Session marked degraded (events still logged; alarm cannot fire reliably)

### 10.11 Timezone changes (travel)

`TimeZoneChangedReceiver` → re-arm evening trigger. Sessions in progress retain their `timezone` field for consistency; new sessions use the new zone.

### 10.12 Doze mode

- Hard-latest and AI alarm use `setAlarmClock` (Doze-exempt)
- `AiTurnWorker` uses expedited work + foreground service
- WorkManager Health Connect poll accepts deferred execution; if Doze defers it past wake, no harm

---

## 11. Data Retention & Privacy

- Session event logs retained 30 days, cleaned up nightly
- Raw accelerometer / heart-rate data never leaves device
- Health Connect data leaves device only inside `SessionEvent.data` payloads (typed, summarized)
- The Anthropic API receives: event log + day plan + calendar/routes tool results
- Anthropic API key stored encrypted at rest via `EncryptedSharedPreferences`
- Routes API key stored in `BuildConfig` (domain-restricted by Google Cloud Console)

---

## 12. Key Invariants

1. `hardLatest` is **immutable** after T0 — no tool, no AI output, no user action moves it within a session
2. Hard-latest alarm is armed as a **separate, independent** `AlarmManager` entry — cleared only on `alarm_dismissed` or session `complete`
3. Events are **appended to Room before** any AI turn fires — append-only log
4. `SetAlarmTool` **clamps** to `[now + 60s, hardLatest]` before calling `AlarmManager`
5. Android never talks to Google APIs except via on-device clients (CalendarContract, Routes, Geocoding)
6. AlarmManager state survives reboot via `BootReceiver` + persisted session in Room
7. Package name `fr.bsodium.cron` is **never** changed — preserves Play Store identity
8. AI replans are **resumable** across process death via `ai_messages` table + `AiTurnWorker`

---

## 13. Migration from Current Codebase

### Delete

- `app/src/main/java/fr/bsodium/cron/engine/` (all subpackages: `orchestrator`, `calendar`, `scheduler`, `travel`, `config`, `model`)
- `app/src/main/java/fr/bsodium/cron/worker/CalendarSyncWorker.kt`
- `app/src/main/java/fr/bsodium/cron/ui/components/StatusToggle.kt`

### Refactor

- `receiver/BootReceiver.kt` → call new schedulers
- `receiver/CalendarChangeReceiver.kt` → dispatch via `CalendarChangeAnalyzer`
- `ui/screens/home/HomeViewModel.kt` → consume `SessionRepository` Flow
- `ui/screens/home/HomeScreen.kt` → surface session state + AI reasoning trail

### Light edits

- `receiver/AlarmReceiver.kt` → emit session events on dismiss/snooze
- `CronApplication.kt` → init Room, WorkManager config, `EveningPlanScheduler.armNext()`
- `AndroidManifest.xml` → 8 new permissions + service + 2 new receivers

### Keep unchanged

- `ui/theme/*`
- `ui/components/PermissionGate.kt`, `AlarmCard.kt`, `EventListItem.kt`
- `MainActivity.kt` (light nav wiring only)
- CI workflows (`.github/workflows/build.yml`, `release.yml`) — git-tag versioning stays as-is

---

## 14. Verification

- **`Clock` injection** at `CronApplication.clock` — debug builds swap for deterministic tests
- **Manual trigger panel** in debug builds (long-press app version): force-emit each `TriggerType`, fast-forward 1h, dump session JSON
- **Mock tool layer** — `ToolRegistry(mockMode = true)` swaps real tools for fixture-backed versions
- **AI conversation snapshot tests** — `AiTurnWorker` writes full `messages[]` to a debug file; `androidTest` replay harness asserts `set_alarm` falls within expected bounds
- **Foreground service stress** — `adb shell dumpsys deviceidle force-idle` + `am set-inactive`; assert session survives
- **Cross-midnight test** — inject `Clock` at 23:55, advance, assert session date doesn't flip
- **Boot loop** — arm evening + hard-latest, `adb reboot`, assert both re-armed via `adb shell dumpsys alarm | grep cron`
- **End-to-end dry run** — mock tools + injected clock advancing in 5-min steps; full session from `evening_plan` through 6 `hc_stage_update`s to `alarm_dismissed`. Assert state transitions and that hard-latest is never violated.

---

## 15. Phases

Mirrors the executable plan at `~/.claude/plans/hey-dude-big-revamp-modular-fountain.md`.

- **Phase 0** — Refresh this doc (done by reading it)
- **Phase 1** — Foundation: deps + Room + model classes
- **Phase 2** — Anthropic client + `ReadCalendarTool` smoke test
- **Phase 3** — Alarm scheduling rewrite (`AlarmScheduler`, `HardLatestScheduler`, `EveningPlanScheduler`)
- **Phase 4** — Sensor stack + `SleepSessionService`
- **Phase 5** — `SessionFsm` + remaining tools + full AI orchestration
- **Phase 6** — Edge cases (§10)
- **Phase 7** — UI polish: onboarding, settings, home refactor
- **Phase 8** — Hardening: resume path, retry/backoff, cost budget, log viewer, retention
