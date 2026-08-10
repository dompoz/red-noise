package com.pinktakhyper.deeprednoise.ui

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// Seed: #D4003A — vivid crimson-red (cooler/bluer than pure red, redder than cerise)
val Crimson900   = Color(0xFF7A0020)
val Crimson800   = Color(0xFF9E0028)
val Crimson700   = Color(0xFFC20030)  // on-primary in dark
val Crimson600   = Color(0xFFD4003A)  // primary seed
val Crimson500   = Color(0xFFE0003C)  // primary in light
val Crimson400   = Color(0xFFFF4D71)
val Crimson300   = Color(0xFFFF849E)
val Crimson200   = Color(0xFFFFB3C2)
val Crimson100   = Color(0xFFFFDAE0)
val Crimson50    = Color(0xFFFFF0F2)

val DeepNight    = Color(0xFF1A000A)
val SurfaceDark  = Color(0xFF27000F)
val SurfaceVariantDark = Color(0xFF3D1020)
val OutlineDark  = Color(0xFF8C4057)

val LightOnSurface = Color(0xFF3D0014)
val LightSurface   = Color(0xFFFFF0F2)
val LightSurfaceVariant = Color(0xFFFFDAE0)
val LightOutline   = Color(0xFFAA3355)

val RedNoiseDarkColorScheme = darkColorScheme(
    primary            = Crimson400,
    onPrimary          = Crimson900,
    primaryContainer   = Crimson800,
    onPrimaryContainer = Crimson100,
    secondary          = Crimson300,
    onSecondary        = Crimson900,
    secondaryContainer = Color(0xFF5C0020),
    onSecondaryContainer = Crimson200,
    tertiary           = Color(0xFFFFB3A0),
    onTertiary         = Color(0xFF5C1800),
    tertiaryContainer  = Color(0xFF7A2800),
    onTertiaryContainer = Color(0xFFFFDBCF),
    error              = Color(0xFFFFB4AB),
    onError            = Color(0xFF690005),
    errorContainer     = Color(0xFF93000A),
    onErrorContainer   = Color(0xFFFFDAD6),
    background         = DeepNight,
    onBackground       = Crimson100,
    surface            = DeepNight,
    onSurface          = Crimson100,
    surfaceVariant     = SurfaceVariantDark,
    onSurfaceVariant   = Crimson200,
    outline            = OutlineDark,
    outlineVariant     = Color(0xFF5A2030),
    scrim              = Color(0xFF000000),
    inverseSurface     = Crimson100,
    inverseOnSurface   = Crimson900,
    inversePrimary     = Crimson600,
)

val RedNoiseLightColorScheme = lightColorScheme(
    primary            = Crimson600,
    onPrimary          = Color.White,
    primaryContainer   = Crimson100,
    onPrimaryContainer = Crimson900,
    secondary          = Color(0xFFA3294A),
    onSecondary        = Color.White,
    secondaryContainer = LightSurfaceVariant,
    onSecondaryContainer = Color(0xFF3D0014),
    tertiary           = Color(0xFF8C4026),
    onTertiary         = Color.White,
    tertiaryContainer  = Color(0xFFFFDBCF),
    onTertiaryContainer = Color(0xFF340F00),
    error              = Color(0xFFBA1A1A),
    onError            = Color.White,
    errorContainer     = Color(0xFFFFDAD6),
    onErrorContainer   = Color(0xFF410002),
    background         = LightSurface,
    onBackground       = LightOnSurface,
    surface            = LightSurface,
    onSurface          = LightOnSurface,
    surfaceVariant     = LightSurfaceVariant,
    onSurfaceVariant   = Color(0xFF5C2033),
    outline            = LightOutline,
    outlineVariant     = Crimson200,
    scrim              = Color(0xFF000000),
    inverseSurface     = Color(0xFF3D0014),
    inverseOnSurface   = Crimson50,
    inversePrimary     = Crimson300,
)
