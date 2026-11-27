package com.arete.app.alerts

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class AlertSchedulerTest {
    private val jalisco = ZoneId.of("America/Mexico_City")
    private val seven = LocalTime.of(7, 0)

    private fun at(hour: Int, minute: Int) = ZonedDateTime.of(2026, 11, 26, hour, minute, 0, 0, jalisco)

    @Test fun `de madrugada espera hasta las 7 del mismo dia`() {
        assertEquals(Duration.ofHours(4).plusMinutes(30), AlertScheduler.delayUntilNext(seven, at(2, 30)))
    }

    @Test fun `despues de las 7 espera hasta las 7 del dia siguiente`() {
        assertEquals(Duration.ofHours(15), AlertScheduler.delayUntilNext(seven, at(16, 0)))
    }

    @Test fun `exactamente a las 7 programa para el dia siguiente`() {
        assertEquals(Duration.ofDays(1), AlertScheduler.delayUntilNext(seven, at(7, 0)))
    }
}
