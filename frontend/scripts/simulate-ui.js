// Simple node script to simulate frontend fetching and rendering logic
const fetch = globalThis.fetch || require('node-fetch')
const BASE = process.env.VITE_API_BASE_URL || 'http://localhost:8080'
async function run() {
  const payload = {
    origin: 'SFO',
    destination: 'LAX',
    earliestDepartureDate: '2026-01-10',
    latestDepartureDate: '2026-01-20',
    maxBudget: 1500.00,
    numTravelers: 1
  }
  // create search
  const create = await fetch(`${BASE}/api/trips/search`, { method: 'POST', headers: {'Content-Type':'application/json'}, body: JSON.stringify(payload) })
  if (!create.ok) { console.error('Create failed', create.status, await create.text()); process.exit(2) }
  const created = await create.json()
  console.log('searchId:', created.searchId)

  // fetch options page
  const opts = await fetch(`${BASE}/api/trips/${encodeURIComponent(created.searchId)}/options?page=0&size=10`)
  if (!opts.ok) { console.error('Options fetch failed', opts.status, await opts.text()); process.exit(3) }
  const body = await opts.json()
  console.log('raw backend page keys:', Object.keys(body))
  const content = (body.options || []).map(o => ({
    id: o.tripOptionId,
    totalPrice: o.totalPrice,
    currency: o.currency || 'USD',
    flightAirline: o.flight?.airline,
    lodgingName: o.lodging?.hotelName,
    mlNote: o.mlRecommendation?.note
  }))
  console.log('options count:', content.length)
  if (content.length>0) console.log('first option sample:', JSON.stringify(content[0], null, 2))
}
run().catch(e=>{ console.error(e); process.exit(1) })
