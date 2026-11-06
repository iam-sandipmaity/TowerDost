package app.telecom.sitetracker.data.seed

import app.telecom.sitetracker.data.db.AppDatabase
import app.telecom.sitetracker.data.entity.IncidentEntity
import app.telecom.sitetracker.data.entity.IncidentHistoryEntity
import app.telecom.sitetracker.data.entity.ReasonEntity
import app.telecom.sitetracker.data.entity.SiteEntity
import app.telecom.sitetracker.data.entity.SiteFieldEntity
import app.telecom.sitetracker.util.AppClock
import app.telecom.sitetracker.util.Dates
import java.util.UUID

/**
 * Seeds standard root cause catalogue and 40 fictional telecom sites with ~90 realistic incidents.
 * Fictional data only; no real operator identifiers or customer credentials.
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

    suspend fun seedDatabase(database: AppDatabase, clock: AppClock) {
        val now = clock.currentTimeMillis()
        val siteDao = database.siteDao()
        val fieldDao = database.fieldDao()
        val reasonDao = database.reasonDao()
        val incidentDao = database.incidentDao()

        // 1. Seed Reasons
        val reasons = getDefaultReasons(now)
        reasonDao.insertReasons(reasons)

        // 2. Seed 40 Fictional Telecom Sites across 5 Indian Circles
        val circlesData = listOf(
            CircleTemplate("Delhi", "North", "Delhi", "New Delhi", listOf(
                Pair("DEL-9001", "Connaught Place Hub"),
                Pair("DEL-9002", "Nehru Place Exchange"),
                Pair("DEL-9003", "Dwarka Sector 10"),
                Pair("DEL-9004", "Rohini Sector 14"),
                Pair("DEL-9005", "Saket District Centre"),
                Pair("DEL-9006", "Karol Bagh Market"),
                Pair("DEL-9007", "Mayur Vihar Phase 1"),
                Pair("DEL-9008", "Okhla Industrial Area")
            ), 28.6139, 77.2090),
            CircleTemplate("Mumbai", "West", "Maharashtra", "Mumbai City", listOf(
                Pair("MUM-9009", "Nariman Point Tower"),
                Pair("MUM-9010", "BKC G-Block Node"),
                Pair("MUM-9011", "Andheri East MIDC"),
                Pair("MUM-9012", "Borivali West Junction"),
                Pair("MUM-9013", "Dadar Central Office"),
                Pair("MUM-9014", "Powai Hiranandani"),
                Pair("MUM-9015", "Vashi Sector 17"),
                Pair("MUM-9016", "Thane Ghodbunder Road")
            ), 19.0760, 72.8777),
            CircleTemplate("Karnataka", "South", "Karnataka", "Bengaluru Urban", listOf(
                Pair("BLR-9017", "MG Road Telecom Centre"),
                Pair("BLR-9018", "Electronic City Phase 1"),
                Pair("BLR-9019", "Whitefield ITPL"),
                Pair("BLR-9020", "Indiranagar 100ft Rd"),
                Pair("BLR-9021", "Koramangala 5th Block"),
                Pair("BLR-9022", "Hebbal Flyover Site"),
                Pair("BLR-9023", "Jayanagar 4th T Block"),
                Pair("BLR-9024", "Yelahanka New Town")
            ), 12.9716, 77.5946),
            CircleTemplate("Tamil Nadu", "South", "Tamil Nadu", "Chennai", listOf(
                Pair("CHE-9025", "Anna Salai Central"),
                Pair("CHE-9026", "T Nagar Panagal Park"),
                Pair("CHE-9027", "OMR Thoraipakkam"),
                Pair("CHE-9028", "Guindy Industrial Estate"),
                Pair("CHE-9029", "Velachery Main Road"),
                Pair("CHE-9030", "Adyar Signal Junction"),
                Pair("CHE-9031", "Ambattur OT Node"),
                Pair("CHE-9032", "Porur Junction")
            ), 13.0827, 80.2707),
            CircleTemplate("West Bengal", "East", "West Bengal", "Kolkata", listOf(
                Pair("KOL-9033", "Park Street Hub"),
                Pair("KOL-9034", "Salt Lake Sector V"),
                Pair("KOL-9035", "New Town Action Area 1"),
                Pair("KOL-9036", "Howrah Station Approach"),
                Pair("KOL-9037", "Gariahat Crossing"),
                Pair("KOL-9038", "Shyambazar Five Point"),
                Pair("KOL-9039", "Behala Chowrasta"),
                Pair("KOL-9040", "Rajarhat Expressway")
            ), 22.5726, 88.3639)
        )

        val siteEntities = mutableListOf<SiteEntity>()
        val fieldEntities = mutableListOf<SiteFieldEntity>()

        var siteIndex = 0
        circlesData.forEach { circle ->
            circle.sites.forEachIndexed { idx, (sapId, name) ->
                val latOffset = (idx % 4 - 1.5) * 0.04
                val lngOffset = (idx / 4 - 0.5) * 0.04
                val lat = circle.centerLat + latOffset
                val lng = circle.centerLng + lngOffset

                val tech = if (idx % 3 == 0) "4G/5G" else if (idx % 3 == 1) "4G" else "2G/4G"
                val siteType = if (idx % 2 == 0) "GBM (Ground Based Mast)" else "RTT (Roof Top Tower)"

                val site = SiteEntity(
                    sapId = sapId,
                    sapNorm = SiteEntity.normalize(sapId),
                    name = name,
                    lat = lat,
                    lng = lng,
                    circle = circle.circle,
                    region = circle.region,
                    state = circle.state,
                    district = circle.district,
                    city = circle.district,
                    technology = tech,
                    siteType = siteType,
                    status = "Active",
                    address = "$name, ${circle.district}, ${circle.state}",
                    tags = "Anchor,Priority-A",
                    notes = "Core aggregation site. Landlord key with security 24x7.",
                    sourceFile = "sample_telecom_master.xlsx",
                    createdAt = now - (90L * Dates.MS_PER_DAY),
                    updatedAt = now - (idx * 3600000L),
                    deletedAt = null
                )
                siteEntities.add(site)

                // Detailed equipment specs for each site
                val specs = listOf(
                    Triple("SMPS Make", if (idx % 2 == 0) "Delta 48V 100A" else "Exicom 48V 150A", "Power"),
                    Triple("Rectifier Module Rating", "48V / 50A High Efficiency", "Power"),
                    Triple("Installed RM Count", "${3 + (idx % 3)} Modules", "Power"),
                    Triple("Battery Bank Capacity", "${300 + (idx % 3) * 100} Ah VRLA", "Battery"),
                    Triple("Battery Backup Hours", "4.5 hours on standard load", "Battery"),
                    Triple("DG Rating kVA", "${15 + (idx % 4) * 5} kVA Silent CPCB-II", "DG"),
                    Triple("DG Fuel Tank", "100 Litres Diesel", "DG"),
                    Triple("Tower Height", "${30 + (idx % 3) * 10} Metres", "Civil"),
                    Triple("AC Units", "2 x 1.5 Ton Inverter Precision", "Electrical"),
                    Triple("EB Sanctioned Load", "15 kW Commercial Tariff", "Commercial"),
                    Triple("MW Antenna Spec", "0.6m High Performance 18GHz", "Transmission")
                )

                specs.forEachIndexed { order, (k, v, grp) ->
                    fieldEntities.add(
                        SiteFieldEntity(
                            sapId = sapId,
                            fieldKey = k,
                            fieldValue = v,
                            groupName = grp,
                            sortOrder = order + 1
                        )
                    )
                }
                siteIndex++
            }
        }

        siteDao.insertSites(siteEntities)
        fieldDao.insertFields(fieldEntities)

        // 3. Seed Realistic Incidents (~90 total)
        val incidents = mutableListOf<IncidentEntity>()
        val histories = mutableListOf<IncidentHistoryEntity>()

        // Specific Test Fixture 1: Site DEL-9001 has 3 Power incidents in last 60 days
        // (Satisfies Acceptance Criterion 6: repeat-fault callout for same category >= 2 in 90 days)
        val del9001Times = listOf(
            now - (50L * Dates.MS_PER_DAY),
            now - (25L * Dates.MS_PER_DAY),
            now - (5L * Dates.MS_PER_DAY)
        )
        del9001Times.forEachIndexed { i, downTime ->
            val incId = "INC-DEL-PWR-$i"
            incidents.add(
                IncidentEntity(
                    id = incId,
                    sapId = "DEL-9001",
                    occurredAt = downTime,
                    category = "Power",
                    reasonId = "REAS_EB_GRID",
                    severity = "Major",
                    status = "Resolved",
                    isOutage = 1,
                    downAt = downTime,
                    restoredAt = downTime + (4 * 3600000L),
                    title = "Grid Power Supply Failure - Phase Dropped",
                    description = "Transformer phase down in local area. DG ran for 4h.",
                    actionTaken = "EB feeder restored by state discom.",
                    engineer = "Ramesh Kumar",
                    createdAt = downTime,
                    updatedAt = downTime + (4 * 3600000L)
                )
            )
        }

        // Specific Test Fixture 2: One 5-day outage that started 7 days ago and restored 2 days ago
        // (Satisfies Acceptance Criterion 18: span overlap across 5 days)
        val fiveDayStart = Dates.getDayStartEpochMs(now - (7L * Dates.MS_PER_DAY)) + (10 * 3600000L) // 10:00 AM on day -7
        val fiveDayEnd = fiveDayStart + (5L * Dates.MS_PER_DAY) + (4 * 3600000L)                      // 2:00 PM on day -2
        incidents.add(
            IncidentEntity(
                id = "INC-MUM-5DAY-OUTAGE",
                sapId = "MUM-9009",
                occurredAt = fiveDayStart,
                category = "DG",
                reasonId = "REAS_DG_REPAIR",
                severity = "Critical",
                status = "Resolved",
                isOutage = 1,
                downAt = fiveDayStart,
                restoredAt = fiveDayEnd,
                title = "DG Major Alternator Failure with Grid Transformer Fault",
                description = "Alternator burnt during extended grid outage. Spare part sourced from Pune.",
                actionTaken = "Alternator replaced, DG load test passed.",
                engineer = "Amit Shah",
                createdAt = fiveDayStart,
                updatedAt = fiveDayEnd
            )
        )

        // Specific Test Fixture 3: A currently open outage (restoredAt = null)
        // (Satisfies Acceptance Criterion 19: still down counter)
        val openOutageDownAt = Dates.getDayStartEpochMs(now - (3L * Dates.MS_PER_DAY)) + (8 * 3600000L)
        val openIncidentId = "INC-BLR-OPEN-OUTAGE"
        incidents.add(
            IncidentEntity(
                id = openIncidentId,
                sapId = "BLR-9017",
                occurredAt = openOutageDownAt,
                category = "Rectifier",
                reasonId = "REAS_RM_MODULE",
                severity = "Critical",
                status = "Open",
                isOutage = 1,
                downAt = openOutageDownAt,
                restoredAt = null,
                title = "Rectifier module (RM) failure - All 3 Modules Tripped",
                description = "High surge caused rectifier failure. Battery drained to LVD cut-off.",
                actionTaken = "Spare RM dispatched from central warehouse.",
                engineer = "Suresh Raina",
                createdAt = openOutageDownAt,
                updatedAt = now - 3600000L
            )
        )
        histories.add(
            IncidentHistoryEntity(
                id = UUID.randomUUID().toString(),
                incidentId = openIncidentId,
                changedAt = openOutageDownAt + 7200000L,
                changedBy = "Suresh Raina",
                field = "status",
                oldValue = "Open",
                newValue = "In Progress"
            )
        )

        // Specific Test Fixture 4: A planned maintenance outage (counts_toward_downtime = 0)
        // (Satisfies Acceptance Criterion 28: appears in register, 0 downtime contribution)
        val plannedStart = now - (12L * Dates.MS_PER_DAY)
        incidents.add(
            IncidentEntity(
                id = "INC-CHE-PLANNED-MAINT",
                sapId = "CHE-9025",
                occurredAt = plannedStart,
                category = "Power",
                reasonId = "REAS_PLANNED_MAINT",
                severity = "Minor",
                status = "Closed",
                isOutage = 1,
                downAt = plannedStart,
                restoredAt = plannedStart + (3 * 3600000L),
                title = "Scheduled Discom Substation Line Maintenance",
                description = "Annual grid transformer overhaul by electricity board.",
                actionTaken = "Planned shutdown executed safely; site restored.",
                engineer = "Venkatesh Iyer",
                createdAt = plannedStart,
                updatedAt = plannedStart + (3 * 3600000L)
            )
        )

        // Specific Test Fixture 5: Midnight crossing outage (e.g. 22:00 to 03:00)
        val yesterdayMidnight = Dates.getDayStartEpochMs(now - Dates.MS_PER_DAY)
        val midnightStart = yesterdayMidnight - (2 * 3600000L) // 22:00 day before yesterday
        val midnightEnd = yesterdayMidnight + (3 * 3600000L)   // 03:00 yesterday
        incidents.add(
            IncidentEntity(
                id = "INC-KOL-MIDNIGHT-CROSS",
                sapId = "KOL-9033",
                occurredAt = midnightStart,
                category = "OFC",
                reasonId = "REAS_OFC_CUT",
                severity = "Major",
                status = "Resolved",
                isOutage = 1,
                downAt = midnightStart,
                restoredAt = midnightEnd,
                title = "Road Widening Fibre Cut on Trunk Highway",
                description = "24-fibre cable cut during excavating works.",
                actionTaken = "Fibre spliced and OTDR tested.",
                engineer = "Subhash Bose",
                createdAt = midnightStart,
                updatedAt = midnightEnd
            )
        )

        // Remaining incidents to bring total to ~90 realistic entries
        val categories = listOf("Power", "Rectifier", "DG", "Battery", "OFC", "Transmission", "BTS-RAN", "AC")
        val reasonMap = mapOf(
            "Power" to "REAS_EB_GRID",
            "Rectifier" to "REAS_RM_MODULE",
            "DG" to "REAS_DG_AUTO_FAIL",
            "Battery" to "REAS_BATT_LOW",
            "OFC" to "REAS_OFC_CUT",
            "Transmission" to "REAS_MW_DOWN",
            "BTS-RAN" to "REAS_BTS_HW",
            "AC" to "REAS_AC_COOLING"
        )

        val engineers = listOf("Rahul Sharma", "Vikram Sen", "Priya Nair", "Manish Tiwari", "Anand Patel")

        val targetCount = 90
        val createdSoFar = incidents.size
        for (i in 0 until (targetCount - createdSoFar)) {
            val site = siteEntities[i % siteEntities.size]
            val cat = categories[i % categories.size]
            val reas = reasonMap[cat] ?: "REAS_OTHER"
            val daysAgo = ((i * 13) % 85) + 1
            val occurred = now - (daysAgo * Dates.MS_PER_DAY) - ((i % 12) * 3600000L)

            val isOutage = if (i % 5 == 0) 0 else 1
            val downAt = if (isOutage == 1) occurred else null
            val durationHours = 1 + (i % 6) * 1.5
            val restoredAt = if (isOutage == 1) (occurred + (durationHours * 3600000L).toLong()) else null

            val severity = when (i % 4) {
                0 -> "Critical"
                1 -> "Major"
                2 -> "Minor"
                else -> "Info"
            }

            val status = if (restoredAt != null) "Resolved" else "Open"
            val eng = engineers[i % engineers.size]

            incidents.add(
                IncidentEntity(
                    id = "INC-AUTO-${1000 + i}",
                    sapId = site.sapId,
                    occurredAt = occurred,
                    category = cat,
                    reasonId = reas,
                    severity = severity,
                    status = status,
                    isOutage = isOutage,
                    downAt = downAt,
                    restoredAt = restoredAt,
                    title = "$cat Fault - ${site.name}",
                    description = "Automated fault capture and routine diagnostic on equipment spec.",
                    actionTaken = if (restoredAt != null) "Field inspection complete, normal parameters verified." else null,
                    engineer = eng,
                    createdAt = occurred,
                    updatedAt = restoredAt ?: occurred
                )
            )
        }

        incidentDao.insertIncidents(incidents)
        incidentDao.insertHistories(histories)
    }

    private data class CircleTemplate(
        val circle: String,
        val region: String,
        val state: String,
        val district: String,
        val sites: List<Pair<String, String>>,
        val centerLat: Double,
        val centerLng: Double
    )
}
