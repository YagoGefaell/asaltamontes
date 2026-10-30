import { useState } from "react";
import { FaEllipsisH } from "react-icons/fa";
import BottomSheet from "../../../shared/ui/BottomSheet";
import ReportSheet from "./ReportSheet";
import "./ReportSheet.css";

/**
 * «⋯» button whose sheet has the single action «Denunciar» (T23b2), for screens without a menu of their own.
 * `className` styles the trigger like the neighbouring buttons.
 */
export default function ReportMenu({ menuTitle, reportTitle, actionLabel = "Denunciar", targetType, target, className }) {
  const [menuOpen, setMenuOpen] = useState(false);
  const [reportOpen, setReportOpen] = useState(false);

  return (
    <>
      <button type="button" className={className} onClick={() => setMenuOpen(true)} aria-label="Más opciones" aria-haspopup="dialog">
        <FaEllipsisH aria-hidden="true" />
      </button>
      <BottomSheet open={menuOpen} title={menuTitle} onClose={() => setMenuOpen(false)}>
        <button type="button" className="report-menu__action" onClick={() => { setMenuOpen(false); setReportOpen(true); }}>
          {actionLabel}
        </button>
      </BottomSheet>
      <ReportSheet open={reportOpen} title={reportTitle} targetType={targetType} target={target} onClose={() => setReportOpen(false)} />
    </>
  );
}
