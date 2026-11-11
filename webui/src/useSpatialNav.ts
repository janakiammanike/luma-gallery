import { useEffect } from 'react'

/**
 * D-pad / arrow-key focus movement for TV browsers and desktop keyboards.
 * Native focus order works for Tab; arrows jump geometrically to the nearest tile
 * in the pressed direction, which is what TV remotes expect.
 */
export function useSpatialNav(enabled: boolean): void {
  useEffect(() => {
    if (!enabled) return

    function onKeyDown(event: KeyboardEvent) {
      if (event.defaultPrevented || event.altKey || event.ctrlKey || event.metaKey) return
      if (event.shiftKey) return

      const target = event.target as HTMLElement | null
      if (
        target &&
        (target.tagName === 'INPUT' ||
          target.tagName === 'TEXTAREA' ||
          target.tagName === 'SELECT' ||
          target.isContentEditable)
      ) {
        return
      }
      if (document.querySelector('.viewer')) return

      const directions: Record<string, 'left' | 'right' | 'up' | 'down'> = {
        ArrowLeft: 'left',
        ArrowRight: 'right',
        ArrowUp: 'up',
        ArrowDown: 'down',
      }
      const direction = directions[event.key]
      if (!direction) return

      const nodes = Array.from(document.querySelectorAll<HTMLElement>('[data-nav]')).filter(
        (element) => element.offsetParent !== null && !element.hasAttribute('disabled'),
      )
      if (nodes.length === 0) return

      const active = document.activeElement as HTMLElement | null
      const current = active && active.matches('[data-nav]') ? active : null
      if (!current) {
        event.preventDefault()
        nodes[0].focus()
        return
      }

      const rect = current.getBoundingClientRect()
      const cx = rect.left + rect.width / 2
      const cy = rect.top + rect.height / 2

      let best: HTMLElement | null = null
      let bestScore = Number.POSITIVE_INFINITY

      for (const element of nodes) {
        if (element === current) continue
        const candidate = element.getBoundingClientRect()
        if (candidate.width === 0 && candidate.height === 0) continue
        const dx = candidate.left + candidate.width / 2 - cx
        const dy = candidate.top + candidate.height / 2 - cy

        const okay =
          direction === 'left'
            ? dx < -4
            : direction === 'right'
              ? dx > 4
              : direction === 'up'
                ? dy < -4
                : dy > 4
        if (!okay) continue

        const horizontal = direction === 'left' || direction === 'right'
        const primary = horizontal ? Math.abs(dx) : Math.abs(dy)
        const secondary = horizontal ? Math.abs(dy) : Math.abs(dx)
        const score = primary + secondary * 2.2
        if (score < bestScore) {
          bestScore = score
          best = element
        }
      }

      if (best) {
        event.preventDefault()
        best.focus({ preventScroll: true })
        best.scrollIntoView({ block: 'nearest', inline: 'nearest', behavior: 'smooth' })
      }
    }

    window.addEventListener('keydown', onKeyDown)
    return () => window.removeEventListener('keydown', onKeyDown)
  }, [enabled])
}
