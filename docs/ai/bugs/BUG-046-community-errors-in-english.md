# BUG-046 — Los errores de comunidades llegan en inglés a la interfaz

- **Estado:** resuelto
- **Severidad:** baja
- **Confianza:** confirmado (lectura de código)
- **Área:** backend/community
- **Tarea que lo resuelve:** T14
- **Detectado:** 2026-10-21, bug hunt de comunidades (Claude)

## Dónde
- `community/service/CommunityService.java`: "Community with that name already exists", "Community not found", "User is not a member of this community", "Only the owner can delete the community".
- `community/controller/CommunityController.java`: "Owner cannot leave the community".
- `community/dto/CreateCommunityDto.java`: mensajes de `@NotBlank`/`@Size`.
- `common/exceptions/GlobalExceptionHandler.java:171` ("An unexpected error occurred") y `:179` ("Data integrity violation occurred").

## Qué pasa
El frontend muestra el `detail` del `ProblemDetail` tal cual (`lib/api-client.js`), p. ej. en `CommunityCreatorPanel` y en el error del botón de unirse. El resto de dominios (auth, user, chat) ya usan mensajes en español; los de comunidades y los dos genéricos del manejador no, en contra de la convención del proyecto (UI y errores al usuario en español).

## Cómo reproducir
Crear dos veces una comunidad con el mismo nombre → la interfaz muestra "Community with that name already exists".

## Arreglo propuesto
Traducir esos mensajes ("Ya existe una comunidad con ese nombre", "Comunidad no encontrada", "No eres miembro de esta comunidad", "Solo la propietaria puede borrar la comunidad", "La propietaria no puede salir de la comunidad", "Ha ocurrido un error inesperado", "La operación entra en conflicto con datos existentes"). Los IT de T14 que comprueben códigos pueden comprobar también el `detail`.
