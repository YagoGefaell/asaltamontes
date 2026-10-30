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
  ownerLabel = "Propietaria"
}) {
  const isMember = role !== null
  const isOwner = role === 'OWNER'

  const lastActionTimeRef = useRef(0)
  
  const onToggleRef = useRef(onToggle)
  // Role to give back if leaving fails (a moderator stays a moderator, BUG-045)
  const memberRoleRef = useRef(role)

  useEffect(() => {
    onToggleRef.current = onToggle
  }, [onToggle])

  useEffect(() => {
    if (role) memberRoleRef.current = role
  }, [role])

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
    (newIsMember, { rollback = false } = {}) => {
      if (onToggleRef.current) {
        const restoredRole = rollback ? memberRoleRef.current || 'MEMBER' : 'MEMBER'
        onToggleRef.current(newIsMember ? restoredRole : null)
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
    buttonText = 'Moderadora'
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
        aria-label={isOwner ? 'Eres propietaria de esta comunidad' : `${isMember ? 'Salir de' : 'Unirse a'} la comunidad`}
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