package com.carebeacon.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {

    @Query("SELECT * FROM accounts WHERE username = :username LIMIT 1")
    suspend fun findByUsername(username: String): Account?

    @Query("SELECT * FROM accounts WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): Account?

    @Query("SELECT * FROM accounts WHERE id = :id LIMIT 1")
    fun observeById(id: String): Flow<Account?>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(account: Account)

    @Query("SELECT COUNT(*) FROM accounts")
    suspend fun count(): Int
}