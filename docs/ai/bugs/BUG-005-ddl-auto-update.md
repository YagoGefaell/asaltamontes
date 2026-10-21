# BUG-005 — Hibernate modifica el esquema en producción (`ddl-auto=update`)

- **Estado:** resuelto
- **Severidad:** alta
- **Confianza:** confirmado (lectura de código)
- **Área:** backend/bd
- **Tarea que lo resuelve:** T02
- **Detectado:** 2026-10-21, análisis inicial del código por Claude

## Dónde
`backend/src/main/resources/application.properties`

## Qué pasa
El esquema no está versionado. `update` nunca borra ni renombra columnas, no crea algunos índices ni constraints y puede dejar el esquema en un estado distinto al esperado; un cambio de entidad mal hecho altera la BD con datos reales al desplegar.

## Cómo reproducir
—

## Arreglo propuesto
Flyway con baseline + `ddl-auto=validate` (T02).
