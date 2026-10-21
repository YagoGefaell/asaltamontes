# BUG-040 — La app desplegada no funciona desde el navegador: falta `CORS_ALLOWED_ORIGINS` y `VITE_API_URL` apunta a la IP antigua

- **Estado:** abierto
- **Severidad:** alta
- **Confianza:** confirmado (comprobado en el servidor el 2026-10-21)
- **Área:** infra
- **Tarea que lo resuelve:** ninguna en código: cambio de configuración en el servidor (Yago). T07 elimina la causa de fondo (nginx same-origin).
- **Detectado:** 2026-10-21, Claude al preparar T03

## Dónde
`/etc/github-runner.env` del servidor (el `--env-file` de `.github/workflows/deploy.yml`), `docker-compose.yml` (`CORS_ALLOWED_ORIGINS: ${CORS_ALLOWED_ORIGINS}`).

## Qué pasa
1. `/etc/github-runner.env` no define `CORS_ALLOWED_ORIGINS`. Compose pasa la variable **vacía** al backend, Spring no aplica el valor por defecto (la variable existe, aunque esté vacía) y la lista de orígenes queda vacía: el preflight CORS devuelve **403 desde cualquier origen**, incluido `http://localhost`.
2. `VITE_API_URL=http://192.168.1.157:8080`, pero la Pi ahora es la **.158**: el bundle del frontend llama a una IP que ya no es la del servidor.

El resultado es que el login y todo lo demás fallan en el navegador aunque los contenedores estén "Up" y el deploy salga verde. Con T03 (#20) el WebSocket también usa la lista CORS, así que la falta de la variable afectará igual al chat.

## Cómo reproducir
```bash
curl -i -X OPTIONS http://localhost:8080/auth/login -H "Origin: http://192.168.1.158" -H "Access-Control-Request-Method: POST"   # → 403
curl -s http://localhost/ | grep -o 'assets/index-[^"]*\.js' | xargs -I{} curl -s http://localhost/{} | grep -o 'http://192.168.1.15[0-9]:8080'  # → .157
```

## Arreglo propuesto
- **Ya (Yago, en el servidor):** en `/etc/github-runner.env`, añadir `CORS_ALLOWED_ORIGINS=http://192.168.1.158` y cambiar `VITE_API_URL=http://192.168.1.158:8080`; relanzar el último deploy (`gh run rerun <id>` o un merge cualquiera). Pedir antes la reserva DHCP de la .158, si no esto volverá a pasar.
- **T07:** con nginx haciendo de proxy same-origin y `VITE_API_URL` vacío, ni la IP ni CORS dependen de la configuración del servidor.
- Opcional: que el paso de verificación del deploy (T07) haga un preflight CORS con el origen real y falle si no es 200.
