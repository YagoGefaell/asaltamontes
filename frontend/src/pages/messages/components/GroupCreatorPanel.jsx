import { useCallback, useEffect, useState } from "react";
import { FaUsers } from "react-icons/fa";
import Button from "../../../shared/forms/Button.jsx";
import ImageUploader from "../../../shared/ui/ImageUploader.jsx";
import PageHeader from "../../../shared/ui/PageHeader.jsx";
import { MEDIA_PURPOSE } from "../../../features/media/services/media.service.js";
import { getUsersByUsername } from "../../../features/users/services/users.service.js";

const GroupCreatorPanel = ({
  isOpen,
  creatingGroup,
  currentUsername,
  onClose,
  onSubmitGroup,
}) => {
  const [groupName, setGroupName] = useState("");
  const [groupImage, setGroupImage] = useState("");
  const [groupMediaId, setGroupMediaId] = useState(null);
  const [participantsQuery, setParticipantsQuery] = useState("");
  const [participantResults, setParticipantResults] = useState([]);
  const [participantSearchLoading, setParticipantSearchLoading] = useState(false);
  const [participantSearchError, setParticipantSearchError] = useState(null);
  const [selectedParticipants, setSelectedParticipants] = useState([]);
  const [createGroupError, setCreateGroupError] = useState(null);
  const [uploadingImage, setUploadingImage] = useState(false);

  const resetState = useCallback(() => {
    setGroupName("");
    setGroupImage("");
    setGroupMediaId(null);
    setParticipantsQuery("");
    setParticipantResults([]);
    setParticipantSearchLoading(false);
    setParticipantSearchError(null);
    setSelectedParticipants([]);
    setCreateGroupError(null);
  }, []);

  useEffect(() => {
    if (isOpen) {
      resetState();
    }
  }, [isOpen, resetState]);

  useEffect(() => {
    if (!isOpen) {
      return;
    }

    const query = participantsQuery.trim();
    if (query.length < 2) {
      setParticipantResults([]);
      setParticipantSearchError(null);
      setParticipantSearchLoading(false);
      return;
    }

    let cancelled = false;
    setParticipantSearchLoading(true);
    setParticipantSearchError(null);

    const timeoutId = setTimeout(async () => {
      try {
        const users = await getUsersByUsername(query);
        if (cancelled) return;

        const selectedUsernames = new Set(
          selectedParticipants.map((participant) => participant.username.toLowerCase())
        );

        const filtered = Array.isArray(users)
          ? users.filter((user) => {
              const username = String(user?.username || "").trim();
              if (!username) return false;
              if (username.toLowerCase() === String(currentUsername || "").toLowerCase()) return false;
              if (selectedUsernames.has(username.toLowerCase())) return false;
              return true;
            })
          : [];

        setParticipantResults(filtered);
      } catch (error) {
        if (cancelled) return;
        setParticipantSearchError(error?.message || "Error buscando participantes");
      } finally {
        if (!cancelled) {
          setParticipantSearchLoading(false);
        }
      }
    }, 300);

    return () => {
      cancelled = true;
      clearTimeout(timeoutId);
    };
  }, [participantsQuery, isOpen, selectedParticipants, currentUsername]);

  const addParticipant = useCallback((user) => {
    const username = String(user?.username || "").trim();
    if (!username) return;

    setSelectedParticipants((prev) => {
      const exists = prev.some((participant) => participant.username.toLowerCase() === username.toLowerCase());
      if (exists) return prev;
      return [...prev, user];
    });

    setParticipantsQuery("");
    setParticipantResults([]);
    setParticipantSearchError(null);
  }, []);

  const removeParticipant = useCallback((username) => {
    setSelectedParticipants((prev) => (
      prev.filter((participant) => participant.username.toLowerCase() !== String(username).toLowerCase())
    ));
  }, []);

  const handleClose = useCallback(() => {
    if (creatingGroup) return;
    onClose();
  }, [creatingGroup, onClose]);

  const handleSubmit = useCallback(async (event) => {
    event.preventDefault();

    if (creatingGroup || uploadingImage) return;

    if (selectedParticipants.length === 0) {
      setCreateGroupError("Debes seleccionar al menos un participante.");
      return;
    }

    setCreateGroupError(null);

    try {
      await onSubmitGroup({
        groupName: groupName.trim(),
        groupMediaId,
        users: selectedParticipants.map((participant) => participant.username),
      });
      onClose();
    } catch (error) {
      setCreateGroupError(error?.message || "No se pudo crear el grupo");
    }
  }, [creatingGroup, uploadingImage, selectedParticipants, onSubmitGroup, groupName, groupMediaId, onClose]);

  if (!isOpen) {
    return null;
  }

  return (
    <div className="group-creator-page" aria-label="Crear grupo">
      <PageHeader title="Crear grupo" onBack={handleClose} backDisabled={creatingGroup} backLabel="Volver a contactos" />

      <form className="group-creator-form" onSubmit={handleSubmit}>
          <ImageUploader
            value={groupImage}
            onChange={(url, mediaId) => {
              setGroupImage(url);
              setGroupMediaId(mediaId || null);
            }}
            onUploadingChange={setUploadingImage}
            purpose={MEDIA_PURPOSE.GROUP_IMAGE}
            label="Imagen del grupo"
            chooseLabel="Elegir imagen"
            changeLabel="Cambiar imagen"
            disabled={creatingGroup}
            tapToChoose
            emptyIcon={<FaUsers />}
          />

          <label className="group-creator-label" htmlFor="group-name-input">Nombre del grupo</label>
          <input
            id="group-name-input"
            type="text"
            className="group-creator-input"
            value={groupName}
            onChange={(event) => setGroupName(event.target.value)}
            maxLength={80}
            disabled={creatingGroup}
          />

          <label className="group-creator-label" htmlFor="group-users-input">Buscar participantes</label>
          <input
            id="group-users-input"
            type="text"
            className="group-creator-input"
            value={participantsQuery}
            onChange={(event) => setParticipantsQuery(event.target.value)}
            disabled={creatingGroup}
            autoComplete="off"
          />

          {participantSearchLoading && <p className="group-creator-hint" role="status">Buscando usuarias...</p>}
          {!participantSearchLoading && participantSearchError && (
            <p className="group-creator-error" role="alert">{participantSearchError}</p>
          )}

          {!participantSearchLoading && !participantSearchError && participantsQuery.trim().length >= 2 && participantResults.length === 0 && (
            <p className="group-creator-hint" role="status">Sin resultados para esa búsqueda.</p>
          )}

          {!participantSearchLoading && !participantSearchError && participantResults.length > 0 && (
            <div className="group-search-results">
              {participantResults.map((user) => (
                <button
                  key={user.username}
                  type="button"
                  className="group-search-result-item"
                  onClick={() => addParticipant(user)}
                  disabled={creatingGroup}
                >
                  <span className="group-search-avatar" aria-hidden="true">
                    {user?.profilePictureUrl ? (
                      <img
                        src={user.profilePictureUrl}
                        alt={user.username}
                        className="group-search-avatar-image"
                      />
                    ) : (
                      String(user?.username || "?").charAt(0).toUpperCase()
                    )}
                  </span>
                  <span className="group-search-main">{user.fullName || user.username}</span>
                  <span className="group-search-username">@{user.username}</span>
                </button>
              ))}
            </div>
          )}

          <div className="group-selected-users">
            {selectedParticipants.map((participant) => (
              <div key={participant.username} className="group-selected-user-chip">
                <span className="group-chip-label">@{participant.username}</span>
                <Button
                  variant="ghost"
                  size="small"
                  className="group-chip-remove"
                  onClick={() => removeParticipant(participant.username)}
                  disabled={creatingGroup}
                  aria-label={`Quitar ${participant.username}`}
                >
                  <span aria-hidden="true">×</span>
                </Button>
              </div>
            ))}
          </div>

          {createGroupError && <p className="group-creator-error" role="alert">{createGroupError}</p>}

          <Button type="submit" className="group-creator-submit" disabled={creatingGroup || uploadingImage}>
            {creatingGroup ? "Creando..." : "Crear grupo"}
          </Button>
      </form>
    </div>
  );
};

export default GroupCreatorPanel;
