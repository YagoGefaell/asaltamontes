import { useEffect, useRef, useState } from "react";
import { FaCamera, FaTimes } from "react-icons/fa";
import Button from "../../../shared/forms/Button";
import { MEDIA_PURPOSE, uploadImage, validateImageFile } from "../../media/services/media.service";
import { POST_MEDIA_MAX } from "../services/posts.service";
import "./PostPhotosPicker.css";

/**
 * Up to four photos for a publication (T22b), uploaded one by one with the media flow (POST_IMAGE).
 * `photos` = [{ key, mediaId, url }]; `onChange(photos)` and `onUploadingChange(bool)` let the form
 * send the ids and block «Publicar» while something is still uploading.
 */
export default function PostPhotosPicker({ photos, onChange, onUploadingChange, disabled = false }) {
  const inputRef = useRef(null);
  const photosRef = useRef(photos);
  const pendingRef = useRef(0);
  const [pending, setPending] = useState(0);
  const [error, setError] = useState("");

  useEffect(() => { photosRef.current = photos; }, [photos]);
  useEffect(() => { onUploadingChange?.(pending > 0); }, [pending, onUploadingChange]);

  const upload = async (file) => {
    pendingRef.current += 1;
    setPending(pendingRef.current);
    try {
      const media = await uploadImage(file, MEDIA_PURPOSE.POST_IMAGE);
      const next = [...photosRef.current, { key: media.id, mediaId: media.id, url: media.urls.medium }];
      photosRef.current = next;
      onChange(next);
    } catch (uploadError) {
      setError(uploadError.message);
    } finally {
      pendingRef.current -= 1;
      setPending(pendingRef.current);
    }
  };

  const handleFiles = (event) => {
    const files = Array.from(event.target.files || []);
    event.target.value = "";
    setError("");
    const room = POST_MEDIA_MAX - photosRef.current.length - pendingRef.current;
    if (files.length > room) setError(`Máximo ${POST_MEDIA_MAX} fotos por publicación`);
    files.slice(0, Math.max(room, 0)).forEach((file) => {
      const invalid = validateImageFile(file);
      if (invalid) setError(invalid);
      else upload(file);
    });
  };

  const remove = (key) => {
    const next = photosRef.current.filter((photo) => photo.key !== key);
    photosRef.current = next;
    onChange(next);
    setError("");
  };

  const full = photos.length + pending >= POST_MEDIA_MAX;

  return (
    <div className="post-photos">
      {(photos.length > 0 || pending > 0) && (
        <ul className="post-photos__list">
          {photos.map((photo) => (
            <li key={photo.key} className="post-photos__item">
              <img src={photo.url} alt="" />
              <button type="button" className="post-photos__remove" onClick={() => remove(photo.key)} aria-label="Quitar foto" disabled={disabled}>
                <FaTimes aria-hidden="true" />
              </button>
            </li>
          ))}
          {Array.from({ length: pending }, (_, index) => (
            <li key={`pending-${index}`} className="post-photos__item post-photos__item--pending" role="progressbar" aria-label="Subiendo foto" />
          ))}
        </ul>
      )}
      <input ref={inputRef} type="file" accept="image/*" multiple hidden data-testid="post-photos-input" onChange={handleFiles} />
      <Button variant="outline" size="small" onClick={() => inputRef.current?.click()} disabled={disabled || full}>
        <FaCamera aria-hidden="true" /> {photos.length > 0 ? "Añadir más fotos" : "Añadir fotos"}
      </Button>
      {error && <p className="post-photos__error" role="alert">{error}</p>}
    </div>
  );
}
