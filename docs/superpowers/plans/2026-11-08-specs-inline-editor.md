# Specifications Inline Editing Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Transform the technical specifications editing in TowerDost into an inline section-card editor matching the Overview tab's UX pattern, allowing technicians to enter spec data in text boxes, add new fields or sections on the fly, and batch-save changes with safeguard confirmation.

**Architecture:** Extend Room `FieldDao` and `SiteRepository` with an atomic `@Transaction` replacement method. In `SiteDetailViewModel`, introduce draft section state (`SpecDraftSection`, `SpecDraftItem`) with granular manipulation methods (add/update/remove specs, add/remove sections) and safeguard-backed persistence. Redesign `SpecsTab` in `SiteDetailScreen.kt` to offer inline data boxes within industrial section cards, section creation controls, and Overview-parity save/cancel buttons.

**Tech Stack:** Kotlin, Jetpack Compose (Material 3), Room DB, Kotlin Coroutines & Flow, JUnit 4.

**Spec:** [docs/superpowers/specs/2026-11-08-specs-inline-editor-design.md](file:///home/error/prj/TowerDost/docs/superpowers/specs/2026-11-08-specs-inline-editor-design.md)

## Global Constraints

- **Offline-Only:** Never add `INTERNET` permission to `AndroidManifest.xml` or build scripts.
- **Android Compatibility:** Must run on `minSdk 24` up to `targetSdk 35`.
- **Zero Mock Data in Production:** All specifications must reflect actual local database state or imported files.
- **Design Tokens:** Strict adherence to IBM Plex fonts, high sunlight contrast (`TelecomAmber`, `RFCyan`, dark slate surfaces), 48dp touch targets, and 1dp hairline borders.

## Review Focus

1. **Empty Field Name Handling:** A user typing a value without a field name should be caught by validation before saving, prompting a clear error.
2. **Empty Section Handling:** Adding a new section without any specs should not crash or corrupt database state when saved; empty sections are either populated or cleanly omitted.
3. **FTS and Timestamp Refresh:** Batch replacing specifications must update the site's `updatedAt` timestamp and synchronize full-text search (`SiteFts`) text.
4. **Draft Isolation:** Cancelling edit mode must discard all unsaved edits, restoring exact previous database state.
5. **Accidental Modification Safeguard:** Tapping "Save Changes" must present a confirmation dialog detailing the site SAP ID before committing changes to SQLite.

---

### Task 1: Database & Repository Batch Specification Operations

**Files:**
- Modify: `app/src/main/java/app/telecom/sitetracker/data/db/FieldDao.kt:12-45`
- Modify: `app/src/main/java/app/telecom/sitetracker/data/repo/SiteRepository.kt:113-178`
- Test: `app/src/test/java/app/telecom/sitetracker/SiteUpdateAndExportTest.kt`

**Interfaces:**
- Produces:
  - `FieldDao.replaceFieldsForSite(sapId: String, fields: List<SiteFieldEntity>)`
  - `SiteRepository.replaceSiteSpecs(sapId: String, fields: List<SiteFieldEntity>)`

- [ ] **Step 1: Write the failing unit test in `SiteUpdateAndExportTest.kt`**
  Add unit test `testBatchReplaceSiteSpecsUpdatesDatabaseAndFts` verifying that `SiteRepository.replaceSiteSpecs` clears prior specs for the site, writes the new specs, updates site `updatedAt`, and preserves unrelated sites.

- [ ] **Step 2: Run test to verify it fails**
  Run unit test and verify `replaceSiteSpecs` method is missing or fails.

- [ ] **Step 3: Implement `replaceFieldsForSite` in `FieldDao.kt` and `replaceSiteSpecs` in `SiteRepository.kt`**
  In `FieldDao.kt`, add:
  ```kotlin
  @androidx.room.Transaction
  suspend fun replaceFieldsForSite(sapId: String, fields: List<SiteFieldEntity>) {
      deleteFieldsForSite(sapId)
      insertFields(fields)
  }
  ```
  In `SiteRepository.kt`, implement `replaceSiteSpecs`:
  ```kotlin
  suspend fun replaceSiteSpecs(sapId: String, fields: List<SiteFieldEntity>) {
      fieldDao.replaceFieldsForSite(sapId, fields)
      val site = siteDao.getSiteBySapId(sapId)
      if (site != null) {
          siteDao.updateSite(site.copy(updatedAt = System.currentTimeMillis()))
          try {
              val fieldText = fields.joinToString(" ") { "${it.fieldKey} ${it.fieldValue ?: ""}" }
              siteDao.deleteFts(site.sapNorm)
              siteDao.insertFts(
                  listOf(
                      SiteFts(
                          sapNorm = site.sapNorm,
                          name = site.name,
                          address = site.address ?: site.city ?: site.district,
                          fieldText = fieldText
                      )
                  )
              )
          } catch (_: Exception) {}
      }
  }
  ```

- [ ] **Step 4: Run test to verify it passes**
  Verify compilation and test execution.

- [ ] **Step 5: Commit**
  ```bash
  git add app/src/main/java/app/telecom/sitetracker/data/db/FieldDao.kt app/src/main/java/app/telecom/sitetracker/data/repo/SiteRepository.kt app/src/test/java/app/telecom/sitetracker/SiteUpdateAndExportTest.kt
  git commit -m "feat(data): add atomic batch specification replacement in FieldDao and SiteRepository"
  ```

---

### Task 2: ViewModel Draft State Management & Safeguard Logic

**Files:**
- Modify: `app/src/main/java/app/telecom/sitetracker/ui/sitedetail/SiteDetailViewModel.kt:21-363`
- Create: `app/src/test/java/app/telecom/sitetracker/SiteDetailViewModelTest.kt`

**Interfaces:**
- Produces:
  - `data class SpecDraftItem(val id: Long = 0L, val fieldKey: String = "", val fieldValue: String = "")`
  - `data class SpecDraftSection(val sectionName: String, val items: List<SpecDraftItem>)`
  - `SiteDetailUiState.isEditingSpecs: Boolean`
  - `SiteDetailUiState.draftSpecsSections: List<SpecDraftSection>`
  - `SiteDetailUiState.confirmSaveSpecs: Boolean`
  - `SiteDetailUiState.isAddingSectionDialog: Boolean`
  - `SiteDetailViewModel.startEditingSpecs()`
  - `SiteDetailViewModel.updateDraftSpecItem(sectionIndex: Int, itemIndex: Int, key: String, value: String)`
  - `SiteDetailViewModel.addDraftSpecItem(sectionIndex: Int)`
  - `SiteDetailViewModel.removeDraftSpecItem(sectionIndex: Int, itemIndex: Int)`
  - `SiteDetailViewModel.showAddSectionDialog()`
  - `SiteDetailViewModel.dismissAddSectionDialog()`
  - `SiteDetailViewModel.addDraftSection(name: String)`
  - `SiteDetailViewModel.removeDraftSection(sectionIndex: Int)`
  - `SiteDetailViewModel.cancelEditingSpecs()`
  - `SiteDetailViewModel.requestSaveSpecs()`
  - `SiteDetailViewModel.dismissSaveSpecsConfirmation()`
  - `SiteDetailViewModel.confirmAndSaveSpecs()`

- [ ] **Step 1: Write unit tests in `SiteDetailViewModelTest.kt`**
  Cover:
  - `startEditingSpecs` loads grouped specs into `draftSpecsSections`
  - `addDraftSpecItem` appends an empty item to the designated section
  - `updateDraftSpecItem` updates key and value in place
  - `removeDraftSpecItem` removes the item
  - `addDraftSection` creates a new section with a starter item
  - `removeDraftSection` deletes an entire section
  - `requestSaveSpecs` fails validation with empty field keys
  - `confirmAndSaveSpecs` persists non-empty specs and resets edit mode

- [ ] **Step 2: Run test to verify it fails**
  Verify tests fail before ViewModel changes.

- [ ] **Step 3: Implement draft state and methods in `SiteDetailViewModel.kt`**
  Add draft classes, state fields, and manipulation functions. Ensure clean validation (preventing blank keys with values) and Room batch update dispatch.

- [ ] **Step 4: Run test to verify it passes**
  Ensure all unit tests in `SiteDetailViewModelTest.kt` pass.

- [ ] **Step 5: Commit**
  ```bash
  git add app/src/main/java/app/telecom/sitetracker/ui/sitedetail/SiteDetailViewModel.kt app/src/test/java/app/telecom/sitetracker/SiteDetailViewModelTest.kt
  git commit -m "feat(ui): add inline specs draft state and manipulation methods in SiteDetailViewModel"
  ```

---

### Task 3: SiteDetailScreen UI Overhaul: Inline Data Boxes, Section Controls & Safeguards

**Files:**
- Modify: `app/src/main/java/app/telecom/sitetracker/ui/sitedetail/SiteDetailScreen.kt:315-435, 730-995`

**Interfaces:**
- Consumes:
  - `uiState.isEditingSpecs`
  - `uiState.draftSpecsSections`
  - `uiState.confirmSaveSpecs`
  - `uiState.isAddingSectionDialog`
  - ViewModel draft methods from Task 2

- [ ] **Step 1: Refactor `SpecsTab` in `SiteDetailScreen.kt`**
  - View mode:
    - Add top Overview-parity banner: "Technical Specifications ([Count] fields)" / "Locked against accidental edits", with `Unlock to Edit` / `Edit Specs` button calling `viewModel.startEditingSpecs()`.
    - Retain `SearchCapsule` for instant spec filtering and `EnterpriseSpecRow` list.
  - Edit mode (`uiState.isEditingSpecs == true`):
    - Top banner: "Editing Specifications: [sapId]" in `TagMaintBg` style: "Modifications will update master records and reflect in all CSV exports."
    - Scrollable list of Section Cards:
      - Section Header: Section Title (`RFCyan`, bold), section delete icon, and `+ Add Spec` button.
      - For each item in section:
        - Row with two `OutlinedTextField` data boxes:
          - Box 1 (weight 1.1f): `Field Name` (e.g. `SMPS CAPACITY`)
          - Box 2 (weight 1.1f): `Field Value` (e.g. `48V 100A`)
          - Trash icon button (`TagIssueColor`) to delete the spec row.
      - Bottom of card: Outlined button `+ Add Field to [Section]`.
    - Below all section cards:
      - Button: `+ Add New Section` (styled with `TelecomAmber` outline).
      - Action row with `[Cancel]` and `[Save Changes]` (filled `TelecomAmber` button).

- [ ] **Step 2: Add Dialogs in `SiteDetailScreen.kt`**
  - Save Confirmation Safeguard Dialog (`uiState.confirmSaveSpecs`):
    - Title: "Save Technical Specifications?"
    - Message: "You are updating technical specifications for site WB-KOL-1001. Permanent changes will be saved to local database and future CSV exports."
    - Buttons: [Cancel] and [Confirm & Save].
  - Add Section Dialog (`uiState.isAddingSectionDialog`):
    - Title: "Add New Specification Section"
    - Input: Section Name (e.g. `OPTICAL TRANSPORT`, `SECURITY`, `CLIMATE CONTROL`)
    - Buttons: [Cancel] and [Create Section].

- [ ] **Step 3: Verification of Compose UI rendering and interactions**
  Verify all lambdas and state flows connect properly between `SiteDetailScreen` and `SiteDetailViewModel`.

- [ ] **Step 4: Commit**
  ```bash
  git add app/src/main/java/app/telecom/sitetracker/ui/sitedetail/SiteDetailScreen.kt
  git commit -m "feat(ui): implement inline section-card editor for technical specifications"
  ```

---

### Task 4: Integration Verification & Code Review

**Files:**
- Modify: `app/src/test/java/app/telecom/sitetracker/SiteUpdateAndExportTest.kt`
- All modified files

- [ ] **Step 1: Run comprehensive unit test suite**
  Verify all unit tests pass.

- [ ] **Step 2: Perform code hygiene and formatting check**
  Ensure no unused imports, correct imports, proper error handling, and zero warnings.

- [ ] **Step 3: Final Commit & Summary**
  Review git diff on `feature/specs-inline-editor` branch.
