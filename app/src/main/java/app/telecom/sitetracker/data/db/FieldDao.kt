package app.telecom.sitetracker.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import app.telecom.sitetracker.data.entity.SiteFieldEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FieldDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFields(fields: List<SiteFieldEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertField(field: SiteFieldEntity): Long

    @Update
    suspend fun updateField(field: SiteFieldEntity)

    @Query("DELETE FROM site_fields WHERE id = :id")
    suspend fun deleteFieldById(id: Long)

    @Query("DELETE FROM site_fields WHERE sap_id = :sapId AND field_key = :fieldKey")
    suspend fun deleteFieldByKey(sapId: String, fieldKey: String)

    @Query("UPDATE site_fields SET field_key = :newKey, group_name = :newGroup WHERE LOWER(field_key) = LOWER(:oldKey)")
    suspend fun renameFieldKey(oldKey: String, newKey: String, newGroup: String)

    @Query("SELECT * FROM site_fields WHERE sap_id = :sapId ORDER BY sort_order ASC, id ASC")
    fun observeFieldsForSite(sapId: String): Flow<List<SiteFieldEntity>>

    @Query("SELECT * FROM site_fields WHERE sap_id = :sapId ORDER BY sort_order ASC, id ASC")
    suspend fun getFieldsForSite(sapId: String): List<SiteFieldEntity>

    @Query("SELECT * FROM site_fields ORDER BY sap_id, sort_order ASC")
    suspend fun getAllFields(): List<SiteFieldEntity>

    @Query("DELETE FROM site_fields WHERE sap_id = :sapId")
    suspend fun deleteFieldsForSite(sapId: String)

    @Query("DELETE FROM site_fields WHERE sap_id IN (:sapIds)")
    suspend fun deleteFieldsForSites(sapIds: List<String>)

    @Query("DELETE FROM site_fields")
    suspend fun deleteAllFields()

    @Transaction
    suspend fun replaceFieldsForSite(sapId: String, fields: List<SiteFieldEntity>) {
        deleteFieldsForSite(sapId)
        insertFields(fields)
    }
}
