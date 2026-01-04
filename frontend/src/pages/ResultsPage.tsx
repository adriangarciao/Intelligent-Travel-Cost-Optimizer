import React, { useState } from 'react'
import { useParams } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { getTripOptions } from '../lib/api'
import TripCard from '../components/TripCard'
import useSavedItems from '../hooks/useSavedItems'
import { computeDealScores, getOptionId } from '../utils/dealScore'
import { useMemo } from 'react'
import type { SearchCriteriaDTO } from '../types/api'

// Component to display search criteria summary
function SearchCriteriaSummary({ criteria }: { criteria: SearchCriteriaDTO | null | undefined }) {
  if (!criteria) return null
  
  const tripTypeLabel = criteria.tripType === 'ROUND_TRIP' ? 'Round-Trip' : 'One-Way'
  
  // Helper to format a date for display
  const formatDate = (dateStr: string | null | undefined) => {
    if (!dateStr) return null
    try {
      return new Date(dateStr + 'T00:00:00').toLocaleDateString('en-US', { 
        weekday: 'short', month: 'short', day: 'numeric' 
      })
    } catch {
      return dateStr
    }
  }
  
  return (
    <div className="mb-4 p-4 bg-blue-50 border border-blue-200 rounded-lg">
      <div className="flex flex-wrap items-center gap-4 text-sm mb-2">
        <span className="font-semibold text-blue-800 text-base">
          {tripTypeLabel}
        </span>
        <span className="text-gray-800 font-medium text-base">
          {criteria.origin} → {criteria.destination}
        </span>
      </div>
      
      <div className="flex flex-wrap gap-6 text-sm">
        {/* Departure info */}
        <div className="flex flex-col">
          <span className="text-blue-600 font-medium">Outbound</span>
          {criteria.selectedDepartureDate ? (
            <span className="text-gray-800 font-semibold">{formatDate(criteria.selectedDepartureDate)}</span>
          ) : criteria.departureWindow ? (
            <span className="text-gray-600 text-xs">
              Window: {formatDate(criteria.departureWindow.earliest)} - {formatDate(criteria.departureWindow.latest)}
            </span>
          ) : null}
        </div>
        
        {/* Return info for round-trip */}
        {criteria.tripType === 'ROUND_TRIP' && (
          <div className="flex flex-col">
            <span className="text-green-600 font-medium">Return</span>
            {criteria.selectedReturnDate ? (
              <span className="text-gray-800 font-semibold">{formatDate(criteria.selectedReturnDate)}</span>
            ) : criteria.returnWindow ? (
              <span className="text-gray-600 text-xs">
                Window: {formatDate(criteria.returnWindow.earliest)} - {formatDate(criteria.returnWindow.latest)}
              </span>
            ) : null}
          </div>
        )}
        
        {/* Traveler count */}
        {typeof criteria.numTravelers === 'number' && criteria.numTravelers > 0 && (
          <div className="flex flex-col">
            <span className="text-gray-500">Travelers</span>
            <span className="text-gray-800">{criteria.numTravelers}</span>
          </div>
        )}
      </div>
    </div>
  )
}

export default function ResultsPage() {
  const { searchId } = useParams()
  const [page, setPage] = useState(0)
  const [size] = useState(10)
  const [sortBy, setSortBy] = useState<string | undefined>('valueScore')
  const [sortDir, setSortDir] = useState<'asc'|'desc'>('desc')
  const saved = useSavedItems()

  const { data, isLoading, error } = useQuery({
    queryKey: ['tripOptions', searchId, page, size, sortBy, sortDir],
    queryFn: () => getTripOptions(searchId as string, page, size, sortBy, sortDir),
    enabled: !!searchId
  })

  // Normalize possible response shapes:
  // - paginated: { content: [...], totalElements }
  // - embedded: { options: [...] }
  const options: any[] = (() => {
    if (!data) return []
    if (Array.isArray((data as any).content)) return (data as any).content
    if (Array.isArray((data as any).options)) return (data as any).options
    return []
  })()

  const totalElements = (data && ((data as any).totalElements ?? (data as any).totalOptions)) ?? options.length

  // Check if more results are available from the provider
  // Default to true for backwards compatibility
  const hasMore = (data as any)?.hasMore ?? true

  // Get search criteria from response if available
  const criteria: SearchCriteriaDTO | undefined = (data as any)?.criteria

  // Get observability data from response
  const observability = (data as any)?.__observability

  // compute deal scores once per searchId
  // Note: useMemo must be called unconditionally (before any early returns) per React hooks rules
  const scoreMap = useMemo(() => computeDealScores(options), [searchId, JSON.stringify(options.map(o => ({ id: o.id ?? o.tripOptionId, price: o.totalPrice })) )])

  // TEMP DEBUG: inspect the resolved data shape
  if (typeof window !== 'undefined') {
    // eslint-disable-next-line no-console
    console.log('ResultsPage: resolved data=', data)
  }

  if (!searchId) return <div>Missing searchId</div>

  return (
    <div>
      <div className="mb-2 text-sm text-gray-600">Debug: searchId={searchId} loading={String(isLoading)} error={error ? 'yes' : 'no'} options={options.length}</div>
      
      {/* Search criteria summary */}
      <SearchCriteriaSummary criteria={criteria} />
      <div className="flex items-center justify-between mb-4">
        <div>
          <h2 className="text-xl font-semibold">Results for {searchId}</h2>
          <div className="text-sm text-gray-500">Sort by:
            <select value={sortBy} onChange={(e) => setSortBy(e.target.value)} className="ml-2 border rounded px-2 py-1">
              <option value="valueScore">Value score</option>
              <option value="totalPrice">Total price</option>
            </select>
            <button className="ml-2 px-2 py-1 border rounded" onClick={() => setSortDir((d) => d === 'asc' ? 'desc' : 'asc')}>{sortDir}</button>
          </div>
        </div>
        <div>
          <div className="text-sm text-gray-600">Page {page + 1}</div>
        </div>
      </div>

          {isLoading && <div>Loading...</div>}
          {error && <div className="text-red-600">Error loading results: {(error as any)?.message ?? 'Unknown'}</div>}

          <div className="space-y-4">
            {options.length === 0 && !isLoading && (
              <div className="text-sm text-gray-500">
                {page > 0 && !hasMore ? 'No further options available' : 'No options found'}
              </div>
            )}
            {options.map((opt: any) => {
              const key = opt.id ?? opt.optionId ?? opt.tripOptionId ?? JSON.stringify(opt)
              const info = scoreMap.get(opt.id ?? opt.tripOptionId ?? opt.optionId ?? key)
              return (
                <TripCard
                  key={key}
                  searchId={searchId}
                  option={opt}
                  onSave={(item) => saved.saveItem(item)}
                  dealScore={info?.score}
                  dealLabel={info?.label}
                  percentileText={info?.percentileText}
                  observability={observability}
                />
              )
            })}
          </div>

      <div className="flex items-center justify-between mt-6">
        <div>
          <button 
            className="px-3 py-1 border rounded mr-2 disabled:opacity-50" 
            onClick={() => setPage((p) => Math.max(0, p - 1))}
            disabled={page === 0}
          >
            Prev
          </button>
          <button 
            className="px-3 py-1 border rounded disabled:opacity-50" 
            onClick={() => setPage((p) => p + 1)}
            disabled={!hasMore && options.length < size}
            title={!hasMore && options.length < size ? 'No further options available' : ''}
          >
            Next
          </button>
          {!hasMore && <span className="ml-2 text-sm text-gray-500 italic">End of results</span>}
        </div>
        <div className="text-sm text-gray-500">Total: {totalElements ?? '—'}</div>
      </div>
    </div>
  )
}
