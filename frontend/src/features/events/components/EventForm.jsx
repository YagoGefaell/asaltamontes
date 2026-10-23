import { lazy, Suspense, useState } from 'react'
import Input from '../../../shared/forms/Input.jsx'
import FormError from '../../../shared/forms/FormError.jsx'
import Button from '../../../shared/forms/Button.jsx'
import Skeleton from '../../../shared/ui/Skeleton.jsx'
import { EVENT_LEVELS, EVENT_TYPES } from '../utils/eventFormat.js'
import {
  CITY_MAX, DESCRIPTION_MAX, MEETING_POINT_MAX, TITLE_MAX, browserTimeZone, validateEventForm,
} from '../utils/eventForm.js'
import { zoneLabel } from '../utils/timeZones.js'
import TimeZoneSelect from './TimeZoneSelect.jsx'
import './EventForm.css'

// Leaflet only downloads when the form is opened
const LocationPicker = lazy(() => import('./LocationPicker.jsx'))

/**
 * Create / edit form. Controlled by the page through `values` / `onChange`; the form validates on submit
 * (mirror of the API) and then live. `serverErrors` are the API's field errors (400).
 * `communities` are the ones she can organize in (create mode only; the community of an event cannot change).
 */
export default function EventForm({
  values, onChange, onSubmit, submitting = false, mode = 'create', communities = [], communityName = null,
  original = null, serverErrors = {}, submitLabel,
}) {
  const timeZone = values.timezone || browserTimeZone()
  const [submitted, setSubmitted] = useState(false)
  const errors = submitted ? { ...serverErrors, ...validateEventForm(values, { timeZone, original }) } : serverErrors
  const set = (patch) => onChange({ ...values, ...patch })
  const field = (name) => ({
    value: values[name],
    onChange: (e) => set({ [name]: e.target.value }),
    error: errors[name],
  })

  const handleSubmit = (event) => {
    event.preventDefault()
    setSubmitted(true)
    if (Object.keys(validateEventForm(values, { timeZone, original })).length === 0) onSubmit()
  }

  const hasPoint = values.latitude != null && values.longitude != null
  const otherZone = timeZone !== browserTimeZone()

  return (
    <form className="event-form" onSubmit={handleSubmit} noValidate>
      <Input label="Título" aria-label="Título" maxLength={TITLE_MAX} placeholder="Ej.: Rodaje suave por la Casa de Campo" {...field('title')} />

      <div className="event-form__row">
        <Input as="select" label="Tipo" aria-label="Tipo" {...field('type')}>
          {Object.entries(EVENT_TYPES).map(([value, label]) => <option key={value} value={value}>{label}</option>)}
        </Input>
        <Input as="select" label="Nivel" aria-label="Nivel" {...field('level')}>
          <option value="">Cualquier nivel</option>
          {Object.entries(EVENT_LEVELS).map(([value, label]) => <option key={value} value={value}>{label}</option>)}
        </Input>
      </div>

      <div className="event-form__row">
        <Input type="datetime-local" label="Empieza" aria-label="Empieza" {...field('startsAt')} />
        <Input type="datetime-local" label="Termina (opcional)" aria-label="Termina" {...field('endsAt')} />
      </div>
      <p className="event-form__hint">
        Hora local{otherZone ? ` de ${zoneLabel(timeZone)} (la zona de la quedada)` : ` (${zoneLabel(timeZone)})`}.
      </p>
      <details className="event-form__advanced">
        <summary>Avanzado</summary>
        <TimeZoneSelect value={timeZone} onChange={(zone) => set({ timezone: zone })} error={errors.timezone} />
        <p className="event-form__hint">
          La hora pertenece al lugar de la quedada: elige su zona si no es la de tu dispositivo.
        </p>
      </details>

      <fieldset className="event-form__place">
        <legend>Punto de encuentro</legend>
        <Input label="Nombre del lugar" aria-label="Lugar" maxLength={MEETING_POINT_MAX} placeholder="Ej.: Parking del Teleférico" {...field('meetingPointName')} />
        <Input label="Ciudad" aria-label="Ciudad" maxLength={CITY_MAX} {...field('city')} />
        <p className="event-form__hint">Toca el mapa para marcar el punto y arrastra la chincheta para ajustarlo.</p>
        <Suspense fallback={<Skeleton height="280px" />}>
          <LocationPicker
            latitude={values.latitude}
            longitude={values.longitude}
            onChange={(latitude, longitude) => set({ latitude, longitude })}
          />
        </Suspense>
        {hasPoint && (
          <div className="event-form__point">
            <span>{values.latitude.toFixed(5)}, {values.longitude.toFixed(5)}</span>
            <Button variant="ghost" size="small" onClick={() => set({ latitude: null, longitude: null })}>Quitar punto</Button>
          </div>
        )}
        <FormError message={errors.location} />
      </fieldset>

      <div className="event-form__row event-form__row--3">
        <Input type="number" inputMode="decimal" step="0.1" min="0" label="Distancia (km)" aria-label="Distancia" {...field('distanceKm')} />
        <Input type="number" inputMode="numeric" step="1" min="0" label="Desnivel (m+)" aria-label="Desnivel" {...field('elevationGainM')} />
        <Input type="number" inputMode="numeric" step="1" min="1" label="Plazas" aria-label="Plazas" placeholder="Sin límite" {...field('capacity')} />
      </div>

      <Input
        as="textarea" rows={5} label="Descripción" aria-label="Descripción" maxLength={DESCRIPTION_MAX}
        placeholder="Ritmo, material, qué esperar…" {...field('description')}
      />

      {mode === 'create' && communities.length > 0 && (
        <fieldset className="event-form__place">
          <legend>Comunidad</legend>
          <Input
            as="select" label="Organizar en" aria-label="Comunidad" value={values.communityId} error={errors.communityId}
            onChange={(e) => set({ communityId: e.target.value, visibility: e.target.value ? values.visibility : 'PUBLIC' })}
          >
            <option value="">Ninguna (quedada abierta)</option>
            {communities.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
          </Input>
          {values.communityId && (
            <Input as="select" label="Quién puede verla" aria-label="Visibilidad" {...field('visibility')}>
              <option value="PUBLIC">Todas</option>
              <option value="COMMUNITY">Solo miembros de la comunidad</option>
            </Input>
          )}
        </fieldset>
      )}
      {mode === 'edit' && communityName && (
        <p className="event-form__hint">Comunidad: {communityName}. La comunidad y la visibilidad no se pueden cambiar.</p>
      )}

      <Button type="submit" loading={submitting}>{submitLabel ?? (mode === 'edit' ? 'Guardar cambios' : 'Crear quedada')}</Button>
    </form>
  )
}
