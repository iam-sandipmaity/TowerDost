package app.telecom.sitetracker.util

import java.time.Instant

/**
 * Clock abstraction allowing deterministic time injection across queries,
 * repositories, ViewModels, and test suites.
 */
interface AppClock {
    fun currentTimeMillis(): Long
    fun nowInstant(): Instant = Instant.ofEpochMilli(currentTimeMillis())
}

/**
 * Default production clock backed by standard system time.
 */
class SystemAppClock : AppClock {
    override fun currentTimeMillis(): Long = System.currentTimeMillis()
}

/**
 * Test and simulation clock supporting arbitrary time jumps and progression.
 */
class FakeAppClock(private var currentMs: Long) : AppClock {
    override fun currentTimeMillis(): Long = currentMs

    fun setTime(newMs: Long) {
        currentMs = newMs
    }

    fun advanceBy(deltaMs: Long) {
        currentMs += deltaMs
    }
}
