import React, { useEffect, useState } from 'react'
import { Routes, Route, Link } from 'react-router-dom'
import SearchPage from './pages/SearchPage'
import ResultsPage from './pages/ResultsPage'
import SavedPage from './pages/SavedPage'
import SavedOffersPage from './pages/SavedOffersPage'
import CompareBar from './components/CompareBar'
import ComparePage from './pages/ComparePage'
import Toast from './components/Toast'
import { getDemoStatus } from './lib/api'

function DemoBanner() {
  const [show, setShow] = useState(false)

  useEffect(() => {
    getDemoStatus().then(({ demoMode }) => setShow(demoMode))
  }, [])

  if (!show) return null

  return (
    <div className="bg-amber-50 border-b border-amber-200 text-amber-800 text-xs text-center py-1.5 px-4">
      Running on demo data — no real API calls. To use live Amadeus flights, set{' '}
      <code className="font-mono bg-amber-100 px-1 rounded">TRAVEL_PROVIDERS_FLIGHTS=amadeus</code>{' '}
      with valid credentials.
    </div>
  )
}

export default function App() {
  return (
    <div className="min-h-screen bg-gray-50">
      <DemoBanner />
      <header className="bg-white border-b sticky top-0 z-40">
        <div className="max-w-5xl mx-auto px-4 py-4 flex items-center justify-between">
          <Link to="/" className="text-2xl font-normal" style={{color:'var(--dusk-blue)'}}>
            TravelOptimizer
          </Link>
          <nav className="flex gap-4 items-center">
            <Link to="/saved/offers" className="text-sm text-gray-600 hover:underline">Saved Offers</Link>
            <Link to="/saved" className="text-sm text-gray-600 hover:underline">Saved Searches</Link>
            <Link to="/compare" className="text-sm text-gray-600 hover:underline">Compare</Link>
          </nav>
        </div>
      </header>
      <main className="max-w-5xl mx-auto px-4 py-8">
        <CompareBar />
        <Toast />
        <Routes>
          <Route path="/" element={<SearchPage />} />
          <Route path="/results/:searchId" element={<ResultsPage />} />
          <Route path="/compare" element={<ComparePage />} />
          <Route path="/saved" element={<SavedPage />} />
          <Route path="/saved/offers" element={<SavedOffersPage />} />
        </Routes>
      </main>
    </div>
  )
}
