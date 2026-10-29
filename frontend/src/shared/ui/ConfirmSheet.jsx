import BottomSheet from "./BottomSheet";
import Button from "../forms/Button";
import FormError from "../forms/FormError";
import "./ConfirmSheet.css";

/**
 * Confirmación de lo irreversible en una hoja inferior (DESIGN.md «Diálogos y avisos»).
 * `error` muestra el fallo dentro de la propia hoja, que sigue abierta para reintentar.
 */
export default function ConfirmSheet({
  open,
  title,
  message,
  confirmLabel = "Confirmar",
  cancelLabel = "Cancelar",
  destructive = false,
  loading = false,
  error = "",
  children,
  onConfirm,
  onCancel,
}) {
  return (
    <BottomSheet
      open={open}
      title={title}
      onClose={onCancel}
      footer={(
        <>
          <FormError message={error} />
          <Button variant={destructive ? "danger" : "primary"} loading={loading} onClick={onConfirm}>{confirmLabel}</Button>
          <Button variant="ghost" onClick={onCancel}>{cancelLabel}</Button>
        </>
      )}
    >
      <p className="confirm-sheet__message">{message}</p>
      {children}
    </BottomSheet>
  );
}
