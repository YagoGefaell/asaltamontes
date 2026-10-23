import { useEffect } from 'react'
import { MapContainer, TileLayer, Marker, Popup, useMap, useMapEvents } from 'react-leaflet'
import { Link } from 'react-router-dom'
import 'leaflet/dist/leaflet.css'
import { DEFAULT_CENTER, DEFAULT_ZOOM, meetingIcon, OSM_ATTRIBUTION, OSM_TILES } from './mapSetup.js'
import { formatEventDateTime, formatPlaces } from '../utils/eventFormat.js'
import { toBounds } from '../services/events.service.js'
import './EventMap.css'

function visibleArea(map) {
  const b = map.getBounds()
  return toBounds({ south: b.getSouth(), west: b.getWest(), north: b.getNorth(), east: b.getEast() })
}

/** Tells the page which area is visible: once the map is ready and every time it stops moving. */
function BoundsReporter({ onBoundsChange }) {
  const map = useMap()
  useMapEvents({ moveend: (e) => onBoundsChange(visibleArea(e.target)) })
  // The map starts in its default view and does not move by itself: report that first area
  useEffect(() => {
    onBoundsChange(visibleArea(map))
  }, [map, onBoundsChange])
  return null
}

/** Map of the events list: one pin per event (`events` all have coordinates). Loaded lazily (React.lazy) with Leaflet. */
export default function EventsMapView({ events, onBoundsChange }) {
  return (
    <MapContainer center={DEFAULT_CENTER} zoom={DEFAULT_ZOOM} scrollWheelZoom={false} className="event-map event-map--list">
      <TileLayer attribution={OSM_ATTRIBUTION} url={OSM_TILES} />
      <BoundsReporter onBoundsChange={onBoundsChange} />
      {events.map((event) => (
        <Marker key={event.id} position={[event.latitude, event.longitude]} icon={meetingIcon}>
          <Popup>
            <Link to={`/events/${event.id}`} className="events-map-popup">
              <strong>{event.title}</strong>
              <span>{formatEventDateTime(event.startsAt, event.timezone)}</span>
              <span>{formatPlaces(event.attendeeCount, event.capacity)}</span>
            </Link>
          </Popup>
        </Marker>
      ))}
    </MapContainer>
  )
}
