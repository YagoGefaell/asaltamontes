import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useAdminReports } from '../../features/admin/hooks/useAdminReports.js'
import { REASON_LABELS, STATUS_LABELS, TYPE_LABELS, formatDateTime } from '../../features/admin/utils/reportLabels.js'
import Button from '../../shared/forms/Button.jsx'
import Input from '../../shared/forms/Input.jsx'
import BottomSheet from '../../shared/ui/BottomSheet.jsx'
import { Chip, ChipRow } from '../../shared/ui/Chip.jsx'
import EmptyState from '../../shared/ui/EmptyState.jsx'
import PageHeader from '../../shared/ui/PageHeader.jsx'
import Skeleton from '../../shared/ui/Skeleton.jsx'
import './Admin.css'

const STATUSES = ['OPEN', 'DISMISSED', 'ACTIONED']

function ReportQueue({ status, type }) {
  const { items, hasMore, loading, error, loadMore, retry } = useAdminReports({ status, type })

  return (
    <>
      {error && (
        <div className="admin-error" role="alert">
          <p>{error}</p>
          <Button variant="ghost" onClick={retry}>Reintentar</Button>
        </div>
      )}

      {loading && items.length === 0 && (
        <ul className="admin-list" aria-label="Cargando denuncias">
          {[0, 1, 2].map((n) => (
            <li key={n} className="admin-row admin-row--skeleton">
              <Skeleton width="40%" height="1rem" />
              <Skeleton width="70%" height="0.875rem" />
            </li>
          ))}
        </ul>
      )}

      {!loading && !error && items.length === 0 && (
        <EmptyState
          title={status === 'OPEN' ? 'No hay denuncias abiertas' : 'No hay denuncias aquí'}
          description="Cuando alguien denuncie algo, aparecerá en esta lista."
        />
      )}

      {items.length > 0 && (
        <ul className="admin-list">
          {items.map((report) => (
            <li key={report.id}>
              <Link to={`/admin/reports/${report.id}`} className="admin-row">
                <span className="admin-row__title">
                  {TYPE_LABELS[report.targetType] ?? report.targetType} · {REASON_LABELS[report.reason] ?? report.reason}
                </span>
                <span className="admin-row__meta">
                  Denuncia de @{report.reporterUsername} · {formatDateTime(report.createdAt)}
                  {report.openReportsOnTarget > 1 && ` · ${report.openReportsOnTarget} abiertas sobre lo mismo`}
                </span>
                {status !== 'OPEN' && <span className="admin-row__status">{STATUS_LABELS[report.status]}</span>}
              </Link>
            </li>
          ))}
        </ul>
      )}

      {hasMore && (
        <div className="admin-more">
          <Button variant="ghost" loading={loading} onClick={loadMore}>Cargar más</Button>
        </div>
      )}
    </>
  )
}

function Admin() {
  const navigate = useNavigate()
  const [status, setStatus] = useState('OPEN')
  const [type, setType] = useState('')
  const [sheetOpen, setSheetOpen] = useState(false)
  const [draftType, setDraftType] = useState('')

  const openSheet = () => {
    setDraftType(type)
    setSheetOpen(true)
  }
  const apply = (next) => {
    setType(next)
    setSheetOpen(false)
  }

  return (
    <div className="admin-page">
      <PageHeader title="Moderación" onBack={() => navigate('/settings')} />

      <ChipRow label="Filtrar denuncias">
        {STATUSES.map((value) => (
          <Chip key={value} selected={status === value} onClick={() => setStatus(value)}>
            {value === 'OPEN' ? 'Abiertas' : value === 'DISMISSED' ? 'Descartadas' : 'Resueltas'}
          </Chip>
        ))}
        <Chip selected={Boolean(type)} onClick={openSheet}>{type ? 'Filtros · 1' : 'Filtros'}</Chip>
      </ChipRow>

      <ReportQueue key={`${status}|${type}`} status={status} type={type || undefined} />

      <BottomSheet
        open={sheetOpen}
        title="Filtros"
        onClose={() => setSheetOpen(false)}
        footer={(
          <>
            <Button variant="primary" onClick={() => apply(draftType)}>Aplicar</Button>
            <Button variant="ghost" onClick={() => apply('')}>Quitar filtros</Button>
          </>
        )}
      >
        <Input as="select" id="admin-type-filter" label="Tipo de contenido" value={draftType} onChange={(event) => setDraftType(event.target.value)}>
          <option value="">Todos</option>
          {Object.entries(TYPE_LABELS).map(([value, label]) => (
            <option key={value} value={value}>{label}</option>
          ))}
        </Input>
      </BottomSheet>
    </div>
  )
}

export default Admin
