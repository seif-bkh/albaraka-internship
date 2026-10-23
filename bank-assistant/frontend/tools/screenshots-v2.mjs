// Screenshots of the new client-facing surfaces against the mock API.
//   node tools/mock-api.mjs &  npx ng serve --proxy-config proxy.conf.json &
//   node tools/screenshots-v2.mjs [outDir]
import puppeteer from 'puppeteer';
import chromium from '@sparticuz/chromium';
import fs from 'node:fs';
import path from 'node:path';

const out = process.argv[2] || 'screenshots-v2';
fs.mkdirSync(out, { recursive: true });
const base = process.env.BASE || 'http://localhost:4200';

const browser = await puppeteer.launch({
  executablePath: await chromium.executablePath(),
  args: [...chromium.args, '--font-render-hinting=none'],
  headless: true,
  defaultViewport: { width: 1440, height: 900 },
});
const page = await browser.newPage();
await page.setViewport({ width: 1440, height: 900, deviceScaleFactor: 1 });

const shot = async (name, full = false) => {
  await new Promise(r => setTimeout(r, 1100));
  await page.screenshot({ path: path.join(out, `${name}.png`), fullPage: full });
  console.log('✓', name);
};

// ── Landing ──
await page.goto(`${base}/`, { waitUntil: 'networkidle0' });
await shot('01-landing-hero');
await fullScroll();
await shot('02-landing-full', true);

// widget open
await page.evaluate(() => document.querySelectorAll('.fab').forEach(b => b.click()));
await shot('03-landing-widget-open');

// ── Login ──
await page.goto(`${base}/login`, { waitUntil: 'networkidle0' });
await shot('04-login-client-tab');
await page.evaluate(() => document.querySelectorAll('.tab')[1]?.click());
await shot('05-login-staff-tab');

// ── Register ──
await page.goto(`${base}/register`, { waitUntil: 'networkidle0' });
await shot('06-register');

// ── Portal (client) ──
await page.goto(`${base}/login`, { waitUntil: 'networkidle0' });
await page.type('#email', 'client@albaraka.tn');
await page.type('#password', 'client123');
await Promise.all([
  page.click('button[type=submit]'),
  page.waitForNavigation({ waitUntil: 'networkidle0' }).catch(() => {}),
]);
await shot('07-portal-home', true);

await page.goto(`${base}/portal/assistant`, { waitUntil: 'networkidle0' });
await shot('08-portal-assistant');
// send a message
await page.type('.composer textarea', 'C\'est quoi le financement Mourabaha ?');
await page.keyboard.press('Enter');
await new Promise(r => setTimeout(r, 4500));
await shot('09-portal-assistant-answer');

await page.goto(`${base}/portal/profil`, { waitUntil: 'networkidle0' });
await shot('10-portal-profile', true);

// ── Admin (staff) ──
await page.evaluate(() => { localStorage.clear(); });
await page.goto(`${base}/login`, { waitUntil: 'networkidle0' });
await page.evaluate(() => document.querySelectorAll('.tab')[1]?.click());
await page.type('#email', 'admin@albaraka.tn');
await page.type('#password', 'postgres');
await Promise.all([
  page.click('button[type=submit]'),
  page.waitForNavigation({ waitUntil: 'networkidle0' }).catch(() => {}),
]);
await shot('11-admin-dashboard');

async function fullScroll() {
  await page.evaluate(async () => {
    await new Promise(resolve => {
      let total = 0;
      const step = () => {
        window.scrollBy(0, 700);
        total += 700;
        if (total < document.body.scrollHeight) setTimeout(step, 90);
        else { window.scrollTo(0, 0); setTimeout(resolve, 300); }
      };
      step();
    });
  });
}

await browser.close();
console.log('done →', out);
