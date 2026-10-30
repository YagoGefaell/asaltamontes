import { useEffect, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import {
  dismissReportRequest,
  getAdminReportRequest,
  removeReportedContentRequest,
} from '../../features/admin/services/admin.service.js'
import {
  REASON_LABELS,
  REMOVE_EFFECT,
  STATUS_LABELS,
  TYPE_LABELS,
  formatDateTime,
} from '../../features/admin/utils/reportLabels.js'
import Button from '../../shared/forms/Button.jsx'
import Input from '../../shared/forms/Input.jsx'
import ConfirmSheet from '../../shared/ui/ConfirmSheet.jsx'
import PageHeader from '../../shared/ui/PageHeader.jsx'
import Skeleton from '../../shared/ui/Skeleton.jsx'
import './Admin.css'

const NOTE_MAX = 1000

// details, contentSnapshot and the note are user-written free text: always rendered as React text nodes
// (escaped, with line breaks kept by CSS), never as HTML.
function Field({ label, children }) {
  return (
    <div className="admin-field">
      <dt>{label}</dt>
      <dd>{children}</dd>
    </div>
  )
}

function AdminReportDetail() {
  const { id } = useParams()
  const navigate = useNavigate()
  const [report, setReport] = useState(null)
  const [loadError, setLoadError] = useState('')
  const [sheet, setSheet] = useState(null) // 'dismiss' | 'remove' | null
  const [note, setNote] = useState('')
  const [busy, setBusy] = useState(false)
  const [actionError, setActionError] = useState('')

  useEffect(() => {
    let active = true
    getAdminReportRequest(id)
      .then((data) => active && setReport(data))
      .catch((err) => {
        if (active) setLoadError(err.status === 404 ? 'Esta denuncia ya no existe.' : 'No se ha podido cargar la denuncia.')
      })
    return () => {
      active = false
    }
  }, [id])

  const openSheet = (which) => {
    setNote('')
    setActionError('')
    setSheet(which)
  }

  const run = async () => {
    setBusy(true)
    setActionError('')
    try {
      const request = sheet === 'dismiss' ? dismissReportRequest : removeReportedContentRequest
      setReport(await request(id, note.trim()))
      setSheet(null)
    } catch (err) {
      setActionError(err.status === 403 ? 'No tienes permiso para hacer esto.' : err.message || 'No se ha podido completar la acción.')
    } finally {
      setBusy(false)
    }
  }

  const isOpen = report?.status === 'OPEN'
  const canRemove = isOpen && report.targetType !== 'USER' && report.contentAvailable !== false

  return (
    <div className="admin-page">
      <PageHeader title="Denuncia" onBack={() => navigate('/admin')} />

      <main className="admin-detail">
        {loadError && <p className="admin-error" role="alert">{loadError}</p>}

        {!report && !loadError && (
          <>
            <Skeleton width="50%" height="1.25rem" />
            <Skeleton width="100%" height="4rem" />
          </>
        )}

        {report && (
          <>
            <h2 className="admin-detail__title">
              {TYPE_LABELS[report.targetType] ?? report.targetType} · {REASON_LABELS[report.reason] ?? report.reason}
            </h2>
            <p className="admin-detail__status">{STATUS_LABELS[report.status] ?? report.status}</p>

            <dl className="admin-fields">
              <Field label="Denunciada por">@{report.reporterUsername}</Field>
              {report.reportedUsername && <Field label="Autora o cuenta denunciada">@{report.reportedUsername}</Field>}
              {report.targetType === 'USER' && <Field label="Cuenta">@{report.target}</Field>}
              {report.targetType === 'COMMUNITY' && <Field label="Comunidad">{report.target}</Field>}
              <Field label="Fecha">{formatDateTime(report.createdAt)}</Field>
              <Field label="Denuncias abiertas sobre lo mismo">{report.openReportsOnTarget}</Field>
              {report.contentAvailable === false && <Field label="Estado del contenido">Ya no existe</Field>}
            </dl>

            {report.details && (
              <section className="admin-block">
                <h3>Detalles de la denunciante</h3>
                <p className="admin-text">{report.details}</p>
              </section>
            )}

            {report.contentSnapshot && (
              <section className="admin-block">
                <h3>Copia del contenido</h3>
                <p className="admin-text">{report.contentSnapshot}</p>
              </section>
            )}

            {!isOpen && (
              <section className="admin-block">
                <h3>Resolución</h3>
                <p className="admin-text">
                  {STATUS_LABELS[report.status]} por @{report.handledByUsername} · {formatDateTime(report.handledAt)}
                  {report.resolution ? `\n${report.resolution}` : ''}
                </p>
              </section>
            )}

            {isOpen && (
              <div className="admin-actions">
                {canRemove && (
                  <Button variant="danger" onClick={() => openSheet('remove')}>Borrar contenido</Button>
                )}
                <Button variant="secondary" onClick={() => openSheet('dismiss')}>Descartar denuncia</Button>
              </div>
            )}
          </>
        )}
      </main>

      <ConfirmSheet
        open={sheet !== null}
        title={sheet === 'remove' ? 'Borrar contenido' : 'Descartar denuncia'}
        message={sheet === 'remove'
          ? `${REMOVE_EFFECT[report?.targetType] ?? ''} Se cerrarán todas las denuncias abiertas sobre ello. No se puede deshacer.`
          : 'Solo se cierra esta denuncia. La misma usuaria no podrá volver a denunciar lo mismo durante 7 días.'}
        confirmLabel={sheet === 'remove' ? 'Borrar contenido' : 'Descartar'}
        destructive={sheet === 'remove'}
        loading={busy}
        error={actionError}
        onConfirm={run}
        onCancel={() => setSheet(null)}
      >
        <Input
          as="textarea"
          id="admin-resolution-note"
          label="Nota (opcional)"
          rows={3}
          maxLength={NOTE_MAX}
          value={note}
          onChange={(event) => setNote(event.target.value)}
        />
      </ConfirmSheet>
    </div>
  )
}

export default AdminReportDetail
