import { useState } from 'react'
import { useAuth } from '../../auth/hooks/useAuth'
import Button from '../../../shared/forms/Button'
import { decideFollowRequest } from '../services/users.service'
import './FollowRequestActions.css'

/**
 * Accept / reject buttons of a FOLLOW_REQUEST notification (T23a). The row resolves in place: loading and error
 * inline, then a one-line result. Accepting counts one more follower for the session.
 */
export default function FollowRequestActions({ username }) {
  const { incrementFollowers } = useAuth()
  const [busy, setBusy] = useState(null)
  const [result, setResult] = useState(null)
  const [error, setError] = useState('')

  const decide = async (decision) => {
    setBusy(decision)
    setError('')
    try {
      const done = await decideFollowRequest(username, decision)
      if (decision === 'approve' && done) incrementFollowers()
      setResult(done ? (decision === 'approve' ? 'accepted' : 'rejected') : 'gone')
    } catch (err) {
      setError(err?.message || 'No se ha podido completar la acción')
    } finally {
      setBusy(null)
    }
  }

  if (result) {
    const text = { accepted: 'Solicitud aceptada', rejected: 'Solicitud rechazada', gone: 'Solicitud ya resuelta' }[result]
    return <p className="fra-result" role="status">{text}</p>
  }

  return (
    <div className="fra">
      <div className="fra-buttons">
        <Button size="small" variant="primary" loading={busy === 'approve'} disabled={busy !== null} onClick={() => decide('approve')}
          aria-label={`Aceptar la solicitud de ${username}`}>
          Aceptar
        </Button>
        <Button size="small" variant="outline" loading={busy === 'reject'} disabled={busy !== null} onClick={() => decide('reject')}
          aria-label={`Rechazar la solicitud de ${username}`}>
          Rechazar
        </Button>
      </div>
      {error && <p className="fra-error" role="alert">{error}</p>}
    </div>
  )
}
