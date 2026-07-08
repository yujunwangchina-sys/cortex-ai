import Cookies from 'js-cookie'

const TokenKey = 'Admin-Token'

// 内存中的令牌，跨域 iframe 下 cookie/localStorage 可能被浏览器拦截，
// 用模块级变量保证同一会话内 getToken 一定能取到。
let _token = null

export function getToken() {
  return _token || Cookies.get(TokenKey) || localStorage.getItem(TokenKey)
}

export function setToken(token) {
  _token = token
  Cookies.set(TokenKey, token)
  localStorage.setItem(TokenKey, token)
  return token
}

export function removeToken() {
  _token = null
  Cookies.remove(TokenKey)
  localStorage.removeItem(TokenKey)
}