import { useEffect, useRef } from 'react'
import { useOptimisticToggle } from '../../hooks/useOptimisticToggle'
import { joinCommunityRequest, leaveCommunityRequest } from '../../features/communities/services/communities.service'
import './CommunityJoinButton.css'

/**
 * Botón para unirse/salir de una comunidad con debounce silencioso
 */
function CommunityJoinButton({ 
  communityId, 
  initialIsMember = false, 
  delayBeforeRequest = 500,
  onToggle = null,
  joinLabel = "Unirse",
  memberLabel = "Miembro"
  // 1. ELIMINADO: Ya no necesitamos 'loadingLabel' porque el botón es 100% interactivo e instantáneo
}) {
  const { state, isLoading, error, toggle } = useOptimisticToggle(
    initialIsMember,
    async (isMember) => {
      if (isMember) {
        await joinCommunityRequest(communityId)
      } else {
        await leaveCommunityRequest(communityId)
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

  // 2. MODIFICADO: El texto cambia INSTANTÁNEAMENTE según el estado optimista.
  // Ya no hay esperas ni textos intermedios de carga.
  const buttonText = state ? memberLabel : joinLabel

  // 3. MODIFICADO: Eliminamos la clase '${isLoading ? 'loading' : ''}' para que no renderice el spinner de CSS
  const buttonClass = `community-join-button ${state ? 'member' : 'not-member'}`

  return (
    <div className="community-join-button-container">
      <button
        className={buttonClass}
        onClick={handleClick}
        /* 4. MODIFICADO: QUITAMOS disabled={isLoading}. 
           Esto permite que si el usuario se confunde, pueda volver a hacer click inmediatamente 
           para cancelar la petición antes de que expire el delay. */
        aria-label={`${state ? 'Salir de' : 'Unirse a'} la comunidad`}
      >
        {buttonText}
      </button>
      {error && (
        <div className="community-join-error">
          <p>{error}</p>
        </div>
      )}
    </div>
  )
}

export default CommunityJoinButton