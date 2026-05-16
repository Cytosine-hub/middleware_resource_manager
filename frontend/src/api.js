const AUTH_KEY = 'mrm.basicAuth'
const USER_KEY = 'mrm.user'

export function getSavedAuth() {
  const token = sessionStorage.getItem(AUTH_KEY)
  const user = sessionStorage.getItem(USER_KEY)
  return token && user ? { token, user: JSON.parse(user) } : null
}

export function saveAuth(username, password, user) {
  const token = btoa(`${username}:${password}`)
  sessionStorage.setItem(AUTH_KEY, token)
  sessionStorage.setItem(USER_KEY, JSON.stringify(user))
  return token
}

export function clearAuth() {
  sessionStorage.removeItem(AUTH_KEY)
  sessionStorage.removeItem(USER_KEY)
}

export function fileUrl(path) {
  return path
}

export async function request(path, options = {}) {
  const token = options.token || sessionStorage.getItem(AUTH_KEY)
  const headers = new Headers(options.headers || {})

  if (token) {
    headers.set('Authorization', `Basic ${token}`)
  }

  let body = options.body
  if (body && !(body instanceof FormData) && typeof body !== 'string') {
    headers.set('Content-Type', 'application/json')
    body = JSON.stringify(body)
  }

  const response = await fetch(path, {
    method: options.method || 'GET',
    headers,
    body
  })

  if (!response.ok) {
    let message = response.statusText || 'Request failed'
    try {
      const payload = await response.json()
      const fieldErrors = payload.fieldErrors ? Object.values(payload.fieldErrors).filter(Boolean) : []
      message = fieldErrors.length ? fieldErrors.join('；') : (payload.message || message)
    } catch (error) {
      // Keep the HTTP status text when the backend did not return JSON.
    }
    const error = new Error(message)
    error.status = response.status
    throw error
  }

  if (response.status === 204) {
    return null
  }

  const text = await response.text()
  if (!text) {
    return null
  }
  return JSON.parse(text)
}
