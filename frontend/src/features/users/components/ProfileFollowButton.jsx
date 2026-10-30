import { useState } from 'react'
import { useAuth } from '../../auth/hooks/useAuth'
import { followUserRequest, unfollowUserRequest } from '../services/users.service'
import '../../../shared/components/FollowUserButton.css'

/**
 * Follow button of another user's profile (T23a). It has three states: follow, requested (private profile waiting
 * for approval, tap to withdraw) and following. It waits for the server (no optimistic change) because the answer
 * decides which one it is.
 */
export default function ProfileFollowButton({ username, isFollowing = false, isFollowRequested = false, isPrivate = false, onChange }) {
  const { incrementFollowing, decrementFollowing } = useAuth()
  const [state, setState] = useState(isFollowing ? 'following' : isFollowRequested ? 'requested' : 'none')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')

  const click = async (event) => {
    event.currentTarget.blur()
    setBusy(true)
    setError('')
    try {
      if (state === 'none') {
        const result = await followUserRequest(username)
        const next = result?.status === 'REQUESTED' ? 'requested' : 'following'
        if (next === 'following') incrementFollowing()
        setState(next)
        onChange?.(next)
      } else {
        await unfollowUserRequest(username)
        if (state === 'following') decrementFollowing()
        setState('none')
        onChange?.('none')
      }
    } catch (err) {
      setError(err?.message || (typeof err === 'string' ? err : 'No se ha podido completar la acción'))
    } finally {
      setBusy(false)
    }
  }

  const label = state === 'following' ? 'Siguiendo' : state === 'requested' ? 'Solicitado' : isPrivate ? 'Solicitar' : 'Seguir'
  const action = state === 'following' ? 'Dejar de seguir a' : state === 'requested' ? 'Cancelar la solicitud a' : isPrivate ? 'Solicitar seguir a' : 'Seguir a'

  return (
    <div className="follow-user-button-container">
      <button
        type="button"
        className={`follow-user-button ${state === 'none' ? 'not-following' : 'following'}`}
        onClick={click}
        disabled={busy}
        aria-label={`${action} ${username}`}
      >
        {label}
      </button>
      {error && <p className="follow-user-error" role="alert">{error}</p>}
    </div>
  )
}
