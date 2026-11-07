# Design — TowerDost Visual System & Tokens

name: TowerDost Industrial Field System
description: High-contrast, high-sunlight utility palette designed for harsh outdoor telecom environments. Dense, crisp, and tactile.

## Principles

1. **Sunlight Legibility:** Minimum contrast ratio 7:1 for core text. Dark mode uses slate/charcoal grounds (`#0F141C`, `#182232`) with crisp white/amber accents. Light mode uses stark high-contrast paper tones (`#F8F9FA`, `#0F172A`).
2. **Tabular Precision:** All downtime durations, dates, percentages, and site SAP IDs use monospace/tabular figures for instant column scanning.
3. **Tactile Feedback:** 48dp minimum touch targets for all primary buttons, toggle switches, and list items. 
4. **Information Density:** High information per viewport. No unnecessary nested cards or floating pastel blobs. Clear 1dp hairline dividers (`#334155`).
5. **No AI Slop:** No decorative emojis as UI icons. Use standard Android Material symbols (`Icons.Default.*` / `Icons.Outlined.*`).

## Color Tokens

### Dark Theme (Primary Field Mode)
- **Background (Ground):** `#0A0E17` (Deep space slate)
- **Surface (Card / Panel):** `#141C2B` (High-density industrial slate)
- **Surface Variant (Input / Inset):** `#1C273A` (Elevated slate)
- **Surface Border:** `#2D3D56` (1dp crisp structural border)
- **Primary Accent (Telecom Amber):** `#F59E0B` (Warning/action amber, high visibility outdoors)
- **Secondary Accent (RF Cyan):** `#0EA5E9` (Data telemetry, status identifiers)
- **Critical / Outage Down:** `#EF4444` (High-luminance emergency red)
- **Restored / Up SLA:** `#10B981` (Industrial green)
- **Text Primary:** `#F8FAFC` (Pure stark white, 100% opacity)
- **Text Secondary:** `#94A3B8` (Legible mute slate, never below 65% opacity)
- **Text Inverted:** `#0F172A` (For badges with light backgrounds)

### Light Theme (High Noon Mode)
- **Background:** `#F8FAFC`
- **Surface:** `#FFFFFF`
- **Surface Variant:** `#F1F5F9`
- **Surface Border:** `#CBD5E1`
- **Primary Accent:** `#D97706`
- **Critical:** `#DC2626`
- **Restored:** `#059669`
- **Text Primary:** `#0F172A`
- **Text Secondary:** `#475569`

## Typography

- **Display & Headings:** Sans-serif bold, tight tracking.
- **Data & Identifiers (SAP ID, Lat/Long, Down Time):** Roboto Mono / Tabular numbers.
- **Labels & Tags:** Sentence-case or uppercase with clear letter spacing, never tracked lowercase pseudo-labels.

## Elevation & Radii

- **Card Radius:** 10dp (Restrained, industrial corner radius)
- **Button Radius:** 8dp
- **Elevation:** Flat with 1dp crisp hairline border (`border(1.dp, MaterialTheme.colorScheme.outlineVariant)`) instead of heavy blurry drop shadows that wash out in sunlight.
