import { useCallback, useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { getCommunityEventsRequest } from '../services/events.service.js'
import EventCard from './EventCard.jsx'
import Button from '../../../shared/forms/Button.jsx'
import Skeleton from '../../../shared/ui/Skeleton.jsx'
import './CommunityEvents.css'

const PREVIEW_SIZE = 5

/** "Próximas quedadas" of a community, with "Crear quedada" for its owner and moderators. */
export default function CommunityEvents({ community }) {
  const navigate = useNavigate()
  const [events, setEvents] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const slug = community.slug || community.name
  const canCreate = community.role === 'OWNER' || community.role === 'MODERATOR'

  const load = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const page = await getCommunityEventsRequest(slug, { size: PREVIEW_SIZE })
      setEvents(page.items)
    } catch (err) {
      setError(err.message)
    } finally {
      setLoading(false)
    }
  }, [slug])

  useEffect(() => {
    load()
  }, [load])

  return (
    <section className="community-events" aria-labelledby="community-events-title">
      <div className="community-events__header">
        <h3 id="community-events-title">Próximas quedadas</h3>
        {canCreate && (
          <Button size="small" onClick={() => navigate(`/events/new?community=${community.id}`)}>Crear quedada</Button>
        )}
      </div>

      {loading && <Skeleton height="104px" />}
      {!loading && error && (
        <p className="community-events__muted">
          {error} <Button variant="ghost" size="small" onClick={load}>Reintentar</Button>
        </p>
      )}
      {!loading && !error && events.length === 0 && (
        <p className="community-events__muted">Todavía no hay quedadas programadas.</p>
      )}
      {!loading && !error && events.map((event) => <EventCard key={event.id} event={event} />)}
    </section>
  )
}
