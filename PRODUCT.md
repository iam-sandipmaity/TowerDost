# Product — TowerDost (Telecom Site & Incident Tracker)

<!-- impeccable:product-schema 1 -->

## Platform

Android Native (Kotlin + Jetpack Compose + Material 3, minSdk 24, targetSdk 35). Single-module, offline-only, zero network permissions.

## Users

Telecom field engineers, technician leads, cluster managers, and operations center staff operating across India (rural, suburban, and urban towers). They work outdoors in intense tropical sunlight, with greasy or gloved hands, on budget Android 8+ devices (2GB RAM), frequently in zero-signal deadzones or permanent airplane mode.

## Product Purpose

TowerDost is an offline field utility designed to track cell tower infrastructure, log outages and restorations in real-time, maintain an editable day-by-day down register, diagnose repeat site failures, and export audit-ready CSV/JSON reports directly on the device with zero cloud dependency.

Success is measured by:
1. Instant site retrieval (<50ms FTS search across 10,000+ sites by SAP ID, name, or specs).
2. Frictionless outage logging with accurate past/current timestamps.
3. Clean, dummy-free day down register with daily uptime SLA insights.
4. Lossless local data export and peer-to-peer backup without servers.

## Operating Context

- **100% Airplane Mode:** Zero `INTERNET` permission in Manifest or build scripts. No third-party analytics, crash loggers, or remote APIs.
- **Real-Time Local State:** Instant SQLite (Room) transaction persistence. Changes in incidents, sites, and register updates reflect immediately across the UI.
- **Hardware Budget:** Designed to run smoothly without frame drops on a ₹8,000 Android device with 2 GB RAM. Memory footprint strictly capped. Core library desugaring guarantees full `java.time` support on Android 7.0–8.1 (API 24–27).

## Core Capabilities

1. **Flexible Master Site Import:** Import master sheets from Excel (`.xlsx`), CSV, or JSON without rigid column structures (fuzzy matching for SAP ID, Site Name, Lat/Lng, District, Circle, and arbitrary equipment specs).
2. **Real-Time Outage Logging:** 1-tap or one-form incident logging with customizable Outage Start Time and Restoration Time (supporting past incidents logged after travel).
3. **Day-by-Day Down Register:** Browse any calendar date to see sites down on that day, total daily clipped outage hours, and editable telecom reason codes.
4. **Telecom Root-Cause Catalogue:** Standardized classification (EB Failure, Battery Low, DG No Fuel, OFC Cut, Card Fault, Severe Weather, Tenant Outage).
5. **Availability & MTTR Insights:** Root-cause Pareto chart, monthly calendar downtime heatmap, and site availability % calculation.
6. **Data Portability:** 1-tap CSV export of active register and site inventory; encrypted offline JSON/ZIP database backup and restore.

## Brand Commitments

- **High-Sunlight Utility:** Deep charcoal/slate surfaces, stark high-contrast typography, crisp borders, and zero low-contrast blurry glassmorphism.
- **Tactile Ergonomics:** Minimum 48dp touch targets, clear tabular figures, single-screen forms without hidden menus.
- **Anti-AI Slop:** Zero decorative dummy cards, zero fake metrics, zero unclickable buttons. Every pixel is an interactive or informative field tool.
