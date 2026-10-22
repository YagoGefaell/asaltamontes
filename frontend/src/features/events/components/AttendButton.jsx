import Button from '../../../shared/forms/Button.jsx'
import { isFull } from '../utils/eventFormat.js'

/**
 * Main action of an event according to its state: join, inside (tap to leave), waitlist (tap to leave),
 * full (join the waitlist), cancelled / finished. Presentational: the state lives in useEventAttendance.
 */
export default function AttendButton({ event, waitlistPosition = null, isOrganizer = false, pending = false, onJoin, onLeave }) {
  if (event.status === 'CANCELLED') {
    return <Button variant="secondary" disabled>Cancelado</Button>
  }
  if (event.status === 'FINISHED') {
    return <Button variant="secondary" disabled>Finalizada</Button>
  }
  if (isOrganizer) {
    return <Button variant="secondary" disabled>Eres la organizadora</Button>
  }
  if (event.myStatus === 'GOING') {
    return (
      <Button variant="outline" onClick={onLeave} disabled={pending} aria-label="Estás dentro. Pulsa para salir de la quedada">
        Estás dentro ✓
      </Button>
    )
  }
  if (event.myStatus === 'WAITLIST') {
    const label = waitlistPosition ? `En lista de espera (#${waitlistPosition})` : 'En lista de espera'
    return (
      <Button variant="outline" onClick={onLeave} disabled={pending} aria-label={`${label}. Pulsa para salir de la lista`}>
        {label}
      </Button>
    )
  }
  if (isFull(event)) {
    return (
      <Button onClick={onJoin} disabled={pending}>
        Completo · apuntarme a la lista
      </Button>
    )
  }
  return (
    <Button onClick={onJoin} disabled={pending}>
      Apuntarme
    </Button>
  )
}
