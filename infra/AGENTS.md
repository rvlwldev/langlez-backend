# INFRA KNOWLEDGE BASE

## OVERVIEW
Technology adapters and drivers.

## MODULES
- **mysql**: JPA, QueryDSL.
- **redis**: Lettuce, Caching, Distrib Lock.
- **mongo**: Audit logging, Chat history.
- **kafka**: Messaging.
- **files**: S3/Local storage.

## CONVENTIONS
- **Config**: Provide `@Configuration` beans for the technology.
- **Test**: Use `TestContainers` for integration testing.
- **Profile**: Support `local` (embedded/docker) and `prod` (managed service) profiles.
