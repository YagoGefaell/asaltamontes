import { useEffect, useRef } from 'react'
import { useOptimisticToggle } from '../../hooks/useOptimisticToggle'
import { useAuth } from '../../features/auth/hooks/useAuth'
import { followUserRequest, unfollowUserRequest } from '../../features/users/services/users.service'
import './FollowUserButton.css'

function FollowUserButton({ 
  userId, 
  initialIsFollowing = false, 
  delayBeforeRequest = 500,
  onToggle = null,
  followLabel = "Seguir",
  unfollowLabel = "Siguiendo"
}) {
  const { incrementFollowing, decrementFollowing } = useAuth()
  
  const { state, isLoading, error, toggle } = useOptimisticToggle(
    initialIsFollowing,
    async (isFollowing) => {
      if (isFollowing) {
        await followUserRequest(userId)
        incrementFollowing()
      } else {
        await unfollowUserRequest(userId)
        decrementFollowing()
      }
    },
    delayBeforeRequest
  )

  const previousStateRef = useRef(state)
  const onToggleRef = useRef(onToggle)

  useEffect(() => {
    onToggleRef.current = onToggle
  }, [onToggle])

  useEffect(() => {
    if (!isLoading && state !== previousStateRef.current) {
      previousStateRef.current = state
      if (onToggleRef.current) {
        onToggleRef.current(state)
      }
    }
  }, [state, isLoading])

  const handleClick = (e) => {
    e.currentTarget.blur()
    toggle()
  }

  // 2. MODIFICADO: El texto reacciona al instante sin esperar a la API
  const buttonText = state ? unfollowLabel : followLabel

  // 3. MODIFICADO: Quitamos la clase 'loading' para que no pinte spinners innecesarios
  const buttonClass = `follow-user-button ${state ? 'following' : 'not-following'}`

  return (
    <div className="follow-user-button-container">
      <button
        className={buttonClass}
        onClick={handleClick}
        aria-label={`${state ? 'Dejar de seguir a' : 'Seguir a'} ${userId}`}
      >
        {buttonText}
      </button>
      {error && (
        <div className="follow-user-error">
          <p>{error}</p>
        </div>
      )}
    </div>
  )
}

export default FollowUserButton