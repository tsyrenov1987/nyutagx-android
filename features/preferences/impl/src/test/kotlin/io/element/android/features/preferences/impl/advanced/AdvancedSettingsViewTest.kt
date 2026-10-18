/*
 * Нютаг (форк Element X) — экран «Дополнительно».
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

@file:OptIn(ExperimentalTestApi::class)

package io.element.android.features.preferences.impl.advanced

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.AndroidComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runAndroidComposeUiTest
import io.element.android.libraries.ui.strings.CommonStrings
import io.element.android.tests.testutils.EnsureNeverCalled
import io.element.android.tests.testutils.ensureCalledOnce
import io.element.android.tests.testutils.pressBack
import io.element.android.tests.testutils.robolectric.RobolectricTest
import org.junit.Test

class AdvancedSettingsViewTest : RobolectricTest() {
    @Test
    fun `click on back invokes the expected callback`() = runAndroidComposeUiTest {
        ensureCalledOnce { callback ->
            setView(onBackClick = callback)
            pressBack()
        }
    }

    @Test
    fun `click on sign out invokes the expected callback`() = runAndroidComposeUiTest {
        ensureCalledOnce { callback ->
            setView(onSignOutClick = callback)
            val text = activity!!.getString(CommonStrings.action_signout)
            onNode(hasText(text) and hasClickAction()).performClick()
        }
    }

    @Test
    fun `click on delete account invokes the expected callback`() = runAndroidComposeUiTest {
        ensureCalledOnce { callback ->
            setView(canDeactivateAccount = true, onDeactivateClick = callback)
            val text = activity!!.getString(CommonStrings.action_delete_account)
            onNode(hasText(text) and hasClickAction()).performClick()
        }
    }

    @Test
    fun `when canDeactivateAccount is false, delete account is not shown`() = runAndroidComposeUiTest {
        setView(canDeactivateAccount = false)
        onNodeWithText(activity!!.getString(CommonStrings.action_delete_account)).assertDoesNotExist()
    }
}

private fun AndroidComposeUiTest<ComponentActivity>.setView(
    canDeactivateAccount: Boolean = true,
    onBackClick: () -> Unit = EnsureNeverCalled(),
    onSignOutClick: () -> Unit = EnsureNeverCalled(),
    onDeactivateClick: () -> Unit = EnsureNeverCalled(),
) {
    setContent {
        AdvancedSettingsView(
            canDeactivateAccount = canDeactivateAccount,
            onBackClick = onBackClick,
            onSignOutClick = onSignOutClick,
            onDeactivateClick = onDeactivateClick,
        )
    }
}
