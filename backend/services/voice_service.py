import os
import json
import base64
from typing import Optional, Tuple

import httpx

from core.config import settings


class RemoteVoiceEngine:
    """Modal에 배포된 Qwen-TTS 서버리스(tts-server/modal_app.py)를 호출하는 클라이언트.

    무거운 모델은 전부 원격 GPU에서 돌고, 이 서버는 참조 음성 보관과 프록시만 담당한다.
    참조 음성은 Firebase Storage(FIREBASE_STORAGE_BUCKET 설정 시)에 저장하고
    로컬 디스크는 캐시로 사용한다. 버킷 미설정 시 로컬 디스크만 사용(개발용).
    """

    def __init__(self):
        base_dir = os.path.dirname(os.path.dirname(__file__))  # backend/
        self.reference_dir = os.path.join(base_dir, "reference_voices")
        os.makedirs(self.reference_dir, exist_ok=True)

    # ---------- 원격 TTS 호출 ----------

    async def _post(self, path: str, payload: dict) -> bytes:
        if not settings.TTS_SERVER_URL:
            raise RuntimeError("TTS_SERVER_URL is not configured")
        url = settings.TTS_SERVER_URL.rstrip("/") + path
        headers = {"X-API-Key": settings.TTS_API_KEY}
        # 콜드 스타트(모델 로딩) 동안 응답이 수 분 걸릴 수 있다
        timeout = httpx.Timeout(600.0, connect=30.0)
        async with httpx.AsyncClient(timeout=timeout) as client:
            res = await client.post(url, json=payload, headers=headers)
            res.raise_for_status()
            return res.content

    async def synthesize_design(self, text: str, instruct: str) -> bytes:
        return await self._post("/synthesize", {"text": text, "instruct": instruct})

    async def synthesize_clone(self, text: str, persona_id: str) -> bytes:
        audio, ref_text = self.load_reference(persona_id)
        return await self._post(
            "/clone",
            {
                "text": text,
                "ref_text": ref_text,
                "ref_audio_b64": base64.b64encode(audio).decode(),
            },
        )

    # ---------- 참조 음성 저장소 ----------

    def _bucket(self):
        if not settings.FIREBASE_STORAGE_BUCKET:
            return None
        import firebase_admin
        if not firebase_admin._apps:
            return None
        from firebase_admin import storage
        return storage.bucket(settings.FIREBASE_STORAGE_BUCKET)

    def _local_paths(self, persona_id: str) -> Tuple[str, str]:
        return (
            os.path.join(self.reference_dir, f"{persona_id}.wav"),
            os.path.join(self.reference_dir, f"{persona_id}.json"),
        )

    def save_master_wav(self, persona_id: str, audio_data: bytes, ref_text: str):
        audio_path, meta_path = self._local_paths(persona_id)
        with open(audio_path, "wb") as f:
            f.write(audio_data)
        with open(meta_path, "w", encoding="utf-8") as f:
            json.dump({"ref_text": ref_text}, f, ensure_ascii=False)

        bucket = self._bucket()
        if bucket:
            bucket.blob(f"reference_voices/{persona_id}.wav").upload_from_string(
                audio_data, content_type="audio/wav"
            )
            bucket.blob(f"reference_voices/{persona_id}.json").upload_from_string(
                json.dumps({"ref_text": ref_text}, ensure_ascii=False),
                content_type="application/json",
            )

    def load_reference(self, persona_id: str) -> Tuple[bytes, str]:
        audio_path, meta_path = self._local_paths(persona_id)

        if not (os.path.exists(audio_path) and os.path.exists(meta_path)):
            bucket = self._bucket()
            if bucket:
                audio_blob = bucket.blob(f"reference_voices/{persona_id}.wav")
                meta_blob = bucket.blob(f"reference_voices/{persona_id}.json")
                if audio_blob.exists() and meta_blob.exists():
                    with open(audio_path, "wb") as f:
                        f.write(audio_blob.download_as_bytes())
                    with open(meta_path, "wb") as f:
                        f.write(meta_blob.download_as_bytes())

        if not (os.path.exists(audio_path) and os.path.exists(meta_path)):
            raise FileNotFoundError(f"Master WAV not found for {persona_id}")

        with open(audio_path, "rb") as f:
            audio = f.read()
        with open(meta_path, "r", encoding="utf-8") as f:
            ref_text = json.load(f)["ref_text"]
        return audio, ref_text

    def get_reference_audio(self, persona_id: str) -> Optional[bytes]:
        try:
            audio, _ = self.load_reference(persona_id)
            return audio
        except FileNotFoundError:
            return None

    def delete_reference(self, persona_id: str):
        audio_path, meta_path = self._local_paths(persona_id)
        for p in (audio_path, meta_path):
            if os.path.exists(p):
                os.remove(p)
        bucket = self._bucket()
        if bucket:
            for name in (f"reference_voices/{persona_id}.wav", f"reference_voices/{persona_id}.json"):
                blob = bucket.blob(name)
                if blob.exists():
                    blob.delete()


voice_engine = RemoteVoiceEngine()
