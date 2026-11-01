package dev.shiwa.onearunbackend.controller

import dev.shiwa.onearunbackend.service.SessionNotFoundException
import dev.shiwa.onearunbackend.service.SessionNotOpenException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

data class ErrorResponse(val message: String?)

@RestControllerAdvice
class GlobalExceptionHandler {
    @ExceptionHandler(SessionNotFoundException::class)
    fun handleNotFound(e: SessionNotFoundException) =
        ResponseEntity(ErrorResponse(e.message), HttpStatus.NOT_FOUND)

    @ExceptionHandler(SessionNotOpenException::class)
    fun handleNotOpen(e: SessionNotOpenException) =
        ResponseEntity(ErrorResponse(e.message), HttpStatus.CONFLICT)

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleBadRequest(e: IllegalArgumentException) =
        ResponseEntity(ErrorResponse(e.message), HttpStatus.BAD_REQUEST)
}
