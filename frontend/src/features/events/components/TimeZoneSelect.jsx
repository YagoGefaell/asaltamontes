import { useMemo, useState } from 'react'
import Input from '../../../shared/forms/Input.jsx'
import { filterTimeZones, listTimeZones, zoneLabel } from '../utils/timeZones.js'

/** IANA zone picker with a search box. Controlled: `value` is the zone id, `onChange(zoneId)`. */
export default function TimeZoneSelect({ value, onChange, error }) {
  const [query, setQuery] = useState('')
  const zones = useMemo(() => listTimeZones(value), [value])
  const shown = filterTimeZones(zones, query, value)

  return (
    <div className="event-form__zone">
      <Input
        type="search" label="Buscar zona horaria" aria-label="Buscar zona horaria" placeholder="Ej.: Lima, Tokyo, Europe…"
        value={query} onChange={(e) => setQuery(e.target.value)}
      />
      <Input
        as="select" label="Zona horaria" aria-label="Zona horaria" value={value} error={error}
        onChange={(e) => onChange(e.target.value)}
      >
        {shown.map((zone) => <option key={zone} value={zone}>{zoneLabel(zone)}</option>)}
      </Input>
    </div>
  )
}
