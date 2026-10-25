# Runs each Uzbek voice file with the same ONNX Runtime version the Android app uses (1.19.2).
import json, time, wave, numpy as np, onnxruntime as ort, urllib.request, os
vocab = json.load(open("models/uz/vocab.json"))
text = "ассалому алайкум сизни кўрганимдан хурсандман"
ids = [vocab[c] for c in text if c in vocab]
seq = [0]
for i in ids: seq += [i, 0]
x = np.array([seq], dtype=np.int64)
files = {"fp16": "models/uz/onnx/model_fp16.onnx", "q8": "models/uz/onnx/model_quantized.onnx"}
for name, path in files.items():
    try:
        t = time.time()
        s = ort.InferenceSession(path, providers=["CPUExecutionProvider"])
        feeds = {"input_ids": x}
        if "attention_mask" in [i.name for i in s.get_inputs()]: feeds["attention_mask"] = np.ones_like(x)
        out = s.run(None, feeds)[0].reshape(-1)
        print(name, "OK", "inputs", [i.name for i in s.get_inputs()], "seconds", round(len(out)/16000, 2), "peak", round(float(np.abs(out).max()), 3), "ms", int((time.time()-t)*1000))
        with wave.open(f"tests/results/native-{name}.wav", "wb") as w:
            w.setnchannels(1); w.setsampwidth(2); w.setframerate(16000)
            w.writeframes((np.clip(out, -1, 1) * 32767).astype(np.int16).tobytes())
    except Exception as e:
        print(name, "FAIL", str(e)[:600])
