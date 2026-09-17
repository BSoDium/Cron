# Implementation Plan - Finish `showBottomScrim` in `EdgeFades.kt`

Implement the `showBottomScrim` conditional logic and complete its documentation in `EdgeFades.kt`. Also update the call site in `MainActivity.kt` to use this property appropriately.

## Proposed Changes

### UI Components

#### [MODIFY] [EdgeFades.kt](file:///Users/bsodium/Developer/Cron/app/src/main/java/fr/bsodium/cron/ui/components/EdgeFades.kt)

- Wrap the bottom scrim `Box` in `if (showBottomScrim)`.
- Complete the KDoc for `showBottomScrim`. I'll follow the style of `showTopScrim` and `showNavPillClearance`.
- Documentation for `showBottomScrim`: "off for routes where the bottom area should remain clear, e.g. for full-screen content or when the bottom gradient clashes with the screen's own layout." (Refining based on `showTopScrim` logic). Actually, looking at `MainActivity.kt`, it's currently passed `showBottomBar` to `showNavPillClearance`.

### Application Shell

#### [MODIFY] [MainActivity.kt](file:///Users/bsodium/Developer/Cron/app/src/main/java/fr/bsodium/cron/MainActivity.kt)

- Pass `showBottomScrim = showBottomBar` to `EdgeFades`. This ensures the entire bottom gradient is removed when the navigation bar (and its pill) are hidden, which aligns with the goal of not "needlessly dimming/obscuring a pill-less screen's own last few rows of content."

## Verification Plan

### Manual Verification
- Deploy the app.
- Navigate between tabs (Home, Memory) and check that the bottom scrim is visible.
- Navigate to Settings and check that the bottom scrim is removed (since `showBottomBar` is false in Settings).
- Inspect the UI to ensure no "double gradient" or "two-tone" issues occur, as mentioned in the existing KDoc for top scrim.
- Take screenshots of the bottom area in both states to verify.
