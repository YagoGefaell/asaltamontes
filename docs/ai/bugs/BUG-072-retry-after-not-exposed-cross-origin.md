# BUG-072 — `Retry-After` no es legible desde el navegador en peticiones entre orígenes

- **Estado:** resuelto
- **Severidad:** baja
- **Confianza:** probable
- **Área:** backend/infra (CORS)
- **Tarea que lo resuelve:** ninguna (PR `fix/` propia)
- **Detectado:** 2026-10-27, al implementar T19b

## Dónde
`infrastructure/config/SecurityConfig.java` (configuración CORS): no define `exposedHeaders`.

## Qué pasa
Con el frontend en otro origen que la API (p. ej. Vite en `:5173` contra el backend en `:8080`), el navegador oculta `Retry-After` a JavaScript salvo que se exponga con `Access-Control-Expose-Headers`. El frontend (T19b) muestra el tiempo de espera cuando lo lee y, si no, un mensaje genérico, así que no se rompe. Tras nginx (mismo origen) funciona.

## Cómo reproducir
Rechazar una solicitud de unión y volver a solicitar desde `npm run dev` con `VITE_API_URL` apuntando al backend: la respuesta 429 trae la cabecera, pero `response.headers.get("Retry-After")` devuelve `null`.

## Arreglo propuesto
`configuration.setExposedHeaders(List.of("Retry-After"))` en el `CorsConfigurationSource`, con un test de la cabecera `Access-Control-Expose-Headers` en una respuesta 429.
