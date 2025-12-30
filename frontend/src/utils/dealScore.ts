type ScoreInfo = { score: number, label: string, percentileText: string }

function clamp(v: number, lo = 0, hi = 1) { return Math.max(lo, Math.min(hi, v)) }

function getOptionId(opt: any) {
  return opt?.id ?? opt?.tripOptionId ?? opt?.optionId ?? JSON.stringify(opt)
}

export function computeDealScores(options: any[]): Map<string, ScoreInfo> {
  const map = new Map<string, ScoreInfo>()
  if (!Array.isArray(options) || options.length === 0) return map
  const prices: { id: string, price: number }[] = []
  for (const o of options) {
    const id = getOptionId(o)
    const p = Number(o?.totalPrice)
    if (!Number.isFinite(p)) continue
    prices.push({ id, price: p })
  }
  if (prices.length < 2) {
    // fallback: neutral score 0.5 for any identifiable option
    for (const o of options) map.set(getOptionId(o), { score: 0.5, label: 'Not enough data', percentileText: '' })
    return map
  }
  // sort ascending by price (cheapest first)
  prices.sort((a, b) => a.price - b.price)
  const n = prices.length
  const idToRank = new Map<string, number>()
  for (let i = 0; i < prices.length; i++) {
    const id = prices[i].id
    if (!idToRank.has(id)) idToRank.set(id, i) // stable: first index wins on ties
  }
  for (const o of options) {
    const id = getOptionId(o)
    const p = Number(o?.totalPrice)
    if (!Number.isFinite(p) || !idToRank.has(id)) {
      map.set(id, { score: 0.5, label: 'Not enough data', percentileText: '' })
      continue
    }
    const rank = idToRank.get(id) as number
    const percentile = clamp(1 - (rank / (n - 1)))
    const score = clamp(percentile)
    let label = 'Poor'
    if (score >= 0.8) label = 'Great deal'
    else if (score >= 0.6) label = 'Good'
    else if (score >= 0.4) label = 'Fair'
    else label = 'Poor'
    const pct = Math.round(score * 100)
    map.set(id, { score, label, percentileText: `Top ${pct}%` })
  }
  return map
}

export { getOptionId }
