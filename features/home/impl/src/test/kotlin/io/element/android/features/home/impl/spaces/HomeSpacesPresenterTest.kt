/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.home.impl.spaces

import com.google.common.truth.Truth.assertThat
import io.element.android.features.invite.api.SeenInvitesStore
import io.element.android.features.invite.test.InMemorySeenInvitesStore
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.core.SessionId
import io.element.android.libraries.matrix.api.nyutag.NyutagCommunities
import io.element.android.libraries.matrix.api.nyutag.NyutagCommunity
import io.element.android.libraries.matrix.api.nyutag.NyutagCommunityPage
import io.element.android.libraries.matrix.api.room.join.JoinRule
import io.element.android.libraries.matrix.test.FakeMatrixClient
import io.element.android.tests.testutils.consumeItemsUntilPredicate
import io.element.android.tests.testutils.test
import kotlinx.coroutines.test.runTest
import org.junit.Test

class HomeSpacesPresenterTest {
    @Test
    fun `present - initial state`() = runTest {
        val presenter = createPresenter()
        presenter.test {
            val state = awaitItem()
            assertThat(state.space).isEqualTo(CurrentSpace.Root)
            assertThat(state.spaceRooms).isEmpty()
            assertThat(state.hideInvitesAvatar).isFalse()
            assertThat(state.seenSpaceInvites).isEmpty()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `present - server communities are listed in the catalog`() = runTest {
        val closed = NyutagCommunity(
            roomId = RoomId("!closed:example.com"),
            name = "Closed",
            topic = null,
            alias = null,
            avatarUrl = null,
            membersCount = 3,
            isOpen = false,
        )
        val presenter = createPresenter(nyutagCommunities = FakeNyutagCommunities(listOf(closed)))
        presenter.test {
            val state = consumeItemsUntilPredicate { it.catalog.isNotEmpty() }.last()
            assertThat(state.catalog.single().roomId).isEqualTo(closed.roomId)
            assertThat(state.catalog.single().joinRule).isEqualTo(JoinRule.Knock)
            assertThat(state.catalog.single().numJoinedMembers).isEqualTo(3)
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun createPresenter(
        client: MatrixClient = FakeMatrixClient(),
        seenInvitesStore: SeenInvitesStore = InMemorySeenInvitesStore(),
        nyutagCommunities: NyutagCommunities = FakeNyutagCommunities(),
    ) = HomeSpacesPresenter(
        client = client,
        seenInvitesStore = seenInvitesStore,
        nyutagCommunities = nyutagCommunities,
    )
}

private class FakeNyutagCommunities(
    private val communities: List<NyutagCommunity> = emptyList(),
) : NyutagCommunities {
    override suspend fun communities(sessionId: SessionId, searchTerm: String?, since: String?) =
        Result.success(NyutagCommunityPage(communities, nextBatch = null))
}
