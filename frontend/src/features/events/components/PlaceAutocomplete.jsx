import { useEffect, useId, useRef, useState } from 'react'
import { FaMapMarkerAlt } from 'react-icons/fa'
import Input from '../../../shared/forms/Input.jsx'
import { searchPlaces } from '../services/places.service.js'
import './PlaceAutocomplete.css'

const MIN_CHARS = 3
const DEBOUNCE_MS = 350

/**
 * "Nombre del lugar" with suggestions as she types (combobox). She can keep a free text
 * ("Parking del Teleférico") or pick a suggestion, which calls `onPick(place)` with its name, city and coordinates.
 * `near` ranks the suggestions around the point or the map area she is looking at.
 */
export default function PlaceAutocomplete({ value, onChange, onPick, near = null, error, maxLength }) {
  const listId = useId()
  const [places, setPlaces] = useState([])
  const [status, setStatus] = useState('idle') // idle | loading | done | error
  const [open, setOpen] = useState(false)
  const [active, setActive] = useState(-1)
  // Typing searches; picking a suggestion writes the field too, and that must not search again
  const typedRef = useRef(false)
  const nearRef = useRef(near)
  useEffect(() => {
    nearRef.current = near
  }, [near])

  useEffect(() => {
    if (!typedRef.current) return undefined
    const query = value.trim()
    // Short queries don't search; the list stays hidden until there are enough letters
    if (query.length < MIN_CHARS) return undefined
    const controller = new AbortController()
    const timer = setTimeout(async () => {
      setStatus('loading')
      try {
        const found = await searchPlaces(query, { near: nearRef.current, signal: controller.signal })
        setPlaces(found)
        setActive(-1)
        setStatus('done')
      } catch (e) {
        if (e.name !== 'AbortError') setStatus('error')
      }
    }, DEBOUNCE_MS)
    return () => {
      clearTimeout(timer)
      controller.abort()
    }
  }, [value])

  const pick = (place) => {
    typedRef.current = false
    setOpen(false)
    setPlaces([])
    setStatus('idle')
    onPick(place)
  }

  const handleKeyDown = (e) => {
    if (!open || places.length === 0) return
    if (e.key === 'ArrowDown' || e.key === 'ArrowUp') {
      e.preventDefault()
      const step = e.key === 'ArrowDown' ? 1 : -1
      setActive((i) => (i + step + places.length) % places.length)
    } else if (e.key === 'Enter' && active >= 0) {
      e.preventDefault() // pick, don't submit the form
      pick(places[active])
    } else if (e.key === 'Escape') {
      setOpen(false)
    }
  }

  const showList = open && value.trim().length >= MIN_CHARS && status !== 'idle'
  const optionId = (i) => `${listId}-${i}`

  return (
    <div className="place-search">
      <Input
        label="Nombre del lugar"
        aria-label="Lugar"
        placeholder="Busca un sitio o escribe el nombre"
        maxLength={maxLength}
        value={value}
        error={error}
        role="combobox"
        aria-autocomplete="list"
        aria-expanded={showList}
        aria-controls={listId}
        aria-activedescendant={showList && active >= 0 ? optionId(active) : undefined}
        autoComplete="off"
        onChange={(e) => {
          typedRef.current = true
          setOpen(true)
          onChange(e.target.value)
        }}
        onFocus={() => setOpen(true)}
        onBlur={() => setOpen(false)}
        onKeyDown={handleKeyDown}
      />
      {showList && (
        <div className="place-search__panel">
          {status === 'loading' && places.length === 0 && <p className="place-search__status" role="status">Buscando…</p>}
          {status === 'error' && <p className="place-search__status" role="status">No se ha podido buscar. Marca el punto en el mapa.</p>}
          {status === 'done' && places.length === 0 && (
            <p className="place-search__status" role="status">Sin resultados. Puedes dejar el nombre y marcar el punto en el mapa.</p>
          )}
          <ul id={listId} role="listbox" aria-label="Lugares encontrados" className="place-search__list">
            {places.map((place, i) => (
              <li
                key={place.id}
                id={optionId(i)}
                role="option"
                aria-selected={i === active}
                className="place-search__option"
                // Keep the focus in the field, so the blur does not close the list before the click
                onMouseDown={(e) => e.preventDefault()}
                onClick={() => pick(place)}
              >
                <FaMapMarkerAlt className="place-search__icon" aria-hidden="true" />
                <span className="place-search__text">
                  <span className="place-search__name">{place.name}</span>
                  {place.detail && <span className="place-search__detail">{place.detail}</span>}
                </span>
              </li>
            ))}
          </ul>
          {places.length > 0 && <p className="place-search__credit">Lugares de OpenStreetMap</p>}
        </div>
      )}
    </div>
  )
}
