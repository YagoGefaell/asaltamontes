# BUG-077 — ProfileAndHomeIT: el test de conteo de sentencias falla en la suite completa de main

- **Estado:** resuelto
- **Severidad:** alta
- **Confianza:** confirmado
- **Área:** backend/user
- **Tarea que lo resuelve:** ninguna (PR `fix/` propia)
- **Detectado:** 2026-10-28, `./mvnw verify` sobre `origin/main` (2b7a8db) y en la CI de la PR #113

## Dónde
`backend/src/test/java/io/github/yagogefaell/asaltamontes/user/controller/ProfileAndHomeIT.java:248` (`suggestions_andListings_doNotRunOneQueryPerRow`)

## Qué pasa
En la suite completa falla siempre: `4x the rows must not mean more statements: 19 -> 20`. Aislado (`-Dit.test=ProfileAndHomeIT`) pasa. Como `validate-backend` es un check obligatorio, bloquea el merge de cualquier PR de backend.

## Cómo reproducir
`cd backend && ./mvnw verify` sobre `main`; el test falla con 19 -> 20 sentencias.

## Arreglo propuesto
Averiguar qué sentencia aparece solo en la segunda ronda cuando la suite comparte contexto (caché de segundo nivel, consulta de una sola vez como la de configuración, o N+1 real en suggestions / listados). Si es N+1 real, arreglar la consulta; si es calentamiento, hacer una ronda de calentamiento antes de medir.

## Causa raíz y resolución
No era un N+1: las consultas de suggestions y listados son idénticas (20 sentencias, las mismas en las dos rondas, comprobado con el log de `org.hibernate.SQL` en la suite completa). El conteo global de `Statistics` incluía SQL de las tareas asíncronas de `NotificationListener` (`@Async` tras commit, disparadas por el follow y la creación de eventos del montaje) que aún corrían al hacer `stats.clear()` o al leer el contador: una carrera que depende de la carga de la máquina. El test espera ahora a que el executor de notificaciones esté ocioso antes de resetear y antes de leer el contador; la aserción (4x filas, no más sentencias) no se debilita.
