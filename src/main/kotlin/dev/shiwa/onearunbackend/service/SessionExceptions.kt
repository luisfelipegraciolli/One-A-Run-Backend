package dev.shiwa.onearunbackend.service

class SessionNotFoundException(message: String) : RuntimeException(message)

class SessionNotOpenException(message: String) : RuntimeException(message)
