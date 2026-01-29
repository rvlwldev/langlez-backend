package com.langlez.swagger.model

import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse

@Target(AnnotationTarget.FUNCTION, AnnotationTarget.ANNOTATION_CLASS)
@Retention(AnnotationRetention.RUNTIME)
@ApiResponse(
    responseCode = "400",
    description = "Bad Request",
    content = [
        Content(
            mediaType = "application/json",
            schema = Schema(implementation = SwaggerErrorResponse::class),
            examples = [
                ExampleObject(
                    name = "ValidationError",
                    value = "{\"code\": \"VALIDATION_ERROR\", \"message\": \"Invalid input parameters\"}",
                ),
            ],
        ),
    ],
)
@ApiResponse(
    responseCode = "401",
    description = "Unauthorized",
    content = [
        Content(
            mediaType = "application/json",
            schema = Schema(implementation = SwaggerErrorResponse::class),
            examples = [
                ExampleObject(
                    name = "Unauthorized",
                    value = "{\"code\": \"UNAUTHORIZED\", \"message\": \"Authentication required\"}",
                ),
            ],
        ),
    ],
)
@ApiResponse(
    responseCode = "403",
    description = "Forbidden",
    content = [
        Content(
            mediaType = "application/json",
            schema = Schema(implementation = SwaggerErrorResponse::class),
            examples = [
                ExampleObject(
                    name = "Forbidden",
                    value = "{\"code\": \"FORBIDDEN\", \"message\": \"Access denied\"}",
                ),
            ],
        ),
    ],
)
@ApiResponse(
    responseCode = "404",
    description = "Not Found",
    content = [
        Content(
            mediaType = "application/json",
            schema = Schema(implementation = SwaggerErrorResponse::class),
            examples = [
                ExampleObject(
                    name = "NotFound",
                    value = "{\"code\": \"RESOURCE_NOT_FOUND\", \"message\": \"Resource not found\"}",
                ),
            ],
        ),
    ],
)
@ApiResponse(
    responseCode = "500",
    description = "Internal Server Error",
    content = [
        Content(
            mediaType = "application/json",
            schema = Schema(implementation = SwaggerErrorResponse::class),
            examples = [
                ExampleObject(
                    name = "InternalServerError",
                    value = "{\"code\": \"INTERNAL_SERVER_ERROR\", \"message\": \"Internal server error occurred\"}",
                ),
            ],
        ),
    ],
)
annotation class StandardErrorResponses
