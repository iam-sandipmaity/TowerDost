# Design Spec — TowerDost Clean Modern Enterprise UI Redesign

**Author:** Antigravity  
**Date:** 2026-11-07  
**Status:** In Review  
**Target Architecture:** Native Android (Jetpack Compose, Material 3, Room, Kotlin 2.0, minSdk 24, targetSdk 35)

---

## 1. Problem Statement & Design Objectives

### Current State Issues
TowerDost currently relies on standard Material 3 boilerplate layouts with floating card fatigue:
- Every screen stacks heavy, rounded cards with default 14dp padding and generic shadows.
- Navigation uses a bulky 80dp bottom bar with standard pill indicators.
- Information density is low; lists lack high-speed tabular scanning.
- Visual hierarchy uses basic text sizing and standard outlines rather than crisp telemetry micro-dots and enterprise data density.

### Design Objectives
Transform TowerDost into a **Clean Modern Enterprise** field utility inspired by systems like Apple Stocks, Datadog, and Linear:
1. **Cardless, Edge-to-Edge List Density:** Replace floating cards with sleek, flat data rows separated by 1dp hairline dividers (`#1F293D`).
2. **Segmented Controls:** Modern pill-slider toggles for tabbed views (Live Down | Register Day View | Monthly Matrix).
3. **Telemetry Status Dot Badges:** Micro-badges with glowing status dots (`● ACTIVE`, `● DOWN`, `● RESTORED`) with 7:1 sunlight contrast.
4. **Tabular Monospace Precision:** All SAP IDs, timestamps, GPS coordinates, and metric readings displayed in IBM Plex Mono.
5. **Streamlined Shell Navigation:** Compact 56dp bottom bar with hairline structural dividers and active indicators.
6. **Zero Feature/Business Logic Disruption:** 100% preservation of offline Room SQLite persistence, flexible import/export, deduplication, and zero network permission.

---

## 2. Visual Foundation & Design Tokens

### Color Palette

| Token Name | Dark Mode (Primary Field) | Light Mode (High Noon) | Purpose |
|---|---|---|---|
| `Ground` | `#0B0F19` (Deep obsidian slate) | `#F8FAFC` (Architectural paper) | Root screen canvas |
| `Surface` | `#131B2B` (Panel surface) | `#FFFFFF` (Pure white card) | Elevated sections / sheet backgrounds |
| `SurfaceElevated` | `#1A2438` (Inset surface) | `#F1F5F9` (Muted inset) | Search inputs, filter capsules |
| `BorderHairline` | `#1F293D` (1dp crisp hairline) | `#E2E8F0` (1dp light divider) | Row dividers, borders, keyline separators |
| `TelecomAmber` | `#F59E0B` (Vibrant amber) | `#D97706` (Deep amber) | Primary actions, branding, warning |
| `TelemetryCyan` | `#0EA5E9` (RF cyan) | `#0284C7` (Deeper cyan) | GPS coordinates, technology tags |
| `StatusDownRed` | `#EF4444` (Signal red) | `#DC2626` (Crimson red) | Outage alerts, critical severity |
| `StatusUpGreen` | `#10B981` (Industrial emerald) | `#059669` (Dark emerald) | Operational status, restored SLA |
| `TextPrimary` | `#F8FAFC` (100% stark white) | `#0F172A` (100% deep slate) | Primary titles, SAP IDs, metrics |
| `TextSecondary` | `#94A3B8` (Legible muted slate) | `#475569` (Legible slate) | Subtitles, city, timestamps |

### Typography
- **Headings & Actions:** IBM Plex Sans (`Bold`, `SemiBold`, `Medium`) with tight tracking.
- **Data & Identifiers:** IBM Plex Mono (`Bold`, `Medium`, `Regular`) with genuine tabular numerals for vertical alignment.

---

## 3. Shared Enterprise Components

1. **`EnterpriseTopBar`:**
   - Height: 52dp.
   - Contains the TowerDost logo (`ic_towerdost_logo`), clean screen title, "100% Offline" status pill, and action icons.
   - Bottom 1dp hairline border (`BorderHairline`).

2. **`EnterpriseSegmentedControl`:**
   - Capsule container (`SurfaceElevated`) with smooth background pill indicator for the active option.
   - Minimum 40dp touch target with crisp typography.

3. **`StatusDotBadge`:**
   - 6dp colored circular dot + uppercase text (`● ACTIVE`, `● DOWN`, `● RESTORED`).
   - Tinted background at 12% opacity with 1dp border at 30% opacity.

4. **`EnterpriseListRow`:**
   - Flat row layout with 1dp bottom hairline divider.
   - Left: Mono SAP ID (16sp bold) + Site Name & Location subtitle.
   - Right: Status dot badge + technology tag (e.g. `4G/5G`).
   - Active ripple state and 48dp minimum click area.

5. **`MetricTile`:**
   - Compact enterprise stat tile with large tabular numeral (24sp bold), label (11sp uppercase), and color-coded trend/status bar.

6. **`SearchCapsule`:**
   - 44dp height, `SurfaceElevated` background, subtle 1dp border, search icon, placeholder, and 1-tap clear button.

---

## 4. Screen Re-Architectures

### 4.1 Shell & Navigation (`MainActivity.kt`)
- Bottom navigation bar height reduced from 80dp to 56dp.
- Hairline top divider (`BorderHairline`).
- Active items highlighted with a subtle amber dot and medium weight label.

### 4.2 Home / Dashboard (`HomeScreen.kt`)
- **Executive Metric Strip:** 3-column tile row:
  - Active Down Sites (Red tabular count)
  - Unrestored Incidents (Amber count)
  - 24h Downtime Hours (Tabular format)
- **Quick Action Bar:** 1-Tap high-contrast "Log Outage" button & "Maintenance Visit" button.
- **Live Incidents Feed:** Flat edge-to-edge list rows showing currently open outages with live elapsed hours and a 1-tap "Restore Now" action.

### 4.3 Site Directory (`SiteListScreen.kt`)
- **Header:** TowerDost emblem, total site count badge ("1,248 Sites").
- **Search & Filters:** Integrated search capsule + horizontal pill filters (`All`, `Active`, `Down`, `RTT`, `GBT`).
- **Site List:** Flat data rows with copyable GPS coordinates, quick directions launcher, and status dot badge.

### 4.4 Down Register (`DownRegisterScreen.kt`)
- **Segmented Control:** `Live Down` | `Register Day View` | `Monthly Matrix`.
- **Date Bar:** Monospaced date scroller with previous/next 48dp buttons and a "Jump to Date" calendar button.
- **Register Rows:** SAP ID in bold mono, duration badge, root-cause tag with inline edit button, and 1-tap green restore button.

### 4.5 Site Detail (`SiteDetailScreen.kt`)
- **Header:** Sticky SAP ID hero with quick GPS coordinates and call/directions buttons.
- **Spec Sheet:** Clean 2-column key-value enterprise grid with hairline dividers.
- **Event Timeline:** Flat chronological feed of visits, maintenance, and outages for that site.

### 4.6 Insights & Analytics (`InsightsScreen.kt`)
- **Network SLA Card:** Large percentage readout (e.g. `99.82% Availability`) with color-coded benchmark status.
- **Root-Cause Pareto:** Horizontal stacked distribution bars with percentage labels.
- **Top 10 Problem Sites:** Clean ranked tabular table with outage frequency and total downtime hours.

---

## 5. Verification Plan

- [ ] Verify test suite passes with zero errors: `./gradlew test`.
- [ ] Verify 100% offline security: Zero `android.permission.INTERNET`.
- [ ] Verify 48dp touch targets on all interactive items.
- [ ] Verify crisp contrast ratio >= 7:1 in both Dark and Light modes.
