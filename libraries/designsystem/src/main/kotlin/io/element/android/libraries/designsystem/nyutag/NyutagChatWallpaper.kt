/*
 * Нютаг (форк Element X Android) — фон ленты чата: мягкий цвет с едва заметным узором из значков, как в привычных
 * мессенджерах. Вариант выбирается в настройках и хранится на устройстве. Узор и раскладка — те же, что на iOS.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.libraries.designsystem.nyutag

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.libraries.designsystem.theme.components.Icon
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.ui.strings.CommonStrings

enum class NyutagWallpaperStyle(
    @StringRes val title: Int,
    private val light: Long,
    private val dark: Long,
    private val lightInk: Long,
    private val darkInk: Long,
) {
    Sky(CommonStrings.nyutag_wallpaper_sky, 0xFFE6EEF6, 0xFF0E141B, 0x171C6FB3, 0x1A4C9BE6),
    Steppe(CommonStrings.nyutag_wallpaper_steppe, 0xFFE7F0E2, 0xFF0F1610, 0x1A3F7A35, 0x1A6DBE5F),
    Sand(CommonStrings.nyutag_wallpaper_sand, 0xFFF3EADB, 0xFF1A150F, 0x1A9A6B25, 0x1AD9A227),
    Dusk(CommonStrings.nyutag_wallpaper_dusk, 0xFFECE7F5, 0xFF15111D, 0x1A6A4AA5, 0x1C9C7BE0),
    Plain(CommonStrings.nyutag_wallpaper_plain, 0, 0, 0, 0);

    /** null — обычный фон темы, без узора. */
    fun background(isLight: Boolean): Color? = if (this == Plain) null else Color(if (isLight) light else dark)

    fun ink(isLight: Boolean): Color? = if (this == Plain) null else Color(if (isLight) lightInk else darkInk)
}

/** Выбранный фон: один на устройство, меняется сразу во всех открытых чатах. */
object NyutagWallpaperSettings {
    private const val PREFS = "nyutag_ui"
    private const val KEY = "chat_wallpaper"
    private var style by mutableStateOf<NyutagWallpaperStyle?>(null)

    @Composable
    fun current(): NyutagWallpaperStyle {
        val context = LocalContext.current
        return style ?: load(context).also { style = it }
    }

    fun select(context: Context, newStyle: NyutagWallpaperStyle) {
        style = newStyle
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, newStyle.name).apply()
    }

    private fun load(context: Context): NyutagWallpaperStyle {
        val name = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
        return NyutagWallpaperStyle.entries.firstOrNull { it.name == name } ?: NyutagWallpaperStyle.Sky
    }
}

/** Значок в плитке 240 dp: центр, поворот и размер — подобраны вручную, как на iOS. */
private class Glyph(val x: Float, val y: Float, val angle: Float, val size: Float)

private const val TILE = 240f

private val LAYOUT = listOf(
    Glyph(24f, 22f, -12f, 22f),
    Glyph(92f, 40f, 10f, 20f),
    Glyph(170f, 18f, -6f, 22f),
    Glyph(214f, 70f, 16f, 18f),
    Glyph(40f, 92f, 8f, 20f),
    Glyph(128f, 104f, -14f, 24f),
    Glyph(196f, 136f, 6f, 20f),
    Glyph(22f, 160f, -8f, 22f),
    Glyph(84f, 172f, 14f, 18f),
    Glyph(150f, 186f, -10f, 22f),
    Glyph(220f, 214f, 12f, 18f),
    Glyph(60f, 226f, -4f, 20f),
    Glyph(110f, 60f, 20f, 16f),
    Glyph(16f, 116f, -18f, 16f),
    Glyph(176f, 84f, -20f, 16f),
    Glyph(130f, 230f, 8f, 16f),
)

/** Фон ленты чата по выбранному в настройках варианту. */
@Composable
fun NyutagChatWallpaper(modifier: Modifier = Modifier) {
    NyutagWallpaper(style = NyutagWallpaperSettings.current(), modifier = modifier)
}

@Composable
fun NyutagWallpaper(style: NyutagWallpaperStyle, modifier: Modifier = Modifier, scale: Float = 1f) {
    val isLight = ElementTheme.isLightTheme
    val background = style.background(isLight) ?: return
    val ink = style.ink(isLight) ?: return
    val painters = listOf(
        CompoundIcons.Chat(),
        CompoundIcons.Favourite(),
        CompoundIcons.DarkMode(),
        CompoundIcons.Cloud(),
        CompoundIcons.Audio(),
        CompoundIcons.TakePhoto(),
        CompoundIcons.Tree(),
        CompoundIcons.Home(),
        CompoundIcons.Send(),
        CompoundIcons.Reaction(),
        CompoundIcons.Sticker(),
        CompoundIcons.VoiceCall(),
        CompoundIcons.LocationPin(),
        CompoundIcons.Image(),
        CompoundIcons.Space(),
        CompoundIcons.Mention(),
    ).map { rememberVectorPainter(it) }
    val filter = remember(ink) { ColorFilter.tint(ink) }
    Canvas(modifier = modifier.background(background)) {
        val tile = TILE.dp.toPx() * scale
        val unit = tile / TILE
        val columns = (size.width / tile).toInt()
        val rows = (size.height / tile).toInt()
        for (row in 0..rows) {
            for (column in 0..columns) {
                LAYOUT.forEachIndexed { index, glyph ->
                    val side = glyph.size * unit
                    translate(left = column * tile + glyph.x * unit - side / 2, top = row * tile + glyph.y * unit - side / 2) {
                        rotate(degrees = glyph.angle, pivot = center.copy(x = side / 2, y = side / 2)) {
                            with(painters[index % painters.size]) {
                                draw(size = Size(side, side), colorFilter = filter)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Плитки-превью для настроек: тап — выбрать фон. */
@Composable
fun NyutagWallpaperPicker(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val selected = NyutagWallpaperSettings.current()
    Column(modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            text = stringResource(CommonStrings.nyutag_wallpaper_title),
            style = ElementTheme.typography.fontBodyLgRegular,
            color = ElementTheme.colors.textPrimary,
        )
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            NyutagWallpaperStyle.entries.forEach { style ->
                val isSelected = style == selected
                Column(
                    modifier = Modifier
                        .semantics { this.selected = isSelected }
                        .clickable(role = Role.RadioButton) { NyutagWallpaperSettings.select(context, style) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    val shape = RoundedCornerShape(12.dp)
                    Box(
                        modifier = Modifier
                            .size(width = 64.dp, height = 96.dp)
                            .clip(shape)
                            .background(ElementTheme.colors.bgCanvasDefault)
                            .border(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) ElementTheme.colors.iconAccentPrimary else ElementTheme.colors.borderInteractiveSecondary,
                                shape = shape,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        NyutagWallpaper(style = style, modifier = Modifier.fillMaxSize(), scale = 0.5f)
                        if (isSelected) {
                            Icon(
                                imageVector = CompoundIcons.CheckCircleSolid(),
                                contentDescription = null,
                                tint = ElementTheme.colors.iconAccentPrimary,
                            )
                        }
                    }
                    Text(
                        modifier = Modifier.padding(top = 4.dp),
                        text = stringResource(style.title),
                        style = ElementTheme.typography.fontBodySmRegular,
                        color = if (isSelected) ElementTheme.colors.textPrimary else ElementTheme.colors.textSecondary,
                    )
                }
            }
        }
    }
}
