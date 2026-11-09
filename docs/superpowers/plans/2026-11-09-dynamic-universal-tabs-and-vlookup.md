# Dynamic Universal Tabs & Flexible VLOOKUP Mapping Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Allow users to define custom universal site tabs alongside Specs, Logs, and Overview with hierarchical sub-sections, route incoming VLOOKUP columns to any tab or new tab dynamically, and enforce a verified mandatory JSON backup protocol before deleting any tab.

**Architecture:** Extend the Room SQLite database to schema version 4 with `site_tabs` registry and `tab_id` columns on `universal_fields` (composite PK `(tab_id, field_key)`) and `site_fields`. Update `SiteDetailScreen` with a dynamic scrollable tab bar where custom tabs display grouped section cards (Option 1B). Enhance `VlookupEngine` and `VlookupMergerScreen` to map columns into any tab or create new tabs on the fly. Enforce pre-deletion backup verification via `BackupManager`.

**Tech Stack:** Native Android, Kotlin 1.9+, Jetpack Compose (Material 3), Room SQLite (Migration 3 -> 4), Kotlinx Serialization (JSON backup), Coroutines & StateFlow.

**Spec:** [docs/superpowers/specs/2026-11-09-dynamic-universal-tabs-and-vlookup-design.md](file:///home/error/prj/TowerDost/docs/superpowers/specs/2026-11-09-dynamic-universal-tabs-and-vlookup-design.md)

## Global Constraints

- **Offline-Only:** Zero `INTERNET` permission in `AndroidManifest.xml` or build scripts.
- **Android Compatibility:** `minSdk 24`, `targetSdk 35`.
- **Zero Mock Data in Production:** All tabs and specs must represent actual database state.
- **Strict Backward Compatibility:** Existing sites, specs, and default tabs (`SPECS`, `LOGS`, `OVERVIEW`) must function identically.
- **48dp Touch Targets & Sunlight Contrast:** High readability styling using `RFCyan`, `TelecomAmber`, and distinct border contrast.
- **Mandatory Backup Gate:** Tab deletion requires exporting a JSON backup and verifying its existence and non-zero byte size on the filesystem.

## Review Focus

1. **Schema Migration Integrity:** Upgrading an existing database from version 3 to 4 preserves all existing `universal_fields` and `site_fields` with default `tab_id = 'SPECS'`.
2. **Namespace Collision:** Two different tabs can both have a field with the same key (e.g. `Status` in Specs and `Status` in Tenant Info) without overwriting each other in `universal_fields`.
3. **Empty Tab Inheritance:** Existing sites with recorded technical specs do not get overwhelmed with blank placeholder fields, but display new custom tab blueprint fields cleanly when added.
4. **Pre-Deletion Backup Verification:** If a JSON backup file fails to write or has 0 bytes, tab deletion is strictly aborted.
5. **VLOOKUP New Tab Dynamic Creation:** Creating a new tab in VLOOKUP column mapping creates the tab in `site_tabs`, maps incoming rows with that `tab_id`, and registers the fields in `universal_fields`.

---

### Task 1: Database Migration 3 -> 4, `SiteTabEntity`, and Repository Extensions

**Files:**
- Create: `app/src/main/java/app/telecom/sitetracker/data/entity/SiteTabEntity.kt`
- Create: `app/src/main/java/app/telecom/sitetracker/data/db/SiteTabDao.kt`
- Modify: `app/src/main/java/app/telecom/sitetracker/data/entity/UniversalFieldEntity.kt`
- Modify: `app/src/main/java/app/telecom/sitetracker/data/entity/SiteFieldEntity.kt`
- Modify: `app/src/main/java/app/telecom/sitetracker/data/db/AppDatabase.kt`
- Modify: `app/src/main/java/app/telecom/sitetracker/data/repo/SiteRepository.kt`
- Test: `app/src/test/java/app/telecom/sitetracker/SiteTabDatabaseTest.kt`

**Interfaces:**
- Produces:
  - `SiteTabEntity(val tabId: String, val displayName: String, val sortOrder: Int, val isSystem: Boolean)`
  - `SiteTabDao`: `observeAllTabs(): Flow<List<SiteTabEntity>>`, `getAllTabs(): List<SiteTabEntity>`, `insertTab(tab: SiteTabEntity)`, `deleteTab(tabId: String)`
  - `SiteRepository.observeAllTabs(): Flow<List<SiteTabEntity>>`
  - `SiteRepository.getAllTabs(): List<SiteTabEntity>`
  - `SiteRepository.getFieldsForSiteAndTab(sapId: String, tabId: String): List<SiteFieldEntity>`
  - `SiteRepository.insertTab(tabId: String, displayName: String)`
  - `SiteRepository.deleteTab(tabId: String)`

- [ ] **Step 1: Write failing test in `SiteTabDatabaseTest.kt`**
Verify migration 3 -> 4, default system tabs insertion (`SPECS`, `LOGS`, `OVERVIEW`), composite primary key on `universal_fields`, and tab CRUD operations.

- [ ] **Step 2: Run test to verify it fails**
Run: `./gradlew testDebugUnitTest --tests app.telecom.sitetracker.SiteTabDatabaseTest`
Expected: FAIL (unresolved references for `SiteTabEntity` and `MIGRATION_3_4`)

- [ ] **Step 3: Implement `SiteTabEntity`, `SiteTabDao`, update entities, migration script, and repository**
  - Implement `SiteTabEntity` and `SiteTabDao`.
  - Update `UniversalFieldEntity` with `@Entity(tableName = "universal_fields", primaryKeys = ["tab_id", "field_key"])` and column `tab_id: String = "SPECS"`.
  - Update `SiteFieldEntity` with column `tab_id: String = "SPECS"`.
  - Add `MIGRATION_3_4` in `AppDatabase.kt` using table copy for `universal_fields` and `ALTER TABLE ... ADD COLUMN` for `site_fields`.
  - Update `AppDatabase` version to 4 and add `siteTabDao()`.
  - Add tab query methods in `SiteRepository`.

- [ ] **Step 4: Run test to verify it passes**
Run: `./gradlew testDebugUnitTest --tests app.telecom.sitetracker.SiteTabDatabaseTest`
Expected: PASS

- [ ] **Step 5: Commit**
```bash
git add app/src/main/java/app/telecom/sitetracker/data/entity/SiteTabEntity.kt \
        app/src/main/java/app/telecom/sitetracker/data/db/SiteTabDao.kt \
        app/src/main/java/app/telecom/sitetracker/data/entity/UniversalFieldEntity.kt \
        app/src/main/java/app/telecom/sitetracker/data/entity/SiteFieldEntity.kt \
        app/src/main/java/app/telecom/sitetracker/data/db/AppDatabase.kt \
        app/src/main/java/app/telecom/sitetracker/data/repo/SiteRepository.kt \
        app/src/test/java/app/telecom/sitetracker/SiteTabDatabaseTest.kt
git commit -m "feat(data): add SiteTabEntity, schema migration 3->4, and tab repository methods"
```

---

### Task 2: Mandatory Pre-Deletion Backup Protocol in `BackupManager`

**Files:**
- Modify: `app/src/main/java/app/telecom/sitetracker/data/backup/BackupManager.kt`
- Test: `app/src/test/java/app/telecom/sitetracker/TabDeletionBackupTest.kt`

**Interfaces:**
- Consumes: `AppDatabase.siteTabDao()`, `AppDatabase.universalFieldDao()`
- Produces:
  - `BackupData.tabs: List<SiteTabEntity>`
  - `BackupData.universalFields: List<UniversalFieldEntity>`
  - `BackupManager.generateAndVerifySafetyBackup(context: Context, tag: String): Result<File>`

- [ ] **Step 1: Write failing test in `TabDeletionBackupTest.kt`**
Verify that `BackupManager` serializes `site_tabs` and `universal_fields` in JSON, and that `generateAndVerifySafetyBackup` writes a file, verifies `file.exists() && file.length() > 0`, and returns a valid `File`.

- [ ] **Step 2: Run test to verify it fails**
Run: `./gradlew testDebugUnitTest --tests app.telecom.sitetracker.TabDeletionBackupTest`
Expected: FAIL

- [ ] **Step 3: Implement JSON serialization and verification in `BackupManager.kt`**
  - Update `BackupData` to include `tabs` and `universalFields`.
  - Update `serializeBackupJson` and `deserializeBackupJson` to read/write tabs and blueprint fields.
  - Implement `generateAndVerifySafetyBackup(context: Context, tag: String): Result<File>`: creates a timestamped JSON backup in `context.filesDir` (or external files), flushes to disk, and verifies existence and non-zero size before returning.

- [ ] **Step 4: Run test to verify it passes**
Run: `./gradlew testDebugUnitTest --tests app.telecom.sitetracker.TabDeletionBackupTest`
Expected: PASS

- [ ] **Step 5: Commit**
```bash
git add app/src/main/java/app/telecom/sitetracker/data/backup/BackupManager.kt \
        app/src/test/java/app/telecom/sitetracker/TabDeletionBackupTest.kt
git commit -m "feat(backup): add tabs and blueprint fields to JSON backup with filesystem verification"
```

---

### Task 3: VLOOKUP Dynamic Tab Selector & Universal Registration

**Files:**
- Modify: `app/src/main/java/app/telecom/sitetracker/data/merger/VlookupModels.kt`
- Modify: `app/src/main/java/app/telecom/sitetracker/data/merger/VlookupEngine.kt`
- Modify: `app/src/main/java/app/telecom/sitetracker/ui/merger/VlookupMergerViewModel.kt`
- Modify: `app/src/main/java/app/telecom/sitetracker/ui/merger/VlookupMergerScreen.kt`
- Test: `app/src/test/java/app/telecom/sitetracker/VlookupCustomTabTest.kt`

**Interfaces:**
- Consumes: `SiteTabDao`, `UniversalFieldDao`, `FieldDao`
- Produces:
  - `VlookupColumnMapping`: adds `tabId: String = "SPECS"`, `tabDisplayName: String = "Specs"`
  - `VlookupMergerUiState`: adds `availableTabs: List<SiteTabEntity>`
  - `VlookupMergerViewModel.createNewTabAndMap(columnIndex: Int, tabName: String, sectionName: String?)`
  - `VlookupEngine.executeMerge`: inserts newly introduced tabs into `site_tabs`, registers fields into `universal_fields`, and writes `tab_id` into `site_fields`.

- [ ] **Step 1: Write failing test in `VlookupCustomTabTest.kt`**
Test that column mapping can assign a column to a custom tab (e.g. `tabId = "TENANT_INFO"`, `groupName = "AIRTEL"`), and executing the merge registers the tab in `site_tabs`, writes the specs with `tab_id = "TENANT_INFO"`, and registers the field in `universal_fields`.

- [ ] **Step 2: Run test to verify it fails**
Run: `./gradlew testDebugUnitTest --tests app.telecom.sitetracker.VlookupCustomTabTest`
Expected: FAIL

- [ ] **Step 3: Implement custom tab support in VLOOKUP model, engine, ViewModel, and Screen**
  - Add `tabId` and `tabDisplayName` to `VlookupColumnMapping`.
  - Update `VlookupEngine.executeMerge` to write `tab_id` into `SiteFieldEntity` and record new tabs/fields in `site_tabs` and `universal_fields`.
  - Update `VlookupMergerViewModel` to load `availableTabs` from `db.siteTabDao()`.
  - Update `ColumnMappingCard` dropdown in `VlookupMergerScreen` to list existing custom tabs and provide `+ Create New Tab...` dialog.

- [ ] **Step 4: Run test to verify it passes**
Run: `./gradlew testDebugUnitTest --tests app.telecom.sitetracker.VlookupCustomTabTest`
Expected: PASS

- [ ] **Step 5: Commit**
```bash
git add app/src/main/java/app/telecom/sitetracker/data/merger/VlookupModels.kt \
        app/src/main/java/app/telecom/sitetracker/data/merger/VlookupEngine.kt \
        app/src/main/java/app/telecom/sitetracker/ui/merger/VlookupMergerViewModel.kt \
        app/src/main/java/app/telecom/sitetracker/ui/merger/VlookupMergerScreen.kt \
        app/src/test/java/app/telecom/sitetracker/VlookupCustomTabTest.kt
git commit -m "feat(vlookup): support dynamic tab and section routing with auto-tab creation"
```

---

### Task 4: Universal Template Dynamic Tab Management

**Files:**
- Modify: `app/src/main/java/app/telecom/sitetracker/ui/template/UniversalTemplateViewModel.kt`
- Modify: `app/src/main/java/app/telecom/sitetracker/ui/template/UniversalTemplateScreen.kt`
- Test: `app/src/test/java/app/telecom/sitetracker/UniversalTemplateTabTest.kt`

**Interfaces:**
- Consumes: `SiteRepository.observeAllTabs()`, `SiteRepository.insertTab()`, `BackupManager`
- Produces:
  - Tab selector in Universal Template screen (`Specs` vs custom tabs)
  - Ability to add new universal tabs from template
  - Adding blueprint sections and fields to specific tabs

- [ ] **Step 1: Write failing test in `UniversalTemplateTabTest.kt`**
Verify that `UniversalTemplateViewModel` observes all tabs, filters fields by selected tab, and adds new fields under the selected `tabId`.

- [ ] **Step 2: Run test to verify it fails**
Run: `./gradlew testDebugUnitTest --tests app.telecom.sitetracker.UniversalTemplateTabTest`
Expected: FAIL

- [ ] **Step 3: Implement tab management in Universal Template**
  - Add `tabs: List<SiteTabEntity>` and `selectedTabId: String` to `UniversalTemplateUiState`.
  - In `UniversalTemplateScreen`, add a horizontal tab selector to switch between `Specs` and custom tabs, plus an `Add Tab` action.
  - Ensure adding fields saves with `tab_id = selectedTabId`.

- [ ] **Step 4: Run test to verify it passes**
Run: `./gradlew testDebugUnitTest --tests app.telecom.sitetracker.UniversalTemplateTabTest`
Expected: PASS

- [ ] **Step 5: Commit**
```bash
git add app/src/main/java/app/telecom/sitetracker/ui/template/UniversalTemplateViewModel.kt \
        app/src/main/java/app/telecom/sitetracker/ui/template/UniversalTemplateScreen.kt \
        app/src/test/java/app/telecom/sitetracker/UniversalTemplateTabTest.kt
git commit -m "feat(template): add tab-aware universal blueprint editor"
```

---

### Task 5: Site Detail Scrollable Tab Strip & Hierarchical Custom Tabs (1B + 2A)

**Files:**
- Modify: `app/src/main/java/app/telecom/sitetracker/ui/sitedetail/SiteDetailViewModel.kt`
- Modify: `app/src/main/java/app/telecom/sitetracker/ui/sitedetail/SiteDetailScreen.kt`
- Test: `app/src/test/java/app/telecom/sitetracker/SiteDetailDynamicTabTest.kt`

**Interfaces:**
- Consumes: `SiteRepository.observeAllTabs()`, `BackupManager.generateAndVerifySafetyBackup(context, tag)`
- Produces:
  - `SiteDetailUiState.tabs: List<SiteTabEntity>`
  - `SiteDetailUiState.selectedTabId: String` (defaults to `"SPECS"`)
  - `SiteDetailUiState.tabFields: Map<String, Map<String, List<SiteFieldEntity>>>` (grouped by tabId -> groupName -> fields)
  - `SiteDetailViewModel.createUniversalTab(tabName: String)`
  - `SiteDetailViewModel.requestDeleteTab(tabId: String)`
  - `SiteDetailViewModel.generateSafetyBackup(context: Context)`
  - `SiteDetailViewModel.confirmDeleteTab(context: Context)`

- [ ] **Step 1: Write failing test in `SiteDetailDynamicTabTest.kt`**
Test that `SiteDetailViewModel` loads dynamic tabs, isolates fields by tab, allows adding a custom tab, supports editing sections inside custom tabs (Option 1B), and blocks deleting a tab until safety backup verification passes (Option 2A).

- [ ] **Step 2: Run test to verify it fails**
Run: `./gradlew testDebugUnitTest --tests app.telecom.sitetracker.SiteDetailDynamicTabTest`
Expected: FAIL

- [ ] **Step 3: Implement dynamic tabs, Option 1B sub-sections, and Option 2A verified backup gate**
  - Update `SiteDetailUiState` with `tabs`, `selectedTabId`, `isCreatingTabDialog`, `tabToDelete`, `backupVerified`.
  - Update `SiteDetailScreen` top tab bar to be horizontally scrollable with `[+]` button to add new tab.
  - Implement `CustomTabContent` using grouped section cards (Option 1B) identical to `SpecsTab`.
  - Implement tab deletion dialog: shows count of affected sites, requires tapping "Generate Safety Backup (.json)", checks `backupManager.generateAndVerifySafetyBackup()`, and only enables "Confirm Delete" after filesystem verification succeeds.

- [ ] **Step 4: Run test to verify it passes**
Run: `./gradlew testDebugUnitTest --tests app.telecom.sitetracker.SiteDetailDynamicTabTest`
Expected: PASS

- [ ] **Step 5: Commit**
```bash
git add app/src/main/java/app/telecom/sitetracker/ui/sitedetail/SiteDetailViewModel.kt \
        app/src/main/java/app/telecom/sitetracker/ui/sitedetail/SiteDetailScreen.kt \
        app/src/test/java/app/telecom/sitetracker/SiteDetailDynamicTabTest.kt
git commit -m "feat(ui): add scrollable site tab bar, hierarchical custom tabs, and verified pre-deletion backup gate"
```

---

### Task 6: Master Export Compatibility & Full System Verification

**Files:**
- Modify: `app/src/main/java/app/telecom/sitetracker/data/export/Exporter.kt`
- Test: `app/src/test/java/app/telecom/sitetracker/DynamicTabExportTest.kt`
- Verification: Run all unit and integration tests across the whole project.

- [ ] **Step 1: Write failing test in `DynamicTabExportTest.kt`**
Verify that `Exporter.exportSingleSiteCsv` and bulk exports include custom tab specifications with clean headers (e.g. `[TENANT INFO] Tenant Name`).

- [ ] **Step 2: Run test to verify it fails**
Run: `./gradlew testDebugUnitTest --tests app.telecom.sitetracker.DynamicTabExportTest`
Expected: FAIL

- [ ] **Step 3: Update `Exporter.kt`**
Ensure exported CSV rows include fields across all active tabs with appropriate prefix or column groupings.

- [ ] **Step 4: Run full test suite to verify everything passes**
Run: `./gradlew testDebugUnitTest`
Expected: ALL TESTS PASS with 0 failures.

- [ ] **Step 5: Commit**
```bash
git add app/src/main/java/app/telecom/sitetracker/data/export/Exporter.kt \
        app/src/test/java/app/telecom/sitetracker/DynamicTabExportTest.kt
git commit -m "feat(export): include custom tab specifications in CSV exports"
```
