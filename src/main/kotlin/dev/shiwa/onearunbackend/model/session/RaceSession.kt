package dev.shiwa.onearunbackend.model.session

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.util.UUID
import kotlin.time.Clock
import kotlin.time.Instant

enum class SessionStatus { WAITING, READY, RUNNING, FINISHED, CANCELLED }

@Entity
@Table(name = "race_session")
class RaceSession(
    @Id
    var id: String = UUID.randomUUID().toString(),

    @Column(unique = true, nullable = false)
    var joinCode: String,

    var distanceMeters: Int,

    var hostRunnerId: String,

    var guestRunnerId: String? = null,

    @Enumerated(EnumType.STRING)
    var status: SessionStatus = SessionStatus.WAITING,

    var createdAt: Instant = Clock.System.now(),

    // Primeiro jogador a completar a distância da sessão
    var winnerRunnerId: String? = null,

    // Distâncias finais registradas quando a sessão é encerrada
    var hostDistanceMeters: Double? = null,

    var guestDistanceMeters: Double? = null,

    var finishedAt: Instant? = null,

    // Evita que dois convidados entrem ao mesmo tempo na mesma sessão
    @Version
    var version: Long = 0
)