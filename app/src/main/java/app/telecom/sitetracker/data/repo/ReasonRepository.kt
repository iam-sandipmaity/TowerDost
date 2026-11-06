package app.telecom.sitetracker.data.repo

import app.telecom.sitetracker.data.db.AppDatabase
import app.telecom.sitetracker.data.entity.ReasonEntity
import kotlinx.coroutines.flow.Flow

class ReasonRepository(private val db: AppDatabase) {
    private val reasonDao = db.reasonDao()

    fun getActiveReasons(): Flow<List<ReasonEntity>> = reasonDao.getActiveReasons()

    fun getAllReasons(): Flow<List<ReasonEntity>> = reasonDao.getAllReasons()

    suspend fun getReasonById(id: String): ReasonEntity? = reasonDao.getReasonById(id)

    suspend fun saveReason(reason: ReasonEntity) {
        reasonDao.upsertReason(reason)
    }

    suspend fun setReasonHidden(id: String, hidden: Boolean, updatedAt: Long) {
        reasonDao.setReasonHidden(id, if (hidden) 1 else 0, updatedAt)
    }

    suspend fun ensureDefaultReasonsSeeded(clock: app.telecom.sitetracker.util.AppClock) {
        if (reasonDao.getReasonCount() == 0) {
            val defaults = app.telecom.sitetracker.data.seed.SampleDataSeeder.getDefaultReasons(clock.currentTimeMillis())
            reasonDao.insertReasons(defaults)
        }
    }
}
