/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.deeplink.impl.usecase

import android.app.Activity
import android.widget.Toast
import dev.zacsweers.metro.ContributesBinding
import io.element.android.libraries.androidutils.system.startSharePlainTextIntent
import io.element.android.libraries.core.meta.BuildMeta
import io.element.android.libraries.deeplink.api.usecase.InviteFriendsUseCase
import io.element.android.libraries.deeplink.impl.R
import io.element.android.libraries.di.SessionScope
import io.element.android.libraries.di.annotations.SessionCoroutineScope
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.matrix.api.nyutag.NyutagInviteException
import io.element.android.libraries.matrix.api.nyutag.NyutagInvites
import io.element.android.libraries.ui.strings.CommonStrings
import io.element.android.services.toolbox.api.strings.StringProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.lang.ref.WeakReference
import io.element.android.libraries.androidutils.R as AndroidUtilsR

/**
 * Нютаг: «Пригласить» выпускает одноразовый код приглашения на сервисе ядра (7 дней, один раз)
 * и открывает системное «Поделиться» с текстом-приглашением. Ссылки matrix.to в Нютаге нет.
 */
@ContributesBinding(SessionScope::class)
class DefaultInviteFriendsUseCase(
    private val stringProvider: StringProvider,
    private val matrixClient: MatrixClient,
    private val buildMeta: BuildMeta,
    private val nyutagInvites: NyutagInvites,
    @SessionCoroutineScope private val sessionCoroutineScope: CoroutineScope,
) : InviteFriendsUseCase {
    override fun execute(activity: Activity) {
        val activityRef = WeakReference(activity)
        Toast.makeText(activity, stringProvider.getString(R.string.nyutag_invite_creating), Toast.LENGTH_SHORT).show()
        sessionCoroutineScope.launch {
            val result = nyutagInvites.createInvite(matrixClient.sessionId)
            withContext(Dispatchers.Main) {
                val host = activityRef.get()?.takeUnless { it.isFinishing || it.isDestroyed } ?: return@withContext
                result.fold(
                    onSuccess = { invite ->
                        val appName = buildMeta.applicationName
                        host.startSharePlainTextIntent(
                            activityResultLauncher = null,
                            chooserTitle = stringProvider.getString(CommonStrings.action_invite_friends),
                            text = stringProvider.getString(R.string.nyutag_invite_share_text, appName, invite.code),
                            extraTitle = stringProvider.getString(CommonStrings.invite_friends_rich_title, appName),
                            noActivityFoundMessage = stringProvider.getString(AndroidUtilsR.string.error_no_compatible_app_found)
                        )
                    },
                    onFailure = { error ->
                        val message = when ((error as? NyutagInviteException)?.errcode) {
                            NyutagInviteException.INVITE_LIMIT -> R.string.nyutag_invite_limit
                            NyutagInviteException.NETWORK -> R.string.nyutag_invite_network
                            else -> R.string.nyutag_invite_failed
                        }
                        Toast.makeText(host, stringProvider.getString(message), Toast.LENGTH_LONG).show()
                    }
                )
            }
        }
    }
}
