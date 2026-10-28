"""Mac 로컬 Qwen3-TTS 서버 — modal_app.py와 같은 API(/health, /synthesize, /clone).

Modal 없이 개발할 때 백엔드의 TTS_SERVER_URL을 여기로 향하게 한다.

    TTS_API_KEY=<backend/.env와 같은 값> python tts-server/local_app.py   # http://localhost:8001

필요: torch·qwen-tts·soundfile·fastapi·uvicorn, ffmpeg (없으면 WAV로 응답).
모델은 처음 쓰일 때 로드한다 — 알람(클론)만 쓰면 보이스 디자인 모델은 받지 않는다.
DESIGN_MODEL / CLONE_MODEL 환경변수로 로컬 경로를 줄 수 있다.
"""
import base64
import hashlib
import hmac
import io
import os
import subprocess
import tempfile
import threading

import numpy as np
import soundfile as sf
import torch
import uvicorn
from fastapi import Depends, FastAPI, Header, HTTPException
from fastapi.responses import Response
from pydantic import BaseModel
from qwen_tts import Qwen3TTSModel

DESIGN_MODEL = os.getenv("DESIGN_MODEL", "Qwen/Qwen3-TTS-12Hz-1.7B-VoiceDesign")
CLONE_MODEL = os.getenv("CLONE_MODEL", "Qwen/Qwen3-TTS-12Hz-0.6B-Base")
DEVICE = "mps" if torch.backends.mps.is_available() else "cpu"
API_KEY = os.environ["TTS_API_KEY"]

_models: dict[str, Qwen3TTSModel] = {}
_prompt_cache: dict[str, object] = {}
# MPS는 동시 생성에 안전하지 않다 — 요청을 한 줄로 세운다
_lock = threading.Lock()


def _model(path: str) -> Qwen3TTSModel:
    if path not in _models:
        _models[path] = Qwen3TTSModel.from_pretrained(path, device_map=DEVICE, dtype=torch.float32)
    return _models[path]


def _cut_off(wav, sr) -> bool:
    """끝 0.15초가 본문만큼 시끄러우면 말하다 잘린 것이다 (정상 출력은 끝이 무음).
    샘플링(temperature)이 가끔 문장 중간에 끝 토큰을 뽑아 생기는 현상."""
    n = int(0.15 * sr)
    if len(wav) <= 2 * n:
        return False
    rms = lambda x: float(np.sqrt(np.mean(np.square(x)))) + 1e-9
    return rms(wav[-n:]) / rms(wav[:-n]) > 0.3


def _generate(fn, retries: int = 2):
    """잘린 출력이면 다시 생성한다. 끝까지 잘리면 마지막 결과를 쓴다."""
    for _ in range(retries + 1):
        wavs, sr = fn()
        if not _cut_off(wavs[0], sr):
            break
        print("TTS output was cut off; regenerating")
    return wavs, sr


def _encode_audio(wavs, sr) -> tuple[bytes, str]:
    """modal_app과 같은 Ogg/Opus 압축. ffmpeg 실패 시 WAV."""
    buf = io.BytesIO()
    sf.write(buf, wavs[0], sr, format="WAV")
    wav = buf.getvalue()
    try:
        proc = subprocess.run(
            ["ffmpeg", "-loglevel", "error", "-i", "pipe:0",
             "-c:a", "libopus", "-b:a", "32k", "-f", "ogg", "pipe:1"],
            input=wav, capture_output=True, check=True, timeout=60,
        )
        if proc.stdout:
            return proc.stdout, "audio/ogg"
    except Exception:
        pass
    return wav, "audio/wav"


def check_api_key(x_api_key: str = Header(default="")):
    if not hmac.compare_digest(x_api_key, API_KEY):
        raise HTTPException(status_code=401, detail="Invalid API key")


class SynthesizeRequest(BaseModel):
    text: str
    instruct: str = ""


class CloneRequest(BaseModel):
    text: str
    ref_text: str
    ref_audio_b64: str


api = FastAPI(title="Onlyou Qwen-TTS (local)")


@api.get("/health")
def health():
    return {"status": "ok", "device": DEVICE}


@api.post("/synthesize", dependencies=[Depends(check_api_key)])
def synthesize(req: SynthesizeRequest):
    with _lock:
        wavs, sr = _generate(lambda: _model(DESIGN_MODEL).generate_voice_design(
            text=req.text, language="Korean", instruct=req.instruct
        ))
    audio, media_type = _encode_audio(wavs, sr)
    return Response(content=audio, media_type=media_type)


@api.post("/clone", dependencies=[Depends(check_api_key)])
def clone(req: CloneRequest):
    with _lock:
        model = _model(CLONE_MODEL)
        key = hashlib.sha256((req.ref_audio_b64 + req.ref_text).encode()).hexdigest()
        prompt = _prompt_cache.get(key)
        if prompt is None:
            if len(_prompt_cache) >= 32:
                _prompt_cache.clear()
            with tempfile.NamedTemporaryFile(suffix=".wav", delete=False) as f:
                f.write(base64.b64decode(req.ref_audio_b64))
                ref_path = f.name
            try:
                prompt = model.create_voice_clone_prompt(
                    ref_audio=ref_path, ref_text=req.ref_text, x_vector_only_mode=False
                )
            finally:
                os.unlink(ref_path)
            _prompt_cache[key] = prompt
        wavs, sr = _generate(lambda: model.generate_voice_clone(
            text=req.text, language="Korean", voice_clone_prompt=prompt
        ))
    audio, media_type = _encode_audio(wavs, sr)
    return Response(content=audio, media_type=media_type)


if __name__ == "__main__":
    uvicorn.run(api, host="0.0.0.0", port=int(os.getenv("PORT", "8001")))
