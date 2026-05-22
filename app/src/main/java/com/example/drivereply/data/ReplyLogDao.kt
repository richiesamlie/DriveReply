package com.example.drivereply.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ReplyLogDao {

    @Query("SELECT * FROM reply_log ORDER BY timestamp DESC")
    fun getAll(): Flow<List<ReplyLogEntry>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: ReplyLogEntry)

    @Query("DELETE FROM reply_log WHERE timestamp < :timestamp")
    suspend fun deleteOlderThan(timestamp: Long)

    @Query("DELETE FROM reply_log")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM reply_log")
    fun getCount(): Flow<Int>
}
