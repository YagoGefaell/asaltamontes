# BUG-042 — El email se puede cambiar sin verificar el nuevo ni confirmar con la contraseña

- **Estado:** abierto
- **Severidad:** media
- **Confianza:** confirmado (lectura de código)
- **Área:** backend/user
- **Tarea que lo resuelve:** T24 (verificación de email y ciclo de vida de la cuenta)
- **Detectado:** 2026-10-21, bug hunting del área user

## Dónde
`user/service/UserProfileUpdater.java` (`Optional.ofNullable(dto.email())…ifPresent(user::setEmail)`), `PATCH /api/users/me`.

## Qué pasa
Con solo un access token válido se cambia el email de la cuenta: no se pide la contraseña actual ni se verifica que el nuevo email sea de la usuaria, e `isVerified` no se reinicia. Cuando T24 añada "olvidé mi contraseña" (que envía el enlace al email), quien consiga un access token durante 15 minutos (un dispositivo desbloqueado, un XSS) podría cambiar el email y después quedarse la cuenta con el reset de contraseña.

## Cómo reproducir
`PATCH /api/users/me {"email": "otra@ejemplo.com"}` con un Bearer válido → 200 y el email cambia.

## Arreglo propuesto
En T24: el cambio de email pide la contraseña actual (o un login reciente) y queda **pendiente** hasta confirmar el enlace enviado al email nuevo, con aviso al email anterior. Mientras tanto, como mínimo, quitar `email` de `UserUpdateDto`.
