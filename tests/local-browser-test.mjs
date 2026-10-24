// Serves the repo locally and runs the real worker in Chrome for each model size; saves audio samples.
import { chromium } from "playwright";
import http from "http"; import fs from "fs"; import path from "path";
const root = path.resolve("..");
const types = { ".html":"text/html", ".js":"text/javascript", ".mjs":"text/javascript", ".json":"application/json", ".wasm":"application/wasm", ".onnx":"application/octet-stream" };
const server = http.createServer((q, r) => { const p = path.join(root, decodeURIComponent(q.url.split("?")[0])); if (!fs.existsSync(p) || fs.statSync(p).isDirectory()) { r.writeHead(404); return r.end(); } r.writeHead(200, { "content-type": types[path.extname(p)] || "application/octet-stream" }); fs.createReadStream(p).pipe(r); }).listen(8123);
const browser = await chromium.launch();
function wav(a, rate) { const b = Buffer.alloc(44 + a.length*2); b.write("RIFF",0); b.writeUInt32LE(36+a.length*2,4); b.write("WAVEfmt ",8); b.writeUInt32LE(16,16); b.writeUInt16LE(1,20); b.writeUInt16LE(1,22); b.writeUInt32LE(rate,24); b.writeUInt32LE(rate*2,28); b.writeUInt16LE(2,32); b.writeUInt16LE(16,34); b.write("data",36); b.writeUInt32LE(a.length*2,40); for (let i=0;i<a.length;i++) b.writeInt16LE(Math.max(-32768,Math.min(32767,Math.round(a[i]*32767))),44+i*2); return b; }
for (const dtype of ["q8", "fp16"]) {
  const page = await browser.newPage(); const logs = [];
  page.on("console", m => logs.push(m.text().slice(0, 300))); page.on("pageerror", e => logs.push("pageerror " + e.message));
  page.on("request", r => { if (!r.url().startsWith("http://localhost")) logs.push("EXTERNAL REQUEST " + r.url().slice(0, 120)); });
  await page.goto("http://localhost:8123/index.html");
  const res = await page.evaluate((dtype) => new Promise(resolve => {
    const t0 = performance.now(); const w = new Worker("uz-tts-worker.js", { type: "module" });
    w.onerror = e => resolve({ fatal: e.message });
    w.onmessage = ({ data }) => {
      if (data.type === "audio") resolve({ ok: true, ms: Math.round(performance.now() - t0), rate: data.rate, audio: Array.from(data.audio) });
      if (data.type === "error") resolve({ ok: false, error: data.message });
    };
    w.postMessage({ id: 1, dtype, text: "Assalomu alaykum! Sizni ko‘rganimdan xursandman, bugun havo juda yaxshi." });
    setTimeout(() => resolve({ timeout: true }), 180000);
  }), dtype);
  if (res.audio) { fs.writeFileSync(`results/uz-${dtype}.wav`, wav(res.audio, res.rate)); res.seconds = res.audio.length / res.rate; delete res.audio; }
  console.log(dtype, JSON.stringify(res)); console.log(logs.slice(0, 15).join("\n")); await page.close();
}
await browser.close(); server.close();
