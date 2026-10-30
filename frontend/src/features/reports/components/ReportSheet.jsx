import { useState } from "react";
import BottomSheet from "../../../shared/ui/BottomSheet";
import Button from "../../../shared/forms/Button";
import FormError from "../../../shared/forms/FormError";
import Input from "../../../shared/forms/Input";
import { formatWait } from "../../communities/utils/communityErrors";
import { createReportRequest, REPORT_DETAILS_MAX, REPORT_REASONS } from "../services/reports.service";
import "./ReportSheet.css";

function reportErrorMessage(error) {
  if (error?.status === 409) return "Ya has denunciado esto. Lo revisaremos.";
  if (error?.status === 404) return "Esto ya no está disponible.";
  if (error?.status === 429) {
    const wait = formatWait(error.retryAfter);
    return wait
      ? `Has enviado muchas denuncias hoy. Podrás volver a denunciar en ${wait}.`
      : "Has enviado muchas denuncias hoy. Inténtalo de nuevo más tarde.";
  }
  if (error?.code === "NETWORK_ERROR" || error?.code === "TIMEOUT") return "Sin conexión con el servidor. Inténtalo de nuevo.";
  return error?.message || "No se ha podido enviar la denuncia.";
}

/**
 * Bottom sheet to report a user, community, event, publication, comment or message (T23b2).
 * Errors stay inside the sheet; once sent it says so and offers «Cerrar» (nothing is said about the reported account).
 */
export default function ReportSheet({ open, title = "Denunciar", targetType, target, onClose }) {
  const [reason, setReason] = useState("");
  const [details, setDetails] = useState("");
  const [sending, setSending] = useState(false);
  const [error, setError] = useState("");
  const [sent, setSent] = useState(false);

  const detailsMissing = reason === "OTHER" && !details.trim();

  const close = () => {
    onClose?.();
    setReason("");
    setDetails("");
    setError("");
    setSent(false);
  };

  const submit = async (event) => {
    event.preventDefault();
    if (!reason || detailsMissing || sending) return;
    setSending(true);
    setError("");
    try {
      await createReportRequest({ targetType, target, reason, details });
      setSent(true);
    } catch (requestError) {
      setError(reportErrorMessage(requestError));
    } finally {
      setSending(false);
    }
  };

  return (
    <BottomSheet open={open} title={title} onClose={close}>
      {sent ? (
        <div className="report-sheet">
          <p className="report-sheet__text">Gracias. Hemos recibido tu denuncia y la revisaremos.</p>
          <Button variant="secondary" onClick={close}>Cerrar</Button>
        </div>
      ) : (
        <form className="report-sheet" onSubmit={submit}>
          <Input
            as="select"
            id="report-reason"
            label="Motivo"
            value={reason}
            onChange={(e) => setReason(e.target.value)}
          >
            <option value="" disabled>Elige un motivo</option>
            {REPORT_REASONS.map(({ value, label }) => <option key={value} value={value}>{label}</option>)}
          </Input>
          <Input
            as="textarea"
            id="report-details"
            label={reason === "OTHER" ? "Cuéntanos qué ha pasado" : "Detalles (opcional)"}
            rows={3}
            maxLength={REPORT_DETAILS_MAX}
            value={details}
            onChange={(e) => setDetails(e.target.value)}
          />
          <FormError message={error} />
          <Button type="submit" variant="primary" loading={sending} disabled={!reason || detailsMissing}>Enviar denuncia</Button>
        </form>
      )}
    </BottomSheet>
  );
}
