package com.carebeacon.app.data

import kotlinx.coroutines.flow.Flow
import java.util.UUID

/**
 * Surface for pairing an account with one or more guardians / wards. The
 * interface exists so the local-only v1 impl and a future server impl can be
 * swapped without touching UI / ViewModel.
 */
interface RelationshipRepository {

    /**
     * As [wardId] (the account that will receive reminders), invite the account
     * identified by [guardianUsername] to act as guardian. Self-invite is legal.
     *
     * Throws:
     *  - [GuardianNotFound] if no account matches the username.
     *  - [DuplicateInvite] if the (ward, guardian) pair already has a non-revoked row.
     *
     * On success the row is created with status ACCEPTED (local mock — the
     * server will mediate acceptance in a later phase).
     */
    suspend fun inviteGuardianByUsername(wardId: String, guardianUsername: String): Relationship

    suspend fun revoke(relationshipId: String)

    /** Stream of active relationships where [accountId] is the guardian. */
    fun observeMyWards(accountId: String): Flow<List<Relationship>>

    /** Stream of active relationships where [accountId] is the ward. */
    fun observeMyGuardians(accountId: String): Flow<List<Relationship>>

    /** One-shot snapshot for tests / boot logic. */
    suspend fun forAccount(accountId: String): List<Relationship>
}

class GuardianNotFound(message: String) : IllegalStateException(message)
class DuplicateInvite(message: String) : IllegalStateException(message)

/**
 * Local-only implementation backed by Room.
 */
class LocalRelationshipRepository(
    private val relationshipDao: RelationshipDao,
    private val accountDao: AccountDao,
) : RelationshipRepository {

    override suspend fun inviteGuardianByUsername(
        wardId: String,
        guardianUsername: String,
    ): Relationship {
        val guardian = accountDao.findByUsername(guardianUsername.trim())
            ?: throw GuardianNotFound("no account with username '$guardianUsername'")
        val existing = relationshipDao.findPair(wardId, guardian.id)
        if (!RelationshipPolicy.canInvite(wardId, guardian.id, existing)) {
            throw DuplicateInvite("relationship already exists for this pair")
        }
        val now = System.currentTimeMillis()
        val row = Relationship(
            id = UUID.randomUUID().toString(),
            wardId = wardId,
            guardianId = guardian.id,
            status = RelationshipPolicy.STATUS_ACCEPTED,
            invitedAt = now,
            acceptedAt = now,
        )
        relationshipDao.insert(row)
        return row
    }

    override suspend fun revoke(relationshipId: String) {
        relationshipDao.updateStatus(
            id = relationshipId,
            status = RelationshipPolicy.STATUS_REVOKED,
            acceptedAt = null,
        )
    }

    override fun observeMyWards(accountId: String): Flow<List<Relationship>> =
        relationshipDao.observeMyWards(accountId)

    override fun observeMyGuardians(accountId: String): Flow<List<Relationship>> =
        relationshipDao.observeMyGuardians(accountId)

    override suspend fun forAccount(accountId: String): List<Relationship> =
        relationshipDao.forAccount(accountId)
}