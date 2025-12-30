import engine from './watchEngine'

const STORAGE_KEY = 'traveloptimizer.savedOffers.v2'
const NOTIF_KEY = 'traveloptimizer.notifications.v1'

type WatchEntry = {
  offerId: string
  tripOptionId?: string
  origin?: string
  destination?: string
  segments?: string | string[]
  airlineCode?: string
  flightNumber?: string
  baselineTotalPrice: number
  baselineCurrency: string
  savedAt: string
  watchEnabled: boolean
  alertThresholdAbsolute?: number
  alertThresholdPercent?: number
  lastCheckedAt?: string
  lastSeenPrice?: number
  lastSeenAt?: string
  lastNotificationAt?: string
}

function readStorage() {
  try { return JSON.parse(localStorage.getItem(STORAGE_KEY) || '{}') } catch (e) { return {} }
}

function writeStorage(obj: any) {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(obj))
}

function readNotifications() {
  try { return JSON.parse(localStorage.getItem(NOTIF_KEY) || '[]') } catch (e) { return [] }
}

function writeNotifications(notifs: any[]) { localStorage.setItem(NOTIF_KEY, JSON.stringify(notifs)) }

export function migrateSavedOffers() {
  // placeholder: if older key exists migrate to v2 (noop for now)
  const old = localStorage.getItem('traveloptimizer.savedOffers')
  if (old && !localStorage.getItem(STORAGE_KEY)) {
    // naive migration: copy
    try { localStorage.setItem(STORAGE_KEY, old); localStorage.removeItem('traveloptimizer.savedOffers') } catch (e) {}
  }
}

export function getWatchForOffer(offerId: string): WatchEntry | null {
  const s = readStorage()
  return s[offerId] || null
}

export function setWatchForOffer(entry: WatchEntry) {
  const s = readStorage()
  s[entry.offerId] = entry
  writeStorage(s)
}

export function toggleWatch(offerId: string, enabled: boolean, baseline?: number, baselineCurrency = 'USD') {
  const s = readStorage()
  const now = new Date().toISOString()
  const existing = s[offerId] || { offerId, baselineTotalPrice: baseline || 0, baselineCurrency, savedAt: now }
  existing.watchEnabled = enabled
  if (baseline !== undefined) existing.baselineTotalPrice = baseline
  existing.lastCheckedAt = existing.lastCheckedAt || null
  s[offerId] = existing
  writeStorage(s)
  return existing
}

export function listWatches() {
  const s = readStorage()
  return Object.values(s)
}

export function addNotification(n: any) {
  const arr = readNotifications()
  // dedupe: if last notification for same offerId has same delta and within 12h skip
  const last = arr.slice().reverse().find((x: any) => x.offerId === n.offerId)
  const now = new Date()
  if (last) {
    const lastT = new Date(last.timestamp)
    if (Math.abs(n.delta - last.delta) < 0.01 && (now.getTime() - lastT.getTime()) < 12 * 3600 * 1000) {
      return false
    }
  }
  arr.unshift(n)
  // keep latest 200
  writeNotifications(arr.slice(0, 200))
  return true
}

export function getNotifications() { return readNotifications() }

// compare engine wrapper: accepts search request and options array
export function processSearchResults(searchRequest: any, options: any[]) {
  migrateSavedOffers()
  const watches = listWatches()
  if (!watches || watches.length === 0) return { updated: [], notifications: [] }
  const result = engine.compareWatchedOffers({ watchedOffers: watches, searchRequest, newOptions: options })
  // persist updated watches
  const s: any = {}
  for (const u of result.updatedOffers) s[u.offerId] = u
  writeStorage(s)
  // persist notifications with dedupe
  for (const n of result.newNotifications) {
    addNotification(n)
  }
  return result
}

export default { migrateSavedOffers, getWatchForOffer, setWatchForOffer, toggleWatch, listWatches, processSearchResults, getNotifications, addNotification }
