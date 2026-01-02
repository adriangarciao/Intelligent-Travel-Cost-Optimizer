import React, { useEffect } from 'react'
import { render, waitFor } from '@testing-library/react'
import { act } from 'react'
import useCompare from './useCompare'

beforeEach(() => {
  localStorage.removeItem('traveloptimizer.compare.offerIds')
  localStorage.removeItem('traveloptimizer.compare.snapshots')
})

function HookHarness({ onReady }: { onReady: (api: any) => void }) {
  const cmp = useCompare()
  useEffect(() => {
    const t = setTimeout(() => {
      const api = {
        getIds: () => {
          const raw = localStorage.getItem('traveloptimizer.compare.offerIds')
          return raw ? JSON.parse(raw) : []
        },
        getSnapshots: () => {
          const raw = localStorage.getItem('traveloptimizer.compare.snapshots')
          return raw ? JSON.parse(raw) : {}
        },
        toggle: (id: string, snap?: any) => cmp.toggle(id, snap),
        remove: (id: string) => cmp.remove(id),
        clear: () => cmp.clear()
      }
      onReady(api)
    }, 0)
    return () => clearTimeout(t)
  }, [cmp, onReady])
  return null
}

test('toggle, remove, clear and max-3 enforcement', async () => {
  let api: any = null
  render(<HookHarness onReady={(c) => { api = c }} />)
  await waitFor(() => expect(api).not.toBeNull())

  expect(api.getIds()).toEqual([])

  act(() => {
    const r = api.toggle('a', { id: 'a', totalPrice: 100 })
    expect(r.ok).toBe(true)
  })
  await waitFor(() => expect(api.getIds()).toEqual(['a']))

  act(() => {
    expect(api.toggle('b', { id: 'b', totalPrice: 200 }).ok).toBe(true)
    expect(api.toggle('c', { id: 'c', totalPrice: 300 }).ok).toBe(true)
  })
  await waitFor(() => expect(api.getIds().slice().sort()).toEqual(['a','b','c'].sort()))

  act(() => {
    const r = api.toggle('d', { id: 'd', totalPrice: 400 })
    expect(r.ok).toBe(false)
  })
  expect(api.getIds().length).toBe(3)

  act(() => api.remove('b'))
  await waitFor(() => expect(api.getIds()).toEqual(expect.not.arrayContaining(['b'])))

  act(() => api.clear())
  await waitFor(() => expect(api.getIds()).toEqual([]))
  expect(api.getSnapshots()).toEqual({})
})
