import { encodeSharePayload, decodeSharePayload } from './shareExport'

test('encode/decode roundtrip and schema', () => {
  const offers = [{ id: 'o1', tripOptionId: 'opt1', totalPrice: 100, currency: 'USD', valueScore: 0.5 }]
  const encoded = encodeSharePayload(offers as any)
  expect(typeof encoded).toBe('string')
  const decoded = decodeSharePayload(encoded)
  expect(decoded.ok).toBe(true)
  if (decoded.ok) {
    expect(decoded.payload.v).toBe(1)
    expect(Array.isArray(decoded.payload.offers)).toBe(true)
    expect(decoded.payload.offers[0].id).toBe('o1')
  }
})

test('decode rejects oversized payload', () => {
  // craft a long string > 2000 chars
  const big = 'x'.repeat(3000)
  const res = decodeSharePayload(big)
  expect(res.ok).toBe(false)
})
