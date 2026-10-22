import { DATE_PRESETS, EMPTY_FILTERS, hasActiveFilters } from '../utils/eventFilters.js'
import { EVENT_LEVELS, EVENT_TYPES } from '../utils/eventFormat.js'
import './EventFilters.css'

/** Controlled filter bar: date chips, "mis quedadas", type, level and city. `city` is the raw text (the page debounces it). */
export default function EventFilters({ filters, onChange }) {
  const set = (patch) => onChange({ ...filters, ...patch })

  return (
    <div className="event-filters">
      <div className="event-filters__chips" role="group" aria-label="Fechas">
        {DATE_PRESETS.map((preset) => (
          <button
            key={preset.value}
            type="button"
            className={`event-filters__chip${filters.date === preset.value ? ' is-active' : ''}`}
            aria-pressed={filters.date === preset.value}
            onClick={() => set({ date: preset.value })}
          >
            {preset.label}
          </button>
        ))}
        <button
          type="button"
          className={`event-filters__chip${filters.mine ? ' is-active' : ''}`}
          aria-pressed={filters.mine}
          onClick={() => set({ mine: !filters.mine })}
        >
          Mis quedadas
        </button>
      </div>

      <div className="event-filters__row">
        <select aria-label="Tipo" value={filters.type} onChange={(e) => set({ type: e.target.value })}>
          <option value="">Todos los tipos</option>
          {Object.entries(EVENT_TYPES).map(([value, label]) => (
            <option key={value} value={value}>{label}</option>
          ))}
        </select>
        <select aria-label="Nivel" value={filters.level} onChange={(e) => set({ level: e.target.value })}>
          <option value="">Todos los niveles</option>
          {Object.entries(EVENT_LEVELS).map(([value, label]) => (
            <option key={value} value={value}>{label}</option>
          ))}
        </select>
        <input
          type="search"
          aria-label="Ciudad"
          placeholder="Ciudad"
          value={filters.city}
          onChange={(e) => set({ city: e.target.value })}
        />
      </div>

      {hasActiveFilters(filters) && (
        <button type="button" className="event-filters__clear" onClick={() => onChange(EMPTY_FILTERS)}>
          Quitar filtros
        </button>
      )}
    </div>
  )
}
