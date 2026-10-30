package com.langlez.consumer

import com.langlez.web.LanglezException
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import jakarta.validation.Valid
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.dao.OptimisticLockingFailureException
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.stereotype.Component
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.filter.OncePerRequestFilter
import org.springframework.web.ErrorResponseException
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.server.ResponseStatusException

@SpringBootApplication
class ConsumerApp

data class SignUp(
    @field:NotBlank(message = "{consumer.name.blank}") val name: String?,
    @field:Size(max = 3, message = "{consumer.title.size}") val title: String?,
)

@RestController
class DemoController {

    @GetMapping("/langlez")
    fun langlez(@RequestParam status: Int, @RequestParam key: String, @RequestParam(required = false) arg: String?): String =
        throw LanglezException(HttpStatus.valueOf(status), key, *listOfNotNull(arg).toTypedArray())

    @PostMapping("/signup", consumes = [MediaType.APPLICATION_JSON_VALUE])
    fun signUp(@Valid @RequestBody body: SignUp): String = "ok"

    @GetMapping("/page")
    fun page(@RequestParam page: Int): String = "page $page"

    @GetMapping("/size")
    fun size(@RequestParam @Min(1, message = "{consumer.size.min}") size: Int): String = "size $size"

    @GetMapping("/rse")
    fun rse(@RequestParam status: Int, @RequestParam(required = false) reason: String?): String =
        throw ResponseStatusException(HttpStatus.valueOf(status), reason)

    @GetMapping("/error-response")
    fun errorResponse(@RequestParam status: Int): String = throw ErrorResponseException(HttpStatus.valueOf(status))

    @GetMapping("/json-only", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun jsonOnly(): String = "ok"

    @PostMapping("/part")
    fun part(@RequestPart("file") file: MultipartFile): String = "ok"

    @GetMapping("/unauthorized")
    fun unauthorized(): String = throw BadCredentialsException("bad")

    @GetMapping("/forbidden")
    fun forbidden(): String = throw AccessDeniedException("denied")

    @GetMapping("/data-conflict")
    fun dataConflict(): String = throw DataIntegrityViolationException("duplicate")

    @GetMapping("/optimistic")
    fun optimistic(): String = throw OptimisticLockingFailureException("stale")

    @GetMapping("/boom")
    fun boom(): String = throw IllegalStateException("boom")
}

@RestController
@Validated
class ClassLevelValidatedController {

    @GetMapping("/classlevel")
    fun classLevel(@RequestParam @Min(1, message = "{consumer.size.min}") n: Int): String = "ok"

    @GetMapping("/classlevel-pair")
    fun classLevelPair(
        @RequestParam @Min(1, message = "{consumer.size.min}") first: Int,
        @RequestParam @Min(1, message = "{consumer.size.min}") second: Int,
    ): String = "ok"
}

@Component
class FailingFilter : OncePerRequestFilter() {

    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, chain: FilterChain) {
        when (request.requestURI) {
            "/filter-boom" -> throw IllegalStateException("filter boom")
            "/filter-forbidden" -> response.sendError(HttpServletResponse.SC_FORBIDDEN)
            else -> chain.doFilter(request, response)
        }
    }
}
