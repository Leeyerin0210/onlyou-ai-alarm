from types import SimpleNamespace

from google import genai

from core import local_llm
from core.local_llm import EMBED_DIM, LocalGenaiClient, _chat_body, _to_messages


def test_messages_from_string_and_contents():
    assert _to_messages("안녕") == [{"role": "user", "content": "안녕"}]
    contents = [
        genai.types.Content(role="user", parts=[genai.types.Part(text="질문")]),
        genai.types.Content(role="model", parts=[genai.types.Part(text="답")]),
    ]
    assert _to_messages(contents, "지침") == [
        {"role": "system", "content": "지침"},
        {"role": "user", "content": "질문"},
        {"role": "assistant", "content": "답"},
    ]


def test_chat_body_maps_config():
    config = genai.types.GenerateContentConfig(
        system_instruction="s", max_output_tokens=50, response_mime_type="application/json"
    )
    body = _chat_body("m", "hi", config, stream=True)
    assert body["format"] == "json"
    assert body["options"] == {"num_predict": 50}
    assert body["messages"][0] == {"role": "system", "content": "s"}
    assert "format" not in _chat_body("m", "hi", None, stream=False)


def test_embeddings_are_padded_to_table_dim(monkeypatch):
    def fake_post(url, json, timeout):
        return SimpleNamespace(raise_for_status=lambda: None,
                               json=lambda: {"embeddings": [[0.1, 0.2]] * len(json["input"])})

    monkeypatch.setattr(local_llm.httpx, "post", fake_post)
    res = LocalGenaiClient("http://x", "m", "e").models.embed_content(contents=["a", "b"])
    assert len(res.embeddings) == 2
    assert len(res.embeddings[0].values) == EMBED_DIM
    assert res.embeddings[0].values[:2] == [0.1, 0.2]
