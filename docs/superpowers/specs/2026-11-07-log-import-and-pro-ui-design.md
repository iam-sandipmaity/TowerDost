# Design Doc: TowerDost Log Import & Professional UI/UX Refinement

## 1. Intent & Scope
Keep all existing app features and business logic exactly as they are right now. Focus strictly on two deliverables:
1. **Import Option for Log:** Provide an intuitive way to import site logs/incident history (CSV/JSON/XLSX) so that switching devices does not result in lost log data.
2. **Professional UI / UX (Non-AI-Slop):** Overhaul the look and feel to look like an app crafted by a professional field software engineer—genuine typography, high-contrast industrial colors, crisp hairline borders, zero pastel washes, zero nested cards, and clear tabular layout.

---

## 2. Goals & Non-Goals

### Goals
1. **Log Data Import:**
   - Import logs exported from TowerDost (`TowerDost_Register_*.csv`) or external spreadsheets into the `incidents` table.
   - 1-tap JSON Full Backup & Restore in Tools for instant device migration.
   - Flexible timestamp parsing (`Dates.parseFlexibleDateTime`) for standard date formats.
   - Non-destructive deduplication (`Update & Append` or `Add New Only`).
2. **Professional UI/UX:**
   - Real **IBM Plex Sans** and **IBM Plex Mono** typography across the app.
   - High-contrast industrial palette: Deep space slate (`#0A0E17`), crisp slate surfaces (`#121927`), 1dp structural hairline borders (`#24334A`), Telecom Amber (`#F59E0B`), and Telemetry Cyan (`#0EA5E9`).
   - Remove washed-out pastel fills; use crisp, solid status badges.
   - Tactile 48dp touch targets and clear information hierarchy.

### Non-Goals
- Modifying outage calculation or down register logic.
- Adding new complex outage management features.
- Adding network permissions (remains 100% offline).

---

## 3. Approaches Considered & Selected Approach

### Considered Approaches
- **Approach A (Selected): Unified Migration System & Full Industrial Field UI Redesign:**
  - Build a dedicated, flexible Log Import pipeline supporting CSV, XLSX, and JSON logs with column mapping and auto-detection.
  - Implement Full Offline Backup (export complete JSON archive of sites, specs, and logs) and 1-tap Restore.
  - Redesign UI systematically: typography (real IBM Plex), color scheme (high-contrast industrial dark/light), flat 1dp structural cards, crisp status tags.
- **Approach B: Separate Standalone Log Import Screen only:**
  - Only import CSV logs without full backup/restore and minimal styling tweaks. (Rejected: Fails the seamless device migration requirement and leaves UI feeling unpolished).
- **Approach C: Raw Database File Copy:**
  - Exposing raw SQLite `.db` file transfer. (Rejected: Fragile across Android versions, risks corrupting active Room journal/WAL, cannot import CSVs from other team members).

---

## 4. Architectural & Technical Specification

### 4.1 Log & Incident Import Pipeline (`LogImporter.kt`)
- **Data Model:** Populates `IncidentEntity` table via `IncidentDao`.
- **Target Fields for Log Mapping:**
  - `ID` (Optional - internal log ID for 1:1 parity)
  - `SAP_ID` (Required)
  - `TITLE` / Fault Summary
  - `CATEGORY` (Issue, Maintenance, Visit, Note)
  - `STATUS` (Open, In Progress, Resolved, Closed)
  - `SEVERITY` (Critical, Major, Minor, Info)
  - `IS_OUTAGE` (Boolean/Int flag)
  - `DOWN_AT` / Occurred At
  - `RESTORED_AT`
  - `ENGINEER` / Technician Name
  - `TICKET_REF` / TT Number
  - `ACTION_TAKEN`
  - `DESCRIPTION` / Remarks
  - `LAT`, `LNG`

- **Matching & Deduplication Resolution:**
  - If incoming record contains an internal `id`, match by `id`.
  - If incoming record does not contain `id` (e.g. standard CSV/XLSX), match existing records using `sap_id` and a ±60-second (1-minute) tolerance window on `occurred_at`/`down_at`, OR exact match on non-empty `ticket_ref`.
  - Under `UPDATE_AND_ADD`: Updates matching incident fields (preserving internal ID), inserts non-matching ones.
  - Under `ADD_NEW_ONLY`: Skips any matching row, inserts only new ones.
  - Under `REPLACE_ALL`: Clears incidents and inserts incoming ones.

- **Flexible Date Parsing (`Dates.parseFlexibleDateTime`):**
  - Sequentially attempts:
    1. `dd/MM/yyyy HH:mm`
    2. `dd/MM/yyyy HH:mm:ss`
    3. `yyyy-MM-dd HH:mm:ss`
    4. `yyyy-MM-dd'T'HH:mm:ss`
    5. `dd MMM yyyy, HH:mm` or `dd MMM yyyy HH:mm`
    6. Raw epoch millisecond or second strings
    7. Fallback to `dd/MM/yyyy` (time at 00:00:00 IST)

- **UI Flow for Log Ingestion:**
  - Fast, streamlined 2-step review: File Selection -> Auto-Detected Preview & Mapping Review -> Import with Progress.
  - Accessible directly from **ToolsScreen** ("Import Site Logs") and from **ActivityScreen** top bar.

### 4.2 Full Database Backup & Restore (`BackupManager.kt`)
- **Export Full Backup:**
  - Serializes `sites`, `site_fields`, `incidents`, `incident_history`, and custom `reasons` into a complete JSON archive:
    ```json
    {
      "version": 1,
      "appName": "TowerDost",
      "exportedAt": 1731000000000,
      "sites": [...],
      "fields": [...],
      "incidents": [...],
      "history": [...],
      "reasons": [...]
    }
    ```
- **Restore Full Backup:**
  - Prompts with explicit dialog: "Clean Overwrite (Recommended for device migration - wipes current database and restores backup exactly)" or "Merge into Existing Data".
  - Executes inside `AppDatabase.withTransaction`:
    - If Clean Overwrite: clears existing tables and rebuilds FTS search index from restored sites.
    - Inserts all entities with atomic rollback on failure.

### 4.3 UI/UX Design System Overhaul ("Industrial Field Grade")
1. **Typography (`Type.kt`):**
   - Bind fonts using genuine `Font(R.font.ibm_plex_sans_regular)`, `Font(R.font.ibm_plex_sans_medium)`, `Font(R.font.ibm_plex_sans_bold)` for `IbmPlexSansFamily`.
   - Bind `Font(R.font.ibm_plex_mono_regular)`, `Font(R.font.ibm_plex_mono_medium)`, `Font(R.font.ibm_plex_mono_bold)` for `IbmPlexMonoFamily`.
   - Use monospace typography tokens for all SAP IDs, timestamps, downtime calculations, coordinates, and telemetry badges.
2. **Color Palette (`Color.kt` & `Theme.kt`):**
   - **Dark Theme (Default Field Mode):**
     - Ground: `#0A0E17` (Deep space slate)
     - Surface: `#121927` (Crisp industrial slate)
     - Surface Elevated / Insets: `#1A2436`
     - Structural Hairline Border: 1dp solid `#24334A`
     - Primary Accent: `#F59E0B` (High-visibility Telecom Amber)
     - Secondary Accent: `#0EA5E9` (RF Telemetry Cyan)
     - Status: Red `#EF4444`, Green `#10B981`, Amber `#F59E0B`, Blue `#3B82F6`
   - **Light Theme (High Noon Sunlight Mode):**
     - Ground: `#F4F6F9` (Crisp paper slate)
     - Surface: `#FFFFFF`
     - Outline: `#CBD5E1`
     - Primary: `#D97706`
     - Text Primary: `#0F172A` (100% stark slate)
     - Text Secondary: `#475569`
3. **Screen Refinements:**
   - **Zero Pastel Washes:** Replace `#FEF2F2`, `#F0FDF4`, `#FFF7ED` with industrial high-contrast slate surfaces + crisp colored status indicators.
   - **Zero Nested Cards:** Single-level flat surfaces with 1dp borders.
   - **Strict 48dp Touch Targets:** On every button, action row, tab, and filter chip.
   - **ToolsScreen:**
     - Top dedicated section: "Device Migration & Backup" with "Full Backup (.json)" and "Restore Backup (.json)".
     - "Data Ingestion" with "Import Master Site Sheet" and "Import Site Logs".
   - **ActivityScreen & DownRegisterScreen:**
     - Streamlined quick actions, high-density tabular cards, fast import/export buttons.

---

## 5. Verification Plan
- **Unit Tests:**
  - Test `LogImporter` with sample CSV matching `Exporter.exportRegisterCsv` output.
  - Test `Dates.parseFlexibleDateTime` with all supported date string patterns.
  - Test `BackupManager` JSON export and restore round-trip data fidelity.
  - Test merge policies (`UPDATE_AND_ADD`, `ADD_NEW_ONLY`).
- **Integration & UI Tests:**
  - Verify `ToolsScreen` displays new Backup & Restore actions.
  - Verify theme colors and typography render correctly with IBM Plex fonts.
