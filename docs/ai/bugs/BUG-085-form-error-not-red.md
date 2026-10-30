# BUG-085 — `FormError` pinta los errores en turquesa, no en rojo

- **Estado:** abierto
- **Severidad:** baja
- **Confianza:** confirmado (captura de `/verify-email?token=bad`, claro y oscuro)
- **Área:** frontend/ui
- **Tarea que lo resuelve:** ninguna (arreglo de una línea en `FormError.css`)
- **Detectado:** 2026-10-30, `/ui-review` de T24a2

## Dónde
`frontend/src/shared/forms/FormError.css` (`.form-error` y `.error-icon` usan `var(--primary)`).

## Qué pasa
El componente que DESIGN.md manda usar para los errores de formulario los muestra en el color principal (turquesa), igual que un enlace; no se distinguen de un texto informativo y no cumplen «texto rojo» ni «`--error` solo para estados».

## Arreglo propuesto
Usar `--error-text` (o `--error`) en ambas reglas y revisar en las capturas clara y oscura las 24 pantallas que lo usan.
