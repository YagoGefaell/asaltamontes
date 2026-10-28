import { useCallback, useEffect, useRef, useState } from "react";
import { FaCamera } from "react-icons/fa";
import Button from "../forms/Button.jsx";
import { useToast } from "./Toast.jsx";
import { uploadImage, validateImageFile } from "../../features/media/services/media.service.js";
import "./ImageUploader.css";

/**
 * Subida de una imagen (T17b): elegir archivo (en el móvil abre cámara o galería), vista previa local,
 * barra de progreso, errores en español y reintento. `onChange(url, mediaId)` recibe la URL de vista previa
 * (`urls.medium`) y el id de la media subida (lo que hay que enviar al backend), o ("", null) si se quita. `onUploadingChange` permite al formulario bloquear el envío mientras sube.
 * Con `tapToChoose` la propia imagen (grande y centrada) es el botón para elegirla, sin botón aparte (foto de perfil).
 * `emptyIcon` se pinta dentro del hueco cuando aún no hay imagen ni `fallbackSrc` (p. ej. el icono de grupo).
 */
export default function ImageUploader({
  value,
  onChange,
  purpose,
  label = "Foto",
  chooseLabel = "Elegir foto",
  changeLabel = "Cambiar foto",
  fallbackSrc,
  shape = "round",
  disabled = false,
  tapToChoose = false,
  emptyIcon = null,
  onUploadingChange,
}) {
  const toast = useToast();
  const inputRef = useRef(null);
  const abortRef = useRef(null);
  const [localPreview, setLocalPreview] = useState(null);
  const [progress, setProgress] = useState(0);
  const [uploading, setUploading] = useState(false);
  const [error, setError] = useState("");
  const [failedFile, setFailedFile] = useState(null);

  const replacePreview = useCallback((file) => {
    setLocalPreview((previous) => {
      if (previous) URL.revokeObjectURL?.(previous);
      return file && URL.createObjectURL ? URL.createObjectURL(file) : null;
    });
  }, []);

  useEffect(() => () => abortRef.current?.abort(), []);

  const setBusy = useCallback((busy) => {
    setUploading(busy);
    onUploadingChange?.(busy);
  }, [onUploadingChange]);

  const startUpload = useCallback(async (file) => {
    setError("");
    setFailedFile(null);
    replacePreview(file);
    setProgress(0);
    setBusy(true);
    const controller = new AbortController();
    abortRef.current = controller;
    try {
      const media = await uploadImage(file, purpose, { onProgress: setProgress, signal: controller.signal });
      onChange(media.urls.medium, media.id);
      replacePreview(null);
      toast.success("Imagen subida");
    } catch (uploadError) {
      if (uploadError.name === "AbortError") return;
      setError(uploadError.message);
      setFailedFile(file);
    } finally {
      setBusy(false);
    }
  }, [purpose, onChange, replacePreview, setBusy, toast]);

  const handleFile = (event) => {
    const file = event.target.files?.[0];
    event.target.value = "";
    if (!file) return;
    const invalid = validateImageFile(file);
    if (invalid) {
      setError(invalid);
      setFailedFile(null);
      return;
    }
    startUpload(file);
  };

  const remove = () => {
    replacePreview(null);
    setError("");
    setFailedFile(null);
    onChange("", null);
  };

  const shown = localPreview || value || fallbackSrc;
  const hasImage = Boolean(localPreview || value);

  const preview = shown ? (
    <img src={shown} alt={tapToChoose ? "" : label} className="image-uploader__preview" />
  ) : (
    <div className="image-uploader__preview image-uploader__preview--empty" aria-hidden="true">{emptyIcon}</div>
  );
  const choose = () => inputRef.current?.click();

  return (
    <div className={["image-uploader", `image-uploader--${shape}`, tapToChoose && "image-uploader--tap"].filter(Boolean).join(" ")}>
      {tapToChoose ? (
        <button
          type="button"
          className="image-uploader__tap"
          onClick={choose}
          disabled={disabled || uploading}
          aria-label={`${hasImage ? changeLabel : chooseLabel}: ${label.toLowerCase()}`}
        >
          {preview}
          <span className="image-uploader__tap-badge" aria-hidden="true"><FaCamera /></span>
        </button>
      ) : (
        preview
      )}
      <div className="image-uploader__controls">
        <input
          ref={inputRef}
          type="file"
          accept="image/*"
          data-testid="image-uploader-input"
          onChange={handleFile}
          disabled={disabled || uploading}
          hidden
        />
        <div className="image-uploader__actions">
          {!tapToChoose && (
            <Button variant="outline" size="small" onClick={choose} disabled={disabled || uploading}>
              {hasImage ? changeLabel : chooseLabel}
            </Button>
          )}
          {value && !uploading && (
            <Button variant="ghost" size="small" onClick={remove} disabled={disabled}>
              Quitar
            </Button>
          )}
        </div>
        {uploading && (
          <div
            className="image-uploader__progress"
            role="progressbar"
            aria-label="Subiendo imagen"
            aria-valuemin={0}
            aria-valuemax={100}
            aria-valuenow={progress}
          >
            <div className="image-uploader__bar" style={{ width: `${progress}%` }} />
          </div>
        )}
        {error && (
          <p className="image-uploader__error" role="alert">
            {error}
            {failedFile && (
              <>
                {" "}
                <button type="button" className="image-uploader__retry" onClick={() => startUpload(failedFile)}>
                  Reintentar
                </button>
              </>
            )}
          </p>
        )}
      </div>
    </div>
  );
}
