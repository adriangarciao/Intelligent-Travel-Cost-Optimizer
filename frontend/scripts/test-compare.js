;(async () => {
  try {
    const mod = await import('../src/lib/watchEngine.js')
    const engine = mod.default || mod

    function expect(cond, msg) { if (!cond) throw new Error(msg) }

    // baseline 700 -> current 650 triggers drop
    const saved = [{ offerId: 'a1', baselineTotalPrice: 700, origin: 'SFO', destination: 'LAX', watchEnabled: true }]
    const options = [{ tripOptionId: 'opt1', totalPrice: 650, __raw: { origin: 'SFO', destination: 'LAX' }, flight: { segments: ['SFO→LAX'] } }]
    const res = engine.compareWatchedOffers({ watchedOffers: saved, searchRequest: { origin: 'SFO', destination: 'LAX' }, newOptions: options })
    expect(res.newNotifications.length === 1, 'Should notify for sufficient drop')

    // below threshold
    const saved2 = [{ offerId: 'a2', baselineTotalPrice: 700, origin: 'SFO', destination: 'LAX', watchEnabled: true, alertThresholdAbsolute: 100 }]
    const options2 = [{ tripOptionId: 'opt2', totalPrice: 650, __raw: { origin: 'SFO', destination: 'LAX' }, flight: { segments: ['SFO→LAX'] } }]
    const res2 = engine.compareWatchedOffers({ watchedOffers: saved2, searchRequest: { origin: 'SFO', destination: 'LAX' }, newOptions: options2 })
    expect(res2.newNotifications.length === 0, 'Should not notify when below absolute threshold')

    // not found does not notify
    const saved3 = [{ offerId: 'a3', baselineTotalPrice: 500, origin: 'NYC', destination: 'LAX', watchEnabled: true }]
    const options3 = [{ tripOptionId: 'opt3', totalPrice: 450, __raw: { origin: 'SFO', destination: 'LAX' }, flight: { segments: ['SFO→LAX'] } }]
    const res3 = engine.compareWatchedOffers({ watchedOffers: saved3, searchRequest: { origin: 'SFO', destination: 'LAX' }, newOptions: options3 })
    expect(res3.newNotifications.length === 0, 'No notify when match not found')

    console.log('All tests passed')
  } catch (e) {
    console.error(e)
    process.exit(1)
  }
})()
