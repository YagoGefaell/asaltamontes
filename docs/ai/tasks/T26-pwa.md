# T26 — PWA instalable

- **Estado:** pendiente
- **Fase:** 4 — Demo privada
- **Depende de:** T07
- **Rama sugerida:** `feat/T26-pwa`
- **Cierra bugs:** —
- **Tamaño:** M
- **Spec:** 🔍 alto nivel — **refinar antes de implementar** (completar con la plantilla `docs/ai/templates/TASK.md` mirando el código de ese momento).

## Objetivo
Que las testers instalen la app en el móvil como si fuera nativa, sin pasar por las tiendas.

## Enfoque
- `vite-plugin-pwa`: manifest (nombre, iconos 192/512/maskable, `theme_color`, `display: standalone`), service worker con precache del shell y estrategia `NetworkFirst` para la API (nunca cachear respuestas autenticadas de forma persistente).
- Pantalla "Instalar app" (instrucciones para iOS, Android y escritorio).
- Revisión mobile: `viewport`, safe areas en iOS, teclado sobre el input del chat, tamaño de los objetivos táctiles.
- Lighthouse PWA/Performance/Accessibility ≥ 90 en Home y Chat (informe en la PR).
- **Requiere HTTPS** para el service worker fuera de `localhost`. El servidor solo está en la LAN (D-013) → depende de que Yago elija Tailscale o mkcert en T07.
