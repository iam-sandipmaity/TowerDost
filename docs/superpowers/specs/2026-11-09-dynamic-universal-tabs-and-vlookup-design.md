# TowerDost — Dynamic Universal Tabs & Flexible VLOOKUP Mapping Design

**Date:** 2026-11-09  
**Status:** Approved by User  
**Target Architecture:** Native Offline Android (Jetpack Compose, Room SQLite, Kotlin Coroutines/Flow)

---

## 1. Intent & The Why

TowerDost is an offline-first telecom field engineering tool. Currently, the site detail interface provides three hardcoded sections:
1. **Specs** (Equipment and technical specifications grouped into categories)
2. **Logs** (Historical and active incident/outage records)
3. **Overview** (Core site attributes like SAP ID, Name, Coordinates, District, Technology)

Telecom field operations frequently deal with specialized datasets that do not neatly fit into generic technical specs or basic site metadata—such as **Tenant Sharing Info**, **EB Billing & Metering**, **Solar / Green Energy**, **Site Security & Access Logs**, or **Quarterly Audit Checklists**.

Users need the power to:
1. Define custom, universal tabs under sites that behave as first-class sections alongside Specs, Logs, and Overview.
2. Structure custom tabs with grouped sub-sections (cards), exactly like the Specs tab.
3. During **VLOOKUP** spreadsheet imports, freely route incoming columns into existing tabs/sections or define a brand-new tab on the fly.
4. Protect against accidental data loss with an enforced, verified local JSON backup protocol before any tab deletion is allowed.
5. Retain 100% backward compatibility: existing database records, master CSV exports, and default tabs remain completely undisturbed.

---

## 2. Goals, Non-Goals & Anti-Goals

### Goals
- **Universal Custom Tabs:** Users can create, view, edit, reorder, and delete custom tabs across all sites.
- **Scrollable Site Detail Tab Bar:** Replace the rigid 3-button segmented control with a clean, high-sunlight scrollable tab strip: `[Specs] [Logs] [Overview] [Tenant Info] [EB Billing] ... [+]`.
- **Hierarchical Section Layout:** Like `Specs`, each custom tab supports grouped sub-sections (cards) with inline editing, adding sections, adding fields, renaming, and deleting fields.
- **VLOOKUP Routing Flexibility:** In the column mapping step of VLOOKUP, users can assign any column to:
  - Core Site Attributes (Overview)
  - Existing Tabs (`Specs`, `Tenant Info`, etc.) with group selection
  - `+ Create New Tab...` to define a new section right inside the import flow.
- **Mandatory Pre-Deletion Backup Protocol:** Before deleting any custom tab that contains site records, TowerDost mandates an export of a JSON backup file to the device storage and explicitly verifies the file on the filesystem before unlocking the final delete action.
- **Zero Regression:** All existing sites, specs, incidents, and tests continue functioning without data loss or breaking changes.

### Non-Goals
- We are not modifying the database structure of `incidents` or how the Logs tab manages tickets.
- We are not replacing core `sites` table columns (SAP ID, lat, lng, etc.)—they remain the backbone of Overview.
- We are not introducing online/cloud synchronization (app remains strictly offline with zero `INTERNET` permission).

### Anti-Goals
- **No data loss during SQLite migration:** Migrating from version 3 to 4 must preserve all existing `universal_fields` and `site_fields` using non-destructive table copy.
- **No unbacked-up tab deletions:** Deleting a universal tab without a verified JSON backup is impossible.

---

## 3. Data Architecture & Schema Evolution (Migration 3 -> 4)

### 3.1 New Entity: `SiteTabEntity`
```kotlin
@Entity(tableName = "site_tabs")
data class SiteTabEntity(
    @PrimaryKey
    @ColumnInfo(name = "tab_id")
    val tabId: String, // Normalized identifier, e.g. "TENANT_INFO"

    @ColumnInfo(name = "display_name")
    val displayName: String, // Human-readable label, e.g. "Tenant Info"

    @ColumnInfo(name = "sort_order")
    val sortOrder: Int = 0,

    @ColumnInfo(name = "is_system")
    val isSystem: Boolean = false // True for SPECS, LOGS, OVERVIEW
)
```

Default System Seed:
- `SPECS` ("Specs", sortOrder = 0, isSystem = true)
- `LOGS` ("Logs", sortOrder = 1, isSystem = true)
- `OVERVIEW` ("Overview", sortOrder = 2, isSystem = true)

### 3.2 Schema Updates to Existing Entities
To maintain full backwards compatibility and prevent namespace collisions across tabs:
- **`universal_fields`**: Migrated to composite primary key `(tab_id, field_key)` via standard non-destructive table copy (`CREATE new, COPY data, DROP old, RENAME`). Existing rows default to `tab_id = 'SPECS'`.
- **`site_fields`**: Add column `tab_id TEXT NOT NULL DEFAULT 'SPECS'`. Existing rows default to `tab_id = 'SPECS'`. In the database, existing `group_name` continues to represent sub-groups or categories within that tab.

### 3.3 Database Migration: `MIGRATION_3_4`
```sql
-- 1. Create site_tabs registry
CREATE TABLE IF NOT EXISTS `site_tabs` (
    `tab_id` TEXT NOT NULL PRIMARY KEY,
    `display_name` TEXT NOT NULL,
    `sort_order` INTEGER NOT NULL DEFAULT 0,
    `is_system` INTEGER NOT NULL DEFAULT 0
);

INSERT OR IGNORE INTO `site_tabs` (`tab_id`, `display_name`, `sort_order`, `is_system`)
VALUES 
    ('SPECS', 'Specs', 0, 1),
    ('LOGS', 'Logs', 1, 1),
    ('OVERVIEW', 'Overview', 2, 1);

-- 2. Add tab_id column to site_fields
ALTER TABLE `site_fields` ADD COLUMN `tab_id` TEXT NOT NULL DEFAULT 'SPECS';

-- 3. Upgrade universal_fields to composite primary key (tab_id, field_key) preserving all data
CREATE TABLE IF NOT EXISTS `universal_fields_new` (
    `tab_id` TEXT NOT NULL DEFAULT 'SPECS',
    `field_key` TEXT NOT NULL,
    `group_name` TEXT NOT NULL,
    `default_value` TEXT,
    `sort_order` INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY(`tab_id`, `field_key`)
);

INSERT INTO `universal_fields_new` (`tab_id`, `field_key`, `group_name`, `default_value`, `sort_order`)
SELECT 'SPECS', `field_key`, `group_name`, `default_value`, `sort_order` FROM `universal_fields`;

DROP TABLE `universal_fields`;
ALTER TABLE `universal_fields_new` RENAME TO `universal_fields`;
```

---

## 4. UI & UX Workflows

### 4.1 Site Detail Screen (`SiteDetailScreen.kt`)
1. **Dynamic Tab Strip:**
   - Displays all active tabs from `site_tabs` sorted by `sort_order`.
   - Each tab badge displays its item count (e.g. `Specs (14)`, `Logs (3)`, `Overview`, `Tenant Info (5)`).
   - Trailing `+` button in the tab bar allows adding a new custom tab directly from the site view.
2. **Built-in Tabs Behavior:**
   - `Specs`: Exactly matches current behavior with grouped specification cards, inline editing, and add section.
   - `Logs`: Displays incidents, outage status, repeat faults, and add log action.
   - `Overview`: Master site metadata, map link, GPS coordinates, and contact details.
3. **Custom Tab Content (`CustomTabContent`):**
   - Each custom tab supports **grouped sections (Option 1B)**, with section cards, header titles, add section, add field, inline editing, and delete actions.
   - **View Mode:** High-contrast key-value list rows with one-tap clipboard copy and search filter.
   - **Edit Mode:** Full inline editing matching Specs ergonomics (add section, add field, rename field, change value, delete field).
4. **Mandatory Pre-Deletion Backup Protocol (Option 2A + Backup Gate):**
   - When a user initiates deleting a custom tab, a dialog shows the number of sites affected:
     *"⚠️ This tab contains data across X sites. To prevent irreversible data loss, TowerDost requires generating a verified JSON backup file to your device storage before deletion."*
   - The user must tap **"Generate Safety Backup (.json)"**.
   - `BackupManager` writes the full backup JSON containing sites, fields, tabs, and incidents to the device.
   - TowerDost tests that `backupFile.exists() && backupFile.length() > 0`.
   - Only once verified, the red **"Confirm Delete Tab"** button becomes active with a confirmation prompt. If backup fails or is missing, deletion is strictly aborted.

### 4.2 Universal Template Screen (`UniversalTemplateScreen.kt`)
- Users can view all Universal Tabs in a tabbed/segmented view.
- Supports adding universal default fields and sections to custom tabs.
- Reorder tabs, sections, and fields globally.

### 4.3 VLOOKUP Merger Screen & Engine (`VlookupMergerScreen.kt` & `VlookupEngine.kt`)
1. **Column Mapping Step (Step 3):**
   - Target field dropdown displays:
     - `IGNORE` ("Ignore Column")
     - `CORE SITE ATTRIBUTES` (Overview attributes: Name, Lat, Lng, Circle, Technology, etc.)
     - `SITE TABS & SPECIFICATIONS`:
       - `Specs` (Sub-groups: General, Power, Battery, DG, etc.)
       - Each existing custom tab (e.g., `Tenant Info`, `EB Billing`) with its sections
     - `+ CREATE NEW TAB...`:
       - User types a new tab name (e.g. `Security`).
       - Creates the tab definition immediately and maps the column with `tabId = "SECURITY"`.
2. **Merge Preview (Step 4):**
   - Previews will clearly indicate the destination tab and section for each mapped column (e.g. `[EB Billing • Main Meter] Meter Number`).
3. **Execution (`VlookupEngine.kt`):**
   - During merge, any newly introduced `tab_id` is inserted into `site_tabs` if not already present.
   - Newly introduced fields are added to `universal_fields` so other sites have the blueprint template.
   - Incoming values are written to `site_fields` with the appropriate `tab_id`, `group_name`, and `field_key`.
   - FTS index is updated to include fields across all tabs.

---

## 5. Backward Compatibility & Verification Strategy

1. **Schema Integrity & Migration Test:**
   - Unit test verifying SQLite migration from version 3 to 4 without losing existing `site_fields` or `universal_fields`.
2. **Export Compatibility:**
   - `Exporter.exportSingleSiteCsv` and master exports will include fields across all tabs, formatted cleanly.
3. **Pre-Deletion Backup Test:**
   - Test verifying that tab deletion cannot execute without a verified backup file on disk.
4. **Unit & Integration Tests:**
   - `SiteTabDaoTest`: CRUD on tabs and ordering.
   - `SiteDetailViewModelTest`: Switching tabs, adding custom tabs, editing sections/fields within a custom tab, pre-deletion backup verification.
   - `VlookupEngineTest`: Merging columns into existing custom tabs and auto-creating new tabs during merge.
   - `UniversalTemplateTest`: Ensuring universal blueprint correctly propagates custom tabs and fields.
