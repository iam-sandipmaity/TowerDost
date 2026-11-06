package app.telecom.sitetracker.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import app.telecom.sitetracker.data.entity.SiteFieldEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FieldDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFields(fields: List<SiteFieldEntity>)

    @Query("SELECT * FROM site_fields WHERE sap_id = :sapId ORDER BY sort_order ASC, id ASC")
    fun observeFieldsForSite(sapId: String): Flow<List<SiteFieldEntity>>

    @Query("SELECT * FROM site_fields WHERE sap_id = :sapId ORDER BY sort_order ASC, id ASC")
    suspend fun getFieldsForSite(sapId: String): List<SiteFieldEntity>

    @Query("SELECT * FROM site_fields ORDER BY sap_id, sort_order ASC")
    suspend fun getAllFields(): List<SiteFieldEntity>

    @Query("DELETE FROM site_fields WHERE sap_id = :sapId")
    suspend fun deleteFieldsForSite(sapId: String)
}
