# Downloads the Uzbek MMS voice, makes smaller phone-friendly copies, stores them in models/uz/
import os, shutil, onnx, onnxruntime as ort
from huggingface_hub import snapshot_download
from onnxconverter_common import float16
from onnxruntime.quantization import quantize_dynamic, QuantType
src = snapshot_download("davron112/mms-tts-uzb-cyrl-onnx", allow_patterns=["*.json", "onnx/model.onnx"])
out = "models/uz"; os.makedirs(out + "/onnx", exist_ok=True)
for f in os.listdir(src):
    if f.endswith(".json"): shutil.copy(os.path.join(src, f), out)
# fold constant weight computations so the weights become plain tensors
opt = "/tmp/uz_opt.onnx"
so = ort.SessionOptions(); so.graph_optimization_level = ort.GraphOptimizationLevel.ORT_ENABLE_BASIC; so.optimized_model_filepath = opt
ort.InferenceSession(src + "/onnx/model.onnx", so, providers=["CPUExecutionProvider"])
try:
    quantize_dynamic(opt, out + "/onnx/model_quantized.onnx", weight_type=QuantType.QUInt8)
except Exception as e:
    print("q8 full failed:", e); quantize_dynamic(opt, out + "/onnx/model_quantized.onnx", weight_type=QuantType.QUInt8, op_types_to_quantize=["MatMul", "Gemm"])
try:
    m = onnx.load(opt)
    fp16 = float16.convert_float_to_float16(m, keep_io_types=True, op_block_list=["RandomNormalLike","RandomNormal","RandomUniformLike","Range","CumSum","Pad","Softplus"])
    onnx.save(fp16, out + "/onnx/model_fp16.onnx")
except Exception as e:
    print("fp16 failed:", e)
for f in os.listdir(out + "/onnx"): print(f, os.path.getsize(out + "/onnx/" + f) // 1e6, "MB")
