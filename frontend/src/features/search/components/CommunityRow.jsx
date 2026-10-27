import { Link } from 'react-router-dom'
import CommunityJoinButton from '../../../shared/components/CommunityJoinButton'
import CommunityRequestButton from '../../../shared/components/CommunityRequestButton'
import Avatar from '../../../shared/ui/Avatar'
import { communityPath } from '../../communities/utils/communityPath.js'
import './CommunityRow.css'

/** Compact row of a community: image, name, city · members and a small join button. */
export default function CommunityRow({ community }) {
  const members = `${community.memberCount} ${community.memberCount === 1 ? 'miembro' : 'miembros'}`
  const meta = [community.city, members].filter(Boolean).join(' · ')

  return (
    <div className="community-row">
      <Link to={communityPath(community)} className="community-row__link">
        <Avatar src={community.imageUrl} name={community.name} size="lg" />
        <span className="community-row__text">
          <span className="community-row__name">{community.name}</span>
          <span className="community-row__meta">{meta}</span>
        </span>
      </Link>
      <div className="community-row__action">
        {community.visibility === 'PRIVATE' && !community.role ? (
          <CommunityRequestButton communityId={community.id} />
        ) : (
          <CommunityJoinButton
            communityId={community.id}
            role={community.role}
            joinLabel="Unirse"
            memberLabel="Miembro"
          />
        )}
      </div>
    </div>
  )
}
