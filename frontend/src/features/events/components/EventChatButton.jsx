import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { FaRegComments } from 'react-icons/fa'
import { getEventChatRequest } from '../services/events.service.js'
import './EventActions.css'

// Opens the chat of the quedada (T21) in the messages page. Only for people who are going.
// Icon-with-text action of the detail row (T29m); the error is shown by the parent through `onError`.
export default function EventChatButton({ event, onError }) {
  const navigate = useNavigate()
  const [loading, setLoading] = useState(false)

  const handleOpen = async () => {
    setLoading(true)
    onError?.('')
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
      onError?.(err.message)
    } finally {
      setLoading(false)
    }
  }

  return (
    <button type="button" className="event-action" onClick={handleOpen} disabled={loading} aria-label="Chat de la quedada">
      <FaRegComments aria-hidden="true" />
      <span>Chat</span>
    </button>
  )
}
