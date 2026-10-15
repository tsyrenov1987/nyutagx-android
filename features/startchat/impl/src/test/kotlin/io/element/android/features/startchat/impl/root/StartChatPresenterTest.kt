/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.startchat.impl.root

import androidx.compose.runtime.MutableState
import com.google.common.truth.Truth.assertThat
import io.element.android.features.invitepeople.test.FakeStartDMAction
import io.element.android.features.startchat.api.ConfirmingStartDmWithMatrixUser
import io.element.android.features.startchat.api.StartDMAction
import io.element.android.features.startchat.impl.NyutagSavedMessages
import io.element.android.features.startchat.impl.userlist.FakeUserListPresenter
import io.element.android.features.startchat.impl.userlist.FakeUserListPresenterFactory
import io.element.android.features.startchat.impl.userlist.UserListDataStore
import io.element.android.libraries.architecture.AsyncAction
import io.element.android.libraries.architecture.AsyncData
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.core.UserId
import io.element.android.libraries.matrix.api.nyutag.NyutagAddressBook
import io.element.android.libraries.matrix.api.nyutag.NyutagContactsSync
import io.element.android.libraries.matrix.api.nyutag.NyutagMatchedContact
import io.element.android.libraries.matrix.api.user.MatrixUser
import io.element.android.libraries.matrix.test.AN_EXCEPTION
import io.element.android.libraries.matrix.test.A_ROOM_ID
import io.element.android.libraries.matrix.test.FakeMatrixClient
import io.element.android.libraries.matrix.test.core.aBuildMeta
import io.element.android.libraries.matrix.test.nyutag.FakeNyutagContactsSync
import io.element.android.libraries.usersearch.test.FakeUserRepository
import io.element.android.tests.testutils.WarmUpRule
import io.element.android.tests.testutils.awaitLastSequentialItem
import io.element.android.tests.testutils.lambda.any
import io.element.android.tests.testutils.lambda.lambdaRecorder
import io.element.android.tests.testutils.lambda.value
import io.element.android.tests.testutils.test
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class StartChatPresenterTest {
    @get:Rule
    val warmUpRule = WarmUpRule()

    @Test
    fun `present - start DM action failure scenario`() = runTest {
        val startDMFailureResult = AsyncAction.Failure(AN_EXCEPTION)
        val executeResult = lambdaRecorder<MatrixUser, Boolean, MutableState<AsyncAction<RoomId>>, Unit> { _, _, actionState ->
            actionState.value = startDMFailureResult
        }
        val startDMAction = FakeStartDMAction(executeResult = executeResult)
        val presenter = createStartChatPresenter(startDMAction)
        presenter.test {
            val initialState = awaitItem()
            assertThat(initialState.startDmAction).isInstanceOf(AsyncAction.Uninitialized::class.java)
            assertThat(initialState.applicationName).isEqualTo(aBuildMeta().applicationName)
            assertThat(initialState.userListState.selectedUsers).isEmpty()
            assertThat(initialState.userListState.isSearchActive).isFalse()
            assertThat(initialState.userListState.isMultiSelectionEnabled).isFalse()
            val matrixUser = MatrixUser(UserId("@name:domain"))
            initialState.eventSink(StartChatEvent.StartDM(matrixUser))
            awaitItem().also { state ->
                assertThat(state.startDmAction).isEqualTo(startDMFailureResult)
                executeResult.assertions().isCalledOnce().with(
                    value(matrixUser),
                    value(false),
                    any(),
                )
                state.eventSink(StartChatEvent.CancelStartDM)
            }
            awaitItem().also { state ->
                assertThat(state.startDmAction.isUninitialized()).isTrue()
            }
        }
    }

    @Test
    fun `present - open saved messages emits the room to open`() = runTest {
        val presenter = createStartChatPresenter(savedMessages = { name, _ ->
            assertThat(name).isEqualTo("Saved")
            Result.success(A_ROOM_ID)
        })
        presenter.test {
            awaitItem().eventSink(StartChatEvent.OpenSavedMessages("Saved", "Notes"))
            assertThat(expectMostRecentItem().startDmAction).isEqualTo(AsyncAction.Success(A_ROOM_ID))
        }
    }

    @Test
    fun `present - start DM action success scenario`() = runTest {
        val startDMSuccessResult = AsyncAction.Success(A_ROOM_ID)
        val executeResult = lambdaRecorder<MatrixUser, Boolean, MutableState<AsyncAction<RoomId>>, Unit> { _, _, actionState ->
            actionState.value = startDMSuccessResult
        }
        val startDMAction = FakeStartDMAction(executeResult = executeResult)
        val presenter = createStartChatPresenter(startDMAction)
        presenter.test {
            val initialState = awaitItem()
            assertThat(initialState.startDmAction).isInstanceOf(AsyncAction.Uninitialized::class.java)
            assertThat(initialState.applicationName).isEqualTo(aBuildMeta().applicationName)
            assertThat(initialState.userListState.selectedUsers).isEmpty()
            assertThat(initialState.userListState.isSearchActive).isFalse()
            assertThat(initialState.userListState.isMultiSelectionEnabled).isFalse()
            val matrixUser = MatrixUser(UserId("@name:domain"))
            initialState.eventSink(StartChatEvent.StartDM(matrixUser))
            awaitItem().also { state ->
                assertThat(state.startDmAction).isEqualTo(startDMSuccessResult)
                executeResult.assertions().isCalledOnce().with(
                    value(matrixUser),
                    value(false),
                    any(),
                )
            }
        }
    }

    @Test
    fun `present - start DM action confirmation scenario - cancel`() = runTest {
        val matrixUser = MatrixUser(UserId("@name:domain"))
        val startDMConfirmationResult = ConfirmingStartDmWithMatrixUser(matrixUser, isUserIdentityUnknown = false)
        val executeResult = lambdaRecorder<MatrixUser, Boolean, MutableState<AsyncAction<RoomId>>, Unit> { _, _, actionState ->
            actionState.value = startDMConfirmationResult
        }
        val startDMAction = FakeStartDMAction(executeResult = executeResult)
        val presenter = createStartChatPresenter(startDMAction)
        presenter.test {
            val initialState = awaitItem()
            assertThat(initialState.startDmAction).isInstanceOf(AsyncAction.Uninitialized::class.java)
            initialState.eventSink(StartChatEvent.StartDM(matrixUser))
            val confirmingState = awaitItem()
            assertThat(confirmingState.startDmAction).isEqualTo(startDMConfirmationResult)
            executeResult.assertions().isCalledOnce().with(
                value(matrixUser),
                value(false),
                any(),
            )
            // Cancelling should not create the DM
            confirmingState.eventSink(StartChatEvent.CancelStartDM)
            val finalState = awaitItem()
            assertThat(finalState.startDmAction.isUninitialized()).isTrue()
            executeResult.assertions().isCalledExactly(1)
        }
    }

    @Test
    fun `present - start DM action confirmation scenario - confirm`() = runTest {
        val matrixUser = MatrixUser(UserId("@name:domain"))
        val startDMConfirmationResult = ConfirmingStartDmWithMatrixUser(matrixUser, isUserIdentityUnknown = false)
        val executeResult = lambdaRecorder<MatrixUser, Boolean, MutableState<AsyncAction<RoomId>>, Unit> { _, _, actionState ->
            actionState.value = startDMConfirmationResult
        }
        val startDMAction = FakeStartDMAction(executeResult = executeResult)
        val presenter = createStartChatPresenter(startDMAction)
        presenter.test {
            val initialState = awaitItem()
            assertThat(initialState.startDmAction).isInstanceOf(AsyncAction.Uninitialized::class.java)
            initialState.eventSink(StartChatEvent.StartDM(matrixUser))
            val confirmingState = awaitItem()
            assertThat(confirmingState.startDmAction).isEqualTo(startDMConfirmationResult)
            executeResult.assertions().isCalledOnce().with(
                value(matrixUser),
                value(false),
                any(),
            )
            // Start DM again should invoke the action with createIfDmDoesNotExist = true
            confirmingState.eventSink(StartChatEvent.StartDM(matrixUser))
            executeResult.assertions().isCalledExactly(2).withSequence(
                listOf(value(matrixUser), value(false), any()),
                listOf(value(matrixUser), value(true), any()),
            )
        }
    }

    @Test
    fun `present - contacts are synced     when the permission is granted and a contact opens a DM without confirmation`() = runTest {
        val bair = NyutagMatchedContact("Баир", "+79000000001", MatrixUser(UserId("@bair:nyutag.chat")))
        val contactsSync = FakeNyutagContactsSync(cachedResult = null, syncResult = Result.success(listOf(bair)))
        val executeResult = lambdaRecorder<MatrixUser, Boolean, MutableState<AsyncAction<RoomId>>, Unit> { _, _, actionState ->
            actionState.value = AsyncAction.Success(A_ROOM_ID)
        }
        val presenter = createStartChatPresenter(
            startDMAction = FakeStartDMAction(executeResult = executeResult),
            canReadContacts = true,
            contactsSync = contactsSync,
        )
        presenter.test {
            val synced = awaitLastSequentialItem()
            assertThat(synced.canReadContacts).isTrue()
            assertThat(synced.nyutagContacts.dataOrNull()).containsExactly(bair)
            assertThat(contactsSync.syncCalls).isEqualTo(1)
            synced.eventSink(StartChatEvent.StartDMWithContact(bair.user))
            assertThat(awaitLastSequentialItem().startDmAction).isEqualTo(AsyncAction.Success(A_ROOM_ID))
            executeResult.assertions().isCalledOnce().with(value(bair.user), value(true), any())
        }
    }

    @Test
    fun `present - contacts are not synced without the permission`() = runTest {
        val contactsSync = FakeNyutagContactsSync()
        val presenter = createStartChatPresenter(canReadContacts = false, contactsSync = contactsSync)
        presenter.test {
            val state = awaitLastSequentialItem()
            assertThat(state.canReadContacts).isFalse()
            assertThat(state.nyutagContacts).isEqualTo(AsyncData.Uninitialized)
            assertThat(contactsSync.syncCalls).isEqualTo(0)
        }
    }
}

internal fun createStartChatPresenter(
    startDMAction: StartDMAction = FakeStartDMAction(),
    savedMessages: NyutagSavedMessages = NyutagSavedMessages { _, _ -> error("not expected") },
    canReadContacts: Boolean = false,
    contactsSync: NyutagContactsSync = FakeNyutagContactsSync(),
): StartChatPresenter {
    return StartChatPresenter(
        presenterFactory = FakeUserListPresenterFactory(FakeUserListPresenter()),
        userRepository = FakeUserRepository(),
        userListDataStore = UserListDataStore(),
        startDMAction = startDMAction,
        savedMessages = savedMessages,
        buildMeta = aBuildMeta(),
        matrixClient = FakeMatrixClient(),
        addressBook = object : NyutagAddressBook {
            override fun canRead() = canReadContacts
            override suspend fun phones() = emptyList<String>()
        },
        contactsSync = contactsSync,
    )
}
