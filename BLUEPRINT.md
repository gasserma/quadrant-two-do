# Quadrant Two Do — Design

Quadrant Two Do is a checklist for things you do regularly: floss daily, change the furnace filter every
three months, see the dentist twice a year. Each task has a period; the app shows how far through
that period you are, sorts the most urgent task to the top, and keeps a log of when you did it. A
home-screen widget shows the same list.

There are no notifications or accounts, and only a few appearance settings. Data stays on the phone,
moves to a new phone with Android's backup, and can be exported to a file.

*(The name: the Eisenhower matrix's quadrant II is "important but not urgent", which is exactly
these tasks. This is a to-do list for quadrant two.)*

## Core idea: one number per task

Every task gets an **urgency** score: how far past its due moment it is, as a fraction of its period.

```
u = (now − next due at 06:00) / period          (in days)
```

- **−1** = just done, **0** = due now, **+1** = a whole period late.
- A task becomes due at **06:00 local time** on its due day, so the list doesn't flip at midnight.
- The score changes continuously through the day.

Urgency sorts the list, picks the colour, and fills the quadrant mark.

### Next due

1. If the task has been done: last done + period.
2. Else, if a first due date is set: that date.
3. Else: created + period.

### Sorting

Highest urgency first; ties go to the earlier next-due date, then the name (A–Z, ignoring case).
Tasks with no due date at all (possible only in imported data) go last.

This matches the order the *Regularly* app shows (as observed by using it), so the list feels
familiar to people coming from it.

## Data model

Four SQLite tables (Room), storing dates as ISO `YYYY-MM-DD` text with no time of day.

```sql
create table tasks (
  _id integer primary key autoincrement,
  name text not null,
  period integer not null,          -- days, at least 1
  firstdue text,                    -- optional first due date
  lastperformed text,               -- newest log entry's date, kept in sync
  lastnotified text,                -- unused; kept for backup compatibility
  created text not null default '',
  details text not null,
  notifications_enabled integer not null,  -- unused; kept for backup compatibility
  notifications_time text,                 -- unused
  notifications_period integer             -- unused
);
create table log (
  _id integer primary key autoincrement,
  taskid integer not null,
  entrydate text not null default '',
  note text not null
);
create table tags (                 -- not used by the app; kept for backup compatibility
  _id integer primary key autoincrement,
  tagname text not null,
  position integer not null
);
create table tasktags (             -- not used by the app; kept for backup compatibility
  _id integer primary key autoincrement,
  taskid integer not null,
  tagid integer not null
);
```

- Every log change updates `lastperformed` in the same transaction (NULL when the log is empty).
- Deleting a task deletes its log entries and tag links.
- **Compatibility:** this is the same schema as the *Regularly* app's backup file (`regularly.db`,
  `user_version` 8). Quadrant Two Do reads and writes that format so people can move their history
  between the two apps. That is the only reason for the unused columns and the tag tables.

## How things are shown

### Day counts

Every number of days is shown as years (365 days), weeks and days, leaving out zero parts:
401 days is **1y 5w 1d**, 14 days is **2w**, 3 days is **3d**.

- Due text: "due today", "due tomorrow", "due yesterday", "due in 1w 2d", "due 46w ago".
- Log timing, for each entry compared with the one before it: "baseline" (the first entry),
  "on schedule", "2d late", "1d early". An entry without a readable date (possible only in imported
  data) shows no timing and is skipped when comparing the others.

### Colours

There's a dark theme ("Dusk") and a light one ("Day"). By default the app follows the phone's
dark-mode setting; Settings can fix it to either.

| Role | Dusk (dark) | Day (light) |
| --- | --- | --- |
| Background | `#12161D` slate | `#F7F8FA` off-white |
| Surfaces / dialogs | `#1B212B` | `#ECEFF3` |
| Accent | `#5EC4B6` sea-glass teal | `#1A7F72` deep teal |
| Text / secondary text | `#E8ECF1` / `#8B95A5` | `#1A2029` / `#5B6575` |

The urgency colour sweeps through a **colour scheme**: one colour when just done, one when due, one
once a whole period late, blended in between (the shorter way round the colour wheel; a grey end
just fades the other colour). Each scheme colour is a hue and a saturation; the theme sets the
brightness: value 0.93 in Dusk, and in Day 0.62 with saturation × 1.5. Each colour is then made
just dark enough (Day) or bright enough (Dusk) to reach 4.6:1 contrast with the background, so
every scheme reads as text in both themes. A unit test checks every scheme, plus the secondary text
and the accent, against the WCAG AA minimum of 4.5:1.

| Scheme | Just done → due → late |
| --- | --- |
| Dusk (default) | teal 170° → amber 40° → coral 5° |
| Traffic light | green → amber → red |
| Blue and orange | blue → grey → orange; easier with colour blindness |
| Sunset | gold → orange → magenta |
| Forest | green → yellow-green → rust |
| Ocean | aqua → blue → indigo; no alarm colours |
| Berry | violet → pink → crimson |
| Fire | yellow → orange → red |
| Monochrome | teal, from nearly grey to strong |

Tasks with no due date use the secondary text colour. The system font is used throughout, light
weight for body text.

### The quadrant mark

Each task has a small **quadrant mark** in its urgency colour: a 2×2 grid, after the Eisenhower
matrix. One cell fills for each quarter of the period since the task was done: top-left,
bottom-left, bottom-right, and finally top-right (quadrant two: important, not urgent) when it falls
due. Overdue tasks stay full, and the colour shows how late. Empty cells are a faint version of the
colour. The mark is also the **done button**: tapping it asks "Log *task* as done today?" and logs
it on confirmation.

The same mark, three-quarters full in teal (quadrant two still empty), is the app's logo: the
launcher icon and the logo next to the title.

## Screens

### Task list

- Title bar: quadrant mark + app name, **+** (new task), ⋮ (Import backup…, Export backup…, Settings;
  debug builds also have "Load sample backup").
- One row per task: quadrant mark (tap to log), name, due text in the urgency colour.
- Tap a row to open it. Long-press to select several, then **Log** (done today) or **Delete**, each
  with a confirmation.

### Task details

- **Name** and **Details** text fields; changes save when you leave the screen.
- **Period** opens a dialog with **Years**, **Weeks** and **Days** boxes, pre-filled from the current
  period, with the total shown underneath ("= 401 days"). The total must be at least one day.
- **Next due** shows the date and due text; tap to set or clear a first due date (used until the task
  is first done).
- **Last done**, and the **Log**: "Add log entry" offers Today, Yesterday or any picked date
  (**future dates are allowed**). Tap an entry to change its date or note, or delete it; long-press to
  select several to delete; ⋮ → Clear task log.
- An existing task has a delete button. A new task doesn't: going back saves it, or offers to
  discard it if it has no name.

### Settings

Every change is saved straight away.

- **Theme:** System (follow the phone's dark mode, the default), Light or Dark. On Android 12+ the
  choice is also given to the system, so the launch screen matches.
- **Widget background:** how solid the widget's panel is, 0–100% in 5% steps (default 70%).
  Over a dark wallpaper the light theme needs a high value, and over a light one the dark theme does.
- **Colours:** the urgency colour scheme (see Colours), each shown with five sample marks.

### Widget

A scrolling list of every task, sorted like the app: name on the left, due text on the right in its
urgency colour, on a panel of the app's background at the opacity chosen in Settings. It follows the
theme setting (with System, the launcher picks light or dark colours itself).

Like the system's own widgets (Calendar, Gmail), the panel fills all the space the launcher gives
it, with the system's widget corner radius on Android 12+, so it lines up with widgets beside it.
The list sits 14dp in from the sides and 10dp from the top and bottom, clear of the rounded corners.
Rows are compact (16sp names, 13sp due text, 3dp row padding) so a 2-row widget fits about seven
tasks. Tapping opens the app. Two sizes: small (110 × 40 dp, fits 2×1) and medium (250 × 110 dp,
also allowed on the lock screen); both resize.

It is redrawn on every change to the tasks, the log or the settings while the app runs, every 4
hours (WorkManager), and just after 06:00 each day when tasks become due.

## Backups and moving to a new phone

### Automatic

Android's own backup carries the task database and the settings to a new phone: Google cloud
backup and the device-to-device transfer during phone setup. The rules (`data_extraction_rules.xml`,
`backup_rules.xml`) include only `quadranttwodo.db`, its write-ahead log and the settings file
(`datastore/settings.preferences_pb`); scheduled-job and widget state are per-device and rebuild
themselves. Exported backup files hold only the tasks and log, not the settings. This works as long as the user has backup turned on.

### Export

⋮ → Export backup… writes a backup file through the system file picker. The suggested name is the
app's name and the date (`quadrant-two-do-2026-11-19.db`). The file is a plain SQLite database in the
compatible format above: the four tables, `user_version` 8, original row ids, unused columns included.

### Import

⋮ → Import backup… reads a picked file, checks it (it must be SQLite with `tasks` and `log` tables),
shows what it contains, then offers:

- **Merge** (default): tasks are matched by name, ignoring case and surrounding spaces. A task on
  both sides keeps the phone's name, period, first due date and details and gains the backup's log
  entries. Entries are counted per (date, note), so nothing already on the phone is duplicated and
  genuine repeats survive. Backup-only tasks are added; phone-only tasks are untouched. Deletions
  can't be detected, so a log entry deleted on the phone comes back if the backup still has it.
- **Replace all**: everything on the phone is replaced by the backup.

Tasks with period 0 in a backup file are skipped, with their log entries.

## Technology

Kotlin, Jetpack Compose (Material 3), Room, `java.time`, Glance (widget) and WorkManager (widget
refreshes). minSdk 26, targetSdk 36. Package `io.github.gasserma.quadranttwodo`. No runtime permissions.

| Area | Code |
| --- | --- |
| Urgency, next due, sorting, wording, colours, settings model | `core/` |
| Database, backups, import/merge, settings storage (DataStore) | `data/` |
| Screens | `ui/list`, `ui/edit`, `ui/settings` |
| Widget | `widget/` |

## Testing

- **Unit and UI tests** (JVM + Robolectric, `./gradlew :app:testDebugUnitTest`): core logic, database
  rules, backup round trips and merging, the screens' main flows, the period boxes, and the widget.
- **Sample data:** a real backup file, `regularly.db` (22 tasks, 7,188 log entries, 2016-03-29 to
  2025-12-30; no tags, notes or period-0 tasks). As of 2026-11-18 08:48 the four daily tasks (Floss,
  Gym, Stick to Cut and Record, Gratitude) top the list at urgency ≈ 322.12 ("due 46w ago"), and Clean
  Grill (365 days, due 2026-11-27) is last at ≈ −0.02.
- **Robolectric quirk:** text fields inside a dialog window never let Compose go idle, so dialog
  contents are tested on their own (see `PeriodFields`).
- **Checked by hand on an emulator:** the system file pickers, widget placement and refresh, and
  backup/restore (backed up with the local test transport, uninstalled, reinstalled: the data came
  back). Not yet tried: device-to-device transfer between two real phones, and whether the Regularly
  app restores a Quadrant Two Do export.

## Open questions

- [ ] Check "Quadrant Two Do" is free on the Play Store before publishing (the package name is
  permanent once published).
- [ ] Launcher icon polish for release; release signing and build.
- [ ] Should tapping a widget row log the task, like the quadrant mark in the app?
