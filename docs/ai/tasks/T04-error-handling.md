# T04 — Manejo de errores unificado

- **Estado:** pendiente
- **Fase:** 0 — Cimientos
- **Depende de:** T01
- **Rama sugerida:** `refactor/T04-error-handling`
- **Cierra bugs:** BUG-010, BUG-011, BUG-036
- **Tamaño:** M

## Objetivo
Que cada error devuelva el código HTTP correcto con un `ProblemDetail` consistente, que los errores inesperados queden registrados en logs y que el frontend pueda distinguir "sesión caducada" (401) de "no tienes permiso" (403).

## Contexto
- `common/exceptions/GlobalExceptionHandler.java`: handler genérico devuelve 500 **sin loguear**.
- Servicios lanzan `IllegalArgumentException`/`RuntimeException` para casos de negocio (`CommunityService`, `MessageService`, `CommunityController.leaveCommunity`) → 500.
- `AlreadyFollowingException`, `SelfFollowException` sin handler.
- `JwtAuthFilter` lanza `InvalidTokenException` dentro del filtro (fuera del `DispatcherServlet`, el `@RestControllerAdvice` no lo captura). Sin `AuthenticationEntryPoint` Spring devuelve 403 para peticiones anónimas.
- `frontend/src/lib/api-client.js` refresca el token ante **401 y 403**.

## Alcance
### Backend
- Jerarquía de excepciones de dominio en `common/exceptions`:
  `NotFoundException` (404), `ForbiddenException` (403), `ConflictException` (409), `BadRequestException` (400). Las existentes (`UserNotFoundException`, `AlreadyFollowingException`, `SelfFollowException`…) pasan a extender de estas.
- Sustituir **todas** las `IllegalArgumentException`/`IllegalStateException`/`RuntimeException` de negocio por la excepción de dominio adecuada (buscar con `grep -rn "new RuntimeException\|new IllegalArgumentException\|new IllegalStateException" backend/src/main`).
- `GlobalExceptionHandler`:
  - Handler por clase base de dominio.
  - `HttpMessageNotReadableException`, `MethodArgumentTypeMismatchException`, `MissingServletRequestParameterException`, `ConstraintViolationException` → 400.
  - `AccessDeniedException` → 403; `AuthenticationException` → 401.
  - `PropertyReferenceException` (sort inválido) → 400.
  - Genérico: `log.error` con stacktrace y request id; respuesta 500 sin detalles internos.
  - `DataIntegrityViolationException` → 409 y `log.warn`.
- Seguridad:
  - `JwtAuthFilter`: no lanzar excepciones; si el token es inválido/expirado, dejar la petición anónima (y opcionalmente anotar el motivo en un atributo de la request).
  - `AuthenticationEntryPoint` propio → 401 `ProblemDetail` JSON (`detail` distingue "token expirado" de "no autenticado" si el filtro dejó el motivo).
  - `AccessDeniedHandler` propio → 403 `ProblemDetail` JSON.
- `FollowService.followUser`: comprobar `existsBy…` antes de guardar o hacer `saveAndFlush` dentro del `try` para que el duplicado produzca `AlreadyFollowingException` (BUG-036).
- Errores en `@MessageMapping` (WebSocket): `@MessageExceptionHandler` que envíe el error a `/user/queue/errors` en vez de perderlo.

- **D-012**: los cambios en `JwtAuthFilter`, `SecurityConfig` y `api-client.js` deben ser mínimos y conservar su estructura; solo se cambia cómo se señaliza el error.

### Frontend
- `api-client.js`: refrescar **solo ante 401**. Un 403 se propaga como error de permisos.
- Suscribirse a `/user/queue/errors` y mostrar el error (toast simple o `console.warn` + estado en el chat; lo mínimo que no se pierda).

## Fuera de alcance
- Cambiar el modelo de tokens → T06.

## Criterios de aceptación
- [ ] Ningún `RuntimeException`/`IllegalArgumentException` genérico para errores de negocio en `backend/src/main`.
- [ ] Petición sin token o con token caducado a `/api/**` → 401 JSON. Con token válido pero sin permiso → 403 JSON.
- [ ] Comunidad inexistente → 404; salir siendo propietaria → 409 (o 400, decidir y documentar); seguir a alguien ya seguido → 409.
- [ ] Todo 500 aparece en el log con stacktrace.
- [ ] El frontend no intenta refrescar ante 403.

## Tests requeridos
- IT por cada código: 400 (JSON malformado, sort inválido), 401 (sin token, token caducado, token mal firmado), 403, 404, 409.
- Test de `api-client` frontend: 403 no dispara refresh, 401 sí.
