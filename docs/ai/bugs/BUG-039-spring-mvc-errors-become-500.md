# BUG-039 — Las rutas inexistentes y otros errores de Spring MVC devuelven 500

- **Estado:** resuelto
- **Severidad:** media
- **Confianza:** confirmado (test en T03: `GET /actuator/env` autenticada → 500)
- **Área:** backend/common
- **Tarea que lo resuelve:** T04
- **Detectado:** 2026-10-21, al escribir los tests de Actuator de T03

## Dónde
`backend/src/main/java/io/github/yagogefaell/asaltamontes/common/exceptions/GlobalExceptionHandler.java` → `handleGeneric(Exception)`

## Qué pasa
`@ExceptionHandler(Exception.class)` atrapa también las excepciones propias de Spring MVC, que deberían tener su código HTTP: `NoResourceFoundException` (ruta inexistente → 404), `HttpRequestMethodNotSupportedException` (405), `HttpMessageNotReadableException` (JSON malformado → 400), `MissingServletRequestParameterException` (400), `MethodArgumentTypeMismatchException` (400)… Todas acaban en un 500 "An unexpected error occurred", lo que confunde al frontend y ensucia cualquier métrica de errores de servidor.

## Cómo reproducir
Con un token válido: `GET /api/no-existe` o `GET /actuator/env` → 500 (debería ser 404).

## Arreglo propuesto
En T04: que `GlobalExceptionHandler` extienda `ResponseEntityExceptionHandler` (ya construye `ProblemDetail` con el código correcto para todas las excepciones de Spring MVC) o añadir handlers explícitos, y dejar el genérico solo para lo realmente inesperado (y que lo loguee). Test IT por cada código.
