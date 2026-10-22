# CLAUDE.md — Asaltamontes Female

Red social para la comunidad **Asaltamontes Female** (trail running y deporte de montaña para mujeres).
Objetivo a corto plazo: una **demo privada de calidad** funcionando en el servidor privado de Yago.
Objetivo a largo plazo: producción con miles de usuarias concurrentes y monetización con marcas.

## Antes de hacer nada en una sesión

1. Ejecuta el skill **`/start-session`** (o sigue a mano `.claude/skills/start-session/SKILL.md`).
2. Nunca empieces a programar sin saber qué tarea (`Txx`) o bug (`BUG-xxx`) estás resolviendo.
3. Al terminar, ejecuta **`/end-session`** para dejar el registro actualizado.
4. Sesión autónoma larga (vía libre de Yago): **`/autopilot`**. Preparación del servidor: `docs/ai/SERVER_SETUP.md`.

## Mapa de documentación de trabajo

| Archivo | Qué es | Versionado |
|---|---|---|
| `docs/ai/WORKFLOW.md` | Flujo de ramas, PRs, commits, revisión y Definition of Done. **Obligatorio leerlo.** | sí |
| `docs/ai/ROADMAP.md` | Orden de implementación, fases y dependencias entre tareas | sí |
| `docs/ai/tasks/Txx-*.md` | Especificación detallada de cada tarea (+ su `Estado`) | sí |
| `docs/ai/bugs/BUG-xxx-*.md` | Registro de bugs, uno por archivo (+ su `Estado`) | sí |
| `docs/ai/DECISIONS.md` | Decisiones de arquitectura/producto ya tomadas. No se re-discuten sin motivo nuevo | sí |
| `docs/ai/templates/` | Plantillas de tarea, bug y cuerpo de PR | sí |
| `.ai/STATUS.md` | Estado vivo: PRs abiertas, en qué se trabaja, bloqueos, preguntas para Yago | **no** (local) |
| `.ai/SESSION_LOG.md` | Diario de sesiones (append-only) | **no** (local) |

`.ai/` está en `.gitignore` a propósito (evita conflictos entre PRs paralelas). Vive en el checkout principal:
`$(git rev-parse --path-format=absolute --git-common-dir)/../.ai/` — úsalo así desde un worktree.

## Reglas innegociables

- **Nunca** commitear en `main` ni hacer merge de PRs: Yago revisa y mergea personalmente.
- **Una rama + una PR por unidad de trabajo** (tarea o bug). Detalles en `docs/ai/WORKFLOW.md`.
- Cada push a `main` **despliega automáticamente** al servidor privado (`.github/workflows/deploy.yml`). Trata cada PR como algo que va a producción.
- Cambios de esquema de BD **solo con migraciones Flyway** (a partir de T02). Nunca editar una migración ya mergeada.
- Nada de secretos en el repo. Variables nuevas → `.env.example` + `docker-compose.yml` + README.
- No toques cambios sin commitear que no sean tuyos (p. ej. trabajo local de Yago): no los incluyas en tus commits.
- Si descubres un bug fuera del alcance de tu tarea: **no lo arregles en la misma PR**; regístralo (`docs/ai/bugs/`) y sigue.
- **Respeta la arquitectura del backend de Yago, sobre todo la autenticación** (`DECISIONS.md` D-012): extender y arreglar en el sitio, nunca sustituir. Los cambios estructurales se proponen y esperan su OK.
- **Trabajas en la misma máquina donde corre la app desplegada** (servidor de casa, runner self-hosted). Nunca toques los contenedores, volúmenes ni la BD del proyecto de compose de producción. Para pruebas manuales, usa `docker compose -p asaltamontes-dev` con puertos alternativos (ver `WORKFLOW.md` §10).

## Stack y estructura

- **Backend** (`backend/`): Java 21, Spring Boot 3.5, Spring Security + JWT, Spring Data JPA, PostgreSQL, WebSocket STOMP, Maven.
  Paquetes por dominio en `io.github.yagogefaell.asaltamontes`: `auth`, `user`, `community`, `message`, `common`, `infrastructure`.
  Cada dominio: `controller/`, `service/`, `repository/`, `domain/` (entidades), `dto/` (records).
- **Frontend** (`frontend/`): React 19 + Vite, React Router 7, STOMP (`@stomp/stompjs`), CSS por componente.
  `src/features/<dominio>/{hooks,services,components}`, `src/pages/`, `src/shared/`, `src/lib/api-client.js`.
- **Infra**: Docker Compose (db, backend, frontend/nginx). Deploy por GitHub Actions en runner self-hosted.

## Comandos

```bash
# Backend
cd backend && ./mvnw verify            # compila + tests (Testcontainers necesita Docker)
cd backend && ./mvnw spring-boot:run   # requiere .env en la raíz
# Frontend
cd frontend && npm ci && npm run lint && npm run build
cd frontend && npm test                # cuando exista (T01)
# Stack completo
docker compose --env-file .env up --build
```

## Convenciones de código

- Código, identificadores y mensajes de commit en **inglés**; UI, mensajes de error al usuario y docs de `docs/ai/` en **español**.
- Imitar el estilo del código vecino (records para DTOs, inyección por constructor/`@RequiredArgsConstructor`, proyecciones JPQL a DTO).
- Backend: lanzar excepciones de dominio (`common/exceptions`), nunca `RuntimeException`/`IllegalArgumentException` genéricas para errores de negocio.
- Paginar todo listado que pueda crecer. Nada de `findAll()` sin límite.
- Toda funcionalidad nueva o bug arreglado lleva **tests** (ver Definition of Done).
- Cambio de esquema: skill **`/new-migration`**. Un hook bloquea editar migraciones ya presentes en `origin/main`.
- PRs que toquen autenticación, permisos o datos personales: pasa antes el agente **`security-reviewer`** (solo esas, no todas) y menciona sus hallazgos en la PR.
