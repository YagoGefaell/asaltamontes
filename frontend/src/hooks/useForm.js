import { useState, useCallback } from 'react'

/**
 * Hook for managing form state with validation
 */
export const useForm = (initialValues = {}, validationRules = {}) => {
  const [values, setValues] = useState(initialValues)
  const [errors, setErrors] = useState({})
  const [touched, setTouched] = useState({})
  const [isSubmitting, setIsSubmitting] = useState(false)

  const setValue = useCallback((name, value) => {
    setValues(prev => ({ ...prev, [name]: value }))
    
    // Clear error when user starts typing
    if (errors[name]) {
      setErrors(prev => ({ ...prev, [name]: null }))
    }
  }, [errors])

  const setError = useCallback((name, error) => {
    setErrors(prev => ({ ...prev, [name]: error }))
  }, [])

  const markFieldAsTouched = useCallback((name) => {
    setTouched(prev => ({ ...prev, [name]: true }))
  }, [])

  const validate = useCallback(() => {
    const newErrors = {}
    
    Object.entries(validationRules).forEach(([field, rules]) => {
      for (const rule of rules) {
        const error = rule(values[field], values)
        if (error) {
          newErrors[field] = error
          break
        }
      }
    })

    setErrors(newErrors)
    return Object.keys(newErrors).length === 0
  }, [values, validationRules])

  const handleChange = useCallback((event) => {
    const { name, value, type, checked } = event.target
    const newValue = type === 'checkbox' ? checked : value
    setValue(name, newValue)
  }, [setValue])

  const handleBlur = useCallback((event) => {
    const { name } = event.target
    markFieldAsTouched(name)
    
    // Validate field on blur if there are rules
    if (validationRules[name]) {
      const fieldErrors = validationRules[name]
      for (const rule of fieldErrors) {
        const error = rule(values[name], values)
        if (error) {
          setError(name, error)
          break
        }
      }
    }
  }, [values, validationRules, setError, markFieldAsTouched])

  const handleSubmit = useCallback(async (onSubmit) => {
    setIsSubmitting(true)
    
    try {
      const isValid = validate()
      if (isValid) {
        await onSubmit(values)
      }
    } catch (error) {
      // Handle submission errors
      if (error.errors) {
        setErrors(error.errors)
      }
      throw error
    } finally {
      setIsSubmitting(false)
    }
  }, [values, validate])

  const reset = useCallback(() => {
    setValues(initialValues)
    setErrors({})
    setTouched({})
    setIsSubmitting(false)
  }, [initialValues])

  return {
    values,
    errors,
    touched,
    isSubmitting,
    setValue,
    setError,
    setTouched: markFieldAsTouched,
    handleChange,
    handleBlur,
    handleSubmit,
    validate,
    reset,
  }
}