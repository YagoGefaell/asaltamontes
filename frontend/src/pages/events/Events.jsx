import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { FaList, FaMapMarkedAlt, FaPlus, FaSearch } from 'react-icons/fa'
import { useEvents } from '../../features/events/hooks/useEvents.js'
import EventCard from '../../features/events/components/EventCard.jsx'
import EventsMapSection from '../../features/events/components/EventsMapSection.jsx'
import EventFilters from '../../features/events/components/EventFilters.jsx'
import { EMPTY_FILTERS, hasActiveFilters, toQueryFilters } from '../../features/events/utils/eventFilters.js'
import EmptyState from '../../shared/ui/EmptyState.jsx'
import Skeleton from '../../shared/ui/Skeleton.jsx'
import PageHeader from '../../shared/ui/PageHeader.jsx'
import Fab from '../../shared/ui/Fab.jsx'
import Button from '../../shared/forms/Button.jsx'
import './Events.css'


export default function Events() {
  const navigate = useNavigate()
  const [filters, setFilters] = useState(EMPTY_FILTERS)
  const [view, setView] = useState('list')
  const mapView = view === 'map'

  const queryFilters = useMemo(() => toQueryFilters(filters), [filters])
  const { events, setEvents, loading, loadingMore, hasMore, loadMore, error, refetch } = useEvents(queryFilters, !mapView)

  const replaceEvent = useCallback(
    (updated) => setEvents((prev) => prev.map((e) => (e.id === updated.id ? { ...e, ...updated } : e))),
    [setEvents],
  )

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
      <PageHeader
        title="Quedadas"
        actions={
          <>
            <Link to="/search?tab=events" aria-label="Buscar quedadas"><FaSearch aria-hidden="true" /></Link>
            <button
              type="button"
              aria-label={mapView ? 'Ver lista' : 'Ver mapa'}
              onClick={() => setView(mapView ? 'list' : 'map')}
            >
              {mapView ? <FaList aria-hidden="true" /> : <FaMapMarkedAlt aria-hidden="true" />}
            </button>
          </>
        }
      />

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

        {!loading && events.map((event) => <EventCard key={event.id} event={event} onChange={(updated) => replaceEvent(updated)} />)}

        {!loading && hasMore && (
          <>
            <div ref={sentinelRef} aria-hidden="true" />
            <Button variant="ghost" onClick={loadMore} loading={loadingMore}>Cargar más</Button>
          </>
        )}
      </div>}
      <Fab icon={<FaPlus aria-hidden="true" />} label="Crear quedada" onClick={() => navigate('/events/new')} />
    </div>
  )
}
