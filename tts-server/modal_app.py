"""
Qwen3-TTS 서버리스 GPU 서버 (Modal)

배포:
    pip install modal
    modal setup                      # 최초 1회 (브라우저 로그인)
    modal secret create onlyou-tts-secret TTS_API_KEY=<임의의 긴 문자열>
    modal deploy tts-server/modal_app.py

배포하면 출력되는 URL 하나(...modal.run)가 백엔드의 TTS_SERVER_URL이 된다.
백엔드는 모든 요청에 X-API-Key 헤더를 붙여야 한다.

엔드포인트:
    GET  /health                     인증 불필요, 웜업 겸용
    POST /synthesize {text, instruct}                    -> audio/wav
    POST /clone {text, ref_text, ref_audio_b64}          -> audio/wav
"""

import modal

app = modal.App("onlyou-qwen-tts")

HF_CACHE_DIR = "/cache"
hf_cache = modal.Volume.from_name("qwen-tts-hf-cache", create_if_missing=True)

DESIGN_MODEL_ID = "Qwen/Qwen3-TTS-12Hz-1.7B-VoiceDesign"
CLONE_MODEL_ID = "Qwen/Qwen3-TTS-12Hz-0.6B-Base"

image = (
    modal.Image.debian_slim(python_version="3.11")
    .apt_install("git", "ffmpeg", "libsndfile1")
    .pip_install("torch", "torchaudio", "soundfile", "huggingface_hub", "fastapi[standard]")
    .pip_install("git+https://github.com/QwenLM/Qwen3-TTS.git")
    .env({"HF_HUB_CACHE": HF_CACHE_DIR})
)

with image.imports():
    import base64
    import hashlib
    import hmac
    import io
    import os
    import tempfile

    import soundfile as sf
    import torch
    from qwen_tts import Qwen3TTSModel

MINUTES = 60


@app.cls(
    image=image,
    gpu="L4",
    volumes={HF_CACHE_DIR: hf_cache},
    secrets=[modal.Secret.from_name("onlyou-tts-secret")],
    scaledown_window=5 * MINUTES,
    timeout=10 * MINUTES,
)
class QwenTTS:
    @modal.enter()
    def load_models(self):
        kwargs = dict(device_map="cuda:0", dtype=torch.bfloat16, attn_implementation="sdpa")
        self.design_model = Qwen3TTSModel.from_pretrained(DESIGN_MODEL_ID, **kwargs)
        self.clone_model = Qwen3TTSModel.from_pretrained(CLONE_MODEL_ID, **kwargs)
        hf_cache.commit()  # 첫 다운로드를 볼륨에 저장해 다음 콜드 스타트를 단축
        self.prompt_cache = {}

    def _wav_bytes(self, wavs, sr) -> bytes:
        buf = io.BytesIO()
        sf.write(buf, wavs[0], sr, format="WAV")
        return buf.getvalue()

    @modal.method()
    def synthesize_design(self, text: str, instruct: str) -> bytes:
        wavs, sr = self.design_model.generate_voice_design(
            text=text, language="Korean", instruct=instruct
        )
        return self._wav_bytes(wavs, sr)

    @modal.method()
    def synthesize_clone(self, text: str, ref_text: str, ref_audio_b64: str) -> bytes:
        cache_key = hashlib.sha256((ref_audio_b64 + ref_text).encode()).hexdigest()
        prompt_items = self.prompt_cache.get(cache_key)
        if prompt_items is None:
            # 참조 음성 프롬프트 캐시가 무한히 자라 컨테이너 메모리를 잠식하지 않도록 상한
            if len(self.prompt_cache) >= 32:
                self.prompt_cache.clear()
            audio_bytes = base64.b64decode(ref_audio_b64)
            with tempfile.NamedTemporaryFile(suffix=".wav", delete=False) as f:
                f.write(audio_bytes)
                ref_path = f.name
            try:
                prompt_items = self.clone_model.create_voice_clone_prompt(
                    ref_audio=ref_path, ref_text=ref_text, x_vector_only_mode=False
                )
            finally:
                os.unlink(ref_path)
            self.prompt_cache[cache_key] = prompt_items
        wavs, sr = self.clone_model.generate_voice_clone(
            text=text, language="Korean", voice_clone_prompt=prompt_items
        )
        return self._wav_bytes(wavs, sr)

    @modal.asgi_app()
    def web(self):
        from fastapi import Depends, FastAPI, HTTPException, Header
        from fastapi.responses import Response
        from pydantic import BaseModel

        def check_api_key(x_api_key: str = Header(default="")):
            # 상수 시간 비교 — 문자열 == 는 타이밍 차이로 키를 유추할 여지를 준다
            if not hmac.compare_digest(x_api_key, os.environ["TTS_API_KEY"]):
                raise HTTPException(status_code=401, detail="Invalid API key")

        class SynthesizeRequest(BaseModel):
            text: str
            instruct: str = ""

        class CloneRequest(BaseModel):
            text: str
            ref_text: str
            ref_audio_b64: str

        api = FastAPI(title="Onlyou Qwen-TTS")

        @api.get("/health")
        def health():
            return {"status": "ok"}

        @api.post("/synthesize", dependencies=[Depends(check_api_key)])
        def synthesize(req: SynthesizeRequest):
            audio = self.synthesize_design.local(req.text, req.instruct)
            return Response(content=audio, media_type="audio/wav")

        @api.post("/clone", dependencies=[Depends(check_api_key)])
        def clone(req: CloneRequest):
            audio = self.synthesize_clone.local(req.text, req.ref_text, req.ref_audio_b64)
            return Response(content=audio, media_type="audio/wav")

        return api
