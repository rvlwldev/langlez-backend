# COMMON KNOWLEDGE BASE

## OVERVIEW
Shared utilities and cross-cutting concerns. Reusable across all modules.

## MODULES
- **i18n**: MessageSource config. Use `messages.properties`.
- **security**: OAuth2, JWT, SecurityConfig.
- **logger**: P6Spy, AOP logging.
- **exception**: Global error handling.
- **swagger**: API Documentation.

## CONVENTIONS
- **Stateless**: Utilities should generally be stateless beans.
- **No Domain**: DO NOT import `module/*` classes here.
- **Library-like**: Treat these as internal libraries.
