import React, { useMemo, useState, useEffect } from 'react'
import DealMeter from './DealMeter'
import useCompare from '../hooks/useCompare'
import { triggerToast } from './Toast'
import { SavedItem } from '../hooks/useSavedItems'
import type { TripOptionDTO, FlightSummary } from '../types/api'
import { saveOffer } from '../lib/api'

type Props = {
  searchId: string
  option: TripOptionDTO
  onSave?: (item: SavedItem) => void
  isSaved?: boolean
}

export type TripCardProps = Props

export default function TripCard({ searchId, option, onSave, isSaved, }: Props & { expandedOverride?: boolean, dealScore?: number, dealLabel?: string, percentileText?: string }) {
  const optionId = option.id || JSON.stringify(option)
  const totalPrice = option.totalPrice ?? 0
  const currency = option.currency || 'USD'
  const valueScore = option.valueScore ?? '—'

  // Prefer the structured `flight` object. Some servers return a flattened `flightSummary` string;
  // avoid treating that string as the flight object.
  const flight: FlightSummary | undefined = (typeof (option as any).flight === 'object' && (option as any).flight)
    || (typeof (option as any).flightSummary === 'object' && (option as any).flightSummary) as FlightSummary | undefined

  const flightNumberStr = flight?.flightNumber ?? ''
  const parsedFlightNumbers = useMemo(() =>
    (flightNumberStr || '')
      .split('/')
      .map(s => s.trim())
      .filter(Boolean),
    [flightNumberStr]
  )

  const rawSegments = flight?.segments ?? []
  const segments = rawSegments.map(s => {
    const str = String(s || '')
    // Replace known mojibake or arrow characters with a clean arrow between 3-letter codes
    const fixed = str.replace(/([A-Z]{3})[^A-Z0-9]+([A-Z]{3})/, '$1 → $2')
    return fixed
  })

  const [expanded, setExpanded] = useState(false)
  const [showBreakdown, setShowBreakdown] = useState(false)

  const [saving, setSaving] = useState(false)
  const [savedId, setSavedId] = useState<string | null>(null)
  const compare = useCompare()
  const compareId = `${searchId}:${optionId}`

  // support external control to force expansion (e.g. open from a notification)
  // read `expandedOverride` from the props object passed in (TypeScript single-site typing above)
  const expandedOverride = (arguments[0] as any)?.expandedOverride as boolean | undefined
  useEffect(() => {
    if (typeof expandedOverride === 'boolean') setExpanded(expandedOverride)
  }, [expandedOverride])

  async function handleSave() {
    // optimistic
    setSaving(true)
    try {
      const payload = {
        tripOptionId: option.id,
        origin: option.__raw?.origin ?? option.flight?.origin,
        destination: option.__raw?.destination ?? option.flight?.destination,
        departDate: option.__raw?.departDate ?? null,
        returnDate: option.__raw?.returnDate ?? null,
        totalPrice: option.totalPrice,
        currency: option.currency,
        airlineCode: option.flight?.airlineCode ?? option.__raw?.flight?.airlineCode,
        airlineName: option.flight?.airlineName ?? option.__raw?.flight?.airlineName,
        flightNumber: option.flight?.flightNumber ?? option.__raw?.flight?.flightNumber,
        durationText: option.flight?.durationText ?? option.__raw?.flight?.durationText,
        segments: JSON.stringify(option.flight?.segments ?? option.__raw?.flight?.segments ?? []),
        note: null,
        // include the full option payload so saved-offers have the complete TripOptionDTO
        option: option.__raw ?? option
      }
      const res = await saveOffer(payload)
      // server returns created id (string)
      setSavedId(String(res))
    } catch (e) {
      // undo optimistic
      // eslint-disable-next-line no-console
      console.error('Failed to save offer', e)
      alert('Failed to save offer')
    } finally {
      setSaving(false)
    }
  }

  return (
    <div className="bg-white p-4 rounded shadow">
      {/* TEMP DEBUG: log exact option being rendered */}
      {typeof window !== 'undefined' && (console.log && console.log('TripCard render option=', option))}
      <div className="flex justify-between items-start">
        <div>
          <div className="text-sm text-gray-500">Price</div>
          <div className="text-lg font-semibold">{currency} {totalPrice}</div>
          <div className="mt-2 text-sm">Value score: {valueScore}</div>
          <div className="mt-2 text-sm text-gray-600 flex items-center gap-2">
            <span>{flight?.airlineName ?? flight?.airline ?? 'Flight'}</span>
            <span className="text-xs text-gray-500">{flightNumberStr || ''}</span>
            {flightNumberStr && (
              <span className="text-xs text-gray-400" title="Multiple flight numbers = connecting itinerary. Click details to see each leg.">i</span>
            )}
          </div>
          {option && <div className="mt-2 text-xs italic text-gray-500">{option.mlNote}</div>}
        </div>

        <div className="flex flex-col items-end gap-2">
          <div className="flex items-center gap-2">
            <button
              className="px-3 py-1 border rounded text-sm"
              onClick={() => setExpanded(e => !e)}
              aria-expanded={expanded}
            >
              {expanded ? 'Hide details' : 'View details'}
            </button>
            {!isSaved && (
              <button
                className="bg-green-600 text-white px-3 py-1 rounded text-sm"
                onClick={async () => {
                  await handleSave()
                  if (onSave) {
                    onSave({
                      id: `${searchId}:${optionId}`,
                      searchId,
                      optionId: String(optionId),
                      title: flight?.airlineName ?? 'Option',
                      price: totalPrice,
                      currency,
                      createdAt: new Date().toISOString()
                    })
                  }
                }}
                disabled={saving}
              >
                {saving ? 'Saving...' : (savedId ? 'Saved' : 'Save')}
              </button>
            )}
            <button
              className={`px-3 py-1 rounded text-sm border ${compare.has(compareId) ? 'bg-blue-600 text-white' : 'bg-white text-gray-700'}` + (compare.ids.length >= 3 && !compare.has(compareId) ? ' opacity-60 cursor-not-allowed' : '')}
              onClick={() => {
                if (compare.ids.length >= 3 && !compare.has(compareId)) {
                  triggerToast('You can compare up to 3 flights')
                  return
                }
                const snap = {
                  id: compareId,
                  tripOptionId: optionId,
                  totalPrice,
                  currency,
                  valueScore: typeof valueScore === 'number' ? valueScore : (Number(valueScore) || 0),
                  flight
                }
                const res = compare.toggle(compareId, snap as any)
                if (!res.ok && res.message) triggerToast(res.message)
              }}
              disabled={compare.ids.length >= 3 && !compare.has(compareId)}
            >
              {compare.has(compareId) ? 'Compare ✓' : 'Compare'}
            </button>
            {isSaved && (
              <div className="px-3 py-1 rounded text-sm text-gray-600">Saved</div>
            )}
          </div>
          <div className="text-xs text-gray-500">{flight?.durationText ?? ''}</div>
        </div>
      </div>

      <div className={`mt-3 overflow-hidden transition-all duration-200 ${expanded ? 'max-h-96' : 'max-h-0'}`}>
        <div className="p-3 border rounded bg-gray-50">
          {(!flight || (segments.length === 0 && parsedFlightNumbers.length === 0)) ? (
            <div className="text-sm text-gray-500">Flight details unavailable.</div>
          ) : (
            <div>
              <div className="text-sm text-gray-700">Duration: {flight?.durationText ?? '—'}</div>
              <div className="text-sm text-gray-700">Stops: {typeof flight?.stops === 'number' ? (flight!.stops === 0 ? 'Nonstop' : String(flight!.stops)) : '—'}</div>

              <div className="mt-2">
                <div className="text-sm font-medium text-gray-800">Segments</div>
                <ul className="mt-1 list-inside list-decimal text-sm text-gray-700">
                  {segments.length > 0 ? (
                            segments.map((seg, idx) => {
                      const pn = parsedFlightNumbers[idx]
                      return (
                        <li key={idx} className="py-1">
                          <span className="font-medium">Segment {idx + 1}:</span> {seg} {pn ? <span className="text-gray-600">— {pn}</span> : null}
                        </li>
                      )
                    })
                  ) : parsedFlightNumbers.length > 0 ? (
                    parsedFlightNumbers.map((pn, idx) => (
                      <li key={idx} className="py-1">Segment {idx + 1}: {pn}</li>
                    ))
                  ) : (
                    <li className="py-1 text-gray-500">No segment breakdown available.</li>
                  )}
                </ul>

                {segments.length !== parsedFlightNumbers.length && parsedFlightNumbers.length > 0 && (
                  <div className="mt-2 text-sm text-gray-600">Flight numbers: {parsedFlightNumbers.join(' / ')}</div>
                )}
              </div>

              {option.valueScoreBreakdown && (
                <div className="mt-3">
                  <button className="text-sm text-blue-600 underline" onClick={() => setShowBreakdown(s => !s)}>
                    {showBreakdown ? 'Hide' : 'Why this value score?'}
                  </button>
                  {showBreakdown && (
                    <div className="mt-2 text-sm text-gray-700">
                      {Object.entries(option.valueScoreBreakdown).map(([k, v]) => (
                        <div key={k} className="flex justify-between">
                          <div className="capitalize">{k.replace(/([A-Z])/g, ' $1').trim()}</div>
                          <div>{(v as number).toFixed(3)}</div>
                        </div>
                      ))}
                    </div>
                  )}
                </div>
              )}
              {/* Deal meter: render under breakdown or segments */}
              <DealMeter dealScore={(arguments[0] as any)?.dealScore ?? null} label={(arguments[0] as any)?.dealLabel} percentileText={(arguments[0] as any)?.percentileText} />
            </div>
          )}
        </div>
      </div>
    </div>
  )
}
