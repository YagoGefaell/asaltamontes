import { useCallback, useEffect, useRef, useState } from "react";
import { FaCheck, FaChevronRight, FaPen, FaPlus, FaTimes, FaUsers } from "react-icons/fa";
import Button from "../../../shared/forms/Button.jsx";
import Avatar from "../../../shared/ui/Avatar.jsx";
import BottomSheet from "../../../shared/ui/BottomSheet.jsx";
import ConfirmSheet from "../../../shared/ui/ConfirmSheet.jsx";
import ImageUploader from "../../../shared/ui/ImageUploader.jsx";
import PageHeader from "../../../shared/ui/PageHeader.jsx";
import SearchField from "../../../shared/ui/SearchField.jsx";
import Skeleton from "../../../shared/ui/Skeleton.jsx";
import ToggleSwitch from "../../../shared/ui/ToggleSwitch.jsx";
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
import "./GroupInfoPanel.css";

const PAGE_SIZE = 20;

const ORIGIN_LABEL = { COMMUNITY: "Chat de la comunidad", EVENT: "Chat de la quedada" };

/**
 * "Info del grupo" (T12, T29r): foto y nombre arriba (editables en el sitio por las administradoras), ajustes,
 * participantes (las acciones salen en una hoja inferior al tocar la fila) y salir. Ocultar controles es solo
 * cortesía: el servidor decide quién puede hacer qué.
 */
const GroupInfoPanel = ({ chat, currentUsername, onClose, onGroupChanged, onLeft, onOpenProfile, onSendMessage, onOpenOrigin }) => {
  const conversationId = chat.id;
  const [details, setDetails] = useState(null);
  const [participants, setParticipants] = useState([]);
  const [page, setPage] = useState(0);
  const [status, setStatus] = useState("loading");
  const [loadingMore, setLoadingMore] = useState(false);
  const [busy, setBusy] = useState(false);
  const [actionError, setActionError] = useState(null);
  const [editingName, setEditingName] = useState(false);
  const [name, setName] = useState(chat.name || "");
  const [adding, setAdding] = useState(false);
  const [query, setQuery] = useState("");
  const [results, setResults] = useState([]);
  const [searching, setSearching] = useState(false);
  const [selected, setSelected] = useState(null);
  const [toExpel, setToExpel] = useState(null);
  const [confirmLeave, setConfirmLeave] = useState(false);
  const nameInputRef = useRef(null);

  const isAdmin = details?.myRole === "ADMIN";
  const managed = Boolean(details?.managed);
  const origin = details?.origin;
  const total = details?.participants?.totalElements ?? participants.length;
  const hasMore = participants.length < total;
  const sheetOpen = Boolean(selected) || Boolean(toExpel) || confirmLeave;

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

  useEffect(() => {
    if (editingName) nameInputRef.current?.focus();
  }, [editingName]);

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

  // Runs a change and shows the server's reason if it is refused; resolves to whether it worked
  const run = async (action) => {
    if (busy) return false;
    setBusy(true);
    setActionError(null);
    try {
      await action();
      return true;
    } catch (error) {
      setActionError(error.message || "No se pudo completar la acción");
      return false;
    } finally {
      setBusy(false);
    }
  };

  // Reloads the group without going back to the skeleton
  const refresh = async () => applyDetails(await getGroupDetailsRequest(conversationId, { page: 0, size: PAGE_SIZE }));

  useEffect(() => {
    const text = query.trim();
    if (!adding || text.length < 2) {
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
  }, [query, adding, participants, currentUsername]);

  const closeAdding = () => {
    setAdding(false);
    setQuery("");
    setResults([]);
    setActionError(null);
  };

  const addMember = (user) => run(async () => {
    applyDetails(await addParticipantsRequest(conversationId, [user.username]));
    setQuery("");
    setResults([]);
  });

  const expel = async () => {
    const participant = toExpel;
    if (!participant) return;
    const done = await run(async () => {
      await removeParticipantRequest(conversationId, participant.username);
      await refresh();
    });
    if (done) {
      setToExpel(null);
      setSelected(null);
    }
  };

  const toggleRole = async (participant) => {
    const done = await run(async () => {
      await changeParticipantRoleRequest(conversationId, participant.username, participant.role === "ADMIN" ? "MEMBER" : "ADMIN");
      await refresh();
    });
    if (done) setSelected(null);
  };

  const startEditingName = () => {
    setActionError(null);
    setName(details.name || "");
    setEditingName(true);
  };

  const cancelEditingName = () => {
    setName(details.name || "");
    setEditingName(false);
  };

  const saveName = async (event) => {
    event.preventDefault();
    const trimmed = name.trim();
    if (!trimmed || trimmed === details.name) {
      cancelEditingName();
      return;
    }
    const done = await run(async () => applyDetails(await updateGroupRequest(conversationId, { groupName: trimmed })));
    if (done) setEditingName(false);
  };

  const changeImage = (_url, mediaId) => run(async () => {
    // A media id sets the image; null (the uploader's "remove") clears it (D-020)
    applyDetails(await updateGroupRequest(conversationId, { groupMediaId: mediaId || null }));
  });

  const changeHistory = (event) => run(async () => {
    applyDetails(await updateGroupRequest(conversationId, { historyVisibleToNew: event.target.checked }));
  });

  const leave = async () => {
    const done = await run(async () => {
      await leaveGroupRequest(conversationId);
    });
    if (done) {
      setConfirmLeave(false);
      onLeft?.();
    }
  };

  const openActions = (participant) => {
    setActionError(null);
    setSelected(participant);
  };

  const closeActions = () => {
    if (busy) return;
    setSelected(null);
    setActionError(null);
  };

  const pick = (callback) => {
    const user = selected;
    setSelected(null);
    callback?.(user);
  };

  if (adding) {
    return (
      <div className="group-creator-page group-info" aria-label="Añadir participantes">
        <PageHeader title="Añadir participantes" onBack={closeAdding} backLabel="Volver a la info del grupo" />
        <div className="group-info__body">
          <SearchField label="Buscar usuarias" value={query} onChange={setQuery} onClear={() => setQuery("")} />
          {searching && <p className="group-info__hint" role="status">Buscando usuarias...</p>}
          {actionError && <p className="group-info__error" role="alert">{actionError}</p>}
          <ul className="group-info__list">
            {results.map((user) => (
              <li key={user.username}>
                <button type="button" className="group-info__row" onClick={() => addMember(user)} disabled={busy}>
                  <Avatar src={user.profilePictureUrl} name={user.fullName || user.username} size="md" />
                  <span className="group-info__text">
                    <span className="group-info__name">{user.fullName || user.username}</span>
                    <span className="group-info__username">@{user.username}</span>
                  </span>
                </button>
              </li>
            ))}
          </ul>
        </div>
      </div>
    );
  }

  return (
    <div className="group-creator-page group-info" aria-label="Info del grupo">
      <PageHeader title="Info del grupo" onBack={onClose} backLabel="Volver al chat" />

      {status === "loading" && (
        <div className="group-info__body group-info__body--loading" role="status" aria-label="Cargando el grupo">
          <Skeleton width="112px" height="112px" circle />
          <Skeleton width="60%" height="1.5rem" />
          <Skeleton width="100%" height="3rem" />
          <Skeleton width="100%" height="3rem" />
        </div>
      )}

      {status === "error" && (
        <div className="group-info__body">
          <p className="group-info__error" role="alert">No se pudo cargar el grupo.</p>
          <Button variant="secondary" size="small" onClick={load}>Reintentar</Button>
        </div>
      )}

      {status === "loaded" && details && (
        <div className="group-info__body">
          <section className="group-info__hero">
            {isAdmin ? (
              <ImageUploader
                value={details.imageUrl || ""}
                onChange={changeImage}
                purpose={MEDIA_PURPOSE.GROUP_IMAGE}
                label="Imagen del grupo"
                chooseLabel="Elegir imagen"
                changeLabel="Cambiar imagen"
                disabled={busy}
                tapToChoose
                emptyIcon={<FaUsers />}
              />
            ) : (
              <Avatar src={details.imageUrl} name={details.name} size={112} />
            )}

            {editingName ? (
              <form className="group-info__rename" onSubmit={saveName}>
                <input
                  ref={nameInputRef}
                  type="text"
                  className="group-info__rename-input"
                  aria-label="Nombre del grupo"
                  value={name}
                  onChange={(event) => setName(event.target.value)}
                  onKeyDown={(event) => event.key === "Escape" && cancelEditingName()}
                  maxLength={80}
                  disabled={busy}
                />
                <button type="submit" className="group-info__icon-button" aria-label="Guardar nombre" disabled={busy || !name.trim()}>
                  <FaCheck aria-hidden="true" />
                </button>
                <button type="button" className="group-info__icon-button" aria-label="Cancelar" onClick={cancelEditingName} disabled={busy}>
                  <FaTimes aria-hidden="true" />
                </button>
              </form>
            ) : (
              <div className="group-info__title">
                <h2 className="group-info__group-name">{details.name}</h2>
                {isAdmin && (
                  <button type="button" className="group-info__icon-button" aria-label="Editar nombre del grupo" onClick={startEditingName}>
                    <FaPen aria-hidden="true" />
                  </button>
                )}
              </div>
            )}
            <p className="group-info__hint">
              Grupo · {details.participantCount} {details.participantCount === 1 ? "participante" : "participantes"}
            </p>
          </section>

          {actionError && !sheetOpen && <p className="group-info__error" role="alert">{actionError}</p>}

          {managed && origin && (
            <button type="button" className="group-info__row group-info__row--card" onClick={() => onOpenOrigin?.(origin)}>
              <span className="group-info__text">
                <span className="group-info__name">{ORIGIN_LABEL[origin.type] || "Chat de"} {origin.name}</span>
              </span>
              <FaChevronRight className="group-info__chevron" aria-hidden="true" />
            </button>
          )}

          {isAdmin && !managed && (
            <div className="group-info__setting">
              <ToggleSwitch
                checked={Boolean(details.historyVisibleToNew)}
                onChange={changeHistory}
                label="Las nuevas participantes ven los mensajes anteriores"
              />
            </div>
          )}

          <h3 className="group-info__section">Participantes</h3>
          <ul className="group-info__list">
            {isAdmin && !managed && (
              <li>
                <button type="button" className="group-info__row" onClick={() => setAdding(true)} disabled={busy}>
                  <span className="group-info__add-icon" aria-hidden="true"><FaPlus /></span>
                  <span className="group-info__name">Añadir participantes</span>
                </button>
              </li>
            )}
            {participants.map((participant) => {
              const isMe = participant.username === currentUsername;
              const content = (
                <>
                  <Avatar src={participant.profilePictureUrl} name={participant.fullName || participant.username} size="md" />
                  <span className="group-info__text">
                    <span className="group-info__name">{participant.fullName || participant.username}</span>
                    <span className="group-info__username">@{participant.username}{isMe ? " (tú)" : ""}</span>
                  </span>
                  {participant.role === "ADMIN" && <span className="group-info__badge">Admin</span>}
                </>
              );
              return (
                <li key={participant.username}>
                  {isMe ? (
                    <div className="group-info__row group-info__row--static">{content}</div>
                  ) : (
                    <button
                      type="button"
                      className="group-info__row"
                      onClick={() => openActions(participant)}
                      aria-label={`Opciones de ${participant.fullName || participant.username}`}
                    >
                      {content}
                    </button>
                  )}
                </li>
              );
            })}
          </ul>
          {hasMore && (
            <Button variant="ghost" size="small" onClick={loadMore} disabled={loadingMore}>
              {loadingMore ? "Cargando…" : "Cargar más"}
            </Button>
          )}

          {!managed && (
            <button type="button" className="group-info__leave" onClick={() => setConfirmLeave(true)} disabled={busy}>
              Salir del grupo
            </button>
          )}
        </div>
      )}

      <BottomSheet open={Boolean(selected) && !toExpel} title={selected?.fullName || selected?.username || ""} onClose={closeActions}>
        {selected && (
          <div className="group-info__actions">
            <button type="button" className="group-info__action" onClick={() => pick((user) => onOpenProfile?.(user.username))}>
              Ver perfil
            </button>
            <button type="button" className="group-info__action" onClick={() => pick(onSendMessage)}>
              Enviar mensaje
            </button>
            {isAdmin && !managed && (
              <>
                <button type="button" className="group-info__action" onClick={() => toggleRole(selected)} disabled={busy}>
                  {selected.role === "ADMIN" ? "Quitar admin" : "Hacer admin"}
                </button>
                <button
                  type="button"
                  className="group-info__action group-info__action--danger"
                  onClick={() => { setActionError(null); setToExpel(selected); }}
                  disabled={busy}
                >
                  Expulsar
                </button>
              </>
            )}
            {actionError && <p className="group-info__error" role="alert">{actionError}</p>}
          </div>
        )}
      </BottomSheet>

      <ConfirmSheet
        open={Boolean(toExpel)}
        title={`¿Expulsar a ${toExpel?.fullName || toExpel?.username || ""}?`}
        message="Dejará de recibir los mensajes del grupo y perderá el historial. No se puede deshacer: solo volverá si la añaden de nuevo."
        confirmLabel="Expulsar"
        destructive
        loading={busy}
        error={toExpel ? actionError || "" : ""}
        onConfirm={expel}
        onCancel={() => {
          if (busy) return;
          setToExpel(null);
          setSelected(null);
          setActionError(null);
        }}
      />

      <ConfirmSheet
        open={confirmLeave}
        title="¿Salir del grupo?"
        message="Dejarás de recibir los mensajes de este grupo y no podrás volver a leer el historial salvo que te vuelvan a añadir."
        confirmLabel="Salir"
        destructive
        loading={busy}
        error={confirmLeave ? actionError || "" : ""}
        onConfirm={leave}
        onCancel={() => !busy && setConfirmLeave(false)}
      />
    </div>
  );
};

export default GroupInfoPanel;
