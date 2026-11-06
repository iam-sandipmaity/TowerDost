package app.telecom.sitetracker.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import app.telecom.sitetracker.data.entity.IncidentEntity
import app.telecom.sitetracker.data.entity.IncidentHistoryEntity
import app.telecom.sitetracker.data.entity.IncidentPhotoEntity
import app.telecom.sitetracker.data.entity.OrphanIncidentView
import app.telecom.sitetracker.data.entity.ReasonEntity
import app.telecom.sitetracker.data.entity.SiteEntity
import app.telecom.sitetracker.data.entity.SiteFieldEntity
import app.telecom.sitetracker.data.entity.SiteFts

@Database(
    entities = [
        SiteEntity::class,
        SiteFieldEntity::class,
        ReasonEntity::class,
        IncidentEntity::class,
        IncidentHistoryEntity::class,
        IncidentPhotoEntity::class,
        SiteFts::class
    ],
    views = [
        OrphanIncidentView::class
    ],
    version = 2,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun siteDao(): SiteDao
    abstract fun fieldDao(): FieldDao
    abstract fun reasonDao(): ReasonDao
    abstract fun incidentDao(): IncidentDao
    abstract fun outageDao(): OutageDao
    abstract fun statsDao(): StatsDao

    companion object {
        const val DATABASE_NAME = "telecom_sitetracker.db"

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
            .fallbackToDestructiveMigration()
            .fallbackToDestructiveMigrationOnDowngrade()
            .addCallback(object : Callback() {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    super.onCreate(db)
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
            .build()
        }
    }
}
