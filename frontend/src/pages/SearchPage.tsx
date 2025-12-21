import React from 'react'
import { useNavigate } from 'react-router-dom'
import SearchForm from '../components/SearchForm'
import { searchTrips } from '../lib/api'
import useRecentSearches, { saveRecentSearch } from '../hooks/useRecentSearches'
import { listRecentSearches } from '../lib/api'
import { useEffect, useState } from 'react'
import { useMutation } from '@tanstack/react-query'

export default function SearchPage() {
  const navigate = useNavigate()
  const { list } = useRecentSearches()
  const [recent, setRecent] = useState(list)

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
    }
  })

  const handleSearch = (payload: any) => {
    console.log('SearchPage handleSearch payload:', payload)
    mutation.mutate(payload)
  }

  return (
    <div className="grid grid-cols-3 gap-6">
      <div className="col-span-2">
        <h2 className="text-xl font-semibold mb-4">Search Trips</h2>
        <SearchForm onSearch={handleSearch} />
      </div>
      <aside className="col-span-1">
        <h3 className="text-lg font-medium mb-3">Recent Searches</h3>
        <div className="space-y-2">
          {recent.length === 0 && <div className="text-sm text-gray-500">No recent searches</div>}
          {recent.map((r) => (
            <div key={r.searchId} className="p-3 bg-white rounded shadow">
              <div className="text-sm font-medium">{r.origin} → {r.destination}</div>
              <div className="text-xs text-gray-500">{new Date(r.createdAt).toLocaleString()}</div>
              <button className="mt-2 text-sm text-blue-600" onClick={() => navigate(`/results/${r.searchId}`)}>Open</button>
            </div>
          ))}
        </div>
      </aside>
    </div>
  )
}
