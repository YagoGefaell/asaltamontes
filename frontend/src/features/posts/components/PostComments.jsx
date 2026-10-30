import { useCallback, useState } from "react";
import { Link } from "react-router-dom";
import { FaFlag, FaPaperPlane, FaTimes } from "react-icons/fa";
import Button from "../../../shared/forms/Button";
import Avatar from "../../../shared/ui/Avatar";
import ReportSheet from "../../reports/components/ReportSheet";
import { useAuth } from "../../auth/hooks/useAuth";
import ConfirmSheet from "../../../shared/ui/ConfirmSheet";
import Skeleton from "../../../shared/ui/Skeleton";
import { useCursorList } from "../../communities/hooks/useCursorList";
import { COMMENT_BODY_MAX, createCommentRequest, deleteCommentRequest, getCommentsRequest } from "../services/posts.service";
import { formatAge } from "../../notifications/utils/notificationText";
import "./PostComments.css";

function sendError(error) {
  if (error.status === 429) return "Estás comentando muy rápido. Inténtalo de nuevo en un rato.";
  if (error.status === 403) return "No puedes comentar en esta publicación.";
  return error.message;
}

/**
 * Comments of a publication (T22b): oldest first, «Cargar más» with the `after` cursor, the field to
 * write at the end. «Eliminar» follows each comment's `canDelete` (hers, moderation or the owner of the profile post, D-027).
 * `onCountChange(delta)` keeps the counter of the card in sync.
 */
export default function PostComments({ postId, onCountChange }) {
  const fetchPage = useCallback((after) => getCommentsRequest(postId, after), [postId]);
  const { items, setItems, loading, loadingMore, error, hasMore, loadMore, reload, removeItem } = useCursorList(fetchPage);
  const { currentUser } = useAuth();
  const [toReport, setToReport] = useState(null);
  const [text, setText] = useState("");
  const [sending, setSending] = useState(false);
  const [formError, setFormError] = useState("");
  const [toDelete, setToDelete] = useState(null);
  const [deleting, setDeleting] = useState(false);
  const [deleteError, setDeleteError] = useState("");

  const send = async (event) => {
    event.preventDefault();
    const body = text.trim();
    if (!body || sending) return;
    setSending(true);
    setFormError("");
    try {
      const created = await createCommentRequest(postId, body);
      setItems((prev) => (prev.some((c) => c.id === created.id) ? prev : [...prev, created]));
      setText("");
      onCountChange?.(1);
    } catch (requestError) {
      setFormError(sendError(requestError));
    } finally {
      setSending(false);
    }
  };

  const confirmDelete = async () => {
    setDeleting(true);
    setDeleteError("");
    try {
      await deleteCommentRequest(postId, toDelete.id);
      removeItem((comment) => comment.id === toDelete.id);
      onCountChange?.(-1);
      setToDelete(null);
    } catch (requestError) {
      setDeleteError(requestError.message);
    } finally {
      setDeleting(false);
    }
  };

  return (
    <section className="post-comments" aria-label="Comentarios">
      <div className="post-comments__scroll">
      {loading && (
        <div className="post-comments__skeletons" data-testid="comments-skeleton">
          <Skeleton height="48px" />
          <Skeleton height="48px" />
        </div>
      )}
      {!loading && error && (
        <div className="post-comments__error" role="alert">
          <p>{error.message || "No se han podido cargar los comentarios"}</p>
          <Button variant="outline" size="small" onClick={reload}>Reintentar</Button>
        </div>
      )}
      {!loading && !error && items.length === 0 && <p className="post-comments__empty">Sé la primera en comentar.</p>}
      {!loading && !error && (
        <ul className="post-comments__list">
          {items.map((comment) => {
            const name = comment.author.fullName || comment.author.username;
            return (
              <li key={comment.id} className="post-comment">
                <Link to={`/users/${encodeURIComponent(comment.author.username)}`} aria-label={`Perfil de ${name}`}>
                  <Avatar src={comment.author.profilePictureUrl} name={name} size="sm" />
                </Link>
                <div className="post-comment__content">
                  <p className="post-comment__text">
                    <Link to={`/users/${encodeURIComponent(comment.author.username)}`} className="post-comment__name">{name}</Link>{" "}
                    {comment.body}
                  </p>
                  <span className="post-comment__age">{formatAge(comment.createdAt)}</span>
                </div>
                {Boolean(currentUser) && comment.author.username !== currentUser.username && (
                  <button type="button" className="post-comment__delete" onClick={() => setToReport(comment)} aria-label="Denunciar comentario" aria-haspopup="dialog">
                    <FaFlag aria-hidden="true" />
                  </button>
                )}
                {comment.canDelete && (
                  <button type="button" className="post-comment__delete" onClick={() => { setDeleteError(""); setToDelete(comment); }} aria-label="Eliminar comentario">
                    <FaTimes aria-hidden="true" />
                  </button>
                )}
              </li>
            );
          })}
        </ul>
      )}
      {!loading && !error && hasMore && (
        <Button variant="ghost" className="post-comments__more" onClick={loadMore} loading={loadingMore}>Cargar más comentarios</Button>
      )}
      </div>

      <form className="post-comments__form" onSubmit={send}>
        <input
          type="text"
          className="post-comments__input"
          aria-label="Escribe un comentario"
          placeholder="Escribe un comentario"
          maxLength={COMMENT_BODY_MAX}
          value={text}
          onChange={(e) => setText(e.target.value)}
        />
        <button type="submit" className="post-comments__send" disabled={sending || !text.trim()} aria-label="Enviar comentario">
          <FaPaperPlane aria-hidden="true" />
        </button>
      </form>
      {formError && <p className="post-comments__form-error" role="alert">{formError}</p>}

      <ReportSheet open={Boolean(toReport)} title="Denunciar comentario" targetType="COMMENT" target={toReport?.id} onClose={() => setToReport(null)} />
      <ConfirmSheet
        open={Boolean(toDelete)}
        title="Eliminar comentario"
        message="Se eliminará este comentario. No se puede deshacer."
        confirmLabel="Eliminar"
        destructive
        loading={deleting}
        error={deleteError}
        onConfirm={confirmDelete}
        onCancel={() => setToDelete(null)}
      />
    </section>
  );
}
