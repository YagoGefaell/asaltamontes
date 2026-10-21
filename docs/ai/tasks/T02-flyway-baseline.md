# T02 — Flyway y baseline del esquema actual

- **Estado:** pendiente
- **Fase:** 0 — Cimientos
- **Depende de:** T01
- **Rama sugerida:** `feat/T02-flyway-baseline`
- **Cierra bugs:** BUG-005
- **Tamaño:** M

## División en sub-PRs (2026-10-21)
- **T02a — Flyway + baseline** (hecho): dependencias, `V1__baseline.sql` generado desde las entidades y comparado con lo que crea Hibernate (columnas, constraints e índices idénticos; solo cambian los nombres de las constraints, ahora explícitos), `ddl-auto=validate`, `baseline-on-migrate`, `FlywayMigrationIT` y sección de migraciones en el README. Cierra BUG-005.
  - El `pg_dump --schema-only` de producción no se ha obtenido: Claude no tiene permiso para leer la BD de producción. Comprobado en local que una BD creada por Hibernate y con datos arranca con baseline + validate sin perder nada. Como la app desplegada ha ejecutado `ddl-auto=update` con las entidades actuales, su esquema tiene todas las columnas que valida Hibernate. Si aun así no arrancara, se recrea la BD (D-014).
- **T02b — Seed de desarrollo** (pendiente): necesita la ruta del archivo de datos de ejemplo del servidor (pregunta abierta a Yago). Alternativa si no aparece: seed ficticio escrito desde cero.

## Objetivo
Dejar de depender de `spring.jpa.hibernate.ddl-auto=update`. El esquema pasa a estar versionado en migraciones SQL revisables, y Hibernate solo **valida**.

## Contexto
- `application.properties`: `spring.jpa.hibernate.ddl-auto=update`.
- El servidor privado tiene una BD creada por Hibernate con **datos de ejemplo desechables** (D-014): se puede recrear desde cero. Existe en el servidor un archivo con esos datos de ejemplo (ubicación por confirmar).
- Tablas actuales (según entidades): `users`, `user_profiles`, `user_follow`, `communities`, `community_members`, `conversations`, `conversation_participants`, `messages`.

## Alcance
### BD
1. Generar el esquema **real** del servidor: pedir a Yago (o ejecutar si hay acceso) `docker exec postgres_db pg_dump -U <user> -d asaltamontes --schema-only --no-owner --no-privileges > /tmp/schema-server.sql`.
2. Generar el esquema que produce Hibernate a partir de las entidades actuales sobre una BD vacía (Testcontainers + `ddl-auto=create` + `pg_dump`) y **compararlos**. Documentar diferencias en la PR (columnas huérfanas de versiones anteriores, constraints con nombres autogenerados, etc.).
3. `backend/src/main/resources/db/migration/V1__baseline.sql`: esquema actual limpio (tipos, PK, FK, unique, índices existentes). Nombres de constraints explícitos.
4. Configuración:
   - `spring.flyway.enabled=true`
   - `spring.flyway.baseline-on-migrate=true` y `spring.flyway.baseline-version=1` → en la BD del servidor (que ya tiene tablas) Flyway marca V1 como aplicada sin ejecutarla; en BDs nuevas ejecuta V1.
   - `spring.jpa.hibernate.ddl-auto=validate`.
5. Si el esquema del servidor difiere del de las entidades, crear `V2__align_schema_with_entities.sql` que lo alinee de forma **no destructiva** (sin `DROP COLUMN` con datos sin aprobación explícita de Yago en la PR).

### Backend
- Dependencias `org.flywaydb:flyway-core` y `org.flywaydb:flyway-database-postgresql`.
- Los tests de T01 pasan a crear el esquema con Flyway (no con Hibernate).
- Test `FlywayMigrationIT`: arranca con BD vacía → migra → Hibernate `validate` OK.

### Docs
- Sección "Migraciones" en `backend/README.md`: cómo crear una nueva (`V<n>__descripcion.sql`), reglas: nunca editar una mergeada, una por PR que cambie esquema, probar con datos.
- Procedimiento de despliegue de esta PR en el servidor (backup previo con `pg_dump` completo antes de mergear) en la descripción de la PR.

### Seed de desarrollo (D-014)
- Localizar en el servidor el archivo de datos de ejemplo que usa Yago y convertirlo en `scripts/seed/seed-dev.sql` (o un `CommandLineRunner` con perfil `dev`/`seed`) **reproducible** sobre el esquema de Flyway. Usuarias ficticias; contraseñas de ejemplo documentadas en el README; ningún dato personal real.
- Documentar cómo resetear la BD local o de desarrollo y cargar el seed.

## Fuera de alcance
- Cambios de esquema nuevos (índices, unicidad case-insensitive) → T05.
- Upgrade de Postgres → T08.

## Criterios de aceptación
- [ ] Con BD vacía, el backend arranca, Flyway aplica V1 (y V2 si existe) y Hibernate valida.
- [ ] Con una copia del esquema del servidor (con datos de prueba), el backend arranca sin perder datos.
- [ ] `ddl-auto=validate` en todos los perfiles excepto, como mucho, uno local explícito.
- [ ] La PR incluye el diff de esquemas servidor vs entidades y la decisión tomada para cada diferencia.

## Tests requeridos
- `FlywayMigrationIT` (BD vacía).
- Test que carga `schema-server.sql` (si se obtiene y no contiene datos personales) + datos de ejemplo, arranca y verifica conteos.

## Notas y riesgos
- Como los datos son desechables, la alternativa simple es válida: baseline generado desde las entidades + recrear la BD del servidor en el despliegue (borrar el volumen solo con el OK de Yago en la PR, ya que lo hace él o el deploy, nunca Claude a mano).
- El dump de esquema no contiene datos personales; el de datos **nunca** se sube al repo.
