import './Loading.css'

/**
 * Loading spinner component with customizable size and text
 * @param {Object} props
 * @param {'small' | 'medium' | 'large'} props.size - Spinner size
 * @param {string} props.text - Loading text to display  
 * @param {boolean} props.overlay - Show as overlay
 * @param {string} props.className - Additional CSS classes
 */
export default function Loading({ 
  size = 'medium', 
  text = 'Cargando...', 
  overlay = false,
  className = '' 
}) {
  const baseClass = 'loading'
  const sizeClass = `loading--${size}`
  const overlayClass = overlay ? 'loading--overlay' : ''
  
  const combinedClasses = [baseClass, sizeClass, overlayClass, className]
    .filter(Boolean)
    .join(' ')

  return (
    <div className={combinedClasses} role="status" aria-label={text}>
      <div className="loading__spinner">
        <div className="loading__spinner-inner"></div>
      </div>
      {text && (
        <span className="loading__text" aria-live="polite">
          {text}
        </span>
      )}
    </div>
  )
}