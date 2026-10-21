## Qué y por qué
<!-- Tarea Txx / BUG-xxx. Resumen en 2-4 frases. -->

Cierra: Txx · BUG-xxx
Depende de: <!-- #N o "nada" -->
Base: <!-- dev (normal) · main (solo release o hotfix, WORKFLOW §11) -->

## Cambios
- 

## Cómo probarlo
1. 

## Checklist (Definition of Done — docs/ai/WORKFLOW.md §6)
- [ ] Criterios de aceptación cumplidos
- [ ] Tests nuevos (bug → test que falla sin el arreglo)
- [ ] `./mvnw verify` verde
- [ ] `npm run lint && npm run build` (y `npm test`) verde
- [ ] Migración Flyway (si aplica) probada con datos
- [ ] Contrato API / `.env.example` / README actualizados (si aplica)
- [ ] Estado de tarea/bugs actualizado en el diff

## Riesgos y notas para la revisión
<!-- Qué mirar con más atención, decisiones tomadas, alternativas descartadas. -->
