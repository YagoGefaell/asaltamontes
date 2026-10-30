// "Ana, Lucía, Marta y 4 más" / "7 participantes" under the group name
export function groupSubtitle(participantNames, participantCount) {
  const names = (participantNames || []).filter(Boolean);
  if (!participantCount) return "";
  if (names.length === 0) return `${participantCount} participantes`;
  const shown = names.slice(0, 3);
  const rest = participantCount - shown.length;
  if (rest <= 0) {
    return shown.length === 1 ? shown[0] : `${shown.slice(0, -1).join(", ")} y ${shown[shown.length - 1]}`;
  }
  return `${shown.join(", ")} y ${rest} más`;
}
