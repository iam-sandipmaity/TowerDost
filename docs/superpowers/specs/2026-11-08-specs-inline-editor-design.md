# Design Doc: Specifications Inline Editing (Overview Parity)

## 1. Intent & Scope
Provide a seamless, inline editing experience for technical equipment specifications within the Site Detail screen, matching the design pattern and tactile workflow of the Site Overview tab.

Field technicians currently have to edit specifications one at a time through modal popups (`AlertDialog`), making batch edits, multi-field inspections, and creating new sections cumbersome. This update replaces modal dialogs with an in-place section-card editor where specifications are displayed in editable data boxes, new specs and sections can be added on the fly, and changes are validated and saved atomically to the local Room database and FTS index.

---

## 2. Goals & Non-Goals

### Goals
1. **Overview UX Parity:**
   - Specifications tab provides an "Unlock to Edit" / "Edit Specs" toggle mirroring the Overview tab.
   - When editing, specs are rendered in inline data boxes (`OutlinedTextField`) grouped by section, rather than individual modal dialogs.
2. **Easy Section & Spec Management:**
   - Add new spec fields directly within any existing section with one tap.
   - Add entirely new sections (e.g. `OPTICAL TRANSPORT`, `SECURITY & SURVEILLANCE`, `CLIMATE CONTROL`) easily.
   - Delete individual spec rows or entire sections with inline controls.
   - Rename sections if needed.
3. **Data Integrity & Safeguards:**
   - Isolated draft state (`draftSpecsSections`) so edits don't mutate live data until explicitly saved.
   - Confirmation dialog before committing batch changes to the SQLite database.
   - Atomic replacement via Room `@Transaction` so all fields for the site remain consistent.
   - Automated update of `SiteEntity.updatedAt` timestamp and full-text search (`SiteFts`) index.
4. **Design Consistency (Zero AI-Slop & Industrial Contrast):**
   - High sunlight visibility, IBM Plex typography, crisp hairline borders, 48dp touch targets, `TelecomAmber` actions, and `RFCyan` section accents.

### Non-Goals
- Modifying the underlying database schema or table names (`site_fields` schema remains intact).
- Changing CSV export formats (the exported CSVs will naturally reflect updated specifications).
- Adding internet/cloud sync permissions (TowerDost remains 100% offline).

---

## 3. Approaches Considered & Selected Approach

### Considered Approaches
* **Approach A (Selected): Section-Card Inline Editor with Draft State:**
  - Mirroring Overview's pattern, entering edit mode converts the Specs tab into a continuous scrollable form.
  - Specifications are grouped into section cards. Each item displays two input boxes (`Field Name` and `Field Value`) with a delete button.
  - Each card has an "+ Add Spec" button; the tab footer has "+ Add Section", "[Cancel]", and "[Save Changes]".
  - Trade-off: Requires managing a draft section list in ViewModel, but delivers the fastest, most intuitive field workflow.
* **Approach B: Bottom Sheet Multi-Step Wizard:**
  - Open a modal bottom sheet with paginated steps per group.
  - Trade-off: Breaks visual continuity, hides site context, and is slower than inline editing.
* **Approach C: Row-by-row Inline Accordion:**
  - Only expand a single row when clicked in the list.
  - Trade-off: Cannot see or batch-edit multiple fields across sections simultaneously, defeating the user's primary need.

---

## 4. Architectural & Technical Specification

### 4.1 Data Models & State
```kotlin
data class SpecDraftItem(
    val id: Long = 0L,
    val fieldKey: String = "",
    val fieldValue: String = ""
)

data class SpecDraftSection(
    val sectionName: String,
    val items: List<SpecDraftItem>
)

data class SiteDetailUiState(
    // Existing fields...
    val isEditingSpecs: Boolean = false,
    val draftSpecsSections: List<SpecDraftSection> = emptyList(),
    val confirmSaveSpecs: Boolean = false,
    val isAddingSectionDialog: Boolean = false
)
```

### 4.2 Room DAO & Repository Layer
- **`FieldDao.kt`**:
  Add atomic batch replacement transaction:
  ```kotlin
  @Transaction
  suspend fun replaceFieldsForSite(sapId: String, fields: List<SiteFieldEntity>) {
      deleteFieldsForSite(sapId)
      insertFields(fields)
  }
  ```
- **`SiteRepository.kt`**:
  Add `replaceSiteSpecs(sapId: String, fields: List<SiteFieldEntity>)`:
  1. Calls `fieldDao.replaceFieldsForSite(sapId, fields)`.
  2. Updates `siteDao.updateSite(site.copy(updatedAt = System.currentTimeMillis()))`.
  3. Updates FTS table `SiteFts` with concatenated field text for search indexing.

### 4.3 ViewModel Operations (`SiteDetailViewModel.kt`)
- `startEditingSpecs()`: Populates `draftSpecsSections` from `groupedSpecs` (or creates default "GENERAL SPECIFICATIONS" if empty).
- `updateDraftSpecItem(sectionIndex: Int, itemIndex: Int, key: String, value: String)`
- `addDraftSpecItem(sectionIndex: Int)`
- `removeDraftSpecItem(sectionIndex: Int, itemIndex: Int)`
- `addDraftSection(name: String)`
- `removeDraftSection(sectionIndex: Int)`
- `cancelEditingSpecs()`: Clears draft state and returns to view mode.
- `requestSaveSpecs()`: Validates keys and triggers `confirmSaveSpecs = true`.
- `confirmAndSaveSpecs()`: Persists draft sections atomically and refreshes UI.

### 4.4 UI Layer (`SiteDetailScreen.kt`)
- **`SpecsTab`**:
  - In View Mode: Shows Top Banner ("Specifications: N items", "Unlock to Edit" / "Edit Specs" button) + SearchCapsule + Grouped Spec rows.
  - In Edit Mode: Shows Editing Header Banner (`TagMaintBg`) + Section Cards with editable text boxes + "+ Add Field" button + "+ Add Section" button + Save / Cancel action buttons.
- **Safeguard Dialogs:**
  - Overview-style confirmation `AlertDialog` before saving specs.
  - Add Section dialog prompting for section name.
