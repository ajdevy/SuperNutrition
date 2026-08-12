"""Create the small G-3 Food101 artifact from the pinned upstream ONNX model."""

import sys

from onnxruntime.quantization import QuantType, quantize_dynamic


def main() -> None:
    if len(sys.argv) != 3:
        raise SystemExit("usage: quantize-model.py SOURCE.onnx OUTPUT.onnx")
    quantize_dynamic(
        sys.argv[1],
        sys.argv[2],
        op_types_to_quantize=["MatMul"],
        per_channel=False,
        weight_type=QuantType.QInt8,
    )


if __name__ == "__main__":
    main()
