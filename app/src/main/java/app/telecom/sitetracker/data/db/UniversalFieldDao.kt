package app.telecom.sitetracker.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import app.telecom.sitetracker.data.entity.UniversalFieldEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UniversalFieldDao {

    @Query("SELECT * FROM universal_fields ORDER BY group_name ASC, sort_order ASC, field_key ASC")
    fun observeAllUniversalFields(): Flow<List<UniversalFieldEntity>>

    @Query("SELECT * FROM universal_fields ORDER BY group_name ASC, sort_order ASC, field_key ASC")
    suspend fun getAllUniversalFields(): List<UniversalFieldEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateField(field: UniversalFieldEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateFields(fields: List<UniversalFieldEntity>)

    @Query("DELETE FROM universal_fields WHERE field_key = :fieldKey")
    suspend fun deleteField(fieldKey: String)

    @Query("DELETE FROM universal_fields WHERE group_name = :groupName")
    suspend fun deleteSection(groupName: String)

    @Query("DELETE FROM universal_fields")
    suspend fun clearAll()

    @Transaction
    suspend fun replaceAll(fields: List<UniversalFieldEntity>) {
        clearAll()
        insertOrUpdateFields(fields)
    }
}
