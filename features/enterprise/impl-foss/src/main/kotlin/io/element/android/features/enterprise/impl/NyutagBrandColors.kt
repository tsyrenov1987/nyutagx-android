/*
 * Нютаг (форк Element X Android) — фирменные цвета.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.features.enterprise.impl

import androidx.compose.ui.graphics.Color
import io.element.android.compound.colors.SemanticColorsLightDark
import io.element.android.compound.tokens.generated.SemanticColors
import io.element.android.compound.tokens.generated.compoundColorsDark
import io.element.android.compound.tokens.generated.compoundColorsLight

/**
 * Палитра Нютага, как на iOS (`NyutagBrand.swift`): синий неба (#1C6FB3) вместо зелёного акцента и чёрных кнопок Element.
 * Текст и иконки на синих кнопках — белые в обеих темах.
 */
internal val nyutagSemanticColors = SemanticColorsLightDark(
    light = compoundColorsLight.withNyutagAccent(
        blue = Color(0xFF1C6FB3),
        pressed = Color(0xFF155A92),
        subtle = Color(0xFFE1EEFA),
        selected = Color(0x241C6FB3),
    ),
    dark = compoundColorsDark.withNyutagAccent(
        blue = Color(0xFF4C9BE6),
        pressed = Color(0xFF3B86CF),
        subtle = Color(0xFF13314D),
        selected = Color(0x3D4C9BE6),
    ),
)

private fun SemanticColors.withNyutagAccent(blue: Color, pressed: Color, subtle: Color, selected: Color) = copy(
    bgActionPrimaryRest = blue,
    bgActionPrimaryHovered = pressed,
    bgActionPrimaryPressed = pressed,
    textOnSolidPrimary = Color.White,
    iconOnSolidPrimary = Color.White,
    bgAccentRest = blue,
    bgAccentHovered = pressed,
    bgAccentPressed = pressed,
    bgAccentSelected = selected,
    bgAccentSubtle = subtle,
    bgBadgeAccent = subtle,
    textBadgeAccent = blue,
    iconAccentPrimary = blue,
    iconAccentTertiary = blue,
    textActionAccent = blue,
    borderAccentPrimary = blue,
    borderAccentSubtle = subtle,
    gradientActionStop1 = blue,
    gradientActionStop2 = blue,
    gradientActionStop3 = pressed,
    gradientActionStop4 = pressed,
)
