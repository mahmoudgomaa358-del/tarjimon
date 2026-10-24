import { chromium } from "playwright";
const browser = await chromium.launch({ args: ["--autoplay-policy=no-user-gesture-required"] });
const page = await browser.newPage();
const logs = [];
page.on("console", m => logs.push(`[console.${m.type()}] ${m.text()}`.slice(0, 500)));
page.on("pageerror", e => logs.push(`[pageerror] ${e.message}`.slice(0, 500)));
page.on("worker", w => logs.push(`[worker started] ${w.url()}`));
page.on("requestfailed", r => logs.push(`[requestfailed] ${r.url().slice(0,150)} ${r.failure()?.errorText}`));
await page.goto("https://mahmoudgomaa358-del.github.io/tarjimon/?t=" + Date.now(), { waitUntil: "networkidle" });
const result = await page.evaluate(() => new Promise(resolve => {
  const t0 = performance.now(); const events = [];
  const w = new Worker("uz-tts-worker.js?t=" + Date.now(), { type: "module" });
  w.onerror = e => resolve({ fatal: "worker onerror: " + (e.message || "no message") + " " + e.filename + ":" + e.lineno, events });
  w.onmessageerror = e => resolve({ fatal: "messageerror", events });
  w.onmessage = ({ data }) => {
    if (data.type === "progress") { if (data.pct % 25 === 0) events.push(`progress ${data.pct} @${Math.round(performance.now()-t0)}ms`); return; }
    events.push(`${data.type} @${Math.round(performance.now()-t0)}ms ${data.message || ""}`);
    if (data.type === "audio") { let peak = 0; for (const x of data.audio) peak = Math.max(peak, Math.abs(x)); resolve({ ok: true, seconds: data.audio.length / data.rate, peak, events }); }
    if (data.type === "error") resolve({ ok: false, events });
  };
  w.postMessage({ id: 1, text: "Salom! Siz bilan tanishganimdan xursandman." });
  setTimeout(() => resolve({ timeout: true, events }), 240000);
}));
console.log(JSON.stringify(result, null, 1));
console.log(logs.join("\n"));
await browser.close();
