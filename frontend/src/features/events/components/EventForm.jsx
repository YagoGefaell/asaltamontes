import { lazy, Suspense, useState } from 'react'
import { FaLocationArrow } from 'react-icons/fa'
import Input from '../../../shared/forms/Input.jsx'
import FormError from '../../../shared/forms/FormError.jsx'
import Button from '../../../shared/forms/Button.jsx'
import Skeleton from '../../../shared/ui/Skeleton.jsx'
import { EVENT_LEVELS, EVENT_TYPES } from '../utils/eventFormat.js'
import {
  CITY_MAX, DESCRIPTION_MAX, MEETING_POINT_MAX, TITLE_MAX, browserTimeZone, roundCoordinate, validateEventForm,
} from '../utils/eventForm.js'
import { zoneLabel } from '../utils/timeZones.js'
import { useHomeArea } from '../hooks/useHomeArea.js'
import PlaceAutocomplete from './PlaceAutocomplete.jsx'
import TimeZoneSelect from './TimeZoneSelect.jsx'
import './EventForm.css'

// Below this zoom the map shows a region too wide to rank place suggestions by
const NEAR_MIN_ZOOM = 9

// The browser only gives the position on HTTPS (or localhost)
const canLocate = () => typeof window !== 'undefined' && window.isSecureContext && 'geolocation' in navigator

const LOCATE_ERRORS = {
  1: 'Has denegado el permiso de ubicación. Puedes activarlo en los ajustes del navegador.',
  default: 'No se ha podido obtener tu ubicación. Marca el punto en el mapa.',
}

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
  // Where the map should fly after a place is picked, and the area she is looking at (to rank suggestions)
  const [mapFocus, setMapFocus] = useState(null)
  const [mapView, setMapView] = useState(null)
  const [locating, setLocating] = useState(false)
  const [locateError, setLocateError] = useState(null)
  const homeArea = useHomeArea()
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
  // Rank suggestions near her point, the area she zoomed into or, failing that, the city in her profile
  const near = hasPoint
    ? { latitude: values.latitude, longitude: values.longitude }
    : mapView?.zoom >= NEAR_MIN_ZOOM ? mapView : homeArea

  const placePoint = (latitude, longitude) => {
    set({ latitude, longitude })
    setMapFocus({ latitude, longitude })
  }

  const locateMe = () => {
    setLocating(true)
    setLocateError(null)
    navigator.geolocation.getCurrentPosition(
      ({ coords }) => {
        setLocating(false)
        placePoint(roundCoordinate(coords.latitude), roundCoordinate(coords.longitude))
      },
      (error) => {
        setLocating(false)
        setLocateError(LOCATE_ERRORS[error.code] || LOCATE_ERRORS.default)
      },
      { enableHighAccuracy: true, timeout: 10000, maximumAge: 60000 },
    )
  }

  const pickPlace = (place) => {
    set({
      meetingPointName: place.name.slice(0, MEETING_POINT_MAX),
      city: place.city ? place.city.slice(0, CITY_MAX) : values.city,
      latitude: place.latitude,
      longitude: place.longitude,
    })
    setMapFocus({ latitude: place.latitude, longitude: place.longitude })
  }
  const otherZone = timeZone !== browserTimeZone()
  const moreHasError = ['distanceKm', 'elevationGainM', 'capacity', 'timezone'].some((name) => errors[name])

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
      <fieldset className="event-form__place">
        <legend>Punto de encuentro</legend>
        <PlaceAutocomplete
          value={values.meetingPointName}
          onChange={(text) => set({ meetingPointName: text })}
          onPick={pickPlace}
          near={near}
          error={errors.meetingPointName}
          maxLength={MEETING_POINT_MAX}
        />
        <Input label="Ciudad" aria-label="Ciudad" maxLength={CITY_MAX} {...field('city')} />
        <p className="event-form__hint">Elige un lugar de la lista o toca el mapa para marcar el punto. Arrastra la chincheta para ajustarlo.</p>
        {canLocate() && (
          <Button variant="ghost" size="small" className="event-form__locate" onClick={locateMe} loading={locating}>
            <FaLocationArrow aria-hidden="true" /> Usar mi ubicación
          </Button>
        )}
        <FormError message={locateError} />
        <Suspense fallback={<Skeleton height="280px" />}>
          <LocationPicker
            latitude={values.latitude}
            longitude={values.longitude}
            onChange={(latitude, longitude) => set({ latitude, longitude })}
            focus={mapFocus}
            onViewChange={setMapView}
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

      <Input
        as="textarea" rows={5} label="Descripción" aria-label="Descripción" maxLength={DESCRIPTION_MAX}
        placeholder="Ritmo, material, qué esperar…" {...field('description')}
      />

      <details className="event-form__advanced" open={moreHasError || undefined}>
        <summary>Más opciones</summary>
        <div className="event-form__more">
          <div className="event-form__row">
            <Input type="number" inputMode="decimal" step="0.1" min="0" label="Distancia (km)" aria-label="Distancia" {...field('distanceKm')} />
            <Input type="number" inputMode="numeric" step="1" min="0" label="Desnivel (m+)" aria-label="Desnivel" {...field('elevationGainM')} />
          </div>
          <Input type="number" inputMode="numeric" step="1" min="1" label="Plazas" aria-label="Plazas" placeholder="Sin límite" {...field('capacity')} />
          <TimeZoneSelect value={timeZone} onChange={(zone) => set({ timezone: zone })} error={errors.timezone} />
          <p className="event-form__hint">
            La hora pertenece al lugar de la quedada: elige su zona si no es la de tu dispositivo.
          </p>
        </div>
      </details>

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
