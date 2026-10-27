export const DATE_PRESETS = [
  { value: 'all', label: 'Próximas' },
  { value: 'today', label: 'Hoy' },
  { value: 'weekend', label: 'Este finde' },
  { value: '30d', label: '30 días' },
]

export const EMPTY_FILTERS = { date: 'all', type: '', level: '', city: '', mine: false }

function startOfDay(date) {
  const d = new Date(date)
  d.setHours(0, 0, 0, 0)
  return d
}

/** { from, to } (ISO) of a date preset in the browser's local time; empty if it does not restrict. */
export function dateRange(preset, now = new Date()) {
  if (preset === 'today') {
    const end = startOfDay(now)
    end.setDate(end.getDate() + 1)
    return { from: now.toISOString(), to: end.toISOString() }
  }
  if (preset === 'weekend') {
    const day = now.getDay() // 0 = Sunday
    const saturday = startOfDay(now)
    // Saturday or Sunday: the weekend is already running, so it starts now
    const running = day === 6 || day === 0
    if (day === 0) saturday.setDate(saturday.getDate() - 1)
    else saturday.setDate(saturday.getDate() + (6 - day))
    const monday = new Date(saturday)
    monday.setDate(monday.getDate() + 2)
    return { from: (running ? now : saturday).toISOString(), to: monday.toISOString() }
  }
  if (preset === '30d') {
    // 30 calendar days in local time (a DST change must not shift the hour)
    const end = new Date(now)
    end.setDate(end.getDate() + 30)
    return { from: now.toISOString(), to: end.toISOString() }
  }
  return {}
}

/** UI filters -> parameters of the service. */
export function toQueryFilters(filters, now = new Date()) {
  return { ...dateRange(filters.date, now), type: filters.type, level: filters.level, city: filters.city, mine: filters.mine }
}

/** How many filters live in the bottom sheet (type, level, city): the "n" of "Filtros · n". */
export function countSheetFilters(filters) {
  return [filters.type, filters.level, filters.city.trim()].filter(Boolean).length
}

export function hasActiveFilters(filters) {
  return filters.date !== 'all' || Boolean(filters.type || filters.level || filters.city.trim() || filters.mine)
}
