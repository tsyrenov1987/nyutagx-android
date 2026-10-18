/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2024, 2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.push.impl.test

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import io.element.android.libraries.matrix.api.core.EventId
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.nyutag.NyutagPushLoopback
import io.element.android.libraries.push.api.gateway.PushGatewayFailure
import io.element.android.libraries.push.impl.BuildConfig
import io.element.android.libraries.push.impl.pushgateway.PushGatewayNotifyRequest
import io.element.android.libraries.pushproviders.api.Config
import java.net.URI
import java.net.URISyntaxException

interface TestPush {
    suspend fun execute(config: Config)
}

@ContributesBinding(AppScope::class)
class DefaultTestPush(
    private val pushGatewayNotifyRequest: PushGatewayNotifyRequest,
    private val nyutagPushLoopback: NyutagPushLoopback,
) : TestPush {
    override suspend fun execute(config: Config) {
        // Нютаг: шлюз пушей на 127.0.0.1 ядра — с телефона его не видно, тестовый пуш отправляет само ядро.
        if (config.url.isLoopbackUrl()) {
            val rejected = nyutagPushLoopback.notify(BuildConfig.PUSHER_APP_ID, config.pushKey).getOrElse {
                throw if (it is NyutagPushLoopback.RateLimitedException) PushGatewayFailure.RateLimited() else it
            }
            if (rejected) throw PushGatewayFailure.PusherRejected()
            return
        }
        pushGatewayNotifyRequest.execute(
            PushGatewayNotifyRequest.Params(
                url = config.url,
                appId = BuildConfig.PUSHER_APP_ID,
                pushKey = config.pushKey,
                eventId = TEST_EVENT_ID,
                roomId = TEST_ROOM_ID,
            )
        )
    }

    private fun String.isLoopbackUrl(): Boolean {
        val host = try {
            URI(this).host
        } catch (_: URISyntaxException) {
            null
        }
        return host == "127.0.0.1" || host == "localhost"
    }

    companion object {
        val TEST_EVENT_ID = EventId("\$THIS_IS_A_FAKE_EVENT_ID")
        val TEST_ROOM_ID = RoomId("!room:domain")
    }
}
