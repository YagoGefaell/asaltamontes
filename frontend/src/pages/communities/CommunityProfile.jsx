import { useEffect } from "react";
import { useParams, useNavigate } from "react-router-dom";
import { useCommunityDetail } from "../../features/communities/hooks/useCommunityDetail";
import CommunityProfileDetail from "./CommunityProfileDetail";

function CommunityProfile() {
  const { slug } = useParams();
  const navigate = useNavigate();
  const { community, loading, error, refetch } = useCommunityDetail(slug);

  const isMember = community?.isMember || false;

  useEffect(() => {
    if (!slug) {
      navigate("/communities");
      return;
    }
  }, [slug, navigate]);

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
