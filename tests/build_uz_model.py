# Downloads the Uzbek MMS voice, makes smaller phone-friendly copies, stores them in models/uz/
import os, shutil, onnx
from huggingface_hub import snapshot_download
from onnxconverter_common import float16
from onnxruntime.quantization import quantize_dynamic, QuantType
src = snapshot_download("davron112/mms-tts-uzb-cyrl-onnx", allow_patterns=["*.json", "onnx/model.onnx"])
out = "models/uz"; os.makedirs(out + "/onnx", exist_ok=True)
for f in os.listdir(src):
    if f.endswith(".json"): shutil.copy(os.path.join(src, f), out)
m = onnx.load(src + "/onnx/model.onnx")
fp16 = float16.convert_float_to_float16(m, keep_io_types=True, op_block_list=["RandomNormalLike","RandomNormal","RandomUniformLike","Range","CumSum","Pad","Softplus"])
onnx.save(fp16, out + "/onnx/model_fp16.onnx")
quantize_dynamic(src + "/onnx/model.onnx", out + "/onnx/model_quantized.onnx", weight_type=QuantType.QUInt8)
for f in os.listdir(out + "/onnx"): print(f, os.path.getsize(out + "/onnx/" + f) // 1e6, "MB")
