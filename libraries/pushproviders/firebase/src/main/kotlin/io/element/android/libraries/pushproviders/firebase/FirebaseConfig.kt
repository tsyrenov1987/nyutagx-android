/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.pushproviders.firebase

object FirebaseConfig {
    /**
     * It is the push gateway for firebase.
     * Note: pusher_http_url should have path '/_matrix/push/v1/notify' -->
     * Нютаг: свой Sygnal на ядре; адрес вызывает сам сервер Нютага (loopback), телефон к нему не ходит.
     */
    const val PUSHER_HTTP_URL: String = "http://127.0.0.1:5000/_matrix/push/v1/notify"

    const val INDEX = 0
    const val NAME = "Firebase"
}
