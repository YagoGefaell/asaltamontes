const user = (username) => (username ? `@${username}` : "una cuenta eliminada");

// Sentence of each action: actor + what happened to target
const ACTION_TEXT = {
  PROMOTE: (a, t) => `${a} nombró moderadora a ${t}`,
  DEMOTE: (a, t) => `${a} quitó la moderación a ${t}`,
  MUTE: (a, t) => `${a} silenció a ${t}`,
  UNMUTE: (a, t) => `${a} quitó el silencio a ${t}`,
  BAN: (a, t) => `${a} expulsó a ${t}`,
  UNBAN: (a, t) => `${a} readmitió a ${t}`,
  TRANSFER_OWNERSHIP: (a, t) => `${a} transfirió la propiedad a ${t}`,
  REQUEST_APPROVED: (a, t) => `${a} aceptó la solicitud de ${t}`,
  REQUEST_REJECTED: (a, t) => `${a} rechazó la solicitud de ${t}`,
  MESSAGE_DELETED: (a, t) => `${a} borró un mensaje de ${t}`,
  COMMUNITY_UPDATED: (a) => `${a} editó los datos de la comunidad`,
};

// COMMUNITY_UPDATED stores the changed fields in `reason`; the others a free text from the moderator
const FIELD_LABEL = { name: "nombre", description: "descripción", city: "ciudad", province: "provincia", visibility: "visibilidad", image: "imagen" };

export function describeEntry(entry) {
  const text = (ACTION_TEXT[entry.action] || ((a) => `${a} hizo una acción de moderación`))(
    user(entry.actorUsername),
    user(entry.targetUsername),
  );
  let detail = null;
  if (entry.reason) {
    detail = entry.action === "COMMUNITY_UPDATED"
      ? `Cambios: ${entry.reason.split(",").map((f) => FIELD_LABEL[f] || f).join(", ")}`
      : `Motivo: ${entry.reason}`;
  }
  return { text, detail };
}
