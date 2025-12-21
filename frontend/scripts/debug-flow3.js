const puppeteer = require('puppeteer');
(async ()=>{
  const browser = await puppeteer.launch({args:['--no-sandbox','--disable-setuid-sandbox']});
  const page = await browser.newPage();
  await page.goto(process.argv[2]||'http://localhost:5173', {waitUntil:'networkidle2', timeout:30000});
  const formHtml = await page.evaluate(()=>{ const f=document.querySelector('form'); return f ? f.outerHTML.slice(0,1000) : 'NO_FORM' });
  const inputs = await page.evaluate(()=>Array.from(document.querySelectorAll('input')).map(i=>({name:i.name,type:i.type,placeholder:i.placeholder,value:i.value})).slice(0,20));
  console.log('FORM HTML START:', formHtml);
  console.log('INPUTS:', JSON.stringify(inputs,null,2));
  await browser.close();
})();