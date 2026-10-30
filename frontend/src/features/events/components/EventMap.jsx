import { useEffect } from 'react'
import { MapContainer, TileLayer, Marker, Popup, useMap } from 'react-leaflet'
import 'leaflet/dist/leaflet.css'
import { meetingIcon, OSM_ATTRIBUTION, OSM_TILES } from './mapSetup.js'
import './EventMap.css'

/**
 * Leaflet measures its container once, when it mounts. On a direct entry the page is still laying out (header,
 * cover, fonts), so the size it saw can be wrong and the tiles stay grey: re-measure whenever the box changes.
 */
function KeepSize() {
  const map = useMap()
  useEffect(() => {
    const container = map.getContainer()
    const refresh = () => map.invalidateSize()
    const observer = typeof ResizeObserver === 'undefined' ? null : new ResizeObserver(refresh)
    observer?.observe(container)
    const timer = setTimeout(refresh, 250)
    return () => {
      observer?.disconnect()
      clearTimeout(timer)
    }
  }, [map])
  return null
}

/** Map with the meeting point. Loaded lazily (React.lazy) so Leaflet stays out of the main bundle. */
export default function EventMap({ latitude, longitude, label }) {
  return (
    <MapContainer center={[latitude, longitude]} zoom={14} scrollWheelZoom={false} className="event-map">
      <TileLayer attribution={OSM_ATTRIBUTION} url={OSM_TILES} />
      <Marker position={[latitude, longitude]} icon={meetingIcon}>
        {label && <Popup>{label}</Popup>}
      </Marker>
      <KeepSize />
    </MapContainer>
  )
}
