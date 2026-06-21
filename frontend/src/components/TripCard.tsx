import { useMemo, useState, useEffect } from 'react'
import DealMeter from './DealMeter'
import useCompare from '../hooks/useCompare'
import { triggerToast } from './Toast'
import ShareExportModal from './ShareExportModal'
import { SavedItem } from '../hooks/useSavedItems'
import type { TripOptionDTO, FlightSummary, TripFlagDTO, FlagSeverity } from '../types/api'
import { saveOffer, sendFeedback } from '../lib/api'

/** Get styling classes for a flag based on severity */
function getFlagStyles(severity: FlagSeverity): { bg: string; text: string; border: string } {
  switch (severity) {
    case 'BAD':
      return { bg: 'bg-red-50', text: 'text-red-700', border: 'border-red-200' }
    case 'WARN':
      return { bg: 'bg-amber-50', text: 'text-amber-700', border: 'border-amber-200' }
    case 'GOOD':
      return { bg: 'bg-green-50', text: 'text-green-700', border: 'border-green-200' }
    case 'INFO':
    default:
      return { bg: 'bg-blue-50', text: 'text-blue-700', border: 'border-blue-200' }
  }
}

/** Get icon for flag severity */
function getFlagIcon(severity: FlagSeverity): string {
  switch (severity) {
    case 'BAD': return '⚠'
    case 'WARN': return '⚡'
    case 'GOOD': return '✓'
    case 'INFO':
    default: return 'ℹ'
  }
}

type Props = {
  searchId: string
  option: TripOptionDTO
  onSave?: (item: SavedItem) => void
  /** Called when the user dismisses this option so the parent can hide it. */
  onDismiss?: (optionId: string) => void
  isSaved?: boolean
}

export type TripCardProps = Props

export default function TripCard({ searchId, option, onSave, onDismiss, isSaved, expandedOverride, dealScore, dealLabel, percentileText, priceTag }: Props & { expandedOverride?: boolean, dealScore?: number, dealLabel?: string, percentileText?: string, priceTag?: 'low'|'high'|undefined }) {
  const optionId = option.id || JSON.stringify(option)
  const totalPrice = option.totalPrice ?? 0
  const currency = option.currency || 'USD'
  const valueScore = option.valueScore ?? '—'

  const mlRec = (option as any).mlRecommendation ?? (option as any).buyWait ?? null
  
  // Normalize action and trend for display
  const actionRaw = (mlRec && (mlRec.action ?? mlRec.decision)) || 'WAIT'
  const action = String(actionRaw || '').toUpperCase()
  let actionClass = 'bg-yellow-100 text-yellow-800'
  if (action === 'BUY') actionClass = 'bg-green-100 text-green-800'
  else if (action === 'WAIT') actionClass = 'bg-red-100 text-red-800'

  const rawTrend = (mlRec && mlRec.trend) || 'stable'
  const trend = (typeof rawTrend === 'string') && rawTrend === rawTrend.toUpperCase()
    ? rawTrend
    : (typeof rawTrend === 'string' ? (rawTrend.charAt(0).toUpperCase() + rawTrend.slice(1).toLowerCase()) : rawTrend)
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
  const [showShare, setShowShare] = useState(false)

  // support external control to force expansion (e.g. open from a notification)
  useEffect(() => {
    if (typeof expandedOverride === 'boolean') setExpanded(expandedOverride)
  }, [expandedOverride])

  // Fields the smart-filter backend analyzes for SAVE/DISMISS events.
  function feedbackFields() {
    return {
      tripOptionId: option.id,
      searchId,
      airlineCode: flight?.airlineCode ?? option.__raw?.flight?.airlineCode,
      stops: typeof flight?.stops === 'number' ? flight.stops : undefined,
      durationMinutes: typeof flight?.duration === 'number' ? flight.duration : undefined,
      price: typeof totalPrice === 'number' ? totalPrice : undefined,
    }
  }

  function handleDismiss() {
    sendFeedback({ eventType: 'DISMISS', ...feedbackFields() })
    if (onDismiss) onDismiss(String(optionId))
  }

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
      // Record the save so the smart-filter engine can learn preferences.
      sendFeedback({ eventType: 'SAVE', ...feedbackFields() })
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
    <div className={`p-4 rounded card card-hoverable card-accent ${priceTag === 'low' ? 'low' : priceTag === 'high' ? 'high' : ''}`}>
      <div className="flex justify-between items-start">
        <div>
          <div className="flex items-center gap-3">
            <div>
              <div className="text-sm muted">Price</div>
              <div className="text-lg font-semibold">{currency} {totalPrice}</div>
            </div>
            {priceTag === 'low' && <div className="price-badge price-low">Best price</div>}
            {priceTag === 'high' && <div className="price-badge price-high">High price</div>}
          </div>
          <div className="mt-2 text-sm">Value score: {valueScore}</div>
          <div className="mt-2 text-sm text-gray-600 flex items-center gap-2">
            <span>{flight?.airlineName ?? flight?.airline ?? 'Flight'}</span>
            <span className="text-xs text-gray-500">{flightNumberStr || ''}</span>
            {flightNumberStr && (
              <span className="text-xs text-gray-400" title="Multiple flight numbers = connecting itinerary. Click details to see each leg.">i</span>
            )}
          </div>

          {/* Explainability Flags */}
          {option.flags && option.flags.length > 0 && (
            <div className="mt-3 flex flex-wrap gap-2">
              {option.flags.map((flag: TripFlagDTO, idx: number) => {
                const styles = getFlagStyles(flag.severity)
                const icon = getFlagIcon(flag.severity)
                return (
                  <div
                    key={flag.code || idx}
                    className={`inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-medium border ${styles.bg} ${styles.text} ${styles.border}`}
                    title={flag.details || flag.title}
                  >
                    <span>{icon}</span>
                    <span>{flag.title}</span>
                  </div>
                )
              })}
            </div>
          )}

          {option && option.mlNote && <div className="mt-2 text-xs italic text-gray-500">{option.mlNote}</div>}
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
                className="btn"
                style={{backgroundColor: 'var(--dry-sage)', color: 'white'}}
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
              className={`px-3 py-1 rounded text-sm border ${compare.has(compareId) ? 'btn' : 'bg-white text-gray-700'}` + (compare.ids.length >= 3 && !compare.has(compareId) ? ' opacity-60 cursor-not-allowed' : '')}
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
            <button className="btn" onClick={() => setShowShare(true)}>Share</button>
            {onDismiss && (
              <button
                className="px-2 py-1 rounded text-sm border bg-white text-gray-500 hover:text-gray-700"
                onClick={handleDismiss}
                title="Not interested — hide this option and refine suggestions"
              >
                Dismiss
              </button>
            )}
            {isSaved && (
              <div className="px-3 py-1 rounded text-sm text-gray-600">Saved</div>
            )}
          </div>
          <div className="text-xs text-gray-500">{flight?.durationText ?? ''}</div>
        </div>
      </div>

      <div className={`mt-3 overflow-hidden transition-all duration-200 ${expanded ? 'max-h-[800px]' : 'max-h-0'}`}>
        <div className="p-3 border rounded bg-gray-50">
          {(!flight || (segments.length === 0 && parsedFlightNumbers.length === 0)) ? (
            <div>
              <div className="skeleton h-4 w-48 mb-2 rounded"></div>
              <div className="skeleton h-3 w-36 mb-2 rounded"></div>
              <div className="skeleton h-3 w-full mb-1 rounded"></div>
              <div className="skeleton h-3 w-3/4 mt-2 rounded"></div>
            </div>
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

              {/* Detailed Flag Explanations */}
              {option.flags && option.flags.length > 0 && (
                <div className="mt-3 border-t pt-3">
                  <div className="text-sm font-medium text-gray-800 mb-2">Flight Insights</div>
                  <div className="space-y-2">
                    {option.flags.map((flag: TripFlagDTO, idx: number) => {
                      const styles = getFlagStyles(flag.severity)
                      const icon = getFlagIcon(flag.severity)
                      return (
                        <div
                          key={flag.code || idx}
                          className={`p-2 rounded-lg border ${styles.bg} ${styles.border}`}
                        >
                          <div className={`flex items-center gap-2 font-medium ${styles.text}`}>
                            <span>{icon}</span>
                            <span>{flag.title}</span>
                          </div>
                          {flag.details && (
                            <div className="mt-1 text-xs text-gray-600 pl-5">
                              {flag.details}
                            </div>
                          )}
                        </div>
                      )
                    })}
                  </div>
                </div>
              )}

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
              <DealMeter dealScore={dealScore ?? null} label={dealLabel} percentileText={percentileText} />
              {/* ML Buy/Wait recommendation */}
              <div className="mt-3 border-t pt-3">
                <div className="text-sm font-medium text-gray-800">Buy/Wait Recommendation</div>
                {mlRec ? (
                    <div className="mt-2 text-sm text-gray-700">
                      <div className="flex items-center gap-3">
                        <div className={`px-2 py-1 rounded text-xs font-semibold ${actionClass}`}>
                          {action}
                        </div>
                        <div className="text-xs text-gray-600">Confidence: {((mlRec.confidence ?? 0) * 100).toFixed(0)}%</div>
                        <div className="text-xs text-gray-500">Trend: {trend}</div>
                      </div>
                      {mlRec.reasons && mlRec.reasons.length > 0 && (
                        <div className="mt-2 text-sm">
                          <div className="text-xs font-medium">Why?</div>
                          <ul className="list-disc list-inside text-xs text-gray-700">
                            {mlRec.reasons.map((r: any, idx: number) => <li key={idx}>{r}</li>)}
                          </ul>
                        </div>
                      )}
                    </div>
                  ) : (
                  // Fallback: if external dealLabel/score was provided (from cached scoring), show that instead
                  (dealLabel || typeof dealScore === 'number' || percentileText) ? (
                    <div className="mt-2 text-sm text-gray-700">
                      <div className="flex items-center gap-3">
                        <div className="text-sm font-semibold">{dealLabel ?? 'Recommendation'}</div>
                        {typeof dealScore === 'number' && (
                          <div className="text-xs text-gray-600">Score: {(dealScore * 100).toFixed(0)}%</div>
                        )}
                        {percentileText && (
                          <div className="text-xs text-gray-500">{percentileText}</div>
                        )}
                      </div>
                    </div>
                  ) : (
                    <div className="mt-2 text-sm text-gray-600">No recommendation available</div>
                  )
                )}
              </div>
            </div>
          )}
        </div>
      </div>
      {showShare && (
        <ShareExportModal items={[{ id: compareId, tripOptionId: optionId, totalPrice, currency, valueScore: typeof valueScore === 'number' ? valueScore : Number(valueScore) || 0, flight }]} onClose={() => setShowShare(false)} />
      )}
    </div>
  )
}
