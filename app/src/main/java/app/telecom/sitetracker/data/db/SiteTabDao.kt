package app.telecom.sitetracker.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import app.telecom.sitetracker.data.entity.SiteTabEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SiteTabDao {

    @Query("SELECT * FROM site_tabs ORDER BY sort_order ASC, tab_id ASC")
    fun observeAllTabs(): Flow<List<SiteTabEntity>>

    @Query("SELECT * FROM site_tabs ORDER BY sort_order ASC, tab_id ASC")
    suspend fun getAllTabs(): List<SiteTabEntity>

    @Query("SELECT * FROM site_tabs WHERE tab_id = :tabId LIMIT 1")
    suspend fun getTabById(tabId: String): SiteTabEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTab(tab: SiteTabEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTabs(tabs: List<SiteTabEntity>)

    @Query("DELETE FROM site_tabs WHERE tab_id = :tabId")
    suspend fun deleteTab(tabId: String)

    @Query("SELECT COUNT(*) FROM site_tabs")
    suspend fun getTabCount(): Int
}
