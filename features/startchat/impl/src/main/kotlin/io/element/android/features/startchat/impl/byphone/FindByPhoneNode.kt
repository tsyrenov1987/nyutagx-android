/*
 * Нютаг — экран «По номеру телефона».
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.features.startchat.impl.byphone

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.bumble.appyx.core.modality.BuildContext
import com.bumble.appyx.core.node.Node
import com.bumble.appyx.core.plugin.Plugin
import com.bumble.appyx.core.plugin.plugins
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedInject
import io.element.android.annotations.ContributesNode
import io.element.android.features.startchat.StartChatNavigator
import io.element.android.libraries.di.SessionScope
import io.element.android.libraries.matrix.api.core.toRoomIdOrAlias

@ContributesNode(SessionScope::class)
@AssistedInject
class FindByPhoneNode(
    @Assisted buildContext: BuildContext,
    @Assisted plugins: List<Plugin>,
    private val presenter: FindByPhonePresenter,
) : Node(buildContext, plugins = plugins) {
    private val navigator = plugins<StartChatNavigator>().first()

    @Composable
    override fun View(modifier: Modifier) {
        val state = presenter.present()
        FindByPhoneView(
            state = state,
            onBackClick = ::navigateUp,
            onOpenDM = { navigator.onRoomCreated(roomIdOrAlias = it.toRoomIdOrAlias(), serverNames = emptyList()) },
            modifier = modifier,
        )
    }
}
