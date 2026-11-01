package dev.shiwa.onearunbackend.service

import dev.shiwa.onearunbackend.repository.RaceSessionRepository
import dev.shiwa.onearunbackend.model.session.CreateSessionResponse
import dev.shiwa.onearunbackend.model.session.JoinSessionResponse
import dev.shiwa.onearunbackend.model.session.RaceSession
import dev.shiwa.onearunbackend.model.session.SessionStatus
import org.springframework.stereotype.Service
import java.util.Locale.getDefault
import java.util.UUID


@Service
class SessionService(
    private val raceRepository: RaceSessionRepository,
    private val mqttService: MqttService
) {
    private val codeAlphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    fun create(distanceMeters: Int): CreateSessionResponse {
        require(distanceMeters in 100..1000) { "distanceMeters must be in 100..1000" }
        val hostRunnerId = UUID.randomUUID().toString()
        val session = raceRepository.save(
            RaceSession(
                joinCode = generateUniqueCode(),
                distanceMeters = distanceMeters,
                hostRunnerId = hostRunnerId,
            )
        )

        return CreateSessionResponse(
            sessionId = session.id,
            joinCode = session.joinCode,
            runnerId = hostRunnerId
        )
    }

    fun getSession(sessionId: String): RaceSession {
        val session = raceRepository.findById(sessionId)
        if (session.isEmpty) {
            throw SessionNotFoundException("Session not found")
        }
        return session.get()
    }

    fun join(joinCode: String): JoinSessionResponse {
        val session = raceRepository.findByJoinCode(joinCode.trim().uppercase(getDefault()))
            ?: throw SessionNotFoundException("Session not found")

        if (session.status != SessionStatus.WAITING) {
            throw SessionNotOpenException("Essa sessão não está mais aberta")
        }
        run {
            val guestRunnerId = UUID.randomUUID().toString()
            session.guestRunnerId = guestRunnerId
            // Com os dois jogadores presentes ninguém mais pode entrar na sessão
            session.status = SessionStatus.READY
            raceRepository.save(session)

            mqttService.publish(payload = "Runner $guestRunnerId joined the race")
            mqttService.publish(
                topic = "race/${session.id}/status",
                payload = "Runner $guestRunnerId joined the race"
            )
            return JoinSessionResponse(
                sessionId = session.id,
                guestRunnerId = guestRunnerId,
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
}