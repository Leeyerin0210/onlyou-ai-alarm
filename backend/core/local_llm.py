"""개발용 로컬 LLM(Ollama) 클라이언트 — genai.Client 중 이 서버가 쓰는 부분만 흉내 낸다.

LOCAL_LLM_URL이 설정되면 core/ai.py가 Gemini 대신 이 클라이언트를 준다.
호출부(chat·alarm·memory·reflection·voice)는 손대지 않는다. 운영에서는 쓰지 않는다.

임베딩: user_memories.embedding이 Gemini 차원(3072)으로 만들어져 있어 0으로 채워 맞춘다.
로컬 임베딩끼리의 코사인 유사도는 그대로 유지되지만, 기존 Gemini 임베딩과는 비교가 의미 없다.
"""
import json
from types import SimpleNamespace

import httpx

EMBED_DIM = 3072
_TIMEOUT = httpx.Timeout(300.0, connect=10.0)


def _to_messages(contents, system_instruction=None) -> list[dict]:
    messages = []
    if system_instruction:
        messages.append({"role": "system", "content": str(system_instruction)})
    if isinstance(contents, str):
        return messages + [{"role": "user", "content": contents}]
    for c in contents:
        if isinstance(c, str):
            messages.append({"role": "user", "content": c})
            continue
        text = "".join(p.text or "" for p in (c.parts or []))
        messages.append({"role": "assistant" if c.role == "model" else "user", "content": text})
    return messages


def _chat_body(model: str, contents, config, stream: bool) -> dict:
    body = {
        "model": model,
        "messages": _to_messages(contents, getattr(config, "system_instruction", None)),
        "stream": stream,
    }
    if getattr(config, "response_mime_type", None) == "application/json":
        body["format"] = "json"
    if getattr(config, "max_output_tokens", None):
        body["options"] = {"num_predict": config.max_output_tokens}
    return body


class _AsyncModels:
    def __init__(self, url: str, model: str):
        self._url, self._model = url, model

    async def generate_content(self, model=None, contents=None, config=None):
        async with httpx.AsyncClient(timeout=_TIMEOUT) as client:
            res = await client.post(f"{self._url}/api/chat",
                                    json=_chat_body(self._model, contents, config, stream=False))
            res.raise_for_status()
            return SimpleNamespace(text=res.json()["message"]["content"])

    async def generate_content_stream(self, model=None, contents=None, config=None):
        body = _chat_body(self._model, contents, config, stream=True)

        async def chunks():
            async with httpx.AsyncClient(timeout=_TIMEOUT) as client:
                async with client.stream("POST", f"{self._url}/api/chat", json=body) as res:
                    res.raise_for_status()
                    async for line in res.aiter_lines():
                        if not line:
                            continue
                        data = json.loads(line)
                        text = data.get("message", {}).get("content", "")
                        if text:
                            yield SimpleNamespace(text=text)

        return chunks()


class _Models:
    def __init__(self, url: str, embed_model: str):
        self._url, self._embed_model = url, embed_model

    def embed_content(self, model=None, contents=None):
        texts = [contents] if isinstance(contents, str) else list(contents)
        res = httpx.post(f"{self._url}/api/embed",
                         json={"model": self._embed_model, "input": texts}, timeout=_TIMEOUT)
        res.raise_for_status()
        vectors = res.json()["embeddings"]
        return SimpleNamespace(embeddings=[
            SimpleNamespace(values=v + [0.0] * (EMBED_DIM - len(v))) for v in vectors
        ])


class LocalGenaiClient:
    def __init__(self, url: str, model: str, embed_model: str):
        url = url.rstrip("/")
        self.models = _Models(url, embed_model)
        self.aio = SimpleNamespace(models=_AsyncModels(url, model))
