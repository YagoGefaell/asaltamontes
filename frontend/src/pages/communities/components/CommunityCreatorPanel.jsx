import { useCallback, useState } from "react";
import ImageUploader from "../../../shared/ui/ImageUploader.jsx";
import { MEDIA_PURPOSE } from "../../../features/media/services/media.service.js";

const CommunityCreatorPanel = ({
  isOpen,
  creatingCommunity,
  onClose,
  onSubmitCommunity,
}) => {
  const [communityName, setCommunityName] = useState("");
  const [communityDescription, setCommunityDescription] = useState("");
  const [communityImageUrl, setCommunityImageUrl] = useState("");
  const [createCommunityError, setCreateCommunityError] = useState(null);
  const [uploadingImage, setUploadingImage] = useState(false);

  const resetState = () => {
    setCommunityName("");
    setCommunityDescription("");
    setCommunityImageUrl("");
    setCreateCommunityError(null);
  };

  const handleClose = useCallback(() => {
    if (creatingCommunity) return;
    resetState();
    onClose();
  }, [creatingCommunity, onClose]);

  const handleSubmit = useCallback(
    async (event) => {
      event.preventDefault();

      if (creatingCommunity || uploadingImage) return;

      if (!communityName.trim()) {
        setCreateCommunityError("El nombre de la comunidad es requerido.");
        return;
      }

      setCreateCommunityError(null);

      try {
        await onSubmitCommunity({
          name: communityName.trim(),
          description: communityDescription.trim() || null,
          imageUrl: communityImageUrl.trim() || null,
        });
        resetState();
        onClose();
      } catch (error) {
        setCreateCommunityError(error?.message || "No se pudo crear la comunidad");
      }
    },
    [
      creatingCommunity,
      uploadingImage,
      communityName,
      communityDescription,
      communityImageUrl,
      onSubmitCommunity,
      onClose,
    ]
  );

  if (!isOpen) {
    return null;
  }

  return (
    <div className="community-creator-page" aria-label="Crear comunidad">
      <div className="communities-header">
        <button
          type="button"
          className="chat-back-btn"
          onClick={handleClose}
          disabled={creatingCommunity}
          aria-label="Volver a comunidades"
        >
          <svg
            className="chat-back-icon"
            viewBox="0 0 24 24"
            fill="none"
            aria-hidden="true"
          >
            <path
              d="M15 18L9 12L15 6"
              stroke="currentColor"
              strokeWidth="2"
              strokeLinecap="round"
              strokeLinejoin="round"
            />
          </svg>
        </button>
        <h2>Crear comunidad</h2>
      </div>

      <form className="community-creator-form" onSubmit={handleSubmit}>
        <label className="community-creator-label" htmlFor="community-name-input">
          Nombre de la comunidad
        </label>
        <input
          id="community-name-input"
          type="text"
          className="community-creator-input"
          value={communityName}
          onChange={(event) => setCommunityName(event.target.value)}
          maxLength={80}
          disabled={creatingCommunity}
          placeholder="Ej: Mujeres en Tech"
          autoFocus
        />

        <label className="community-creator-label" htmlFor="community-description-input">
          Descripción (opcional)
        </label>
        <textarea
          id="community-description-input"
          className="community-creator-input community-creator-textarea"
          value={communityDescription}
          onChange={(event) => setCommunityDescription(event.target.value)}
          maxLength={500}
          disabled={creatingCommunity}
          placeholder="Describe la comunidad..."
          rows="4"
        />

        <span className="community-creator-label">Imagen de portada (opcional)</span>
        <ImageUploader
          value={communityImageUrl}
          onChange={setCommunityImageUrl}
          onUploadingChange={setUploadingImage}
          purpose={MEDIA_PURPOSE.COMMUNITY_COVER}
          label="Imagen de portada"
          chooseLabel="Elegir imagen"
          changeLabel="Cambiar imagen"
          shape="wide"
          disabled={creatingCommunity}
        />

        {createCommunityError && (
          <p className="community-creator-error">{createCommunityError}</p>
        )}

        <div className="community-creator-actions">
          <button
            type="button"
            className="community-creator-btn-cancel"
            onClick={handleClose}
            disabled={creatingCommunity}
          >
            Cancelar
          </button>
          <button
            type="submit"
            className="community-creator-btn-submit"
            disabled={creatingCommunity || uploadingImage}
          >
            {creatingCommunity ? "Creando..." : "Crear comunidad"}
          </button>
        </div>
      </form>
    </div>
  );
};

export default CommunityCreatorPanel;
