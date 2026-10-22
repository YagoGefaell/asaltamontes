# BUG-051 — Un mensaje recibido por WebSocket en un chat nunca abierto impide cargar su historial

- **Estado:** resuelto
- **Severidad:** alta
- **Confianza:** confirmado (lectura de código)
- **Área:** frontend/chat
- **Tarea que lo resuelve:** ninguna (PR `fix/` propia)
- **Detectado:** 2026-10-22, bug hunt de frontend-chat (Claude)

## Dónde
- `frontend/src/features/messages/components/conversations.provider.jsx:505-513` → `handleNewMessage` crea `chatMessages[conversationId] = [mensaje]` aunque ese chat no se haya cargado nunca.
- `frontend/src/features/messages/components/conversations.provider.jsx:332-336` → `loadConversationMessages` trata cualquier valor truthy de `chatMessages[conversationId]` como "ya cargado" y no pide el historial.

## Qué pasa
`loadConversationMessages` decide si hay que ir al servidor con `const alreadyLoaded = chatMessages[conversationId]`. Pero `handleNewMessage` rellena esa misma clave con un array de un solo elemento en cuanto llega un mensaje en vivo, sin haber cargado la primera página. Al abrir después ese chat, la UI cree que ya está cargado: solo se ve el mensaje recién llegado, `historyPaging[conversationId]` no existe (no hay "cargar anteriores") y el resto de la conversación es inaccesible hasta recargar la página. Afecta a cualquier chat no abierto en la sesión (el caso normal: te escriben mientras estás en otra pantalla), y también al primer mensaje de un chat nuevo.

## Cómo reproducir
1. Usuaria A y B con una conversación de 20 mensajes. B inicia sesión y NO abre ese chat.
2. A envía un mensaje. B lo recibe (aparece en la lista de chats con el punto de no leído).
3. B abre el chat: ve solo ese último mensaje, sin los 20 anteriores ni forma de cargarlos.
Test (Vitest, con el provider): emitir un payload por `handleNewMessage` y llamar a `loadConversationMessages`; comprobar que se llama a `getConversationMessagesRequest`.

## Arreglo propuesto
Separar "cargado" de "contiene mensajes": usar `historyPaging[conversationId]` (o un `Set` de chats con la primera página cargada) como indicador en `loadConversationMessages`. Alternativa: que `handleNewMessage` solo escriba en `chatMessages` si el chat ya está cargado (el historial lo traerá luego). Añadir test.
