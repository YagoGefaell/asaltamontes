# BUG-052 — Al salir de /messages con un chat abierto, `activeChatId` queda fijo y los mensajes nuevos se dan por leídos

- **Estado:** abierto
- **Severidad:** media
- **Confianza:** confirmado (lectura de código)
- **Área:** frontend/chat
- **Tarea que lo resuelve:** ninguna (PR `fix/` propia)
- **Detectado:** 2026-10-22, bug hunt de frontend-chat (Claude)

## Dónde
- `frontend/src/pages/messages/Messages.jsx:107-111` (`handleLeaveChat` es el único sitio que hace `setActiveChatId(null)`; no hay cleanup al desmontar `Messages`).
- `frontend/src/features/messages/components/conversations.provider.jsx:499-503, 546` (`isCurrentlyReading` se calcula solo con `activeChatId`).

## Qué pasa
`ConversationsProvider` vive en toda la app, pero `activeChatId` solo se limpia con el botón "Volver" del chat. Si la usuaria está dentro de un chat y navega con la barra de navegación (Inicio, Buscar, Perfil…), `Messages` se desmonta y `activeChatId` conserva el id. Desde ese momento, cada mensaje de ese chat se marca como leído (envía `chat.read` al servidor), pone `unreadCount = 0` y no incrementa el badge de la navegación. La usuaria no ve el mensaje ni recibe aviso de que existe. Lo mismo ocurre al volver a `/messages`: se muestra la lista (el `selectedChat` local se reinicia) pero el chat sigue "activo". También con la pestaña en segundo plano (`document.hidden`) se considera que lee. Al cerrar sesión tampoco se reinicia `activeChatId`.

## Cómo reproducir
1. Abrir un chat, pulsar "Inicio" en la barra de navegación.
2. Desde otra cuenta, escribir en ese chat.
3. El badge de mensajes no sube y, al volver a la lista, el chat aparece como leído; el servidor también lo tiene como leído.

## Arreglo propuesto
Efecto de limpieza en `Messages` (`useEffect(() => () => setActiveChatId(null), [])`) y limpiar también `activeChatId` en el reset por `!isAuthenticated` del provider. Considerar exigir `document.visibilityState === 'visible'` en `isCurrentlyReading`. Test del provider.
