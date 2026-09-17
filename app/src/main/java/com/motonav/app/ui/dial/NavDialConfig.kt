package com.motonav.app.ui.dial

// Per-element visibility for the nav page's dial. Stage 4 adds a settings UI + persistence for
// this; for now it's a single hardcoded default so the dial reads config from day one and stage 4
// is purely additive (see MotoNav_TASK7_STAGED_PLAN.md stage 4).
enum class CompassStyle { DOT, TRIANGLE, LETTER_N, OFF }

data class NavDialConfig(
    val showCompass: Boolean = true,
    val compassStyle: CompassStyle = CompassStyle.TRIANGLE,
    val showSpeed: Boolean = true,
    val showSpeedLimit: Boolean = true,
    val showEta: Boolean = true,
    val showDistanceRemaining: Boolean = true,
    val showStreetName: Boolean = true,
)

val DefaultNavDialConfig = NavDialConfig()
