const http = require('http');

const payload = JSON.stringify({ origin: 'SFO', destination: 'JFK', earliestDepartureDate: '2026-01-12', latestDepartureDate: '2026-01-14', numTravelers: 1, maxBudget: 2000 });

const postOptions = { hostname: 'localhost', port: 8080, path: '/api/trips/search', method: 'POST', headers: { 'Content-Type': 'application/json', 'Content-Length': Buffer.byteLength(payload) } };

const postReq = http.request(postOptions, (postRes) => {
  let body = '';
  postRes.setEncoding('utf8');
  postRes.on('data', (c) => body += c);
  postRes.on('end', () => {
    try {
      const json = JSON.parse(body);
      console.log('POST status', postRes.statusCode);
      const sid = json.searchId;
      console.log('searchId=', sid);
      const getOptions = { hostname: 'localhost', port: 8080, path: `/api/trips/${sid}/options`, method: 'GET' };
      const getReq = http.request(getOptions, (getRes) => {
        let gbody = '';
        getRes.setEncoding('utf8');
        getRes.on('data', (d) => gbody += d);
        getRes.on('end', () => {
          try {
            const gjson = JSON.parse(gbody);
            console.log('GET status', getRes.statusCode);
            const opt = (gjson.options && gjson.options[0]) || null;
            console.log('option keys:', opt ? Object.keys(opt) : null);
            if (opt) {
              console.log('mlRecommendation present?', opt.mlRecommendation !== null && opt.mlRecommendation !== undefined);
              console.log('buyWait present?', opt.buyWait !== null && opt.buyWait !== undefined);
              console.log('mlRecommendation sample:', JSON.stringify(opt.mlRecommendation, null, 2));
            }
            require('fs').writeFileSync('latest_options.json', JSON.stringify(gjson, null, 2));
            console.log('Wrote latest_options.json');
          } catch (e) { console.error('GET parse error', e); }
        });
      });
      getReq.on('error', (e) => console.error('GET request error', e));
      getReq.end();
    } catch (e) { console.error('POST parse error', e); }
  });
});
postReq.on('error', (e) => console.error('POST request error', e));
postReq.write(payload);
postReq.end();
