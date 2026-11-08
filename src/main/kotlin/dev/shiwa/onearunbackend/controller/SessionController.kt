package dev.shiwa.onearunbackend.controller

import dev.shiwa.onearunbackend.model.session.*
import dev.shiwa.onearunbackend.service.SessionService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*


@RestController
@RequestMapping("/api/v1/race")
class SessionController(
    private val sessionService: SessionService,
) {
    @GetMapping("/{id}")
    fun getSession(@PathVariable id: String): ResponseEntity<RaceSession> {
        val response = sessionService.getSession(id)
        return ResponseEntity(response, HttpStatus.CREATED)
    }
    @PostMapping()
    fun createSession(@RequestBody @Valid request: CreateSessionRequest): ResponseEntity<CreateSessionResponse>  {
        val response = sessionService.create(distanceMeters =  request.distanceInMeters, hostRunnerName = request.hostRunnerName)
        return ResponseEntity(response, HttpStatus.CREATED)
    }
    @PostMapping("/join")
    fun join(@RequestBody @Valid request: JoinSessionRequest): ResponseEntity<JoinSessionResponse>{
        val response = sessionService.join(joinCode = request.joinCode, guestRunnerName = request.guestRunnerName)
        return ResponseEntity(response, HttpStatus.OK)
    }
}