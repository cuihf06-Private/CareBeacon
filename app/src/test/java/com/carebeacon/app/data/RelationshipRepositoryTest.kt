package com.carebeacon.app.data

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for [LocalRelationshipRepository] against in-memory fakes. Covers the
 * happy paths plus the failure modes that drive UI error states.
 */
class RelationshipRepositoryTest {

    private fun newRepo(): Triple<LocalRelationshipRepository, FakeRelationshipDao, FakeAccountDao> {
        val rdao = FakeRelationshipDao()
        val adao = FakeAccountDao()
        return Triple(LocalRelationshipRepository(rdao, adao), rdao, adao)
    }

    private suspend fun seed(
        adao: FakeAccountDao,
        vararg usernames: String,
    ): List<Account> = usernames.map { name ->
        val acc = Account(
            id = "id-$name",
            username = name,
            displayName = name.replaceFirstChar { it.uppercase() },
            createdAt = 0L,
        )
        adao.insert(acc)
        acc
    }

    @Test
    fun `inviteGuardianByUsername creates an accepted row`() {
        val (repo, _, adao) = newRepo()
        runBlocking {
            val (ward, guardian) = seed(adao, "ward", "guardian")
            val row = repo.inviteGuardianByUsername(ward.id, "guardian")
            assertEquals(ward.id, row.wardId)
            assertEquals(guardian.id, row.guardianId)
            assertEquals(RelationshipPolicy.STATUS_ACCEPTED, row.status)
            assertTrue(row.acceptedAt != null && row.acceptedAt!! > 0L)
        }
    }

    @Test
    fun `inviteGuardianByUsername throws GuardianNotFound when username is unknown`() {
        val (repo, _, adao) = newRepo()
        runBlocking {
            val (ward) = seed(adao, "ward")
            assertThrows(GuardianNotFound::class.java) {
                runBlocking { repo.inviteGuardianByUsername(ward.id, "ghost") }
            }
        }
    }

    @Test
    fun `inviteGuardianByUsername throws DuplicateInvite when pair already active`() {
        val (repo, _, adao) = newRepo()
        runBlocking {
            val (ward, _) = seed(adao, "ward", "guardian")
            repo.inviteGuardianByUsername(ward.id, "guardian")
            assertThrows(DuplicateInvite::class.java) {
                runBlocking { repo.inviteGuardianByUsername(ward.id, "guardian") }
            }
        }
    }

    @Test
    fun `self invite is legal in production`() {
        val (repo, _, adao) = newRepo()
        runBlocking {
            val (me) = seed(adao, "solo")
            val row = repo.inviteGuardianByUsername(me.id, "solo")
            assertEquals(me.id, row.wardId)
            assertEquals(me.id, row.guardianId)
        }
    }

    @Test
    fun `self invite twice throws DuplicateInvite`() {
        val (repo, _, adao) = newRepo()
        runBlocking {
            val (me) = seed(adao, "solo")
            repo.inviteGuardianByUsername(me.id, "solo")
            assertThrows(DuplicateInvite::class.java) {
                runBlocking { repo.inviteGuardianByUsername(me.id, "solo") }
            }
        }
    }

    @Test
    fun `revoke removes the pair from observeMyWards and observeMyGuardians`() {
        val (repo, _, adao) = newRepo()
        runBlocking {
            val (ward, guardian) = seed(adao, "ward", "guardian")
            val row = repo.inviteGuardianByUsername(ward.id, "guardian")
            repo.revoke(row.id)

            assertTrue(repo.observeMyWards(guardian.id).first().isEmpty())
            assertTrue(repo.observeMyGuardians(ward.id).first().isEmpty())
        }
    }

    @Test
    fun `after revoke a fresh invite of the same pair succeeds`() {
        val (repo, _, adao) = newRepo()
        runBlocking {
            val (ward, _) = seed(adao, "ward", "guardian")
            val first = repo.inviteGuardianByUsername(ward.id, "guardian")
            repo.revoke(first.id)
            val second = repo.inviteGuardianByUsername(ward.id, "guardian")
            assertEquals(RelationshipPolicy.STATUS_ACCEPTED, second.status)
        }
    }

    @Test
    fun `observeMyWards and observeMyGuardians keep the two sides symmetric`() {
        val (repo, _, adao) = newRepo()
        runBlocking {
            val (a, b) = seed(adao, "alice", "bob")
            repo.inviteGuardianByUsername(a.id, "bob")
            repo.inviteGuardianByUsername(b.id, "alice")

            val bobsWards = repo.observeMyWards(b.id).first().map { it.wardId }
            val bobsGuardians = repo.observeMyGuardians(b.id).first().map { it.guardianId }
            assertTrue(bobsWards.contains(a.id))
            assertTrue(bobsGuardians.contains(a.id))
        }
    }

    @Test
    fun `forAccount returns both sides for one account`() {
        val (repo, _, adao) = newRepo()
        runBlocking {
            val accounts = seed(adao, "alice", "bob", "carol")
            val alice = accounts[0]
            val bob = accounts[1]
            repo.inviteGuardianByUsername(alice.id, "bob")
            repo.inviteGuardianByUsername(alice.id, "carol")
            repo.inviteGuardianByUsername(bob.id, "alice")

            val rows = repo.forAccount(alice.id)
            // 2 rows where alice is ward + 1 row where alice is guardian = 3.
            assertEquals(3, rows.size)
        }
    }
}