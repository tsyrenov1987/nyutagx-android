/*
 * Нютаг — фейк сервиса приглашений для тестов.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.libraries.matrix.test.nyutag

import io.element.android.libraries.matrix.api.core.SessionId
import io.element.android.libraries.matrix.api.nyutag.NyutagAnnouncement
import io.element.android.libraries.matrix.api.nyutag.NyutagContactMedium
import io.element.android.libraries.matrix.api.nyutag.NyutagInvite
import io.element.android.libraries.matrix.api.nyutag.NyutagInvites
import io.element.android.libraries.matrix.api.nyutag.NyutagSignUpConfig
import io.element.android.libraries.matrix.api.nyutag.NyutagVerification

class FakeNyutagInvites(
    var config: Result<NyutagSignUpConfig> = Result.success(NyutagSignUpConfig(true, false, false, false)),
    var registerResult: Result<String> = Result.success("@alice:nyutag.chat"),
    var resolveResult: Result<String> = Result.success("@alice:nyutag.chat"),
    var verificationResult: Result<NyutagVerification> = Result.success(NyutagVerification("sid", 60)),
    var checkResult: Result<Unit> = Result.success(Unit),
    var inviteResult: Result<NyutagInvite> = Result.success(NyutagInvite("ABCD-EFGH", 0L)),
    var checkInviteResult: Result<String?> = Result.success("@bob:nyutag.chat"),
    var checkUsernameResult: Result<Unit> = Result.success(Unit),
    var checkPhoneResult: Result<String> = Result.success("+79000000000"),
    var lookupResult: Result<Map<String, String>> = Result.success(emptyMap()),
    var hasPhoneResult: Result<Boolean> = Result.success(true),
    var setPhoneResult: Result<Unit> = Result.success(Unit),
) : NyutagInvites {
    val checkedUsernames = mutableListOf<String>()
    var registeredUsername: String? = null
    var registeredPhone: String? = null
    var registeredSid: String? = null
    val lookedUpPhones = mutableListOf<String>()

    var announcementResult: Result<NyutagAnnouncement?> = Result.success(null)
    val markedAnnouncements = mutableListOf<Pair<NyutagAnnouncement, Boolean>>()

    override suspend fun nextAnnouncement(language: String) = announcementResult
    override fun markAnnouncement(announcement: NyutagAnnouncement, later: Boolean) {
        markedAnnouncements += announcement to later
    }

    override suspend fun signUpConfig() = config
    override suspend fun checkInvite(code: String) = checkInviteResult
    override suspend fun checkUsername(username: String) = checkUsernameResult.also { checkedUsernames += username }
    override suspend fun startVerification(medium: NyutagContactMedium, address: String, inviteCode: String) = verificationResult
    override suspend fun checkVerification(sid: String, token: String) = checkResult
    override suspend fun checkPhone(phone: String, inviteCode: String) = checkPhoneResult
    override suspend fun register(code: String, username: String, password: String, sid: String?, phone: String?) = registerResult.also {
        registeredUsername = username
        registeredSid = sid
        registeredPhone = phone
    }
    override suspend fun resolveLogin(identifier: String, password: String) = resolveResult
    override suspend fun createInvite(sessionId: SessionId) = inviteResult
    override suspend fun lookupPhones(sessionId: SessionId, phones: List<String>) = lookupResult.also { lookedUpPhones += phones }

    var inContactsResult: Result<Boolean> = Result.success(false)
    override suspend fun isInContacts(sessionId: SessionId, userId: String, phones: List<String>) = inContactsResult
    override suspend fun hasPhone(sessionId: SessionId) = hasPhoneResult
    override suspend fun setPhone(sessionId: SessionId, phone: String) = setPhoneResult
}
