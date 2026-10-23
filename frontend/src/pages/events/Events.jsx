import { useEffect, useMemo, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useEvents } from '../../features/events/hooks/useEvents.js'
import EventCard from '../../features/events/components/EventCard.jsx'
import EventsMapSection from '../../features/events/components/EventsMapSection.jsx'
import EventFilters from '../../features/events/components/EventFilters.jsx'
import { EMPTY_FILTERS, hasActiveFilters, toQueryFilters } from '../../features/events/utils/eventFilters.js'
import EmptyState from '../../shared/ui/EmptyState.jsx'
import Skeleton from '../../shared/ui/Skeleton.jsx'
import Button from '../../shared/forms/Button.jsx'
import './Events.css'

const CITY_DEBOUNCE_MS = 300
const VIEWS = [
  { value: 'list', label: 'Lista' },
  { value: 'map', label: 'Mapa' },
]

export default function Events() {
  const navigate = useNavigate()
  const [filters, setFilters] = useState(EMPTY_FILTERS)
  const [debouncedCity, setDebouncedCity] = useState('')
  const [view, setView] = useState('list')
  const mapView = view === 'map'

  // Search by city when she stops typing
  useEffect(() => {
    const timeoutId = setTimeout(() => setDebouncedCity(filters.city.trim()), CITY_DEBOUNCE_MS)
    return () => clearTimeout(timeoutId)
  }, [filters.city])

  const queryFilters = useMemo(
    () => toQueryFilters({ ...filters, city: debouncedCity }),
    [filters, debouncedCity],
  )
  const { events, loading, loadingMore, hasMore, loadMore, error, refetch } = useEvents(queryFilters, !mapView)

  // Infinite scroll: load the next page when the sentinel comes into view
  const sentinelRef = useRef(null)
  useEffect(() => {
    const node = sentinelRef.current
    if (!node || !hasMore || typeof IntersectionObserver === 'undefined') return undefined
    const observer = new IntersectionObserver((entries) => {
      if (entries.some((entry) => entry.isIntersecting)) loadMore()
    }, { rootMargin: '200px' })
    observer.observe(node)
    return () => observer.disconnect()
  }, [hasMore, loadMore, events.length])

  return (
    <div className="events-page">
      <header className="events-header">
        <h2>Quedadas</h2>
        <Button size="small" onClick={() => navigate('/events/new')}>Crear quedada</Button>
      </header>

      <div className="events-view-toggle" role="group" aria-label="Vista">
        {VIEWS.map(({ value, label }) => (
          <button
            key={value}
            type="button"
            className={`events-view-toggle__option${view === value ? ' is-active' : ''}`}
            aria-pressed={view === value}
            onClick={() => setView(value)}
          >
            {label}
          </button>
        ))}
      </div>

      <EventFilters filters={filters} onChange={setFilters} />

      {mapView && <EventsMapSection filters={queryFilters} />}

      {!mapView && <div className="events-list">
        {loading && [0, 1, 2].map((i) => <Skeleton key={i} height="104px" />)}

        {!loading && error && (
          <EmptyState
            title="No se han podido cargar las quedadas"
            description={error}
            action={<Button variant="outline" onClick={refetch}>Reintentar</Button>}
          />
        )}

        {!loading && !error && events.length === 0 && (
          <EmptyState
            title={hasActiveFilters(filters) ? 'Ninguna quedada con estos filtros' : 'Aún no hay quedadas'}
            description={hasActiveFilters(filters) ? 'Prueba con otras fechas o quita algún filtro.' : 'Cuando se organice alguna, aparecerá aquí.'}
            action={hasActiveFilters(filters) ? <Button variant="outline" onClick={() => setFilters(EMPTY_FILTERS)}>Quitar filtros</Button> : null}
          />
        )}

        {!loading && events.map((event) => <EventCard key={event.id} event={event} />)}

        {!loading && hasMore && (
          <>
            <div ref={sentinelRef} aria-hidden="true" />
            <Button variant="ghost" onClick={loadMore} loading={loadingMore}>Cargar más</Button>
          </>
        )}
      </div>}
    </div>
  )
}
