# Timesheet — Android App Specification

| | |
|---|---|
| **Version** | 1.0 (draft) |
| **Date** | 2026-10-16 |
| **Platform** | Android (phone first; tablet supported) |
| **Status** | For review |

---

## 1. Purpose

Timesheet lets an engineer log, every working day, how their hours were spent across projects, and which time-booking system each block of hours goes into (**NTIME** or **P6**). The working day has a fixed number of available hours (default **8**). Any hours not booked against a project are automatically assigned to **Overhead** (Department Hours). The app then rolls everything up into daily, weekly and monthly totals on a dashboard, so the user can copy the numbers into NTIME and P6 without doing arithmetic by hand.

### 1.1 Goals

1. Entering a day's hours takes under 30 seconds for a returning project.
2. The user never has to calculate overhead themselves.
3. At any time, the user can see per-project, per-system and overhead totals for today, this week and this month.

### 1.2 Non-goals (v1)

- Direct integration with NTIME, P6 or Oracle (no API sync; the app is a personal ledger).
- Multi-user accounts, approvals or manager workflows.
- Payroll, billing rates or cost calculation.

---

## 2. Glossary

| Term | Meaning |
|---|---|
| **Project** | A piece of work hours are booked against. Identified by Project Name, Project Number and Oracle Code. |
| **Project Number** | The company's project identifier (free text, e.g. `PRJ-2041`). |
| **Oracle Code** | The finance/task code used in Oracle for the project (free text, e.g. `4100.22.07`). |
| **NTIME** | Time-booking system #1. A project entry can be tagged for NTIME. |
| **P6** | Time-booking system #2 (Primavera P6). A project entry can be tagged for P6. |
| **Booking System** | Either `NTIME` or `P6`; chosen per entry. |
| **Available Hours** | Hours available in a working day. Default **8.0**, configurable in Settings. |
| **Overhead** (Department Hours) | `Available Hours − sum of project hours` for that day. Calculated, never typed in. |
| **Entry** | One line in a day: a project + hours + booking system. |
| **Day Sheet** | All entries for one calendar date plus its calculated overhead. |

---

## 3. Functional Requirements

### FR-1 Daily entry

For a chosen date (default: today) the user adds one or more **entries**. Each entry has:

| Field | Type | Required | Rules |
|---|---|---|---|
| Project Name | Text | Yes | 1–100 chars. Dropdown of saved projects (see FR-4.5); typing filters the list or enters a new project. |
| Project Number | Text | Yes | 1–30 chars. Auto-filled when a saved project is picked. |
| Oracle Code | Text | Yes | 1–30 chars. Auto-filled when a saved project is picked. |
| Task Description | Text (multi-line) | Yes | 1–200 chars. What was worked on, e.g. "Reviewed piping layout drawings". |
| Manhours | Decimal | Yes | > 0, step **0.5** (configurable to 0.25), max = remaining hours of the day. |
| Booking System | Choice: `NTIME` / `P6` | Yes | Segmented toggle. Defaults to the last system used for that project. |

- **FR-1.1** A day may contain any number of entries, including several for the same project (e.g. 2 h in NTIME and 1 h in P6 for Project A). On the dashboard these are summed per project and also split per system.
- **FR-1.2** Entries can be edited and deleted. Totals recalculate immediately. Each entry is saved as soon as it is confirmed, which also creates the day's Day Sheet; **Save day** is only needed to log a day with no project entries (a full day of overhead) or a non-working day.
- **FR-1.3** The user can pick any past date or the current date. Future dates are blocked by default (setting: *Allow future dates*, off).
- **FR-1.4** "Copy previous day" button: copies the most recent day's entries into the selected date as a starting point.
- **FR-1.5** Entry form shows a live hours bar: `Project hours | Overhead | Available`.

### FR-2 Overhead calculation

For each day that has been **saved** (i.e. is a working day with a Day Sheet):

```
projectHours(day)  = Σ entry.hours for that day
overhead(day)      = max(0, availableHours − projectHours(day))
```

- **FR-2.1** Overhead is read-only and always derived; it is never stored as a user value (it may be cached, but must be recomputed when entries or Available Hours change).
- **FR-2.2** Total project hours for a day **cannot exceed** Available Hours. The Save button is disabled and an inline error is shown: *"Project hours (8.5) exceed available hours (8.0)."*
  - Setting *Allow overtime* (off by default): when on, project hours may exceed Available Hours, overhead is 0, and the excess is reported as **Overtime** on the dashboard.
- **FR-2.3** Overhead is **not** tagged with NTIME or P6. It is its own bucket ("Overhead / Department Hours").
  - Setting *Overhead booking system* (default: *None*): if the user's department books overhead into one of the systems, they can pick `NTIME` or `P6` so overhead is included in that system's totals.
- **FR-2.4** A day with no Day Sheet contributes **0** to all totals (overhead is not auto-filled for days the user didn't work or didn't log).
- **FR-2.5** Day types: `Working` (default), `Leave`, `Public Holiday`, `Weekend`. Only `Working` days produce overhead. A `Working` day saved with zero entries = full day of overhead (e.g. 8 h).

#### Worked example (from the requirement)

| Entry | Hours | System |
|---|---|---|
| Project A | 4.0 | NTIME |
| Project B | 3.0 | P6 |
| **Overhead** (calculated) | **1.0** | — |
| **Day total** | **8.0** | |

Day breakdown shown to user: `NTIME 4.0 · P6 3.0 · Overhead 1.0 = 8.0 / 8.0`.

### FR-3 Dashboard (auto-calculated totals)

The Dashboard is the home screen. It has a period selector — **Day / Week / Month** — and arrows to move to the previous/next period. All values update immediately after any entry is added, edited or deleted.

For the selected period it shows:

1. **Summary cards**
   - Total hours logged (projects + overhead)
   - NTIME total
   - P6 total
   - Overhead total
   - (If enabled) Overtime total
   - Utilisation % = project hours ÷ (project hours + overhead)
2. **Per-project table**

   | Project | Number | Oracle Code | NTIME | P6 | Total |
   |---|---|---|---|---|---|
   | Project A | PRJ-2041 | 4100.22.07 | 12.0 | 0.0 | 12.0 |
   | Project B | PRJ-2055 | 4100.31.02 | 0.0 | 9.0 | 9.0 |
   | **Overhead** | — | — | — | — | 3.0 |
   | **Total** | | | 12.0 | 9.0 | 24.0 |

   Sorted by Total descending; Overhead row always last before Total.
3. **Chart**
   - Day: horizontal stacked bar (projects + overhead) against Available Hours.
   - Week: one stacked column per day (Mon–Sun or per week-start setting), coloured NTIME / P6 / Overhead.
   - Month: one stacked column per week of the month, plus a donut of project share.
4. **Missing days indicator** (Week/Month): working days with no Day Sheet are listed ("3 days not logged") and tapping one opens the Entry screen for that date.

#### Period definitions

| Period | Range |
|---|---|
| Day | The selected calendar date. |
| Week | 7 days starting on the configured **Week start day** (default **Monday**; Sunday and Saturday selectable). |
| Month | 1st to last day of the calendar month. |

Aggregation formulas for period `P`:

```
projectTotal(p, P)         = Σ hours of entries for project p in P
projectSystemTotal(p,s,P)  = Σ hours of entries for project p with system s in P
systemTotal(s, P)          = Σ hours of entries with system s in P
overheadTotal(P)           = Σ overhead(day) for working days in P with a Day Sheet
grandTotal(P)              = Σ projectTotal + overheadTotal
```

### FR-4 Project list (master data)

- **FR-4.1** Projects are saved automatically the first time they are used in an entry, and can also be managed under *Projects*: add, edit, archive.
- **FR-4.2** Uniqueness key: **Project Number + Oracle Code**. Entering an existing combination with a different name prompts: *"Update project name?"*
- **FR-4.3** Archived projects are hidden from autocomplete but keep their history on the dashboard.
- **FR-4.4** Editing a project's name/number/code updates how past entries are displayed (entries reference the project by ID).
- **FR-4.5** Project name, number and Oracle code are stored once entered. When adding a new entry, the user can pick a saved project from a dropdown on the Project Name field instead of retyping; picking fills in the number, Oracle code and last-used booking system. Entering a new name, number and code still works and saves the new project.

### FR-5 History & export

- **FR-5.1** *History* screen: calendar month view; each day shows a dot coloured by status (complete = 8 h logged incl. overhead, not logged, leave/holiday).
- **FR-5.2** Export of any Day / Week / Month (or custom range) to **CSV** and **PDF**, shared through the Android share sheet. CSV columns:
  `Date, Day Type, Project Name, Project Number, Oracle Code, Task Description, System, Hours`
  with overhead exported as rows where `Project Name = Overhead`, `Task Description = Department Hours`, `System = ` (blank or the configured overhead system).
- **FR-5.3** Copy-to-clipboard of the period summary in plain text, grouped by system, to paste into NTIME/P6.

### FR-6 Settings

| Setting | Default | Options |
|---|---|---|
| Available hours per day | 8.0 | 1.0 – 24.0, step 0.5 |
| Hours step | 0.5 | 0.25, 0.5, 1.0 |
| Week start day | Monday | Saturday, Sunday, Monday |
| Weekend days | Sat, Sun | Any combination |
| Allow overtime | Off | On/Off |
| Overhead booking system | None | None, NTIME, P6 |
| Allow future dates | Off | On/Off |
| Daily reminder | Off, 17:00 | On/Off + time |
| Theme | System | Light, Dark, System |
| Backup / restore | — | Export/import JSON file |

- **FR-6.1** Changing *Available hours* applies **from that date forward**. Each Day Sheet stores the Available Hours in effect when it was created, so historical overhead does not change.

### FR-7 Reminders

- Optional daily notification at the configured time on working days if today has no Day Sheet: *"You haven't logged today's hours."* Tapping opens today's Entry screen.

---

## 4. Screens & Navigation

Bottom navigation: **Dashboard · Add Entry · History · Projects**; Settings via top-bar overflow.

### 4.1 Dashboard
- Top: period selector chips (Day / Week / Month), date range label with ‹ › arrows; tap the label to open a date picker.
- Summary cards (2×2 grid on phone).
- Chart.
- Per-project table.
- FAB: **+ Add entry** (for the selected day, or today).

### 4.2 Day Entry
```
┌──────────────────────────────────────┐
│ ‹  Thu, 16 Oct 2026  ›   [Working ▾] │
│ ███████████████▓▓▓▓░░░               │
│ Projects 7.0  · Overhead 1.0 / 8.0   │
├──────────────────────────────────────┤
│ Project A   PRJ-2041 · 4100.22.07    │
│ 4.0 h                  [NTIME]  ✎ 🗑 │
│ Project B   PRJ-2055 · 4100.31.02    │
│ 3.0 h                     [P6]  ✎ 🗑 │
├──────────────────────────────────────┤
│ Overhead (auto)              1.0 h   │
├──────────────────────────────────────┤
│ [+ Add project]   [Copy previous day]│
│              [ Save day ]            │
└──────────────────────────────────────┘
```

### 4.3 Add/Edit Entry (bottom sheet)
- Project Name (dropdown of saved projects, or type a new one; picking fills Number + Oracle Code)
- Project Number
- Oracle Code
- Task Description (multi-line, 200 chars)
- Hours: stepper (− / +) with numeric input; shows "Remaining: x h"
- Booking System: segmented button `NTIME | P6`
- Buttons: Cancel / Save

### 4.4 History
- Month calendar with status dots; tap a day → Day Entry.

### 4.5 Projects
- Searchable list; tap to edit; swipe to archive; "Show archived" toggle.
- Project detail: lifetime hours, NTIME vs P6 split, last 12 weeks mini-chart.

### 4.6 Settings
- As listed in FR-6.

---

## 5. Data Model (Room / SQLite)

```kotlin
@Entity(
    tableName = "project",
    indices = [Index(value = ["projectNumber", "oracleCode"], unique = true)]
)
data class Project(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val projectNumber: String,
    val oracleCode: String,
    val lastSystem: BookingSystem? = null,
    val archived: Boolean = false,
    val createdAt: Instant,
    val updatedAt: Instant,
)

enum class BookingSystem { NTIME, P6 }
enum class DayType { WORKING, LEAVE, PUBLIC_HOLIDAY, WEEKEND }

@Entity(tableName = "day_sheet")
data class DaySheet(
    @PrimaryKey val date: LocalDate,          // one sheet per calendar date
    val dayType: DayType = DayType.WORKING,
    val availableMinutes: Int,                // snapshot of the setting (FR-6.1), 8 h = 480
    val note: String? = null,
    val updatedAt: Instant,
)

@Entity(
    tableName = "time_entry",
    foreignKeys = [
        ForeignKey(DaySheet::class, ["date"], ["date"], onDelete = CASCADE),
        ForeignKey(Project::class, ["id"], ["projectId"], onDelete = RESTRICT),
    ],
    indices = [Index("date"), Index("projectId")]
)
data class TimeEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: LocalDate,
    val projectId: Long,
    val minutes: Int,                         // 4.5 h = 270, see note
    val system: BookingSystem,
    val description: String,                 // task description, 1–200 chars
    val createdAt: Instant,
    val updatedAt: Instant,
)
```

**Note on precision:** hours are persisted as **integer minutes** (e.g. 4.5 h → 270) to avoid floating-point rounding errors in sums; converted to hours with one/two decimals only for display.

Overhead is **derived**, not a table:

```kotlin
fun overheadMinutes(sheet: DaySheet, entries: List<TimeEntry>): Int =
    if (sheet.dayType != DayType.WORKING) 0
    else maxOf(0, sheet.availableMinutes - entries.sumOf { it.minutes })

fun overtimeMinutes(sheet: DaySheet, entries: List<TimeEntry>): Int =
    maxOf(0, entries.sumOf { it.minutes } - sheet.availableMinutes)
```

Key DAO queries (reactive `Flow`s so the dashboard auto-updates):

```sql
-- per project & system for a date range
SELECT p.id, p.name, p.projectNumber, p.oracleCode, e.system, SUM(e.minutes) AS minutes
FROM time_entry e JOIN project p ON p.id = e.projectId
WHERE e.date BETWEEN :from AND :to
GROUP BY p.id, e.system;

-- overhead for a date range
SELECT SUM(MAX(0, d.availableMinutes - IFNULL(t.used, 0)))
FROM day_sheet d
LEFT JOIN (SELECT date, SUM(minutes) used FROM time_entry GROUP BY date) t ON t.date = d.date
WHERE d.date BETWEEN :from AND :to AND d.dayType = 'WORKING';
```

---

## 6. Architecture & Technology

| Concern | Choice |
|---|---|
| Language | Kotlin 2.x |
| UI | Jetpack Compose, Material 3 |
| Architecture | MVVM + unidirectional data flow (ViewModel exposes `StateFlow<UiState>`) |
| Persistence | Room (SQLite), DataStore (Preferences) for settings |
| DI | Hilt |
| Async | Kotlin Coroutines + Flow |
| Dates | `java.time` (`LocalDate`, `WeekFields`) |
| Charts | Vico (Compose charts) |
| Background | WorkManager (reminders) |
| Export | CSV via `OutputStream`; PDF via `android.graphics.pdf.PdfDocument`; share via `FileProvider` |
| Min / target SDK | minSdk 26 (Android 8.0) / targetSdk 35 |
| Offline | Fully offline; no network permission required |

Module/package layout:

```
app/
 ├─ data/        (Room entities, DAOs, repositories, DataStore)
 ├─ domain/      (TimesheetCalculator, use cases, period ranges)
 ├─ ui/
 │   ├─ dashboard/
 │   ├─ entry/
 │   ├─ history/
 │   ├─ projects/
 │   └─ settings/
 └─ work/        (ReminderWorker)
```

`TimesheetCalculator` (pure Kotlin, no Android dependencies) owns all overhead and aggregation logic so it is fully unit-testable.

---

## 7. Validation & Edge Cases

| Case | Behaviour |
|---|---|
| Project hours sum > Available Hours | Block save with inline error (unless *Allow overtime*). |
| Project hours sum = Available Hours | Overhead = 0. Valid. |
| Working day saved with no entries | Overhead = full Available Hours. |
| Hours = 0 or negative | Field error: "Hours must be greater than 0." |
| Hours not a multiple of step | Rounded to nearest step on blur, with snackbar notice. |
| Same project twice in a day, same system | Allowed; prompt "Merge with existing entry?" (default: merge). Merging joins the task descriptions with "; ". |
| Same project, different systems | Allowed; kept as separate entries. |
| Day type changed from Working → Leave | Existing entries kept but warning shown; overhead becomes 0. |
| Delete project that has entries | Not allowed; offer Archive instead. |
| Available Hours setting changed | Applies to new Day Sheets only; existing sheets keep their snapshot. Option in Settings: "Apply to all days from date…" |
| Week spanning two months | Week view shows full week; Month view counts only days inside the month. |
| Time zone / date change | Dates are local calendar dates; no time-of-day stored on entries. |

---

## 8. Non-Functional Requirements

- **Performance:** Dashboard renders a month view in < 300 ms with 2 years of data (~5,000 entries).
- **Reliability:** All writes are transactional (Day Sheet + entries saved atomically). No data loss on process death; unsaved form state survives rotation via `SavedStateHandle`.
- **Privacy:** Data stays on device. Android Auto Backup enabled for the database; manual JSON backup/restore available.
- **Accessibility:** TalkBack labels on all controls, minimum 48 dp touch targets, colours for NTIME / P6 / Overhead distinguishable in colour-blind modes and always paired with text labels.
- **Localisation:** Strings externalised; decimal separator follows device locale; RTL layout supported.

---

## 9. Acceptance Criteria

1. **Overhead basic:** Given Available Hours = 8, when the user saves Project A 4 h (NTIME) and Project B 3 h (P6), then the day shows NTIME 4.0, P6 3.0, Overhead 1.0, Total 8.0.
2. **Full overhead:** Given a Working day with no entries is saved, the dashboard shows Overhead 8.0 for that day.
3. **Over-allocation blocked:** Given entries totalling 7.5 h, adding a 1 h entry is rejected with the "exceed available hours" error.
4. **Split system per project:** Given Project A 2 h NTIME and Project A 2 h P6 on the same day, the per-project table shows Project A: NTIME 2.0, P6 2.0, Total 4.0, and Overhead 4.0.
5. **Weekly roll-up:** Given 5 working days each with Project A 4 h NTIME and Project B 3 h P6, the week view shows Project A 20.0, Project B 15.0, NTIME 20.0, P6 15.0, Overhead 5.0, Total 40.0.
6. **Monthly roll-up:** Month totals equal the sum of all Day totals within the calendar month, including partial weeks.
7. **Live update:** Editing an entry from 4 h to 5 h immediately changes the day's overhead from 1.0 to 0.0 and updates week/month totals without leaving the screen.
8. **Unlogged days:** A working day with no Day Sheet adds 0 to totals and appears under "days not logged".
9. **Leave day:** A day marked Leave contributes 0 overhead.
10. **Settings snapshot:** Changing Available Hours to 9 does not change overhead on previously saved days.
11. **Export:** CSV export of a week contains one row per entry plus one Overhead row per working day with overhead > 0, and the sum of the Hours column equals the dashboard Total.

---

## 10. Test Plan (summary)

- **Unit tests** (`TimesheetCalculator`): overhead, overtime, per-period aggregation, week boundaries for each week-start option, month boundaries, leap years, minute/hour conversion.
- **DAO tests** (Room in-memory): aggregation queries, cascade delete, unique project key.
- **UI tests** (Compose testing): add/edit/delete entry, validation errors, dashboard updates after entry.
- **Manual:** rotation, process death, dark mode, TalkBack, RTL.

---

## 11. Release Plan

| Milestone | Scope |
|---|---|
| **M1 — MVP** | FR-1, FR-2, FR-3 (Day/Week/Month cards + table), FR-4 basic, Settings (available hours, week start). |
| **M2** | Charts, History calendar, Copy previous day, Day types, Reminders. |
| **M3** | CSV/PDF export, clipboard summary, backup/restore, overtime & overhead-system settings. |

---

## 12. Open Questions

1. Should overhead be booked into a specific system (NTIME or P6) by default, or kept separate? (Spec default: separate, configurable.)
2. Is overtime ever allowed, or must a day always total exactly 8 h? (Spec default: blocked, configurable.)
3. Which day does the work week start on — Sunday or Monday? (Spec default: Monday, configurable.)
4. Should Leave / Public Holiday days count as overhead (e.g. 8 h Department Hours) or as zero? (Spec default: zero.)
5. Are Project Number and Oracle Code formats fixed (e.g. regex) so they can be validated?
6. Is a half-hour granularity sufficient, or are quarter-hours needed?
