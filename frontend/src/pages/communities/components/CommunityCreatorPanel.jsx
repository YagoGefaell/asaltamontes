import { useCallback, useState } from "react";
import { FaMountain } from "react-icons/fa";
import Button from "../../../shared/forms/Button.jsx";
import FormError from "../../../shared/forms/FormError.jsx";
import Input from "../../../shared/forms/Input.jsx";
import SectionCard from "../../../shared/ui/SectionCard.jsx";
import ImageUploader from "../../../shared/ui/ImageUploader.jsx";
import PageHeader from "../../../shared/ui/PageHeader.jsx";
import { MEDIA_PURPOSE } from "../../../features/media/services/media.service.js";

const CommunityCreatorPanel = ({
  creatingCommunity,
  onClose,
  onSubmitCommunity,
}) => {
  const [communityName, setCommunityName] = useState("");
  const [communityDescription, setCommunityDescription] = useState("");
  const [communityImageUrl, setCommunityImageUrl] = useState("");
  const [coverMediaId, setCoverMediaId] = useState(null);
  const [createCommunityError, setCreateCommunityError] = useState(null);
  const [uploadingImage, setUploadingImage] = useState(false);

  const resetState = () => {
    setCommunityName("");
    setCommunityDescription("");
    setCommunityImageUrl("");
    setCoverMediaId(null);
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
          coverMediaId,
        });
      } catch (error) {
        setCreateCommunityError(error?.message || "No se pudo crear la comunidad");
      }
    },
    [
      creatingCommunity,
      uploadingImage,
      communityName,
      communityDescription,
      coverMediaId,
      onSubmitCommunity,
    ]
  );

  return (
    <div className="community-creator-page" aria-label="Crear comunidad">
      <PageHeader title="Crear comunidad" onBack={handleClose} backDisabled={creatingCommunity} backLabel="Volver" />

      <form className="community-creator-form" onSubmit={handleSubmit}>
        <ImageUploader
          value={communityImageUrl}
          onChange={(url, mediaId) => {
            setCommunityImageUrl(url);
            setCoverMediaId(mediaId || null);
          }}
          onUploadingChange={setUploadingImage}
          purpose={MEDIA_PURPOSE.COMMUNITY_COVER}
          label="Imagen de la comunidad"
          chooseLabel="Elegir imagen"
          changeLabel="Cambiar imagen"
          disabled={creatingCommunity}
          tapToChoose
          emptyIcon={<FaMountain />}
        />

        <SectionCard>
          <Input
            id="community-name-input"
            label="Nombre de la comunidad"
            value={communityName}
            onChange={(event) => setCommunityName(event.target.value)}
            maxLength={80}
            disabled={creatingCommunity}
            placeholder="Ej.: Trail Girls Madrid"
            autoFocus
          />
          <Input
            as="textarea"
            id="community-description-input"
            label="Descripción (opcional)"
            value={communityDescription}
            onChange={(event) => setCommunityDescription(event.target.value)}
            maxLength={500}
            disabled={creatingCommunity}
            placeholder="Cuéntanos de qué va la comunidad…"
            rows="4"
          />
        </SectionCard>


        <FormError message={createCommunityError} />

        <Button type="submit" loading={creatingCommunity} disabled={uploadingImage}>
          Crear comunidad
        </Button>
      </form>
    </div>
  );
};

export default CommunityCreatorPanel;
