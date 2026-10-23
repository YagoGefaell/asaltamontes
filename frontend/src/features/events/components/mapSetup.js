import L from 'leaflet'
import markerIcon from 'leaflet/dist/images/marker-icon.png'
import markerIcon2x from 'leaflet/dist/images/marker-icon-2x.png'
import markerShadow from 'leaflet/dist/images/marker-shadow.png'

// Leaflet looks for its marker images relative to the CSS, which the bundler breaks: point them explicitly
export const meetingIcon = L.icon({
  iconUrl: markerIcon,
  iconRetinaUrl: markerIcon2x,
  shadowUrl: markerShadow,
  iconSize: [25, 41],
  iconAnchor: [12, 41],
  popupAnchor: [1, -34],
  shadowSize: [41, 41],
})

// OSM tile usage policy: visible attribution (D-005)
export const OSM_ATTRIBUTION = '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
export const OSM_TILES = 'https://tile.openstreetmap.org/{z}/{x}/{y}.png'

// Centre of Spain, wide enough to see the peninsula, when there is no point yet
export const DEFAULT_CENTER = [40.4, -3.7]
export const DEFAULT_ZOOM = 5
