const puppeteer = require('puppeteer');
(async ()=>{
  const browser = await puppeteer.launch({args:['--no-sandbox','--disable-setuid-sandbox']});
  const page = await browser.newPage();
  page.on('console', m => console.log('PAGE_LOG>', m.type(), m.text()));
  await page.goto(process.argv[2]||'http://localhost:5173', {waitUntil:'networkidle2', timeout:30000});
  await page.evaluate(()=>{
    const origFetch = window.fetch;
    window.fetch = function(...args){
      console.log('fetch-called', args[0], args[1] && args[1].method, args[1] && args[1].body);
      return origFetch.apply(this, args).then(async res => {
        try { const clone = res.clone(); const text = await clone.text(); console.log('fetch-resp', res.status, text.slice(0,200)); } catch(e){ console.log('fetch-resp-error', e.message)}
        return res;
      })
    }
    const origX = window.XMLHttpRequest.prototype.open;
    window.XMLHttpRequest.prototype.open = function(method, url){
      this.addEventListener('load', ()=>{ console.log('xhr-load', method, url, this.status, this.responseText && this.responseText.slice(0,200)) });
      return origX.apply(this, arguments);
    }
  });
  // fill and submit
  await page.type('input[name=origin]', 'SFO');
  await page.type('input[name=destination]', 'LAX');
  await page.click('button[type=submit]');
  await new Promise(r=>setTimeout(r,3000));
  await browser.close();
})();