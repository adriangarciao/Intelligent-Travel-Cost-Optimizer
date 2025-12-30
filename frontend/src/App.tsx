import React from 'react'
import { Routes, Route, Link } from 'react-router-dom'
import SearchPage from './pages/SearchPage'
import ResultsPage from './pages/ResultsPage'
import SavedPage from './pages/SavedPage'
import SavedOffersPage from './pages/SavedOffersPage'

export default function App() {
  return (
    <div className="min-h-screen bg-gray-50">
      <div style={{background:'#fde68a',padding:'6px',textAlign:'center',fontWeight:600}}>UI mounted OK — Travel Optimizer (debug banner)</div>
      <header className="bg-white border-b">
        <div className="max-w-5xl mx-auto px-4 py-4 flex items-center justify-between">
          <Link to="/" className="text-2xl font-semibold">
            Travel Optimizer
          </Link>
          <nav className="flex gap-3">
            <Link to="/saved/offers" className="text-sm text-gray-600">Saved Offers</Link>
            <Link to="/saved" className="text-sm text-gray-600">Saved Searches</Link>
          </nav>
        </div>
      </header>
      <main className="max-w-5xl mx-auto px-4 py-8">
        <Routes>
          <Route path="/" element={<SearchPage />} />
          <Route path="/results/:searchId" element={<ResultsPage />} />
          <Route path="/saved" element={<SavedPage />} />
          <Route path="/saved/offers" element={<SavedOffersPage />} />
        </Routes>
      </main>
    </div>
  )
}
