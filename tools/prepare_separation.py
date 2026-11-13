#!/usr/bin/env python3
"""Reproducible offline UMX-HQ conversion; no model download occurs on the phone.
Weights: Stöter/Liutkus, https://doi.org/10.5281/zenodo.3370489 (MIT).
Architecture adapted from sigsep/open-unmix-pytorch (MIT, see THIRD_PARTY.md).
"""
import hashlib, json, pathlib, urllib.request
import numpy as np
import torch
from torch import nn
import onnxruntime as ort
from onnxruntime.quantization import quantize_dynamic, QuantType

ROOT=pathlib.Path(__file__).resolve().parents[1]
DEST=ROOT/'app/src/main/assets/separation'
CACHE=ROOT/'build/umxhq'
SOURCES={'vocals':'b62c91ce','drums':'9619578f','bass':'8d85a5bd','other':'b52fbbf7'}
class OpenUnmix(nn.Module):
    def __init__(self):
        super().__init__(); self.nb_bins=1487
        self.fc1=nn.Linear(2974,512,bias=False);self.bn1=nn.BatchNorm1d(512)
        self.lstm=nn.LSTM(512,256,3,bidirectional=True,dropout=.4)
        self.fc2=nn.Linear(1024,512,bias=False);self.bn2=nn.BatchNorm1d(512)
        self.fc3=nn.Linear(512,4098,bias=False);self.bn3=nn.BatchNorm1d(4098)
        self.input_mean=nn.Parameter(torch.zeros(1487));self.input_scale=nn.Parameter(torch.ones(1487))
        self.output_scale=nn.Parameter(torch.ones(2049));self.output_mean=nn.Parameter(torch.ones(2049))
    def forward(self, magnitude):
        mix=magnitude.permute(3,0,1,2);frames=mix.shape[0]
        x=(mix[...,:1487]+self.input_mean)*self.input_scale
        x=torch.tanh(self.bn1(self.fc1(x.reshape(-1,2974))).reshape(frames,1,512))
        x=torch.cat([x,self.lstm(x)[0]],-1)
        x=torch.relu(self.bn2(self.fc2(x.reshape(-1,1024))))
        x=self.bn3(self.fc3(x)).reshape(frames,1,2,2049)
        return (torch.relu(x*self.output_scale+self.output_mean)*mix).permute(1,2,3,0)

def main():
    DEST.mkdir(parents=True,exist_ok=True);CACHE.mkdir(parents=True,exist_ok=True)
    torch.set_num_threads(2); torch.manual_seed(14)
    manifest={'model':'UMX-HQ','license':'MIT','sampleRate':44100,'fft':4096,'hop':1024,'stems':[]}
    for name,sha in SOURCES.items():
        path=CACHE/f'{name}-{sha}.pth'
        if not path.exists():
            with urllib.request.urlopen(f'https://zenodo.org/records/3370489/files/{path.name}',timeout=180) as r:path.write_bytes(r.read())
        digest=hashlib.sha256(path.read_bytes()).hexdigest()
        if not digest.startswith(sha):raise RuntimeError(f'Checksum mismatch: {path.name}')
        model=OpenUnmix().eval();weights=torch.load(path,map_location='cpu',weights_only=True)
        # Legacy checkpoints contain transform buffers that are not part of the magnitude network.
        missing,extra=model.load_state_dict(weights,strict=False)
        if missing:raise RuntimeError(f'Missing model weights: {missing}')
        if any(k not in ('sample_rate','stft.window','transform.0.window') for k in extra):raise RuntimeError(f'Unknown weights: {extra}')
        full=CACHE/f'{name}.onnx';target=DEST/f'{name}.onnx'
        example=torch.rand(1,2,2049,96)*8
        torch.onnx.export(model,example,str(full),opset_version=17,input_names=['magnitude'],output_names=['estimate'],dynamic_axes={'magnitude':{3:'frames'},'estimate':{3:'frames'}},dynamo=False)
        quantize_dynamic(str(full),str(target),weight_type=QuantType.QInt8,op_types_to_quantize=['MatMul'],per_channel=True)
        options=ort.SessionOptions();options.intra_op_num_threads=2;options.inter_op_num_threads=1
        session=ort.InferenceSession(str(target),sess_options=options,providers=['CPUExecutionProvider'])
        for frames in [32,96,192]:
            x=np.random.default_rng(12).uniform(0,8,(1,2,2049,frames)).astype('float32')
            with torch.no_grad():expected=model(torch.from_numpy(x)).numpy()
            actual=session.run(None,{'magnitude':x})[0]
            error=np.mean(np.abs(actual-expected))/max(.001,np.mean(np.abs(expected)))
            if not np.isfinite(actual).all() or error>.12:raise RuntimeError(f'Inference mismatch: {name} {frames} {error}')
            print(name,frames,'relative MAE',round(float(error),5),flush=True)
        manifest['stems'].append({'name':name,'weightSha256':digest,'file':target.name,'sha256':hashlib.sha256(target.read_bytes()).hexdigest(),'bytes':target.stat().st_size})
    (DEST/'manifest.json').write_text(json.dumps(manifest,indent=2)+'\n')
    print('Four offline models verified',sum(s['bytes'] for s in manifest['stems']),flush=True)
if __name__=='__main__':main()
