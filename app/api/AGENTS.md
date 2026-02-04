# API KNOWLEDGE BASE

## OVERVIEW
Main application entry point. Aggregates modules and provides REST endpoints.

## RESPONSIBILITIES
- **Configuration**: `application.yml` and profile management.
- **Observability**: Connecting `infra` drivers to `common:logger`.
- **Resources**: `spy.properties` (P6Spy).

## CONVENTIONS
- **Controller**: Thin layer. Validate input -> Call Service -> Return DTO.
- **Response**: Uniform response format (if defined).
- **Error Handling**: Rely on `common:exception` GlobalExceptionHandler.

## ANTI-PATTERNS
- Business logic in this module (Move to `module/`).
- defining Entities here.
