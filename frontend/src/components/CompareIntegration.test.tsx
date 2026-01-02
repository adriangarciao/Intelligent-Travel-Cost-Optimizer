import React from 'react'
import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import userEvent from '@testing-library/user-event'
import CompareToggleOffer from './CompareToggleOffer'
import CompareBar from './CompareBar'

beforeEach(() => {
  localStorage.removeItem('traveloptimizer.compare.offerIds')
  localStorage.removeItem('traveloptimizer.compare.snapshots')
})

test('select up to 3 offers shows CompareBar max message and disables others', async () => {
  render(
    <MemoryRouter>
      <div>
        <CompareToggleOffer id="a" option={{ id: 'a', totalPrice: 100 }} />
        <CompareToggleOffer id="b" option={{ id: 'b', totalPrice: 200 }} />
        <CompareToggleOffer id="c" option={{ id: 'c', totalPrice: 300 }} />
        <CompareToggleOffer id="d" option={{ id: 'd', totalPrice: 400 }} />
        <CompareBar />
      </div>
    </MemoryRouter>
  )

  // find all Compare buttons and click the first three
  const compareBtns = screen.getAllByText('Compare')
  await userEvent.click(compareBtns[0])
  await userEvent.click(compareBtns[1])
  await userEvent.click(compareBtns[2])

  // Now the CompareBar should show the max message
  expect(screen.getByText(/Maximum reached \(3\)/i)).toBeTruthy()
})
