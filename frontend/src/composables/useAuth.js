/**
 * 认证状态管理 composable
 * 从 App.vue 中提取，供所有组件共享
 */
import { reactive } from 'vue'
import { getSavedAuth, saveAuth, clearAuth, request } from '../api'
import CryptoJS from 'crypto-js'

const auth = reactive({ token: '', user: null })

function sha256(str) {
  return CryptoJS.SHA256(str).toString()
}

export function useAuth() {
  async function login(username, password) {
    const pwHash = sha256(password)
    const token = btoa(username + ':' + pwHash)
    const data = await request('/api/auth/login', {
      method: 'POST',
      headers: { Authorization: 'Basic ' + token }
    })
    saveAuth(username, data.token, data, data.expiresAt)
    auth.token = data.token
    auth.user = data
    return data
  }

  async function logout(callApi = true) {
    if (callApi && auth.token) {
      try {
        await request('/api/auth/logout', { method: 'POST' })
      } catch { /* ignore */ }
    }
    clearAuth()
    auth.token = ''
    auth.user = null
  }

  function restoreAuth() {
    const saved = getSavedAuth()
    if (saved) {
      auth.token = saved.token
      auth.user = saved.user
    }
  }

  function isAdmin() {
    return auth.user?.role === '系统管理员'
  }

  return { auth, login, logout, restoreAuth, isAdmin }
}
