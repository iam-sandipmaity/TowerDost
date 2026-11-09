package app.telecom.sitetracker.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import app.telecom.sitetracker.data.entity.IncidentEntity
import app.telecom.sitetracker.data.entity.IncidentHistoryEntity
import app.telecom.sitetracker.data.entity.IncidentPhotoEntity
import app.telecom.sitetracker.data.entity.OrphanIncidentView
import app.telecom.sitetracker.data.entity.ReasonEntity
import app.telecom.sitetracker.data.entity.SiteEntity
import app.telecom.sitetracker.data.entity.SiteFieldEntity
import app.telecom.sitetracker.data.entity.SiteFts
import app.telecom.sitetracker.data.entity.SiteTabEntity
import app.telecom.sitetracker.data.entity.UniversalFieldEntity

@Database(
    entities = [
        SiteEntity::class,
        SiteFieldEntity::class,
        UniversalFieldEntity::class,
        ReasonEntity::class,
        IncidentEntity::class,
        IncidentHistoryEntity::class,
        IncidentPhotoEntity::class,
        SiteFts::class,
        SiteTabEntity::class
    ],
    views = [
        OrphanIncidentView::class
    ],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun siteDao(): SiteDao
    abstract fun fieldDao(): FieldDao
    abstract fun universalFieldDao(): UniversalFieldDao
    abstract fun reasonDao(): ReasonDao
    abstract fun incidentDao(): IncidentDao
    abstract fun outageDao(): OutageDao
    abstract fun statsDao(): StatsDao
    abstract fun siteTabDao(): SiteTabDao

    companion object {
        const val DATABASE_NAME = "telecom_sitetracker.db"

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `universal_fields` (
                        `field_key` TEXT NOT NULL PRIMARY KEY,
                        `group_name` TEXT NOT NULL,
                        `default_value` TEXT,
                        `sort_order` INTEGER NOT NULL DEFAULT 0
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. Create site_tabs registry
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `site_tabs` (
                        `tab_id` TEXT NOT NULL PRIMARY KEY,
                        `display_name` TEXT NOT NULL,
                        `sort_order` INTEGER NOT NULL DEFAULT 0,
                        `is_system` INTEGER NOT NULL DEFAULT 0
                    )
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    INSERT OR IGNORE INTO `site_tabs` (`tab_id`, `display_name`, `sort_order`, `is_system`)
                    VALUES 
                        ('SPECS', 'Specs', 0, 1),
                        ('LOGS', 'Logs', 1, 1),
                        ('OVERVIEW', 'Overview', 2, 1)
                    """.trimIndent()
                )

                // 2. Add tab_id column to site_fields
                db.execSQL(
                    "ALTER TABLE `site_fields` ADD COLUMN `tab_id` TEXT NOT NULL DEFAULT 'SPECS'"
                )

                // 3. Upgrade universal_fields to composite primary key (tab_id, field_key) preserving all data
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `universal_fields_new` (
                        `tab_id` TEXT NOT NULL DEFAULT 'SPECS',
                        `field_key` TEXT NOT NULL,
                        `group_name` TEXT NOT NULL,
                        `default_value` TEXT,
                        `sort_order` INTEGER NOT NULL DEFAULT 0,
                        PRIMARY KEY(`tab_id`, `field_key`)
                    )
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    INSERT INTO `universal_fields_new` (`tab_id`, `field_key`, `group_name`, `default_value`, `sort_order`)
                    SELECT 'SPECS', `field_key`, `group_name`, `default_value`, `sort_order` FROM `universal_fields`
                    """.trimIndent()
                )

                db.execSQL("DROP TABLE `universal_fields`")
                db.execSQL("ALTER TABLE `universal_fields_new` RENAME TO `universal_fields`")
            }
        }

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                try {
                    buildDatabase(context)
                } catch (e: Throwable) {
                    // If database schema verification fails or database is corrupted,
                    // wipe cleanly and recreate to avoid startup crash loop.
                    try {
                        context.applicationContext.deleteDatabase(DATABASE_NAME)
                    } catch (_: Throwable) { }
                    buildDatabase(context)
                }
            }
        }

        private fun buildDatabase(context: Context): AppDatabase {
            val instance = Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                DATABASE_NAME
            )
            .addMigrations(MIGRATION_2_3, MIGRATION_3_4)
            .fallbackToDestructiveMigration()
            .fallbackToDestructiveMigrationOnDowngrade()
            .addCallback(object : Callback() {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    super.onCreate(db)
                    db.execSQL(
                        """
                        INSERT OR IGNORE INTO `site_tabs` (`tab_id`, `display_name`, `sort_order`, `is_system`)
                        VALUES 
                            ('SPECS', 'Specs', 0, 1),
                            ('LOGS', 'Logs', 1, 1),
                            ('OVERVIEW', 'Overview', 2, 1)
                        """.trimIndent()
                    )
                }
            })
            .build()
            INSTANCE = instance
            return instance
        }

        fun createInMemory(context: Context): AppDatabase {
            return Room.inMemoryDatabaseBuilder(
                context.applicationContext,
                AppDatabase::class.java
            )
            .allowMainThreadQueries()
            .addCallback(object : Callback() {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    super.onCreate(db)
                    db.execSQL(
                        """
                        INSERT OR IGNORE INTO `site_tabs` (`tab_id`, `display_name`, `sort_order`, `is_system`)
                        VALUES 
                            ('SPECS', 'Specs', 0, 1),
                            ('LOGS', 'Logs', 1, 1),
                            ('OVERVIEW', 'Overview', 2, 1)
                        """.trimIndent()
                    )
                }
            })
            .build()
        }
    }
}
