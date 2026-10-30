import "./CommunityCreate.css";
import { useCallback, useState } from "react";
import { useNavigate } from "react-router-dom";
import { createCommunityRequest } from "../../features/communities/services/communities.service.js";
import { communityPath } from "../../features/communities/utils/communityPath.js";
import CommunityCreatorPanel from "./components/CommunityCreatorPanel";

/** Own screen to create a community (`/communities/new`); on success she lands in the new one. */
export default function CommunityCreate() {
  const navigate = useNavigate();
  const [creating, setCreating] = useState(false);

  const handleSubmit = useCallback(
    async (data) => {
      if (creating) return;
      setCreating(true);
      try {
        const created = await createCommunityRequest(data);
        // Success needs no notice: she lands in the community she just created
        navigate(communityPath(created), { replace: true });
      } finally {
        setCreating(false);
      }
    },
    [creating, navigate],
  );

  return (
    <CommunityCreatorPanel
      creatingCommunity={creating}
      onClose={() => navigate(-1)}
      onSubmitCommunity={handleSubmit}
    />
  );
}
