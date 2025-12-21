const puppeteer = require('puppeteer');
const url = process.argv[2] || 'http://localhost:5173/results/5afb11b4-f2b3-47c5-81f9-1e86af4f5725';
const out = process.argv[3] || '../../artifacts/results-screenshot.png';
(async () => {
  try {
    const browser = await puppeteer.launch({args: ['--no-sandbox','--disable-setuid-sandbox']});
    const page = await browser.newPage();
    await page.setViewport({width: 1280, height: 900});
    await page.goto(url, {waitUntil: 'networkidle2', timeout: 30000});
    await page.screenshot({path: out, fullPage: true});
    console.log('Saved screenshot to', out);
    await browser.close();
  } catch (e) {
    console.error('Screenshot failed:', e);
    process.exit(1);
  }
})();
