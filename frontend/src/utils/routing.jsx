import { lazy, Suspense } from 'react'
import Loading from '../shared/components/Loading.jsx'

/**
 * Higher-order component for lazy loading with loading state
 * @param {Function} importFunc - Dynamic import function
 * @param {Object} fallback - Custom loading component
 */
export const lazyLoad = (importFunc, fallback = <Loading text="Cargando página..." />) => {
  const LazyComponent = lazy(importFunc)

  const WrappedComponent = (props) => (
    <Suspense fallback={fallback}>
      <LazyComponent {...props} />
    </Suspense>
  )

  // Set display name for better debugging
  WrappedComponent.displayName = `LazyLoad(${LazyComponent.displayName || LazyComponent.name || 'Component'})`

  return WrappedComponent
}

/**
 * Route configuration utilities
 */
export const createProtectedRoute = (element, guards = []) => {
  return guards.reduce((wrappedElement, Guard) => (
    <Guard>{wrappedElement}</Guard>
  ), element)
}

/**
 * Navigation utilities
 */
export const navigationHelpers = {
  goBack: (navigate, fallback = '/') => {
    const canGoBack = window.history.length > 1
    if (canGoBack) {
      navigate(-1)
    } else {
      navigate(fallback)
    }
  },

  goToWithReplace: (navigate, path) => {
    navigate(path, { replace: true })
  },

  goToWithState: (navigate, path, state) => {
    navigate(path, { state })
  },
}