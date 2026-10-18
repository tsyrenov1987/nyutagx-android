/*
 * Нютаг — полоса идущего звонка внутри приложения, как в Telegram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.features.call.utils

import io.element.android.features.call.api.NyutagOngoingCall
import io.element.android.features.call.impl.utils.NyutagOngoingCallPublisher

class FakeNyutagOngoingCallPublisher : NyutagOngoingCallPublisher {
    var lastCall: NyutagOngoingCall? = null
        private set

    override fun publish(call: NyutagOngoingCall?) {
        lastCall = call
    }
}
