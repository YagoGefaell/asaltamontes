import { useCallback, useEffect, useState } from "react";
import Avatar from "../../../shared/ui/Avatar";
import Button from "../../../shared/forms/Button";
import EmptyState from "../../../shared/ui/EmptyState";
import Modal from "../../../shared/ui/Modal";
import Skeleton from "../../../shared/ui/Skeleton";
import FormError from "../../../shared/forms/FormError";
import { useToast } from "../../../shared/ui/Toast";
import {
  getCommunityMembersPageRequest,
  setMemberRoleRequest,
  muteMemberRequest,
  unmuteMemberRequest,
  banMemberRequest,
  transferOwnershipRequest,
} from "../../../features/communities/services/communities.service";
import { communityErrorMessage } from "../../../features/communities/utils/communityErrors";
import "./CommunityModeration.css";

const ROLE_LABEL = { OWNER: "Propietaria", MODERATOR: "Moderadora" };
const REASON_MAX = 300;

/**
 * Members management (T19b). Owner: roles, transfer, mute, expel. Moderator: mute and expel plain members only
 * (the backend enforces it too; here the buttons just are not offered).
 */
export default function MemberAdminList({ community, onOwnershipTransferred }) {
  const toast = useToast();
  const isOwner = community.role === "OWNER";
  const [members, setMembers] = useState([]);
  const [page, setPage] = useState(0);
  const [hasMore, setHasMore] = useState(false);
  const [loading, setLoading] = useState(true);
  const [loadingMore, setLoadingMore] = useState(false);
  const [error, setError] = useState(null);
  const [busy, setBusy] = useState(null);
  const [banTarget, setBanTarget] = useState(null);
  const [reason, setReason] = useState("");
  const [transferTarget, setTransferTarget] = useState(null);
  const [dialogError, setDialogError] = useState(null);

  useEffect(() => {
    let cancelled = false;
    getCommunityMembersPageRequest(community.id, 0)
      .then((result) => {
        if (cancelled) return;
        setMembers(result.items);
        setHasMore(result.hasMore);
        setPage(0);
      })
      .catch((err) => { if (!cancelled) setError(communityErrorMessage(err)); })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, [community.id]);

  const loadMore = async () => {
    setLoadingMore(true);
    try {
      const result = await getCommunityMembersPageRequest(community.id, page + 1);
      setMembers((prev) => {
        const seen = new Set(prev.map((m) => m.username));
        return [...prev, ...result.items.filter((m) => !seen.has(m.username))];
      });
      setHasMore(result.hasMore);
      setPage(page + 1);
    } catch (err) {
      toast.error(communityErrorMessage(err));
    } finally {
      setLoadingMore(false);
    }
  };

  const patch = useCallback((username, changes) => {
    setMembers((prev) => prev.map((m) => (m.username === username ? { ...m, ...changes } : m)));
  }, []);

  const run = async (username, action, onDone) => {
    setBusy(username);
    try {
      await action();
      onDone();
    } catch (err) {
      toast.error(communityErrorMessage(err));
    } finally {
      setBusy(null);
    }
  };

  const toggleModerator = (member) => {
    const next = member.role === "MODERATOR" ? "MEMBER" : "MODERATOR";
    run(member.username, () => setMemberRoleRequest(community.id, member.username, next), () => {
      patch(member.username, { role: next });
      toast.success(next === "MODERATOR" ? `@${member.username} ahora es moderadora` : `@${member.username} ya no es moderadora`);
    });
  };

  const toggleMute = (member) => {
    const muted = member.status === "MUTED";
    run(
      member.username,
      () => (muted ? unmuteMemberRequest(community.id, member.username) : muteMemberRequest(community.id, member.username)),
      () => {
        patch(member.username, { status: muted ? "ACTIVE" : "MUTED" });
        toast.success(muted ? `@${member.username} ya puede participar` : `@${member.username} silenciada`);
      },
    );
  };

  const closeBan = () => {
    if (busy) return;
    setBanTarget(null);
    setReason("");
    setDialogError(null);
  };

  const confirmBan = async () => {
    const target = banTarget;
    setBusy(target.username);
    setDialogError(null);
    try {
      await banMemberRequest(community.id, target.username, reason);
      setMembers((prev) => prev.filter((m) => m.username !== target.username));
      toast.success(`@${target.username} expulsada`);
      setBanTarget(null);
      setReason("");
    } catch (err) {
      setDialogError(communityErrorMessage(err));
    } finally {
      setBusy(null);
    }
  };

  const confirmTransfer = async () => {
    const target = transferTarget;
    setBusy(target.username);
    setDialogError(null);
    try {
      await transferOwnershipRequest(community.id, target.username);
      toast.success(`@${target.username} es ahora la propietaria`);
      setTransferTarget(null);
      onOwnershipTransferred?.();
    } catch (err) {
      setDialogError(communityErrorMessage(err));
    } finally {
      setBusy(null);
    }
  };

  if (loading) {
    return <Skeleton width="100%" height="4rem" />;
  }
  if (error) {
    return <FormError message={error} />;
  }
  if (members.length === 0) {
    return <EmptyState title="Todavía no hay más miembros" description="Cuando alguien se una, aparecerá aquí." />;
  }

  return (
    <>
      <ul className="moderation-list">
        {members.map((member) => {
          const isModerator = member.role === "MODERATOR";
          const canModerate = member.role !== "OWNER" && (isOwner || !isModerator);
          const muted = member.status === "MUTED";
          const disabled = busy === member.username;
          return (
            <li key={member.username} className="moderation-item">
              <Avatar src={member.profilePictureUrl} name={member.fullName || member.username} size="md" />
              <div className="moderation-item__info">
                <strong>{member.fullName || member.username}</strong>
                <span className="moderation-item__meta">@{member.username}</span>
                <span className="moderation-item__badges">
                  {ROLE_LABEL[member.role] && <span className="moderation-badge">{ROLE_LABEL[member.role]}</span>}
                  {muted && <span className="moderation-badge moderation-badge--warning">Silenciada</span>}
                </span>
              </div>
              {canModerate && (
                <div className="moderation-item__actions">
                  {isOwner && (
                    <Button variant="outline" size="small" disabled={disabled} onClick={() => toggleModerator(member)}>
                      {isModerator ? "Quitar moderación" : "Hacer moderadora"}
                    </Button>
                  )}
                  <Button variant="outline" size="small" disabled={disabled} onClick={() => toggleMute(member)}>
                    {muted ? "Quitar silencio" : "Silenciar"}
                  </Button>
                  <Button variant="danger" size="small" disabled={disabled} onClick={() => setBanTarget(member)}>
                    Expulsar
                  </Button>
                  {isOwner && (
                    <Button variant="ghost" size="small" disabled={disabled} onClick={() => setTransferTarget(member)}>
                      Transferir propiedad
                    </Button>
                  )}
                </div>
              )}
            </li>
          );
        })}
      </ul>
      {hasMore && (
        <Button variant="secondary" onClick={loadMore} loading={loadingMore}>Cargar más</Button>
      )}

      <Modal
        open={!!banTarget}
        title={banTarget ? `Expulsar a @${banTarget.username}` : ""}
        onClose={closeBan}
        footer={
          <>
            <Button variant="outline" size="small" onClick={closeBan} disabled={!!busy}>Cancelar</Button>
            <Button variant="danger" size="small" onClick={confirmBan} loading={!!busy}>Expulsar</Button>
          </>
        }
      >
        <p>Dejará de ver la comunidad como miembro y no podrá volver a unirse hasta que la readmitas.</p>
        <label className="moderation-label" htmlFor="ban-reason">Motivo (opcional)</label>
        <textarea
          id="ban-reason"
          className="moderation-input moderation-textarea"
          rows="3"
          maxLength={REASON_MAX}
          value={reason}
          onChange={(e) => setReason(e.target.value)}
          placeholder="Queda en el registro de moderación"
        />
        <FormError message={dialogError} />
      </Modal>

      <Modal
        open={!!transferTarget}
        title="Transferir la propiedad"
        onClose={() => { if (!busy) { setTransferTarget(null); setDialogError(null); } }}
        footer={
          <>
            <Button variant="outline" size="small" onClick={() => { setTransferTarget(null); setDialogError(null); }} disabled={!!busy}>
              Cancelar
            </Button>
            <Button variant="danger" size="small" onClick={confirmTransfer} loading={!!busy}>Transferir</Button>
          </>
        }
      >
        <p>
          @{transferTarget?.username} pasará a ser la propietaria de la comunidad y tú serás moderadora.
          Solo ella podrá devolvértela.
        </p>
        <FormError message={dialogError} />
      </Modal>
    </>
  );
}
