export type MediaItem = {
  id: number
  name: string
  mime: string
  video: boolean
  date: number
  size: number
  width: number
  height: number
  duration: number
  bucket: string
  folder: string
  live: boolean
  liveVideoId: number
  thumb: string
  image: string
  videoUrl: string | null
  download: string
}

export type Album = {
  key: string
  smart?: string
  bucket?: number
  title: string
  count: number
  type: 'smart' | 'folder'
  cover: string | null
}

export type Session = {
  authenticated: boolean
  name?: string
  itemCount?: number
  server?: string
}

export type MediaPage = {
  total: number
  offset: number
  items: MediaItem[]
}

export type MediaQuery = {
  smart?: string
  bucket?: number
  type?: 'all' | 'image' | 'video' | 'live'
  q?: string
  offset: number
  limit: number
}

export class ApiError extends Error {
  status: number
  constructor(status: number, message: string) {
    super(message)
    this.status = status
  }
}

async function request<T>(url: string, init?: RequestInit): Promise<T> {
  const response = await fetch(url, {
    credentials: 'same-origin',
    ...init,
    headers: {
      Accept: 'application/json',
      ...(init?.headers ?? {}),
    },
  })
  const text = await response.text()
  let payload: any = null
  try {
    payload = text ? JSON.parse(text) : null
  } catch {
    payload = null
  }
  if (!response.ok) {
    throw new ApiError(response.status, payload?.error ?? `Request failed (${response.status})`)
  }
  return payload as T
}

export const api = {
  session: () => request<Session>('/api/session'),
  login: (pin: string) =>
    request<Session>('/api/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ pin }),
    }),
  logout: () => request<Session>('/api/logout', { method: 'POST' }),
  media: (query: MediaQuery) => {
    const params = new URLSearchParams()
    if (query.smart) params.set('smart', query.smart)
    if (query.bucket) params.set('bucket', String(query.bucket))
    if (query.type && query.type !== 'all') params.set('type', query.type)
    if (query.q) params.set('q', query.q)
    params.set('offset', String(query.offset))
    params.set('limit', String(query.limit))
    return request<MediaPage>(`/api/media?${params.toString()}`)
  },
  albums: () => request<{ albums: Album[] }>('/api/albums'),
}

export function uploadFile(
  file: File,
  onProgress: (percent: number) => void,
): Promise<{ saved: boolean; id: number; name: string }> {
  return new Promise((resolve, reject) => {
    const xhr = new XMLHttpRequest()
    xhr.open('POST', '/api/upload')
    xhr.withCredentials = true
    xhr.responseType = 'text'
    xhr.upload.onprogress = (event) => {
      if (event.lengthComputable) {
        onProgress(Math.round((event.loaded / event.total) * 100))
      }
    }
    xhr.onerror = () => reject(new Error('Network error while uploading'))
    xhr.onabort = () => reject(new Error('Upload cancelled'))
    xhr.onload = () => {
      if (xhr.status >= 200 && xhr.status < 300) {
        try {
          resolve(JSON.parse(xhr.responseText))
        } catch {
          resolve({ saved: true, id: 0, name: file.name })
        }
      } else {
        let message = `Upload failed (${xhr.status})`
        try {
          message = JSON.parse(xhr.responseText)?.error ?? message
        } catch {
          /* keep default */
        }
        reject(new Error(message))
      }
    }
    const body = new FormData()
    body.append('file', file, file.name)
    body.append('name', file.name)
    xhr.send(body)
  })
}

export function formatDuration(ms: number): string {
  if (!ms || ms <= 0) return ''
  const total = Math.round(ms / 1000)
  const seconds = total % 60
  const minutes = Math.floor(total / 60) % 60
  const hours = Math.floor(total / 3600)
  if (hours > 0) return `${hours}:${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`
  return `${minutes}:${String(seconds).padStart(2, '0')}`
}

export function formatBytes(bytes: number): string {
  if (!bytes || bytes <= 0) return '—'
  const units = ['B', 'KB', 'MB', 'GB', 'TB']
  let value = bytes
  let unit = 0
  while (value >= 1024 && unit < units.length - 1) {
    value /= 1024
    unit++
  }
  return `${value >= 10 || unit === 0 ? Math.round(value) : value.toFixed(1)} ${units[unit]}`
}

export function formatDate(ms: number): string {
  if (!ms) return ''
  try {
    return new Date(ms).toLocaleString(undefined, {
      year: 'numeric',
      month: 'short',
      day: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    })
  } catch {
    return ''
  }
}
