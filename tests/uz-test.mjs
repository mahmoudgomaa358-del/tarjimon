import { pipeline, AutoTokenizer } from "@huggingface/transformers";
import fs from "fs";
const [M, dtype, tag] = process.argv.slice(2);
function wav(a, rate) {
  const b = Buffer.alloc(44 + a.length * 2);
  b.write("RIFF",0); b.writeUInt32LE(36 + a.length*2,4); b.write("WAVEfmt ",8); b.writeUInt32LE(16,16); b.writeUInt16LE(1,20); b.writeUInt16LE(1,22);
  b.writeUInt32LE(rate,24); b.writeUInt32LE(rate*2,28); b.writeUInt16LE(2,32); b.writeUInt16LE(16,34); b.write("data",36); b.writeUInt32LE(a.length*2,40);
  for (let i=0;i<a.length;i++) b.writeInt16LE(Math.max(-32768,Math.min(32767,Math.round(a[i]*32767))),44+i*2);
  return b;
}
try {
  const tok = await AutoTokenizer.from_pretrained(M);
  console.log(tag, "tokens", Array.from(tok("салом дунё").input_ids.data, Number).join(","));
  const t0 = Date.now();
  const tts = await pipeline("text-to-speech", M, { dtype });
  const out = await tts("ассалому алайкум сизни кўрганимдан хурсандман бугун ҳаво жуда яхши");
  const a = out.audio; let peak = 0; for (const x of a) peak = Math.max(peak, Math.abs(x));
  console.log(`${tag} ${dtype} OK seconds=${(a.length/out.sampling_rate).toFixed(2)} peak=${peak.toFixed(3)} ms=${Date.now()-t0}`);
  fs.writeFileSync(`results/${tag}.wav`, wav(a, out.sampling_rate));
} catch (e) { console.log(`${tag} ${dtype} FAIL`, String(e.message).slice(0,400)); }
