import { useState } from "react";
import { Link } from "react-router-dom";
import { FaEllipsisH, FaHeart, FaRegComment, FaRegHeart } from "react-icons/fa";
import Avatar from "../../../shared/ui/Avatar";
import BottomSheet from "../../../shared/ui/BottomSheet";
import ConfirmSheet from "../../../shared/ui/ConfirmSheet";
import { communityPath } from "../../communities/utils/communityPath";
import { deletePostRequest, likePostRequest, unlikePostRequest } from "../services/posts.service";
import { formatAge } from "../../notifications/utils/notificationText";
import "./PostCard.css";

/**
 * Publication (T22b): author and community, text, photos, like and comments. «Eliminar» only when
 * the API says `canDelete`. In the list, the comment button opens the post; in `detail` it is only a counter.
 */
export default function PostCard({ post, detail = false, onDeleted }) {
  const [likedByMe, setLikedByMe] = useState(post.likedByMe);
  const [likeCount, setLikeCount] = useState(post.likeCount);
  const [likeError, setLikeError] = useState("");
  const [liking, setLiking] = useState(false);
  const [menuOpen, setMenuOpen] = useState(false);
  const [confirmOpen, setConfirmOpen] = useState(false);
  const [deleting, setDeleting] = useState(false);
  const [deleteError, setDeleteError] = useState("");

  const { author, community, media = [] } = post;
  const name = author.fullName || author.username;

  // The server answers with the state after the call, so a double tap cannot drift the counter
  const toggleLike = async () => {
    if (liking) return;
    setLiking(true);
    setLikeError("");
    try {
      const state = likedByMe ? await unlikePostRequest(post.id) : await likePostRequest(post.id);
      setLikedByMe(state.likedByMe);
      setLikeCount(state.likeCount);
    } catch (error) {
      setLikeError(error.message);
    } finally {
      setLiking(false);
    }
  };

  const confirmDelete = async () => {
    setDeleting(true);
    setDeleteError("");
    try {
      await deletePostRequest(post.id);
      setConfirmOpen(false);
      onDeleted?.(post);
    } catch (error) {
      setDeleteError(error.message);
    } finally {
      setDeleting(false);
    }
  };

  const commentLabel = `${post.commentCount} ${post.commentCount === 1 ? "comentario" : "comentarios"}`;
  const commentContent = (
    <>
      <FaRegComment aria-hidden="true" />
      <span>{post.commentCount}</span>
    </>
  );

  return (
    <article className="post-card" aria-label={`Publicación de ${name}`}>
      <header className="post-card__header">
        <Link to={`/users/${encodeURIComponent(author.username)}`} className="post-card__author" aria-label={`Perfil de ${name}`}>
          <Avatar src={author.profilePictureUrl} name={name} size="sm" />
        </Link>
        <div className="post-card__who">
          <Link to={`/users/${encodeURIComponent(author.username)}`} className="post-card__name">{name}</Link>
          <span className="post-card__meta">
            {community && (
              <>
                <Link to={communityPath(community)} className="post-card__community">{community.name}</Link>
                {" · "}
              </>
            )}
            {formatAge(post.createdAt)}
          </span>
        </div>
        {post.canDelete && (
          <button type="button" className="post-card__more" onClick={() => setMenuOpen(true)} aria-label="Más opciones" aria-haspopup="dialog">
            <FaEllipsisH aria-hidden="true" />
          </button>
        )}
      </header>

      <p className="post-card__body">{post.body}</p>

      {media.length > 0 && (
        <div className={`post-card__media post-card__media--${media.length}`}>
          {media.map((photo, index) => (
            <img key={photo.large || index} src={media.length === 1 ? photo.large : photo.medium} alt="" loading="lazy" />
          ))}
        </div>
      )}

      <footer className="post-card__actions">
        <button
          type="button"
          className={`post-card__action${likedByMe ? " post-card__action--liked" : ""}`}
          onClick={toggleLike}
          aria-pressed={likedByMe}
          aria-label={likedByMe ? "Quitar me gusta" : "Me gusta"}
        >
          {likedByMe ? <FaHeart aria-hidden="true" /> : <FaRegHeart aria-hidden="true" />}
          <span>{likeCount}</span>
        </button>
        {detail ? (
          <span className="post-card__action post-card__action--static" aria-label={commentLabel}>{commentContent}</span>
        ) : (
          <Link to={`/posts/${post.id}`} className="post-card__action" aria-label={commentLabel}>{commentContent}</Link>
        )}
        {likeError && <span className="post-card__error" role="alert">{likeError}</span>}
      </footer>

      <BottomSheet open={menuOpen} title="Publicación" onClose={() => setMenuOpen(false)}>
        <button
          type="button"
          className="post-card__sheet-action"
          onClick={() => { setMenuOpen(false); setDeleteError(""); setConfirmOpen(true); }}
        >
          Eliminar publicación
        </button>
      </BottomSheet>
      <ConfirmSheet
        open={confirmOpen}
        title="Eliminar publicación"
        message="Se eliminará la publicación con sus fotos y comentarios. No se puede deshacer."
        confirmLabel="Eliminar"
        destructive
        loading={deleting}
        error={deleteError}
        onConfirm={confirmDelete}
        onCancel={() => setConfirmOpen(false)}
      />
    </article>
  );
}
