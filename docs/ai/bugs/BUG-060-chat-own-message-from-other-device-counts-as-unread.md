# BUG-060 — Un mensaje propio enviado desde otra pestaña o dispositivo suma un no leído

- **Estado:** abierto
- **Severidad:** baja
- **Confianza:** probable (verificar con dos sesiones del mismo usuario)
- **Área:** frontend/chat
- **Tarea que lo resuelve:** ninguna (PR `fix/` propia)
- **Detectado:** 2026-10-22, bug hunt de frontend-chat (Claude)

## Dónde
`frontend/src/features/messages/components/conversations.provider.jsx:495-559` (`handleNewMessage`: `newUnreadCount = isCurrentlyReading ? 0 : unreadCount + 1` sin comprobar el remitente).

## Qué pasa
El broadcaster envía cada mensaje a todos los participantes, incluida la autora (`ChatMessageBroadcaster`). Si escribe desde el móvil, la sesión del ordenador (donde ese chat no está abierto) recibe su propio mensaje y lo cuenta como no leído: el punto del chat y el badge de la navegación suben por un mensaje que ella misma envió. Pasa igual en la misma pestaña si pulsa "Volver" antes de que llegue el eco.

## Cómo reproducir
Dos sesiones del mismo usuario: enviar un mensaje desde la sesión A con la sesión B en otra pantalla; B muestra un no leído.

## Arreglo propuesto
En `handleNewMessage`, si `payload.senderUsername === currentUser?.username` no incrementar `unreadCount` ni enviar `chat.read`. Test del provider.
