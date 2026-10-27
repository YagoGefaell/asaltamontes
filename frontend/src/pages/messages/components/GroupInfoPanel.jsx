import { useCallback, useEffect, useState } from "react";
import Button from "../../../shared/forms/Button.jsx";
import ImageUploader from "../../../shared/ui/ImageUploader.jsx";
import Modal from "../../../shared/ui/Modal.jsx";
import ToggleSwitch from "../../../shared/ui/ToggleSwitch.jsx";
import Skeleton from "../../../shared/ui/Skeleton.jsx";
import { MEDIA_PURPOSE } from "../../../features/media/services/media.service.js";
import { getUsersByUsername } from "../../../features/users/services/users.service.js";
import {
  addParticipantsRequest,
  changeParticipantRoleRequest,
  getGroupDetailsRequest,
  leaveGroupRequest,
  removeParticipantRequest,
  updateGroupRequest,
} from "../../../features/messages/services/messages.service.js";

const PAGE_SIZE = 20;

/**
 * "Info del grupo" (T12): participants (paged), and for admins: add, expel, change role, rename, image and the
 * history option. Hiding controls is only courtesy: the server decides who may do what.
 */
const GroupInfoPanel = ({ chat, currentUsername, onClose, onGroupChanged, onLeft }) => {
  const conversationId = chat.id;
  const [details, setDetails] = useState(null);
  const [participants, setParticipants] = useState([]);
  const [page, setPage] = useState(0);
  const [status, setStatus] = useState("loading");
  const [loadingMore, setLoadingMore] = useState(false);
  const [busy, setBusy] = useState(false);
  const [actionError, setActionError] = useState(null);
  const [name, setName] = useState(chat.name || "");
  const [query, setQuery] = useState("");
  const [results, setResults] = useState([]);
  const [searching, setSearching] = useState(false);
  const [confirmLeave, setConfirmLeave] = useState(false);

  const isAdmin = details?.myRole === "ADMIN";
  const managed = Boolean(details?.managed);
  const total = details?.participants?.totalElements ?? participants.length;
  const hasMore = participants.length < total;

  const applyDetails = useCallback((next) => {
    setDetails(next);
    setParticipants(next.participants?.content ?? []);
    setPage(0);
    setName(next.name || "");
    onGroupChanged?.(next);
  }, [onGroupChanged]);

  const load = useCallback(async () => {
    setStatus("loading");
    try {
      applyDetails(await getGroupDetailsRequest(conversationId, { page: 0, size: PAGE_SIZE }));
      setStatus("loaded");
    } catch {
      setStatus("error");
    }
  }, [conversationId, applyDetails]);

  useEffect(() => {
    load();
  }, [load]);

  const loadMore = async () => {
    if (loadingMore || !hasMore) return;
    setLoadingMore(true);
    try {
      const next = await getGroupDetailsRequest(conversationId, { page: page + 1, size: PAGE_SIZE });
      setParticipants((prev) => {
        const known = new Set(prev.map((participant) => participant.username));
        return [...prev, ...(next.participants?.content ?? []).filter((participant) => !known.has(participant.username))];
      });
      setPage(page + 1);
    } catch (error) {
      setActionError(error.message);
    } finally {
      setLoadingMore(false);
    }
  };

  // Runs a change and shows the server's reason if it is refused
  const run = async (action) => {
    if (busy) return;
    setBusy(true);
    setActionError(null);
    try {
      await action();
    } catch (error) {
      setActionError(error.message || "No se pudo completar la acción");
    } finally {
      setBusy(false);
    }
  };

  useEffect(() => {
    const text = query.trim();
    if (!isAdmin || text.length < 2) {
      setResults([]);
      return undefined;
    }
    let cancelled = false;
    setSearching(true);
    const timeoutId = setTimeout(async () => {
      try {
        const users = await getUsersByUsername(text);
        if (cancelled) return;
        const inGroup = new Set(participants.map((participant) => participant.username.toLowerCase()));
        setResults((Array.isArray(users) ? users : []).filter((user) => {
          const username = String(user?.username || "").toLowerCase();
          return username && username !== String(currentUsername || "").toLowerCase() && !inGroup.has(username);
        }));
      } catch {
        if (!cancelled) setResults([]);
      } finally {
        if (!cancelled) setSearching(false);
      }
    }, 300);
    return () => {
      cancelled = true;
      clearTimeout(timeoutId);
    };
  }, [query, isAdmin, participants, currentUsername]);

  const addMember = (user) => run(async () => {
    applyDetails(await addParticipantsRequest(conversationId, [user.username]));
    setQuery("");
    setResults([]);
  });

  const expel = (participant) => run(async () => {
    await removeParticipantRequest(conversationId, participant.username);
    await load();
  });

  const toggleRole = (participant) => run(async () => {
    await changeParticipantRoleRequest(conversationId, participant.username, participant.role === "ADMIN" ? "MEMBER" : "ADMIN");
    await load();
  });

  const rename = (event) => {
    event.preventDefault();
    const trimmed = name.trim();
    if (!trimmed || trimmed === details.name) return;
    run(async () => applyDetails(await updateGroupRequest(conversationId, { groupName: trimmed })));
  };

  const changeImage = (_url, mediaId) => run(async () => {
    // A media id sets the image; null (the uploader's "remove") clears it (D-020)
    applyDetails(await updateGroupRequest(conversationId, { groupMediaId: mediaId || null }));
  });

  const changeHistory = (event) => run(async () => {
    applyDetails(await updateGroupRequest(conversationId, { historyVisibleToNew: event.target.checked }));
  });

  const leave = () => run(async () => {
    await leaveGroupRequest(conversationId);
    setConfirmLeave(false);
    onLeft?.();
  });

  return (
    <div className="group-creator-page group-info" aria-label="Info del grupo">
      <div className="messages-header">
        <Button variant="ghost" className="chat-back-btn" onClick={onClose} aria-label="Volver al chat">
          <svg className="chat-back-icon" viewBox="0 0 24 24" fill="none" aria-hidden="true">
            <path d="M15 18L9 12L15 6" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
          </svg>
        </Button>
        <h2 className="messages-title">Info del grupo</h2>
      </div>

      {status === "loading" && (
        <div className="group-creator-form" role="status" aria-label="Cargando el grupo">
          <Skeleton width="60%" height="1.5rem" />
          <Skeleton width="100%" height="3rem" />
          <Skeleton width="100%" height="3rem" />
        </div>
      )}

      {status === "error" && (
        <div className="group-creator-form">
          <p className="group-creator-error" role="alert">No se pudo cargar el grupo.</p>
          <Button variant="secondary" size="small" onClick={load}>Reintentar</Button>
        </div>
      )}

      {status === "loaded" && details && (
        <div className="group-creator-form">
          <h3 className="group-info-name">{details.name}</h3>
          <p className="group-creator-hint">{details.participantCount} participantes</p>
          {managed && (
            <p className="group-creator-hint">Este grupo se gestiona automáticamente: no se pueden añadir ni expulsar participantes a mano.</p>
          )}

          {isAdmin && (
            <>
              <form className="group-info-rename" onSubmit={rename}>
                <label className="group-creator-label" htmlFor="group-rename-input">Nombre del grupo</label>
                <input
                  id="group-rename-input"
                  type="text"
                  className="group-creator-input"
                  value={name}
                  onChange={(event) => setName(event.target.value)}
                  maxLength={80}
                  disabled={busy}
                />
                <Button type="submit" size="small" disabled={busy || !name.trim() || name.trim() === details.name}>
                  Guardar nombre
                </Button>
              </form>

              <span className="group-creator-label">Imagen del grupo</span>
              <ImageUploader
                value={details.imageUrl || ""}
                onChange={changeImage}
                purpose={MEDIA_PURPOSE.GROUP_IMAGE}
                label="Imagen del grupo"
                chooseLabel="Elegir imagen"
                changeLabel="Cambiar imagen"
                disabled={busy}
              />

              {!managed && (
                <ToggleSwitch
                  checked={Boolean(details.historyVisibleToNew)}
                  onChange={changeHistory}
                  label="Las nuevas participantes ven los mensajes anteriores"
                />
              )}

              {!managed && (
                <>
                  <label className="group-creator-label" htmlFor="group-add-input">Añadir participantes</label>
                  <input
                    id="group-add-input"
                    type="text"
                    className="group-creator-input"
                    value={query}
                    onChange={(event) => setQuery(event.target.value)}
                    autoComplete="off"
                    disabled={busy}
                  />
                  {searching && <p className="group-creator-hint" role="status">Buscando usuarias...</p>}
                  {results.length > 0 && (
                    <div className="group-search-results">
                      {results.map((user) => (
                        <button
                          key={user.username}
                          type="button"
                          className="group-search-result-item"
                          onClick={() => addMember(user)}
                          disabled={busy}
                        >
                          <span className="group-search-avatar" aria-hidden="true">
                            {String(user.username).charAt(0).toUpperCase()}
                          </span>
                          <span className="group-search-main">{user.fullName || user.username}</span>
                          <span className="group-search-username">@{user.username}</span>
                        </button>
                      ))}
                    </div>
                  )}
                </>
              )}
            </>
          )}

          {actionError && <p className="group-creator-error" role="alert">{actionError}</p>}

          <h3 className="group-info-subtitle">Participantes</h3>
          <ul className="group-info-members">
            {participants.map((participant) => {
              const isMe = participant.username === currentUsername;
              return (
                <li key={participant.username} className="group-info-member">
                  <span className="group-search-avatar" aria-hidden="true">
                    {participant.profilePictureUrl ? (
                      <img src={participant.profilePictureUrl} alt="" className="group-search-avatar-image" />
                    ) : (
                      String(participant.username).charAt(0).toUpperCase()
                    )}
                  </span>
                  <span className="group-info-member-name">
                    {participant.fullName || participant.username}
                    <span className="group-search-username"> @{participant.username}{isMe ? " (tú)" : ""}</span>
                  </span>
                  {participant.role === "ADMIN" && <span className="group-info-badge">Administradora</span>}
                  {isAdmin && !managed && !isMe && (
                    <span className="group-info-member-actions">
                      <Button
                        variant="ghost"
                        size="small"
                        onClick={() => toggleRole(participant)}
                        disabled={busy}
                        aria-label={`${participant.role === "ADMIN" ? "Quitar rol de administradora a" : "Hacer administradora a"} ${participant.username}`}
                      >
                        {participant.role === "ADMIN" ? "Quitar admin" : "Hacer admin"}
                      </Button>
                      <Button
                        variant="ghost"
                        size="small"
                        onClick={() => expel(participant)}
                        disabled={busy}
                        aria-label={`Expulsar a ${participant.username}`}
                      >
                        Expulsar
                      </Button>
                    </span>
                  )}
                </li>
              );
            })}
          </ul>
          {hasMore && (
            <Button variant="secondary" size="small" onClick={loadMore} disabled={loadingMore}>
              {loadingMore ? "Cargando…" : "Cargar más"}
            </Button>
          )}

          <Button variant="secondary" className="group-info-leave" onClick={() => setConfirmLeave(true)} disabled={busy}>
            Salir del grupo
          </Button>
        </div>
      )}

      <Modal
        open={confirmLeave}
        title="¿Salir del grupo?"
        onClose={() => setConfirmLeave(false)}
        footer={(
          <>
            <Button variant="secondary" onClick={() => setConfirmLeave(false)} disabled={busy}>Cancelar</Button>
            <Button onClick={leave} disabled={busy}>Salir</Button>
          </>
        )}
      >
        <p>Dejarás de recibir los mensajes de este grupo y no podrás volver a leer el historial salvo que te vuelvan a añadir.</p>
      </Modal>
    </div>
  );
};

export default GroupInfoPanel;
