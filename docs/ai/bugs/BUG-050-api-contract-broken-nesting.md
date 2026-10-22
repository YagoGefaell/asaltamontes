# BUG-050 — `docs/api-contract-v1.json` está mal anidado: todo cuelga de `/auth/register`

- **Estado:** abierto
- **Severidad:** baja
- **Confianza:** confirmado (`json.load`: `paths` solo tiene `/auth/register` y `components`)
- **Área:** docs
- **Tarea que lo resuelve:** ninguna (PR `docs/` propia)
- **Detectado:** 2026-10-22, Claude durante T15a

## Dónde
`docs/api-contract-v1.json`, línea ~64: el `},` cierra `post` de `/auth/register`, pero falta la llave que cierra `/auth/register`.

## Qué pasa
El archivo es JSON válido, pero el resto de rutas y hasta `components` quedan **dentro** de `/auth/register`. Para un visor OpenAPI (Swagger UI, Redoc) o un generador de clientes, el contrato solo tiene un endpoint y no tiene esquemas. Quien lo lee a mano no lo nota.

## Cómo reproducir
`python3 -c "import json; d=json.load(open('docs/api-contract-v1.json')); print(list(d), list(d['paths']))"` → `['openapi', 'info', 'servers', 'paths'] ['/auth/register', 'components']`.

## Arreglo propuesto
Añadir la llave que falta (y quitar una al final), y validar en CI con `npx @redocly/cli lint` o, como mínimo, un script que compruebe que `components` y todas las rutas están en su sitio.
