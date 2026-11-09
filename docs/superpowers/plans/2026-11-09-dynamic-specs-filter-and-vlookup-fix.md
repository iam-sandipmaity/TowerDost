# Dynamic Specs Filter & VLOOKUP Alignment Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:**
1. Implement a dynamic specification & attribute filter directly below the search bar on the Site List screen (allowing users to choose up to 4 arbitrary field/spec filters without forcing hardcoded categories).
2. Fix the auto-mapper bug where `"Sr No"` was wrongly assigned to `TargetField.SAP_ID`, causing genuine SAP ID columns to become generic custom specs.
3. Fix the VLOOKUP "Existing in Data" calculation bug where core fields were falsely flagged as existing even when the user only imported SAP IDs with null attributes.
4. Align and polish the VLOOKUP UI/UX for `EXISTING` and `NEW` badges with high sunlight contrast and clean enterprise industrial design.

**Architecture:**
- **Import & Mapper:** Update `ColumnMapper.kt` and `VlookupEngine.kt` to classify row sequence numbers (`"Sr No"`, `"Serial No"`, `"Sl No"`) as `TargetField.IGNORE`, preventing collision with `TargetField.SAP_ID`.
- **VLOOKUP Existence Verification:** Enhance `VlookupMergerViewModel` to verify actual data presence in SQLite (checking non-null core fields and distinct specifications in `site_fields`).
- **Dynamic Filter System:** In `SiteListViewModel`, provide dynamic multi-attribute filtering supporting up to 4 concurrent user-chosen criteria (e.g. `Technology: 5G`, `Circle: West Bengal`, `DG CAPACITY: 15KVA`).
- **Compose UI Polish:** In `SiteListScreen.kt`, add an interactive horizontal filter bar with `+ Filter` selector, value chooser bottom sheet/dialog, and dismissible filter chips. In `VlookupMergerScreen.kt`, redesign `ColumnMappingCard` for pixel-aligned, sunlight-readable badge indicators.

**Tech Stack:** Kotlin, Jetpack Compose (Material 3), Room DB (SQLite), Kotlin Coroutines & Flow, StateFlow, JUnit 4.

---

## Global Constraints

- **Offline-Only:** Never add `INTERNET` permission to `AndroidManifest.xml` or build scripts.
- **Android Compatibility:** Must run on `minSdk 24` up to `targetSdk 35`.
- **Zero Mock Data in Production:** All filter dimensions and specifications must reflect actual local database state or imported files (`INFRA TRACKER.xlsx`).
- **Design Tokens:** Strict adherence to IBM Plex fonts, high sunlight contrast (`TelecomAmber`, `RFCyan`, `StatusRestoredGreen`, dark slate surfaces), 48dp touch targets, and 1dp hairline borders.

---

## Review Focus

1. **Import Header Priority:** Ensure `"Sr No"` is never matched to `SAP_ID`, even if it appears as the very first column in an Excel or CSV file.
2. **Existence Accuracy:** When a database has sites with only SAP IDs populated, imported columns like `Site Name`, `Latitude`, and `Circle` must correctly show as `NEW ATTRIBUTE`, not `EXISTING IN DATA`.
3. **Max 4 Dynamic Filters:** The filter bar must restrict active filters to a maximum of 4, allowing the user to select any field and any value without hardcoding presets.
4. **Instant Reactive Feedback:** Removing or adding a filter chip must immediately update the active site directory count and paged/search list.
5. **No Duplicate Key Crashes:** Ensure all LazyRow and LazyColumn keys in the new UI components use composite unique keys.

---

### Task 1: Fix Header Mapping: "Sr No" to IGNORE and Protect SAP_ID Detection

**Files:**
- Modify: `app/src/main/java/app/telecom/sitetracker/data/import/ColumnMapper.kt:45-125`
- Modify: `app/src/main/java/app/telecom/sitetracker/data/merger/VlookupEngine.kt:20-55`
- Test: `app/src/test/java/app/telecom/sitetracker/ColumnMapperTest.kt`
- Test: `app/src/test/java/app/telecom/sitetracker/VlookupMergerTest.kt`

**Interfaces:**
- `ColumnMapper.ALIASES[TargetField.IGNORE]`: includes `"sr no"`, `"sr_no"`, `"sl no"`, `"sl_no"`, `"serial no"`, `"serial_no"`, `"s no"`, `"s_no"`, `"row no"`
- `ColumnMapper.ALIASES[TargetField.SAP_ID]`: removes `"sr no"`
- `VlookupEngine.detectKeyColumn(headers: List<String>)`: excludes row serial number headers from key detection

- [ ] **Step 1: Write failing unit test in `ColumnMapperTest.kt` and `VlookupMergerTest.kt`**
  - In `ColumnMapperTest.kt`, test that `matchField("Sr No") == TargetField.IGNORE` and `matchField("Serial No") == TargetField.IGNORE`.
  - Test that in a sheet with headers `["Sr No", "SAP ID", "Site Name"]`, `generateInitialMappings` maps `"Sr No"` to `IGNORE` and `"SAP ID"` to `SAP_ID` (not `CUSTOM_SPEC`).
  - In `VlookupMergerTest.kt`, test `detectKeyColumn(listOf("Sr No", "SAP ID", "Circle"))` returns index 1 (`"SAP ID"`), not index 0.

- [ ] **Step 2: Run test to verify it fails**
  - Run tests and confirm failure due to `"sr no"` currently being mapped to `SAP_ID`.

- [ ] **Step 3: Update `ColumnMapper.kt` and `VlookupEngine.kt`**
  - Remove `"sr no"` from `TargetField.SAP_ID` aliases in `ColumnMapper.kt`.
  - Add explicit serial number aliases to `TargetField.IGNORE`.
  - In `VlookupEngine.kt`, ensure `KEY_ALIASES` filters out serial number prefixes and detects actual SAP ID columns.

- [ ] **Step 4: Verify tests pass**
  - Re-run unit tests to ensure clean green pass.

---

### Task 2: Accurate Database Field Existence Check in VLOOKUP Merger

**Files:**
- Modify: `app/src/main/java/app/telecom/sitetracker/data/db/SiteDao.kt`
- Modify: `app/src/main/java/app/telecom/sitetracker/data/repo/SiteRepository.kt`
- Modify: `app/src/main/java/app/telecom/sitetracker/ui/merger/VlookupMergerViewModel.kt:60-120`
- Test: `app/src/test/java/app/telecom/sitetracker/VlookupMergerTest.kt`

**Interfaces:**
- `SiteDao.getPopulatedCoreFieldsSummary(): PopulatedFieldsSummary` (checks non-empty counts for name, lat, lng, city, circle, etc.)
- `SiteRepository.getPopulatedAttributeKeys(): Set<String>`
- `VlookupMergerViewModel.checkIsExistingInDb(target: TargetField, specKey: String?, existingKeys: Set<String>): Boolean`

- [ ] **Step 1: Write failing unit test in `VlookupMergerTest.kt`**
  - Add unit test verifying that if a site database contains records with only `sapId` (where `name == null`, `city == null`, etc.), `checkIsExistingInDb(TargetField.NAME)` evaluates to `false` (`NEW ATTRIBUTE`).
  - Verify that only attributes with non-empty values in the database return `true` (`EXISTING IN DATA`).

- [ ] **Step 2: Run test to verify it fails**
  - Verify failure where core fields currently default unconditionally to `true`.

- [ ] **Step 3: Implement database inspection in `SiteDao.kt`, `SiteRepository.kt`, and `VlookupMergerViewModel.kt`**
  - In `SiteDao.kt`, add a query counting non-blank values across core site columns.
  - In `SiteRepository.kt`, provide `getPopulatedAttributeKeys()` returning the combined set of populated core field keys (`NAME`, `LAT`, `LNG`, `CITY`, etc.) and distinct spec keys from `fieldDao`.
  - In `VlookupMergerViewModel.kt`, initialize `existingFieldKeys` using `getPopulatedAttributeKeys()` and update `checkIsExistingInDb` to test membership against actual populated data.
  - In Step 3 mapping dropdown, remove `TargetField.SAP_ID` from the available options for non-key columns.

- [ ] **Step 4: Verify tests pass**
  - Run unit tests to confirm accurate existing vs new determination.

---

### Task 3: VLOOKUP UI/UX Alignment & High-Sunlight Badges in `ColumnMappingCard`

**Files:**
- Modify: `app/src/main/java/app/telecom/sitetracker/ui/merger/VlookupMergerScreen.kt:760-950`

**UI Improvements:**
- Redesign `ColumnMappingCard`:
  - Consistent vertical alignment: 48dp checkbox target on the left.
  - Middle column:
    - Row 1: Source Header name in high-contrast text.
    - Row 2: Status pill badge:
      - `EXISTING IN DATA` (`RFCyan`, `0.15f` background, `0.7f` border, monospace 9sp bold).
      - `NEW ATTRIBUTE` (`StatusRestoredGreen`, `0.15f` background, `0.7f` border, monospace 9sp bold).
      - Subtitle hint: `"Will update existing records"` vs `"Will create new specification"`.
    - Row 3: Sample data values (monospace, slate).
  - Right: Clean target mapping selector button with distinct icon and dropdown arrow.
- Step 3 Toolbar:
  - Clean summary counter with pill badges: `• X Existing in data • Y New attribute`.

- [ ] **Step 1: Update `ColumnMappingCard` composable in `VlookupMergerScreen.kt`**
  - Apply the structured layout with dedicated status chip row and high sunlight contrast.
  - Ensure touch targets meet the 48dp minimum standard.

- [ ] **Step 2: Test rendering and UI interactions**
  - Verify dropdown menu options omit `SAP ID` in column mapping Step 3.
  - Verify toggle checkbox updates counts and status smoothly.

---

### Task 4: Dynamic Multi-Attribute Filtering (Max 4) on Site Directory

**Files:**
- Create: `app/src/main/java/app/telecom/sitetracker/ui/sitelist/DynamicFilterModels.kt`
- Modify: `app/src/main/java/app/telecom/sitetracker/data/db/SiteDao.kt`
- Modify: `app/src/main/java/app/telecom/sitetracker/data/repo/SiteRepository.kt`
- Modify: `app/src/main/java/app/telecom/sitetracker/ui/sitelist/SiteListViewModel.kt`
- Modify: `app/src/main/java/app/telecom/sitetracker/ui/sitelist/SiteListScreen.kt`
- Test: `app/src/test/java/app/telecom/sitetracker/DynamicFilterTest.kt`

**Data Models:**
```kotlin
data class FilterDimension(
    val key: String,
    val displayName: String,
    val isCoreField: Boolean,
    val availableValues: List<String> = emptyList()
)

data class ActiveFilter(
    val id: String = java.util.UUID.randomUUID().toString(),
    val dimensionKey: String,
    val displayName: String,
    val selectedValue: String,
    val isCoreField: Boolean
)
```

**Features:**
- Up to 4 active dynamic filters (`maxFilters = 4`).
- Filter button (`+ Filter`) directly below the search bar on `SiteListScreen.kt`.
- Filter Selection Modal / Bottom Sheet:
  - Step A: Choose field from populated database attributes (Core: Circle, City, District, Technology, Site Type, Status + any Equipment Spec from `site_fields`).
  - Step B: Choose value from distinct values in database or type value.
- Active filters rendered as dismissible chips with `✕` button.
- Filtering engine: evaluates `sites` and `site_fields` matching search text AND all active filters.

- [ ] **Step 1: Write unit tests in `DynamicFilterTest.kt`**
  - Test adding up to 4 filters and rejecting/preventing a 5th filter.
  - Test filtering sites by core attribute (e.g. `Technology == "5G"`).
  - Test filtering sites by custom specification (e.g. `DG CAPACITY == "15KVA"`).
  - Test multi-filter conjunction (AND logic across multiple filters).
  - Test removing a filter chip restores sites matching remaining criteria.

- [ ] **Step 2: Run test to verify it fails**
  - Confirm failure of filter model and filtering logic.

- [ ] **Step 3: Implement `DynamicFilterModels.kt`, repository methods, and `SiteListViewModel` logic**
  - Implement `FilterDimension`, `ActiveFilter`, and distinct value lookups in `SiteRepository`.
  - In `SiteListViewModel`, expose `activeFilters: StateFlow<List<ActiveFilter>>`, `availableDimensions: StateFlow<List<FilterDimension>>`, `addFilter`, `removeFilter`, `clearAllFilters`.
  - Connect filtering to `executeSearchOrNearMe` so search query, GPS proximity, and dynamic filters work together seamlessly.

- [ ] **Step 4: Implement Compose UI in `SiteListScreen.kt`**
  - Add horizontal scrolling filter bar below the search bar.
  - Implement `+ Filter` assist button and active filter chips.
  - Implement `FilterSelectionSheet` allowing selection of dimension and target value.
  - Add subtitle match counter reflecting dynamic filter count.

- [ ] **Step 5: Verify all unit tests pass**
  - Run test suite and confirm 100% green pass.

---

### Task 5: Integration Verification & Regression Testing

**Files:**
- Test: `app/src/test/java/app/telecom/sitetracker/VlookupMergerTest.kt`
- Test: `app/src/test/java/app/telecom/sitetracker/SiteSearchTest.kt`
- Test: `app/src/test/java/app/telecom/sitetracker/DynamicFilterTest.kt`
- Test: `app/src/test/java/app/telecom/sitetracker/ColumnMapperTest.kt`

- [ ] **Step 1: Run complete test suite**
  - Verify all unit tests in the project compile and pass.

- [ ] **Step 2: Commit and push feature branch to origin**
  - Commit all changes with descriptive commit messages.
  - Push branch `feature/dynamic-specs-filter-and-vlookup-fix` to `origin`.
