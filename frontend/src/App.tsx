import React from 'react'
import { Routes, Route, Link } from 'react-router-dom'
import SearchPage from './pages/SearchPage'
import ResultsPage from './pages/ResultsPage'
import SavedPage from './pages/SavedPage'
import SavedOffersPage from './pages/SavedOffersPage'
import CompareBar from './components/CompareBar'
import ComparePage from './pages/ComparePage'
import Toast from './components/Toast'

export default function App() {
  return (
    <div className="min-h-screen bg-gray-50">
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
