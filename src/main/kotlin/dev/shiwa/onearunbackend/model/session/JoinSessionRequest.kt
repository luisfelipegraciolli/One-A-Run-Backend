package dev.shiwa.onearunbackend.model.session

import jakarta.validation.constraints.NotBlank

data class JoinSessionRequest(
    @field:NotBlank val guestRunnerName: String,
    @field:NotBlank val joinCode: String
)
