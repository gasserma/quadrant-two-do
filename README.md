# Quadrant Two Do

A small Android checklist for things you do regularly: floss daily, change the furnace filter every
year, see the dentist twice a year. Each task has a period; Quadrant Two Do shows how far through
it you are, puts the most urgent task on top, and keeps a log of when you did it.

### The name

The Eisenhower matrix sorts tasks by whether they're *urgent* and whether they're *important*.
Quadrant II is important but not urgent: flossing, the furnace filter, the dentist. Nothing forces
you to do these today, so they're the first things to slip. This app is a to-do list for quadrant
two: a Quadrant Two Do list. It keeps them in view by showing how close each one is to becoming
urgent.

## Why this exists

I used Regularly for years.
It stopped being supported, so I wanted a replacement with the subset of its features I
actually used, and two things above all:

- **Import my `regularly.db`** so years of history come along.
- **Export a file that should be Regularly-compatible**, in case Regularly is ever picked back up
  again.

Quadrant Two Do isn't affiliated with Regularly or its author. It just reads and writes the same backup
format. Everything that matches Regularly (the backup format, the sort order, tasks falling due at
06:00) was worked out by using the app and reading the backup files it writes. No Regularly code was
used, decompiled or copied.

## Your data

### Importing from Regularly

In Regularly, back up your data; it produces a `regularly.db` file. Copy that file to your phone, then
in Quadrant Two Do use **⋮ → Import backup…** and pick it. You'll see what the file contains and can
choose:

- **Merge** (default): tasks are matched by name. Tasks you already have keep their settings and gain
  the backup's log entries; new tasks are added. Nothing is duplicated.
- **Replace all**: everything in Quadrant Two Do is replaced by the backup.

Regularly's tasks without a period ("irregular") are skipped, since Quadrant Two Do doesn't have them.
Tags are imported but not shown.

Quadrant Two Do also has no notifications, and it's entirely possible I've missed other Regularly
features as well.

### Exporting

**⋮ → Export backup…** saves a single file (e.g. `quadrant-two-do-2026-11-19.db`). It's a plain SQLite
database using Regularly's exact schema: the same four tables, schema version 8, and the original row
ids, with Regularly's unused columns and tag tables kept intact. That's what *should* make it
restorable in Regularly, though I haven't been able to test that against Regularly itself.

The same file can be imported back into Quadrant Two Do on another phone.

### Moving to a new phone

Android's own backup (Google backup and the transfer during new-phone setup) carries the task database
to the new phone automatically, as long as backup is turned on. Export is the manual fallback.

## Building

Requirements: Android Studio (or a JDK 17+; Android Studio's bundled `jbr` works) and the Android SDK
with platform 37.

```sh
./gradlew :app:assembleDebug          # build app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:testDebugUnitTest      # run the unit and UI tests (JVM + Robolectric)
```

On Windows, point `JAVA_HOME` at Android Studio's `jbr` folder if no JDK is installed.

The app targets Android 8.0 (API 26) and up.

## Project layout

| Path | What |
| --- | --- |
| `app/src/main/java/.../core` | Urgency, due dates, sorting, wording and colours (plain Kotlin) |
| `app/src/main/java/.../data` | Room database, backup file format, import/merge, export |
| `app/src/main/java/.../ui` | Compose screens: task list, task details |
| `app/src/main/java/.../widget` | Glance home-screen widget |
| `BLUEPRINT.md` | Design notes: how urgency, sorting, colours, backups and merging work |

Debug builds include a sample backup and a **⋮ → Load sample backup** item, so the UI can be tried
with real data.

## Built with

Kotlin, Jetpack Compose (Material 3), Room, Glance and WorkManager.

## License

[0BSD](LICENSE) (Zero-Clause BSD): use it for anything, no attribution required.
