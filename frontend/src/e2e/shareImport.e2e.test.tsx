import React from 'react'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter, Route, Routes } from 'react-router-dom'

import TripCard from '../components/TripCard'
import ComparePage from '../pages/ComparePage'

// Minimal mock offer used by TripCard in tests
const mockOffer = {
  id: 'opt-1',
  price: { total: '123.45', currency: 'USD' },
  segments: [],
  provider: 'mock',
}

function renderWithProviders(ui: React.ReactElement, { route = '/' } = {}) {
  const qc = new QueryClient()
  return render(
    <QueryClientProvider client={qc}>
      <MemoryRouter initialEntries={[route]}>
        {ui}
      </MemoryRouter>
    </QueryClientProvider>
  )
}

test('full share -> import flow populates compare from shared URL', async () => {
  // Capture clipboard writes
  let clipboardText = ''
  // @ts-ignore
  global.navigator.clipboard = {
    writeText: async (text: string) => {
      clipboardText = text
      return Promise.resolve()
    },
  }

  // Render a TripCard with searchId so compare id is deterministic
  const searchId = 's1'

  renderWithProviders(
    <Routes>
      <Route
        path="/"
        element={<TripCard option={mockOffer} searchId={searchId} />}
      />
      <Route path="/compare" element={<ComparePage />} />
    </Routes>,
    { route: '/' }
  )

  // Open share modal by clicking the Share button on TripCard
  const shareButton = await screen.findByRole('button', { name: /share/i })
  await userEvent.click(shareButton)

  // Click the copy link button in modal
  const copyLink = await screen.findByText(/copy share link/i)
  await userEvent.click(copyLink)

  expect(clipboardText).not.toBe('')

  // Extract share param from the copied URL
  const url = new URL(clipboardText)
  const shareParam = url.searchParams.get('share')
  expect(shareParam).toBeTruthy()

  // Now navigate to ComparePage with the share payload
  // Update browser URL and re-render with initial route set to /compare?share=...
  window.history.pushState({}, '', `/compare?share=${shareParam}`)
  renderWithProviders(<ComparePage />, { route: `/compare?share=${shareParam}` })

  // Wait for the toast or UI that indicates import completed, or for compare entries
  await waitFor(() => {
    // compare entries are persisted to localStorage key used by useCompare
    const raw = localStorage.getItem('traveloptimizer.compare.offerIds')
    expect(raw).toBeTruthy()
    const ids = JSON.parse(raw || '[]')
    // Compare id is `${searchId}:${offer.id}` per implementation
    expect(ids).toContain(`${searchId}:${mockOffer.id}`)
  })
})
