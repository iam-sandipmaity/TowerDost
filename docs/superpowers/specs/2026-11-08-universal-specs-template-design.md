# Design Doc: Pillar 1 — Universal Technical Specifications Template Manager

## 1. Intent & Scope
Provide a central, global specification schema ("Universal Template") in TowerDost that defines standard equipment categories and specification fields inherited by **every site** across the application.

Currently, specifications only exist on a site if explicitly imported or typed on that specific site. If a technician wants standard fields (e.g., `DG FUEL CAPACITY`, `BATTERY BANK SOH`, `EB METER NUMBER`, `SAFETY INSPECTION DATE`) to be tracked across all sites, there is no way to define this globally. 

With the Universal Template Manager:
1. Field engineers define global specification fields and categories in one central screen.
2. Every site in the application automatically reflects these universal fields in both View Mode (showing recorded values or `—` if blank) and Edit Mode (providing ready-to-fill text boxes).
3. Serves as the master schema for Pillar 2 (Universal Site Creator) and Pillar 3 (VLOOKUP Sheet Merger).

---

## 2. Goals & Non-Goals

### Goals
1. **Central Schema Definition (`UniversalFieldEntity`):**
   - Manage standard field keys, default categories (e.g. `POWER`, `BATTERY`, `DG`, `RAN`, `TRANSMISSION`, `SAFETY`), and display sort order.
   - Ability to add, edit, or delete universal fields and sections from a dedicated screen.
2. **Universal Inheritance Across All Sites:**
   - When viewing any site's Specs tab, universal fields are merged with site-specific values:
     - If the site has data for a field, display the site's value.
     - If the site does not yet have data, display the field with placeholder `—` in View mode, and as an empty input box in Edit mode.
   - Saving specifications on a site persists newly entered values into `site_fields` for that site.
3. **Template Management UI (`UniversalTemplateScreen`):**
   - Accessible from the **Tools** tab and directly via a **"Manage Universal Template"** action in `SiteDetailScreen`.
   - Fast, tactile section-card editor with `+ Add Field`, `+ Add Section`, and deletion controls.
   - Option to pre-seed standard telecom field defaults (e.g., SMPS Make, Battery Ah, DG kVA, Antenna Tilt).
4. **Offline & Non-Destructive Storage:**
   - Stored in Room SQLite database (`universal_fields` table) with database migration (`MIGRATION_2_3`).
   - 100% offline, zero network permissions.

### Non-Goals
- Pillar 2: The standalone "Add New Site" screen (will be planned and built immediately after Pillar 1).
- Pillar 3: The VLOOKUP-style spreadsheet merger (will be built after Pillar 2).

---

## 3. Approaches Considered & Selected Approach

### Considered Approaches
* **Approach A (Selected): Dedicated `UniversalFieldEntity` Table with Dynamic Merge in ViewModel:**
  - Store global schema in `universal_fields`.
  - When a site loads, `SiteDetailViewModel` combines the site's existing `site_fields` with `universal_fields`.
  - Clean separation between global schema and per-site recorded data. Editing a universal field definition updates every site immediately without needing to alter thousands of rows in SQLite.
* **Approach B: Batch inserting empty `SiteFieldEntity` rows for every site in SQLite:**
  - Rejected: Bloats the database with thousands of blank rows for all sites, creates synchronization nightmares when a universal field is added or renamed, and slows down database performance.
* **Approach C: In-memory hardcoded enum/list:**
  - Rejected: Inflexible; users cannot customize fields for their specific telecom circle or company requirements.

---

## 4. Architectural & Technical Specification

### 4.1 Data Model (`UniversalFieldEntity.kt`)
```kotlin
@Entity(tableName = "universal_fields")
data class UniversalFieldEntity(
    @PrimaryKey
    @ColumnInfo(name = "field_key")
    val fieldKey: String,

    @ColumnInfo(name = "group_name")
    val groupName: String,

    @ColumnInfo(name = "default_value")
    val defaultValue: String? = null,

    @ColumnInfo(name = "sort_order")
    val sortOrder: Int = 0
)
```

### 4.2 Room DAO & Repository Layer
- **`UniversalFieldDao.kt`**:
  - `observeAllUniversalFields(): Flow<List<UniversalFieldEntity>>`
  - `getAllUniversalFields(): List<UniversalFieldEntity>`
  - `insertOrUpdateField(field: UniversalFieldEntity)`
  - `deleteField(fieldKey: String)`
  - `deleteSection(groupName: String)`
  - `replaceAll(fields: List<UniversalFieldEntity>)`
- **`AppDatabase.kt`**:
  - Bump `version = 3`.
  - Add `MIGRATION_2_3` creating `universal_fields` table without data loss.
- **`SiteRepository.kt`**:
  - Expose universal fields methods and seed defaults if empty.

### 4.3 Merging Logic in `SiteDetailViewModel.kt`
```kotlin
// When loading site fields:
val siteFields = siteRepository.getFieldsForSite(sapId)
val universalFields = siteRepository.getAllUniversalFields()

// Merge: for each universal field, if siteFields already has it, use site's value;
// otherwise, add placeholder item with null value so it appears in View and Edit modes.
val mergedFields = siteFields.toMutableList()
for (uni in universalFields) {
    if (mergedFields.none { it.fieldKey.equals(uni.fieldKey, ignoreCase = true) }) {
        mergedFields.add(
            SiteFieldEntity(
                id = 0L,
                sapId = actualSapId,
                fieldKey = uni.fieldKey,
                fieldValue = uni.defaultValue,
                groupName = uni.groupName,
                sortOrder = uni.sortOrder
            )
        )
    }
}
val grouped = mergedFields.groupBy { it.groupName ?: "GENERAL SPECIFICATIONS" }
```

### 4.4 UI Layer
1. **`UniversalTemplateScreen.kt` & `UniversalTemplateViewModel.kt`:**
   - Section-card based manager.
   - Shows existing categories and fields.
   - Buttons to add fields, add sections, and delete.
   - "Load Telecom Defaults" button for instant bootstrap.
2. **Integration into Navigation & Entry Points:**
   - In **ToolsScreen**: Add tile "Universal Specs Template".
   - In **SiteDetailScreen**: Add action "Manage Template" in the Specs tab.
