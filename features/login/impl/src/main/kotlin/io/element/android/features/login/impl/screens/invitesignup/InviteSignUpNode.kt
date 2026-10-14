/*
 * Нютаг — регистрация по коду приглашения.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.features.login.impl.screens.invitesignup

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.bumble.appyx.core.modality.BuildContext
import com.bumble.appyx.core.node.Node
import com.bumble.appyx.core.plugin.Plugin
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedInject
import io.element.android.annotations.ContributesNode
import io.element.android.libraries.architecture.NodeInputs

@ContributesNode(AppScope::class)
@AssistedInject
class InviteSignUpNode(
    @Assisted buildContext: BuildContext,
    @Assisted plugins: List<Plugin>,
    private val presenter: InviteSignUpPresenter,
) : Node(buildContext, plugins = plugins) {
    /** Код из ссылки-приглашения; пусто — человек введёт его сам. */
    data class Inputs(val code: String) : NodeInputs

    init {
        presenter.initialCode = plugins.filterIsInstance<Inputs>().firstOrNull()?.code.orEmpty()
    }

    @Composable
    override fun View(modifier: Modifier) {
        val state = presenter.present()
        InviteSignUpView(
            state = state,
            modifier = modifier,
            onBackClick = ::navigateUp,
        )
    }
}
