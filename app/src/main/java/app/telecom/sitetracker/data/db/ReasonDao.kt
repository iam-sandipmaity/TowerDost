package app.telecom.sitetracker.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import app.telecom.sitetracker.data.entity.ReasonEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ReasonDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReasons(reasons: List<ReasonEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertReason(reason: ReasonEntity)

    @Update
    suspend fun updateReason(reason: ReasonEntity)

    @Query("SELECT * FROM reasons ORDER BY is_hidden ASC, sort_order ASC, label ASC")
    fun getAllReasons(): Flow<List<ReasonEntity>>

    @Query("SELECT * FROM reasons WHERE is_hidden = 0 ORDER BY sort_order ASC, label ASC")
    fun getActiveReasons(): Flow<List<ReasonEntity>>

    @Query("SELECT * FROM reasons WHERE id = :id LIMIT 1")
    suspend fun getReasonById(id: String): ReasonEntity?

    @Query("SELECT * FROM reasons ORDER BY sort_order ASC")
    suspend fun getAllReasonsList(): List<ReasonEntity>

    @Query("UPDATE reasons SET is_hidden = :hidden, updated_at = :updatedAt WHERE id = :id")
    suspend fun setReasonHidden(id: String, hidden: Int, updatedAt: Long)

    @Query("SELECT COUNT(*) FROM reasons")
    suspend fun getReasonCount(): Int
}
