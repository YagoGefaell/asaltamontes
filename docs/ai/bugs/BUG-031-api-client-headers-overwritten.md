# BUG-031 — `api-client` pierde `Authorization` si una llamada pasa `headers`

- **Estado:** abierto
- **Severidad:** baja
- **Confianza:** confirmado (lectura de código)
- **Área:** frontend/lib
- **Tarea que lo resuelve:** ninguna (PR `fix/` propia)
- **Detectado:** 2026-10-21, análisis inicial del código por Claude

## Dónde
`frontend/src/lib/api-client.js` → `_makeRequest` (`...options` se esparce después de `headers`)

## Qué pasa
`config = { headers: {...auth, ...options.headers}, ...options }`: como `options` contiene `headers`, el último spread sustituye el objeto `headers` combinado por el del llamante → se pierden `Authorization`, `Content-Type` y `Accept`. Hoy solo afecta a `loginWithGoogleRequest` (que no necesita token), pero cualquier llamada futura con cabeceras propias fallará con 401.

## Cómo reproducir
Test Vitest: `apiClient.get('/x', { headers: { 'X-A': '1' } })` → el `fetch` no recibe `Authorization`.

## Arreglo propuesto
Separar `headers` del resto de `options` antes de construir `config`. Test incluido.
