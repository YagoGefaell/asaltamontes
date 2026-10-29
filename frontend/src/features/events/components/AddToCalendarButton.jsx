import { useState } from 'react'
import Button from '../../../shared/forms/Button.jsx'
import FormError from '../../../shared/forms/FormError.jsx'
import { getEventCalendarRequest } from '../services/events.service.js'

// Downloads the .ics of the event (T21) so the calendar app of her phone or computer opens it
export default function AddToCalendarButton({ event }) {
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')

  const handleClick = async () => {
    setLoading(true)
    setError('')
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
      setError(err.message)
    } finally {
      setLoading(false)
    }
  }

  return (
    <>
      <Button variant="outline" onClick={handleClick} loading={loading}>
        Añadir al calendario
      </Button>
      <FormError message={error} />
    </>
  )
}
