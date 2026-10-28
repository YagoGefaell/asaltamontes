import { useState } from "react";
import { FaMountain } from "react-icons/fa";
import Button from "../../../shared/forms/Button";
import FormError from "../../../shared/forms/FormError";
import Input from "../../../shared/forms/Input";
import SectionCard from "../../../shared/ui/SectionCard";
import ImageUploader from "../../../shared/ui/ImageUploader";
import { useToast } from "../../../shared/ui/Toast";
import { MEDIA_PURPOSE } from "../../../features/media/services/media.service";
import { updateCommunityRequest } from "../../../features/communities/services/communities.service";
import { communityErrorMessage } from "../../../features/communities/utils/communityErrors";
import "./CommunityModeration.css";

const orNull = (text) => text.trim() || null;

/** Edit name, description, place, cover and (owner only) visibility. Only the changed fields are sent (D-020). */
export default function CommunityEditForm({ community, onSaved }) {
  const toast = useToast();
  const isOwner = community.role === "OWNER";
  const [name, setName] = useState(community.name || "");
  const [description, setDescription] = useState(community.description || "");
  const [city, setCity] = useState(community.city || "");
  const [province, setProvince] = useState(community.province || "");
  const [visibility, setVisibility] = useState(community.visibility || "PUBLIC");
  const [imageUrl, setImageUrl] = useState(community.imageUrl || "");
  const [coverMediaId, setCoverMediaId] = useState(null);
  const [uploading, setUploading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState(null);

  const handleSubmit = async (event) => {
    event.preventDefault();
    if (saving || uploading) return;
    if (!name.trim()) {
      setError("El nombre de la comunidad es obligatorio.");
      return;
    }
    const changes = {};
    if (name.trim() !== community.name) changes.name = name.trim();
    if (orNull(description) !== (community.description || null)) changes.description = orNull(description);
    if (orNull(city) !== (community.city || null)) changes.city = orNull(city);
    if (orNull(province) !== (community.province || null)) changes.province = orNull(province);
    if (isOwner && visibility !== community.visibility) changes.visibility = visibility;
    if (coverMediaId) changes.coverMediaId = coverMediaId;
    if (Object.keys(changes).length === 0) {
      toast.info("No hay cambios que guardar");
      return;
    }
    setSaving(true);
    setError(null);
    try {
      const updated = await updateCommunityRequest(community.id, changes);
      setCoverMediaId(null);
      onSaved?.(updated);
      toast.success("Comunidad actualizada");
    } catch (err) {
      setError(err.status === 409 ? "Ya existe otra comunidad con ese nombre." : communityErrorMessage(err));
    } finally {
      setSaving(false);
    }
  };

  return (
    <form className="moderation-form" onSubmit={handleSubmit}>
      <ImageUploader
        value={imageUrl}
        onChange={(url, mediaId) => {
          setImageUrl(url);
          setCoverMediaId(mediaId || null);
        }}
        onUploadingChange={setUploading}
        purpose={MEDIA_PURPOSE.COMMUNITY_COVER}
        label="Imagen de la comunidad"
        chooseLabel="Elegir imagen"
        changeLabel="Cambiar imagen"
        tapToChoose
        emptyIcon={<FaMountain />}
        disabled={saving}
      />

      <SectionCard title="Datos de la comunidad">
        <Input
          id="community-edit-name"
          label="Nombre"
          value={name}
          maxLength={80}
          onChange={(e) => setName(e.target.value)}
          disabled={saving}
        />
        <Input
          as="textarea"
          id="community-edit-description"
          label="Descripción"
          rows="4"
          value={description}
          maxLength={500}
          onChange={(e) => setDescription(e.target.value)}
          disabled={saving}
        />
        <div className="moderation-row-fields">
          <Input id="community-edit-city" label="Ciudad" value={city} maxLength={80}
            onChange={(e) => setCity(e.target.value)} disabled={saving} />
          <Input id="community-edit-province" label="Provincia" value={province} maxLength={80}
            onChange={(e) => setProvince(e.target.value)} disabled={saving} />
        </div>
      </SectionCard>

      <SectionCard title="Visibilidad">
        <Input
          as="select"
          id="community-edit-visibility"
          label="Quién puede unirse"
          value={visibility}
          onChange={(e) => setVisibility(e.target.value)}
          disabled={saving || !isOwner}
        >
          <option value="PUBLIC">Pública: cualquiera puede unirse</option>
          <option value="PRIVATE">Privada: hay que solicitar el acceso</option>
        </Input>
        {!isOwner && <p className="moderation-hint">Solo la propietaria puede cambiar la visibilidad.</p>}
        {isOwner && visibility === "PRIVATE" && (
          <p className="moderation-hint">
            Quien no sea miembro solo verá el nombre, la imagen, la descripción y el número de miembros.
          </p>
        )}
      </SectionCard>

      <FormError message={error} />
      <Button type="submit" loading={saving} disabled={uploading}>Guardar cambios</Button>
    </form>
  );
}
