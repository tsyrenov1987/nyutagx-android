/*
 * Нютаг — стартовый экран как на iOS (AuthenticationStartScreen): небо над степью, знак, приветствие, язык в углу.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.features.login.impl.screens.onboarding

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.features.login.impl.R
import io.element.android.libraries.designsystem.nyutag.NyutagLanguageOption
import io.element.android.libraries.designsystem.theme.components.DropdownMenu
import io.element.android.libraries.designsystem.theme.components.DropdownMenuItem
import io.element.android.libraries.designsystem.theme.components.Icon
import io.element.android.libraries.designsystem.theme.components.Surface
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.ui.strings.CommonStrings

private val nyutagGold = Color(0xFFD9A227)

/** Синий бренда сверху растворяется в фоне экрана, у правого верхнего угла — тёплый отсвет золота. */
@Composable
internal fun Modifier.nyutagStartBackground(): Modifier {
    val canvas = ElementTheme.colors.bgCanvasDefault
    val blue = ElementTheme.colors.bgActionPrimaryRest
    return drawBehind {
        drawRect(canvas)
        drawRect(Brush.verticalGradient(0f to blue.copy(alpha = 0.32f), 0.25f to blue.copy(alpha = 0.08f), 0.5f to Color.Transparent))
        drawRect(
            Brush.radialGradient(
                colors = listOf(nyutagGold.copy(alpha = 0.10f), Color.Transparent),
                center = Offset(size.width * 0.85f, size.height * 0.12f),
                radius = 260.dp.toPx(),
            )
        )
    }
}

@Composable
internal fun NyutagStartContent(
    state: OnBoardingState,
    logoResId: Int,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1.2f))
        val shape = RoundedCornerShape(percent = 22)
        Image(
            painter = painterResource(id = logoResId),
            contentDescription = null,
            modifier = Modifier
                .size(112.dp)
                .shadow(elevation = 18.dp, shape = shape, ambientColor = ElementTheme.colors.bgActionPrimaryRest, spotColor = ElementTheme.colors.bgActionPrimaryRest),
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = stringResource(id = R.string.screen_onboarding_welcome_title),
            color = ElementTheme.colors.textPrimary,
            style = ElementTheme.typography.fontHeadingXlBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(id = R.string.screen_onboarding_welcome_message, state.productionApplicationName),
            color = ElementTheme.colors.textSecondary,
            style = ElementTheme.typography.fontBodyLgRegular,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.weight(1f))
    }
}

/** Язык прямо на старте: бурятского нет среди языков телефона, а до настроек ещё не дойти. */
@Composable
internal fun NyutagLanguageChip(modifier: Modifier = Modifier) {
    var language by remember { mutableStateOf(NyutagLanguageOption.current()) }
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        Surface(
            modifier = Modifier
                .clip(CircleShape)
                .clickable(role = Role.Button) { expanded = true },
            shape = CircleShape,
            color = ElementTheme.colors.bgCanvasDefault.copy(alpha = 0.72f),
            contentColor = ElementTheme.colors.textPrimary,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(imageVector = CompoundIcons.Public(), contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    text = if (language == NyutagLanguageOption.System) stringResource(CommonStrings.nyutag_language_title) else language.getText(),
                    style = ElementTheme.typography.fontBodyMdMedium,
                )
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            NyutagLanguageOption.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.getText()) },
                    trailingIcon = if (option == language) {
                        { Icon(imageVector = CompoundIcons.Check(), contentDescription = null) }
                    } else {
                        null
                    },
                    onClick = {
                        expanded = false
                        if (option != language) {
                            language = option
                            NyutagLanguageOption.select(option)
                        }
                    },
                )
            }
        }
    }
}
