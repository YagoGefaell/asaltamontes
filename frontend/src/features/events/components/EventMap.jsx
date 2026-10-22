import { MapContainer, TileLayer, Marker, Popup } from 'react-leaflet'
import L from 'leaflet'
import 'leaflet/dist/leaflet.css'
import markerIcon from 'leaflet/dist/images/marker-icon.png'
import markerIcon2x from 'leaflet/dist/images/marker-icon-2x.png'
import markerShadow from 'leaflet/dist/images/marker-shadow.png'
import './EventMap.css'

// Leaflet looks for its marker images relative to the CSS, which the bundler breaks: point them explicitly
const meetingIcon = L.icon({
  iconUrl: markerIcon,
  iconRetinaUrl: markerIcon2x,
  shadowUrl: markerShadow,
  iconSize: [25, 41],
  iconAnchor: [12, 41],
  popupAnchor: [1, -34],
  shadowSize: [41, 41],
})

// OSM tile usage policy: visible attribution (D-005)
const OSM_ATTRIBUTION = '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'

/** Map with the meeting point. Loaded lazily (React.lazy) so Leaflet stays out of the main bundle. */
export default function EventMap({ latitude, longitude, label }) {
  return (
    <MapContainer center={[latitude, longitude]} zoom={14} scrollWheelZoom={false} className="event-map">
      <TileLayer attribution={OSM_ATTRIBUTION} url="https://tile.openstreetmap.org/{z}/{x}/{y}.png" />
      <Marker position={[latitude, longitude]} icon={meetingIcon}>
        {label && <Popup>{label}</Popup>}
      </Marker>
    </MapContainer>
  )
}
