# T06 — Endurecimiento de autenticación

- **Estado:** pendiente
- **Fase:** 0 — Cimientos
- **Depende de:** T02, T04
- **Rama sugerida:** `feat/T06-auth-hardening` (dividir: `T06a` tokens, `T06b` validación + rate limit + Google)
- **Cierra bugs:** BUG-002, BUG-003, BUG-023, BUG-024, BUG-032, BUG-033
- **Tamaño:** L

## Objetivo
Tokens que no se pueden intercambiar entre sí, sesiones revocables (logout real, "cerrar sesión en todos los dispositivos"), protección frente a fuerza bruta y validación consistente front/back.

## Contexto
- `JwtUtil`: access y refresh firmados con la misma clave, sin claim de tipo → un refresh token sirve como `Bearer` y viceversa.
- `AuthController.logout` solo borra la cookie; el refresh token sigue siendo válido 7 días.
- `RegisterRequest`/`LoginRequest` sin Bean Validation; `@RequestBody` sin `@Valid`.
- `GoogleTokenVerifier`/`UserService.findOrCreateGoogleUser`: no comprueba `email_verified`; username `givenName + "_" + millis`.
- Front `config/constants.js` `VALIDATION`: username 3–20 `[a-zA-Z0-9_]`, password ≥ 8; back: username 3–50 `[a-zA-Z0-9._]`, password ≥ 6.

## Alcance
### T06a — Tokens
- Claim `typ` (`access` | `refresh`) en ambos. `JwtAuthFilter` y `WebSocketJwtInterceptor` aceptan solo `access`; `/auth/refresh` solo `refresh`.
- Tabla `refresh_tokens` (migración): `id`, `user_id` FK, `token_hash` (SHA-256), `family_id` (UUID), `expires_at`, `revoked_at`, `replaced_by_id`, `created_at`, `user_agent`, `ip`. Índices por `token_hash` (único) y `user_id`.
- El refresh token pasa a ser **opaco aleatorio** (32 bytes, base64url), no JWT. Se guarda solo su hash.
- **Rotación**: cada `/auth/refresh` revoca el actual y emite uno nuevo en la misma familia (nueva cookie). **Detección de reutilización**: si llega un token ya revocado → revocar toda la familia (posible robo) → 401.
- `/auth/logout`: revoca el token de la cookie.
- `POST /api/users/me/sessions/revoke-all` (cerrar sesión en todos los dispositivos).
- Job programado diario que borra tokens expirados hace > 30 días.
- `refresh` comprueba que la usuaria existe y no está deshabilitada.

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
- [ ] Reutilizar un refresh token ya rotado revoca la familia (los dos tokens fallan).
- [ ] 6.º intento de login en un minuto → 429.
- [ ] Registro con datos inválidos → 400 con errores por campo; el frontend valida lo mismo antes de enviar.
- [ ] Login con Google de un email no verificado → 401.

## Tests requeridos
- IT para cada criterio de aceptación.
- Unit test del generador de usernames de Google (acentos, espacios, colisiones, null).

## Notas y riesgos
- Al desplegar T06a todas las sesiones existentes se invalidan (los refresh JWT antiguos dejan de valer) → avisar en la PR.
