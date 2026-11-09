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

    @Query("SELECT * FROM universal_fields ORDER BY sort_order ASC, group_name ASC, field_key ASC")
    fun observeAllUniversalFields(): Flow<List<UniversalFieldEntity>>

    @Query("SELECT * FROM universal_fields ORDER BY sort_order ASC, group_name ASC, field_key ASC")
    suspend fun getAllUniversalFields(): List<UniversalFieldEntity>

    @Query("SELECT * FROM universal_fields WHERE tab_id = :tabId ORDER BY sort_order ASC, group_name ASC, field_key ASC")
    fun observeUniversalFieldsForTab(tabId: String): Flow<List<UniversalFieldEntity>>

    @Query("SELECT * FROM universal_fields WHERE tab_id = :tabId ORDER BY sort_order ASC, group_name ASC, field_key ASC")
    suspend fun getUniversalFieldsForTab(tabId: String): List<UniversalFieldEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateField(field: UniversalFieldEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateFields(fields: List<UniversalFieldEntity>)

    @Query("DELETE FROM universal_fields WHERE field_key = :fieldKey")
    suspend fun deleteField(fieldKey: String)

    @Query("DELETE FROM universal_fields WHERE tab_id = :tabId AND field_key = :fieldKey")
    suspend fun deleteField(tabId: String, fieldKey: String)

    @Query("DELETE FROM universal_fields WHERE group_name = :groupName")
    suspend fun deleteSection(groupName: String)

    @Query("DELETE FROM universal_fields WHERE tab_id = :tabId AND group_name = :groupName")
    suspend fun deleteSection(tabId: String, groupName: String)

    @Query("DELETE FROM universal_fields WHERE tab_id = :tabId")
    suspend fun deleteFieldsForTab(tabId: String)

    @Query("DELETE FROM universal_fields")
    suspend fun clearAll()

    @Transaction
    suspend fun replaceAll(fields: List<UniversalFieldEntity>) {
        clearAll()
        insertOrUpdateFields(fields)
    }
}
