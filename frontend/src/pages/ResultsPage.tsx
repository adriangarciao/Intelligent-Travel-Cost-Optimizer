import React, { useState } from 'react'
import { useParams } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { getTripOptions } from '../lib/api'
import TripCard from '../components/TripCard'
import SuggestedFilters from '../components/SuggestedFilters'
import DiagnosticsPanel, { useDiagnosticsFromResponse } from '../components/DiagnosticsPanel'
import useSavedItems from '../hooks/useSavedItems'
import { computeDealScores, getOptionId } from '../utils/dealScore'
import { useMemo } from 'react'
import type { SearchCriteriaDTO } from '../types/api'

/** Client-side filters that the smart-filter suggestions can apply. */
type ActiveFilters = {
  nonStopOnly?: boolean
  maxLayovers?: number
  avoidAirlines?: string[]
  preferAirlines?: string[]
}

/** Apply the active smart filters to the current page of options. */
function applyFilters(options: any[], filters: ActiveFilters): any[] {
  return options.filter((opt) => {
    const flight = opt.flight ?? opt.flightSummary ?? {}
    const stops: number | undefined = typeof flight.stops === 'number' ? flight.stops : undefined
    const airline: string | undefined = flight.airlineCode
    if (filters.nonStopOnly && stops !== 0) return false
    if (typeof filters.maxLayovers === 'number' && (stops == null || stops > filters.maxLayovers)) return false
    if (filters.avoidAirlines?.length && airline && filters.avoidAirlines.includes(airline)) return false
    if (filters.preferAirlines?.length && (!airline || !filters.preferAirlines.includes(airline))) return false
    return true
  })
}

/** Human-readable label for an active filter chip. */
function filterChipLabel(key: keyof ActiveFilters, value: any): string {
  switch (key) {
    case 'nonStopOnly':
      return 'Nonstop only'
    case 'maxLayovers':
      return `Max ${value} layover${value === 1 ? '' : 's'}`
    case 'avoidAirlines':
      return `Avoiding ${Array.isArray(value) ? value.join(', ') : value}`
    case 'preferAirlines':
      return `Preferring ${Array.isArray(value) ? value.join(', ') : value}`
    default:
      return String(key)
  }
}

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

  // Smart-filter state: suggestions are personalized from the user's save/dismiss
  // history. Applying one filters the current results client-side.
  const [activeFilters, setActiveFilters] = useState<ActiveFilters>({})
  const [dismissedIds, setDismissedIds] = useState<Set<string>>(new Set())
  // Bumping this re-fetches suggestions after the user saves or dismisses.
  const [suggestionsRefresh, setSuggestionsRefresh] = useState(0)
  const bumpSuggestions = () => setSuggestionsRefresh((n) => n + 1)

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

  // compute deal scores once per searchId
  // Note: useMemo must be called unconditionally (before any early returns) per React hooks rules
  const scoreMap = useMemo(() => computeDealScores(options), [searchId, JSON.stringify(options.map(o => ({ id: o.id ?? o.tripOptionId, price: o.totalPrice })) )])

  // Price ranking: determine top 2 most expensive and bottom 2 cheapest by current options
  const priceRank = useMemo(() => {
    const list = options.map((o: any, idx: number) => ({ idx, id: o.id ?? o.optionId ?? o.tripOptionId ?? String(idx), price: Number(o.totalPrice || 0) }))
    // sort by price ascending
    const sorted = [...list].sort((a, b) => a.price - b.price)
    const low = new Set(sorted.slice(0, 2).map(s => s.id))
    const high = new Set(sorted.slice(-2).map(s => s.id))
    return { low, high }
  }, [JSON.stringify(options.map(o => ({ id: o.id ?? o.optionId ?? o.tripOptionId, price: o.totalPrice })) )])

  // Apply dismissed-card hiding and active smart filters to the current page.
  const visibleOptions = useMemo(() => {
    const notDismissed = options.filter((o: any) => !dismissedIds.has(String(o.id ?? o.optionId ?? o.tripOptionId)))
    return applyFilters(notDismissed, activeFilters)
  }, [options, dismissedIds, activeFilters])

  const activeFilterEntries = Object.entries(activeFilters).filter(([, v]) => v != null && (!Array.isArray(v) || v.length > 0))

  // Dev-only diagnostics (request id, offers count, actuator metrics). Renders null in production.
  const diagnostics = useDiagnosticsFromResponse(data ? { ...data, searchId } : undefined)

  if (!searchId) return <div>Missing searchId</div>

  return (
    <div>
      {/* Search criteria summary */}
      <SearchCriteriaSummary criteria={criteria} />

      {/* Personalized filter suggestions from the user's save/dismiss history */}
      <SuggestedFilters
        refreshTrigger={suggestionsRefresh}
        className="mb-4"
        onApplyFilter={(key, value) => setActiveFilters((prev) => ({ ...prev, [key]: value }))}
      />

      {activeFilterEntries.length > 0 && (
        <div className="mb-4 flex flex-wrap items-center gap-2">
          <span className="text-xs text-gray-500">Active filters:</span>
          {activeFilterEntries.map(([key, value]) => (
            <span key={key} className="inline-flex items-center gap-1.5 bg-blue-100 text-blue-800 text-xs px-2.5 py-1 rounded-full">
              {filterChipLabel(key as keyof ActiveFilters, value)}
              <button
                className="text-blue-500 hover:text-blue-700"
                title="Remove filter"
                onClick={() => setActiveFilters((prev) => {
                  const next = { ...prev }
                  delete (next as any)[key]
                  return next
                })}
              >
                ✕
              </button>
            </span>
          ))}
          <button className="text-xs text-gray-500 underline" onClick={() => setActiveFilters({})}>
            Clear all
          </button>
        </div>
      )}
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

          {isLoading && (
            <div className="space-y-4">
              {[1,2,3].map(i => (
                <div key={i} className="p-4 card">
                  <div className="skeleton h-6 w-40 mb-3 rounded"></div>
                  <div className="skeleton h-4 w-28 mb-2 rounded"></div>
                  <div className="skeleton h-3 w-full mb-1 rounded"></div>
                  <div className="skeleton h-3 w-3/4 mt-2 rounded"></div>
                </div>
              ))}
            </div>
          )}
          {error && <div className="text-red-600">Error loading results: {(error as any)?.message ?? 'Unknown'}</div>}

          <div className="space-y-4">
            {visibleOptions.length === 0 && !isLoading && (
              <div className="text-sm text-gray-500">
                {options.length > 0
                  ? 'No options match the active filters.'
                  : page > 0 && !hasMore
                    ? 'No further options available'
                    : 'No options found'}
              </div>
            )}
            {visibleOptions.map((opt: any) => {
              const key = opt.id ?? opt.optionId ?? opt.tripOptionId ?? JSON.stringify(opt)
              const info = scoreMap.get(opt.id ?? opt.tripOptionId ?? opt.optionId ?? key)
              const priceTag = priceRank.low.has(opt.id ?? opt.optionId ?? opt.tripOptionId ?? key) ? 'low' : (priceRank.high.has(opt.id ?? opt.optionId ?? opt.tripOptionId ?? key) ? 'high' : undefined)
              return (
                <TripCard
                  key={key}
                  searchId={searchId}
                  option={opt}
                  onSave={(item) => { saved.saveItem(item); bumpSuggestions() }}
                  onDismiss={(id) => { setDismissedIds((prev) => new Set(prev).add(id)); bumpSuggestions() }}
                  dealScore={info?.score}
                  dealLabel={info?.label}
                  percentileText={info?.percentileText}
                  priceTag={priceTag}
                />
              )
            })}
          </div>

      <div className="flex items-center justify-between mt-6">
        <div>
          <button
            className="btn"
            onClick={() => setPage((p) => Math.max(0, p - 1))}
            disabled={page === 0}
            style={{opacity: page === 0 ? 0.5 : 1, marginRight: '0.5rem'}}
          >
            Prev
          </button>
          <button
            className="btn"
            onClick={() => setPage((p) => p + 1)}
            disabled={!hasMore && options.length < size}
            title={!hasMore && options.length < size ? 'No further options available' : ''}
            style={{opacity: (!hasMore && options.length < size) ? 0.5 : 1}}
          >
            Next
          </button>
          {!hasMore && <span className="ml-2 text-sm text-gray-500 italic">End of results</span>}
        </div>
        <div className="text-sm text-gray-500">Total: {totalElements ?? '—'}</div>
      </div>

      {/* Dev-only floating diagnostics widget (hidden in production builds) */}
      <DiagnosticsPanel data={diagnostics} />
    </div>
  )
}
