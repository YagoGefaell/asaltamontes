import { useState } from 'react'
import { FaRegCalendarPlus } from 'react-icons/fa'
import { getEventCalendarRequest } from '../services/events.service.js'
import './EventActions.css'

// Downloads the .ics of the event (T21) so the calendar app of her phone or computer opens it.
// Icon-with-text action of the detail row (T29m); the error is shown by the parent through `onError`.
export default function AddToCalendarButton({ event, onError }) {
  const [loading, setLoading] = useState(false)

  const handleClick = async () => {
    setLoading(true)
    onError?.('')
    try {
      const text = await getEventCalendarRequest(event.id)
      const url = URL.createObjectURL(new Blob([text], { type: 'text/calendar;charset=utf-8' }))
      const link = document.createElement('a')
      link.href = url
      link.download = 'quedada.ics'
      document.body.appendChild(link)
      link.click()
      link.remove()
      URL.revokeObjectURL(url)
    } catch (err) {
      onError?.(err.message)
    } finally {
      setLoading(false)
    }
  }

  return (
    <button type="button" className="event-action" onClick={handleClick} disabled={loading} aria-label="Añadir al calendario">
      <FaRegCalendarPlus aria-hidden="true" />
      <span>Calendario</span>
    </button>
  )
}
