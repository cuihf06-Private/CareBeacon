package com.carebeacon.app.alarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.GregorianCalendar
import java.util.TimeZone

/**
 * Unit tests for [TriggerCalculator]. The class is pure: no Android dependencies,
 * runs on the JVM under `./gradlew :app:testDebugUnitTest`.
 */
class TriggerCalculatorTest {

    private val utc = TimeZone.getTimeZone("UTC")

    /** Helper: build epoch-millis for a specific UTC wall-clock instant. */
    private fun utc(y: Int, m: Int, d: Int, h: Int, mi: Int): Long {
        val cal = GregorianCalendar(utc).apply {
            clear()
            set(y, m - 1, d, h, mi, 0)
        }
        return cal.timeInMillis
    }

    @Test
    fun `one-shot fires later today when target is in the future`() {
        val now = utc(2026, 7, 25, 7, 0)
        val next = TriggerCalculator.nextTrigger(now, hour = 9, minute = 30, weekMask = 0, tz = utc)
        assertEquals(utc(2026, 7, 25, 9, 30), next)
    }

    @Test
    fun `one-shot fires tomorrow when target has already passed`() {
        val now = utc(2026, 7, 25, 10, 0)
        val next = TriggerCalculator.nextTrigger(now, hour = 9, minute = 30, weekMask = 0, tz = utc)
        assertEquals(utc(2026, 7, 26, 9, 30), next)
    }

    @Test
    fun `one-shot fires tomorrow when target equals now`() {
        // Boundary: trigger == now is "in the past or present", so push to tomorrow.
        val now = utc(2026, 7, 25, 9, 30)
        val next = TriggerCalculator.nextTrigger(now, hour = 9, minute = 30, weekMask = 0, tz = utc)
        assertEquals(utc(2026, 7, 26, 9, 30), next)
    }

    @Test
    fun `repeating skips today if today's time has passed and today is in the mask`() {
        // 2026-07-25 is a Saturday (bit 5). At 10:00 we want Saturday 9:30 — past.
        val now = utc(2026, 7, 25, 10, 0) // Saturday
        val mask = 1 shl 5 // Saturday only
        val next = TriggerCalculator.nextTrigger(now, hour = 9, minute = 30, weekMask = mask, tz = utc)
        assertEquals(utc(2026, 8, 1, 9, 30), next) // next Saturday
    }

    @Test
    fun `repeating picks today when today's time is still in the future`() {
        // Saturday 2026-07-25 at 06:00, want Saturday 09:30 — still today.
        val now = utc(2026, 7, 25, 6, 0)
        val mask = 1 shl 5 // Saturday only
        val next = TriggerCalculator.nextTrigger(now, hour = 9, minute = 30, weekMask = mask, tz = utc)
        assertEquals(utc(2026, 7, 25, 9, 30), next)
    }

    @Test
    fun `repeating walks forward to find next matching weekday`() {
        // Sunday 2026-07-26 at 06:00, only Monday is enabled → next Monday is 07-27.
        val now = utc(2026, 7, 26, 6, 0) // Sunday
        val mask = 1 shl 0 // Monday only
        val next = TriggerCalculator.nextTrigger(now, hour = 8, minute = 0, weekMask = mask, tz = utc)
        assertEquals(utc(2026, 7, 27, 8, 0), next)
    }

    @Test
    fun `repeating with multi-day mask picks soonest matching day strictly after now`() {
        // Wednesday 2026-07-22 at 23:00. Mask = Mon | Wed | Fri (bits 0, 2, 4).
        // Wednesday 22:00 has passed; next candidate is Friday 2026-07-24 22:00.
        val now = utc(2026, 7, 22, 23, 0)
        val mask = (1 shl 0) or (1 shl 2) or (1 shl 4)
        val next = TriggerCalculator.nextTrigger(now, hour = 22, minute = 0, weekMask = mask, tz = utc)
        assertEquals(utc(2026, 7, 24, 22, 0), next)
    }

    @Test
    fun `every day of week resolves within the 8 day window`() {
        // For every weekday bit, the function must return a strictly future instant.
        val now = utc(2026, 7, 25, 6, 0) // Saturday morning
        for (bit in 0..6) {
            val mask = 1 shl bit
            val next = TriggerCalculator.nextTrigger(now, hour = 9, minute = 0, weekMask = mask, tz = utc)
            assertTrue("bit=$bit produced non-future trigger", next > now)
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects hour out of range`() {
        TriggerCalculator.nextTrigger(0L, hour = 24, minute = 0, weekMask = 0, tz = utc)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects minute out of range`() {
        TriggerCalculator.nextTrigger(0L, hour = 0, minute = 60, weekMask = 0, tz = utc)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects weekMask with bits beyond bit 6`() {
        TriggerCalculator.nextTrigger(0L, hour = 0, minute = 0, weekMask = 1 shl 7, tz = utc)
    }
}