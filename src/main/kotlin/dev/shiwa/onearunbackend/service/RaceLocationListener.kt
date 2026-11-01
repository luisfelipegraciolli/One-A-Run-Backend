package dev.shiwa.onearunbackend.service

import dev.shiwa.onearunbackend.model.session.RaceSession
import dev.shiwa.onearunbackend.model.session.SessionStatus
import dev.shiwa.onearunbackend.model.tracking.RaceResult
import dev.shiwa.onearunbackend.model.tracking.LocationUpdate
import dev.shiwa.onearunbackend.model.tracking.RunnerProgress
import dev.shiwa.onearunbackend.repository.RaceSessionRepository
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.time.Clock

/**
 * Recebe as coordenadas dos jogadores em `race/{sessionId}/runner/{runnerId}/location`
 * e publica o progresso de cada um em `race/{sessionId}/runner/{runnerId}/progress`.
 */
@Component
class RaceLocationListener(
    private val mqttService: MqttService,
    private val trackingService: RaceTrackingService,
    private val raceRepository: RaceSessionRepository,
    private val jsonMapper: JsonMapper
) {

    // Garante que mensagens simultâneas dos dois jogadores não decidam o vencedor ao mesmo tempo
    private val sessionLocks = ConcurrentHashMap<String, Any>()

    @EventListener(ApplicationReadyEvent::class)
    fun subscribeToLocations() {
        mqttService.subscribe(LOCATION_TOPIC_FILTER) { topic, payload -> handleMessage(topic, payload) }
            .whenComplete { _, throwable ->
                if (throwable != null) {
                    System.err.println("Failed to subscribe to $LOCATION_TOPIC_FILTER: ${throwable.message}. Retrying...")
                    CompletableFuture.delayedExecutor(RETRY_DELAY_SECONDS, TimeUnit.SECONDS)
                        .execute { subscribeToLocations() }
                } else {
                    println("Successfully subscribed to topic: $LOCATION_TOPIC_FILTER")
                }
            }
    }

    fun handleMessage(topic: String, payload: String) {
        try {
            val (sessionId, runnerId) = parseTopic(topic) ?: return
            val lock = sessionLocks.computeIfAbsent(sessionId) { Any() }
            synchronized(lock) { processLocation(sessionId, runnerId, payload) }
        } catch (e: Exception) {
            System.err.println("Invalid location message on $topic: ${e.message}")
        }
    }

    private fun processLocation(sessionId: String, runnerId: String, payload: String) {
        val session = raceRepository.findById(sessionId).orElse(null) ?: return
        // A corrida só acontece com os dois jogadores e enquanto não foi encerrada
        if (session.status != SessionStatus.READY && session.status != SessionStatus.RUNNING) return
        val guestId = session.guestRunnerId ?: return
        if (runnerId != session.hostRunnerId && runnerId != guestId) return

        val location = jsonMapper.readValue(payload, LocationUpdate::class.java)
        val target = session.distanceMeters
        // Quem já completou a distância tem o percurso congelado
        if (trackingService.getDistance(sessionId, runnerId) < target) {
            trackingService.registerLocation(sessionId, runnerId, location)
        }

        val previousStatus = session.status
        val previousWinner = session.winnerRunnerId
        updateOutcome(session, runnerId)
        if (session.status != previousStatus || session.winnerRunnerId != previousWinner) {
            raceRepository.save(session)
        }

        // Atualiza os dois jogadores: o próprio e o adversário
        publishProgress(session, session.hostRunnerId)
        publishProgress(session, guestId)

        if (session.winnerRunnerId != previousWinner || session.status == SessionStatus.FINISHED) {
            publishResult(session)
        }
        if (session.status == SessionStatus.FINISHED) {
            trackingService.clearSession(sessionId)
            sessionLocks.remove(sessionId)
        }
    }

    /**
     * Inicia a corrida, define o vencedor (primeiro a completar a distância) e
     * encerra a sessão quando ambos os jogadores completam a distância.
     */
    private fun updateOutcome(session: RaceSession, runnerId: String) {
        if (session.status == SessionStatus.READY) session.status = SessionStatus.RUNNING

        val target = session.distanceMeters
        val hostDistance = trackingService.getDistance(session.id, session.hostRunnerId)
        val guestDistance = trackingService.getDistance(session.id, session.guestRunnerId)
        val hostFinished = hostDistance >= target
        val guestFinished = guestDistance >= target

        // Só a localização atual pode ter feito alguém cruzar a meta agora
        if (session.winnerRunnerId == null && (hostFinished || guestFinished)) {
            session.winnerRunnerId = runnerId
        }

        if (hostFinished && guestFinished) {
            session.status = SessionStatus.FINISHED
            session.hostDistanceMeters = hostDistance
            session.guestDistanceMeters = guestDistance
            session.finishedAt = Clock.System.now()
        }
    }

    fun buildResult(session: RaceSession): RaceResult = RaceResult(
        sessionId = session.id,
        sessionStatus = session.status,
        targetDistanceMeters = session.distanceMeters,
        winnerRunnerId = session.winnerRunnerId,
        hostRunnerId = session.hostRunnerId,
        hostDistanceMeters = session.hostDistanceMeters
            ?: trackingService.getDistance(session.id, session.hostRunnerId),
        guestRunnerId = session.guestRunnerId,
        guestDistanceMeters = session.guestDistanceMeters
            ?: trackingService.getDistance(session.id, session.guestRunnerId)
    )

    private fun publishResult(session: RaceSession) {
        mqttService.publish(
            topic = "race/${session.id}/result",
            payload = jsonMapper.writeValueAsString(buildResult(session))
        )
    }

    fun buildProgress(session: RaceSession, runnerId: String): RunnerProgress {
        val opponentId = if (runnerId == session.hostRunnerId) session.guestRunnerId else session.hostRunnerId
        val target = session.distanceMeters
        val distance = trackingService.getDistance(session.id, runnerId)
        val opponentDistance = trackingService.getDistance(session.id, opponentId)
        return RunnerProgress(
            sessionId = session.id,
            runnerId = runnerId,
            distanceMeters = distance,
            opponentRunnerId = opponentId,
            opponentDistanceMeters = opponentDistance,
            targetDistanceMeters = target,
            remainingMeters = (target - distance).coerceAtLeast(0.0),
            opponentRemainingMeters = (target - opponentDistance).coerceAtLeast(0.0),
            finished = distance >= target,
            opponentFinished = opponentDistance >= target,
            sessionStatus = session.status,
            winnerRunnerId = session.winnerRunnerId,
            won = session.winnerRunnerId?.let { it == runnerId }
        )
    }

    private fun publishProgress(session: RaceSession, runnerId: String) {
        val progress = buildProgress(session, runnerId)
        mqttService.publish(
            topic = "race/${session.id}/runner/$runnerId/progress",
            payload = jsonMapper.writeValueAsString(progress)
        )
    }

    private fun parseTopic(topic: String): Pair<String, String>? {
        // race/{sessionId}/runner/{runnerId}/location
        val levels = topic.split("/")
        if (levels.size != 5 || levels[0] != "race" || levels[2] != "runner" || levels[4] != "location") return null
        return levels[1] to levels[3]
    }

    companion object {
        const val LOCATION_TOPIC_FILTER = "race/+/runner/+/location"
        private const val RETRY_DELAY_SECONDS = 5L
    }
}
