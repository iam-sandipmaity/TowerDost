# TowerDost — Agent Guidelines & Skills Integration

This repository implements **TowerDost (Telecom Site & Incident Tracker)**, an offline-only native Android field engineering application.

## Loaded Skills & Disciplines

The following agent skills are active in `.agents/skills/`:
1. **superpowers (`.agents/skills/superpowers/`)**:
   - Strict Test-Driven Development (TDD) — write unit tests for business logic before code changes.
   - Systematic Debugging — reproduce, isolate, fix, and verify with tests.
   - Clear, step-by-step milestone execution without skipping verification.
2. **hallmark (`.agents/skills/hallmark/`)**:
   - Zero AI-slop design floor: no pastel purple gradients, no nested cards, no fake metrics, no tracked capitals on tiny labels.
   - High sunlight readability, proper density, and purposeful typography.
3. **impeccable (`.agents/skills/impeccable/`)**:
   - Adhere to `PRODUCT.md` and `DESIGN.md`.
   - "Operate" mode for enterprise field tool UX: high contrast, 48dp touch targets, instant feedback, real tabular numbers.
4. **context7 (`.agents/skills/context7/`)**:
   - Documentation & API context lookup for Android Jetpack Compose, Room, Kotlin Coroutines/Flow, and Material 3.

## Architectural Commandments

- **Offline-Only:** Never add `INTERNET` permission to `AndroidManifest.xml` or build scripts.
- **Android Compatibility:** Must run on `minSdk 24` up to `targetSdk 35`. All `java.time` operations must use desugaring.
- **Zero Mock Data in Production:** The register and site lists must reflect actual local database state or imported files (`INFRA TRACKER.xlsx`). No hardcoded mock incidents in real user flows.
- **Verification Requirement:** Every milestone must be verified with unit tests (`[U]`), instrumented tests (`[I]`), or manual verification steps (`[M]`).
