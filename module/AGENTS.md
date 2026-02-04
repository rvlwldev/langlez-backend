# MODULE KNOWLEDGE BASE

## OVERVIEW
Business domain modules. Each module is a self-contained vertical slice.

## STRUCTURE
```
module/{name}/
├── api/                # DTOs, Controllers (Http)
├── application/        # Service, UseCase, Ports
├── domain/             # Entities, Domain Logic (Pure Kotlin)
└── infrastructure/     # Repositories (Impl), External Adapters
```

## CONVENTIONS
- **Isolation**: Modules should interact via defined UseCases or Domain Services.
- **Persistence**: 
  - `domain/repository/XRepository` (Interface)
  - `infrastructure/persistence/XRepositoryImpl` (Implementation)
- **Mapping**: DTO <-> Entity mapping happens in `api` or `application` layer.

## ANTI-PATTERNS
- Importing `infrastructure` classes in `domain`.
- Direct database calls from `api`.
