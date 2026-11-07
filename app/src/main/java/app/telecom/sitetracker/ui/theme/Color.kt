package app.telecom.sitetracker.ui.theme

import androidx.compose.ui.graphics.Color

// Primary Brand Palette: Telecom Cobalt Blue
val BrandPrimary = Color(0xFF1D4ED8)       // Cobalt 700 - high sunlight contrast
val BrandPrimaryLight = Color(0xFF3B82F6)  // Cobalt 500 for dark mode
val BrandPrimaryContainer = Color(0xFFDBEAFE) // Soft ice blue
val OnBrandPrimaryContainer = Color(0xFF1E3A8A)

// Surfaces & Backgrounds
val SurfaceLight = Color(0xFFFFFFFF)
val BackgroundLight = Color(0xFFF8FAFC)
val CardBorderLight = Color(0xFFE2E8F0)
val TextPrimaryLight = Color(0xFF0F172A)
val TextSecondaryLight = Color(0xFF475569)

val SurfaceDark = Color(0xFF131B2A)
val BackgroundDark = Color(0xFF090D16)
val CardBorderDark = Color(0xFF1E293B)
val TextPrimaryDark = Color(0xFFF8FAFC)
val TextSecondaryDark = Color(0xFF94A3B8)

// Semantic Tag Colors
val TagIssueColor = Color(0xFFDC2626)
val TagIssueBg = Color(0xFFFEE2E2)

val TagMaintColor = Color(0xFFD97706)
val TagMaintBg = Color(0xFFFEF3C7)

val TagVisitColor = Color(0xFF2563EB)
val TagVisitBg = Color(0xFFDBEAFE)

val TagNoteColor = Color(0xFF475569)
val TagNoteBg = Color(0xFFF1F5F9)

// Backward compatibility aliases
val TealPrimary = BrandPrimary
val TealDark = BrandPrimary
val TealLight = BrandPrimaryLight
val TealContainer = BrandPrimaryContainer
val OnTealContainer = OnBrandPrimaryContainer
val SeverityCritical = TagIssueColor
val SeverityMajor = TagMaintColor
val SeverityMinor = Color(0xFFEAB308)
val SeverityInfo = Color(0xFF64748B)
