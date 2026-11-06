package app.telecom.sitetracker

import app.telecom.sitetracker.util.Geo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GeoTest {

    @Test
    fun testHaversineDistance() {
        // Kolkata (22.5726, 88.3639) to Kharagpur (22.3452, 87.3232) is ~110-115 km
        val distance = Geo.haversineDistanceKm(22.5726, 88.3639, 22.3452, 87.3232)
        assertTrue("Distance should be between 105 and 120 km: $distance", distance in 105.0..120.0)
    }

    @Test
    fun testBearingAndCompass() {
        assertEquals("N", Geo.bearingToCompass(0.0))
        assertEquals("N", Geo.bearingToCompass(355.0))
        assertEquals("NE", Geo.bearingToCompass(45.0))
        assertEquals("E", Geo.bearingToCompass(90.0))
        assertEquals("SE", Geo.bearingToCompass(135.0))
        assertEquals("S", Geo.bearingToCompass(180.0))
        assertEquals("SW", Geo.bearingToCompass(225.0))
        assertEquals("W", Geo.bearingToCompass(270.0))
        assertEquals("NW", Geo.bearingToCompass(315.0))
    }

    @Test
    fun testFormatDistanceAndBearing() {
        val formatted = Geo.formatDistanceAndBearing(22.5726, 88.3639, 22.3452, 87.3232)
        assertTrue(formatted.contains("km"))
        assertTrue(formatted.contains("W") || formatted.contains("SW"))
    }

    @Test
    fun testIndiaBoundingBox() {
        // Kolkata, Kharagpur, Delhi, Mumbai, Chennai, Bengaluru are within India
        assertTrue(Geo.isWithinIndia(22.5726, 88.3639)) // Kolkata
        assertTrue(Geo.isWithinIndia(28.6139, 77.2090)) // Delhi
        assertTrue(Geo.isWithinIndia(19.0760, 72.8777)) // Mumbai
        assertTrue(Geo.isWithinIndia(13.0827, 80.2707)) // Chennai

        // London, New York, Tokyo are outside India
        assertFalse(Geo.isWithinIndia(51.5074, -0.1278))
        assertFalse(Geo.isWithinIndia(40.7128, -74.0060))
        assertFalse(Geo.isWithinIndia(null, 88.3639))
    }

    @Test
    fun testCoordinateParsing() {
        // Standard decimal with dot
        assertEquals(22.5726, Geo.parseCoordinate("22.5726")!!, 0.0001)

        // Decimal with European comma
        assertEquals(22.5726, Geo.parseCoordinate("22,5726")!!, 0.0001)

        // Decimal with directional suffix
        assertEquals(-22.5726, Geo.parseCoordinate("22.5726 S")!!, 0.0001)
        assertEquals(88.3639, Geo.parseCoordinate("88.3639 E")!!, 0.0001)

        // DMS format: 22°34'21.4"N
        val dmsVal = Geo.parseCoordinate("22°34'21.4\"N")
        assertNotNull(dmsVal)
        val expected = 22.0 + (34.0 / 60.0) + (21.4 / 3600.0)
        assertEquals(expected, dmsVal!!, 0.001)

        // DMS with spaces
        val dmsSpaces = Geo.parseCoordinate("87 20 19.5 E")
        assertNotNull(dmsSpaces)
        val expectedLng = 87.0 + (20.0 / 60.0) + (19.5 / 3600.0)
        assertEquals(expectedLng, dmsSpaces!!, 0.001)

        // Blank or invalid
        assertNull(Geo.parseCoordinate(""))
        assertNull(Geo.parseCoordinate("ABC"))
    }

    @Test
    fun testCoordinatePairParsing() {
        val pair1 = Geo.parseCoordinatePair("22.06887, 87.33884")
        assertNotNull(pair1)
        assertEquals(22.06887, pair1!!.first, 0.0001)
        assertEquals(87.33884, pair1.second, 0.0001)

        // Inverted input (lng, lat) should be normalized to (lat, lng) within India bounds
        val pairInverted = Geo.parseCoordinatePair("87.33884, 22.06887")
        assertNotNull(pairInverted)
        assertEquals(22.06887, pairInverted!!.first, 0.0001)
        assertEquals(87.33884, pairInverted.second, 0.0001)
    }
}
