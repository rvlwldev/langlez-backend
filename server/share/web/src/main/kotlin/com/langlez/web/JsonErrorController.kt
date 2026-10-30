package com.langlez.web

import jakarta.servlet.RequestDispatcher
import jakarta.servlet.http.HttpServletRequest
import org.springframework.boot.web.error.ErrorAttributeOptions
import org.springframework.boot.webmvc.error.ErrorAttributes
import org.springframework.boot.webmvc.error.ErrorController
import org.springframework.http.HttpStatusCode
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.context.request.ServletWebRequest

@RestController
@RequestMapping($$"${server.error.path:${error.path:/error}}")
class JsonErrorController(private val errorAttributes: ErrorAttributes) : ErrorController {

    @RequestMapping
    fun error(request: HttpServletRequest): ResponseEntity<Map<String, Any?>> {
        val status = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE) as? Int ?: 500
        return ResponseEntity.status(HttpStatusCode.valueOf(status))
            .contentType(MediaType.APPLICATION_JSON)
            .body(errorAttributes.getErrorAttributes(ServletWebRequest(request), ErrorAttributeOptions.defaults()))
    }
}
