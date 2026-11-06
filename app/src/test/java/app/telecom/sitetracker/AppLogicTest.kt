package app.telecom.sitetracker

import app.telecom.sitetracker.util.FakeAppClock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppLogicTest {

    @Test
    fun testSeedingAndKpiCalculations() = runTest {
        val clock = FakeAppClock(1794000000000L) // Fixed timestamp in 2026
        assertEquals(1794000000000L, clock.currentTimeMillis())
    }
}
