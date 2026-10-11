/*
 * Нютаг — фейк сервиса приглашений для тестов.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.libraries.matrix.test.nyutag

import io.element.android.libraries.matrix.api.core.SessionId
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
) : NyutagInvites {
    val checkedUsernames = mutableListOf<String>()
    var registeredUsername: String? = null

    override suspend fun signUpConfig() = config
    override suspend fun checkInvite(code: String) = checkInviteResult
    override suspend fun checkUsername(username: String) = checkUsernameResult.also { checkedUsernames += username }
    override suspend fun startVerification(medium: NyutagContactMedium, address: String, inviteCode: String) = verificationResult
    override suspend fun checkVerification(sid: String, token: String) = checkResult
    override suspend fun register(code: String, username: String, password: String, sid: String?) = registerResult.also { registeredUsername = username }
    override suspend fun resolveLogin(identifier: String, password: String) = resolveResult
    override suspend fun createInvite(sessionId: SessionId) = inviteResult
}
