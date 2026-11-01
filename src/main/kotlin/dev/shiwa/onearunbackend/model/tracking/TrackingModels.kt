package dev.shiwa.onearunbackend.model.tracking

import dev.shiwa.onearunbackend.model.session.SessionStatus

/**
 * Coordenada enviada pelo app do jogador em `race/{sessionId}/runner/{runnerId}/location`.
 *
 * Exemplo: `{"latitude": -23.5505, "longitude": -46.6333, "timestamp": 1730390000000, "accuracy": 5.0}`
 */
data class LocationUpdate(
    val latitude: Double,
    val longitude: Double,
    // epoch em milissegundos; se ausente, usa o horário do servidor
    val timestamp: Long? = null,
    // precisão horizontal em metros informada pelo GPS do dispositivo
    val accuracy: Double? = null
)

/**
 * Informações publicadas de volta ao jogador em `race/{sessionId}/runner/{runnerId}/progress`.
 */
data class RunnerProgress(
    val sessionId: String,
    val runnerId: String,
    val distanceMeters: Double,
    val opponentRunnerId: String?,
    val opponentDistanceMeters: Double,
    val targetDistanceMeters: Int,
    val remainingMeters: Double,
    val opponentRemainingMeters: Double,
    val finished: Boolean,
    val opponentFinished: Boolean,
    val sessionStatus: SessionStatus,
    val winnerRunnerId: String?,
    // null enquanto ninguém completou a distância
    val won: Boolean?
)

/**
 * Resultado da sessão publicado em `race/{sessionId}/result` quando o vencedor é definido
 * e novamente quando ambos completam a distância (status `FINISHED`).
 */
data class RaceResult(
    val sessionId: String,
    val sessionStatus: SessionStatus,
    val targetDistanceMeters: Int,
    val winnerRunnerId: String?,
    val hostRunnerId: String,
    val hostDistanceMeters: Double,
    val guestRunnerId: String?,
    val guestDistanceMeters: Double
)
