import { useEffect, useState } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";
import Button from "../../shared/forms/Button";
import FormError from "../../shared/forms/FormError";
import Input from "../../shared/forms/Input";
import PageHeader from "../../shared/ui/PageHeader";
import PostPhotosPicker from "../../features/posts/components/PostPhotosPicker";
import { POST_BODY_MAX, createPostRequest } from "../../features/posts/services/posts.service";
import { getOrganizableCommunitiesRequest } from "../../features/communities/services/communities.service";
import "./PostCreate.css";

const PROFILE = "";

function submitError(error) {
  if (error.status === 429) {
    const minutes = error.retryAfter ? Math.ceil(error.retryAfter / 60) : null;
    return minutes ? `Has publicado mucho hoy. Inténtalo de nuevo en ${minutes} min.` : "Has publicado mucho hoy. Inténtalo más tarde.";
  }
  if (error.status === 403) return "Solo la propietaria y las moderadoras pueden publicar en esta comunidad.";
  return error.message;
}

/** Create a publication (T22b) on its own screen: where (her profile or a community she moderates), text and up to 4 photos. */
export default function PostCreate() {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const [communities, setCommunities] = useState([]);
  const [destination, setDestination] = useState(searchParams.get("community") ?? PROFILE);
  const [body, setBody] = useState("");
  const [photos, setPhotos] = useState([]);
  const [uploading, setUploading] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState("");
  const [bodyError, setBodyError] = useState("");

  // Only owner and moderators publish in a community (D-025)
  useEffect(() => {
    let active = true;
    getOrganizableCommunitiesRequest()
      .then((list) => active && setCommunities(list))
      .catch((loadError) => active && setError(loadError.message));
    return () => { active = false; };
  }, []);

  const submit = async (event) => {
    event.preventDefault();
    const text = body.trim();
    if (!text) {
      setBodyError("Escribe algo para publicar");
      return;
    }
    setBodyError("");
    setError("");
    setSubmitting(true);
    try {
      const post = await createPostRequest({
        communitySlug: destination || null,
        body: text,
        mediaIds: photos.map((photo) => photo.mediaId),
      });
      navigate(`/posts/${post.id}`, { replace: true });
    } catch (requestError) {
      if (requestError.errors?.body) setBodyError(requestError.errors.body);
      else setError(submitError(requestError));
      setSubmitting(false);
    }
  };

  return (
    <div className="post-create">
      <PageHeader title="Nueva publicación" onBack={() => navigate(-1)} backLabel="Volver" />
      <form className="post-create__form" onSubmit={submit} noValidate>
        <Input as="select" id="post-destination" label="Publicar en" aria-label="Publicar en" value={destination} onChange={(e) => setDestination(e.target.value)}>
          <option value={PROFILE}>Mi perfil</option>
          {communities.map((community) => (
            <option key={community.id} value={community.slug}>{community.name}</option>
          ))}
        </Input>
        <div>
          <Input
            as="textarea"
            id="post-body"
            label="Texto"
            aria-label="Texto"
            rows={6}
            maxLength={POST_BODY_MAX}
            placeholder="¿Qué quieres compartir?"
            value={body}
            error={bodyError}
            onChange={(e) => setBody(e.target.value)}
          />
          <p className="post-create__counter" aria-live="polite">{body.length}/{POST_BODY_MAX}</p>
        </div>
        <PostPhotosPicker photos={photos} onChange={setPhotos} onUploadingChange={setUploading} disabled={submitting} />
        <FormError message={error} />
        <Button type="submit" variant="primary" loading={submitting} disabled={submitting || uploading}>Publicar</Button>
      </form>
    </div>
  );
}
