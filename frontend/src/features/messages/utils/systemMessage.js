// Text of a system message of a group (T12). The server sends a structured payload ({ action, actor, targets, ... })
// and the text is composed here, in Spanish, so it can say "Tú" / "te" for the current user (D-019: no text in the API).
// `content` is only the fallback for actions this client does not know yet.
import { formatEventDateTime } from "../../events/utils/eventFormat";

const name = (username) => `@${username}`;

function names(targets, me) {
  const list = (Array.isArray(targets) ? targets : []).map((target) => (target === me ? "ti" : name(target)));
  if (list.length <= 1) return list[0] ?? "";
  return `${list.slice(0, -1).join(", ")} y ${list[list.length - 1]}`;
}

export function systemMessageText(message, currentUsername) {
  const payload = message?.systemPayload;
  const fallback = message?.content ?? "";
  if (!payload?.action) return fallback;

  const me = currentUsername;
  const actorIsMe = payload.actor === me;
  const actor = payload.actor ? name(payload.actor) : "Alguien";
  const targets = Array.isArray(payload.targets) ? payload.targets : [];
  const onlyMeTargeted = targets.length === 1 && targets[0] === me;

  switch (payload.action) {
    case "MEMBER_ADDED":
      if (actorIsMe) return `Añadiste a ${names(targets, me)}`;
      return onlyMeTargeted ? `${actor} te añadió al grupo` : `${actor} añadió a ${names(targets, me)}`;
    case "MEMBER_REMOVED":
      return actorIsMe ? `Expulsaste a ${names(targets, me)}` : `${actor} expulsó a ${names(targets, me)}`;
    case "MEMBER_LEFT":
      return actorIsMe ? "Saliste del grupo" : `${actor} salió del grupo`;
    case "ROLE_CHANGED":
      if (payload.role === "ADMIN") {
        if (actorIsMe) return `Hiciste administradora a ${names(targets, me)}`;
        return onlyMeTargeted ? `${actor} te hizo administradora` : `${actor} hizo administradora a ${names(targets, me)}`;
      }
      if (actorIsMe) return `Quitaste el rol de administradora a ${names(targets, me)}`;
      return onlyMeTargeted
        ? `${actor} te quitó el rol de administradora`
        : `${actor} quitó el rol de administradora a ${names(targets, me)}`;
    case "ADMIN_ASSIGNED":
      return onlyMeTargeted ? "Ahora eres administradora del grupo" : `${names(targets, me)} es ahora administradora del grupo`;
    case "GROUP_RENAMED":
      return `${actorIsMe ? "Cambiaste" : `${actor} cambió`} el nombre del grupo a «${payload.name ?? ""}»`;
    case "GROUP_IMAGE_CHANGED":
      if (payload.removed) return actorIsMe ? "Quitaste la imagen del grupo" : `${actor} quitó la imagen del grupo`;
      return actorIsMe ? "Cambiaste la imagen del grupo" : `${actor} cambió la imagen del grupo`;
    case "HISTORY_VISIBILITY_CHANGED":
      if (payload.visible) {
        return `${actorIsMe ? "Activaste" : `${actor} activó`} que las nuevas participantes vean el historial`;
      }
      return `${actorIsMe ? "Desactivaste" : `${actor} desactivó`} que las nuevas participantes vean el historial`;
    case "EVENT_CANCELLED":
      return payload.reason ? `La quedada se ha cancelado: ${payload.reason}` : "La quedada se ha cancelado";
    case "EVENT_UPDATED": {
      const changed = Array.isArray(payload.changed) ? payload.changed : [];
      const parts = [];
      if (changed.includes("startsAt") || changed.includes("timezone")) parts.push("la hora");
      if (changed.includes("city") || changed.includes("meetingPoint")) parts.push("el lugar");
      const what = parts.length > 0 ? `Ha cambiado ${parts.join(" y ")} de la quedada` : "Ha cambiado la quedada";
      // The time at the place of the event, with its zone when it differs from hers (D-019)
      return payload.startsAt ? `${what}. Ahora: ${formatEventDateTime(payload.startsAt, payload.timeZone)}` : what;
    }
    default:
      return fallback;
  }
}
