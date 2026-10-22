import { lazy, Suspense, useCallback, useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { FaArrowLeft } from 'react-icons/fa'
import { useAuth } from '../../features/auth/hooks/useAuth.js'
import { useEvent } from '../../features/events/hooks/useEvent.js'
import { useEventAttendance } from '../../features/events/hooks/useEventAttendance.js'
import { getEventAttendeesRequest } from '../../features/events/services/events.service.js'
import AttendButton from '../../features/events/components/AttendButton.jsx'
import {
  EVENT_LEVELS, EVENT_TYPES, directionsUrl, formatDistance, formatEventDateTime, formatPlaces,
} from '../../features/events/utils/eventFormat.js'
import Avatar from '../../shared/ui/Avatar.jsx'
import EmptyState from '../../shared/ui/EmptyState.jsx'
import Skeleton from '../../shared/ui/Skeleton.jsx'
import Button from '../../shared/forms/Button.jsx'
import { useToast } from '../../shared/ui/Toast.jsx'
import { communityPath } from '../../features/communities/utils/communityPath.js'
import './EventDetail.css'

// Leaflet only downloads when an event with coordinates is opened
const EventMap = lazy(() => import('../../features/events/components/EventMap.jsx'))

const ATTENDEES_PREVIEW = 8

function Attendees({ eventId, count, refreshKey }) {
  const [people, setPeople] = useState([])
  const [hasMore, setHasMore] = useState(false)
  const [page, setPage] = useState(0)
  const [expanded, setExpanded] = useState(false)
  const [loadingMore, setLoadingMore] = useState(false)

  // Reload from the first page when the attendance changes (she joined or left)
  useEffect(() => {
    let active = true
    getEventAttendeesRequest(eventId, { status: 'GOING', page: 0 })
      .then((result) => {
        if (!active) return
        setPeople(result.items)
        setHasMore(result.hasMore)
        setPage(0)
      })
      .catch(() => {})
    return () => {
      active = false
    }
  }, [eventId, refreshKey])

  const loadMore = async () => {
    setLoadingMore(true)
    try {
      const result = await getEventAttendeesRequest(eventId, { status: 'GOING', page: page + 1 })
      setPeople((prev) => [...prev, ...result.items])
      setHasMore(result.hasMore)
      setPage(result.page)
    } catch {
      setHasMore(false)
    } finally {
      setLoadingMore(false)
    }
  }

  if (count === 0) return <p className="event-detail__muted">Todavía no se ha apuntado nadie.</p>

  const shown = expanded ? people : people.slice(0, ATTENDEES_PREVIEW)
  return (
    <>
      <ul className="event-detail__attendees">
        {shown.map((person) => (
          <li key={person.username}>
            <Link to={`/users/${person.username}`} className="event-detail__attendee">
              <Avatar src={person.profilePictureUrl} name={person.fullName || person.username} size="sm" />
              <span>{person.fullName || person.username}</span>
            </Link>
          </li>
        ))}
      </ul>
      {!expanded && (people.length > ATTENDEES_PREVIEW || hasMore) && (
        <Button variant="ghost" onClick={() => setExpanded(true)}>Ver todas ({count})</Button>
      )}
      {expanded && hasMore && <Button variant="ghost" onClick={loadMore} loading={loadingMore}>Cargar más</Button>}
    </>
  )
}

export default function EventDetail() {
  const { id } = useParams()
  const navigate = useNavigate()
  const toast = useToast()
  const { currentUser } = useAuth()
  const { event, setEvent, loading, error, refetch } = useEvent(id)
  const [waitlistPosition, setWaitlistPosition] = useState(null)
  const onError = useCallback((message) => toast.error(message), [toast])
  const { pending, join, leave } = useEventAttendance(event, setEvent, { onError })

  const username = currentUser?.username
  const myStatus = event?.myStatus
  const eventId = event?.id

  // Position in the waitlist (not part of the event DTO): look for her in the WAITLIST page
  useEffect(() => {
    if (myStatus !== 'WAITLIST' || !username) return undefined
    let active = true
    getEventAttendeesRequest(eventId, { status: 'WAITLIST', page: 0, size: 100 })
      .then((result) => {
        if (!active) return
        const mine = result.items.find((a) => a.username === username)
        setWaitlistPosition(mine?.waitlistPosition ?? null)
      })
      .catch(() => {})
    return () => {
      active = false
    }
  }, [eventId, myStatus, username])

  if (loading) {
    return (
      <div className="event-detail event-detail--loading">
        <Skeleton height="2rem" />
        <Skeleton height="6rem" />
        <Skeleton height="220px" />
      </div>
    )
  }

  if (error || !event) {
    return (
      <div className="event-detail">
        <EmptyState
          title="No se ha podido cargar la quedada"
          description={error || 'Puede que no exista o que no tengas acceso.'}
          action={
            <>
              <Button variant="outline" onClick={refetch}>Reintentar</Button>
              <Button variant="ghost" onClick={() => navigate('/events')}>Ver quedadas</Button>
            </>
          }
        />
      </div>
    )
  }

  const hasPoint = event.latitude != null && event.longitude != null
  const distance = formatDistance(event.distanceKm, event.elevationGainM)
  const isOrganizer = Boolean(username) && event.organizerUsername === username

  return (
    <div className="event-detail">
      <header className="event-detail__header">
        <button type="button" className="event-detail__back" onClick={() => navigate('/events')} aria-label="Volver a las quedadas">
          <FaArrowLeft />
        </button>
        <h2>{event.title}</h2>
      </header>

      <div className="event-detail__content">
        {event.status === 'CANCELLED' && (
          <p className="event-detail__cancelled" role="status">
            Quedada cancelada{event.cancelReason ? `: ${event.cancelReason}` : '.'}
          </p>
        )}

        <p className="event-detail__when">{formatEventDateTime(event.startsAt, event.timezone)}</p>
        <div className="event-detail__tags">
          <span>{EVENT_TYPES[event.type] ?? event.type}</span>
          {event.level && <span>{EVENT_LEVELS[event.level] ?? event.level}</span>}
          {distance && <span>{distance}</span>}
          <span>{formatPlaces(event.attendeeCount, event.capacity)}</span>
        </div>

        <div className="event-detail__action">
          <AttendButton
            event={event}
            waitlistPosition={waitlistPosition}
            isOrganizer={isOrganizer}
            pending={pending}
            onJoin={join}
            onLeave={leave}
          />
        </div>

        {event.description && <p className="event-detail__description">{event.description}</p>}

        <section className="event-detail__section">
          <h3>Punto de encuentro</h3>
          <p>{[event.meetingPointName, event.city].filter(Boolean).join(' · ') || 'Por confirmar'}</p>
          {hasPoint && (
            <>
              <Suspense fallback={<Skeleton height="220px" />}>
                <EventMap latitude={event.latitude} longitude={event.longitude} label={event.meetingPointName} />
              </Suspense>
              <a
                className="event-detail__directions"
                href={directionsUrl(event.latitude, event.longitude)}
                target="_blank"
                rel="noopener noreferrer"
              >
                Cómo llegar
              </a>
            </>
          )}
        </section>

        <section className="event-detail__section">
          <h3>Organiza</h3>
          <Link to={`/users/${event.organizerUsername}`} className="event-detail__attendee">
            <Avatar src={event.organizerPictureUrl} name={event.organizerFullName || event.organizerUsername} size="sm" />
            <span>{event.organizerFullName || event.organizerUsername}</span>
          </Link>
          {event.communityName && (
            <p>
              Comunidad:{' '}
              <Link to={communityPath({ slug: event.communitySlug, name: event.communityName })}>{event.communityName}</Link>
            </p>
          )}
        </section>

        <section className="event-detail__section">
          <h3>Asistentes</h3>
          <Attendees eventId={event.id} count={event.attendeeCount} refreshKey={`${event.myStatus}-${event.attendeeCount}`} />
        </section>
      </div>
    </div>
  )
}
