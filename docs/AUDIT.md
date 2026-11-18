# Dinrekha — full app audit (17 Nov 2026)

How it was checked: Android lint on the release build, 133 unit tests, the live state of the phone
(Honor ELI-NX9, Android 16: permissions, alarms, notification channels, background jobs, logs, memory),
and a code review of every flow — start-up, reminders, habits, sleep/journal, wallet, focus, calendar,
class notes, app blocker, widgets, backup/reset, language.

Phone snapshot: 20 alarms scheduled, 23 notification channels all allowed, accessibility service on,
usage access and overlay allowed, battery optimisation off, 67 MB memory, no crashes.

## Fixed during this audit

| # | Severity | Problem | Effect before the fix |
|---|---|---|---|
| 1 | Critical | Release optimiser (R8) stripped WorkManager's `OverwritingInputMerger` constructor | Every home-screen widget update failed with `InstantiationException`; widgets would never refresh |
| 2 | High | Notification channel clean-up deleted every `c_…` channel it didn't own, including the App Blocker's `c_limit_warnings` | After changing any notification setting, "50% / 80% / 1 min left" limit warnings silently stopped |
| 3 | High | Class notes and photo references were not in the JSON backup | Export → restore lost every class note |
| 4 | High | 24 database queries on 9 screens were re-created on every redraw | Lists flashed empty/zero and reloaded on Today, App limits, More, Calendar…; extra jank and DB work |
| 5 | High | Accessibility service ran the block-rule DB check and a ~10-keyword screen search on every event (many per second) | Battery drain and possible stutter inside Facebook / YouTube |
| 6 | Medium | Widgets redrew on every settings-table write (caches too) | Bursts of 14+ background jobs in under a second |
| 7 | Medium | Side-counter switch created state without `remember` (lint error) | Switch state could reset on redraw |
| 8 | Low | Class-note capture used the phone's timezone instead of Asia/Dhaka | Wrong default date if the phone's timezone ever differs |
| 9 | Low | Decorative icons in the status notification were announced by screen readers | Noise for TalkBack users |

Earlier passes today also fixed: Focus pause/stop buttons clipped, FB/YT reels not counted, double
title bars (7 screens), sun card dark in light mode, 165 uses of 11 sp text, 32 all-caps labels,
18 tap targets under 48 dp, blocker back-gesture bypass, API 29 crash, 66 MB → 23 MB APK.

## Workflow pass (18 Nov 2026)

Every user flow traced end to end: start-up & onboarding, notification / widget / share links,
tabs & back stack, Today & day view, wallet add/edit/delete, calendar events, focus, sleep,
class notes, app blocker rules, settings, backup restore and factory reset, app lock.

| # | Flow | Problem | Fix |
|---|---|---|---|
| 1 | Share photos → Dinrekha | Shared photos were cleared before the capture screen read them, so it opened empty | Photos are held until the capture screen takes them |
| 2 | Class notes | No courses yet → "Please select a course" with no way to add one | "Add course" button on the capture screen |
| 3 | Class notes | Photos taken were lost if the camera app pushed Dinrekha out of memory or the phone rotated; a failed save left the button stuck on "Saving…" | Photo list survives; failed save shows the error and can be retried |
| 4 | App lock on | Leaving the app (camera, file picker, share) tore down every screen: back stack, typed forms, the photo just taken and the backup file just picked were lost | Lock panel now covers the screen instead of removing it; one unlock prompt at a time |
| 5 | Notification / widget links | Opening "Today", "Wallet", "Focus" from a notification stacked a second copy of the tab | Tab links switch tabs; links with arguments open above Today |
| 6 | Notification / share links | Rotating the phone re-ran the link and jumped back to that screen (or re-opened capture) | Links are handled only on a fresh start or a new intent |
| 7 | App lock + notification link | A link arriving while the app lock was up navigated before the screens existed | Link waits until the app is unlocked |
| 8 | Add transaction / event / block rule | A quick second tap on Save saved twice; a second Back/Save tap popped two screens | Save/Delete run once; back is ignored while the first one is animating |
| 9 | 30-day plan → today's day | "Start focus" and "Log last night's sleep" did nothing on that screen | Wired up |
| 10 | Block screen → "Focus 45 min" | Opened Focus with the default length (45 was ignored) | Link now carries a focus type, so the preset applies |
| 11 | Factory reset / restore | The reschedule ran in the Settings screen, which reset disposes when it jumps to onboarding — old alarms could keep firing | Runs in the app scope; toast after it finishes |
| 12 | Settings lock | "My routines" and "Notification log" weren't behind the Settings lock like the other settings pages | Locked the same way |

## Simpler structure (18 Nov 2026)

| Before | After |
|---|---|
| Tabs: Today, Calendar, Focus, Wallet, More | Today, Calendar, **Study** (focus timer + class notes), Wallet, **Me** |
| 7 progress screens (Stats, plan grid, weekly review, daily / weekly / 30-day report, share card) | One **Progress** screen: Today · Week · Plan · Habits; share and "write review" on the tabs |
| Settings: one 12-section scroll; Routines, Schedule, Notifications, Blocker also reachable from More | Settings hub of 6 rows → Look & language and Privacy & data are their own pages; one way in to each place |
| Today: 8 blocks, checklist below the glance tiles, reflection always open | Checklist right after "up next"; reflection folded until 6 PM; class banner has one button |

## Still wrong or risky (not fixed)

1. ~~No version control~~ — **fixed:** git set up on 17 Nov (branch `main`). Two AI tools still edit
   the same folder, so commit after each working build and check `git status` before starting work.
2. ~~Low-contrast colours~~ — **fixed 18 Nov:** 18 failing text/icon colours (down to 1.5:1) now pass
   4.5:1 (text) / 3:1 (icons) on light and dark cards via `Color.readable()`; tested in ContrastTest.
   Not covered: colours drawn on their own backgrounds (bank cards, photo viewer, share-card images).
3. **Facebook / YouTube reel counting is unverified on the phone.** The new detection (full-screen
   pager, one item per swipe) is built and installed, but nobody has watched reels with it yet.
4. **Backups don't contain the photo files**, only their paths. Restoring on a new phone keeps the
   notes but not the pictures.
5. **The app is not "100% offline".** Weather sends approximate coordinates to Open-Meteo (accepted
   by you; stated in the privacy policy).
6. **Play Store blockers** (fine for a personal app): signed with the debug key; uses
   QUERY_ALL_PACKAGES, an accessibility service for blocking, overlay and device-admin permissions.
7. **Translation gaps:** ~5% of fixed text, some report sentences built from pieces, and
   screen-reader labels stay English.
8. **Widget picker shows the app icon**, not a real preview of each widget.
9. **Only unit tests.** No automated UI tests, so screen regressions are caught only by hand.
10. **Library updates available** (Compose BOM 2026.09, Kotlin 2.4.21, Gradle 9.9.1). Not updated;
    worth doing on a quiet day with a full re-test.
11. **62 lint style hints** (`toUri()` KTX etc.) — cosmetic.

## Features from your specs that are not built

Deadline tracker · step counter · water & meals · connection reminder · stand-alone Insights screen ·
import from the phone's calendar · drag-to-move calendar events · in-app multi-photo camera for
class notes · CSV import into Wallet · website blocking (built by the other tool, not verified).

## What works well

- Reminders: every trigger is covered (boot, time/timezone change, app update, exact-alarm permission
  change, app open), only the next occurrence per slot is scheduled, quiet hours and mute respected.
- Data: Room v1 → v10 with explicit migrations and exported schemas; all money in paisa.
- Speed: release build with R8, 7 ms median frame time, ~1% janky frames.
- Privacy: no accounts, no analytics, wallet lock respected by widgets.
