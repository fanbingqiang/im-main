import axios from 'axios'

const api = axios.create({
  baseURL: '/api',
  timeout: 10000,
  withCredentials: true
})

api.interceptors.response.use(
  res => res,
  err => {
    if (err.response?.status === 401) {
      window.location.href = '/login'
    }
    return Promise.reject(err)
  }
)

export function sendCode(phone) {
  return api.post('/user/sendSMSCode', phone, { headers: { 'Content-Type': 'text/plain' } })
}

export function mobileLogin(mobile, code) {
  return api.post('/user/mobileLogin', { mobile, code })
}

export function getUserById() {
  return api.get('/user/getUserById')
}

export function getOnlineRooms() {
  return api.get('/im/room/list')
}

export function getRoomInfo(roomId) {
  return api.get('/im/room/info', { params: { roomId } })
}

export function getOnlineCount(roomId) {
  return api.get('/im/room/onlineCount', { params: { roomId } })
}

export function createRoom(roomName) {
  return api.post('/im/room/create', null, { params: { roomName } })
}

export function getMessageHistory(roomId, limit = 50) {
  return api.get('/im/message/history', { params: { roomId, limit } })
}

// 直播
export function getLiveList() {
  return api.get('/live/list')
}

export function getLiveInfo(streamId) {
  return api.get('/live/info', { params: { streamId } })
}

export function createLive(title, roomName) {
  return api.post('/live/create', null, { params: { title, roomName } })
}

export function startLive(streamId) {
  return api.post('/live/start', null, { params: { streamId } })
}

export function endLive(streamId) {
  return api.post('/live/end', null, { params: { streamId } })
}

// 点播视频
export function getVodList(page = 1, size = 20) {
  return api.get('/vod/list', { params: { page, size } })
}

export function searchVideos(keyword) {
  return api.get('/vod/search', { params: { keyword } })
}
