# AURA TV — Android TV launcher starter (0.1.0)

A clean, ad-free launcher shell for the TCL 32S250G Google TV. The project is intentionally a starter build, not a claim of full device validation.

## Included
- Android TV `LEANBACK_LAUNCHER` entry and a `CATEGORY_HOME` home-handler activity.
- Installed app discovery (Leanback and regular launchable apps) and launching.
- D-pad left/right + OK navigation across the first row of apps.
- Back quick menu, Settings key shortcut where the remote reports it, editable local reminder.
- Idle screensaver after five minutes; any key returns.
- Accent color setting, dark cinematic background, 16:9 landscape layout.
- No ad SDKs and no ad inventory.

## Important limitations
- **Home replacement is not guaranteed by installing alone.** Android/Google TV may ask which Home app to use, may keep the OEM launcher as default, or may restrict changing it. Select AURA TV as Home only if the TV offers that choice. Keep the stock launcher available as a fallback.
- Dedicated Netflix/YouTube/etc. remote buttons are generally handled by TV firmware and may launch their mapped apps directly. A normal third-party app cannot safely intercept or remap those hardware keys. This project does not claim to.
- Weather currently has a clearly marked integration placeholder; no live forecast is fabricated.
- The reminder is stored locally on the TV. Phone reminders/calendar sync is not yet implemented. That requires an authenticated companion/sync path (for example, Google Calendar OAuth or a small phone companion app) and user consent.
- Screensaver is an in-app idle screen. Android's system-level dream/screensaver settings remain controlled by the TV OS.
- This source project has not been compiled or tested on the user's physical TV in this environment.

## Build
1. Install Android Studio (current stable) on a computer.
2. Open this folder as an existing project.
3. Let Android Studio install Android SDK Platform 35 and the matching Gradle components.
4. Build > Build Bundle(s) / APK(s) > Build APK(s).
5. Transfer the APK to the TV (USB or a trusted local-network sideload method) and install it. Enable developer options / install unknown apps only if you understand the security implications.
6. Launch AURA TV. If Android presents a Home-app chooser, choose AURA TV to test it. If no chooser appears, use the TV's Home/default-app settings if exposed. Do not disable or uninstall the stock launcher.

## Remote testing
- D-pad left/right selects apps; OK/Enter launches.
- Back opens AURA quick menu.
- Settings/Menu key opens settings if the remote maps it to a standard Android key code.
- Home behavior and branded app buttons depend on TCL firmware and must be tested on-device.

## Device target
TCL 32S250G is a 32-inch 720p Google TV model. The interface uses scalable canvas sizing and a landscape layout, with a conservative minSdk 23 baseline. Confirm the installed Android version in Settings > System > About before final device tuning.
