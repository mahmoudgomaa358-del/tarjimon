import { pipeline, AutoTokenizer, env } from "@huggingface/transformers";
import fs from "fs";
const M = "davron112/mms-tts-uzb-cyrl-onnx";
try { const tok = await AutoTokenizer.from_pretrained(M); console.log("tokenizer OK", JSON.stringify(tok("салом дунё").input_ids.tolist?.() ?? tok("салом дунё").input_ids)); }
catch (e) { console.log("TOKENIZER FAIL", e.stack); }
for (const dtype of ["fp16", "fp32"]) {
  try {
    const t0 = Date.now();
    const tts = await pipeline("text-to-speech", M, { dtype });
    const out = await tts("салом қалайсиз мен ўзбекман");
    const a = out.audio; let peak = 0; for (const x of a) peak = Math.max(peak, Math.abs(x));
    console.log(`${dtype} OK samples=${a.length} rate=${out.sampling_rate} peak=${peak.toFixed(3)} ms=${Date.now()-t0}`);
  } catch (e) { console.log(`${dtype} FAIL`, e.stack); }
}
