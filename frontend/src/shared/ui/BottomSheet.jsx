import { useEffect, useId, useRef, useState } from "react";
import { createPortal } from "react-dom";
import "./BottomSheet.css";

const FOCUSABLE =
  'a[href], button:not([disabled]), textarea:not([disabled]), input:not([disabled]), select:not([disabled]), [tabindex]:not([tabindex="-1"])';
const CLOSE_THRESHOLD = 100; // px arrastrados hacia abajo para cerrar

/**
 * Hoja inferior accesible: `aria-modal`, `Esc` cierra, foco atrapado y devuelto
 * a quien la abrió. Con `draggable` se cierra arrastrando el asa hacia abajo.
 */
export default function BottomSheet({ open, title, onClose, children, footer, draggable = true }) {
  const sheetRef = useRef(null);
  const onCloseRef = useRef(onClose);
  const startY = useRef(null);
  const [dragY, setDragY] = useState(0);
  const titleId = useId();

  useEffect(() => {
    onCloseRef.current = onClose;
  }, [onClose]);

  useEffect(() => {
    if (!open) return undefined;
    const previouslyFocused = document.activeElement;
    const sheet = sheetRef.current;
    (sheet.querySelector(FOCUSABLE) || sheet).focus();

    const onKeyDown = (event) => {
      if (event.key === "Escape") {
        event.stopPropagation();
        onCloseRef.current?.();
        return;
      }
      if (event.key !== "Tab") return;
      const items = Array.from(sheet.querySelectorAll(FOCUSABLE));
      if (items.length === 0) {
        event.preventDefault();
        return;
      }
      const first = items[0];
      const last = items[items.length - 1];
      if (event.shiftKey && document.activeElement === first) {
        event.preventDefault();
        last.focus();
      } else if (!event.shiftKey && document.activeElement === last) {
        event.preventDefault();
        first.focus();
      }
    };

    document.addEventListener("keydown", onKeyDown);
    return () => {
      document.removeEventListener("keydown", onKeyDown);
      if (previouslyFocused instanceof HTMLElement) previouslyFocused.focus();
    };
  }, [open]);

  if (!open) return null;

  const onPointerDown = (event) => {
    startY.current = event.clientY;
    event.currentTarget.setPointerCapture?.(event.pointerId);
  };
  const onPointerMove = (event) => {
    if (startY.current === null) return;
    setDragY(Math.max(0, event.clientY - startY.current));
  };
  const onPointerEnd = () => {
    if (startY.current === null) return;
    const shouldClose = dragY > CLOSE_THRESHOLD;
    startY.current = null;
    setDragY(0);
    if (shouldClose) onClose?.();
  };

  return createPortal(
    <div className="bottom-sheet-backdrop" onMouseDown={(e) => e.target === e.currentTarget && onClose?.()}>
      <div
        ref={sheetRef}
        className="bottom-sheet"
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        tabIndex={-1}
        style={dragY ? { transform: `translateY(${dragY}px)`, transition: "none" } : undefined}
      >
        {draggable && (
          <div
            className="bottom-sheet__handle"
            data-testid="bottom-sheet-handle"
            onPointerDown={onPointerDown}
            onPointerMove={onPointerMove}
            onPointerUp={onPointerEnd}
            onPointerCancel={onPointerEnd}
          >
            <span className="bottom-sheet__grip" />
          </div>
        )}
        <h2 id={titleId} className="bottom-sheet__title">{title}</h2>
        <div className="bottom-sheet__body">{children}</div>
        {footer && <div className="bottom-sheet__footer">{footer}</div>}
      </div>
    </div>,
    document.body,
  );
}
