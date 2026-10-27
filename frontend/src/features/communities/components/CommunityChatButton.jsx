import { useState } from "react";
import { useNavigate } from "react-router-dom";
import Button from "../../../shared/forms/Button";
import { useToast } from "../../../shared/ui/Toast";
import { getCommunityChatRequest } from "../services/communities.service";

// Opens the chat of the community (T20) in the messages page. Only members see it.
export default function CommunityChatButton({ community }) {
  const navigate = useNavigate();
  const toast = useToast();
  const [loading, setLoading] = useState(false);

  const handleOpen = async () => {
    setLoading(true);
    try {
      const chat = await getCommunityChatRequest(community.id);
      navigate("/messages", {
        state: {
          selectedConversation: {
            conversationId: chat.conversationId,
            type: "GROUP",
            displayName: community.name,
            displayImage: community.imageUrl || null,
          },
        },
      });
    } catch (error) {
      toast.error(error.message);
    } finally {
      setLoading(false);
    }
  };

  return (
    <Button variant="outline" size="small" onClick={handleOpen} loading={loading}>
      Chat
    </Button>
  );
}
