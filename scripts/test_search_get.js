(async ()=>{
  const payload = {
    origin: 'SFO', destination: 'JFK', earliestDepartureDate: '2026-01-12', latestDepartureDate: '2026-01-14', numTravelers: 1, maxBudget: 2000
  };
  try {
    const res = await fetch('http://localhost:8080/api/trips/search', { method: 'POST', headers: {'Content-Type':'application/json'}, body: JSON.stringify(payload) });
    const body = await res.json();
    console.log('POST /api/trips/search status', res.status);
    const sid = body.searchId;
    console.log('searchId=', sid);
    const res2 = await fetch(`http://localhost:8080/api/trips/${encodeURIComponent(sid)}/options`);
    console.log('GET options status', res2.status);
    const body2 = await res2.json();
    // print paths for first option
    const opt = (body2.options && body2.options[0]) || null;
    console.log('option keys:', opt ? Object.keys(opt) : null);
    if (opt) {
      console.log('mlRecommendation path:', opt.mlRecommendation ? 'present' : 'missing');
      console.log('buyWait path:', opt.buyWait ? 'present' : 'missing');
      console.log('mlRecommendation sample:', JSON.stringify(opt.mlRecommendation, null, 2));
    }
    // save full response
    const fs = await import('fs');
    fs.writeFileSync('latest_options.json', JSON.stringify(body2, null, 2));
    console.log('Wrote latest_options.json');
  } catch (e) {
    console.error('Error', e);
  }
})();
