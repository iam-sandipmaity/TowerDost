package app.telecom.sitetracker.util

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationManager
import java.util.Locale
import java.util.regex.Pattern
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Geo utilities without Google Play Services dependencies.
 * Uses Android LocationManager, Haversine formula, and robust coordinate parsing.
 */
object Geo {

    private const val EARTH_RADIUS_KM = 6371.0

    // India geographic bounding box
    const val MIN_INDIA_LAT = 6.0
    const val MAX_INDIA_LAT = 38.0
    const val MIN_INDIA_LNG = 68.0
    const val MAX_INDIA_LNG = 98.0

    private val COMPASS_POINTS = arrayOf(
        "N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE",
        "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW"
    )

    /**
     * Calculates great-circle distance between two points on the Earth's surface in kilometers.
     */
    fun haversineDistanceKm(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val rLat1 = Math.toRadians(lat1)
        val rLat2 = Math.toRadians(lat2)

        val a = sin(dLat / 2).pow(2.0) +
                cos(rLat1) * cos(rLat2) * sin(dLng / 2).pow(2.0)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return EARTH_RADIUS_KM * c
    }

    /**
     * Calculates the initial bearing (forward azimuth) from point 1 to point 2 in degrees (0..360).
     */
    fun bearingDegrees(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val rLat1 = Math.toRadians(lat1)
        val rLat2 = Math.toRadians(lat2)
        val dLng = Math.toRadians(lng2 - lng1)

        val y = sin(dLng) * cos(rLat2)
        val x = cos(rLat1) * sin(rLat2) - sin(rLat1) * cos(rLat2) * cos(dLng)
        val radians = atan2(y, x)
        val degrees = Math.toDegrees(radians)
        return (degrees + 360.0) % 360.0
    }

    /**
     * Converts a 0..360 degree bearing to a 16-wind compass abbreviation (e.g., "SW", "NNE").
     */
    fun bearingToCompass(degrees: Double): String {
        val normalized = (degrees % 360.0 + 360.0) % 360.0
        val index = ((normalized + 11.25) / 22.5).toInt() % 16
        return COMPASS_POINTS[index]
    }

    /**
     * Formats distance and bearing relative to a reference coordinate.
     * Example: "1.4 km SW" or "450 m NE"
     */
    fun formatDistanceAndBearing(fromLat: Double, fromLng: Double, toLat: Double, toLng: Double): String {
        val distKm = haversineDistanceKm(fromLat, fromLng, toLat, toLng)
        val bearing = bearingDegrees(fromLat, fromLng, toLat, toLng)
        val compass = bearingToCompass(bearing)

        return if (distKm < 1.0) {
            val meters = (distKm * 1000).roundToInt()
            "${meters} m $compass"
        } else {
            String.format(Locale.US, "%.1f km %s", distKm, compass)
        }
    }

    /**
     * Checks if a coordinate falls within the Indian territory bounding box.
     */
    fun isWithinIndia(lat: Double?, lng: Double?): Boolean {
        if (lat == null || lng == null) return false
        return lat in MIN_INDIA_LAT..MAX_INDIA_LAT && lng in MIN_INDIA_LNG..MAX_INDIA_LNG
    }

    /**
     * Parses a single coordinate string (decimal with . or ,, DMS format, with direction signs).
     */
    fun parseCoordinate(raw: String?): Double? {
        if (raw.isNullOrBlank()) return null
        val trimmed = raw.trim()

        // 1. Try standard decimal (replace European decimal comma if present)
        val decimalMatch = Pattern.compile("^([+-]?\\d+(?:[.,]\\d+)?)\\s*([NSEWnsew])?$").matcher(trimmed)
        if (decimalMatch.matches()) {
            val numStr = decimalMatch.group(1)?.replace(',', '.') ?: return null
            val value = numStr.toDoubleOrNull() ?: return null
            val dir = decimalMatch.group(2)?.uppercase(Locale.US)
            return if (dir == "S" || dir == "W") -Math.abs(value) else value
        }

        // 2. Try DMS format: e.g. 22°34'21.4"N or 22 34 21.4 N or 22:34:21.4N
        val dmsPattern = Pattern.compile("""(\d+)[°\s:]+(\d+)['\s:]+([\d.]+)["]?\s*([NSEWnsew])?""")
        val dmsMatch = dmsPattern.matcher(trimmed)
        if (dmsMatch.find()) {
            val deg = dmsMatch.group(1)?.toDoubleOrNull() ?: return null
            val min = dmsMatch.group(2)?.toDoubleOrNull() ?: return null
            val sec = dmsMatch.group(3)?.toDoubleOrNull() ?: return null
            val dir = dmsMatch.group(4)?.uppercase(Locale.US)

            var dec = deg + (min / 60.0) + (sec / 3600.0)
            if (dir == "S" || dir == "W") {
                dec = -dec
            }
            return dec
        }

        return null
    }

    /**
     * Detects if an arbitrary search string contains a pair of coordinates.
     * E.g. "22.06887, 87.33884" or "22°04'08\"N, 87°20'20\"E"
     */
    fun parseCoordinatePair(raw: String): Pair<Double, Double>? {
        val parts = if (raw.contains(",")) {
            raw.split(",")
        } else if (raw.contains("/")) {
            raw.split("/")
        } else if (raw.contains(";")) {
            raw.split(";")
        } else {
            // Check space separated if both contain degree/direction or 2 numbers
            val tokens = raw.trim().split("\\s+".toRegex())
            if (tokens.size == 2) tokens else return null
        }

        if (parts.size != 2) return null
        val lat = parseCoordinate(parts[0]) ?: return null
        val lng = parseCoordinate(parts[1]) ?: return null

        // If user entered lng first (lng > 60 in India, lat < 40 in India), flip if needed
        return if (lat in MIN_INDIA_LNG..MAX_INDIA_LNG && lng in MIN_INDIA_LAT..MAX_INDIA_LAT) {
            Pair(lng, lat)
        } else {
            Pair(lat, lng)
        }
    }

    /**
     * Obtains the last known location using LocationManager (GPS -> Network).
     * Does not require Google Play Services.
     */
    @SuppressLint("MissingPermission")
    fun getLastKnownLocation(context: Context): Location? {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return null

        return try {
            val gpsLoc = if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            } else null

            val netLoc = if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            } else null

            when {
                gpsLoc != null && netLoc != null -> {
                    if (gpsLoc.time >= netLoc.time) gpsLoc else netLoc
                }
                gpsLoc != null -> gpsLoc
                else -> netLoc
            }
        } catch (_: SecurityException) {
            null
        }
    }
}
