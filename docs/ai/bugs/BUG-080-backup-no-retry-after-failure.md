# BUG-080 — Si la copia diaria falla, no se reintenta hasta el día siguiente

- **Estado:** abierto
- **Severidad:** media
- **Confianza:** confirmado
- **Área:** infra
- **Tarea que lo resuelve:** ninguna (PR `fix/` propia)
- **Detectado:** 2026-10-29, auditoría de UI en la Pi (contenedor `db_backup` en estado unhealthy)

## Dónde
`scripts/backup/backup-loop.sh` (funciones `backup` y bucle principal); `docker-compose.yml`, servicio `backup`.

## Qué pasa
El 2026-10-29 a las 10:47 los contenedores se reiniciaron (probablemente reinicio del daemon o de la Pi). Al arrancar, `backup-loop.sh` lanzó la copia inmediata porque la última tenía más de 24 h, pero `db` aún no aceptaba conexiones:

```
pg_dump: error: connection to server at "db" (172.18.0.4), port 5432 failed: Connection refused
2026-10-29 10:47:06 backup: FAILED
2026-10-29 10:47:07 backup: next backup in 60173s (at 03:30)
```

`depends_on: condition: service_healthy` solo se respeta en `docker compose up`, no cuando Docker reinicia los contenedores por su `restart` policy. Tras el fallo, el script espera a la siguiente hora programada: **~41 h sin copia** (última buena: 2026-10-28 03:30) y el healthcheck en rojo todo ese tiempo.

## Cómo reproducir
Con el stack de pruebas: parar `db`, reiniciar `backup` y ver que registra `FAILED` y programa la siguiente copia para el día siguiente.

## Arreglo propuesto
- Antes de `pg_dump`, esperar a la BD con `pg_isready` (hasta unos minutos).
- Si la copia falla, reintentar con espera creciente (p. ej. 1, 5, 15, 60 min) antes de volver al horario diario; cada intento se registra.
- Test: script de prueba o comprobación manual documentada en la PR (parar `db`, reiniciar `backup`, arrancar `db` y ver que la copia se completa sola).
- No tocar el compose de producción desde la sesión: el arreglo llega con el deploy al mergear.
