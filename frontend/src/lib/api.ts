const BASE = (import.meta.env.VITE_API_BASE_URL as string) || 'http://localhost:8080'
console.log('API BASE at runtime:', BASE)
import { v4 as uuidv4 } from 'uuid'
const CLIENT_ID_KEY = 'traveloptimizer.clientId'
export function getClientId() {
  let id = localStorage.getItem(CLIENT_ID_KEY)
  if (!id) {
    id = uuidv4()
    localStorage.setItem(CLIENT_ID_KEY, id)
  }
  return id
}
import type { TripSearchResponseDTO, TripOptionsPageDTO, TripOptionDTO } from './types'

type TripSearchPayload = {
  origin: string
  destination: string
  earliestDepartureDate?: string
  latestDepartureDate?: string
  earliestReturnDate?: string
  latestReturnDate?: string
  maxBudget?: number
  numTravelers?: number
}

// use types in src/lib/types.ts

export async function searchTrips(payload: TripSearchPayload) {
  console.log('searchTrips payload:', payload)
  const res = await fetch(`${BASE}/api/trips/search`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload)
  })
  if (!res.ok) throw new Error(await res.text())
  return (await res.json()) as TripSearchResponseDTO
}

export async function getTripOptions(
  searchId: string,
  page = 0,
  size = 10,
  sortBy?: string,
  sortDir?: 'asc' | 'desc'
) {
  const params = new URLSearchParams()
  params.set('page', String(page))
  params.set('size', String(size))
  if (sortBy) params.set('sortBy', sortBy)
  if (sortDir) params.set('sortDir', sortDir)
  const res = await fetch(`${BASE}/api/trips/${encodeURIComponent(searchId)}/options?${params.toString()}`)
  if (!res.ok) throw new Error(await res.text())
  const body = await res.json() as TripOptionsPageDTO
  // Transform server response { options, totalOptions, ... } -> UI expected { content, totalElements }
  const transformed = {
    ...body,
    content: (body.options || []).map((o: any) => ({
      // normalize ids
      id: o.tripOptionId || o.tripOptionId,
      optionId: o.tripOptionId || o.tripOptionId,
      // pricing
      totalPrice: o.totalPrice,
      price: o.totalPrice,
      currency: o.currency || 'USD',
      valueScore: o.valueScore,
      // flatten ML note
      mlNote: o.mlRecommendation?.note,
      // provide lightweight summary fields used by TripCard
      flightSummary: o.flight?.airline ? `${o.flight.airline} ${o.flight.flightNumber ?? ''}`.trim() : undefined,
      lodgingName: o.lodging?.hotelName,
      // keep original nested objects for detail views
      flight: o.flight,
      lodging: o.lodging,
      // include original object
      __raw: o
    })),
    totalElements: body.totalOptions ?? body.totalElements ?? 0
  }
  return transformed as any
}

// Saved items API (server-backed). Requires X-Client-Id header.
export async function saveSavedItem(payload: any) {
  const res = await fetch(`${BASE}/api/saved`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'X-Client-Id': getClientId()
    },
    body: JSON.stringify(payload)
  })
  if (!res.ok) throw new Error(await res.text())
  const body = await res.text()
  return body
}

export async function listSavedItems() {
  const res = await fetch(`${BASE}/api/saved`, {
    method: 'GET',
    headers: { 'X-Client-Id': getClientId() }
  })
  if (!res.ok) throw new Error(await res.text())
  return (await res.json()) as any[]
}

export async function deleteSavedItem(savedId: string) {
  const res = await fetch(`${BASE}/api/saved/${encodeURIComponent(savedId)}`, {
    method: 'DELETE',
    headers: { 'X-Client-Id': getClientId() }
  })
  if (!res.ok) throw new Error(await res.text())
  return true
}

export async function listRecentSearches(limit = 10) {
  const res = await fetch(`${BASE}/api/trips/recent?limit=${limit}`)
  if (!res.ok) throw new Error(await res.text())
  return (await res.json()) as any[]
}
