import React from 'react'
import { OfferSnapshot, encodeSharePayload, downloadJson, downloadCsv, buildOfferSummaryText } from '../utils/shareExport'
import { triggerToast } from './Toast'

export default function ShareExportModal({ items, onClose }: { items: OfferSnapshot[], onClose: () => void }) {
  return (
    <div className="fixed inset-0 bg-black bg-opacity-40 z-50 flex items-center justify-center">
      <div className="bg-white rounded shadow-lg p-6 w-full max-w-lg">
        <h3 className="text-lg font-semibold mb-3">Share / Export</h3>
        <div className="text-sm text-gray-600 mb-3">Share link contains only the selected offer details (no personal data).</div>
        <div className="flex flex-col gap-2">
          <button className="px-3 py-2 border rounded text-left" onClick={async () => {
            try {
              const payload = encodeSharePayload(items)
              const url = window.location.origin + window.location.pathname + '?share=' + payload
              await navigator.clipboard.writeText(url)
              triggerToast('Share link copied to clipboard')
            } catch (e) { triggerToast('Copy failed') }
          }}>Copy share link</button>

          <button className="px-3 py-2 border rounded text-left" onClick={() => {
            const filename = `traveloptimizer-compare-${new Date().toISOString().replace(/[:.]/g,'-')}.json`
            downloadJson(filename, { offers: items, createdAt: new Date().toISOString() })
          }}>Download JSON</button>

          <button className="px-3 py-2 border rounded text-left" onClick={() => {
            const filename = `traveloptimizer-compare-${new Date().toISOString().replace(/[:.]/g,'-')}.csv`
            const rows = items.map((it: any) => ({
              tripOptionId: it.tripOptionId || it.id,
              totalPrice: it.totalPrice,
              currency: it.currency,
              valueScore: it.valueScore,
              airlineName: it.flight?.airlineName || '',
              airlineCode: it.flight?.airlineCode || '',
              flightNumber: it.flight?.flightNumber || '',
              stops: it.flight?.stops ?? '',
              durationText: it.flight?.durationText || '',
              segments: Array.isArray(it.flight?.segments) ? it.flight.segments.join('|') : ''
            }))
            downloadCsv(filename, rows, ['tripOptionId','totalPrice','currency','valueScore','airlineName','airlineCode','flightNumber','stops','durationText','segments'])
          }}>Download CSV</button>

          <button className="px-3 py-2 border rounded text-left" onClick={async () => {
            try {
              const txt = buildOfferSummaryText(items)
              await navigator.clipboard.writeText(txt)
              triggerToast('Summary copied to clipboard')
            } catch (e) { triggerToast('Copy failed') }
          }}>Copy Summary</button>

          {navigator.share && (
            <button className="px-3 py-2 border rounded text-left" onClick={() => {
              try {
                const payload = encodeSharePayload(items)
                const url = window.location.origin + window.location.pathname + '?share=' + payload
                // eslint-disable-next-line @typescript-eslint/no-floating-promises
                navigator.share({ title: 'Compare offers', text: 'Compare offers from TravelOptimizer', url }).catch(() => {})
              } catch (e) {}
            }}>Share via device</button>
          )}

          <div className="mt-3 flex justify-end gap-2">
            <button className="px-3 py-1 border rounded" onClick={onClose}>Close</button>
          </div>
        </div>
      </div>
    </div>
  )
}
