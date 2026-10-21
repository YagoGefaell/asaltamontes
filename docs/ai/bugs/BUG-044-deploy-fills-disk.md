# BUG-044 — Cada deploy deja caché de build e imágenes viejas: la microSD se llena

- **Estado:** resuelto
- **Severidad:** alta
- **Confianza:** confirmado
- **Área:** infra
- **Tarea que lo resuelve:** ninguna (PR `fix/` propia)
- **Detectado:** 2026-10-21, Claude en el portátil revisando el servidor con Yago

## Dónde
`.github/workflows/deploy.yml` → paso final `docker image prune -f`

## Qué pasa
El deploy solo borra las imágenes *dangling*. La caché de BuildKit (capas de Maven, `npm ci`, etc.) y las imágenes antiguas que conservan un tag nunca se borran. Con los deploys seguidos del 2026-10-21 la microSD pasó del 38 % al 58 % en 3 horas (22 GB de caché de build). A ese ritmo el disco se llena en un día de trabajo y entonces fallan el build, Postgres y los backups (`pg_dump` en el mismo disco).

Además, los contenedores creados antes de activar el cgroup de memoria (2026-10-21) tienen `HostConfig.Memory = 0`: compose no los recrea porque su configuración no ha cambiado, así que `mem_limit` sigue sin aplicarse hasta que cambie la imagen.

## Cómo reproducir
`docker system df` tras varios deploys: `Build Cache` crece en cada uno. `docker inspect -f '{{.HostConfig.Memory}}' postgres_db` → `0` aunque el compose declare `mem_limit: 512m`.

## Arreglo propuesto
Al final de un deploy correcto: `docker builder prune -f --filter until=24h` y `docker image prune -a -f --filter until=72h` (nunca volúmenes). Aviso en el job si el disco pasa del 80 %. Recrear una sola vez (`--force-recreate --no-deps`) los servicios con `mem_limit` cuyo contenedor tenga el límite a 0.
