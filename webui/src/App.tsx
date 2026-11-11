import { useCallback, useEffect, useRef, useState } from 'react'
import {
  Album,
  MediaItem,
  Session,
  api,
  formatBytes,
  formatDate,
  formatDuration,
  uploadFile,
} from './api'
import { useSpatialNav } from './useSpatialNav'

type Tab = 'photos' | 'albums'
type MediaType = 'all' | 'image' | 'video' | 'live'

type Filter = {
  smart?: string
  bucket?: number
  title?: string
  type: MediaType
  q: string
}

export default function App() {
  const [session, setSession] = useState<Session | null>(null)
  const [checking, setChecking] = useState(true)

  useEffect(() => {
    let cancelled = false
    api
      .session()
      .then((value) => {
        if (!cancelled) setSession(value)
      })
      .catch(() => {
        if (!cancelled) setSession({ authenticated: false })
      })
      .finally(() => {
        if (!cancelled) setChecking(false)
      })
    return () => {
      cancelled = true
    }
  }, [])

  if (checking) return <Splash />
  if (!session?.authenticated) {
    return <Login onAuthenticated={(value) => setSession(value)} />
  }
  return <Gallery session={session} onLogout={() => setSession({ authenticated: false })} />
}

function Splash() {
  return (
    <div className="splash">
      <div className="brand large">
        Luma<span className="brand-dot" />
      </div>
      <div className="spinner" />
    </div>
  )
}

function Login({ onAuthenticated }: { onAuthenticated: (session: Session) => void }) {
  const [pin, setPin] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const busyRef = useRef(false)

  const submit = useCallback(
    async (value: string) => {
      if (busyRef.current || value.length < 4) return
      busyRef.current = true
      setBusy(true)
      setError(null)
      try {
        const result = await api.login(value)
        onAuthenticated({ ...result, authenticated: true })
      } catch (err) {
        setError(err instanceof Error ? err.message : 'Wrong PIN')
        setPin('')
      } finally {
        busyRef.current = false
        setBusy(false)
      }
    },
    [onAuthenticated],
  )

  const pressDigit = useCallback(
    (digit: string) => {
      if (busyRef.current) return
      setError(null)
      setPin((current) => {
        if (current.length >= 6) return current
        const next = current + digit
        if (next.length === 6) void submit(next)
        return next
      })
    },
    [submit],
  )

  useEffect(() => {
    function onKeyDown(event: KeyboardEvent) {
      if (/^[0-9]$/.test(event.key)) {
        event.preventDefault()
        pressDigit(event.key)
      } else if (event.key === 'Backspace') {
        event.preventDefault()
        setPin((current) => current.slice(0, -1))
      } else if (event.key === 'Enter') {
        event.preventDefault()
        setPin((current) => {
          void submit(current)
          return current
        })
      }
    }
    window.addEventListener('keydown', onKeyDown)
    return () => window.removeEventListener('keydown', onKeyDown)
  }, [pressDigit, submit])

  return (
    <div className="login">
      <div className="login-card">
        <div className="brand large">
          Luma<span className="brand-dot" />
        </div>
        <p className="login-lead">
          Enter the PIN shown under <strong>Settings → Web &amp; TV</strong> on your phone.
        </p>

        <div className={`pin-dots${error ? ' error' : ''}`} aria-label="PIN entry">
          {Array.from({ length: 6 }).map((_, index) => (
            <span key={index} className={index < pin.length ? 'dot filled' : 'dot'} />
          ))}
        </div>

        <div className="login-error" role="alert">
          {error ?? (busy ? 'Checking…' : '')}
        </div>

        <div className="keypad">
          {['1', '2', '3', '4', '5', '6', '7', '8', '9'].map((digit) => (
            <button
              key={digit}
              className="key"
              onClick={() => pressDigit(digit)}
              disabled={busy}
            >
              {digit}
            </button>
          ))}
          <button className="key subtle" onClick={() => setPin('')} disabled={busy}>
            Clear
          </button>
          <button className="key" onClick={() => pressDigit('0')} disabled={busy}>
            0
          </button>
          <button
            className="key subtle"
            onClick={() => setPin((current) => current.slice(0, -1))}
            disabled={busy}
            aria-label="Backspace"
          >
            ⌫
          </button>
        </div>

        <p className="login-hint">The PIN keeps your gallery private on the local network.</p>
      </div>
    </div>
  )
}

function Gallery({ session, onLogout }: { session: Session; onLogout: () => void }) {
  const [tab, setTab] = useState<Tab>('photos')
  const [filter, setFilter] = useState<Filter>({ type: 'all', q: '' })
  const [searchText, setSearchText] = useState('')
  const [items, setItems] = useState<MediaItem[]>([])
  const [total, setTotal] = useState(0)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [reloadKey, setReloadKey] = useState(0)
  const [albums, setAlbums] = useState<Album[]>([])
  const [viewerIndex, setViewerIndex] = useState<number | null>(null)
  const [dragging, setDragging] = useState(false)
  const [upload, setUpload] = useState<{
    done: number
    total: number
    name: string
    percent: number
  } | null>(null)
  const [toast, setToast] = useState<string | null>(null)

  const offsetRef = useRef(0)
  const loadingRef = useRef(false)
  const requestRef = useRef(0)
  const fileInputRef = useRef<HTMLInputElement | null>(null)
  const toastTimerRef = useRef<number | undefined>(undefined)

  useSpatialNav(viewerIndex === null)

  function showToast(message: string) {
    setToast(message)
    window.clearTimeout(toastTimerRef.current)
    toastTimerRef.current = window.setTimeout(() => setToast(null), 3_200)
  }

  useEffect(() => {
    return () => window.clearTimeout(toastTimerRef.current)
  }, [])

  // Debounced search.
  useEffect(() => {
    const timer = window.setTimeout(() => {
      setFilter((current) =>
        current.q === searchText ? current : { ...current, q: searchText },
      )
    }, 300)
    return () => window.clearTimeout(timer)
  }, [searchText])

  const loadPage = useCallback(
    async (reset: boolean) => {
      if (loadingRef.current) return
      loadingRef.current = true
      setLoading(true)
      const requestId = ++requestRef.current
      const offset = reset ? 0 : offsetRef.current
      try {
        const page = await api.media({
          smart: filter.smart,
          bucket: filter.bucket,
          type: filter.type,
          q: filter.q,
          offset,
          limit: 150,
        })
        if (requestId !== requestRef.current) return
        setItems((current) => (reset ? page.items : [...current, ...page.items]))
        offsetRef.current = offset + page.items.length
        setTotal(page.total)
        setError(null)
      } catch (err) {
        if (requestId === requestRef.current) {
          setError(err instanceof Error ? err.message : 'Could not load media')
        }
      } finally {
        if (requestId === requestRef.current) {
          loadingRef.current = false
          setLoading(false)
        }
      }
    },
    [filter],
  )

  useEffect(() => {
    offsetRef.current = 0
    requestRef.current++
    loadingRef.current = false
    setItems([])
    setTotal(0)
    void loadPage(true)
  }, [filter, reloadKey, loadPage])

  useEffect(() => {
    window.scrollTo({ top: 0 })
  }, [filter])

  useEffect(() => {
    if (tab !== 'albums') return
    let cancelled = false
    api
      .albums()
      .then((value) => {
        if (!cancelled) setAlbums(value.albums)
      })
      .catch(() => {
        /* the media list already reports connection problems */
      })
    return () => {
      cancelled = true
    }
  }, [tab, reloadKey])

  useEffect(() => {
    function onScroll() {
      if (loadingRef.current || tab !== 'photos') return
      if (items.length >= total) return
      const element = document.documentElement
      const remaining = element.scrollHeight - element.scrollTop - element.clientHeight
      if (remaining < 900) void loadPage(false)
    }
    window.addEventListener('scroll', onScroll, { passive: true })
    return () => window.removeEventListener('scroll', onScroll)
  }, [items.length, total, loadPage, tab])

  useEffect(() => {
    if (viewerIndex === null) return
    if (viewerIndex >= items.length - 4 && items.length < total) void loadPage(false)
  }, [viewerIndex, items.length, total, loadPage])

  const uploadFiles = useCallback(
    async (files: File[]) => {
      if (files.length === 0) return
      let done = 0
      let failures = 0
      for (const file of files) {
        setUpload({ done, total: files.length, name: file.name, percent: 0 })
        try {
          await uploadFile(file, (percent) =>
            setUpload({ done, total: files.length, name: file.name, percent }),
          )
          done++
        } catch (err) {
          failures++
          showToast(err instanceof Error ? err.message : `Could not upload ${file.name}`)
        }
      }
      setUpload(null)
      if (done > 0) {
        showToast(`${done} item${done === 1 ? '' : 's'} uploaded to Luma`)
        setReloadKey((key) => key + 1)
      } else if (failures > 0) {
        showToast('Upload failed. Check storage permission on the phone.')
      }
    },
    [],
  )

  // Drag and drop on desktop.
  useEffect(() => {
    let depth = 0

    function hasFiles(event: DragEvent) {
      return Array.from(event.dataTransfer?.types ?? []).includes('Files')
    }
    function onDragEnter(event: DragEvent) {
      if (!hasFiles(event)) return
      event.preventDefault()
      depth++
      setDragging(true)
    }
    function onDragOver(event: DragEvent) {
      if (!hasFiles(event)) return
      event.preventDefault()
      if (event.dataTransfer) event.dataTransfer.dropEffect = 'copy'
    }
    function onDragLeave(event: DragEvent) {
      if (!hasFiles(event)) return
      depth = Math.max(0, depth - 1)
      if (depth === 0) setDragging(false)
    }
    function onDrop(event: DragEvent) {
      event.preventDefault()
      depth = 0
      setDragging(false)
      const files = event.dataTransfer?.files
      if (files && files.length > 0) void uploadFiles(Array.from(files))
    }

    window.addEventListener('dragenter', onDragEnter)
    window.addEventListener('dragover', onDragOver)
    window.addEventListener('dragleave', onDragLeave)
    window.addEventListener('drop', onDrop)
    return () => {
      window.removeEventListener('dragenter', onDragEnter)
      window.removeEventListener('dragover', onDragOver)
      window.removeEventListener('dragleave', onDragLeave)
      window.removeEventListener('drop', onDrop)
    }
  }, [uploadFiles])

  function selectAlbum(album: Album) {
    setFilter({
      smart: album.smart,
      bucket: album.bucket,
      title: album.title,
      type: 'all',
      q: searchText,
    })
    setTab('photos')
  }

  async function signOut() {
    try {
      await api.logout()
    } catch {
      /* cookie will be dropped on the next failed request anyway */
    }
    onLogout()
  }

  const chips: { key: MediaType; label: string }[] = [
    { key: 'all', label: 'All' },
    { key: 'image', label: 'Photos' },
    { key: 'video', label: 'Videos' },
    { key: 'live', label: 'Live Photos' },
  ]

  return (
    <div className="app">
      <header className="topbar">
        <div className="brand">
          Luma<span className="brand-dot" />
        </div>

        <nav className="tabs" aria-label="Sections">
          <button
            data-nav
            className={tab === 'photos' ? 'tab active' : 'tab'}
            onClick={() => setTab('photos')}
          >
            Photos
          </button>
          <button
            data-nav
            className={tab === 'albums' ? 'tab active' : 'tab'}
            onClick={() => setTab('albums')}
          >
            Albums
          </button>
        </nav>

        <div className="search-wrap">
          <input
            className="search"
            type="search"
            value={searchText}
            placeholder="Search photos, videos, folders"
            aria-label="Search media"
            onChange={(event) => {
              setSearchText(event.target.value)
              if (tab === 'albums') setTab('photos')
            }}
          />
          {searchText && (
            <button
              data-nav
              className="search-clear"
              aria-label="Clear search"
              onClick={() => setSearchText('')}
            >
              ×
            </button>
          )}
        </div>

        <button data-nav className="upload-btn" onClick={() => fileInputRef.current?.click()}>
          <span className="upload-plus">+</span> Upload
        </button>
        <button data-nav className="ghost-btn" onClick={() => void signOut()} title="Sign out">
          Exit
        </button>

        <input
          ref={fileInputRef}
          type="file"
          multiple
          accept="image/*,video/*,.heic,.heif,.mov"
          hidden
          onChange={(event) => {
            const files = event.target.files
            if (files) void uploadFiles(Array.from(files))
            event.target.value = ''
          }}
        />
      </header>

      <main className="content">
        <div className="toolbar">
          <div className="chips">
            {chips.map((chip) => (
              <button
                key={chip.key}
                data-nav
                className={filter.type === chip.key ? 'chip active' : 'chip'}
                onClick={() => {
                  setFilter((current) => ({ ...current, type: chip.key }))
                  setTab('photos')
                }}
              >
                {chip.label}
              </button>
            ))}
            {(filter.smart || filter.bucket) && (
              <button
                data-nav
                className="chip album-chip"
                onClick={() =>
                  setFilter((current) => ({
                    ...current,
                    smart: undefined,
                    bucket: undefined,
                    title: undefined,
                  }))
                }
              >
                {filter.title ?? 'Album'} ×
              </button>
            )}
          </div>
          <div className="count">
            {tab === 'photos' ? `${total.toLocaleString()} items` : `${albums.length} albums`}
            {session.itemCount ? <span className="count-dim"> · {session.itemCount.toLocaleString()} on phone</span> : null}
          </div>
        </div>

        {tab === 'photos' ? (
          <p className="format-hint">
            iPhone tip: Safari sends photos as JPEG and drops the Live Photo clip. For original HEIC
            and Live Photos, share the .HEIC + .MOV with LocalSend, or upload both files from the iOS
            Files app.
          </p>
        ) : null}

        {tab === 'albums' ? (
          <AlbumsView albums={albums} onSelect={selectAlbum} />
        ) : error && items.length === 0 ? (
          <div className="empty">
            <div className="empty-art">⚠</div>
            <h2>Could not reach Luma</h2>
            <p>{error}</p>
            <button data-nav className="upload-btn" onClick={() => setReloadKey((key) => key + 1)}>
              Try again
            </button>
          </div>
        ) : items.length === 0 && loading ? (
          <div className="grid">
            {Array.from({ length: 18 }).map((_, index) => (
              <div key={index} className="tile skeleton" />
            ))}
          </div>
        ) : items.length === 0 ? (
          <div className="empty">
            <div className="empty-art">◍</div>
            <h2>Nothing here yet</h2>
            <p>Try another album, clear the search, or upload something new.</p>
            <button data-nav className="upload-btn" onClick={() => fileInputRef.current?.click()}>
              Upload media
            </button>
          </div>
        ) : (
          <>
            <div className="grid">
              {items.map((item, index) => (
                <MediaTile
                  key={item.id}
                  item={item}
                  onOpen={() => setViewerIndex(index)}
                />
              ))}
            </div>
            <div className="list-footer">
              {loading ? (
                <>
                  <div className="spinner small" /> Loading more…
                </>
              ) : items.length < total ? (
                <button
                  data-nav
                  className="chip"
                  onClick={() => void loadPage(false)}
                >
                  Load more ({items.length.toLocaleString()} of {total.toLocaleString()})
                </button>
              ) : (
                <span>End of library · {total.toLocaleString()} items</span>
              )}
            </div>
          </>
        )}
      </main>

      {viewerIndex !== null && items[viewerIndex] && (
        <Viewer
          items={items}
          index={viewerIndex}
          onIndex={setViewerIndex}
          onClose={() => setViewerIndex(null)}
          onNeedMore={() => void loadPage(false)}
        />
      )}

      {upload && (
        <div className="upload-panel" role="status">
          <div className="upload-row">
            <span className="upload-name">
              {upload.done + 1 > upload.total ? upload.total : upload.done + 1} / {upload.total} ·{' '}
              {upload.name}
            </span>
            <span>{upload.percent}%</span>
          </div>
          <div className="progress">
            <div style={{ width: `${upload.percent}%` }} />
          </div>
        </div>
      )}

      {dragging && (
        <div className="drop-overlay">
          <div className="drop-card">
            <div className="drop-icon">↓</div>
            <h2>Drop to upload</h2>
            <p>Photos and videos are saved into Luma on your phone.</p>
          </div>
        </div>
      )}

      {toast && <div className="toast">{toast}</div>}
    </div>
  )
}

function MediaTile({ item, onOpen }: { item: MediaItem; onOpen: () => void }) {
  return (
    <button
      data-nav
      className="tile"
      onClick={onOpen}
      title={item.name}
      aria-label={`${item.name}${item.video ? ', video' : ''}${item.live ? ', live photo' : ''}`}
    >
      <img src={`${item.thumb}?size=420`} alt="" loading="lazy" decoding="async" draggable={false} />
      <span className="tile-shade" />
      {item.video && (
        <span className="badge duration">
          <span className="play-glyph">▶</span> {formatDuration(item.duration)}
        </span>
      )}
      {item.live && <span className="badge live">LIVE</span>}
      {!item.video && !item.live && <span className="badge file">{item.name.split('.').pop()?.toUpperCase()}</span>}
    </button>
  )
}

function AlbumsView({ albums, onSelect }: { albums: Album[]; onSelect: (album: Album) => void }) {
  if (albums.length === 0) {
    return (
      <div className="empty">
        <div className="empty-art">▤</div>
        <h2>No albums</h2>
        <p>Albums appear here as soon as your phone has photos.</p>
      </div>
    )
  }
  return (
    <div className="album-grid">
      {albums.map((album) => (
        <button
          key={album.key}
          data-nav
          className="album-card"
          onClick={() => onSelect(album)}
        >
          <div className="album-cover">
            {album.cover ? (
              <img src={`${album.cover}?size=560`} alt="" loading="lazy" draggable={false} />
            ) : (
              <span className="album-placeholder">▤</span>
            )}
          </div>
          <div className="album-meta">
            <span className="album-title">{album.title}</span>
            <span className="album-count">{album.count.toLocaleString()} items</span>
          </div>
        </button>
      ))}
    </div>
  )
}

function Viewer({
  items,
  index,
  onIndex,
  onClose,
  onNeedMore,
}: {
  items: MediaItem[]
  index: number
  onIndex: (index: number) => void
  onClose: () => void
  onNeedMore: () => void
}) {
  const item = items[index]
  const [liveMode, setLiveMode] = useState(false)
  const [zoom, setZoom] = useState(1)
  const [videoFailed, setVideoFailed] = useState(false)
  const touchStartRef = useRef<{ x: number; y: number } | null>(null)

  useEffect(() => {
    setLiveMode(false)
    setZoom(1)
    setVideoFailed(false)
  }, [item?.id])

  const go = useCallback(
    (delta: number) => {
      const next = index + delta
      if (next < 0 || next >= items.length) return
      onIndex(next)
      if (next >= items.length - 4) onNeedMore()
    },
    [index, items.length, onIndex, onNeedMore],
  )

  const isLive = Boolean(item?.live && item.liveVideoId)
  const showVideo = Boolean(item?.video)
  const liveVideo = isLive && liveMode
  const videoSrc = showVideo ? item.videoUrl : liveVideo ? `/api/video/${item.liveVideoId}` : null

  useEffect(() => {
    function onKeyDown(event: KeyboardEvent) {
      if (event.key === 'Escape') {
        event.preventDefault()
        onClose()
      } else if (event.key === 'ArrowLeft') {
        event.preventDefault()
        go(-1)
      } else if (event.key === 'ArrowRight') {
        event.preventDefault()
        go(1)
      } else if (event.key === ' ' || event.code === 'Space') {
        const video = document.querySelector<HTMLVideoElement>('.viewer video')
        if (video) {
          event.preventDefault()
          if (video.paused) void video.play()
          else video.pause()
        }
      } else if (event.key === '+' || event.key === '=') {
        event.preventDefault()
        setZoom((value) => Math.min(4, value + 0.5))
      } else if (event.key === '-') {
        event.preventDefault()
        setZoom((value) => Math.max(1, value - 0.5))
      } else if (event.key.toLowerCase() === 'd') {
        window.location.href = item.download
      } else if (event.key.toLowerCase() === 'l' && isLive) {
        setLiveMode((value) => !value)
      }
    }
    window.addEventListener('keydown', onKeyDown)
    return () => window.removeEventListener('keydown', onKeyDown)
  }, [go, onClose, item, isLive])

  return (
    <div
      className="viewer"
      onTouchStart={(event) => {
        if (event.touches.length === 1) {
          touchStartRef.current = {
            x: event.touches[0].clientX,
            y: event.touches[0].clientY,
          }
        }
      }}
      onTouchEnd={(event) => {
        const start = touchStartRef.current
        touchStartRef.current = null
        if (!start || zoom > 1) return
        const touch = event.changedTouches[0]
        const dx = touch.clientX - start.x
        const dy = touch.clientY - start.y
        if (Math.abs(dx) > 64 && Math.abs(dx) > Math.abs(dy)) go(dx < 0 ? 1 : -1)
      }}
    >
      <div className="viewer-top">
        <button className="vbtn" onClick={onClose} aria-label="Back">
          ←
        </button>
        <div className="viewer-title">
          <span className="viewer-name">{item.name}</span>
          <span className="viewer-sub">
            {formatDate(item.date)} · {item.width} × {item.height}
            {item.size ? ` · ${formatBytes(item.size)}` : ''}
          </span>
        </div>
        <div className="viewer-actions">
          {isLive && (
            <button
              className={liveMode ? 'vbtn accent' : 'vbtn'}
              onClick={() => setLiveMode((value) => !value)}
              title="Play the Live Photo clip"
            >
              LIVE
            </button>
          )}
          <a className="vbtn" href={item.download} download>
            Download
          </a>
          {isLive && (
            <a className="vbtn" href={`/api/live/${item.id}.zip`} download>
              ZIP
            </a>
          )}
          <button className="vbtn" onClick={onClose} aria-label="Close" title="Close (Esc)">
            ✕
          </button>
        </div>
      </div>

      <div
        className="viewer-stage"
        style={{ backgroundImage: `url("${item.thumb}?size=96")` }}
        onDoubleClick={() => setZoom((value) => (value > 1 ? 1 : 2.5))}
      >
        {videoSrc ? (
          videoFailed ? (
            <div className="video-fallback">
              <div className="empty-art">▶</div>
              <h2>This browser cannot decode the clip</h2>
              <p>
                {item.name} is probably HEVC / QuickTime. Safari plays it natively; Chrome, Edge
                and many TV browsers do not.
              </p>
              <a className="upload-btn" href={item.download} download>
                Download original
              </a>
            </div>
          ) : (
            <video
              key={videoSrc}
              src={videoSrc}
              controls
              autoPlay
              playsInline
              onError={() => setVideoFailed(true)}
            />
          )
        ) : (
          <img
            className="viewer-image"
            src={item.image}
            alt={item.name}
            draggable={false}
            style={{ transform: `scale(${zoom})` }}
          />
        )}
      </div>

      <div className="viewer-bottom">
        <button className="vbtn" onClick={() => go(-1)} disabled={index === 0} aria-label="Previous">
          ‹
        </button>
        <span className="viewer-counter">
          {index + 1} / {items.length}
        </span>
        <button
          className="vbtn"
          onClick={() => go(1)}
          disabled={index >= items.length - 1}
          aria-label="Next"
        >
          ›
        </button>
      </div>
    </div>
  )
}
