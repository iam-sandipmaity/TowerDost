package app.telecom.sitetracker.data.seed

import app.telecom.sitetracker.data.entity.ReasonEntity

/**
 * Standard Indian telecom root cause taxonomy.
 * Zero fictional/demo sites or mock incidents.
 */
object SampleDataSeeder {

    fun getDefaultReasons(nowMs: Long): List<ReasonEntity> {
        return listOf(
            ReasonEntity("REAS_EB_GRID", "EB / grid power failure", "Power", "Power", 1, 10, 0, 1, nowMs),
            ReasonEntity("REAS_SMPS_PLANT", "Power plant / SMPS failure", "Power", "SMPS", 1, 20, 0, 1, nowMs),
            ReasonEntity("REAS_RM_MODULE", "Rectifier module (RM) failure", "Power", "Rectifier", 1, 30, 0, 1, nowMs),
            ReasonEntity("REAS_BATT_LOW", "Battery backup low", "Battery", "Battery", 1, 40, 0, 1, nowMs),
            ReasonEntity("REAS_BATT_FAILED", "Battery bank failed", "Battery", "Battery", 1, 50, 0, 1, nowMs),
            ReasonEntity("REAS_DG_MANUAL", "DG manual start required", "DG", "DG", 1, 60, 0, 1, nowMs),
            ReasonEntity("REAS_DG_AUTO_FAIL", "DG auto-start failure", "DG", "DG", 1, 70, 0, 1, nowMs),
            ReasonEntity("REAS_DG_FUEL_SVC", "DG fuel / oil / servicing", "DG", "DG", 1, 80, 0, 1, nowMs),
            ReasonEntity("REAS_DG_REPAIR", "DG under repair", "DG", "DG", 1, 90, 0, 1, nowMs),
            ReasonEntity("REAS_OFC_CUT", "OFC cut (fibre)", "Transmission", "OFC", 1, 100, 0, 1, nowMs),
            ReasonEntity("REAS_MW_DOWN", "Transmission / MW / backhaul down", "Transmission", "Transmission", 1, 110, 0, 1, nowMs),
            ReasonEntity("REAS_BTS_HW", "BTS / RAN hardware", "RAN", "BTS-RAN", 1, 120, 0, 1, nowMs),
            ReasonEntity("REAS_TOWER_ANT", "Tower / antenna / feeder", "Civil", "Tower", 1, 130, 0, 1, nowMs),
            ReasonEntity("REAS_AC_COOLING", "AC / cooling failure", "Electrical", "AC", 1, 140, 0, 1, nowMs),
            ReasonEntity("REAS_SOLAR_HYBRID", "Solar / hybrid plant issue", "Power", "Power", 1, 150, 0, 1, nowMs),
            ReasonEntity("REAS_EARTH_SURGE", "Earthing / surge", "Electrical", "Power", 1, 160, 0, 1, nowMs),
            ReasonEntity("REAS_FIRE_SMOKE", "Fire / smoke alarm", "Security", "Security", 1, 170, 0, 1, nowMs),
            ReasonEntity("REAS_THEFT", "Theft / vandalism", "Security", "Security", 1, 180, 0, 1, nowMs),
            ReasonEntity("REAS_ACCESS_KEY", "Site access / key / landlord issue", "Access", "Access", 1, 190, 0, 1, nowMs),
            ReasonEntity("REAS_COMM_DISPUTE", "Commercial / sealing / rent dispute", "Commercial", "Commercial", 1, 200, 0, 1, nowMs),
            ReasonEntity("REAS_LOAD_SHED", "Load shedding (scheduled)", "Planned", "Power", 0, 210, 0, 1, nowMs),
            ReasonEntity("REAS_PLANNED_MAINT", "Planned maintenance", "Planned", "Power", 0, 220, 0, 1, nowMs),
            ReasonEntity("REAS_OTHER", "Other", "Other", "Other", 1, 230, 0, 1, nowMs),
            ReasonEntity("REAS_NO_RECORD", "No reason recorded", "Other", "Other", 1, 240, 0, 1, nowMs)
        )
    }
}
