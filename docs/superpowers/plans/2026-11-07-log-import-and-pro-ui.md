# TowerDost Log Import & Professional Industrial UI/UX Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement site log data import (CSV, XLSX, JSON) and full device migration backup/restore, while overhauling the app's visual design into a high-contrast, non-AI-slop industrial field tool using bundled IBM Plex typography.

**Architecture:** A standalone `LogImporter` parses CSV/XLSX/JSON log records into Room `IncidentEntity` with flexible timestamp parsing and deduplication. A `BackupManager` provides 1-tap full JSON backup/restore across devices. The UI layer updates theme colors to industrial slate and amber with 1dp structural hairline borders, binds real IBM Plex fonts in `Type.kt`, and flattens cards across all screens to eliminate pastel washes.

**Tech Stack:** Kotlin, Jetpack Compose, Material 3, Room SQLite, Apache POI / XML streaming (XlsxReader), Java Time desugaring, JUnit 4.

**Spec:** `docs/superpowers/specs/2026-11-07-log-import-and-pro-ui-design.md`

## Global Constraints
- Offline-Only: Zero `INTERNET` permission in Manifest or build scripts.
- MinSdk 24, TargetSdk 35; all `java.time` operations must use desugaring.
- Tabular Precision: SAP IDs, timestamps, downtime calculations, and coordinates use monospace tabular figures.
- High-Sunlight Contrast: Minimum 7:1 contrast for text; 1dp hairline borders (`#24334A` in dark mode, `#CBD5E1` in light mode); no washed-out pastel fills.
- Tactile Targets: 48dp minimum touch targets for all primary buttons, tabs, chips, and interactive list cards.
- Existing Features Intact: All outage register, reason catalogue, and site inventory features remain intact.

## Review Focus
1. **Minute vs Millisecond Timestamp Deduplication:** CSV exports format timestamps to the minute (`dd/MM/yyyy HH:mm`) while stored logs use milliseconds. Re-importing an exported CSV must match existing logs within a ±60-second window or via ticket_ref/id so duplicate logs are not created.
2. **Flexible DateTime Parsing:** External sheets contain diverse date formats (`dd/MM/yyyy HH:mm`, `yyyy-MM-dd HH:mm:ss`, `dd MMM yyyy, HH:mm`, epoch seconds/ms). The parser must resolve each without throwing unhandled `DateTimeParseException`.
3. **Database Restore Integrity:** Full database restore must execute inside a single Room transaction; if clean overwrite is selected, old sample demo sites must be cleared and FTS search index must be rebuilt.
4. **Font Asset Binding:** IBM Plex Sans and IBM Plex Mono fonts in `res/font/` must be explicitly bound with weights (Regular, Medium, Bold) rather than falling back to system generic fonts.
5. **No Broken Touch Targets:** Replacing card layouts must maintain at least 48dp minimum clickable height on all buttons, filter chips, and action icons.

---

### Task 1: Flexible DateTime Parser & Timestamp Utilities

**Files:**
- Modify: `app/src/main/java/app/telecom/sitetracker/util/Dates.kt`
- Test: `app/src/test/java/app/telecom/sitetracker/DatesTest.kt`

**Interfaces:**
- Produces: `Dates.parseFlexibleDateTime(raw: String?): Long?`
- Produces: `Dates.isWithinMinute(ms1: Long, ms2: Long): Boolean`

- [ ] **Step 1: Write the failing unit tests for flexible date parsing**

In `app/src/test/java/app/telecom/sitetracker/DatesTest.kt`, add tests:
```kotlin
@Test
fun testParseFlexibleDateTime_variousFormats() {
    // dd/MM/yyyy HH:mm
    val t1 = Dates.parseFlexibleDateTime("15/08/2026 14:30")
    assertNotNull(t1)

    // dd/MM/yyyy HH:mm:ss
    val t2 = Dates.parseFlexibleDateTime("15/08/2026 14:30:45")
    assertNotNull(t2)

    // yyyy-MM-dd HH:mm:ss
    val t3 = Dates.parseFlexibleDateTime("2026-08-15 14:30:00")
    assertNotNull(t3)

    // yyyy-MM-dd'T'HH:mm:ss
    val t4 = Dates.parseFlexibleDateTime("2026-08-15T14:30:00")
    assertNotNull(t4)

    // dd MMM yyyy, HH:mm
    val t5 = Dates.parseFlexibleDateTime("15 Aug 2026, 14:30")
    assertNotNull(t5)

    // Raw epoch ms
    val now = 1786780000000L
    val t6 = Dates.parseFlexibleDateTime(now.toString())
    assertEquals(now, t6)

    // Null or blank
    assertNull(Dates.parseFlexibleDateTime(null))
    assertNull(Dates.parseFlexibleDateTime(""))
    assertNull(Dates.parseFlexibleDateTime("invalid_date"))
}

@Test
fun testIsWithinMinute() {
    val base = 1786780000000L
    assertTrue(Dates.isWithinMinute(base, base + 30000L))
    assertTrue(Dates.isWithinMinute(base, base - 59000L))
    assertFalse(Dates.isWithinMinute(base, base + 61000L))
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `find app/src/test -name DatesTest.kt` (or inspect test file compilation)
Expected: FAIL due to missing `parseFlexibleDateTime` and `isWithinMinute`.

- [ ] **Step 3: Implement `parseFlexibleDateTime` and `isWithinMinute` in `Dates.kt`**

Add implementations to `Dates.kt` using standard `DateTimeFormatter` list in `Asia/Kolkata` zone and try-catch chain, falling back cleanly to epoch numbers or null.

- [ ] **Step 4: Verify test passes**

Run test and confirm assertions succeed.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/app/telecom/sitetracker/util/Dates.kt app/src/test/java/app/telecom/sitetracker/DatesTest.kt
git commit -m "feat: add flexible date-time parser and timestamp proximity check"
```

---

### Task 2: Log & Outage Register Importer (`LogImporter.kt`)

**Files:**
- Create: `app/src/main/java/app/telecom/sitetracker/data/import/LogImporter.kt`
- Test: `app/src/test/java/app/telecom/sitetracker/LogImporterTest.kt`

**Interfaces:**
- Consumes: `AppDatabase`, `AppClock`, `Dates.parseFlexibleDateTime`
- Produces: `LogImporter.executeImport(rows: List<List<String>>, headers: List<String>, policy: MergePolicy): LogImportResult`

- [ ] **Step 1: Write the failing unit test for `LogImporter`**

In `app/src/test/java/app/telecom/sitetracker/LogImporterTest.kt`:
Test importing rows matching `Exporter.exportRegisterCsv` header and data rows.
Test deduplication: re-importing the same rows with `UPDATE_AND_ADD` updates existing records instead of doubling the total incident count.
Test `ADD_NEW_ONLY` skips existing incidents.

- [ ] **Step 2: Run test to verify it fails**

Expected: FAIL with `LogImporter` not found.

- [ ] **Step 3: Implement `LogImporter`**

Define `LogTargetField` enum:
- `SAP_ID`, `TITLE`, `TYPE`, `SEVERITY`, `CATEGORY`, `STATUS`, `DOWN_AT`, `RESTORED_AT`, `ENGINEER`, `TICKET_REF`, `ACTION_TAKEN`, `DESCRIPTION`, `LAT`, `LNG`, `ID`.
Implement header alias matcher (`matchLogField(header: String): LogTargetField?`).
Implement chunked transaction import:
- Query existing incidents for the target SAP IDs.
- Check match by `id`, or by `sapId` + `Dates.isWithinMinute(existing.downAt/occurredAt, incomingDownAt)`, or by matching `ticketRef`.
- Apply `MergePolicy`.
- Insert/update via `incidentDao.insertIncidents`.

- [ ] **Step 4: Run test to verify it passes**

Expected: PASS with 100% of rows mapped and deduplicated correctly.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/app/telecom/sitetracker/data/import/LogImporter.kt app/src/test/java/app/telecom/sitetracker/LogImporterTest.kt
git commit -m "feat: implement LogImporter with smart deduplication and merge policies"
```

---

### Task 3: Full Database Backup & Restore (`BackupManager.kt`)

**Files:**
- Create: `app/src/main/java/app/telecom/sitetracker/data/backup/BackupManager.kt`
- Test: `app/src/test/java/app/telecom/sitetracker/BackupManagerTest.kt`

**Interfaces:**
- Consumes: `AppDatabase`, `AppClock`
- Produces: `BackupManager.createBackupJson(): String`
- Produces: `BackupManager.restoreFromBackupJson(jsonString: String, cleanOverwrite: Boolean): RestoreResult`

- [ ] **Step 1: Write the failing unit test for `BackupManager`**

In `app/src/test/java/app/telecom/sitetracker/BackupManagerTest.kt`:
- Create test database with 2 sites, 3 site fields, 2 incidents, 1 reason.
- Call `createBackupJson()` -> assert json contains `"version": 1`, `"sites"`, `"incidents"`.
- Call `restoreFromBackupJson()` on an empty or pre-existing database with `cleanOverwrite = true`.
- Assert all records are restored identically.

- [ ] **Step 2: Run test to verify it fails**

Expected: FAIL with `BackupManager` missing.

- [ ] **Step 3: Implement `BackupManager`**

Create `BackupManager(db: AppDatabase, clock: AppClock)`:
- `createBackupJson()` uses `org.json.JSONObject` and `org.json.JSONArray` (Android built-in, zero external dependencies).
- Exports:
  - `sites`: sapId, name, lat, lng, circle, region, state, district, city, technology, siteType, status, address, tags, notes.
  - `fields`: sapId, fieldKey, fieldValue, groupName, sortOrder.
  - `incidents`: id, sapId, occurredAt, category, severity, status, isOutage, downAt, restoredAt, title, description, actionTaken, engineer, ticketRef.
  - `reasons`: id, code, label, category, isDefault.
- `restoreFromBackupJson()` executes in `db.withTransaction`:
  - If `cleanOverwrite`: `siteDao.deleteAllSites()`, `incidentDao.deleteAllIncidents()`, `siteDao.clearFts()`.
  - Batch inserts restored sites, fields, incidents, reasons.
  - Rebuilds FTS index for search.

- [ ] **Step 4: Run test to verify it passes**

Expected: PASS with complete round-trip data retention.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/app/telecom/sitetracker/data/backup/BackupManager.kt app/src/test/java/app/telecom/sitetracker/BackupManagerTest.kt
git commit -m "feat: implement BackupManager for complete 1-tap phone-to-phone migration"
```

---

### Task 4: Genuine IBM Plex Typography, Industrial Palette & App Logo Asset

**Files:**
- Modify: `app/src/main/java/app/telecom/sitetracker/ui/theme/Type.kt`
- Modify: `app/src/main/java/app/telecom/sitetracker/ui/theme/Color.kt`
- Modify: `app/src/main/java/app/telecom/sitetracker/ui/theme/Theme.kt`
- Add: `app/src/main/res/drawable/ic_towerdost_logo.png`
- Add: `app/src/main/res/mipmap-*/ic_launcher.png` (generated from `~/Downloads/towerdost.svg`)

**Interfaces:**
- Produces: `IbmPlexSansFamily` loaded from `R.font.ibm_plex_sans_*`
- Produces: `IbmPlexMonoFamily` loaded from `R.font.ibm_plex_mono_*`
- Produces: Updated `AppTypography` with authentic font families and tight tabular letter spacing
- Produces: Industrial tokens: `GroundDark`, `SurfaceDark`, `BorderDark`, `TelecomAmber`, `TelemetryCyan`
- Produces: App logo mipmaps & high-res drawable converted from `~/Downloads/towerdost.svg`

- [ ] **Step 1: Convert `~/Downloads/towerdost.svg` into Android mipmaps and high-res drawable**

Using `rsvg-convert`:
- `drawable/ic_towerdost_logo.png` (512x512)
- `mipmap-mdpi/ic_launcher.png` (48x48)
- `mipmap-hdpi/ic_launcher.png` (72x72)
- `mipmap-xhdpi/ic_launcher.png` (96x96)
- `mipmap-xxhdpi/ic_launcher.png` (144x144)
- `mipmap-xxxhdpi/ic_launcher.png` (192x192)
Update `ic_launcher.xml` and `ic_launcher_round.xml` to reference the new logo.

- [ ] **Step 2: Update `Type.kt` to bind bundled IBM Plex fonts**

In `Type.kt`:
```kotlin
val IbmPlexSansFamily = FontFamily(
    Font(R.font.ibm_plex_sans_regular, FontWeight.Normal),
    Font(R.font.ibm_plex_sans_medium, FontWeight.Medium),
    Font(R.font.ibm_plex_sans_bold, FontWeight.Bold)
)

val IbmPlexMonoFamily = FontFamily(
    Font(R.font.ibm_plex_mono_regular, FontWeight.Normal),
    Font(R.font.ibm_plex_mono_medium, FontWeight.Medium),
    Font(R.font.ibm_plex_mono_bold, FontWeight.Bold)
)
```
Set all `TextStyle` entries in `AppTypography`, `SapIdStyle`, and `MetricMonoStyle` to use these font families.

- [ ] **Step 3: Update `Color.kt` and `Theme.kt` with High-Contrast Industrial Tokens**

In `Color.kt`:
```kotlin
// Industrial Palette
val GroundDark = Color(0xFF0A0E17)
val SurfaceDark = Color(0xFF121927)
val SurfaceElevatedDark = Color(0xFF1A2436)
val BorderDark = Color(0xFF24334A)

val GroundLight = Color(0xFFF4F6F9)
val SurfaceLight = Color(0xFFFFFFFF)
val SurfaceElevatedLight = Color(0xFFEAEFF5)
val BorderLight = Color(0xFFCBD5E1)

val TelecomAmber = Color(0xFFF59E0B)
val TelecomAmberDark = Color(0xFFD97706)
val TelemetryCyan = Color(0xFF0EA5E9)

// High-contrast status colors (zero pastel fills)
val StatusDownRed = Color(0xFFEF4444)
val StatusRestoredGreen = Color(0xFF10B981)
val StatusMaintAmber = Color(0xFFF59E0B)
val StatusVisitBlue = Color(0xFF3B82F6)
```
Update `Theme.kt` color schemes to map `outlineVariant` to `BorderDark`/`BorderLight`, `surface` to `SurfaceDark`/`SurfaceLight`, and `primary` to `TelecomAmber`/`TelecomAmberDark`.

- [ ] **Step 4: Verify build / tests**

Run compilation check on theme files to verify all tokens resolve without error.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/app/telecom/sitetracker/ui/theme/Type.kt app/src/main/java/app/telecom/sitetracker/ui/theme/Color.kt app/src/main/java/app/telecom/sitetracker/ui/theme/Theme.kt app/src/main/res/
git commit -m "style: bind genuine IBM Plex fonts, implement high-contrast industrial theme, and add app logo"
```

---

### Task 5: Log Import UI Flow & Tools Screen Migration Actions

**Files:**
- Create: `app/src/main/java/app/telecom/sitetracker/ui/tools/LogImportDialog.kt`
- Modify: `app/src/main/java/app/telecom/sitetracker/ui/tools/ToolsScreen.kt`
- Modify: `app/src/main/java/app/telecom/sitetracker/ui/activity/ActivityScreen.kt`

**Interfaces:**
- Consumes: `LogImporter`, `BackupManager`, `Exporter`
- Produces: Log import launcher and UI preview dialog in `ToolsScreen` and `ActivityScreen`
- Produces: 1-tap "Full Offline Backup" & "Restore Backup" actions in `ToolsScreen`

- [ ] **Step 1: Create `LogImportDialog.kt`**

Build a streamlined dialog/sheet:
- Displays selected file name, record count, and auto-detected column summary (e.g. "SAP ID, Title, Down At, Engineer detected").
- Radio selection for Merge Policy: "Update & Append" vs "Add New Only".
- Progress indicator during execution.
- Result summary: "X logs inserted, Y logs updated".

- [ ] **Step 2: Integrate into `ToolsScreen.kt`**

In `ToolsScreen`:
- Add "Device Migration & Backup" section at top:
  - "Full Backup (.json)": Exports full archive and triggers Android share sheet.
  - "Restore Backup (.json)": File picker for `.json`, confirms overwrite or merge, and restores data.
- In "Data Ingestion" section:
  - Add "Import Site Logs (.csv, .xlsx, .json)" action alongside "Import Master Site Sheet".

- [ ] **Step 3: Integrate Fast Log Import in `ActivityScreen.kt`**

In `ActivityScreen`:
- Add an "Import Logs" icon button to `TopAppBar` actions.
- Picking a CSV or Excel file triggers `LogImportDialog` directly.

- [ ] **Step 4: Verify integration tests**

Verify screen composables render properly and handle file launch events cleanly.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/app/telecom/sitetracker/ui/tools/LogImportDialog.kt app/src/main/java/app/telecom/sitetracker/ui/tools/ToolsScreen.kt app/src/main/java/app/telecom/sitetracker/ui/activity/ActivityScreen.kt
git commit -m "feat: add Log Import dialog, full backup/restore, and activity screen import shortcut"
```

---

### Task 6: Visual & UX Field Polish Across Screens (Anti-Slop Clean-up)

**Files:**
- Modify: `app/src/main/java/app/telecom/sitetracker/ui/sitelist/SiteListScreen.kt`
- Modify: `app/src/main/java/app/telecom/sitetracker/ui/activity/ActivityScreen.kt`
- Modify: `app/src/main/java/app/telecom/sitetracker/ui/home/HomeScreen.kt`
- Modify: `app/src/main/java/app/telecom/sitetracker/ui/outages/DownRegisterScreen.kt`
- Modify: `app/src/main/java/app/telecom/sitetracker/MainActivity.kt`

**Interfaces:**
- Consumes: `AppTypography`, `SapIdStyle`, `MetricMonoStyle`, `BorderDark`, `BorderLight`

- [ ] **Step 1: Polish `SiteListScreen.kt`**

- Flatten site cards: remove default elevation shadows; use `border(1.dp, MaterialTheme.colorScheme.outlineVariant)` and crisp industrial slate ground.
- Status badges: crisp rectangular tags with 4dp radius and high contrast text (no washed-out pastel fills).
- Minimum 48dp height for all list items and filter chips.

- [ ] **Step 2: Polish `ActivityScreen.kt`**

- Refactor `SiteLogFeedCard`: crisp 1dp border, solid category pill (`MAINTENANCE`, `VISIT`, `ISSUE`, `NOTE`), monospaced SAP ID and date display.
- Search input: 48dp height with crisp border.

- [ ] **Step 3: Polish `HomeScreen.kt` and `DownRegisterScreen.kt`**

- Strip `#FEF2F2`, `#F0FDF4`, and `#FFF7ED` pastel card fills.
- Use `SurfaceDark`/`SurfaceLight` with 1dp borders and high-contrast color indicators for "Sites Down Now" and "Downtime".
- Down register date picker navigation: crisp 48dp arrow buttons and tabular monospace dates.

- [ ] **Step 4: Polish `MainActivity.kt` Navigation Bar**

- Industrial slate bottom navigation bar with 1dp hairline top border (`BorderDark`).
- Tab icons and labels styled with IBM Plex typography and clear active accent pill.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/app/telecom/sitetracker/ui/sitelist/SiteListScreen.kt app/src/main/java/app/telecom/sitetracker/ui/activity/ActivityScreen.kt app/src/main/java/app/telecom/sitetracker/ui/home/HomeScreen.kt app/src/main/java/app/telecom/sitetracker/ui/outages/DownRegisterScreen.kt app/src/main/java/app/telecom/sitetracker/MainActivity.kt
git commit -m "style: polish card surfaces, borders, touch targets, and bottom navigation"
```

---

### Task 7: Comprehensive Verification & Regression Check

**Files:**
- Test: All tests in `app/src/test/`

- [ ] **Step 1: Run complete test suite**
- [ ] **Step 2: Verify zero regression across existing site import and down register features**
- [ ] **Step 3: Final verification commit**

```bash
git commit --allow-empty -m "chore: verify log import and professional industrial UI complete"
```
