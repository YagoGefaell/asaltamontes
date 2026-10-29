import { useEffect, useState } from 'react'
import { useNavigate, useParams, useSearchParams } from 'react-router-dom'
import EventForm from '../../features/events/components/EventForm.jsx'
import { useEvent } from '../../features/events/hooks/useEvent.js'
import { createEventRequest, updateEventRequest } from '../../features/events/services/events.service.js'
import {
  EMPTY_FORM, formFromEvent, toCreatePayload, toUpdatePayload,
} from '../../features/events/utils/eventForm.js'
import { getOrganizableCommunitiesRequest } from '../../features/communities/services/communities.service.js'
import EmptyState from '../../shared/ui/EmptyState.jsx'
import Skeleton from '../../shared/ui/Skeleton.jsx'
import Button from '../../shared/forms/Button.jsx'
import PageHeader from '../../shared/ui/PageHeader.jsx'
import './EventDetail.css'

function Header({ title }) {
  const navigate = useNavigate()
  return (
    <PageHeader title={title} onBack={() => navigate(-1)} backLabel="Volver" />
  )
}

// Field errors of the API's 400 ({ field: message }) that the form knows how to show
function fieldErrors(error) {
  const known = ['title', 'description', 'type', 'startsAt', 'endsAt', 'meetingPointName', 'city', 'distanceKm', 'elevationGainM', 'capacity', 'timezone']
  return Object.fromEntries(Object.entries(error?.errors ?? {}).filter(([key]) => known.includes(key)))
}

/** Shared by both modes: sends the request and reports the outcome. */
function useSave(save, onDone) {
  const [submitting, setSubmitting] = useState(false)
  const [serverErrors, setServerErrors] = useState({})
  const [formError, setFormError] = useState(null)
  const submit = async () => {
    setSubmitting(true)
    setServerErrors({})
    setFormError(null)
    try {
      onDone(await save())
    } catch (error) {
      const errors = fieldErrors(error)
      setServerErrors(errors)
      // Without a field to blame (403, 409, past start…), say it above the button
      if (Object.keys(errors).length === 0) setFormError(error.message)
    } finally {
      setSubmitting(false)
    }
  }
  return { submitting, serverErrors, formError, submit }
}

function CreateEvent() {
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const [values, setValues] = useState({ ...EMPTY_FORM, communityId: searchParams.get('community') ?? '' })
  const [communities, setCommunities] = useState([])
  const [loadError, setLoadError] = useState(null)

  useEffect(() => {
    let active = true
    getOrganizableCommunitiesRequest()
      .then((list) => {
        if (!active) return
        setCommunities(list)
        // A community id in the link only counts if she can organize in it
        setValues((prev) => (list.some((c) => String(c.id) === prev.communityId) ? prev : { ...prev, communityId: '' }))
      })
      .catch(() => active && setLoadError('No se han podido cargar tus comunidades: la quedada será abierta.'))
    return () => {
      active = false
    }
     
  }, [])

  const { submitting, serverErrors, formError, submit } = useSave(
    () => createEventRequest(toCreatePayload(values)),
    (event) => {
      navigate(`/events/${event.id}`, { replace: true })
    },
  )

  return (
    <div className="event-detail">
      <Header title="Crear quedada" />
      <EventForm
        values={values}
        onChange={setValues}
        onSubmit={submit}
        submitting={submitting}
        communities={communities}
        serverErrors={serverErrors}
        formError={formError || loadError}
      />
    </div>
  )
}

function EditForm({ event }) {
  const navigate = useNavigate()
  const [original] = useState(() => formFromEvent(event))
  const [values, setValues] = useState(original)

  const { submitting, serverErrors, formError, submit } = useSave(
    () => updateEventRequest(event.id, toUpdatePayload(values, original)),
    () => {
      navigate(`/events/${event.id}`, { replace: true })
    },
  )

  return (
    <EventForm
      mode="edit"
      values={values}
      onChange={setValues}
      onSubmit={submit}
      submitting={submitting}
      original={original}
      communityName={event.communityName}
      serverErrors={serverErrors}
      formError={formError}
    />
  )
}

function EditEvent({ id }) {
  const navigate = useNavigate()
  const { event, loading, error, refetch } = useEvent(id)

  if (loading) {
    return (
      <div className="event-detail event-detail--loading">
        <Skeleton height="2rem" />
        <Skeleton height="12rem" />
      </div>
    )
  }

  let body
  if (error || !event) {
    body = (
      <EmptyState
        title="No se ha podido cargar la quedada"
        description={error || 'Puede que no exista o que no tengas acceso.'}
        action={<Button variant="outline" onClick={refetch}>Reintentar</Button>}
      />
    )
  } else if (!event.canEdit || event.status === 'CANCELLED') {
    body = (
      <EmptyState
        title={event.status === 'CANCELLED' ? 'La quedada está cancelada' : 'No puedes editar esta quedada'}
        description={event.status === 'CANCELLED' ? 'Una quedada cancelada ya no se puede editar.' : 'Solo la organizadora o la moderación de la comunidad pueden hacerlo.'}
        action={<Button variant="outline" onClick={() => navigate(`/events/${id}`)}>Ver quedada</Button>}
      />
    )
  } else {
    body = <EditForm event={event} />
  }

  return (
    <div className="event-detail">
      <Header title="Editar quedada" />
      {body}
    </div>
  )
}

/** /events/new and /events/:id/edit */
export default function EventEditor() {
  const { id } = useParams()
  return id ? <EditEvent id={id} /> : <CreateEvent />
}
