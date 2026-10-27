# BUG-068 — Borrar una cuenta no aplica las reglas de los grupos (última administradora, grupo vacío)

- **Estado:** abierto
- **Severidad:** baja (hoy ningún endpoint llama a `UserService.deleteUser`; se manifestará con T23)
- **Confianza:** probable (lectura de código)
- **Área:** backend/user + backend/message
- **Tarea que lo resuelve:** T23 (borrado de cuenta), junto con BUG-065
- **Detectado:** 2026-10-27, al implementar T12 (Claude)

## Dónde
`UserService.deleteUser` y `message/service/GroupService#leave`.

## Qué pasa
T12 garantiza que un grupo no se queda sin administradora ni vacío cuando una participante **sale** (`POST /leave`), pero `deleteUser` no pasa por esa lógica: al borrar una cuenta que es la última administradora de un grupo, el grupo quedaría sin admin (y las FK `fk_conversation_participants_user` / `fk_messages_sender` ya impiden el borrado, BUG-065). `conversations.created_by_id` sí se pone a NULL solo (`ON DELETE SET NULL`).

## Cómo reproducir
Con un grupo de dos personas, llamar a `deleteUser` de la única admin: el grupo queda con una participante sin rol ADMIN (cuando BUG-065 permita borrar).

## Arreglo propuesto
En T23, antes de borrar la cuenta, recorrer sus grupos y aplicar `GroupService#leave` (promocionar a la más antigua o borrar el grupo vacío) y anonimizar sus mensajes (`sender_id` ya admite NULL con `type = SYSTEM`; decidir si los mensajes de usuaria pasan a un remitente "cuenta eliminada").
