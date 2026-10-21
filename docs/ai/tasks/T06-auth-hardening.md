# T06 — Endurecimiento de autenticación

- **Estado:** hecho
- **Fase:** 0 — Cimientos
- **Depende de:** T02, T04
- **Rama sugerida:** `feat/T06-auth-hardening` (dividir: `T06a` tokens, `T06b` validación + rate limit + Google)
- **Cierra bugs:** BUG-002, BUG-003, BUG-023, BUG-024, BUG-032, BUG-033
- **Tamaño:** L

## Progreso
- **T06a — tokens** (hecho): claim `typ`, `jti` + tabla `refresh_sessions` (V3), logout que revoca, `POST /api/users/me/sessions/revoke-all`, job de limpieza y botón en Ajustes. Cierra BUG-002, BUG-003 y BUG-040. Sin la columna `user_agent` (no la necesita ningún criterio; se añadirá si se hace una pantalla de "sesiones activas"). La rotación del refresh va como propuesta en la PR.
- **T06b — validación, rate limiting y Google** (hecho): reglas únicas en `UserFieldRules` (Bean Validation → 400 + `UserValidator`) replicadas en `constants.js`/`REGISTER_RULES`; `AuthRateLimitFilter` (en memoria, ventana fija, configurable con `asaltamontes.rate-limit.*`); `email_verified` y `UsernameGenerator` para Google. Cierra BUG-023, BUG-024, BUG-032 y BUG-033. La confirmación de contraseña sigue en `UserValidator` (409), como antes.

## Objetivo
Tokens que no se pueden intercambiar entre sí, sesiones revocables (logout real, "cerrar sesión en todos los dispositivos") **sin cambiar la arquitectura actual**, protección frente a fuerza bruta y validación consistente front/back.

## Contexto
- `JwtUtil`: access y refresh firmados con la misma clave, sin claim de tipo → un refresh token sirve como `Bearer` y viceversa.
- `AuthController.logout` solo borra la cookie; el refresh token sigue siendo válido 7 días.
- `RegisterRequest`/`LoginRequest` sin Bean Validation; `@RequestBody` sin `@Valid`.
- `GoogleTokenVerifier`/`UserService.findOrCreateGoogleUser`: no comprueba `email_verified`; username `givenName + "_" + millis`.
- Front `config/constants.js` `VALIDATION`: username 3–20 `[a-zA-Z0-9_]`, password ≥ 8; back: username 3–50 `[a-zA-Z0-9._]`, password ≥ 6.

## Alcance
### T06a — Tokens (extendiendo el diseño actual, ver D-012)
> **Restricción:** se mantiene la arquitectura de Yago: refresh JWT en cookie httpOnly, access JWT en memoria, `JwtUtil`, `JwtAuthFilter`, `AuthService`, `AuthController` y `CookieUtil`. No se cambia el tipo de token ni se añaden frameworks. Todo lo que vaya más allá de lo descrito aquí se propone en la PR y espera el OK de Yago.

- **Claim de tipo**: `JwtUtil.generateAccessToken` ya construye un `Map` de claims → añadir `typ=access`; `generateRefreshToken` añade `typ=refresh`. Método `JwtUtil.isType(claims, type)`. `JwtAuthFilter` y `WebSocketJwtInterceptor` aceptan solo `access`; `AuthService.refreshAccessToken` solo `refresh`.
- **Revocación con `jti`**: el refresh JWT lleva un `jti` (UUID). Nueva tabla mínima `refresh_sessions` (`jti` PK, `user_id` FK, `expires_at`, `revoked_at`, `created_at`, `user_agent`) + migración. Se crea una fila al emitir el refresh (`AuthService.generateTokens`).
  - `refreshAccessToken`: además de validar firma y caducidad como hoy, comprueba que el `jti` existe y no está revocado, y que la usuaria existe.
  - `logout`: marca el `jti` de la cookie como revocado (y borra la cookie como ahora).
  - `POST /api/users/me/sessions/revoke-all` → revoca todas las sesiones de la usuaria.
  - Job diario que borra las filas caducadas hace más de 30 días.
- **Decisión de Yago (2026-10-21): no se implementa en T06a.** Se entrega como **propuesta aparte** (en la descripción de la PR o en una PR `docs/`, con el diff estimado) para que Yago decida: rotar el refresh en cada `/auth/refresh` (nuevo `jti` + nueva cookie, revocando el anterior) con detección de reutilización. Mejora la seguridad, pero cambia el comportamiento del flujo actual; implementarlo solo si Yago lo aprueba.

### T06b — Validación, rate limiting y Google
- Bean Validation en `RegisterRequest`, `LoginRequest`, `UserUpdateDto`, `CreateCommunityDto`… y `@Valid` en los controladores. Mantener los mensajes en español que ya usa `UserValidator`; las comprobaciones de unicidad siguen en el validador.
- **Reglas únicas** (fuente de verdad backend, replicar en `frontend/src/config/constants.js`):
  username 3–30, `^[a-zA-Z0-9._]+$`, sin punto inicial/final; password 8–100; fullName 2–60 letras, espacios, guiones y apóstrofos (`^[\p{L}][\p{L} '\-]*$`).
  Las cuentas existentes con contraseñas de 6-7 caracteres siguen pudiendo iniciar sesión (la regla aplica a registro/cambio).
- Rate limiting en memoria (Bucket4j o implementación simple con Caffeine) por IP **y** por username: `/auth/login` 5/min, `/auth/register` 3/min/IP, `/auth/refresh` 30/min. 429 `ProblemDetail` con `Retry-After`. IP real vía `X-Forwarded-For` (solo confiando en el proxy de T07).
- Google: rechazar si `email_verified != true`. Username generado: normalizar `givenName` (quitar acentos, minúsculas, solo `[a-z0-9._]`, fallback `runner`), truncar y añadir sufijo numérico corto hasta que sea único.

### Frontend
- `auth.service.js`: el refresh ahora rota la cookie (no requiere cambios si sigue usando `credentials: 'include'`; verificar).
- Ajustar `VALIDATION` y mensajes de los formularios.
- Botón "Cerrar sesión en todos los dispositivos" en Ajustes.

## Criterios de aceptación
- [ ] Usar un refresh token como `Bearer` → 401. Usar un access token en `/auth/refresh` → 401.
- [ ] Tras logout, el refresh token anterior → 401.
- [ ] Tras "cerrar sesión en todos los dispositivos", todos los refresh tokens de la usuaria → 401.
- [ ] Las clases y el flujo de autenticación existentes se conservan (diff acotado; revisable por Yago en minutos).
- [ ] 6.º intento de login en un minuto → 429.
- [ ] Registro con datos inválidos → 400 con errores por campo; el frontend valida lo mismo antes de enviar.
- [ ] Login con Google de un email no verificado → 401.

## Tests requeridos
- IT para cada criterio de aceptación.
- Unit test del generador de usernames de Google (acentos, espacios, colisiones, null).

## Notas y riesgos
- Al desplegar T06a, los refresh JWT emitidos antes no tienen `jti` → dejan de valer y hay que volver a iniciar sesión (aceptable: no hay usuarias reales, D-014).
- D-012: esta tarea toca el código del que Yago está más orgulloso. Diff mínimo, mismo estilo, y explicar en la PR cada cambio en las clases de `auth/` e `infrastructure/security/`.
