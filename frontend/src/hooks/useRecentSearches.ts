import { useCallback } from 'react'

const KEY = 'traveloptimizer:recentSearches'

export type RecentSearch = {
  searchId: string
  origin: string
  destination: string
  earliestDeparture?: string
  latestDeparture?: string
  createdAt: string
}

export function saveRecentSearch(item: RecentSearch) {
  try {
    const raw = localStorage.getItem(KEY)
    const arr: RecentSearch[] = raw ? JSON.parse(raw) : []
    const filtered = arr.filter((s) => s.searchId !== item.searchId)
    filtered.unshift(item)
    const trimmed = filtered.slice(0, 20)
    localStorage.setItem(KEY, JSON.stringify(trimmed))
  } catch (e) {
    console.warn('failed to save recent', e)
  }
}

export function getRecentSearches(): RecentSearch[] {
  try {
    const raw = localStorage.getItem(KEY)
    return raw ? JSON.parse(raw) : []
  } catch (e) {
    return []
  }
}

export default function useRecentSearches() {
  const add = useCallback((item: RecentSearch) => saveRecentSearch(item), [])
  const list = getRecentSearches()
  return { add, list }
}
