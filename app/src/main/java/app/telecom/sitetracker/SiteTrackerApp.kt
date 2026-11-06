package app.telecom.sitetracker

import android.app.Application
import app.telecom.sitetracker.data.db.AppDatabase
import app.telecom.sitetracker.data.repo.IncidentRepository
import app.telecom.sitetracker.data.repo.OutageRepository
import app.telecom.sitetracker.data.repo.ReasonRepository
import app.telecom.sitetracker.data.repo.SiteRepository
import app.telecom.sitetracker.data.repo.StatsRepository
import app.telecom.sitetracker.util.AppClock
import app.telecom.sitetracker.util.CrashLog
import app.telecom.sitetracker.util.SystemAppClock

class SiteTrackerApp : Application() {

    lateinit var database: AppDatabase
        private set

    var clock: AppClock = SystemAppClock()

    lateinit var siteRepository: SiteRepository
        private set

    lateinit var reasonRepository: ReasonRepository
        private set

    lateinit var incidentRepository: IncidentRepository
        private set

    lateinit var outageRepository: OutageRepository
        private set

    lateinit var statsRepository: StatsRepository
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        // Initialize local offline crash handler
        CrashLog.init(this)

        // Initialize Room Database
        database = AppDatabase.getInstance(this)

        // Initialize Repositories (Pure Manual DI)
        siteRepository = SiteRepository(database)
        reasonRepository = ReasonRepository(database)
        incidentRepository = IncidentRepository(database)
        outageRepository = OutageRepository(database)
        statsRepository = StatsRepository(database)
    }

    companion object {
        lateinit var instance: SiteTrackerApp
            private set
    }
}
