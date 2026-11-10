package dev.shiwa.onearunbackend.service

import dev.shiwa.onearunbackend.repository.RaceSessionRepository
import dev.shiwa.onearunbackend.model.session.CreateSessionResponse
import dev.shiwa.onearunbackend.model.session.JoinSessionResponse
import dev.shiwa.onearunbackend.model.session.RaceSession
import dev.shiwa.onearunbackend.model.session.SessionStatus
import org.springframework.stereotype.Service
import java.util.Locale.getDefault
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit


@Service
class SessionService(
    private val raceRepository: RaceSessionRepository,
    private val mqttService: MqttService
) {
    private val codeAlphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    fun create(hostRunnerName: String, distanceMeters: Int): CreateSessionResponse {
        require(distanceMeters in 100..1000) { "distanceMeters must be in 100..1000" }
        val hostRunnerId = UUID.randomUUID().toString()
        val session = raceRepository.save(
            RaceSession(
                joinCode = generateUniqueCode(),
                distanceMeters = distanceMeters,
                hostRunnerId = hostRunnerId,
                hostRunnerName = hostRunnerName,
            )
        )

        return CreateSessionResponse(
            sessionId = session.id,
            joinCode = session.joinCode,
            runnerId = hostRunnerId,
            hostRunnerName = hostRunnerName,
        )
    }

    fun getSession(sessionId: String): RaceSession {
        val session = raceRepository.findById(sessionId)
        if (session.isEmpty) {
            throw SessionNotFoundException("Session not found")
        }
        return session.get()
    }

    fun join(guestRunnerName: String, joinCode: String): JoinSessionResponse {
        val session = raceRepository.findByJoinCode(joinCode.trim().uppercase(getDefault()))
            ?: throw SessionNotFoundException("Session not found")

        if (session.status != SessionStatus.WAITING) {
            throw SessionNotOpenException("Essa sessão não está mais aberta")
        }
        run {
            val guestRunnerId = UUID.randomUUID().toString()
            session.guestRunnerId = guestRunnerId
            session.guestRunnerName = guestRunnerName
            // Com os dois jogadores presentes ninguém mais pode entrar na sessão
            session.status = SessionStatus.READY
            raceRepository.save(session)

            subscribeToStatus("race/${session.id}/status")

            mqttService.publish(
                topic = "race/${session.id}/status",
                payload = "Runner $guestRunnerId joined the race"
            )

            return JoinSessionResponse(
                sessionId = session.id,
                guestRunnerId = guestRunnerId,
                guestRunnerName = guestRunnerName,
                distanceMeters = session.distanceMeters
            )
        }
    }

    private fun generateUniqueCode(): String {
        repeat(10) {
            val code = (1..6).map { codeAlphabet.random() }.joinToString("")
            if (!raceRepository.existsByJoinCode(code)) return code
        }
        error("Não foi possível gerar um código único")
    }

    private fun subscribeToStatus(topic: String) {
        // DEBUG Line
        val delay = 5L
        mqttService.subscribe(topic = topic)
            .whenComplete { _, throwable ->
                if (throwable != null) {
                    System.err.println("Failed to subscribe to $topic: ${throwable.message}. Retrying...")
                    CompletableFuture.delayedExecutor(delay, TimeUnit.SECONDS)
                        .execute { subscribeToStatus(
                            topic = topic
                        ) }
                } else {
                    println("Successfully subscribed to topic: $topic")
                }
            }
    }
}