# BUG-025 — `isVerified` no tiene efecto y todas las cuentas quedan sin verificar

- **Estado:** resuelto (T24a: `isEnabled()` ya no depende de `isVerified`; los límites los decide `VerifiedGuard`)
- **Severidad:** baja
- **Confianza:** confirmado (lectura de código)
- **Área:** backend/user
- **Tarea que lo resuelve:** T24
- **Detectado:** 2026-10-21, análisis inicial del código por Claude

## Dónde
`backend/src/main/java/io/github/yagogefaell/asaltamontes/user/domain/UserAccount.java` (`isEnabled()` devuelve `isVerified`), `AuthenticationProviderImpl`

## Qué pasa
Todas las cuentas se crean con `isVerified=false`. `isEnabled()` depende de ese campo, pero ni el provider ni el filtro JWT lo consultan, así que hoy no tiene efecto. Si alguien activara la comprobación estándar de Spring, **nadie** podría iniciar sesión.

## Cómo reproducir
—

## Arreglo propuesto
Verificación de email real (T24). Mientras tanto, no depender de `isEnabled()`.
