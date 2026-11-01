package dev.shiwa.onearunbackend.service

import dev.shiwa.onearunbackend.model.tracking.LocationUpdate
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Acumula, em memória, a distância percorrida por cada jogador de cada sessão
 * a partir das coordenadas de GPS recebidas.
 */
@Service
class RaceTrackingService {

    private class RunnerTrack {
        var lastLatitude: Double? = null
        var lastLongitude: Double? = null
        var lastTimestamp: Long = 0
        var distanceMeters: Double = 0.0
    }

    // sessionId -> (runnerId -> track)
    private val tracks = ConcurrentHashMap<String, ConcurrentHashMap<String, RunnerTrack>>()

    /**
     * Registra uma nova coordenada e retorna a distância total acumulada do jogador.
     * Pontos imprecisos, fora de ordem ou com velocidade irreal são ignorados.
     */
    fun registerLocation(sessionId: String, runnerId: String, location: LocationUpdate): Double {
        require(location.latitude in -90.0..90.0) { "latitude inválida" }
        require(location.longitude in -180.0..180.0) { "longitude inválida" }

        val track = tracks
            .computeIfAbsent(sessionId) { ConcurrentHashMap() }
            .computeIfAbsent(runnerId) { RunnerTrack() }

        synchronized(track) {
            val timestamp = location.timestamp ?: System.currentTimeMillis()
            val accuracy = location.accuracy
            if (accuracy != null && accuracy > MAX_ACCURACY_METERS) return track.distanceMeters

            val lastLat = track.lastLatitude
            val lastLon = track.lastLongitude
            if (lastLat == null || lastLon == null) {
                track.anchor(location, timestamp)
                return track.distanceMeters
            }

            if (timestamp <= track.lastTimestamp) return track.distanceMeters

            val segment = haversineMeters(lastLat, lastLon, location.latitude, location.longitude)
            // Ignora pequenas oscilações do GPS mantendo o ponto de ancoragem
            if (segment < MIN_SEGMENT_METERS) return track.distanceMeters

            val seconds = (timestamp - track.lastTimestamp) / 1000.0
            if (segment / seconds > MAX_SPEED_METERS_PER_SECOND) return track.distanceMeters

            track.distanceMeters += segment
            track.anchor(location, timestamp)
            return track.distanceMeters
        }
    }

    fun getDistance(sessionId: String, runnerId: String?): Double {
        if (runnerId == null) return 0.0
        val track = tracks[sessionId]?.get(runnerId) ?: return 0.0
        return synchronized(track) { track.distanceMeters }
    }

    fun clearSession(sessionId: String) {
        tracks.remove(sessionId)
    }

    private fun RunnerTrack.anchor(location: LocationUpdate, timestamp: Long) {
        lastLatitude = location.latitude
        lastLongitude = location.longitude
        lastTimestamp = timestamp
    }

    companion object {
        private const val EARTH_RADIUS_METERS = 6_371_000.0
        const val MIN_SEGMENT_METERS = 2.0
        const val MAX_ACCURACY_METERS = 30.0
        // ~43 km/h: acima disso consideramos um salto de GPS
        const val MAX_SPEED_METERS_PER_SECOND = 12.0

        fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
            val dLat = Math.toRadians(lat2 - lat1)
            val dLon = Math.toRadians(lon2 - lon1)
            val a = sin(dLat / 2).pow(2) +
                    cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
            return 2 * EARTH_RADIUS_METERS * asin(sqrt(a))
        }
    }
}
