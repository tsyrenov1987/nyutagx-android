/*
 * Нютаг — имя сети для ядра «Кильватера».
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.libraries.matrix.impl.nyutag

import java.security.MessageDigest

/**
 * Имя сети, под которым farvater-core ведёт память путей (mobile.go: `wifi`, `wifi:<хеш шлюзов>`, `cell`, `wired`).
 * Wi-Fi различаются по шлюзам, и наружу уходит хеш, а не адреса: у домашней и рабочей сети — своя память, и
 * ядро не начинает разведку заново, вернувшись в знакомую. Та же схема, что в Farvater (NetworkKey.kt).
 */
internal object NyutagNetworkKey {
    /** Wi-Fi без известных шлюзов — просто `wifi`; мобильная и провод — по одной сети каждая. */
    fun of(wifi: Boolean, wired: Boolean, gateways: List<String>): String = when {
        wifi && gateways.isEmpty() -> "wifi"
        wifi -> "wifi:" + MessageDigest.getInstance("SHA-256")
            .digest(gateways.sorted().joinToString(",").toByteArray())
            .take(4).joinToString("") { "%02x".format(it) }
        wired -> "wired"
        else -> "cell"
    }

    /**
     * Что сказать ядру, когда после [last] телефон сообщил [next]: null — ничего не менялось.
     * Wi-Fi, увиденный мельком без шлюзов, считается той же сетью, в которой телефон уже был.
     */
    fun change(last: String, next: String): String? =
        next.takeIf { it != last && !(it == "wifi" && last.startsWith("wifi:")) }
}
