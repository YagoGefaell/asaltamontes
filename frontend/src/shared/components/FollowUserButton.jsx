import { useEffect, useRef } from 'react'
import { useOptimisticToggle } from '../../hooks/useOptimisticToggle'
import { followUserRequest, unfollowUserRequest } from '../../features/users/services/users.service'
import './FollowUserButton.css'

/**
 * Botón para seguir/dejar de seguir a un usuario con debounce silencioso
 */
function FollowUserButton({ 
  userId, 
  initialIsFollowing = false, 
  delayBeforeRequest = 500,
  onToggle = null,
  followLabel = "Seguir",
  unfollowLabel = "Siguiendo"
  // 1. ELIMINADO: Ya no necesitamos 'loadingLabel'
}) {
  const { state, isLoading, error, toggle } = useOptimisticToggle(
    initialIsFollowing,
    async (isFollowing) => {
      if (isFollowing) {
        await followUserRequest(userId)
      } else {
        await unfollowUserRequest(userId)
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
        /* 4. MODIFICADO: Quitamos 'disabled={isLoading}' para dar libertad total de click 
           y permitir la cancelación instantánea si el usuario se arrepiente */
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