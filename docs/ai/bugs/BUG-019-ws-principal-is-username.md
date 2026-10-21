# BUG-019 — Tras cambiar el username no llegan los mensajes en tiempo real

- **Estado:** resuelto
- **Severidad:** media
- **Confianza:** confirmado (lectura de código)
- **Área:** backend/chat
- **Tarea que lo resuelve:** T09
- **Detectado:** 2026-10-21, análisis inicial del código por Claude

## Dónde
`backend/src/main/java/io/github/yagogefaell/asaltamontes/message/service/MessageService.java` → `convertAndSendToUser(participant.getUser().getUsername(), …)`; `backend/src/main/java/io/github/yagogefaell/asaltamontes/user/service/UserProfileUpdater.java` permite cambiar el username

## Qué pasa
El usuario STOMP queda fijado al username en el CONNECT. Si la usuaria cambia su username, el backend envía al nombre nuevo, pero la sesión está registrada con el antiguo → no recibe nada hasta reconectar. Además, `saveMessage` carga a la remitente por username.

## Cómo reproducir
Cambiar el username en Ajustes y pedir a otra persona que te escriba.

## Arreglo propuesto
Usar la id de usuaria como nombre del principal (T09).
