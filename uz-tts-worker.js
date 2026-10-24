// Uzbek text-to-speech that runs on the phone itself (Meta MMS voice, Cyrillic model).
// The model downloads once (cached by the browser) and then works offline.
// Everything is loaded from the app's own files, so it keeps working offline.
import { AutoTokenizer, VitsModel, env } from "./vendor/transformers.min.js";

env.allowRemoteModels = false;
env.allowLocalModels = true;
env.localModelPath = new URL("./models/", self.location).href;
env.backends.onnx.wasm.wasmPaths = new URL("./vendor/", self.location).href;
env.backends.onnx.wasm.numThreads = 1;
const MODEL = "uz";
let DTYPE = "fp16";   // 57 MB; "q8" (37 MB) also works
let tts = null, dtypeInUse = null;

function progress(p) {
  if (p.status === "progress" && /\.onnx$/.test(p.file || "")) postMessage({ type: "progress", pct: Math.round(p.progress || 0) });
}
async function load(dtype) {
  const tokenizer = await AutoTokenizer.from_pretrained(MODEL);
  const model = await VitsModel.from_pretrained(MODEL, { dtype, progress_callback: progress });
  tts = async (text) => {
    const { waveform } = await model(tokenizer(text));
    return { audio: waveform.data, sampling_rate: model.config.sampling_rate || 16000 };
  };
  dtypeInUse = dtype;
  postMessage({ type: "ready" });
}
async function ensure() {
  if (!tts) await load(DTYPE);
}

// ---- Uzbek Latin -> Cyrillic (the voice model reads Cyrillic) ----
const ONES = ["nol","bir","ikki","uch","to'rt","besh","olti","yetti","sakkiz","to'qqiz"];
const TENS = ["","o'n","yigirma","o'ttiz","qirq","ellik","oltmish","yetmish","sakson","to'qson"];
function num(n) {
  if (n < 10) return ONES[n];
  if (n < 100) return TENS[Math.floor(n / 10)] + (n % 10 ? " " + ONES[n % 10] : "");
  if (n < 1000) { const h = Math.floor(n / 100); return (h > 1 ? ONES[h] + " " : "") + "yuz" + (n % 100 ? " " + num(n % 100) : ""); }
  if (n < 1e6) { const t = Math.floor(n / 1000); return num(t) + " ming" + (n % 1000 ? " " + num(n % 1000) : ""); }
  if (n < 1e9) { const m = Math.floor(n / 1e6); return num(m) + " million" + (n % 1e6 ? " " + num(n % 1e6) : ""); }
  return String(n).split("").map(d => ONES[+d]).join(" ");
}
const SINGLE = { a:"а",b:"б",c:"с",d:"д",e:"е",f:"ф",g:"г",h:"ҳ",i:"и",j:"ж",k:"к",l:"л",m:"м",n:"н",o:"о",p:"п",q:"қ",r:"р",s:"с",t:"т",u:"у",v:"в",w:"в",x:"х",y:"й",z:"з" };
const LETTER = "a-zа-яёўғқҳ";
function toCyrillic(text) {
  let s = text.toLowerCase().normalize("NFC");
  s = s.replace(/\d+/g, d => " " + num(parseInt(d, 10)) + " ");
  s = s.replace(/[‘’ʻʼ`´']/g, "'");
  s = s.replace(/o'/g, "ў").replace(/g'/g, "ғ").replace(/sh/g, "ш").replace(/ch/g, "ч");
  s = s.replace(new RegExp(`(^|[^${LETTER}])e`, "g"), "$1э");
  s = s.replace(/yo/g, "ё").replace(/yu/g, "ю").replace(/ya/g, "я").replace(/ye/g, "е");
  s = s.replace(/'/g, "ъ");
  s = s.replace(/[a-z]/g, ch => SINGLE[ch] || "");
  return s;
}
function clean(cyr) {
  return cyr.replace(/[^абвгдежзийклмнопрстуфхчшъьэюяёўғқҳ ]/g, " ").replace(/\s+/g, " ").trim();
}

async function synth(text) {
  const sentences = toCyrillic(text).split(/[.!?;:\n]+/).map(clean).filter(Boolean);
  const parts = []; let rate = 16000;
  for (const s of sentences) {
    const out = await tts(s);
    rate = out.sampling_rate; parts.push(out.audio);
  }
  const gap = Math.round(rate * 0.25);
  const total = parts.reduce((a, p) => a + p.length, 0) + gap * Math.max(0, parts.length - 1);
  const audio = new Float32Array(total); let o = 0;
  parts.forEach((p, i) => { audio.set(p, o); o += p.length + (i < parts.length - 1 ? gap : 0); });
  return { audio, rate };
}

onmessage = async ({ data }) => {
  const { id, text, preload, dtype } = data;
  if (dtype && !tts) DTYPE = dtype;
  try {
    await ensure();
    if (preload) return;
    const res = await synth(text);
    postMessage({ type: "audio", id, audio: res.audio, rate: res.rate }, [res.audio.buffer]);
  } catch (err) {
    postMessage({ type: "error", id, message: String(err && err.message || err) });
  }
};
