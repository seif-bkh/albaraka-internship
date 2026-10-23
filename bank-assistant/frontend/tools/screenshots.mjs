// Screenshots of every route against the mock API. Usage:
//   node tools/mock-api.mjs &  npx ng serve --proxy-config proxy.conf.json &
//   node tools/screenshots.mjs [outDir]
// Requires `puppeteer` (npm i -D puppeteer) — not a project dependency.
import puppeteer from 'puppeteer';
import fs from 'node:fs';
import path from 'node:path';
import { createRequire } from 'node:module';

const out = process.argv[2] || 'screenshots';
fs.mkdirSync(out, { recursive: true });
const base = process.env.BASE || 'http://localhost:4200';
const browser = await puppeteer.launch({
  executablePath: process.env.CHROME_PATH || undefined,
  args: ['--no-sandbox', '--disable-setuid-sandbox', '--disable-gpu', '--font-render-hinting=none'],
});
const page = await browser.newPage();
await page.setViewport({ width: 1440, height: 900, deviceScaleFactor: 1 });

const shot = async (name, full = true) => {
  await new Promise(r => setTimeout(r, 900));
  await page.screenshot({ path: path.join(out, `${name}.png`), fullPage: full });
  console.log('✓', name);
};

// 1. login (empty state), then log in
await page.goto(`${base}/login`, { waitUntil: 'networkidle0' });
await shot('01-login');
await page.type('input[type=email], input[name=email], input[formcontrolname=email]', 'admin@albaraka.tn').catch(() => {});
await page.type('input[type=password]', 'postgres').catch(() => {});
await Promise.all([page.click('button[type=submit]').catch(() => {}), page.waitForNavigation({ waitUntil: 'networkidle0' }).catch(() => {})]);

const routes = ['dashboard', 'documents', 'ingestion', 'facts', 'taxonomy', 'stats', 'monitoring', 'users', 'historique'];
let i = 2;
for (const r of routes) {
  await page.goto(`${base}/${r}`, { waitUntil: 'networkidle0' });
  await shot(`${String(i++).padStart(2, '0')}-${r}`);
}

// monitoring with a selected turn
await page.goto(`${base}/monitoring`, { waitUntil: 'networkidle0' });
await page.click('.turn').catch(() => {});
await shot(`${String(i++).padStart(2, '0')}-monitoring-detail`);

// historique with a conversation opened
await page.goto(`${base}/historique`, { waitUntil: 'networkidle0' });
await page.click('table tbody tr .row-actions a').catch(() => {});
await shot(`${String(i++).padStart(2, '0')}-historique-detail`);

// public chat: fresh, then with an answer
await page.goto(`${base}/chat`, { waitUntil: 'networkidle0' });
await shot(`${String(i++).padStart(2, '0')}-chat-empty`, false);
await page.type('.composer textarea', "c'est quoi le financement Mourabaha ?").catch(() => {});
await page.keyboard.press('Enter');
await new Promise(r => setTimeout(r, 4000));
await page.type('.composer textarea', 'quand puis-je passer pour parler à un conseiller ?').catch(() => {});
await page.keyboard.press('Enter');
await new Promise(r => setTimeout(r, 4000));
await shot(`${String(i++).padStart(2, '0')}-chat-answers`, false);

// mobile chat
await page.setViewport({ width: 390, height: 844, deviceScaleFactor: 2 });
await page.reload({ waitUntil: 'networkidle0' });
await shot(`${String(i++).padStart(2, '0')}-chat-mobile`, false);

await browser.close();
