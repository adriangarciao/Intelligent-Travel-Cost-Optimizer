// Simple, deterministic match and compare engine used by the frontend watch system.
// Implemented in plain JS so it can be executed by a Node test script.

function scoreMatch(saved, option) {
  // scoring: higher is better
  let score = 0
  if (!saved || !option) return 0
  // exact id match (strong)
  if (saved.tripOptionId && option.tripOptionId && String(saved.tripOptionId) === String(option.tripOptionId)) {
    score += 100
    return score
  }
  // route match
  if (saved.origin && saved.destination && option && option.__raw) {
    const o = option.__raw.origin || option.__raw.origin || option.__raw?.origin || option.__raw?.flight?.origin || option.__raw?.origin
    const d = option.__raw.destination || option.__raw.destination || option.__raw?.destination || option.__raw?.flight?.destination || option.__raw?.destination
    if (String(saved.origin).toLowerCase() === String(o).toLowerCase() && String(saved.destination).toLowerCase() === String(d).toLowerCase()) {
      score += 30
    }
  }
  // segments equality
  try {
    const ssegs = saved.segments ? (Array.isArray(saved.segments) ? saved.segments : JSON.parse(saved.segments)) : []
    const osegs = option.flight && option.flight.segments ? option.flight.segments : []
    if (Array.isArray(ssegs) && Array.isArray(osegs) && ssegs.length > 0 && ssegs.length === osegs.length) {
      let same = true
      for (let i = 0; i < ssegs.length; i++) {
        if (String(ssegs[i]).trim() !== String(osegs[i]).trim()) { same = false; break }
      }
      if (same) score += 20
    }
  } catch (e) {}
  // airline / flight number
  if (saved.airlineCode && option.flight && option.flight.airlineCode && String(saved.airlineCode).toLowerCase() === String(option.flight.airlineCode).toLowerCase()) score += 5
  if (saved.flightNumber && option.flight && option.flight.flightNumber && String(saved.flightNumber).toLowerCase() === String(option.flight.flightNumber).toLowerCase()) score += 5
  // stops
  if (typeof saved.stops === 'number' && option.flight && typeof option.flight.stops === 'number') {
    if (saved.stops === option.flight.stops) score += 5
  }
  return score
}

function matchOffer(saved, options) {
  if (!options || options.length === 0) return null
  let best = null
  let bestScore = 0
  for (const opt of options) {
    const s = scoreMatch(saved, opt)
    if (s > bestScore) { bestScore = s; best = opt }
  }
  // consider a match only if score >= 30 (route match accepted) or exact id
  if (bestScore >= 30) return { match: best, score: bestScore }
  return null
}

function compareWatchedOffers({ watchedOffers, searchRequest, newOptions }) {
  const updated = []
  const notifications = []
  const now = new Date().toISOString()
  for (const w of watchedOffers) {
    if (!w.watchEnabled) { updated.push(w); continue }
    // quick route filter
    if (searchRequest && w.origin && w.destination) {
      if (!(String(w.origin).toLowerCase() === String(searchRequest.origin).toLowerCase() && String(w.destination).toLowerCase() === String(searchRequest.destination).toLowerCase())) {
        // not the same route for this search
        updated.push(w)
        continue
      }
    }
    const res = matchOffer(w, newOptions)
    if (!res) {
      // not found in latest search
      w.lastCheckedAt = now
      w.lastSeenAt = w.lastSeenAt || null
      updated.push(w)
      continue
    }
    const currentPrice = Number(res.match.totalPrice || 0)
    const baseline = Number(w.baselineTotalPrice || 0)
    const delta = currentPrice - baseline
    const percent = baseline !== 0 ? (delta / baseline) * 100 : 0
    // update last seen
    w.lastCheckedAt = now
    w.lastSeenPrice = currentPrice
    w.lastSeenAt = now
    // threshold check
    const hasAbs = Object.prototype.hasOwnProperty.call(w, 'alertThresholdAbsolute')
    const hasPct = Object.prototype.hasOwnProperty.call(w, 'alertThresholdPercent')
    const absThreshold = hasAbs ? Number(w.alertThresholdAbsolute) : 25
    const pctThreshold = hasPct ? Number(w.alertThresholdPercent) : 5
    let notify = false
    if (hasAbs && hasPct) {
      notify = (Math.abs(delta) >= absThreshold) || (Math.abs(percent) >= pctThreshold)
    } else if (hasAbs) {
      notify = (Math.abs(delta) >= absThreshold)
    } else if (hasPct) {
      notify = (Math.abs(percent) >= pctThreshold)
    } else {
      notify = (Math.abs(delta) >= absThreshold) || (Math.abs(percent) >= pctThreshold)
    }
    if (notify) {
      notifications.push({
        id: `${w.offerId}:${now}`,
        offerId: w.offerId,
        timestamp: now,
        origin: w.origin,
        destination: w.destination,
        baseline: baseline,
        current: currentPrice,
        delta: delta,
        percent: percent
      })
    }
    updated.push(w)
  }
  return { updatedOffers: updated, newNotifications: notifications }
}

// ESM-friendly exports for Vite/browser
export { scoreMatch, matchOffer, compareWatchedOffers }
export default { scoreMatch, matchOffer, compareWatchedOffers }
