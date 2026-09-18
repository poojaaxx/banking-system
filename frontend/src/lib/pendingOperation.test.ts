import { act, renderHook } from '@testing-library/react'
import { beforeEach, describe, expect, it } from 'vitest'
import { usePendingOperation } from './pendingOperation'

describe('usePendingOperation', () => {
  beforeEach(() => {
    localStorage.clear()
  })

  it('has no pending key initially', () => {
    const { result } = renderHook(() => usePendingOperation('transfer:1'))
    expect(result.current.pendingKey).toBeNull()
  })

  it('ensureKey mints a key and persists it to localStorage', () => {
    const { result } = renderHook(() => usePendingOperation('transfer:1'))
    let key = ''
    act(() => {
      key = result.current.ensureKey()
    })
    expect(key).toBeTruthy()
    expect(localStorage.getItem('pending-op:transfer:1')).toBe(key)
    expect(result.current.pendingKey).toBe(key)
  })

  it('ensureKey called again (e.g. after a page refresh) returns the SAME key, never a new one', () => {
    const { result, unmount } = renderHook(() => usePendingOperation('transfer:1'))
    let firstKey = ''
    act(() => {
      firstKey = result.current.ensureKey()
    })
    unmount()

    // Simulate a fresh mount, as happens on page refresh.
    const { result: secondResult } = renderHook(() => usePendingOperation('transfer:1'))
    let secondKey = ''
    act(() => {
      secondKey = secondResult.current.ensureKey()
    })
    expect(secondKey).toBe(firstKey)
  })

  it('clear removes the key so the NEXT ensureKey mints a fresh one', () => {
    const { result } = renderHook(() => usePendingOperation('transfer:1'))
    let firstKey = ''
    act(() => {
      firstKey = result.current.ensureKey()
    })
    act(() => {
      result.current.clear()
    })
    expect(result.current.pendingKey).toBeNull()
    expect(localStorage.getItem('pending-op:transfer:1')).toBeNull()

    let secondKey = ''
    act(() => {
      secondKey = result.current.ensureKey()
    })
    expect(secondKey).not.toBe(firstKey)
  })

  it('scopes keys independently per operation', () => {
    const { result: a } = renderHook(() => usePendingOperation('transfer:1'))
    const { result: b } = renderHook(() => usePendingOperation('transfer:2'))
    let keyA = ''
    let keyB = ''
    act(() => {
      keyA = a.current.ensureKey()
      keyB = b.current.ensureKey()
    })
    expect(keyA).not.toBe(keyB)
  })
})
