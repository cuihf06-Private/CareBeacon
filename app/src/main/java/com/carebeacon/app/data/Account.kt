package com.carebeacon.app.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A login identity. The same account may be a guardian for some accounts and a
 * ward for others — that is expressed by [Relationship] rows, not on the
 * account itself.
 *
 * No password is stored yet (v2 design choice; will move to server-issued
 * credential once a backend lands).
 */
@Entity(
    tableName = "accounts",
    indices = [Index(value = ["username"], unique = true)],
)
data class Account(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    /** Login handle, unique across the local store. */
    @ColumnInfo(name = "username")
    val username: String,

    @ColumnInfo(name = "display_name")
    val displayName: String,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,
)