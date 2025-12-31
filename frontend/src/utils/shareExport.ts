export type OfferSnapshot = {
  id: string
  tripOptionId?: string
  totalPrice?: number
  currency?: string
  valueScore?: number
  flight?: any
  lodging?: any
}

type SharePayloadV1 = {
  v: 1
  createdAt: string
  offers: OfferSnapshot[]
}

function base64UrlEncode(input: string) {
  // browser-friendly UTF-8 -> base64
  try {
    if (typeof btoa === 'function') {
      const utf8 = encodeURIComponent(input).replace(/%([0-9A-F]{2})/g, function(match, p1) {
        return String.fromCharCode(parseInt(p1, 16))
      })
      const b64 = btoa(utf8)
      return b64.replace(/=/g, '').replace(/\+/g, '-').replace(/\//g, '_')
    }
  } catch (e) {
    // fall through to Buffer if available
  }
  // Node fallback
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  if (typeof (globalThis as any).Buffer !== 'undefined') {
    // @ts-ignore
    return (globalThis as any).Buffer.from(input).toString('base64').replace(/=/g, '').replace(/\+/g, '-').replace(/\//g, '_')
  }
  throw new Error('No base64 encoder available')
}

function base64UrlDecode(str: string) {
  const b64 = str.replace(/-/g, '+').replace(/_/g, '/')
  const pad = b64.length % 4 === 0 ? '' : '='.repeat(4 - (b64.length % 4))
  const full = b64 + pad
  try {
    if (typeof atob === 'function') {
      const bin = atob(full)
      // decode UTF-8
      const esc = bin.split('').map(function(c) {
        return '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2)
      }).join('')
      return decodeURIComponent(esc)
    }
  } catch (e) {
    // fall through to Buffer
  }
  // Node fallback
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  if (typeof (globalThis as any).Buffer !== 'undefined') {
    // @ts-ignore
    return (globalThis as any).Buffer.from(full, 'base64').toString('utf8')
  }
  throw new Error('No base64 decoder available')
}

export function encodeSharePayload(offers: OfferSnapshot[]) {
  const payload: SharePayloadV1 = { v: 1, createdAt: new Date().toISOString(), offers }
  const json = JSON.stringify(payload)
  return base64UrlEncode(json)
}

export function decodeSharePayload(str: string): { ok: true; payload: SharePayloadV1 } | { ok: false; error: string } {
  try {
    if (typeof str !== 'string' || str.length > 2000) return { ok: false, error: 'Payload too large' }
    const json = base64UrlDecode(str)
    const parsed = JSON.parse(json)
    if (!parsed || parsed.v !== 1 || !Array.isArray(parsed.offers)) return { ok: false, error: 'Invalid schema' }
    return { ok: true, payload: parsed }
  } catch (e) {
    return { ok: false, error: 'Decode error' }
  }
}

export function downloadJson(filename: string, data: any) {
  const blob = new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  document.body.appendChild(a)
  a.click()
  a.remove()
  URL.revokeObjectURL(url)
}

function csvEscape(v: any) {
  if (v === null || v === undefined) return ''
  const s = String(v)
  if (s.includes(',') || s.includes('\n') || s.includes('"') || s.includes('|')) return '"' + s.replace(/"/g, '""') + '"'
  return s
}

export function downloadCsv(filename: string, rows: Record<string, any>[], columns: string[]) {
  const header = columns.join(',')
  const lines = rows.map(r => columns.map(c => csvEscape(r[c])).join(','))
  const csv = [header].concat(lines).join('\n')
  const blob = new Blob([csv], { type: 'text/csv' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  document.body.appendChild(a)
  a.click()
  a.remove()
  URL.revokeObjectURL(url)
}

export function buildOfferSummaryText(offers: OfferSnapshot[]) {
  return offers.map(o => {
    const route = (o.flight && o.flight.origin && o.flight.destination) ? `${o.flight.origin} → ${o.flight.destination}` : (o.tripOptionId || o.id || 'Unknown')
    const price = `${o.currency || 'USD'} ${o.totalPrice ?? '—'}`
    const airline = (o.flight && (o.flight.airlineName || o.flight.airlineCode)) ? `${o.flight.airlineName || ''} (${o.flight.airlineCode || ''})`.trim() : ''
    const flightNumber = o.flight?.flightNumber || ''
    const duration = o.flight?.durationText || ''
    const segments = Array.isArray(o.flight?.segments) ? o.flight.segments.map((s: any) => (typeof s === 'string' ? s : s)).join(' | ') : ''
    return `${route} | ${price} | ${airline} ${flightNumber} | ${duration} | Segments: ${segments}`
  }).join('\n')
}
