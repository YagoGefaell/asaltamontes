import { MapContainer, TileLayer, Marker, useMapEvents } from 'react-leaflet'
import 'leaflet/dist/leaflet.css'
import { DEFAULT_CENTER, DEFAULT_ZOOM, meetingIcon, OSM_ATTRIBUTION, OSM_TILES } from './mapSetup.js'
import './EventMap.css'

function ClickToPlace({ onChange }) {
  useMapEvents({ click: (e) => onChange(e.latlng.lat, e.latlng.lng) })
  return null
}

// Coordinates with ~1 m precision are plenty and keep the payload tidy
const round = (n) => Math.round(n * 1e6) / 1e6

/**
 * Map where she picks the meeting point: tap to place the marker, drag it to adjust.
 * `onChange(latitude, longitude)`. Lazy-loaded with Leaflet.
 */
export default function LocationPicker({ latitude, longitude, onChange }) {
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
