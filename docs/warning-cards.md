# Warning Cards System

## Overview

The warning cards system displays contextual alerts and notifications to the user via pill-shaped cards in the floating card stack (see `docs/floating-card-layout.md`). Warnings are dismissible, persistent across app sessions, and re-appear when their underlying condition changes.

This document covers:
1. **Developer Settings Warning** — alerts when development settings affecting performance/cost are enabled.
2. **Settings Changed Warning** — prompts re-run when user-facing settings are modified.
3. **Future extensions** — map, widgets, and custom alerts.

## Architecture

### Warning Types (Sealed Class)

```kotlin
sealed class WarningCard {
    data class DeveloperSettings(
        val enabledSettings: List<String>, // e.g., ["devtools", "mock_location"]
        val id: String = "warning_devtools",
    ) : WarningCard()

    data class SettingsChanged(
        val changedSettings: List<String>, // e.g., ["alarm_volume", "vibration"]
        val onRerun: suspend () -> Unit, // Callback to execute rerun
        val id: String = "warning_settings_changed",
    ) : WarningCard()

    data class Custom(
        val title: String,
        val message: String,
        val actionLabel: String? = null,
        val onAction: suspend () -> Unit = {},
        val id: String,
    ) : WarningCard()
}
```

Each warning has:
- A stable `id` for persistence (tracking dismissal).
- Content (title, message, optional action).
- An optional callback for primary actions (rerun, dismiss).

### ViewModel State

Add warning management to the home screen ViewModel:

```kotlin
data class HomeUiState(
    // ... existing fields
    warnings: List<WarningCard> = emptyList(),
    dismissedWarningIds: Set<String> = emptySet(), // Persisted
)
```

Expose flows:
- `warnings` — current visible warnings (filtered by `dismissedWarningIds`).
- Methods to dismiss, acknowledge, or trigger actions on warnings.

### Persistence

Store dismissed warning IDs in `DataStore<HomePreferences>`:

```proto
message HomePreferences {
    repeated string dismissed_warning_ids = 1;
    // ... other fields
}
```

**Reset dismissal** when the underlying condition changes:
- **Developer Settings Warning**: Reset when dev setting toggles off.
- **Settings Changed Warning**: Auto-dismiss after user initiates rerun; reset if settings change again before rerun completes.
- **Custom**: Define reset logic per warning type.

## Implementations

### 1. Developer Settings Warning

**Trigger**: DevTools or developer-only features enabled that may impact performance or AI usage costs.

**Content**:
```
Title: "Developer settings enabled"
Message: "These may affect app reliability and increase AI consumption costs."
Action: (Dismiss only)
```

**Implementation**:

Detect in ViewModel when loading preferences:

```kotlin
private fun checkDeveloperSettings() {
    dataStore.data
        .mapLatest { prefs ->
            val enabled = buildList {
                if (prefs.enableDevTools) add("devtools")
                if (prefs.enableMockLocation) add("mock_location")
                if (prefs.verboseLogging) add("verbose_logging")
                // Add other dev flags
            }
            if (enabled.isNotEmpty() && !isDismissed("warning_devtools")) {
                WarningCard.DeveloperSettings(enabledSettings = enabled)
            } else null
        }
        .collectLatest { warning ->
            _warnings.value = (_warnings.value.filterNot { it.id == "warning_devtools" })
                .let { if (warning != null) it + warning else it }
        }
}
```

Reset dismissal when dev settings toggle off:

```kotlin
fun updateDeveloperSetting(setting: String, enabled: Boolean) {
    viewModelScope.launch {
        // ... apply setting
        if (!enabled) {
            dismissedWarningIds.update { it - "warning_devtools" } // Re-show if toggled
        }
    }
}
```

### 2. Settings Changed Warning

**Trigger**: User modifies alarm-affecting settings (volume, vibration, snooze duration, etc.) that require re-running the current alarm.

**Content**:
```
Title: "Settings changed"
Message: "Your changes will apply after the next alarm runs."
Action: "Rerun now" (primary), "Dismiss" (secondary)
```

**Implementation**:

Track settings changes in a settings repository:

```kotlin
private suspend fun detectSettingsChanges() {
    dataStore.data
        .mapLatest { prefs ->
            val settingsHash = prefs.alarmSettingsHash()
            val hasChanged = lastKnownHash != null && lastKnownHash != settingsHash
            
            if (hasChanged && !isDismissed("warning_settings_changed")) {
                val changed = calculateDifferences(prefs)
                WarningCard.SettingsChanged(
                    changedSettings = changed,
                    onRerun = { rerunCurrentAlarm() },
                )
            } else null
        }
        .collectLatest { warning ->
            _warnings.value = (_warnings.value.filterNot { it.id == "warning_settings_changed" })
                .let { if (warning != null) it + warning else it }
        }
}

private suspend fun rerunCurrentAlarm() {
    try {
        alarmRepository.rerun(getCurrentAlarmId())
        dismissWarning("warning_settings_changed")
        lastKnownHash = getCurrentSettingsHash()
    } catch (e: Exception) {
        Log.w(TAG, "Failed to rerun alarm", e)
        // Show error toast or inline message
    }
}
```

Auto-dismiss the warning after rerun succeeds, or if user dismisses it manually (acknowledging they understand the setting won't apply until next alarm).

### 3. Commute Map Card

**Trigger**: Upcoming alarm with commute data available (from Google Maps API or user-configured route).

**Content**:
```
A card-sized map preview of the commute route for tomorrow's alarm time, 
with route duration, distance, and current traffic.
```

**Implementation**:

This integrates with the floating card layout (not a warning, but a widget):

```kotlin
private suspend fun fetchCommuteData() {
    val nextAlarm = alarmRepository.nextAlarm().first()
    val alarmTime = nextAlarm?.triggerTime ?: return
    
    mapsClient.getCommute(
        origin = userLocation,
        destination = userSettings.workAddress,
        departureTime = alarmTime,
    ).collect { route ->
        _floatingCards.update {
            it.filterNot { it is FloatingCard.CommuteMap } + 
            FloatingCard.CommuteMap(route)
        }
    }
}
```

The card should:
- Display an embedded map preview (use Google Maps SDK or static image).
- Show route duration and traffic status.
- Dismiss button to hide (persisted as dismissed).
- Optional: Tap to open full Google Maps navigation.

### 4. Custom Warnings (Framework for Future Widgets)

Allow other systems (alert manager, AI decision engine, etc.) to emit warnings:

```kotlin
fun emitWarning(warning: WarningCard.Custom) {
    viewModelScope.launch {
        if (!isDismissed(warning.id)) {
            _warnings.value = _warnings.value + warning
        }
    }
}

fun dismissWarning(id: String) {
    viewModelScope.launch {
        dismissedWarningIds.update { it + id }
        _warnings.value = _warnings.value.filterNot { it.id == id }
        // Persist to DataStore
    }
}
```

## UI Components

### WarningCardContent Composable

Render individual warning cards:

```kotlin
@Composable
fun WarningCardContent(
    warning: WarningCard,
    onDismiss: () -> Unit,
    onAction: suspend () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.pill)) // Pill shape
            .background(
                color = MaterialTheme.colorScheme.secondaryContainer,
            ),
        shape = RoundedCornerShape(Radius.pill),
        tonalElevation = Spacing.xs, // Subtle elevation via color
    ) {
        Row(
            modifier = Modifier.padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Icon(
                imageVector = Icons.Rounded.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(20.dp),
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                when (warning) {
                    is WarningCard.DeveloperSettings -> {
                        Text(
                            text = "Developer settings enabled",
                            style = CronTypography.labelMono,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                        Text(
                            text = "These may affect app reliability and AI consumption costs.",
                            style = CronTypography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
                        )
                    }
                    is WarningCard.SettingsChanged -> {
                        Text(
                            text = "Settings changed",
                            style = CronTypography.labelMono,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                        Text(
                            text = "Changes apply after the next alarm runs.",
                            style = CronTypography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
                        )
                    }
                    is WarningCard.Custom -> {
                        Text(
                            text = warning.title,
                            style = CronTypography.labelMono,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                        Text(
                            text = warning.message,
                            style = CronTypography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
                        )
                    }
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                modifier = Modifier.align(Alignment.CenterVertically),
            ) {
                // Primary action (e.g., "Rerun", specific action)
                when (warning) {
                    is WarningCard.SettingsChanged -> {
                        Button(
                            onClick = {
                                viewModelScope.launch {
                                    onAction()
                                }
                            },
                            modifier = Modifier.height(32.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondary,
                            ),
                        ) {
                            Text("Rerun")
                        }
                    }
                    else -> {} // No action button
                }

                // Dismiss button
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Dismiss",
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
        }
    }
}
```

### Integration into Floating Stack

Update `FloatingCardStack` to handle `WarningCard`:

```kotlin
when (card) {
    is FloatingCard.Alarm -> NextAlarmCard(...)
    is FloatingCard.CommuteMap -> CommuteMapCard(...)
    is FloatingCard.Warning -> WarningCardContent(
        warning = card.warning,
        onDismiss = { onDismiss(card) },
        onAction = { /* trigger card.warning.onAction */ },
    )
}
```

## State Management & Dismissal

### Dismissal Lifecycle

1. **User dismisses warning** → ViewModel calls `dismissWarning(id)`.
2. **Dismissed ID persisted** → DataStore updated.
3. **Warning removed from list** → UI animates out (via `FloatingCardStack`).
4. **Condition changes** → ViewModel detects and resets dismissal.

### Auto-Dismiss Scenarios

- **Settings Changed Warning** → Auto-dismiss 5s after rerun completes (or immediately if user dismisses).
- **Developer Settings Warning** → Never auto-dismiss; only clear if user disables the setting.
- **Commute Map** → Auto-dismiss at alarm trigger time (or user can dismiss manually).

## Testing & Validation

### Unit Tests
- Mock developer settings and verify warning appears/disappears.
- Verify dismissal persistence across app restarts.
- Test dismissal reset when underlying condition changes.

### UI Tests (Roborazzi)
- Single warning card rendered correctly.
- Multiple warnings stacked with proper spacing.
- Dismiss animation smooth and complete.
- Pill shape and colors match design tokens.

### Manual Testing
- Enable dev settings → warning appears.
- Dismiss → persisted, doesn't reappear until dev setting toggles.
- Change alarm settings → warning appears.
- Tap "Rerun" → alarm reruns, warning auto-dismisses (or user dismisses manually).
- Scroll content underneath warnings → gradient overlay visible, no clipping.

## Color Roles

Warnings use `secondaryContainer` for background and `secondary` for accent elements (icon, action button), ensuring they stand out from primary content without being alarming (red/orange). Customize via dynamic color if needed.

## Future Considerations

1. **Sound/Haptics**: Emit a subtle vibration or notification sound when a warning appears.
2. **Analytics**: Track warning dismissals and actions to understand user behavior.
3. **Expiration**: Auto-dismiss warnings after a set duration (e.g., 30 seconds for transient alerts).
4. **Grouping**: Combine similar warnings (e.g., multiple dev settings → single "N dev settings enabled" warning).
