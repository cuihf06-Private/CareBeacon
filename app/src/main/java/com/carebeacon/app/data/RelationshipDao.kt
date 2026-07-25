package com.carebeacon.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RelationshipDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(relationship: Relationship)

    @Query("UPDATE relationships SET status = :status, accepted_at = :acceptedAt WHERE id = :id")
    suspend fun updateStatus(id: String, status: String, acceptedAt: Long?)

    @Query("SELECT * FROM relationships WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): Relationship?

    @Query("SELECT * FROM relationships WHERE ward_id = :wardId AND guardian_id = :guardianId LIMIT 1")
    suspend fun findPair(wardId: String, guardianId: String): Relationship?

    /** All non-revoked relationships where the account is the guardian. */
    @Query(
        """
        SELECT * FROM relationships
        WHERE guardian_id = :accountId AND status != 'REVOKED'
        ORDER BY invited_at DESC
        """
    )
    fun observeMyWards(accountId: String): Flow<List<Relationship>>

    /** All non-revoked relationships where the account is the ward. */
    @Query(
        """
        SELECT * FROM relationships
        WHERE ward_id = :accountId AND status != 'REVOKED'
        ORDER BY invited_at DESC
        """
    )
    fun observeMyGuardians(accountId: String): Flow<List<Relationship>>

    /** Snapshot of all non-revoked relationships touching an account (any side). */
    @Query(
        """
        SELECT * FROM relationships
        WHERE (ward_id = :accountId OR guardian_id = :accountId) AND status != 'REVOKED'
        ORDER BY invited_at DESC
        """
    )
    suspend fun forAccount(accountId: String): List<Relationship>

    @Query("SELECT COUNT(*) FROM relationships")
    suspend fun count(): Int
}