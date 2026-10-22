// Criterio de T29a: ningun color hexadecimal fuera de styles/tokens.css y styles/global.css.
// Uso: node scripts/check-colors.mjs  (se ejecuta dentro de `npm run lint`).
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const src = path.join(path.dirname(fileURLToPath(import.meta.url)), '..', 'src')
const ALLOWED = new Set(['styles/tokens.css', 'styles/global.css'])
const HEX = /(?<![&\w])#[0-9a-fA-F]{3,8}\b/g
const EXT = /\.(css|jsx?)$/
const TEST = /\.test\.jsx?$/

const offenders = []
function walk(dir) {
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const full = path.join(dir, entry.name)
    if (entry.isDirectory()) {
      walk(full)
      continue
    }
    const rel = path.relative(src, full).split(path.sep).join('/')
    if (!EXT.test(rel) || TEST.test(rel) || ALLOWED.has(rel)) continue
    fs.readFileSync(full, 'utf8').split('\n').forEach((line, i) => {
      for (const m of line.matchAll(HEX)) {
        if (/^#[0-9a-fA-F]{3,8}$/.test(m[0])) offenders.push(`${rel}:${i + 1}: ${m[0]}`)
      }
    })
  }
}
walk(src)

if (offenders.length) {
  console.error('Colores hexadecimales fuera de tokens.css/global.css (usa variables CSS):')
  offenders.forEach((o) => console.error('  ' + o))
  process.exit(1)
}
console.log('check-colors: OK')
