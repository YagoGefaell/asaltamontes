import { lazy, Suspense, useMemo, useState } from 'react'
import { useEventsInBounds } from '../hooks/useEventsInBounds.js'
import { MAP_EVENTS_LIMIT } from '../services/events.service.js'
import EmptyState from '../../../shared/ui/EmptyState.jsx'
import Skeleton from '../../../shared/ui/Skeleton.jsx'
import Button from '../../../shared/forms/Button.jsx'
import './EventsMapSection.css'

// Leaflet only travels when she opens the map view
const EventsMapView = lazy(() => import('./EventsMapView.jsx'))

/** Map view of the events list: searches the visible area with the same filters as the list. `filters` must be memoized. */
export default function EventsMapSection({ filters }) {
  const [bounds, setBounds] = useState(null)
  const { events, total, loading, error, refetch } = useEventsInBounds(bounds, filters)
  const pins = useMemo(() => events.filter((e) => e.latitude != null && e.longitude != null), [events])

  let status = ''
  if (loading) status = 'Buscando quedadas…'
  else if (!error && bounds && total === 0) status = 'No hay quedadas en esta zona. Mueve el mapa o quita algún filtro.'
  else if (!error && total > MAP_EVENTS_LIMIT) status = `Mostrando las ${pins.length} primeras de ${total}. Acerca el mapa para ver el resto.`
  else if (!error && total > 0) status = `${total} ${total === 1 ? 'quedada' : 'quedadas'} en esta zona`

  return (
    <div className="events-map-section">
      <Suspense fallback={<Skeleton height="320px" />}>
        <EventsMapView events={pins} onBoundsChange={setBounds} />
      </Suspense>

      <p className="events-map-section__status" role="status">{status}</p>

      {error && (
        <EmptyState
          title="No se han podido cargar las quedadas"
          description={error}
          action={<Button variant="outline" onClick={refetch}>Reintentar</Button>}
        />
      )}
    </div>
  )
}
