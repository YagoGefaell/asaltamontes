# BUG-009 — `@Data` en entidades JPA con relaciones bidireccionales

- **Estado:** abierto
- **Severidad:** media
- **Confianza:** probable (verificar con test)
- **Área:** backend/user
- **Tarea que lo resuelve:** T05
- **Detectado:** 2026-10-21, análisis inicial del código por Claude

## Dónde
`backend/src/main/java/io/github/yagogefaell/asaltamontes/user/domain/UserAccount.java`, `UserProfile.java`, `UserFollow.java`

## Qué pasa
`@Data` genera `toString`, `equals` y `hashCode` con todos los campos. `UserAccount.profile` ↔ `UserProfile.user` hace que `toString()` o `hashCode()` entren en recursión infinita (`StackOverflowError`) en cuanto algo los invoque (un log, meterlos en un `Set`, el depurador). Además, `equals` basado en campos mutables rompe las colecciones de Hibernate.

## Cómo reproducir
Test unitario: crear `UserAccount` + `UserProfile` enlazados y llamar a `toString()`.

## Arreglo propuesto
`@Getter @Setter` + `equals/hashCode` por id (T05).
