/**
 * Cookie debugging utilities
 */

/**
 * Get all cookies as an object with detailed debugging
 */
export function getAllCookies() {
  const rawCookies = document.cookie
  console.log('🔍 Raw document.cookie:', rawCookies)
  
  const cookies = {}
  if (!rawCookies) {
    console.warn('❌ document.cookie is empty')
    return cookies
  }
  
  document.cookie.split(';').forEach(cookie => {
    const [name, value] = cookie.trim().split('=')
    if (name) {
      cookies[name] = value ? decodeURIComponent(value) : ''
      console.log(`🍪 Found cookie: ${name} = ${value ? '[PRESENT]' : '[EMPTY]'}`)
    }
  })
  return cookies
}

/**
 * Debug utility to inspect all aspects of cookie storage
 */
export function inspectCookieStorage() {
  console.group('🔬 Complete Cookie Inspection')
  
  // Check raw document.cookie
  console.log('Raw document.cookie:', document.cookie)
  
  // Check specific auth cookies
  const authCookies = checkAuthCookies()
  console.log('Auth cookies status:', authCookies)
  
  // Check localStorage for comparison
  console.log('localStorage keys:', Object.keys(localStorage))
  
  // Check if we're on the right domain/port
  console.log('Current location:', {
    hostname: window.location.hostname,
    port: window.location.port,
    protocol: window.location.protocol
  })
  
  console.groupEnd()
  return authCookies
}

// Make debugging function available globally for console use
if (typeof window !== 'undefined') {
  window.debugCookies = inspectCookieStorage;
  console.log('🛠️ Cookie debugging available: run debugCookies() in console');
  
  // Add cookie change monitoring
  let lastCookieState = document.cookie;
  
  window.startCookieMonitoring = () => {
    const interval = setInterval(() => {
      const currentCookies = document.cookie;
      if (currentCookies !== lastCookieState) {
        console.log('🔄 Cookie change detected!');
        console.log('Previous:', lastCookieState || 'NO COOKIES');
        console.log('Current:', currentCookies || 'NO COOKIES');
        
        const authStatus = checkAuthCookies();
        if (authStatus.hasAccessToken || authStatus.hasRefreshToken) {
          console.log('✅ Auth cookies are now present!', authStatus);
        }
        
        lastCookieState = currentCookies;
      }
    }, 50); // Check every 50ms
    
    console.log('🔄 Cookie monitoring started (checking every 50ms)');
    
    // Stop after 5 seconds
    setTimeout(() => {
      clearInterval(interval);
      console.log('🔄 Cookie monitoring stopped');
    }, 5000);
    
    return interval;
  };
  
  console.log('🛠️ Cookie monitoring available: run startCookieMonitoring() in console');
}

/**
 * Check if authentication cookies exist with enhanced debugging
 */
export function checkAuthCookies() {
  const cookies = getAllCookies()
  const hasAccessToken = 'accesToken' in cookies
  const hasRefreshToken = 'refreshToken' in cookies
  
  const result = {
    hasAccessToken,
    hasRefreshToken,
    accessToken: cookies.accesToken || null,
    refreshToken: cookies.refreshToken || null,
    allCookies: cookies,
    totalCookieCount: Object.keys(cookies).length
  }
  
  // Log detailed status
  console.log('🍪 Cookie check results:', {
    totalCookies: result.totalCookieCount,
    hasAccess: hasAccessToken ? '✅' : '❌',
    hasRefresh: hasRefreshToken ? '✅' : '❌',
    cookieNames: Object.keys(cookies)
  })
  
  return result
}

/**
 * Debug cookie information after authentication
 */
export function debugAuthCookies(operation = 'operation') {
  const authStatus = checkAuthCookies()
  
  console.group(`🍪 Cookie Status After ${operation}`)
  console.log('Access Token Present:', authStatus.hasAccessToken ? '✅' : '❌')
  console.log('Refresh Token Present:', authStatus.hasRefreshToken ? '✅' : '❌')
  
  if (Object.keys(authStatus.allCookies).length === 0) {
    console.warn('No cookies found at all. This may indicate:')
    console.warn('- Backend is not setting cookies')
    console.warn('- CORS configuration issue')
    console.warn('- Cookie domain/path mismatch')
  } else {
    console.log('All cookies:', authStatus.allCookies)
  }
  console.groupEnd()
  
  return authStatus
}

/**
 * Clear all authentication cookies
 */
export function clearAuthCookies() {
  // Clear cookies by setting them to expire in the past
  document.cookie = 'accesToken=; expires=Thu, 01 Jan 1970 00:00:00 UTC; path=/;'
  document.cookie = 'refreshToken=; expires=Thu, 01 Jan 1970 00:00:00 UTC; path=/;'
}