import { useEffect, useRef, useState } from 'react'
import { FaShareAlt } from 'react-icons/fa'
import './EventActions.css'

/**
 * Shares the link of the event with the Web Share API; where it does not exist (desktop browsers) the link is
 * copied and the label says so in place (no pop-up, DESIGN «Diálogos y avisos»).
 */
export default function ShareEventButton({ event, onError }) {
  const [copied, setCopied] = useState(false)
  const timer = useRef(null)

  useEffect(() => () => clearTimeout(timer.current), [])

  const handleShare = async () => {
    onError?.('')
    const url = `${window.location.origin}/events/${event.id}`
    try {
      if (navigator.share) {
        await navigator.share({ title: event.title, url })
        return
      }
      await navigator.clipboard.writeText(url)
      setCopied(true)
      clearTimeout(timer.current)
      timer.current = setTimeout(() => setCopied(false), 2500)
    } catch (err) {
      // She closed the share sheet: not an error
      if (err?.name !== 'AbortError') onError?.('No se ha podido compartir. Copia el enlace desde la barra del navegador.')
    }
  }

  return (
    <button type="button" className="event-action" onClick={handleShare} aria-label="Compartir la quedada">
      <FaShareAlt aria-hidden="true" />
      <span aria-live="polite">{copied ? 'Enlace copiado' : 'Compartir'}</span>
    </button>
  )
}
