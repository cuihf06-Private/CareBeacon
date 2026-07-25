package com.carebeacon.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface AckLogDao {

    @Query("SELECT * FROM ack_logs ORDER BY acknowledged_at DESC")
    fun observeAll(): Flow<List<AckLog>>

    @Query("SELECT * FROM ack_logs WHERE reminder_id = :rid ORDER BY acknowledged_at DESC")
    fun observeForReminder(rid: Long): Flow<List<AckLog>>

    @Query("SELECT * FROM ack_logs WHERE synced = 0 ORDER BY acknowledged_at ASC")
    suspend fun unsynced(): List<AckLog>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: AckLog): Long

    @Update
    suspend fun update(log: AckLog)

    @Query("UPDATE ack_logs SET synced = 1 WHERE id = :id")
    suspend fun markSynced(id: Long)
}