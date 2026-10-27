import { Link } from 'react-router-dom'
import Avatar from '../../../shared/ui/Avatar.jsx'
import Button from '../../../shared/forms/Button.jsx'
import { useAuth } from '../../auth/hooks/useAuth.js'
import { useToast } from '../../../shared/ui/Toast.jsx'
import { useEventAttendance } from '../hooks/useEventAttendance.js'
import { EVENT_LEVELS, formatDateBadge, formatDistance, formatEventDateTime, formatPlaces, isFull } from '../utils/eventFormat.js'
import './EventCard.css'

const MAX_AVATARS = 3

/** Small join / leave button of the card. Only mounted when the list can update the event (`onChange`). */
function CardAttend({ event, onChange }) {
  const toast = useToast()
  const { currentUser } = useAuth()
  const { pending, join, leave } = useEventAttendance(event, onChange, { onError: toast.error })
  const username = currentUser?.username
  if (event.status === 'CANCELLED' || event.status === 'FINISHED') return null
  if (username && event.organizerUsername === username) return <span className="event-card__badge">Organizas</span>
  if (event.myStatus === 'GOING') {
    return <Button size="small" variant="outline" onClick={leave} disabled={pending} aria-label="Estás dentro. Pulsa para salir de la quedada">Estás dentro ✓</Button>
  }
  if (event.myStatus === 'WAITLIST') {
    return <Button size="small" variant="outline" onClick={leave} disabled={pending}>En lista de espera</Button>
  }
  return (
    <Button size="small" variant="secondary" className="event-card__join" onClick={join} disabled={pending}>
      {isFull(event) ? 'Lista de espera' : 'Apuntarme'}
    </Button>
  )
}

/**
 * Card of a listing (T29e): orange date block, title, metadata in two short lines, stacked avatars (when the
 * listing sends `attendeePreview`) and "Apuntarme". With `onChange` the join button works from the list.
 */
export default function EventCard({ event, onChange }) {
  const { day, month } = formatDateBadge(event.startsAt, event.timezone)
  const distance = formatDistance(event.distanceKm, event.elevationGainM)
  const place = event.meetingPointName || event.city
  const when = [formatEventDateTime(event.startsAt, event.timezone), place].filter(Boolean).join(' · ')
  const details = [event.level && (EVENT_LEVELS[event.level] ?? event.level), distance].filter(Boolean).join(' · ')
  const full = isFull(event)
  const preview = (event.attendeePreview ?? []).slice(0, MAX_AVATARS)

  return (
    <article className="event-card">
      <Link to={`/events/${event.id}`} className="event-card__main">
        <div className="event-card__date" aria-hidden="true">
          <span className="event-card__day">{day}</span>
          <span className="event-card__month">{month}</span>
        </div>
        <div className="event-card__body">
          <h3 className="event-card__title">{event.title}</h3>
          <p className="event-card__meta">{when}</p>
          {details && <p className="event-card__meta">{details}</p>}
        </div>
      </Link>
      <div className="event-card__footer">
        <div className="event-card__people">
          {preview.length > 0 && (
            <span className="event-card__avatars" aria-hidden="true">
              {preview.map((p) => (
                <Avatar key={p.username} src={p.avatar?.thumb} name={p.fullName || p.username} size="sm" className="event-card__avatar" />
              ))}
            </span>
          )}
          <span className={`event-card__places${full ? ' event-card__places--full' : ''}`}>
            {formatPlaces(event.attendeeCount, event.capacity)}
            {full && ' · completo'}
          </span>
        </div>
        {onChange && <CardAttend event={event} onChange={onChange} />}
      </div>
    </article>
  )
}
