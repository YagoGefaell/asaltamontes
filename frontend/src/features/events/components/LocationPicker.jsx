import { useEffect } from 'react'
import { MapContainer, TileLayer, Marker, useMap, useMapEvents } from 'react-leaflet'
import 'leaflet/dist/leaflet.css'
import { DEFAULT_CENTER, DEFAULT_ZOOM, meetingIcon, OSM_ATTRIBUTION, OSM_TILES } from './mapSetup.js'
import './EventMap.css'

function ClickToPlace({ onChange }) {
  useMapEvents({ click: (e) => onChange(e.latlng.lat, e.latlng.lng) })
  return null
}

// Fly to a place picked in the search box (`focus` is a new object each time she picks one)
function FollowFocus({ focus }) {
  const map = useMap()
  useEffect(() => {
    if (focus) map.setView([focus.latitude, focus.longitude], FOCUS_ZOOM)
  }, [focus, map])
  return null
}

// Tells the form which area she is looking at, to rank the place suggestions near it
function ReportView({ onViewChange }) {
  useMapEvents({
    moveend: (e) => {
      const center = e.target.getCenter()
      onViewChange({ latitude: center.lat, longitude: center.lng, zoom: e.target.getZoom() })
    },
  })
  return null
}

const FOCUS_ZOOM = 16

// Coordinates with ~1 m precision are plenty and keep the payload tidy
const round = (n) => Math.round(n * 1e6) / 1e6

/**
 * Map where she picks the meeting point: tap to place the marker, drag it to adjust.
 * `onChange(latitude, longitude)`. `focus` centres the map on a place picked elsewhere;
 * `onViewChange({ latitude, longitude, zoom })` reports where she has moved it. Lazy-loaded with Leaflet.
 */
export default function LocationPicker({ latitude, longitude, onChange, focus = null, onViewChange }) {
  const hasPoint = latitude != null && longitude != null
  const set = (lat, lng) => onChange(round(lat), round(lng))
  return (
    <MapContainer
      center={hasPoint ? [latitude, longitude] : DEFAULT_CENTER}
      zoom={hasPoint ? 14 : DEFAULT_ZOOM}
      scrollWheelZoom={false}
      className="event-map event-map--picker"
    >
      <TileLayer attribution={OSM_ATTRIBUTION} url={OSM_TILES} />
      <ClickToPlace onChange={set} />
      <FollowFocus focus={focus} />
      {onViewChange && <ReportView onViewChange={onViewChange} />}
      {hasPoint && (
        <Marker
          position={[latitude, longitude]}
          icon={meetingIcon}
          draggable
          eventHandlers={{
            dragend: (e) => {
              const point = e.target.getLatLng()
              set(point.lat, point.lng)
            },
          }}
        />
      )}
    </MapContainer>
  )
}
