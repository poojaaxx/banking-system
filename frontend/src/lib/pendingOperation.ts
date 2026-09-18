import { useCallback, useState } from 'react'

/**
 * Persists the idempotency key for one in-flight financial operation across
 * page refreshes, scoped to the current customer and the specific operation
 * (e.g. "transfer:42"). Only the key itself is stored -- never amounts,
 * account numbers, or anything auth-related -- in localStorage.
 *
 * The reconciliation strategy is: retrying the SAME key is itself the
 * reconciliation (the backend's idempotency store will replay a completed
 * result or safely resume a failed one). We only ever mint a fresh key once
 * an attempt has been confirmed to have a definite, clean outcome (success or
 * a clear business rejection) -- never automatically, and never just because
 * the network hiccuped.
 */
export function usePendingOperation(scopeKey: string) {
  const storageKey = `pending-op:${scopeKey}`
  const [pendingKey, setPendingKey] = useState<string | null>(() => {
    try {
      return localStorage.getItem(storageKey)
    } catch {
      return null
    }
  })

  const ensureKey = useCallback((): string => {
    try {
      const existing = localStorage.getItem(storageKey)
      if (existing) {
        setPendingKey(existing)
        return existing
      }
    } catch {
      // localStorage unavailable (private browsing, blocked storage) -- fall
      // back to an in-memory-only key for this attempt.
    }
    const fresh = crypto.randomUUID()
    try {
      localStorage.setItem(storageKey, fresh)
    } catch {
      /* ignore */
    }
    setPendingKey(fresh)
    return fresh
  }, [storageKey])

  const clear = useCallback(() => {
    try {
      localStorage.removeItem(storageKey)
    } catch {
      /* ignore */
    }
    setPendingKey(null)
  }, [storageKey])

  return { pendingKey, ensureKey, clear }
}
