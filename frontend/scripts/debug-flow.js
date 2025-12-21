const puppeteer = require('puppeteer');
(async ()=>{
  const browser = await puppeteer.launch({args:['--no-sandbox','--disable-setuid-sandbox']});
  const page = await browser.newPage();
  const logs = [];
  page.on('console', msg => logs.push({type: msg.type(), text: msg.text()}));
  // capture failed requests and responses
  const requests = [];
  page.on('request', req => {
    if (req.url().includes('/api/trips')) requests.push({event:'request', url:req.url(), method:req.method(), postData: req.postData()});
  });
  page.on('response', async res => {
    const url = res.url();
    if (url.includes('/api/trips')) {
      let body = null;
      try { body = await res.text(); } catch(e){ body = '<binary or no body>' }
      requests.push({event:'response', url, status: res.status(), body});
    }
  });

  const base = process.argv[2]||'http://localhost:5173';
  await page.goto(base, {waitUntil:'networkidle2', timeout:30000});
  // try to fill search form
  try {
    await page.type('input[name=origin]', 'SFO');
    await page.type('input[name=destination]', 'LAX');
    await page.click('button[type=submit]');
    await page.waitForNavigation({waitUntil:'networkidle2', timeout:30000});
  } catch(e){ /* ignore fill errors */ }

  // give time for background requests
  await new Promise((r) => setTimeout(r, 2000));

  console.log('CONSOLE LOGS:');
  console.log(JSON.stringify(logs, null, 2));
  console.log('API REQUESTS:');
  console.log(JSON.stringify(requests, null, 2));

  await browser.close();
})();