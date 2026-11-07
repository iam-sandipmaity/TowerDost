# Clean Modern Enterprise UI Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Completely rebuild the TowerDost user interface into a Clean Modern Enterprise field tool (Apple Stocks / Datadog style) featuring flat cardless list density, hairline structural dividers, telemetry status dots, and segmented pill controls.

**Architecture:** Create a unified `EnterpriseComponents.kt` design kit (top bars, segmented controls, status dot badges, metric tiles, flat list rows, and search capsules), then refactor the application shell and 5 primary screens to consume this kit while preserving 100% of the existing Room database, import/export, and offline business logic.

**Tech Stack:** Native Android, Jetpack Compose, Material 3, IBM Plex Sans & Mono fonts, Room SQLite, Kotlin 2.0.

**Spec:** `docs/superpowers/specs/2026-11-07-clean-modern-enterprise-ui.md`

## Global Constraints

- **Offline-Only:** Never add `INTERNET` permission to `AndroidManifest.xml` or build scripts.
- **Android Compatibility:** Must run on `minSdk 24` up to `targetSdk 35` with core library desugaring.
- **Sunlight Readability:** Minimum 7:1 contrast ratio across core text and status indicators.
- **Touch Targets:** Minimum 48dp tactile touch targets across all buttons and clickable rows.
- **Zero Business Logic Regressions:** Outage calculation, down register math, Room persistence, and log imports must remain untouched.

## Review Focus

1. **Light/Dark theme readability:** Hairline borders and text colors must remain distinct in both obsidian dark mode and high-noon light mode.
2. **Tabular vertical alignment:** SAP IDs, coordinates, and hours must not shift horizontally when numbers change.
3. **Empty and error states:** Zero unhandled crashes when site lists, outages, or specs are empty.
4. **Touch responsiveness:** Ripple states must trigger cleanly across flat edge-to-edge list rows.
5. **No floating cards:** Zero nested cards or heavy drop shadows in primary list screens.

---

### Task 1: Enterprise Design System & Shared Components

**Files:**
- Modify: `app/src/main/java/app/telecom/sitetracker/ui/theme/Color.kt`
- Modify: `app/src/main/java/app/telecom/sitetracker/ui/theme/Theme.kt`
- Create: `app/src/main/java/app/telecom/sitetracker/ui/components/EnterpriseComponents.kt`

**Interfaces:**
- Produces: `EnterpriseTopBar(title: String, subtitle: String? = null, onBack: (() -> Unit)? = null, actions: @Composable RowScope.() -> Unit = {})`
- Produces: `EnterpriseSegmentedControl(options: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit)`
- Produces: `StatusDotBadge(text: String, color: Color)`
- Produces: `EnterpriseListRow(modifier: Modifier = Modifier, onClick: () -> Unit, content: @Composable ColumnScope.() -> Unit)`
- Produces: `MetricTile(label: String, value: String, valueColor: Color, modifier: Modifier = Modifier, subValue: String? = null)`
- Produces: `SearchCapsule(query: String, onQueryChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier)`

- [ ] **Step 1: Update Enterprise Color Tokens**

In `Color.kt`, refine obsidian dark ground (`#0B0F19`), surface panel (`#131B2B`), inset capsule (`#1A2438`), hairline border (`#1F293D`), and status accents (`#F59E0B` amber, `#0EA5E9` telemetry cyan, `#EF4444` signal red, `#10B981` emerald).

- [ ] **Step 2: Build `EnterpriseComponents.kt`**

Implement the 6 core components with hairline borders, IBM Plex typography, and 48dp touch targets.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/app/telecom/sitetracker/ui/theme/ app/src/main/java/app/telecom/sitetracker/ui/components/EnterpriseComponents.kt
git commit -m "style(design): introduce clean modern enterprise component system and tokens"
```

---

### Task 2: Application Shell & Compact Navigation

**Files:**
- Modify: `app/src/main/java/app/telecom/sitetracker/MainActivity.kt`

**Interfaces:**
- Consumes: `EnterpriseComponents.kt`, `Theme.kt`
- Produces: Sleek 56dp bottom navigation bar with active dot indicators and hairline top divider.

- [ ] **Step 1: Refactor `MainAppShell` Navigation Bar**

Set `NavigationBar` height to 56dp with 0dp elevation and 1dp `BorderHairline` top border. Style items with compact labels and glowing active dot/tint indicators.

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/app/telecom/sitetracker/MainActivity.kt
git commit -m "style(shell): streamline bottom navigation to compact 56dp enterprise bar"
```

---

### Task 3: Site Directory Tabular Redesign

**Files:**
- Modify: `app/src/main/java/app/telecom/sitetracker/ui/sitelist/SiteListScreen.kt`

**Interfaces:**
- Consumes: `EnterpriseComponents.kt` (`EnterpriseTopBar`, `SearchCapsule`, `StatusDotBadge`, `EnterpriseListRow`)
- Replaces: Floating `Card` list with flat, border-separated data rows.

- [ ] **Step 1: Refactor Top Bar and Search/Filters**

Use `EnterpriseTopBar` with site count badge, embedded `SearchCapsule`, and horizontal filter pills.

- [ ] **Step 2: Refactor `SiteRow` to Cardless Enterprise Layout**

Left: Mono SAP ID (16sp bold) + Site name & City/District.
Right: `StatusDotBadge` (`● ACTIVE` / `● DOWN`) + technology pill (`4G/5G`).
Subtext: Copyable GPS coordinates with icon and 1-tap map directions trigger.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/app/telecom/sitetracker/ui/sitelist/SiteListScreen.kt
git commit -m "style(sitelist): redesign site directory with flat enterprise rows and tabular typography"
```

---

### Task 4: Command Center Home Screen

**Files:**
- Modify: `app/src/main/java/app/telecom/sitetracker/ui/home/HomeScreen.kt`

**Interfaces:**
- Consumes: `EnterpriseComponents.kt` (`EnterpriseTopBar`, `MetricTile`, `StatusDotBadge`, `EnterpriseListRow`)

- [ ] **Step 1: Build Executive 3-Column Metric Strip**

Replace disjointed boxes with a unified 3-column `MetricTile` row:
- Sites Down (Large tabular red/emerald count)
- Unrestored Outages (Amber count)
- 24h Downtime Hours (Tabular hours)

- [ ] **Step 2: Build Fast Action Dock**

Tactile 48dp action buttons: "Log Outage" (high-contrast amber) & "Add Maintenance Log" (sleek secondary).

- [ ] **Step 3: Build Cardless Live Incidents Feed**

Flat data rows showing open outages with live elapsed duration and 1-tap green restore button.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/app/telecom/sitetracker/ui/home/HomeScreen.kt
git commit -m "style(home): redesign dashboard as executive telemetry command center"
```

---

### Task 5: Down Register Screen Redesign

**Files:**
- Modify: `app/src/main/java/app/telecom/sitetracker/ui/outages/DownRegisterScreen.kt`

**Interfaces:**
- Consumes: `EnterpriseComponents.kt` (`EnterpriseTopBar`, `EnterpriseSegmentedControl`, `StatusDotBadge`, `EnterpriseListRow`)

- [ ] **Step 1: Integrate `EnterpriseSegmentedControl`**

Replace Android `ScrollableTabRow` with sleek Apple/Datadog sliding pill tabs (`Live Down` | `Register Day View` | `Monthly Matrix`).

- [ ] **Step 2: Redesign Monospaced Date Bar**

Clean 44dp date strip with tabular date readout, 48dp tactile previous/next chevrons, and "Jump to Date" dialog trigger.

- [ ] **Step 3: Redesign Register Outage Rows**

Flat cardless rows with mono SAP ID, duration badge, root-cause tag with inline edit button, and 1-tap restore action.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/app/telecom/sitetracker/ui/outages/DownRegisterScreen.kt
git commit -m "style(outages): modernize down register with segmented controls and flat rows"
```

---

### Task 6: Site Detail & Insights Redesign

**Files:**
- Modify: `app/src/main/java/app/telecom/sitetracker/ui/sitedetail/SiteDetailScreen.kt`
- Modify: `app/src/main/java/app/telecom/sitetracker/ui/insights/InsightsScreen.kt`

**Interfaces:**
- Consumes: `EnterpriseComponents.kt`

- [ ] **Step 1: Rebuild `SiteDetailScreen.kt` Technical Spec Sheet**

Two-column key-value enterprise grid with hairline horizontal dividers for equipment specs.
Sticky bottom dock for "Call", "Directions", and "Add Log".

- [ ] **Step 2: Rebuild `InsightsScreen.kt` Analytics**

Datadog-style Network SLA card with large percentage indicator, horizontal Pareto breakdown bars, and top problem sites ranked table.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/app/telecom/sitetracker/ui/sitedetail/SiteDetailScreen.kt app/src/main/java/app/telecom/sitetracker/ui/insights/InsightsScreen.kt
git commit -m "style(detail-insights): modernize technical spec sheets and executive analytics"
```

---

### Task 7: Comprehensive Verification & Regression Check

**Files:**
- Test: All tests in `app/src/test/`

- [ ] **Step 1: Verify test suite**

Verify `DatesTest`, `LogImporterTest`, `BackupManagerTest`, and `ManifestAndSecurityTest`.

- [ ] **Step 2: Verify offline constraints**

Confirm zero `android.permission.INTERNET` in `AndroidManifest.xml`.

- [ ] **Step 3: Commit verification**

```bash
git commit --allow-empty -m "chore: verify clean modern enterprise UI implementation complete"
```
