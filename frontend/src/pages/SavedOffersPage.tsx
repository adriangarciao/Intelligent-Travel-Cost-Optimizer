import React, { useEffect, useState } from 'react'
import { listSavedOffers, deleteSavedOffer } from '../lib/api'
import TripCard from '../components/TripCard'
import type { TripOptionDTO } from '../lib/types'
import watch from '../lib/watch'
import { computeDealScores, getOptionId } from '../utils/dealScore'
import { useQueryClient } from '@tanstack/react-query'
import NotificationsPanel from '../components/NotificationsPanel'
import { downloadJson, downloadCsv } from '../utils/shareExport'

export default function SavedOffersPage() {
  const [offers, setOffers] = useState<any[]>([])
  const [selectedOfferId, setSelectedOfferId] = useState<string | null>(null)

  useEffect(() => {
    listSavedOffers().then(setOffers).catch(() => setOffers([]))
  }, [])
  const queryClient = useQueryClient()

  function getCachedOptionsForSearch(searchId?: string) {
    if (!searchId) return []
    try {
      const queries = queryClient.getQueriesData() as Array<[any, any]>
      for (const [key, data] of queries) {
        if (Array.isArray(key) && key[0] === 'tripOptions' && key.includes(searchId) && data) {
          if (Array.isArray((data as any).content)) return (data as any).content
          if (Array.isArray((data as any).options)) return (data as any).options
        }
      }
    } catch (e) {
      // ignore
    }
    return []
  }

  async function handleRemove(id: string) {
    try {
      await deleteSavedOffer(id)
      setOffers((o) => o.filter(x => x.id !== id))
    } catch (e) {
      // eslint-disable-next-line no-console
      console.error(e)
      alert('Failed to remove')
    }
  }

  function mapSavedToOption(o: any): { option: TripOptionDTO, legacy: boolean } {
    // If server returns a full `option` payload, prefer it. Otherwise, build a minimal option and mark legacy.
    const legacy = !(o && o.option)
    if (!legacy) {
      const originalOption = o.option
      const opt: any = o.option as TripOptionDTO
      // ensure id and pricing fields exist for TripCard
      opt.id = opt.id || opt.tripOptionId || o.tripOptionId || o.id
      opt.totalPrice = opt.totalPrice || o.totalPrice
      opt.currency = opt.currency || o.currency || 'USD'
      // ensure flight object exists so TripCard can render details
      if (!opt.flight) {
        const segs = (() => {
          try {
            if (opt.flight && opt.flight.segments) return opt.flight.segments
            if (o.segments) return JSON.parse(o.segments)
          } catch (e) {
            // fall through
          }
          return []
        })()
        opt.flight = {
          airline: o.airlineName || o.airline || (opt.flight && opt.flight.airline) || undefined,
          airlineCode: o.airlineCode || (opt.flight && opt.flight.airlineCode) || undefined,
          airlineName: o.airlineName || (opt.flight && opt.flight.airlineName) || undefined,
          flightNumber: o.flightNumber || (opt.flight && opt.flight.flightNumber) || undefined,
          durationText: o.durationText || (opt.flight && opt.flight.durationText) || undefined,
          segments: segs,
          stops: (opt.flight && typeof opt.flight.stops === 'number') ? opt.flight.stops : (Array.isArray(segs) ? Math.max(0, segs.length - 1) : 0)
        }
      } else {
        // ensure stops is populated if flight exists
        if (opt.flight && (opt.flight.stops === undefined || opt.flight.stops === null)) {
          try {
            const segs = opt.flight.segments || (o.segments ? JSON.parse(o.segments) : [])
            opt.flight.stops = Array.isArray(segs) ? Math.max(0, segs.length - 1) : 0
          } catch (e) {
            opt.flight.stops = 0
          }
        }
      }
      // ensure valueScore exists so UI shows the original computed score when available
      if (opt.valueScore === undefined || opt.valueScore === null) {
        if (originalOption && originalOption.valueScore !== undefined && originalOption.valueScore !== null) {
          opt.valueScore = originalOption.valueScore
        } else if (o.valueScore !== undefined && o.valueScore !== null) {
          opt.valueScore = o.valueScore
        } else {
          opt.valueScore = 0
        }
      }
      // If valueScore is still 0, compute a lightweight fallback so UI shows meaningful data
      if (!opt.valueScore || Number(opt.valueScore) === 0) {
        try {
          const price = Number(opt.totalPrice || 0)
          const rating = (opt.lodging && opt.lodging.rating) ? Number(opt.lodging.rating) : (o.rating || 0)
          const stops = (opt.flight && typeof opt.flight.stops === 'number') ? opt.flight.stops : 0
          const priceScore = price > 0 ? (1.0 / (1.0 + Math.log(1 + price))) : 0
          const ratingScore = rating ? (rating / 5.0) : 0
          const stopsPenalty = Math.max(0, 1.0 - stops * 0.2)
          let score = priceScore * 0.6 + ratingScore * 0.3 + stopsPenalty * 0.1
          score = Math.max(0, Math.min(1, score))
          opt.valueScore = Math.round(score * 1000) / 1000
        } catch (e) {
          opt.valueScore = 0
        }
      }
      // preserve buyWait if present from server / saved option
      try {
        opt.buyWait = opt.buyWait || (originalOption && (originalOption as any).buyWait) || (o && (o as any).buyWait) || undefined
      } catch (e) {
        opt.buyWait = undefined
      }
      // preserve mlRecommendation as well
      try {
        opt.mlRecommendation = opt.mlRecommendation || (originalOption && (originalOption as any).mlRecommendation) || (o && (o as any).mlRecommendation) || undefined
      } catch (e) {
        opt.mlRecommendation = undefined
      }
      opt.__raw = opt
      return { option: opt as TripOptionDTO, legacy: false }
    }
    // legacy minimal option
    const segs = (() => {
      try { return o.segments ? JSON.parse(o.segments) : [] } catch (e) { return [] }
    })()
    const opt: any = {
      id: o.id,
      totalPrice: o.totalPrice,
      currency: o.currency,
      flight: {
        airline: o.airlineName || o.airline,
        airlineCode: o.airlineCode,
        airlineName: o.airlineName,
        flightNumber: o.flightNumber,
        durationText: o.durationText,
        segments: segs,
        stops: Array.isArray(segs) ? Math.max(0, segs.length - 1) : 0
      },
      lodging: undefined,
      valueScore: o.valueScore || 0,
      buyWait: (o && (o as any).buyWait) || undefined,
      __raw: o
    }
    return { option: opt as TripOptionDTO, legacy: true }
  }

  return (
    <div>
      <h2 className="text-xl font-semibold mb-4">Saved Offers</h2>
      <div className="mb-3">
        <button className="px-3 py-1 border rounded mr-2" onClick={() => {
          const filename = `traveloptimizer-saved-${new Date().toISOString().replace(/[:.]/g,'-')}.json`
          downloadJson(filename, { offers, createdAt: new Date().toISOString() })
        }}>Export Saved JSON</button>
        <button className="px-3 py-1 border rounded" onClick={() => {
          const filename = `traveloptimizer-saved-${new Date().toISOString().replace(/[:.]/g,'-')}.csv`
          const rows = offers.map((o: any) => ({
            tripOptionId: o.tripOptionId || o.id,
            totalPrice: o.totalPrice,
            currency: o.currency,
            valueScore: o.valueScore || (o.option && o.option.valueScore) || 0,
            airlineName: o.airlineName || (o.option && o.option.flight && o.option.flight.airlineName) || '',
            airlineCode: o.airlineCode || (o.option && o.option.flight && o.option.flight.airlineCode) || '',
            flightNumber: o.flightNumber || (o.option && o.option.flight && o.option.flight.flightNumber) || '',
            stops: (() => {
              try { const segs = o.segments ? JSON.parse(o.segments) : (o.option && o.option.flight && o.option.flight.segments) || []; return Array.isArray(segs) ? Math.max(0, segs.length - 1) : '' } catch (e) { return '' }
            })(),
            durationText: o.durationText || (o.option && o.option.flight && o.option.flight.durationText) || '',
            segments: (() => { try { const segs = o.segments ? JSON.parse(o.segments) : (o.option && o.option.flight && o.option.flight.segments) || []; return Array.isArray(segs) ? segs.join('|') : '' } catch (e) { return '' } })()
          }))
          downloadCsv(filename, rows, ['tripOptionId','totalPrice','currency','valueScore','airlineName','airlineCode','flightNumber','stops','durationText','segments'])
        }}>Export Saved CSV</button>
      </div>
      {offers.length === 0 && <div className="text-sm text-gray-500">No saved offers</div>}
      <div className="space-y-3">
        {offers.map(o => {
          const { option, legacy } = mapSavedToOption(o)
          // try to compute deal score from cached results for the same searchId
          const cached = getCachedOptionsForSearch(o.searchId)
          const scoreMap = cached && cached.length > 0 ? computeDealScores(cached) : new Map()
          const info = scoreMap.get(option.id)
          return (
            <div id={`saved-offer-${o.id}`} key={o.id} className="p-3 border rounded bg-white">
              <div className="flex justify-between items-start">
                <div className="flex-1">
                  <TripCard searchId={o.searchId || 'saved'} option={option} isSaved={true} expandedOverride={selectedOfferId === o.id} dealScore={info?.score ?? null} dealLabel={info?.label} percentileText={info?.percentileText} />
                </div>
                <div className="ml-4 flex items-start">
                    <div className="flex flex-col gap-2">
                      <div className="text-sm font-medium">{option.currency} {option.totalPrice}</div>
                      {/* Watch toggle and baseline/last seen */}
                      <div className="text-xs text-gray-600">
                        <label className="flex items-center gap-2">
                          <input type="checkbox" checked={Boolean(watch.getWatchForOffer(o.id)?.watchEnabled)} onChange={(e) => {
                            const enabled = e.target.checked
                            const baseline = option.totalPrice
                            watch.toggleWatch(o.id, enabled, baseline, option.currency || 'USD')
                          }} />
                          <span>Watch Price</span>
                        </label>
                        <div>
                          Baseline: {option.currency} {watch.getWatchForOffer(o.id)?.baselineTotalPrice ?? option.totalPrice}
                        </div>
                        <div>
                          Last seen: {watch.getWatchForOffer(o.id)?.lastSeenPrice ? `${option.currency} ${watch.getWatchForOffer(o.id)?.lastSeenPrice}` : 'n/a'}
                        </div>
                      </div>
                      <button className="px-2 py-1 border rounded text-sm" onClick={() => handleRemove(o.id)}>Remove</button>
                    </div>
                </div>
              </div>
              {legacy && (
                <div className="mt-2 text-sm text-gray-500">(Legacy saved item: details may be unavailable.)</div>
              )}
            </div>
          )
        })}
      </div>
      <div className="mt-6">
        <NotificationsPanel onOpen={(offerId) => {
          setSelectedOfferId(offerId)
          // scroll the corresponding saved offer into view
          setTimeout(() => {
            try {
              const el = document.getElementById(`saved-offer-${offerId}`)
              if (el) el.scrollIntoView({ behavior: 'smooth', block: 'center' })
            } catch (e) {}
          }, 150)
          // clear selection after some time so the user can collapse manually
          setTimeout(() => setSelectedOfferId(null), 8000)
        }} />
      </div>
    </div>
  )
}
