package dev.shiwa.onearunbackend.controller


import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class RootController {
    @GetMapping("/")
    fun root() = "Hello World! This is the backend for the One A Run Android App!"
}