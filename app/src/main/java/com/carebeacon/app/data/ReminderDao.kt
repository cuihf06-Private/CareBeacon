package com.carebeacon.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ReminderDao {

    @Query("SELECT * FROM reminders WHERE enabled = 1 ORDER BY hour, minute")
    fun observeEnabled(): Flow<List<Reminder>>

    @Query("SELECT * FROM reminders ORDER BY hour, minute")
    fun observeAll(): Flow<List<Reminder>>

    @Query("SELECT * FROM reminders WHERE id = :id")
    suspend fun get(id: Long): Reminder?

    @Query("SELECT * FROM reminders WHERE enabled = 1")
    suspend fun allEnabled(): List<Reminder>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(reminder: Reminder): Long

    @Update
    suspend fun update(reminder: Reminder)

    @Delete
    suspend fun delete(reminder: Reminder)

    @Query("UPDATE reminders SET next_trigger_at = :triggerAt WHERE id = :id")
    suspend fun reschedule(id: Long, triggerAt: Long)
}