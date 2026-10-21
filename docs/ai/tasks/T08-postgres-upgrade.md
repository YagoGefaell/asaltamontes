# T08 — Actualizar PostgreSQL 13 → 17

- **Estado:** pendiente
- **Fase:** 0 — Cimientos
- **Depende de:** T02, T07 (backups funcionando)
- **Rama sugerida:** `chore/T08-postgres-17`
- **Cierra bugs:** BUG-028 (parte)
- **Tamaño:** S

## Objetivo
PostgreSQL 13 está fuera de soporte desde noviembre de 2025 (sin parches de seguridad). Pasar a 17 sin perder datos.

## Alcance
> Simplificado por D-014: como los datos son desechables, basta con cambiar la imagen, usar un volumen nuevo y cargar el seed de T02. El script de dump/restore se mantiene como procedimiento documentado para cuando haya datos reales.

- `docker-compose.yml`: `image: postgres:17-alpine` y **volumen nuevo** (`postgres17_data`); el antiguo se conserva hasta validar.
- Script `scripts/db/upgrade-13-to-17.sh` (idempotente y comentado):
  1. `pg_dumpall` (o `pg_dump -Fc`) desde el contenedor 13.
  2. Levantar el 17 con el volumen nuevo.
  3. Restaurar, `ANALYZE`.
  4. Verificar conteos por tabla (13 vs 17) e imprimir la comparación.
- Instrucciones de rollback (volver a apuntar al volumen antiguo).
- Tests (Testcontainers) ya usan `postgres:17-alpine` desde T01.

## Criterios de aceptación
- [ ] Script probado en local con una copia de datos sintéticos.
- [ ] Conteos idénticos antes/después.
- [ ] Procedimiento y rollback descritos en la PR; Yago ejecuta (o autoriza ejecutar) el script en el servidor.

## Notas y riesgos
- Operación con riesgo de datos: **no** se ejecuta automáticamente en el deploy. Es un procedimiento manual guiado.
