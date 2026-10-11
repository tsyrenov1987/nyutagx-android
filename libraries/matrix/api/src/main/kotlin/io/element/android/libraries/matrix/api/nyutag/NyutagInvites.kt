/*
 * Нютаг (форк Element X) — приглашения.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.libraries.matrix.api.nyutag

import io.element.android.libraries.matrix.api.core.SessionId

/**
 * Нютаг: приглашения по одноразовому коду. Сервис `/_nyutag/v1/` живёт на ядре рядом с Tuwunel,
 * ходим к нему тем же туннелем «Кильватер» и с тем же CA, что и SDK.
 */
interface NyutagInvites {
    /** Что требует сервер при регистрации и какие каналы доставки кодов настроены. */
    suspend fun signUpConfig(): Result<NyutagSignUpConfig>

    /** Проверяет код приглашения на первом шаге регистрации; Matrix ID пригласившего, если сервер его назвал. */
    suspend fun checkInvite(code: String): Result<String?>

    /** Свободен ли ник: занятый или неподходящий приходит ошибкой `M_USER_IN_USE` / `M_INVALID_USERNAME`. */
    suspend fun checkUsername(username: String): Result<Unit>

    /** Шлёт шестизначный код на телефон или почту. Код приглашения нужен серверу, чтобы не слать SMS чужим. */
    suspend fun startVerification(medium: NyutagContactMedium, address: String, inviteCode: String): Result<NyutagVerification>

    /** Проверяет код из SMS или письма. */
    suspend fun checkVerification(sid: String, token: String): Result<Unit>

    /**
     * Регистрирует аккаунт и возвращает его Matrix ID; войти нужно обычным паролем.
     * [sid] — подтверждённый телефон или почта; сервер привяжет их к аккаунту для входа.
     */
    suspend fun register(code: String, username: String, password: String, sid: String? = null): Result<String>

    /** Вход по телефону или почте: Matrix ID аккаунта, если пароль верный. */
    suspend fun resolveLogin(identifier: String, password: String): Result<String>

    /** Выпускает одноразовый код от имени участника [sessionId]; код живёт 7 дней. */
    suspend fun createInvite(sessionId: SessionId): Result<NyutagInvite>
}

enum class NyutagContactMedium(val wire: String) {
    Phone("msisdn"),
    Email("email"),
}

data class NyutagSignUpConfig(
    val requireInvite: Boolean,
    /** Телефон или почта обязательны (сервер включает это, когда настроен хотя бы один канал). */
    val requireContact: Boolean,
    val emailAvailable: Boolean,
    val phoneAvailable: Boolean,
)

data class NyutagVerification(
    val sid: String,
    val resendAfterSeconds: Int,
)

/** Похоже ли введённое на телефон или почту (а не на имя пользователя). */
fun looksLikeNyutagContact(identifier: String): Boolean {
    val s = identifier.trim()
    if (s.startsWith("@") || s.contains(":")) return false
    if (s.contains("@")) return true
    return s.count { it.isDigit() } >= 10 && s.all { it.isDigit() || it in "+-() " }
}

data class NyutagInvite(
    /** Код вида `XXXX-XXXX`. */
    val code: String,
    val expiresAtMillis: Long,
)

/** Отказ сервиса приглашений; [errcode] — машинный код: `NYUTAG_CODE_USED`, `M_USER_IN_USE` и т.п. */
class NyutagInviteException(val errcode: String, message: String) : Exception(message) {
    companion object {
        const val UNKNOWN_CODE = "NYUTAG_UNKNOWN_CODE"
        const val CODE_USED = "NYUTAG_CODE_USED"
        const val CODE_EXPIRED = "NYUTAG_CODE_EXPIRED"
        const val BAD_CODE = "NYUTAG_BAD_CODE"
        const val INVITE_LIMIT = "NYUTAG_INVITE_LIMIT"
        const val USER_IN_USE = "M_USER_IN_USE"
        const val INVALID_USERNAME = "M_INVALID_USERNAME"
        const val WEAK_PASSWORD = "M_WEAK_PASSWORD"
        const val LIMIT_EXCEEDED = "M_LIMIT_EXCEEDED"
        const val NETWORK = "NYUTAG_NETWORK"
        const val BAD_CONTACT = "NYUTAG_BAD_CONTACT"
        const val CONTACT_IN_USE = "NYUTAG_CONTACT_IN_USE"
        const val CONTACT_REQUIRED = "NYUTAG_CONTACT_REQUIRED"
        const val CONTACT_NOT_VERIFIED = "NYUTAG_CONTACT_NOT_VERIFIED"
        const val CHANNEL_UNAVAILABLE = "NYUTAG_CHANNEL_UNAVAILABLE"
        const val DELIVERY_FAILED = "NYUTAG_DELIVERY_FAILED"
        const val VERIFY_WRONG = "NYUTAG_VERIFY_WRONG"
        const val VERIFY_EXPIRED = "NYUTAG_VERIFY_EXPIRED"
        const val VERIFY_ATTEMPTS = "NYUTAG_VERIFY_ATTEMPTS"
        const val UNKNOWN_SESSION = "NYUTAG_UNKNOWN_SESSION"
        const val FORBIDDEN = "M_FORBIDDEN"
    }
}
