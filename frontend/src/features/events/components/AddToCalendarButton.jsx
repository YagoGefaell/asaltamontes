import { useState } from 'react'
import Button from '../../../shared/forms/Button.jsx'
import { useToast } from '../../../shared/ui/Toast.jsx'
import { getEventCalendarRequest } from '../services/events.service.js'

// Downloads the .ics of the event (T21) so the calendar app of her phone or computer opens it
export default function AddToCalendarButton({ event }) {
  const toast = useToast()
  const [loading, setLoading] = useState(false)

  const handleClick = async () => {
    setLoading(true)
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
    } catch (error) {
      toast.error(error.message)
    } finally {
      setLoading(false)
    }
  }

  return (
    <Button variant="outline" onClick={handleClick} loading={loading}>
      Añadir al calendario
    </Button>
  )
}
