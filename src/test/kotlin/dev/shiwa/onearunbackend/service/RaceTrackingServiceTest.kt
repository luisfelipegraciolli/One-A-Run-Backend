package dev.shiwa.onearunbackend.service

import dev.shiwa.onearunbackend.model.tracking.LocationUpdate
import kotlin.test.Test
import kotlin.test.assertEquals

class RaceTrackingServiceTest {

    // ~0.0001 grau de latitude ≈ 11,12 m
    private val step = 0.0001
    private val stepMeters = RaceTrackingService.haversineMeters(0.0, 0.0, step, 0.0)

    @Test
    fun `haversine calcula distancia conhecida`() {
        // 1 grau de latitude ≈ 111,195 km
        assertEquals(111_195.0, RaceTrackingService.haversineMeters(0.0, 0.0, 1.0, 0.0), 1.0)
    }

    @Test
    fun `acumula distancia de cada jogador separadamente`() {
        val service = RaceTrackingService()
        repeat(4) { i ->
            service.registerLocation("s", "host", LocationUpdate(i * step, 0.0, i * 2_000L))
        }
        repeat(2) { i ->
            service.registerLocation("s", "guest", LocationUpdate(i * step, 0.0, i * 2_000L))
        }

        assertEquals(3 * stepMeters, service.getDistance("s", "host"), 0.01)
        assertEquals(stepMeters, service.getDistance("s", "guest"), 0.01)
        assertEquals(0.0, service.getDistance("other", "host"))
    }

    @Test
    fun `ignora ruido, baixa precisao, saltos e pontos fora de ordem`() {
        val service = RaceTrackingService()
        service.registerLocation("s", "r", LocationUpdate(0.0, 0.0, 0))
        // oscilação < 2 m
        service.registerLocation("s", "r", LocationUpdate(0.00001, 0.0, 1_000))
        // baixa precisão
        service.registerLocation("s", "r", LocationUpdate(step, 0.0, 2_000, accuracy = 50.0))
        // salto irreal (~1,1 km em 1 s)
        service.registerLocation("s", "r", LocationUpdate(0.01, 0.0, 3_000))
        // fora de ordem
        service.registerLocation("s", "r", LocationUpdate(step, 0.0, 0))
        assertEquals(0.0, service.getDistance("s", "r"))

        service.registerLocation("s", "r", LocationUpdate(step, 0.0, 4_000))
        assertEquals(stepMeters, service.getDistance("s", "r"), 0.01)
    }
}
