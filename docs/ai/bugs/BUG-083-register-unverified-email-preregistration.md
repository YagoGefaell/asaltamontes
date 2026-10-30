# BUG-083 — Pre-registro con el email de otra persona y enlace con Google

- **Estado:** abierto
- **Severidad:** media
- **Confianza:** probable (lectura de código, sin test)
- **Área:** backend/auth
- **Tarea que lo resuelve:** T24b (junto al reset de contraseña)
- **Detectado:** 2026-10-30, revisión de T24a por Claude

## Dónde
`backend/src/main/java/io/github/yagogefaell/asaltamontes/user/service/UserService.java` (`registerUserWithProfile`, `findOrCreateGoogleUser`), `AuthService.googleLogin`

## Qué pasa
`POST /auth/register` no comprueba que quien se registra sea dueña del email. Si alguien registra la cuenta `victima@x.com` con una contraseña que ella conoce y, más tarde, la dueña real entra con Google con ese email, `findOrCreateGoogleUser` localiza la cuenta por email y le da una sesión sobre **la cuenta que creó la atacante** (que sigue conociendo la contraseña y puede entrar a la vez). Existe desde antes de T24a: la verificación de email no lo cierra porque la cuenta existente no se toca al entrar con Google (T24a no la marca como verificada a propósito).

## Cómo reproducir
1. Registrar `x@test.com` con contraseña `A` (sin verificar).
2. Entrar con Google como `x@test.com`: la sesión es de la cuenta registrada en 1, y `A` sigue funcionando.

## Arreglo propuesto
En `findOrCreateGoogleUser`, si la cuenta encontrada tiene `isVerified = false` y es de contraseña: Google demuestra la propiedad del email, así que se marca verificada, se **borra su `passwordHash`** (la dueña elige una nueva con «olvidé mi contraseña», T24b) y se revocan todas sus `RefreshSession`. Requiere T24b para no dejar sin contraseña a quien se registró de buena fe. Pregunta para Yago: ¿OK a que entrar con Google sobre una cuenta sin verificar elimine su contraseña?
