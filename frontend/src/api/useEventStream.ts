import { useQueryClient } from '@tanstack/react-query'
import { useEffect, useRef } from 'react'

/**
 * Subscribes to the authenticated SSE stream. The native EventSource API
 * already sends Last-Event-ID on reconnect and retries automatically on
 * drop, so this hook's only job is to turn "a notification arrived" into
 * "refetch the affected data" -- the stream is a hint, never the source of
 * truth (see CLAUDE.md). It also does one refetch immediately on connect, to
 * pick up anything that happened while the tab was closed entirely (which
 * Last-Event-ID replay can't cover, since there was no earlier event id).
 */
export function useEventStream(enabled: boolean) {
  const queryClient = useQueryClient()
  const sourceRef = useRef<EventSource | null>(null)

  useEffect(() => {
    if (!enabled) {
      return
    }

    const refreshEverything = () => {
      queryClient.invalidateQueries({ queryKey: ['dashboard'] })
      queryClient.invalidateQueries({ queryKey: ['accounts'] })
      queryClient.invalidateQueries({ queryKey: ['transactions'] })
      queryClient.invalidateQueries({ queryKey: ['notifications'] })
      queryClient.invalidateQueries({ queryKey: ['money-requests'] })
      queryClient.invalidateQueries({ queryKey: ['savings-goals'] })
    }

    const source = new EventSource('/api/events/stream')
    sourceRef.current = source

    source.addEventListener('notification', () => {
      refreshEverything()
    })
    source.addEventListener('connected', () => {
      refreshEverything()
    })
    source.onerror = () => {
      // EventSource retries on its own; nothing to do here besides letting
      // stale data stand until the next successful event or manual refresh.
    }

    return () => {
      source.close()
      sourceRef.current = null
    }
  }, [enabled, queryClient])
}
