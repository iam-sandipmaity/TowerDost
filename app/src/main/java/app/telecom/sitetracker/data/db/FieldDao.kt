package app.telecom.sitetracker.data.db

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import app.telecom.sitetracker.data.entity.SiteFieldEntity
import kotlinx.coroutines.flow.Flow

data class DistinctFieldSpec(
    @ColumnInfo(name = "group_name") val groupName: String,
    @ColumnInfo(name = "field_key") val fieldKey: String,
    @ColumnInfo(name = "min_sort") val minSort: Int
)

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

    @Query("SELECT COALESCE(NULLIF(TRIM(group_name), ''), 'General Specifications') AS group_name, field_key, MIN(sort_order) AS min_sort FROM site_fields WHERE field_key IS NOT NULL AND TRIM(field_key) != '' GROUP BY UPPER(COALESCE(NULLIF(TRIM(group_name), ''), 'General Specifications')), UPPER(field_key) ORDER BY min_sort ASC, group_name ASC, field_key ASC")
    suspend fun getDistinctSpecsFromSites(): List<DistinctFieldSpec>

    @Query("DELETE FROM site_fields WHERE sap_id = :sapId")
    suspend fun deleteFieldsForSite(sapId: String)

    @Query("DELETE FROM site_fields WHERE sap_id IN (:sapIds)")
    suspend fun deleteFieldsForSites(sapIds: List<String>)

    @Query("DELETE FROM site_fields WHERE sap_id LIKE 'DEL-9%' OR sap_id LIKE 'MUM-9%' OR sap_id LIKE 'KOL-9%' OR sap_id LIKE 'CHN-9%' OR sap_id LIKE 'CHE-9%' OR sap_id LIKE 'BLR-9%' OR sap_id LIKE 'SAMPLE%'")
    suspend fun deleteSampleFields()

    @Query("DELETE FROM site_fields")
    suspend fun deleteAllFields()

    @Transaction
    suspend fun replaceFieldsForSite(sapId: String, fields: List<SiteFieldEntity>) {
        deleteFieldsForSite(sapId)
        insertFields(fields)
    }

    @Query("SELECT DISTINCT field_value FROM site_fields WHERE LOWER(field_key) = LOWER(:fieldKey) AND field_value IS NOT NULL AND TRIM(field_value) != '' ORDER BY field_value ASC")
    suspend fun getDistinctValuesForSpec(fieldKey: String): List<String>

    @Query("SELECT sap_id FROM site_fields WHERE LOWER(field_key) = LOWER(:fieldKey) AND LOWER(TRIM(field_value)) = LOWER(TRIM(:fieldValue))")
    suspend fun getSapIdsMatchingSpec(fieldKey: String, fieldValue: String): List<String>
}
