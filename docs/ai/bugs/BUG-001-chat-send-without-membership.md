# BUG-001 — Cualquier usuaria puede enviar mensajes a cualquier conversación

- **Estado:** abierto
- **Severidad:** crítica
- **Confianza:** confirmado (lectura de código)
- **Área:** backend/chat
- **Tarea que lo resuelve:** T09
- **Detectado:** 2026-10-21, análisis inicial del código por Claude

## Dónde
`backend/src/main/java/io/github/yagogefaell/asaltamontes/message/service/MessageService.java` → `saveMessage()` · `backend/src/main/java/io/github/yagogefaell/asaltamontes/message/controller/ChatController.java`

## Qué pasa
`processIncomingMessage` guarda y difunde el mensaje sin comprobar que la remitente participa en la conversación. Basta con conocer o adivinar un `conversationId` (son secuenciales) para escribir en chats privados ajenos, y el mensaje se difunde a sus participantes.

## Cómo reproducir
Conectarse por STOMP con una usuaria A y enviar `/app/chat.send` con `{conversationId: <chat entre B y C>, content: "hola"}`. B y C reciben el mensaje.

## Arreglo propuesto
Buscar `ConversationParticipant` por `(conversationId, senderId)` y lanzar `ForbiddenException` si no existe. Test IT con cliente STOMP.
