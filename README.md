# Dinrekha (দিনরেখা)

Personal, 100 % offline habit + routine + money app for one person (DIU SWE 42, Section H1).
Kotlin · Jetpack Compose (Material 3) · Room · MVVM · manual DI · no internet permission.

![logo](docs/dinrekha-logo.svg)

## Promo video

<a href="docs/video/dinrekha-promo.mp4"><img src="docs/video/dinrekha-promo-poster.jpg" width="270" alt="Dinrekha promo video (click to play)"></a>

▶️ [Watch the promo video](docs/video/dinrekha-promo.mp4) (Bangla voice-over, 79 s, 540×960)

## Screenshots

<table>
  <tr><td align="center"><img src="docs/screenshots/01-today.png" width="180" alt="Today"><br><sub>Today</sub></td><td align="center"><img src="docs/screenshots/02-calendar.png" width="180" alt="Calendar"><br><sub>Calendar</sub></td><td align="center"><img src="docs/screenshots/03-study-focus.png" width="180" alt="Study focus"><br><sub>Study focus</sub></td><td align="center"><img src="docs/screenshots/04-study-notes.png" width="180" alt="Study notes"><br><sub>Study notes</sub></td></tr>
  <tr><td align="center"><img src="docs/screenshots/05-wallet.png" width="180" alt="Wallet"><br><sub>Wallet</sub></td><td align="center"><img src="docs/screenshots/06-me.png" width="180" alt="Me"><br><sub>Me</sub></td><td align="center"><img src="docs/screenshots/07-progress-today.png" width="180" alt="Progress today"><br><sub>Progress today</sub></td><td align="center"><img src="docs/screenshots/08-progress-week.png" width="180" alt="Progress week"><br><sub>Progress week</sub></td></tr>
  <tr><td align="center"><img src="docs/screenshots/09-progress-plan.png" width="180" alt="Progress plan"><br><sub>Progress plan</sub></td><td align="center"><img src="docs/screenshots/10-progress-habits.png" width="180" alt="Progress habits"><br><sub>Progress habits</sub></td><td align="center"><img src="docs/screenshots/11-plan-grid.png" width="180" alt="Plan grid"><br><sub>Plan grid</sub></td><td align="center"><img src="docs/screenshots/12-sleep.png" width="180" alt="Sleep"><br><sub>Sleep</sub></td></tr>
  <tr><td align="center"><img src="docs/screenshots/13-blocker.png" width="180" alt="Blocker"><br><sub>Blocker</sub></td><td align="center"><img src="docs/screenshots/14-screen-time.png" width="180" alt="Screen time"><br><sub>Screen time</sub></td><td align="center"><img src="docs/screenshots/15-wallet-report.png" width="180" alt="Wallet report"><br><sub>Wallet report</sub></td><td align="center"><img src="docs/screenshots/16-budgets.png" width="180" alt="Budgets"><br><sub>Budgets</sub></td></tr>
  <tr><td align="center"><img src="docs/screenshots/17-settings.png" width="180" alt="Settings"><br><sub>Settings</sub></td><td align="center"><img src="docs/screenshots/18-schedule.png" width="180" alt="Schedule"><br><sub>Schedule</sub></td><td align="center"><img src="docs/screenshots/19-habits-routines.png" width="180" alt="Habits routines"><br><sub>Habits routines</sub></td><td align="center"><img src="docs/screenshots/20-notifications.png" width="180" alt="Notifications"><br><sub>Notifications</sub></td></tr>
  <tr><td align="center"><img src="docs/screenshots/21-look.png" width="180" alt="Look"><br><sub>Look</sub></td><td align="center"><img src="docs/screenshots/22-privacy.png" width="180" alt="Privacy"><br><sub>Privacy</sub></td><td align="center"><img src="docs/screenshots/23-share-card.png" width="180" alt="Share card"><br><sub>Share card</sub></td></tr>
</table>

## Build & install

```bash
./gradlew testDebugUnitTest      # 51 unit tests: schedule, reminders, money
./gradlew assembleDebug          # → app/build/outputs/apk/debug/app-debug.apk
```

Install on the phone (USB debugging or wireless debugging enabled):

```bash
~/Library/Android/sdk/platform-tools/adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Or copy `app-debug.apk` to the phone and open it (allow "Install unknown apps" for your file manager).
First launch shows onboarding: allow notifications, exact alarms, and turn battery optimisation off.

Migration test (needs a connected phone/emulator): `./gradlew connectedDebugAndroidTest`

## Project structure

```
app/src/main/java/com/example/tracker/
├── TrackerApp / AppContainer.kt   manual DI (db, repositories, scheduler)
├── MainActivity.kt                splash → animated intro → nav; reschedules on open
├── data/
│   ├── schedule/  Schedule.kt          ← all plan constants (dates, phases, classes, buses, blocks)
│   │              PlanCalendar.kt      day/phase, wake & bed maths, quiet hours, checklist, timeline
│   │              ReminderPlanner.kt   every reminder + "next occurrence per slot"
│   │              Habits.kt            stable habit keys
│   ├── finance/   Money.kt (paisa ↔ ৳ text), FinanceMath.kt (balances, transfers+fees, reports, budgets)
│   ├── db/        entities, DAOs, AppDatabase (v2), Migrations.kt (1→2), Converters
│   ├── repo/      HabitRepository, FinanceRepository, SettingsRepository
│   └── backup/    BackupManager (JSON full backup, CSV transactions)
├── alarm/         AlarmScheduler, Receivers (Reminder/Done/Reschedule), Notifications (+ status countdown)
└── ui/            today, grid, review, stats, settings, onboarding, wallet/*, splash, nav, theme
app/schemas/       Room schema snapshots (1.json, 2.json) for migrations
```

## Database (Room / SQLite, version 2)

| v | table | notes |
|---|---|---|
| 1 | `day_log(date PK, mood, note)` | |
| 1 | `habit_check(id PK, date, habit_key, done, done_at)` | unique(date, habit_key) |
| 1 | `weekly_review(week_no PK, went_well, got_in_way, next_change, created_at)` | |
| 1 | `settings(key PK, value)` | prayer times, toggles, mute date, app lock |
| 2 | `account(id, name, type, opening_balance_paisa, opening_date, last4, color, archived)` | balance is **calculated** |
| 2 | `category(id, name, icon, kind)` | seeded with your defaults |
| 2 | `txn(id, type, amount_paisa, account_id, to_account_id, category_id, date_time, note, linked_txn_id)` | indexes on date_time, account_id, category_id |
| 2 | `budget(id, category_id, month, limit_paisa)` | unique(category_id, month) |
| 2 | `recurring(id, title, amount_paisa, category_id, account_id, day_of_month, active, last_confirmed_month)` | |

Money is always `Long` paisa. Transfer + fee is written in one DB transaction.

## How reminders work

* `ReminderPlanner.nextPerSlot(now)` computes the **next** occurrence of each slot (wake, screens-off, bedtime, each prayer, bus, each focus block, weekly review, expense log, each recurring bill).
* `AlarmScheduler` sets one alarm per slot: wake-up via `setAlarmClock()` (Doze-proof, shows the alarm icon), everything else via `setExactAndAllowWhileIdle()` (inexact fallback if exact alarms are denied).
* When an alarm fires, `ReminderReceiver` shows the notification and reschedules everything → the following occurrence.
* Rescheduled on boot, time/time-zone change, app update, exact-alarm permission change, app open and every settings change.
* "Done ✓" / "I'm up ✓" / "Add ✓" actions write to Room through `DoneReceiver` without opening the app.
* A silent, ongoing **status-bar countdown** shows the next reminder with a live timer, today's progress and today's spending.

## Decisions worth knowing

* **Fajr is never notified**: it is always before your wake time, and the "no notifications between bedtime and wake" rule wins. Tick it on the checklist instead. (Change in `ReminderPlanner.isAllowed` if you want it.)
* **Mute today** silences everything except the wake-up alarm (including tonight's bedtime reminders that fire after midnight).
* **Focus blocks expected per phase**: 1 (days 1–7), 2 (days 8–14), all (from day 15), in order study → research → code. Optional blocks show on the timeline but are not reminded.
* **Bedtime** = min(phase bedtime, next wake − 7 h) — e.g. tonight (17 Nov) is 23:00 because Day 1 (Wed) wake is 06:00.
* **After Day 30** the phase-4 targets simply continue.
* Wallet lock uses fingerprint/face or the phone PIN (`BIOMETRIC_WEAK | DEVICE_CREDENTIAL`) and re-locks when the app goes to the background.
* `allowBackup=false` — nothing goes to Google cloud backup. Use Settings → Backup → Export JSON.

## Roadmap

See [docs/ROADMAP.md](docs/ROADMAP.md) for the requested v2+ modules (calendar, schedule editor, focus timer, screen time, sleep log, deadlines, widget, journal, insights, app blocker, class notes).
