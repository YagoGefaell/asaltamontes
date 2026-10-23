import { MapContainer, TileLayer, Marker, Popup } from 'react-leaflet'
import 'leaflet/dist/leaflet.css'
import { meetingIcon, OSM_ATTRIBUTION, OSM_TILES } from './mapSetup.js'
import './EventMap.css'

/** Map with the meeting point. Loaded lazily (React.lazy) so Leaflet stays out of the main bundle. */
export default function EventMap({ latitude, longitude, label }) {
  return (
    <MapContainer center={[latitude, longitude]} zoom={14} scrollWheelZoom={false} className="event-map">
      <TileLayer attribution={OSM_ATTRIBUTION} url={OSM_TILES} />
      <Marker position={[latitude, longitude]} icon={meetingIcon}>
        {label && <Popup>{label}</Popup>}
      </Marker>
    </MapContainer>
  )
}
