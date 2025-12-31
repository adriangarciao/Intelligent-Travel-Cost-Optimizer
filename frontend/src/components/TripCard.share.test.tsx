import React from 'react'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import TripCard from './TripCard'

beforeEach(() => {
  // clear compare storage
  localStorage.removeItem('traveloptimizer.compare.offerIds')
  localStorage.removeItem('traveloptimizer.compare.snapshots')
})

test('TripCard Share modal copies share link to clipboard', async () => {
  // mock clipboard
  const writeText = vi.fn(() => Promise.resolve())
  // @ts-ignore
  global.navigator.clipboard = { writeText }

  const option = {
    id: 'opt-1',
    totalPrice: 200,
    currency: 'USD',
    valueScore: 0.7,
    flight: { airlineName: 'TestAir', airlineCode: 'TA', flightNumber: 'TA100', durationText: '2h', segments: ['AAA→BBB'] }
  }

  render(<TripCard searchId="s1" option={option as any} />)

  // open Share modal
  const shareBtn = await screen.findByText('Share')
  await userEvent.click(shareBtn)

  // find Copy share link button and click
  const copyBtn = await screen.findByText('Copy share link')
  await userEvent.click(copyBtn)

  // clipboard should be called
  expect(writeText).toHaveBeenCalled()
  const arg = writeText.mock.calls[0][0]
  expect(typeof arg).toBe('string')
  expect(arg.includes('?share=')).toBe(true)
})
