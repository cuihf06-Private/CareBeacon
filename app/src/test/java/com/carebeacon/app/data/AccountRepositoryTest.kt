package com.carebeacon.app.data

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for [LocalAccountRepository] against in-memory fakes. Exercises the
 * full surface area used by the upcoming auth UI.
 */
class AccountRepositoryTest {

    private fun newRepo(): Triple<LocalAccountRepository, FakeAccountDao, FakePreferencesDataStore> {
        val dao = FakeAccountDao()
        val ds = FakePreferencesDataStore()
        return Triple(LocalAccountRepository(dao, SessionStore(ds)), dao, ds)
    }

    @Test
    fun `register creates an account and makes it the current session`() {
        val (repo, dao, _) = newRepo()
        runBlocking {
            val account = repo.register("alice", "Alice")
            assertEquals("alice", account.username)
            assertEquals("Alice", account.displayName)
            assertNotNull(account.id)
            assertEquals(1, dao.count())
            assertEquals(account.id, repo.currentAccount()?.id)
        }
    }

    @Test
    fun `register throws UsernameAlreadyTaken on duplicate`() {
        val (repo, _, _) = newRepo()
        runBlocking {
            repo.register("bob", "Bob")
            assertThrows(UsernameAlreadyTaken::class.java) {
                runBlocking { repo.register("bob", "Bob 2") }
            }
        }
    }

    @Test
    fun `register rejects blank input`() {
        val (repo, _, _) = newRepo()
        runBlocking {
            assertThrows(IllegalArgumentException::class.java) {
                runBlocking { repo.register("  ", "X") }
            }
            assertThrows(IllegalArgumentException::class.java) {
                runBlocking { repo.register("ok", "  ") }
            }
        }
    }

    @Test
    fun `login resolves by username and updates the session`() {
        val (repo, _, _) = newRepo()
        runBlocking {
            val a = repo.register("carol", "Carol")
            repo.logout()
            assertNull(repo.currentAccount())

            val loggedIn = repo.login("carol")
            assertEquals(a.id, loggedIn.id)
            assertEquals(a.id, repo.currentAccount()?.id)
        }
    }

    @Test
    fun `login throws InvalidCredentials for unknown username`() {
        val (repo, _, _) = newRepo()
        runBlocking {
            repo.register("dave", "Dave")
            assertThrows(InvalidCredentials::class.java) {
                runBlocking { repo.login("nobody") }
            }
        }
    }

    @Test
    fun `logout clears the session but keeps the account row`() {
        val (repo, dao, _) = newRepo()
        runBlocking {
            repo.register("erin", "Erin")
            repo.logout()
            assertNull(repo.currentAccount())
            assertEquals(1, dao.count())
            assertNotNull(repo.findByUsername("erin"))
        }
    }

    @Test
    fun `observeCurrentAccount re-emits when a different account logs in`() {
        val (repo, _, _) = newRepo()
        runBlocking {
            repo.register("frank", "Frank")
            assertEquals("frank", repo.observeCurrentAccount().first()?.username)

            repo.register("gina", "Gina")
            assertEquals("gina", repo.observeCurrentAccount().first()?.username)
        }
    }

    @Test
    fun `switching accounts is just logout + login - reproduces the bug fix`() {
        // The whole point of this PR: you must be able to walk away from one
        // account and into another without uninstalling.
        val (repo, _, _) = newRepo()
        runBlocking {
            repo.register("hank", "Hank")
            assertEquals("hank", repo.currentAccount()?.username)

            repo.logout()
            assertNull(repo.currentAccount())

            val next = repo.login("hank")
            assertEquals("hank", next.username)

            repo.register("ivy", "Ivy")
            assertEquals("ivy", repo.currentAccount()?.username)
        }
    }

    @Test
    fun `findByUsername is null for unknown user`() {
        val (repo, _, _) = newRepo()
        runBlocking {
            assertNull(repo.findByUsername("ghost"))
            repo.register("jane", "Jane")
            assertTrue(repo.findByUsername("jane") != null)
        }
    }
}