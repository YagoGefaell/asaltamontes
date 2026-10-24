# BUG-065 — Borrar una cuenta sigue fallando si tiene seguidores, membresías, mensajes, eventos o comunidades

- **Estado:** abierto
- **Severidad:** baja (ningún endpoint llama hoy a `UserService.deleteUser`; bloquea el borrado de cuenta de T23)
- **Confianza:** probable (lectura de las migraciones: FK a `users` sin `ON DELETE`)
- **Área:** backend/user
- **Tarea que lo resuelve:** T23 (borrado de cuenta), junto con la decisión de qué pasa con lo que la usuaria creó
- **Detectado:** 2026-10-24, al arreglar BUG-064 (Claude)

## Dónde
`UserService.deleteUser` y las FK a `users(id)` de `V1__baseline.sql` (`fk_user_follow_*`, `fk_communities_created_by`, `fk_community_members_user`, `fk_conversations_last_sender`, `fk_conversation_participants_user`, `fk_messages_sender`) y `V5__events.sql` (`fk_events_organizer`, `fk_event_attendees_user`).

## Qué pasa
BUG-064 hace que se borren las imágenes de la usuaria, pero `deleteUser` sigue sin tratar el resto de filas que cuelgan de la cuenta. Con cualquiera de ellas la FK impide el borrado (500).

## Cómo reproducir
Seguir a alguien (o crear una comunidad, enviar un mensaje, organizar un evento) y llamar a `UserService.deleteUser(id)`.

## Arreglo propuesto
Decidir con Yago en T23 qué se conserva (mensajes anonimizados, comunidades y eventos reasignados o borrados) y borrar/anonimizar en consecuencia, con test de integración por cada tipo de fila. Recordar los contadores desnormalizados (`member_count`, seguidores).
