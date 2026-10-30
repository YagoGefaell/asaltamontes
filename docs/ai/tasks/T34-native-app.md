# T34 — App nativa (iOS y Android) con Capacitor

- **Estado:** pendiente (🔍 spec de alto nivel: refinar antes de implementar)
- **Fase:** después de la demo (D-006: primero PWA, la nativa después)
- **Depende de:** T26 (PWA), T18 (notificaciones), T23 (denuncias, bloqueos y moderación), T24 (borrar cuenta), T25 (páginas legales); backend accesible desde internet con dominio y HTTPS (migración de hosting, ver ROADMAP «Después de la demo»)
- **Rama sugerida:** `feat/T34-native-app` (dividir en varias PRs al refinar)
- **Cierra bugs:** —
- **Tamaño:** L

## Objetivo
Publicar Asaltamontes en App Store y Google Play reutilizando el frontend React actual, sin reescribir la interfaz.

## Decisión de enfoque (Yago, 2026-10-30)
- **Capacitor** envuelve la web (`frontend/`) en una app nativa y da acceso a push, cámara, galería y almacenamiento seguro. Se reutiliza ~95 % del código.
- React Native / Expo queda descartado mientras el rendimiento de la interfaz web sea suficiente.
- Orden: Tailscale o dominio con HTTPS → T26 (PWA) → demo → backend en internet → T34 → TestFlight y prueba interna de Play → publicación.

## Requisitos previos (fuera del código)
- **Backend en internet** con dominio y HTTPS: la app de una usuaria no llega a la LAN (D-013). VPS/cloud o exponer la Pi de forma segura (p. ej. Cloudflare Tunnel).
- **Cuentas de desarrollador**: Apple Developer (99 $/año) y Google Play Console (25 $, pago único).
- **Compilación iOS**: un Mac con Xcode o un servicio en la nube (Codemagic, Ionic Appflow, GitHub Actions con runner macOS). Android compila en Linux.
- **Requisitos de las tiendas** (Apple es la más estricta):
  - Borrar la cuenta desde la propia app (T24).
  - Contenido generado por usuarias: denunciar, bloquear y moderar (T23, D-022); sin esto Apple rechaza las redes sociales.
  - Política de privacidad y textos legales (T25).
  - Ficha de privacidad de datos en ambas tiendas.

## Enfoque (a refinar)
- **Proyecto Capacitor** dentro de `frontend/` (o `mobile/`): `ios/` y `android/` generados, mismo build de Vite. Iconos, splash y `theme_color` compartidos con la PWA (T26).
- **Autenticación** (respetando D-012, sin cambiar el diseño de Yago): revisar cómo se guarda el JWT/refresh dentro de la app; usar almacenamiento seguro nativo (Keychain/Keystore) en lugar de localStorage o cookies si hace falta. Cualquier cambio estructural se propone antes.
- **CORS y orígenes**: permitir el origen de Capacitor (`capacitor://localhost` en iOS, `https://localhost` en Android) en el backend, por configuración.
- **Push nativas**: FCM (Android) y APNs (iOS). Backend: registro de tokens de dispositivo por usuaria (migración Flyway, `public_id`), envío desde las notificaciones de T18 y los mensajes de chat. El WebSocket se corta en segundo plano, así que los avisos de mensajes nuevos llegan por push.
- **Enlaces profundos**: Universal Links (iOS) y App Links (Android) para que un enlace a una quedada, comunidad o perfil abra la app.
- **Cámara y galería** para las publicaciones (T22) con el plugin de Capacitor.
- **CI**: build de Android en GitHub Actions; iOS en runner macOS o servicio externo. Firma con secretos de GitHub, nunca en el repo.
- **Pruebas en dispositivo** (Yago tiene iPhone y Android):
  - Android: APK por USB/Android Studio; testers con la prueba interna de Play Console.
  - iPhone: Xcode por cable (sin cuenta de pago caduca a los 7 días); testers con TestFlight.

## Preguntas abiertas (para el refinado)
- Hosting del backend en producción y dominio definitivo.
- ¿Mac propio o compilación iOS en la nube?
- Push: ¿Firebase para ambas plataformas o APNs directo en iOS?
- ¿La web/PWA sigue disponible en paralelo a las apps? (Por defecto, sí.)

## Fuera de alcance
- Reescritura en React Native.
- Funcionalidades solo nativas que no existan en la web (salvo push, cámara y enlaces profundos).
