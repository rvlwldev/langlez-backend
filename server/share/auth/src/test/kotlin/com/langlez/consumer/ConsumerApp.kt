package com.langlez.consumer

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@SpringBootApplication
class ConsumerApp

@RestController
class DemoController {

    @GetMapping("/me")
    fun me(@AuthenticationPrincipal jwt: Jwt): String = jwt.subject.orEmpty()

    @GetMapping("/open/ping")
    fun ping(): String = "pong"

    @GetMapping("/open/boom")
    fun boom(): String = error("서버 내부 오류")
}
