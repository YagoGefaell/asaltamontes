# BUG-028 — Compose: Postgres 13 sin soporte, BD expuesta, sin healthchecks y deploy con caída

- **Estado:** abierto
- **Severidad:** media
- **Confianza:** confirmado (lectura de código)
- **Área:** infra
- **Tarea que lo resuelve:** T07, T08
- **Detectado:** 2026-10-21, análisis inicial del código por Claude

## Dónde
`docker-compose.yml`, `.github/workflows/deploy.yml`

## Qué pasa
PostgreSQL 13 está fuera de soporte desde noviembre de 2025. El puerto `5433` publicado en todas las interfaces expone la BD a la red. `depends_on` sin `condition` arranca el backend antes de que la BD acepte conexiones. El deploy hace `docker compose down` antes de construir, así que la app está caída todo el build. No hay backups.

## Cómo reproducir
—

## Arreglo propuesto
T07 (compose, deploy, backups) y T08 (upgrade).
