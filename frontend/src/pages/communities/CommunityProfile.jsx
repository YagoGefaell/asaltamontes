import { useEffect } from "react";
import { useParams, useNavigate } from "react-router-dom";
import { useAuth } from "../../features/auth/hooks/useAuth";
import { useCommunityDetail } from "../../features/communities/hooks/useCommunityDetail";
import CommunityProfileDetail from "./CommunityProfileDetail";

function CommunityProfile() {
  const { communityName } = useParams();
  const navigate = useNavigate();
  const { currentUser } = useAuth();
  const { community, loading, error, refetch } = useCommunityDetail(communityName);

  const isMember = community?.isMember || false;

  useEffect(() => {
    if (!communityName) {
      navigate("/communities");
      return;
    }
  }, [communityName, navigate]);

  return (
    <CommunityProfileDetail
      community={community}
      loading={loading}
      error={error}
      isMember={isMember}
      onBack={() => navigate(-1)}
      onRefetch={refetch}
    />
  );
}

export default CommunityProfile;
