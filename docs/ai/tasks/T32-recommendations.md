# T32 — Recomendaciones con puntuación

- **Estado:** pendiente (🔍 spec de alto nivel: refinar antes de implementar)
- **Fase:** después de T22 (descubrimiento)
- **Depende de:** T22 (publicaciones y Explorar), T29l (fila «Sugeridas para ti»); usa T33 si ya existe
- **Rama sugerida:** `feat/T32-recommendations`
- **Cierra bugs:** —
- **Tamaño:** L (dividir en varias PRs al refinar)

## Objetivo
Que a cada usuaria le aparezcan las comunidades, quedadas, usuarias y publicaciones que más valor tienen para ella, en lugar de listas ordenadas por fecha o por ciudad. Es lo que hace útiles «Sugeridas para ti» (Comunidades), las filas de sugerencias de Inicio y Explorar (Buscar).

## Decisiones de Yago (2026-10-29)
- Una **puntuación** común, aplicada a cuatro tipos: comunidades, quedadas, usuarias y (más adelante) publicaciones de Explorar.
- **Señales** que cuentan (todas):
  - **Cercanía**: distancia a la ciudad o zona de la usuaria, sin pedir GPS (como las sugerencias actuales).
  - **Tamaño y actividad**: miembros o seguidoras, y actividad reciente (quedadas, mensajes o publicaciones de los últimos días). Una comunidad grande pero sin actividad puntúa poco.
  - **Su círculo**: comunidades donde están personas que sigue, quedadas a las que van ellas, usuarias a las que siguen sus seguidas.
  - **Verificada** (T33): bonus a las comunidades verificadas.
  - **Afinidad**: parecido con lo que ya hace (tipo de deporte o nivel de las quedadas a las que va).
- **Explorar** es para descubrir lo que **no sigue**: fotos de perfiles y comunidades públicas que no sigue, ordenadas por la puntuación, sin repetir lo que ya ve en Inicio.
- Nunca se recomienda lo que no puede ver (comunidades privadas a no miembros, perfiles privados a no seguidoras, T23) ni a usuarias con bloqueo en cualquier dirección (D-022).

## Enfoque (a refinar)
- Primera versión **sin infraestructura nueva**: puntuación calculada en SQL con pesos configurables (`asaltamontes.recommendations.*`), y si hace falta una tabla de agregados (actividad de los últimos 7/30 días por comunidad) refrescada periódicamente, no en cada petición.
- Endpoints paginados con cursor opaco (D-021): p. ej. `GET /api/recommendations/communities|events|users`. Explorar reutiliza la misma puntuación en `GET /api/explore/posts`.
- Las pantallas no cambian: T29l y las filas de Inicio pasan a consumir estos endpoints.
- Documentar los pesos iniciales y cómo ajustarlos; dejar medido el coste de la consulta con el volumen de T28.

## Preguntas abiertas (para el refinado)
- Pesos iniciales de cada señal y si la usuaria puede «ocultar» una sugerencia.
- Si las quedadas recomendadas se limitan a una ventana de fechas (p. ej. próximos 14 días).
- Explorar: cuánto pesa lo reciente frente a lo popular (sin cerrar por Yago todavía).

## Fuera de alcance
- Aprendizaje automático, ranking personalizado por clics, geolocalización por GPS (solo con «Cerca de mí», más adelante).
