import { lazy, Suspense, useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { FaPen, FaRegMap } from 'react-icons/fa'
import { useAuth } from '../../features/auth/hooks/useAuth.js'
import { useEvent } from '../../features/events/hooks/useEvent.js'
import { useEventAttendance } from '../../features/events/hooks/useEventAttendance.js'
import { getEventAttendeesRequest } from '../../features/events/services/events.service.js'
import AttendButton from '../../features/events/components/AttendButton.jsx'
import AddToCalendarButton from '../../features/events/components/AddToCalendarButton.jsx'
import EventChatButton from '../../features/events/components/EventChatButton.jsx'
import ShareEventButton from '../../features/events/components/ShareEventButton.jsx'
import {
  EVENT_LEVELS, EVENT_TYPES, directionsUrl, formatDistance, formatEventDateTime, formatPlaces, formatViewerDateTime,
} from '../../features/events/utils/eventFormat.js'
import Avatar from '../../shared/ui/Avatar.jsx'
import EmptyState from '../../shared/ui/EmptyState.jsx'
import Skeleton from '../../shared/ui/Skeleton.jsx'
import Button from '../../shared/forms/Button.jsx'
import PageHeader from '../../shared/ui/PageHeader.jsx'
import FormError from '../../shared/forms/FormError.jsx'
import ReportMenu from '../../features/reports/components/ReportMenu.jsx'
import { communityPath } from '../../features/communities/utils/communityPath.js'
import '../../features/events/components/EventActions.css'
import './EventDetail.css'

// Leaflet only downloads when an event with coordinates is opened
const EventMap = lazy(() => import('../../features/events/components/EventMap.jsx'))

const ATTENDEES_PREVIEW = 5

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

  if (!expanded) {
    return (
      <div className="event-detail__stack-row">
        <span className="event-detail__stack" aria-hidden="true">
          {people.slice(0, ATTENDEES_PREVIEW).map((person) => (
            <Avatar key={person.username} src={person.profilePictureUrl} name={person.fullName || person.username} size="md" className="event-detail__stack-avatar" />
          ))}
        </span>
        <Button variant="ghost" onClick={() => setExpanded(true)} aria-label={`Ver todas las asistentes (${count})`}>Ver todas ({count})</Button>
      </div>
    )
  }
  return (
    <>
      <ul className="event-detail__attendees">
        {people.map((person) => (
          <li key={person.username}>
            <Link to={`/users/${person.username}`} className="event-detail__attendee">
              <Avatar src={person.profilePictureUrl} name={person.fullName || person.username} size="sm" />
              <span>{person.fullName || person.username}</span>
            </Link>
          </li>
        ))}
      </ul>
      {hasMore && <Button variant="ghost" onClick={loadMore} loading={loadingMore}>Cargar más</Button>}
      <Button variant="ghost" onClick={() => setExpanded(false)}>Ver menos</Button>
    </>
  )
}

export default function EventDetail() {
  const { id } = useParams()
  const navigate = useNavigate()
  const { currentUser } = useAuth()
  const { event, setEvent, loading, error, refetch } = useEvent(id)
  const [waitlistPosition, setWaitlistPosition] = useState(null)
  const [attendError, setAttendError] = useState('')
  const [actionError, setActionError] = useState('')
  const { pending, join, leave } = useEventAttendance(event, setEvent, { onError: setAttendError })
  const attend = (action) => () => {
    setAttendError('')
    return action()
  }

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
  const viewerTime = formatViewerDateTime(event.startsAt, event.timezone)
  const distance = formatDistance(event.distanceKm, event.elevationGainM)
  const isOrganizer = Boolean(username) && event.organizerUsername === username

  return (
    <div className="event-detail">
      <PageHeader
        onBack={() => navigate('/events')}
        backLabel="Volver a las quedadas"
        actions={(
          <>
            {event.canEdit && event.status !== 'CANCELLED' && (
              <button type="button" onClick={() => navigate(`/events/${event.id}/edit`)} aria-label="Editar quedada" title="Editar quedada">
                <FaPen aria-hidden="true" />
              </button>
            )}
            {!isOrganizer && (
              <ReportMenu menuTitle="Quedada" reportTitle="Denunciar quedada" actionLabel="Denunciar quedada" targetType="EVENT" target={event.id} />
            )}
          </>
        )}
      >
        <h1 className="event-detail__header-title">{event.title}</h1>
      </PageHeader>

      {event.imageUrl && <img className="event-detail__cover" src={event.imageUrl} alt="" />}

      <div className="event-detail__content">
        {event.status === 'CANCELLED' && (
          <p className="event-detail__cancelled" role="status">
            Quedada cancelada{event.cancelReason ? `: ${event.cancelReason}` : '.'}
          </p>
        )}

        <div className="event-detail__title-row">
          <p className="event-detail__when">{formatEventDateTime(event.startsAt, event.timezone)}</p>
          {isOrganizer && <span className="event-detail__organizer-label">Eres la organizadora</span>}
        </div>
        {viewerTime && <p className="event-detail__viewer-time">Tu hora: {viewerTime}</p>}
        <p className="event-detail__meta">{[
          EVENT_TYPES[event.type] ?? event.type,
          event.level && (EVENT_LEVELS[event.level] ?? event.level),
          distance,
          formatPlaces(event.attendeeCount, event.capacity),
        ].filter(Boolean).join(' · ')}</p>

        <FormError message={attendError} />
        {!isOrganizer && (
          <div className="event-detail__action">
            <AttendButton
              event={event}
              waitlistPosition={waitlistPosition}
              isOrganizer={isOrganizer}
              pending={pending}
              onJoin={attend(join)}
              onLeave={attend(leave)}
            />
          </div>
        )}
        {event.description && <p className="event-detail__description">{event.description}</p>}

        <section className="event-detail__section">
          <h3>Punto de encuentro</h3>
          <p>{[event.meetingPointName, event.city].filter(Boolean).join(' · ') || 'Por confirmar'}</p>
          {hasPoint && (
            <Suspense fallback={<Skeleton height="220px" />}>
              <EventMap latitude={event.latitude} longitude={event.longitude} label={event.meetingPointName} />
            </Suspense>
          )}
        </section>

        <div className="event-detail__actions" role="group" aria-label="Acciones de la quedada">
          {myStatus === 'GOING' && <EventChatButton event={event} onError={setActionError} />}
          <AddToCalendarButton event={event} onError={setActionError} />
          <ShareEventButton event={event} onError={setActionError} />
          {hasPoint && (
            <a className="event-action" href={directionsUrl(event.latitude, event.longitude)} target="_blank" rel="noopener noreferrer">
              <FaRegMap aria-hidden="true" />
              <span>Cómo llegar</span>
            </a>
          )}
        </div>
        <FormError message={actionError} />

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
