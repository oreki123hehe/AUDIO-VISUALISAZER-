# Bundled offline separation

UMX-HQ by Fabian-Robert Stöter and Antoine Liutkus / Inria:
https://doi.org/10.5281/zenodo.3370489
https://github.com/sigsep/open-unmix-pytorch

The Zenodo model record identifies the license as MIT. This app uses UMX-HQ, not the noncommercial UMXL model. The model architecture in `tools/prepare_separation.py` is adapted from Open-Unmix. Its MIT notice is included in the APK at `assets/OPEN_UNMIX_LICENSE.txt`.

The four checkpoint downloads are checked against their published SHA-256 prefixes. Conversion uses pinned PyTorch 2.5.1, ONNX 1.17.0 and ONNX Runtime 1.20.1. Only dense matrix weights are dynamically quantized; recurrent layers retain float weights. Each model is tested against the original PyTorch network at three sequence lengths. The generated manifest records complete weight/model SHA-256 hashes and sizes. Android checks the manifest before opening cached models.

ONNX Runtime Android 1.20.0 is distributed under the MIT license; its dependency contains its notices. https://github.com/microsoft/onnxruntime

The phone processes music locally without Internet permission. Four model assets total approximately 93 MB before APK compression. Model estimates can contain leakage and artifacts; the `other` output is all remaining instruments, not an isolated melody. This application uses contextual chunks and ratio masks with mixture phase; it does not claim parity with the unchunked iterative Wiener reference separator.
