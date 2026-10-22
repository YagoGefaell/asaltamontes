import { Link } from 'react-router-dom'
import { EVENT_LEVELS, EVENT_TYPES, formatDateBadge, formatDistance, formatEventDateTime, formatPlaces, isFull } from '../utils/eventFormat.js'
import './EventCard.css'

/** Card of a listing: big date, title, place, distance, level and places. */
export default function EventCard({ event }) {
  const { day, month } = formatDateBadge(event.startsAt, event.timezone)
  const distance = formatDistance(event.distanceKm, event.elevationGainM)
  const place = [event.meetingPointName, event.city].filter(Boolean).join(' · ')
  const full = isFull(event)

  return (
    <Link to={`/events/${event.id}`} className="event-card">
      <div className="event-card__date" aria-hidden="true">
        <span className="event-card__day">{day}</span>
        <span className="event-card__month">{month}</span>
      </div>
      <div className="event-card__body">
        <h3 className="event-card__title">{event.title}</h3>
        <p className="event-card__when">{formatEventDateTime(event.startsAt, event.timezone)}</p>
        {place && <p className="event-card__place">{place}</p>}
        <div className="event-card__tags">
          <span className="event-card__tag">{EVENT_TYPES[event.type] ?? event.type}</span>
          {event.level && <span className="event-card__tag">{EVENT_LEVELS[event.level] ?? event.level}</span>}
          {distance && <span className="event-card__tag">{distance}</span>}
          <span className={`event-card__tag${full ? ' event-card__tag--full' : ''}`}>
            {formatPlaces(event.attendeeCount, event.capacity)}
            {full && ' · completo'}
          </span>
        </div>
      </div>
    </Link>
  )
}
