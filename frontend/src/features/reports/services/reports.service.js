import { apiClient } from '../../../lib/api-client.js'

export const REPORT_DETAILS_MAX = 1000

export const REPORT_REASONS = [
  { value: 'SPAM', label: 'Spam o publicidad' },
  { value: 'HARASSMENT', label: 'Acoso o intimidación' },
  { value: 'HATE', label: 'Odio o discriminación' },
  { value: 'INAPPROPRIATE', label: 'Contenido inapropiado' },
  { value: 'DANGEROUS', label: 'Peligroso o ilegal' },
  { value: 'FAKE_ACCOUNT', label: 'Cuenta falsa' },
  { value: 'OTHER', label: 'Otro motivo' },
]

/**
 * Reports something (T23b). `targetType`: USER | MESSAGE | POST | COMMENT | EVENT | COMMUNITY.
 * `target`: username (USER), slug (COMMUNITY) or public uuid (the rest). `details` is required for OTHER.
 * Keeps status and retryAfter so the sheet can tell 404, 409 and 429 apart.
 */
export async function createReportRequest({ targetType, target, reason, details }) {
  try {
    return await apiClient.post('/api/reports', { targetType, target, reason, details: details?.trim() || null })
  } catch (error) {
    const wrapped = new Error(error.message || 'No se ha podido enviar la denuncia')
    wrapped.status = error.status
    wrapped.retryAfter = error.retryAfter
    throw wrapped
  }
}
