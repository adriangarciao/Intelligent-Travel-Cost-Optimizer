const http = require('http');
const payload = {
  origin: 'SFO',
  destination: 'JFK',
  earliestDepartureDate: '2026-01-12',
  latestDepartureDate: '2026-01-14',
  numTravelers: 1,
  maxBudget: 2000
};
const data = JSON.stringify(payload);
const options = {
  hostname: 'localhost',
  port: 8080,
  path: '/api/trips/search',
  method: 'POST',
  headers: {
    'Content-Type': 'application/json',
    'Content-Length': Buffer.byteLength(data)
  }
};

const req = http.request(options, (res) => {
  let body = '';
  res.setEncoding('utf8');
  res.on('data', (chunk) => body += chunk);
  res.on('end', () => {
    try {
      console.log(body);
    } catch (e) {
      console.error('Failed to parse response', e);
    }
  });
});
req.on('error', (e) => {
  console.error('Request error', e.message);
});
req.write(data);
req.end();
