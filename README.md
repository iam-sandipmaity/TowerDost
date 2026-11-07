# TowerDost (टावर दोस्त)
### The 100% Offline Native Field Engineering Companion for Telecom Cell Towers

[![Android](https://img.shields.io/badge/Platform-Android%207.0%2B%20(API%2024--35)-0A0E17?style=flat&logo=android&logoColor=3DDC84)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.21-0A0E17?style=flat&logo=kotlin&logoColor=7F52FF)](https://kotlinlang.org)
[![Jetpack Compose](https://img.shields.io/badge/UI-Jetpack%20Compose%20%2B%20Material%203-0A0E17?style=flat&logo=jetpackcompose&logoColor=4285F4)](https://developer.android.com/jetpack/compose)
[![Room Database](https://img.shields.io/badge/Database-Room%202.6%20%2B%20SQLite%20FTS4-0A0E17?style=flat&logo=sqlite&logoColor=003B57)](https://developer.android.com/training/data-storage/room)
[![Offline Security](https://img.shields.io/badge/Network-Zero%20Internet%20Permission-10B981?style=flat&logo=shield&logoColor=white)](#offline-only-guarantee)
[![Tests](https://img.shields.io/badge/Unit%20Tests-44%20Passing-10B981?style=flat)](app/src/test)

---

## Overview

**TowerDost** is a purpose-built, rugged, offline-only native Android tool engineered specifically for cell tower field technicians, cluster supervisors, and telecom operations teams. 

In intense outdoor conditions—monsoons, remote rural deadzones, intense tropical heat, or permanent airplane mode—telecom engineers cannot rely on cloud-connected web portals or bloated spreadsheets. **TowerDost runs entirely on-device with zero network requests**, giving engineers instant access to tower specifications, outage registers, maintenance history, and diagnostic reports in milliseconds.

```
       ▲  [RF Wavefronts]
      / \
     /   \  TowerDost Industrial Emblem
    |=====| Top Sector Array
    | \ / | Upper K-Truss
    |==O==| Telemetry Hub
    | / \ | Lower Lattice
   /=======\ Ground Anchor Pedestal
```

---

## Core Capabilities

### 1. Master Site Intelligence (<50ms Search)
- **Instant Full-Text Search (SQLite FTS4):** Search across 10,000+ towers in under 50ms by SAP ID, tower name, circle, district, or arbitrary technical specifications.
- **Smart SAP Normalization:** Alphanumeric normalization matches any query variant (`"DEL-1001"`, `"del 1001"`, `"DEL1001"`).
- **Proximity & Orientation:** Calculates Haversine distance and compass bearing to nearby sites using on-device GPS without internet maps.
- **Direct Navigation & Dialing:** Launches device GPS navigation (`geo:lat,lng`) and calls local site technicians with 1-tap.

### 2. Technical Specifications & Master Overview Editor
- **47+ Technical Equipment Attributes:** Deep visibility into Power (SMPS capacity, modules, EB phase), Generator (DG KVA, fuel level, controller), Battery (type, AH capacity, string count), Transmission (MW hops, OFC ring), and Radio (antenna azimuth, tilt, tenancy).
- **Accidental-Edit Safeguards:** 
  - Overview data is **locked by default** against accidental thumb-taps while moving.
  - Modifying details requires unlocking edit mode and confirming changes via a dedicated prompt.
  - Technical specs feature an intentional modal editor with warning notices and two-step permanent deletion guards.
- **Custom Equipment Specs:** Add custom fields on-the-fly to adapt to any network expansion.

### 3. Real-Time Outage & Activity Logger
- **Flexible Logging Categories:**
  - `⚠️ Issue / Outage`: Severe network-impacting faults with downtime tracking.
  - `🔧 Routine Maintenance`: Preventive servicing, DG maintenance, SMPS module replacements.
  - `📋 Site Visit`: Security audits, landlord access visits, landlord fuel checks.
  - `📝 General Note`: Tower observations and technician turnover notes.
- **Customizable Timestamps:** Log past events accurately after returning from zero-connectivity remote regions.
- **Root-Cause Classification:** Standardized categorization across Power (EB/DG/Battery), Transmission (OFC cut/MW fail), RAN/Hardware, and Environmental.

### 4. Day-by-Day Down Register & SLA Tracking
- **Clipped Downtime Hours Calculation:** Accurately attributes outage durations that cross midnight boundaries into exact daily clipped hours.
- **Daily Calendar Navigation:** Review any past day's outage list, active down sites, and restoration times.
- **Reason Code Catalog:** Telecom-standard categories (EB Failure, Battery Depleted, DG Auto-Start Failure, OFC Fiber Cut, Card Fault, Severe Weather).

### 5. Downtime Insights & Repeat Failure Detector
- **Repeat Fault Warning:** Automatically highlights chronic sites that experience 3+ failures in a rolling 30-day window.
- **Actionable Equipment Recommendations:** Generates contextual diagnostic checklists before engineer dispatch based on historical failure modes.
- **MTTR & Availability Analytics:** Root-cause distribution and downtime trends.

### 6. Full Data Portability & Local CSV Export
- **Dynamic Master Export:** Generates standard CSV spreadsheets including core site properties **and dynamically indexed equipment specification columns**.
- **Single-Site Audit Export:** Produces a comprehensive CSV report containing Overview, Technical Specs, and historical Incident / Maintenance logs for a single site.
- **Zero-Cloud Peer Sharing:** Shares reports via standard Android Share sheet (Bluetooth, USB, SD Card, Wi-Fi Direct, Messaging) without servers.

---

## Offline-Only Guarantee

TowerDost is strictly isolated from external networks by design:

- **Zero Network Permission:** `AndroidManifest.xml` contains **no** `android.permission.INTERNET`.
- **Zero Remote Tracking:** No third-party crash reporters, telemetry SDKs, or cloud backends.
- **Private Local Storage:** All databases, preferences, and cached exports are held strictly in application-private sandbox storage.
- **Build Verification:** Tested continuously via [`ManifestAndSecurityTest.kt`](file:///home/error/prj/app/src/test/java/app/telecom/sitetracker/ManifestAndSecurityTest.kt) to guarantee zero network capability.

---

## Design System: Built for Harsh Sunlight

Designed according to strict industrial field utility principles:

| Principle | Implementation |
|---|---|
| **Sunlight Contrast** | Minimum 7:1 contrast ratio. Deep slate grounds (`#0A0E17`, `#141C2B`) with stark white text (`#F8FAFC`) and high-visibility Telecom Amber (`#F59E0B`) accents. |
| **Tactile Ergonomics** | Strict **48dp minimum touch targets** for all actionable buttons, list items, and toggles to support greasy, wet, or gloved hands. |
| **Tabular Precision** | Tabular figures and monospace typography (`Roboto Mono`) for SAP IDs, timestamps, downtime hours, and GPS coordinates. |
| **Anti-AI Slop** | Zero decorative blurry gradients, zero nested card slop, zero fake metrics. Every element is an interactive or informative field tool. |

---

## Architecture & Tech Stack

```
app.telecom.sitetracker
 ├── data
 │    ├── db            # Room Database, SiteDao, IncidentDao, FieldDao, ReasonDao
 │    ├── entity        # SiteEntity, SiteFieldEntity, IncidentEntity, SiteFts
 │    ├── export        # Exporter (Master CSV, Single Site CSV, Register CSV)
 │    ├── import        # Importer (XLSX, CSV, ColumnMapper, Fuzzy matching)
 │    └── repo          # SiteRepository, IncidentRepository, OutageRepository
 ├── ui
 │    ├── activity      # Consolidated activity feed & search
 │    ├── home          # Quick actions, urgent outages, recent sites
 │    ├── incident      # Log incident / maintenance / visit dialog & form
 │    ├── insights      # Root-cause analysis & SLA metrics
 │    ├── navigation    # NavGraph, Screen routes, TopLevel destinations
 │    ├── outages       # Day-by-day Down Register
 │    ├── search        # FTS fast search & nearby sites by GPS
 │    ├── sitedetail    # Pinned header, Specs tab, Logs tab, Overview tab
 │    ├── sitelist      # Paginated master site list (Paging 3)
 │    ├── theme         # High-contrast color tokens, Typography, Shapes
 │    └── tools         # Importer, Export actions, Database maintenance
 └── util               # Dates, Geo, BitmapUtils, SystemAppClock
```

### Technology Highlights
- **Jetpack Compose + Material 3:** Modern reactive declarative UI.
- **Room Persistence Library 2.6:** SQLite with FTS4 virtual tables for instant multi-field searching.
- **Desugared `java.time` API:** Guarantees modern date/time capabilities on Android 7.0–8.1 (minSdk 24) up to Android 15 (targetSdk 35).
- **Kotlin Coroutines & Flow:** Reactive UI state management without blocking main threads.
- **Hardware-Efficient:** Optimized for low-cost devices (₹8,000 / 2GB RAM budget).

---

## Building & Verification

### Prerequisites
- **JDK:** OpenJDK 17 or 21 (`JAVA_HOME` pointing to JDK installation)
- **Android SDK:** Platform 35, Build-Tools 35.0.0
- **Gradle:** 8.9 (via wrapper)

### Build Commands
```bash
# Set JDK environment (if not set globally)
export JAVA_HOME=/path/to/jdk-17
export PATH=$JAVA_HOME/bin:$PATH

# Run all unit and integration tests (44/44 tests)
./gradlew testDebugUnitTest

# Assemble Debug APK
./gradlew assembleDebug
```
The compiled APK will be located at:
`app/build/outputs/apk/debug/app-debug.apk`

---

## Importing Site Data

TowerDost accommodates real-world telecom tracker formats:

1. Copy or download your master Excel sheet (e.g., `INFRA TRACKER.xlsx`) or `.csv` file to the device.
2. Open **TowerDost** > navigate to **Tools** tab.
3. Tap **Import Tracker File (.xlsx / .csv)**.
4. The built-in parser dynamically detects:
   - Primary SAP ID / Site ID
   - Site Name, District, City, Circle, State
   - Latitude and Longitude
   - Up to 47+ technical specification columns (SMPS, DG, Battery, Antennas)
5. Progress is displayed in real-time, indexing thousands of towers into Room and FTS tables within seconds.

---

## Testing & Quality Assurance

TowerDost is developed using strict **Test-Driven Development (TDD)**:

- `ManifestAndSecurityTest`: Asserts absence of `android.permission.INTERNET` and verifies component isolation.
- `SiteUpdateAndExportTest`: Verifies dynamic specification generation, master site updates, and CSV export integrity.
- `Milestone2Test` & `Milestone3Test`: Verifies FTS indexing, clipped downtime calculations, and date logic.
- `RealXlsxIntegrationTest` & `XlsxColumnTest`: Verifies real telecom Excel workbook parsing and fuzzy header resolution.
- `DatesTest` & `GeoTest`: Verifies Haversine math, bearing calculations, and IST date formatting.

---

## License

```
Copyright 2026 TowerDost Contributors

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0
```
