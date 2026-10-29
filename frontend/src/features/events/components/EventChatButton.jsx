import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import Button from '../../../shared/forms/Button.jsx'
import FormError from '../../../shared/forms/FormError.jsx'
import { getEventChatRequest } from '../services/events.service.js'

// Opens the chat of the quedada (T21) in the messages page. Only for people who are going.
export default function EventChatButton({ event }) {
  const navigate = useNavigate()
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')

  const handleOpen = async () => {
    setLoading(true)
    setError('')
    try {
      const chat = await getEventChatRequest(event.id)
      navigate('/messages', {
        state: {
          selectedConversation: {
            conversationId: chat.conversationId,
            type: 'GROUP',
            displayName: event.title,
            displayImage: event.imageUrl || null,
          },
        },
      })
    } catch (err) {
      setError(err.message)
    } finally {
      setLoading(false)
    }
  }

  return (
    <>
      <Button variant="outline" onClick={handleOpen} loading={loading}>
        Chat de la quedada
      </Button>
      <FormError message={error} />
    </>
  )
}
