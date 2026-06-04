import { useEffect, useRef, useCallback } from 'react'
import { useOptimisticToggle } from '../../hooks/useOptimisticToggle'
import { joinCommunityRequest, leaveCommunityRequest } from '../../features/communities/services/communities.service'
import './CommunityJoinButton.css'

function CommunityJoinButton({ 
  communityId, 
  delayBeforeRequest = 500,
  onToggle = null,
  joinLabel = "Unirse",
  memberLabel = "Miembro",
  role = null,
  ownerLabel = "Propietario"
}) {
  const isMember = role !== null
  const isOwner = role === 'OWNER'

  const lastActionTimeRef = useRef(0)
  
  const onToggleRef = useRef(onToggle)

  useEffect(() => {
    onToggleRef.current = onToggle
  }, [onToggle])

  const handleSave = useCallback(async (newIsMember) => {
    if (newIsMember) {
      await joinCommunityRequest(communityId)
    } else {
      await leaveCommunityRequest(communityId)
    }

    lastActionTimeRef.current = Date.now()
  }, [communityId])

  const { state, error, toggle } = useOptimisticToggle(
    isMember,
    handleSave,
    delayBeforeRequest,
    (newIsMember) => {
      if (onToggleRef.current) {
        onToggleRef.current(newIsMember ? 'MEMBER' : null)
      }
    }
  )

  const handleClick = (e) => {
    e.currentTarget.blur()
    toggle()
  }
  
  let buttonText
  let buttonClass = 'community-join-button'
  
  if (role === 'OWNER') {
    buttonText = ownerLabel
    buttonClass += ' owner'
  } else if (role === 'MODERATOR') {
    buttonText = 'Moderador'
    buttonClass += ' moderator'
  } else if (state === true) {
    buttonText = memberLabel
    buttonClass += ' member'
  } else {
    buttonText = joinLabel
    buttonClass += ' not-member'
  }

  const isDisabled = isOwner

  return (
    <div className="community-join-button-container">
      <button
        className={buttonClass}
        onClick={handleClick}
        disabled={isDisabled}
        aria-label={isOwner ? 'Eres propietario de esta comunidad' : `${isMember ? 'Salir de' : 'Unirse a'} la comunidad`}
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