import React from 'react'
import { render, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import ComparePage from './ComparePage'
import { encodeSharePayload } from '../utils/shareExport'

beforeEach(() => {
  localStorage.removeItem('traveloptimizer.compare.offerIds')
  localStorage.removeItem('traveloptimizer.compare.snapshots')
})

test('import share payload from URL populates compare store', async () => {
  const offers = [{
    id: 'test:1',
    tripOptionId: '1',
    totalPrice: 123.45,
    currency: 'USD',
    valueScore: 0.5,
    flight: { airlineName: 'TestAir', airlineCode: 'TA', flightNumber: 'TA100', durationText: '2h 30m', segments: ['AAA→BBB'] }
  }]
  const payload = encodeSharePayload(offers as any)
  // set URL to include share param
  window.history.pushState({}, '', '/compare?share=' + payload)

  render(
    <QueryClientProvider client={new QueryClient()}>
      <MemoryRouter initialEntries={[`/compare?share=${payload}`]}>
        <ComparePage />
      </MemoryRouter>
    </QueryClientProvider>
  )

  await waitFor(() => {
    const idsRaw = localStorage.getItem('traveloptimizer.compare.offerIds')
    expect(idsRaw).toBeTruthy()
    const ids = idsRaw ? JSON.parse(idsRaw) : []
    expect(ids).toContain('test:1')
  })
})
