# Online Banking Dashboard System (Demo)

> **Demo banking — fictional money.** This project simulates a banking dashboard
> for portfolio/learning purposes. No real money, real bank accounts, UPI, cards,
> loans, or identity documents are involved anywhere in this system.

Full architecture, environment variables, setup, testing, deployment and backup
docs are being built out incrementally — see [docs/](docs/) and
[IMPLEMENTATION_STATUS.md](IMPLEMENTATION_STATUS.md) for the current, honest state
of what is implemented and verified.

## Quick start (once the backend/frontend scaffolds land)

```powershell
docker compose up --build
```

Then open http://localhost:8080

## Repository layout

```
backend/    Spring Boot (Java 17) API, Flyway migrations, backend tests
frontend/   React + TypeScript + Vite SPA
e2e/        Playwright end-to-end tests against the packaged app
scripts/    Local dev / backup / restore helper scripts
docs/       Architecture, API, deployment, backup/restore documentation
```

See [CLAUDE.md](CLAUDE.md) for pinned architectural decisions and working
conventions for anyone (human or agent) continuing this build.
