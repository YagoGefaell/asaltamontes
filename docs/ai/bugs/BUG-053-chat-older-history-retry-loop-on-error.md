# BUG-053 — Si falla la carga de mensajes anteriores, se reintenta en bucle sin pausa

- **Estado:** abierto
- **Severidad:** media
- **Confianza:** probable (la lógica está confirmada por lectura; el bucle depende del comportamiento de `IntersectionObserver`: verificar con el backend parado)
- **Área:** frontend/chat
- **Tarea que lo resuelve:** ninguna (PR `fix/` propia)
- **Detectado:** 2026-10-22, bug hunt de frontend-chat (Claude)

## Dónde
- `frontend/src/pages/messages/components/ChatPanel.jsx:408-422` (efecto con `IntersectionObserver`, dependencias `loadingOlder` y `onLoadOlder`).
- `frontend/src/pages/messages/Messages.jsx:355` (`onLoadOlder` es una función en línea nueva en cada render).
- `frontend/src/features/messages/components/conversations.provider.jsx:373-375` (el `catch` deja `hasMore` en `true` y `loading: false`).

## Qué pasa
Cuando `loadOlderMessages` falla (offline, 403, 500, timeout de 10 s), el `catch` restablece `loading: false` conservando `hasMore: true`. El cambio de `loadingOlder` hace que el efecto del panel se re-ejecute: se crea un `IntersectionObserver` nuevo, que notifica de inmediato el estado inicial del sentinel; si el principio de la lista está visible (chat corto o usuaria arriba del todo) vuelve a llamar a `onLoadOlder`, que vuelve a fallar. Resultado: un bucle de peticiones fallidas a la velocidad de la red, sin backoff ni mensaje de error. Además, como `onLoadOlder` cambia en cada render, el observador se recrea con cada pulsación en el input del mensaje, relanzando la carga si el sentinel es visible.

## Cómo reproducir
1. Abrir un chat con más de una página de historial y subir hasta el principio.
2. Parar el backend (o cortar la red) y subir de nuevo: el panel de red muestra peticiones `?before=…` en bucle.

## Arreglo propuesto
Guardar un estado `error` en `historyPaging` que detenga la carga automática y muestre "No se pudieron cargar los mensajes. Reintentar" con botón manual; estabilizar `onLoadOlder` con `useCallback` (o leerlo desde un ref en el efecto) para no recrear el observador en cada render. Test del panel con `loadOlder` rechazado.
