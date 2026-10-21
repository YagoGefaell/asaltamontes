# BUG-037 — No hay tests y la CI solo compila

- **Estado:** abierto
- **Severidad:** media
- **Confianza:** confirmado (lectura de código)
- **Área:** ci
- **Tarea que lo resuelve:** T01
- **Detectado:** 2026-10-21, análisis inicial del código por Claude

## Dónde
`backend/src/test/.../AsaltamontesApplicationTests.java`, `.github/workflows/ci.yml`

## Qué pasa
El único test (`contextLoads`) necesita una BD real y variables de entorno, y la CI no lo ejecuta (`mvn compile`). No hay red de seguridad para ningún cambio.

## Cómo reproducir
—

## Arreglo propuesto
T01.
