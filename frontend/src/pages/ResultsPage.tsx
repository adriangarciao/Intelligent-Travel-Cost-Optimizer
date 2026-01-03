import React, { useState } from 'react'
import { useParams } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { getTripOptions } from '../lib/api'
import TripCard from '../components/TripCard'
import useSavedItems from '../hooks/useSavedItems'
import { computeDealScores, getOptionId } from '../utils/dealScore'
import { useMemo } from 'react'

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

  // TEMP DEBUG: inspect the resolved data shape
  if (typeof window !== 'undefined') {
    // eslint-disable-next-line no-console
    console.log('ResultsPage: resolved data=', data)
  }

  if (!searchId) return <div>Missing searchId</div>
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

  // compute deal scores once per searchId
  const scoreMap = useMemo(() => computeDealScores(options), [searchId, JSON.stringify(options.map(o => ({ id: o.id ?? o.tripOptionId, price: o.totalPrice })) )])

  return (
    <div>
      <div className="mb-2 text-sm text-gray-600">Debug: searchId={searchId} loading={String(isLoading)} error={error ? 'yes' : 'no'} options={options.length}</div>
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
                  dealScore={info?.score ?? null}
                  dealLabel={info?.label}
                  percentileText={info?.percentileText}
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
