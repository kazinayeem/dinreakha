# Dinrekha roadmap

v1 (built): habits & 30-day plan, all reminders + status countdown, finance module with app lock, backup, Dinrekha branding + animated splash.

v1.1 (built, DB v3): **Settings as central control** — schedule editor (plan dates, phases, class-day overrides,
classes, buses, focus blocks, prayer times, holidays, reset), **My routines** (daily / weekly / monthly, tick on Today,
optional reminder), classes tickable on Today, notification control (master switch, per type on/off + offset/time +
sound/vibrate/silent + ringtone + test, quiet hours plan/custom/off, wake snooze 5/10/15 + max, mute today, holiday
mode, notification log), general (colour palettes incl. Material You / Catppuccin / Dracula / Nord, light/dark/system,
first day of week, 12h/24h everywhere), app-lock scope (wallet / settings / whole app).
Still open from that spec: per-type controls for Deadlines, Calendar events and Water arrive with those modules.

Below are the modules you asked for after v1. **Next, in this order: 10. App blocker (also records screen time) → 9. Class notes & photos → 8b. Progress reports & share cards**, then the rest. Each one is a new
Room migration (v3, v4 …) and new screens, using the reminder planner and navigation from v1.

## 1. ✅ Settings → schedule editor (done in v1.1 — see above)
Move `Schedule.kt` constants into tables seeded with the current values: classes, bus times, plan start date,
phases (wake/bed, class-day overrides), study/research/code blocks, prayer times, holidays (16 Dec pre-filled).
Notification control: master switch; per type on/off + time/offset + sound/vibrate/silent + ringtone;
quiet hours; wake snooze length (5/10/15) + max snoozes; mute today; holiday mode (date range);
notification log (scheduled + recently fired); test button per channel.
General: theme light/dark/system, first day of week (Sat), 12h/24h, app-lock scope.
New nav: Today · Calendar · Focus · Wallet · More.

## 2. ✅ Calendar (done, DB v4 — read-only system-calendar import and drag-to-move/resize still open)
Month / Week / Day (hour timeline) / Agenda, swipe between periods, Today button, layers with colours and
toggles (my events, schedule-generated items, deadlines, day notes). Events: all-day or timed, location, notes,
colour, repeat (daily / weekly-on-days / monthly / yearly / until-date / count), multiple reminders, edit one vs
all occurrences, skip an occurrence, long-press to create, drag to move/resize (nice to have).
Optional read-only import from the system calendar (READ_CALENDAR); .ics export/import.
Tables: `event`, `event_exception`, `event_reminder`, `day_note`, `holiday`.

## 3. ✅ Focus timer (done, DB v5 — exact alarm + live notification countdown instead of a long-running foreground service)
Start from a block or manually; 25/45/90/custom; pause/stop; foreground service with ongoing countdown;
optional Do-Not-Disturb; on finish log `focus_session(id, type, start, end, minutes, note)` and auto-tick the habit.

## 4. Deadline tracker
Title, course, type, due date-time, priority, done; reminders 3 days / 1 day / morning-of; overdue list; shown
in the calendar; can attach photos (after module 9).

## 5. Sleep log + mood journal + low-mood check + connection reminder
`sleep_log(date PK, slept_at, woke_at, quality)` with a morning prompt prefilled from usage events; charts of
actual vs target. Nightly mood + one-line journal (30 min before screens-off), journal search, mood heatmap.
Gentle low-mood card after 3 days ≤ 2 with a trusted-contact button (can be turned off). Weekly
"call family" reminder (Friday after Jumu'ah).

## 6. Screen time + step counter + water & meals
UsageStatsManager (usage-access onboarding): total phone time, top 5 apps, unlocks, after-midnight usage,
7-day chart, optional limit warning. TYPE_STEP_COUNTER via WorkManager (ACTIVITY_RECOGNITION), auto-tick walk
at a step goal (default 3000). Water counter (goal 8, optional 2-hourly reminders), meal ticks.

## 7. Home-screen widget (Jetpack Glance)
Next item ("Bus 07:00 in 35 min"), checklist progress, quick ticks, Add expense, Capture note.

## 8. Insights (weekly, computed locally)
Mood / focus / completion on before- vs after-midnight sleep days, phone vs focus time, best weekday, plain-language cards.

## 8b. Progress reports & shareable image cards (after 3–6, which supply the data)
Daily summary at bedtime + anytime; improvement score 0–100 with editable weights; weekly report (Friday, with the
review) — week-over-week arrows, bedtime / focus / habit-heatmap / screen-time charts, best day, streaks, neutral
plain-language insights; 30-day Day 1 vs Day 30 view; badges. Shareable cards (Story 1080×1920, Square 1080×1080):
daily, weekly, before/after, achievement; 4–5 templates; privacy toggles (mood, journal, money, app names, exact sleep
times OFF by default); live preview; Compose → Bitmap at exact pixel size; save to Pictures/Dinrekha (MediaStore),
share sheet (FileProvider), optional Facebook Stories intent when an App ID is set. Friday 21:00 "weekly card is ready".
`report_cache(period_type, period_start, json, created_at)`. Unit tests: score, week-over-week, privacy filtering.

## 9. ✅ Class notes & photos (done, DB v6)
`course`, `class_note`, `note_image`. Multi-photo camera capture, Photo Picker, share-target (ACTION_SEND & ACTION_SEND_MULTIPLE),
crop/rotate, JPEG ~1600 px q80 + ~320 px thumbnails in `filesDir/notes/<course>/<date>/`, course library cards with stats,
date-grouped note list with tag filtering and ⭐ important filter, full-screen interactive pinch-to-zoom image viewer,
on-device ML Kit text recognition (offline, no INTERNET permission), multi-page PDF export via Android PdfDocument,
calendar day 📷 markers, day view class event notes integration with "Add photo" button, Today screen active class detection
and quick capture action, Hub "Courses & Class Notes" card, and class notification capture action.

## 10. ✅ App blocker & usage limits (done, DB v7)
AccessibilityService foreground-app & Chrome website URL detection (+ UsageStats fallback), full-screen block screen (`BlockActivity`),
daily limits with 50/80 %/1-min warning notifications, per-session limits & cool-downs, schedule blocks (sleep mode from the sleep plan),
focus & class blocks, app groups, permanent system allow-list (calls/phone/dialer/SMS/emergency never blocked), strict mode
(60 s wait + typed sentence, max 2 × 5-min emergencies/day), Device Admin anti-uninstall support, Today screen limit chips, and usage stats.
Tables `block_rule`, `app_usage`, `block_event`, `emergency_unlock`. Unit tests for limits, midnight reset, schedule overlap, and strict-mode lock.
