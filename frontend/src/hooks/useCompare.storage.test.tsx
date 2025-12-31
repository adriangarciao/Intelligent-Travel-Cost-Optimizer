import React, { useEffect } from 'react'
import { render, waitFor } from '@testing-library/react'
import { act } from 'react'
import useCompare from './useCompare'

beforeEach(() => {
  localStorage.removeItem('traveloptimizer.compare.offerIds')
  localStorage.removeItem('traveloptimizer.compare.snapshots')
})

function Harness({ onReady }: { onReady: (api: any) => void }) {
  const cmp = useCompare()
  useEffect(() => {
    const t = setTimeout(() => onReady({ ids: () => {
      const raw = localStorage.getItem('traveloptimizer.compare.offerIds')
      return raw ? JSON.parse(raw) : []
    }, toggle: (id: string, s?: any) => cmp.toggle(id, s) }), 0)
    return () => clearTimeout(t)
  }, [cmp, onReady])
  return null
}

test('same-tab custom event sync and storage event sync', async () => {
  let a: any = null
  let b: any = null
  render(
    <div>
      <Harness onReady={(api) => { if (!a) a = api; else b = api }} />
      <Harness onReady={(api) => { if (!a) a = api; else b = api }} />
    </div>
  )

  // Wait for harnesses
  await waitFor(() => expect(a).not.toBeNull())
  await waitFor(() => expect(b).not.toBeNull())

  // give hooks a tick to register listeners
  await new Promise((r) => setTimeout(r, 0))

  act(() => {
    const r = a.toggle('x', { id: 'x', totalPrice: 100 })
    expect(r.ok).toBe(true)
  })

  await waitFor(() => expect(a.ids()).toContain('x'))
  await waitFor(() => expect(b.ids()).toContain('x'))

  // Now simulate cross-tab: write directly to localStorage and dispatch storage event
  act(() => {
    localStorage.setItem('traveloptimizer.compare.offerIds', JSON.stringify(['y']))
    localStorage.setItem('traveloptimizer.compare.snapshots', JSON.stringify({ y: { id: 'y', totalPrice: 50 } }))
    // dispatch storage event
    window.dispatchEvent(new StorageEvent('storage', { key: 'traveloptimizer.compare.offerIds', newValue: JSON.stringify(['y']) } as any))
  })

  await waitFor(() => expect(a.ids()).toContain('y'))
  await waitFor(() => expect(b.ids()).toContain('y'))
})
