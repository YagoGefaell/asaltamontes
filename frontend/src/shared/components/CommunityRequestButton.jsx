import { useState } from 'react'
import { joinCommunityRequest } from '../../features/communities/services/communities.service'
import { communityErrorMessage } from '../../features/communities/utils/communityErrors'
import './CommunityJoinButton.css'

/**
 * Join button of a PRIVATE community the user is not in (T19b): "Solicitar unirse" sends the request (202) and
 * then shows "Solicitud enviada". There is no way to withdraw it. A rejection has a cooldown (429 + Retry-After),
 * and a 403 carries a neutral message that does not reveal why.
 */
export default function CommunityRequestButton({ communityId, joinRequested = false, onRequested }) {
  const [sent, setSent] = useState(false)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState(null)
  const pending = joinRequested || sent

  const handleClick = async () => {
    if (pending || loading) return
    setLoading(true)
    setError(null)
    try {
      await joinCommunityRequest(communityId)
      setSent(true)
      onRequested?.()
    } catch (err) {
      setError(communityErrorMessage(err, 'No se pudo enviar la solicitud.'))
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="community-join-button-container">
      <button
        type="button"
        className={`community-join-button ${pending ? 'member community-join-pending' : 'not-member'}`}
        onClick={handleClick}
        disabled={pending || loading}
        aria-busy={loading || undefined}
      >
        {pending ? 'Solicitud enviada' : loading ? 'Enviando...' : 'Solicitar unirse'}
      </button>
      {error && (
        <div className="community-join-error" role="alert">
          <p>{error}</p>
        </div>
      )}
    </div>
  )
}
