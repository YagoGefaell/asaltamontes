# BUG-041 — Detrás de `tailscale serve`, todas las usuarias compartirían el rate limit por IP

- **Estado:** abierto
- **Severidad:** alta (cuando se active T07c; hoy no ocurre)
- **Confianza:** probable (lectura de la configuración; se reproduce con cualquier proxy delante de nginx)
- **Área:** infra
- **Tarea que lo resuelve:** T07c (HTTPS con Tailscale)
- **Detectado:** 2026-10-21, Claude al revisar T06b + T07a

## Dónde
`frontend/proxy_headers.inc` (`proxy_set_header X-Forwarded-For $remote_addr;`) + `infrastructure/ratelimit/AuthRateLimitFilter.java` (IP = `request.getRemoteAddr()`, resuelta desde `X-Forwarded-For` en el perfil `prod`).

## Qué pasa
T07a hace que nginx **sobrescriba** `X-Forwarded-For` con la IP que ve (`$remote_addr`), para que una clienta no pueda falsear su IP mientras nginx sea el primer salto. Con T07c (`tailscale serve` terminando HTTPS en la Pi y reenviando a nginx), nginx verá **siempre `127.0.0.1`**: todas las testers compartirían el mismo contador y el límite de **5 logins/min por IP sería para toda la app**. Lo mismo para registro (3/min) y refresh (30/min, que el frontend llama al cargar la página).

## Cómo reproducir
Poner cualquier proxy local delante de nginx (p. ej. `socat` o `tailscale serve` → `localhost:80`) y hacer 6 logins desde dispositivos distintos: el 6.º recibe 429.

## Arreglo propuesto
En T07c, cuando haya un proxy de confianza delante:
- nginx con el módulo `realip`: `set_real_ip_from 127.0.0.1; real_ip_header X-Forwarded-For; real_ip_recursive on;` (tailscale serve añade `X-Forwarded-For` con la IP de la tailnet). Así `$remote_addr` pasa a ser la IP real y la cabecera sigue sin poder falsearse desde fuera.
- Verificar con dos dispositivos que los contadores son independientes, y añadir el caso a la prueba manual de T07c.
