import { useState } from 'react'
import { FaSlidersH } from 'react-icons/fa'
import BottomSheet from '../../../shared/ui/BottomSheet.jsx'
import { Chip, ChipRow } from '../../../shared/ui/Chip.jsx'
import Input from '../../../shared/forms/Input.jsx'
import Button from '../../../shared/forms/Button.jsx'
import { DATE_PRESETS, countSheetFilters } from '../utils/eventFilters.js'
import { EVENT_LEVELS, EVENT_TYPES } from '../utils/eventFormat.js'
import './EventFilters.css'

/**
 * Controlled filter bar (T29e): ONE row of chips (dates, "mis quedadas") plus a "Filtros · n" chip that opens a
 * bottom sheet with type, level and city. The sheet edits a draft until she taps "Aplicar".
 */
export default function EventFilters({ filters, onChange }) {
  const [open, setOpen] = useState(false)
  const [draft, setDraft] = useState(filters)
  const count = countSheetFilters(filters)

  const openSheet = () => {
    setDraft(filters)
    setOpen(true)
  }
  const apply = () => {
    onChange({ ...filters, type: draft.type, level: draft.level, city: draft.city.trim() })
    setOpen(false)
  }
  const clear = () => {
    onChange({ ...filters, type: '', level: '', city: '' })
    setOpen(false)
  }

  return (
    <>
      <ChipRow label="Filtros de quedadas">
        <Chip selected={count > 0} onClick={openSheet} aria-haspopup="dialog">
          <FaSlidersH aria-hidden="true" /> {count > 0 ? `Filtros · ${count}` : 'Filtros'}
        </Chip>
        {DATE_PRESETS.map((preset) => (
          <Chip
            key={preset.value}
            selected={filters.date === preset.value}
            onClick={() => onChange({ ...filters, date: preset.value })}
          >
            {preset.label}
          </Chip>
        ))}
        <Chip selected={filters.mine} onClick={() => onChange({ ...filters, mine: !filters.mine })}>
          Mis quedadas
        </Chip>
      </ChipRow>

      <BottomSheet
        open={open}
        title="Filtros"
        onClose={() => setOpen(false)}
        footer={
          <div className="filters-sheet__actions">
            <Button variant="ghost" onClick={clear}>Quitar filtros</Button>
            <Button onClick={apply}>Aplicar</Button>
          </div>
        }
      >
        <div className="filters-sheet__fields">
          <Input id="ef-type" label="Tipo" as="select" value={draft.type} onChange={(e) => setDraft({ ...draft, type: e.target.value })}>
            <option value="">Todos los tipos</option>
            {Object.entries(EVENT_TYPES).map(([value, label]) => (
              <option key={value} value={value}>{label}</option>
            ))}
          </Input>
          <Input id="ef-level" label="Nivel" as="select" value={draft.level} onChange={(e) => setDraft({ ...draft, level: e.target.value })}>
            <option value="">Todos los niveles</option>
            {Object.entries(EVENT_LEVELS).map(([value, label]) => (
              <option key={value} value={value}>{label}</option>
            ))}
          </Input>
          <Input
            id="ef-city"
            label="Ciudad"
            value={draft.city}
            autoComplete="off"
            onChange={(e) => setDraft({ ...draft, city: e.target.value })}
          />
        </div>
      </BottomSheet>
    </>
  )
}
