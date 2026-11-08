# Universal Specifications Template Manager (Pillar 1) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Create a central Universal Technical Specifications Template system in TowerDost so that any standard equipment field or category defined globally is automatically inherited and visible across every site in the app.

**Architecture:** Introduce `UniversalFieldEntity` and `UniversalFieldDao` in Room with `MIGRATION_2_3`. Expose CRUD and defaults in `SiteRepository`. In `SiteDetailViewModel`, merge the universal template dynamically with per-site records so every site automatically displays all standard fields. Build `UniversalTemplateScreen` and `UniversalTemplateViewModel` for editing the global template, and integrate into `ToolsScreen` and `SiteDetailScreen`.

**Tech Stack:** Kotlin, Jetpack Compose (Material 3), Room DB, Kotlin Coroutines & Flow, JUnit 4.

**Spec:** [docs/superpowers/specs/2026-11-08-universal-specs-template-design.md](file:///home/error/prj/TowerDost/docs/superpowers/specs/2026-11-08-universal-specs-template-design.md)

## Global Constraints

- **Offline-Only:** Never add `INTERNET` permission to `AndroidManifest.xml` or build scripts.
- **Android Compatibility:** Must run on `minSdk 24` up to `targetSdk 35`.
- **Database Safety:** Safe Room migration `MIGRATION_2_3` without dropping existing tables.
- **Design Tokens:** Strict adherence to IBM Plex fonts, high sunlight contrast (`TelecomAmber`, `RFCyan`, dark slate surfaces), 48dp touch targets, and 1dp hairline borders.

## Review Focus

1. **Non-Destructive Room Migration:** Upgrading database version to 3 must not wipe existing `sites`, `site_fields`, or `incidents`.
2. **Pre-existing vs Blank Field Values:** If a site has already recorded a value for a universal field, its recorded value must be preserved; if unrecorded, it must render as `—` (or blank input in edit mode) rather than duplicating the row.
3. **Template Deletions:** Deleting a universal field definition removes the global requirement, but does not corrupt existing sites that already had data saved for it.
4. **Instant Inheritance:** Adding a new universal field in the template manager immediately appears in all site detail screens upon return.
5. **Zero Frame Drops:** Template manager and merged site view must use virtualized `LazyColumn` for 60fps smoothness.

---

### Task 1: Database & Repository Layer for Universal Template

**Files:**
- Create: `app/src/main/java/app/telecom/sitetracker/data/entity/UniversalFieldEntity.kt`
- Create: `app/src/main/java/app/telecom/sitetracker/data/db/UniversalFieldDao.kt`
- Modify: `app/src/main/java/app/telecom/sitetracker/data/db/AppDatabase.kt:17-88`
- Modify: `app/src/main/java/app/telecom/sitetracker/data/repo/SiteRepository.kt:20-200`
- Create: `app/src/test/java/app/telecom/sitetracker/UniversalFieldTest.kt`

**Interfaces:**
- Produces:
  - `UniversalFieldEntity(val fieldKey: String, val groupName: String, val defaultValue: String? = null, val sortOrder: Int = 0)`
  - `UniversalFieldDao` CRUD and flow methods
  - `SiteRepository.observeUniversalFields(): Flow<List<UniversalFieldEntity>>`
  - `SiteRepository.getUniversalFields(): List<UniversalFieldEntity>`
  - `SiteRepository.saveUniversalField(field: UniversalFieldEntity)`
  - `SiteRepository.deleteUniversalField(fieldKey: String)`
  - `SiteRepository.deleteUniversalSection(groupName: String)`
  - `SiteRepository.seedDefaultUniversalFields()`

- [ ] **Step 1: Write unit tests in `UniversalFieldTest.kt`**
  Verify entity defaults, sort order comparator, and template merging logic against site records.

- [ ] **Step 2: Implement `UniversalFieldEntity.kt` and `UniversalFieldDao.kt`**
  Define entity and Room DAO interface with `@Insert(onConflict = REPLACE)`, `@Query`, and `@Transaction replaceAll`.

- [ ] **Step 3: Update `AppDatabase.kt` with `version = 3` and `MIGRATION_2_3`**
  Add entity to `@Database`, define migration SQL, and wire `universalFieldDao()`.

- [ ] **Step 4: Implement repository methods in `SiteRepository.kt`**
  Add CRUD methods and default telecom seed categories (`POWER`, `BATTERY`, `DG`, `RAN`, `TRANSMISSION`, `SAFETY`, `ENVIRONMENT`).

- [ ] **Step 5: Run tests and verify**
  Run `UniversalFieldTest` and verify compilation.

- [ ] **Step 6: Commit**
  ```bash
  git add app/src/main/java/app/telecom/sitetracker/data/entity/UniversalFieldEntity.kt app/src/main/java/app/telecom/sitetracker/data/db/UniversalFieldDao.kt app/src/main/java/app/telecom/sitetracker/data/db/AppDatabase.kt app/src/main/java/app/telecom/sitetracker/data/repo/SiteRepository.kt app/src/test/java/app/telecom/sitetracker/UniversalFieldTest.kt
  git commit -m "feat(data): add UniversalFieldEntity, DAO, migration and repository support"
  ```

---

### Task 2: Merge Universal Template into Site Details

**Files:**
- Modify: `app/src/main/java/app/telecom/sitetracker/ui/sitedetail/SiteDetailViewModel.kt:320-360`
- Modify: `app/src/test/java/app/telecom/sitetracker/SiteDetailViewModelTest.kt`

**Interfaces:**
- Consumes:
  - `SiteRepository.getUniversalFields()`
- Modifies:
  - `SiteDetailViewModel.loadSiteDetails()` merging `universalFields` with `fields`

- [ ] **Step 1: Write unit test in `SiteDetailViewModelTest.kt`**
  Test `testUniversalFieldsMergedWithSiteSpecificSpecs` verifying that empty fields from the universal template are added without overriding existing site values.

- [ ] **Step 2: Update `SiteDetailViewModel.loadSiteDetails()`**
  Fetch both `siteRepository.getFieldsForSite` and `siteRepository.getUniversalFields()`, merge by case-insensitive key, and group for UI.

- [ ] **Step 3: Verify and run unit tests**
  Ensure all tests in `SiteDetailViewModelTest.kt` pass.

- [ ] **Step 4: Commit**
  ```bash
  git add app/src/main/java/app/telecom/sitetracker/ui/sitedetail/SiteDetailViewModel.kt app/src/test/java/app/telecom/sitetracker/SiteDetailViewModelTest.kt
  git commit -m "feat(ui): dynamically merge universal template fields into site details"
  ```

---

### Task 3: Universal Template Manager Screen & Navigation

**Files:**
- Create: `app/src/main/java/app/telecom/sitetracker/ui/template/UniversalTemplateViewModel.kt`
- Create: `app/src/main/java/app/telecom/sitetracker/ui/template/UniversalTemplateScreen.kt`
- Modify: `app/src/main/java/app/telecom/sitetracker/ui/navigation/Screen.kt`
- Modify: `app/src/main/java/app/telecom/sitetracker/ui/navigation/AppNavGraph.kt`
- Modify: `app/src/main/java/app/telecom/sitetracker/ui/tools/ToolsScreen.kt`
- Modify: `app/src/main/java/app/telecom/sitetracker/ui/sitedetail/SiteDetailScreen.kt`

**Interfaces:**
- Produces:
  - `Screen.UniversalTemplate`
  - `UniversalTemplateScreen(onNavigateBack: () -> Unit)`
  - Tile in `ToolsScreen`
  - Button in `SiteDetailScreen` Specs tab

- [ ] **Step 1: Create `UniversalTemplateViewModel.kt`**
  Manage state `groupedUniversalFields`, `isAddingSection`, `isAddingField`, and methods `saveField`, `deleteField`, `deleteSection`, `resetToDefaults`.

- [ ] **Step 2: Create `UniversalTemplateScreen.kt`**
  Build Compose screen with `EnterpriseTopBar`, virtualized `LazyColumn` section cards, `+ Add Field` per section, `+ Add New Section`, and `Load Telecom Defaults` action button.

- [ ] **Step 3: Register in `Screen.kt` and `AppNavGraph.kt`**
  Add route `Screen.UniversalTemplate` and wire into navigation graph.

- [ ] **Step 4: Add entry points in `ToolsScreen.kt` and `SiteDetailScreen.kt`**
  - Add "Universal Specs Template" card in Tools.
  - Add "Template Settings" button in Specs tab header.

- [ ] **Step 5: Commit**
  ```bash
  git add app/src/main/java/app/telecom/sitetracker/ui/template/ app/src/main/java/app/telecom/sitetracker/ui/navigation/ app/src/main/java/app/telecom/sitetracker/ui/tools/ToolsScreen.kt app/src/main/java/app/telecom/sitetracker/ui/sitedetail/SiteDetailScreen.kt
  git commit -m "feat(ui): implement UniversalTemplateScreen and wire navigation entry points"
  ```

---

### Task 4: Integration Verification & Push to Branch

**Files:**
- All modified files

- [ ] **Step 1: Run comprehensive unit test suite**
  Verify all unit tests pass.

- [ ] **Step 2: Code hygiene and review check**
  Verify no syntax errors, clean imports, and proper error handling.

- [ ] **Step 3: Commit and Push to `feature/specs-inline-editor`**
  Push all commits to GitHub.
