package dev.shiwa.onearunbackend.model.session

import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank

data class CreateSessionRequest(
    @field:Min(400) @field:Max(1000) val distanceInMeters: Int,
    @field:NotBlank val hostRunnerName: String,
)