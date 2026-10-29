import { useState } from "react";
import { useNavigate } from "react-router-dom";
import Button from "../../../shared/forms/Button";
import FormError from "../../../shared/forms/FormError";
import { getCommunityChatRequest } from "../services/communities.service";

// Opens the chat of the community (T20) in the messages page. Only members see it.
export default function CommunityChatButton({ community }) {
  const navigate = useNavigate();
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");

  const handleOpen = async () => {
    setLoading(true);
    setError("");
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
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  };

  return (
    <>
      <Button variant="outline" size="small" onClick={handleOpen} loading={loading}>
        Chat
      </Button>
      <FormError message={error} />
    </>
  );
}
