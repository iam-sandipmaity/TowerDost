package app.telecom.sitetracker.ui.theme

import androidx.compose.ui.graphics.Color

// =========================================================================
// TowerDost Industrial Field System (DESIGN.md Tokens)
// High-contrast, high-sunlight utility palette designed for outdoor telecom engineering.
// =========================================================================

// Primary Brand Palette: Telecom Amber & RF Cyan
val TelecomAmber = Color(0xFFF59E0B)       // Industrial amber (high sunlight visibility)
val TelecomAmberDark = Color(0xFFD97706)   // Deep amber for light mode
val TelemetryCyan = Color(0xFF0EA5E9)      // RF telemetry cyan
val CobaltAccent = Color(0xFF2563EB)

// Backward compatibility brand aliases
val BrandPrimary = TelecomAmber
val BrandPrimaryLight = TelecomAmber
val BrandPrimaryContainer = Color(0xFF1E283A)
val OnBrandPrimaryContainer = Color(0xFFF8FAFC)
val TealPrimary = BrandPrimary
val TealDark = TelecomAmberDark
val TealLight = TelecomAmber
val TealContainer = BrandPrimaryContainer
val OnTealContainer = OnBrandPrimaryContainer

// Industrial Dark Palette (Primary Field Engineering Mode)
val GroundDark = Color(0xFF0A0E17)          // Deep space slate
val SurfaceDark = Color(0xFF141C2B)         // High-density industrial slate
val SurfaceElevatedDark = Color(0xFF1C273A) // Elevated inset / input slate
val BorderDark = Color(0xFF2D3D56)          // 1dp crisp structural border
val TextPrimaryDark = Color(0xFFF8FAFC)     // 100% stark white
val TextSecondaryDark = Color(0xFF94A3B8)   // Legible mute slate (>=65% opacity)

// High Noon Light Palette (Stark Outdoor Sunlight Mode)
val GroundLight = Color(0xFFF4F6F9)         // Crisp architectural paper
val SurfaceLight = Color(0xFFFFFFFF)        // Pure white card
val SurfaceElevatedLight = Color(0xFFEAEFF5)// Inset input surface
val BorderLight = Color(0xFFCBD5E1)         // 1dp slate structural divider
val TextPrimaryLight = Color(0xFF0F172A)    // High-contrast deep slate (100%)
val TextSecondaryLight = Color(0xFF475569)  // Secondary slate

// Backward compatibility surface aliases
val BackgroundDark = GroundDark
val BackgroundLight = GroundLight
val CardBorderDark = BorderDark
val CardBorderLight = BorderLight

// Semantic Status Colors (High-Luminance Outdoor Standards)
val StatusDownRed = Color(0xFFEF4444)       // Outage Down emergency red
val StatusRestoredGreen = Color(0xFF10B981) // Restored / SLA operational green
val StatusMaintAmber = Color(0xFFF59E0B)    // Scheduled maintenance / warning
val StatusVisitBlue = Color(0xFF3B82F6)     // Routine site audit / visit
val StatusNoteSlate = Color(0xFF64748B)     // Log entry / note

// Semantic Tag Colors (Used across activity cards and badges)
val TagIssueColor = StatusDownRed
val TagIssueBg = Color(0x26EF4444)          // 15% tinted dark pill

val TagMaintColor = StatusMaintAmber
val TagMaintBg = Color(0x26F59E0B)

val TagVisitColor = StatusVisitBlue
val TagVisitBg = Color(0x263B82F6)

val TagNoteColor = StatusNoteSlate
val TagNoteBg = Color(0x2664748B)

// Severity aliases
val SeverityCritical = StatusDownRed
val SeverityMajor = StatusMaintAmber
val SeverityMinor = Color(0xFFEAB308)
val SeverityInfo = StatusNoteSlate
