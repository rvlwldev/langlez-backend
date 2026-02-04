# Langlez Backend Server

**Langlez(랭크즈)** Global Social Networking Service for Language Exchange.
Built with **Modular Monolith Architecture** to ensure scalability and maintainability.

---

## 1. Getting Started

### Prerequisites
- JDK 21+
- Docker (for TestContainers & Local Infra)

### Build & Run
```bash
# Build (Skip tests for fast build)
./gradlew build -x test

# Run API Server (Local Profile)
./gradlew :app:api:bootRun
```

### Code Style
```bash
# Apply ktlint formatting
./gradlew ktlintFormat
```

---

## 2. Project Architecture

### Structure
- **app/**: Executable applications (API, Admin). Aggregates modules.
- **module/**: Business domains (Auth, Member, Chat, etc.). **Vertical Slices**.
- **common/**: Shared utilities (Security, I18n, Logger).
- **infra/**: Technology adapters (MySQL, Redis, Kafka).

### Key Principles
- **No Cross-Module Joins**: Modules communicate via Service Interfaces or Events.
- **Environment Consistency**: Single codebase for Local/Prod.
- **Kotest & MockK**: Standard testing stack.

---

## 3. Contribution & AI Guidelines

For detailed development rules, architecture deep-dive, and AI agent instructions, please refer to:

👉 **[AGENTS.md](./AGENTS.md)**

*AI Agents MUST read `AGENTS.md` before starting any task.*

---

## 4. Documentation
- **Roadmap & User Actions**: [TODO.md](./TODO.md)
- **API Docs**: Swagger UI (Available when running locally)
