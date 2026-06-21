// hero image served from frontend/public/images
const heroImg = '/images/image1.jpg'
import { useNavigate } from 'react-router-dom'
import SearchForm from '../components/SearchForm'
import HowItWorksSection from '../components/HowItWorksSection'
import { searchTrips } from '../lib/api'
import useRecentSearches, { saveRecentSearch, RecentSearch } from '../hooks/useRecentSearches'
import { listRecentSearches } from '../lib/api'
import { useEffect, useState } from 'react'
import { useMutation } from '@tanstack/react-query'

export default function SearchPage() {
  const navigate = useNavigate()
  const { list } = useRecentSearches()
  const [recent, setRecent] = useState<RecentSearch[]>(list)
  const [showAllRecent, setShowAllRecent] = useState(false)

  useEffect(() => {
    let mounted = true
    listRecentSearches(10).then((remote) => {
      if (mounted && Array.isArray(remote) && remote.length > 0) {
        setRecent(remote.map(r => ({
          searchId: r.searchId,
          origin: r.origin,
          destination: r.destination,
          earliestDeparture: r.earliestDepartureDate,
          latestDeparture: r.latestDepartureDate,
          createdAt: r.createdAt
        })))
      }
    }).catch(() => {
      // ignore and keep local list
    })
    return () => { mounted = false }
  }, [])

  const mutation = useMutation({
    mutationFn: (payload: any) => searchTrips(payload),
    onSuccess(data, variables) {
      const { searchId } = data
      saveRecentSearch({
        searchId,
        origin: variables.origin,
        destination: variables.destination,
        earliestDepartureDate: variables.earliestDepartureDate,
        latestDepartureDate: variables.latestDepartureDate,
        createdAt: new Date().toISOString()
      })
      navigate(`/results/${searchId}`)
    },
    onError(error) {
      console.error('Search failed:', error)
    }
  })

  const handleSearch = (payload: any) => {
    mutation.mutate(payload)
  }

  return (
    <div className="space-y-6">
      {/* Hero */}
      <section className="hero-bleed relative" style={{backgroundImage:`url(${heroImg})`, backgroundSize:'cover', backgroundPosition:'center'}}>
        <div className="hero-overlay" aria-hidden="true"></div>
        <div className="max-w-5xl mx-auto px-4 py-12 md:py-16">
          <div className="hero-content">
            {/* Header Text */}
            <div className="text-center mb-8 fade-in">
              <h1 className="text-3xl md:text-5xl font-bold" style={{color:'var(--soft-linen)', textShadow:'0 2px 12px rgba(0,0,0,0.35)'}}>
                TravelOptimizer
              </h1>
              <p className="mt-3 text-lg md:text-xl" style={{color:'rgba(255,255,255,0.92)'}}>
                Smarter flight decisions in seconds.
              </p>
              <div className="mt-2 text-sm md:text-base" style={{color:'rgba(255,255,255,0.85)'}}>
                Find the best deal — and know when to buy.
              </div>
            </div>

            {/* Search Form Card - Centered and Wider */}
            <div id="search-form" className="max-w-xl mx-auto fade-in">
              <div className="card p-6 md:p-8" style={{background:'rgba(255,255,255,0.98)'}}>
                <SearchForm onSearch={handleSearch} isLoading={mutation.isPending} />
                {mutation.isError && (
                  <p className="mt-3 text-sm text-red-600">
                    Search failed: {(mutation.error as Error)?.message ?? 'Unknown error'}
                  </p>
                )}
              </div>
            </div>
          </div>
        </div>
      </section>

      {/* How it works - inserted between hero and recent searches */}
      <HowItWorksSection onScrollToSearch={() => {
        const el = document.getElementById('search-form')
        if (el) el.scrollIntoView({ behavior: 'smooth', block: 'center' })
      }} />

      {/* Recent searches */}
      <section>
        <div className="flex items-center justify-between mb-3">
          <h3 className="text-lg font-medium">Recent Searches</h3>
          {recent.length > 5 && (
            <button className="text-sm text-blue-600" onClick={() => setShowAllRecent(s => !s)}>{showAllRecent ? 'Show less' : 'View more'}</button>
          )}
        </div>

        <div className="grid grid-cols-1 md:grid-cols-3 gap-3">
          {(recent.length === 0) && <div className="text-sm muted">No recent searches</div>}
          {(recent.slice(0, showAllRecent ? recent.length : 5)).map((r) => (
            <div key={r.searchId} className="p-3 card card-hoverable card-accent" role="listitem">
              <div className="text-sm font-medium">{r.origin} → {r.destination}</div>
              <div className="text-xs muted">{new Date(r.createdAt).toLocaleString()}</div>
              <button className="mt-2 text-sm" style={{color:'var(--cornflower-blue)'}} onClick={() => navigate(`/results/${r.searchId}`)}>Open</button>
            </div>
          ))}
        </div>
      </section>
    </div>
  )
}
