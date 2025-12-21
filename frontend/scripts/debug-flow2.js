const puppeteer = require('puppeteer');
(async ()=>{
  const browser = await puppeteer.launch({args:['--no-sandbox','--disable-setuid-sandbox']});
  const page = await browser.newPage();
  const logs = [];
  page.on('console', msg => logs.push({type: msg.type(), text: msg.text()}));
  page.on('pageerror', err => logs.push({type: 'pageerror', text: err.message}));
  page.on('requestfailed', req => { if (req.url().includes('/api/trips')) requests.push({event:'requestfailed', url:req.url(), failureText: req.failure().errorText}); });
  const requests = [];
  page.on('request', req => { if (req.url().includes('/api/trips')) requests.push({event:'request', url:req.url(), method:req.method(), postData: req.postData()}); });
  page.on('response', async res => { if (res.url().includes('/api/trips')) { let body=null; try{body=await res.text()}catch(e){body='<no body>'} requests.push({event:'response', url:res.url(), status:res.status(), body}); } });

  const base = process.argv[2]||'http://localhost:5173';
  await page.goto(base, {waitUntil:'networkidle2', timeout:30000});
  try {
    await page.type('input[name=origin]', 'SFO');
    await page.type('input[name=destination]', 'LAX');
    await page.click('button[type=submit]');
    await page.waitForNavigation({waitUntil:'networkidle2', timeout:30000});
  } catch(e){ /* ignore */ }
  await new Promise(r=>setTimeout(r,2000));
  console.log('CONSOLE LOGS:', JSON.stringify(logs));
  console.log('API REQUESTS:', JSON.stringify(requests));
  await browser.close();
})();