# T05 — Higiene de entidades, unicidad de username e índices

- **Estado:** pendiente
- **Fase:** 0 — Cimientos
- **Depende de:** T02
- **Rama sugerida:** `refactor/T05-entities-indexes`
- **Cierra bugs:** BUG-008, BUG-009
- **Tamaño:** M

## Objetivo
Evitar errores latentes de JPA (`@Data` en entidades), garantizar que los usernames son únicos sin distinguir mayúsculas y añadir los índices que necesitan las consultas actuales para escalar.

## Contexto
- `@Data` en `UserAccount`, `UserProfile`, `UserFollow` (relaciones bidireccionales → `toString`/`hashCode` recursivos).
- `UserValidator` usa `existsByUsername` (sensible a mayúsculas) pero todo el sistema busca con `findByUsernameIgnoreCase`.
- `UserFollowRepository.findFollowingByUsername/findFollowersByUsername` comparan `username = :username` (sensible).
- Consultas sin índice: `messages` por `conversation_id` + orden por fecha, `conversation_participants` por `user_id`, `community_members` por `user_account_id`, búsqueda `LIKE '%q%'` en `users.username`.
- `UserFollow` usa `@ManyToOne` EAGER por defecto.

## Alcance
### Entidades
- Sustituir `@Data` por `@Getter @Setter` + `@ToString(exclude = …)` (o sin `@ToString`) + `equals/hashCode` basados en id (patrón recomendado por Hibernate) en **todas** las entidades.
- `@ManyToOne(fetch = LAZY)` en `UserFollow`.

### Migración `V<n>__username_ci_and_indexes.sql`
- **Antes**: consulta de detección de duplicados `SELECT lower(username), count(*) FROM users GROUP BY 1 HAVING count(*) > 1;` (y lo mismo con email). Si hay duplicados en el servidor, la migración **renombra** los duplicados de forma determinista (`<username>_<id>`) y la PR lo documenta. Pedir confirmación a Yago en la PR.
- Índice único `ux_users_username_lower ON users (lower(username))` y `ux_users_email_lower ON users (lower(email))`.
- Índices:
  - `messages (conversation_id, id DESC)` (preparado para la paginación por cursor de T10).
  - `conversation_participants (user_id)`.
  - `community_members (user_account_id)`.
  - `pg_trgm` + índice GIN trigram sobre `lower(username)` y `lower(full_name)` para la búsqueda.
- Revisar con `EXPLAIN ANALYZE` (datos sintéticos: 10k usuarias, 100k follows, 500k mensajes) las consultas principales y pegar el resultado relevante en la PR.

### Servicios
- `UserValidator`: `existsByUsernameIgnoreCase`; `existsByUsernameExceptId` con `lower()`.
- Consultas de follows por username: `lower(u.username) = lower(:username)` o, mejor, resolver el id primero.

## Fuera de alcance
- Cambiar el modelo de unread count → T09.

## Criterios de aceptación
- [ ] No queda `@Data` en ninguna clase `@Entity`.
- [ ] Registrar "Ana" cuando existe "ana" → 409 con error de campo `username`.
- [ ] La migración se aplica sobre una BD con duplicados de ejemplo sin fallar.
- [ ] Las consultas de historial de chat, lista de conversaciones y búsqueda usan índice (EXPLAIN en la PR).

## Tests requeridos
- IT: registro con username que difiere solo en mayúsculas → 409.
- IT de la migración con datos duplicados sembrados antes de aplicarla (Flyway `target` en dos pasos).
- Test unitario: `toString()`/`hashCode()` de `UserAccount` con perfil asociado no lanza `StackOverflowError`.
