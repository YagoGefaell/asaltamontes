# BUG-017 — El mensaje se difunde por WebSocket antes del commit

- **Estado:** abierto
- **Severidad:** baja
- **Confianza:** confirmado (lectura de código)
- **Área:** backend/chat
- **Tarea que lo resuelve:** T09
- **Detectado:** 2026-10-21, análisis inicial del código por Claude

## Dónde
`backend/src/main/java/io/github/yagogefaell/asaltamontes/message/service/MessageService.java` → `processIncomingMessage()` (`@Transactional`) llama a `sendMessageToConversation` dentro de la transacción

## Qué pasa
Si el commit falla, las clientas ya han recibido un mensaje que no existe. Si un cliente pide el historial justo al recibirlo, puede no encontrarlo todavía.

## Cómo reproducir
—

## Arreglo propuesto
`@TransactionalEventListener(AFTER_COMMIT)` (T09).
