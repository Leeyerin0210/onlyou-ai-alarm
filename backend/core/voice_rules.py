"""비서 목소리 규칙 — 서버(후보 생성·검증)와 기준 음성 생성 스크립트가 함께 쓴다."""

# 기준 음성 길이(초). 클로닝은 10초 안팎일 때 가장 안정적이다.
MIN_SEC, MAX_SEC = 8.0, 13.0
TARGET_SEC = 10.0
ATTEMPTS = 4  # 공식 비서 기준 음성 스크립트(gen_reference_voices.py)만 다시 뽑는다

# 유저가 쓰는 목소리 설명·샘플 문장 길이 (앱과 같은 값).
# 유저 목소리는 한 번만 만든다 — 빠르고 활발한 목소리는 초당 6자 가까이 읽어서, 50자 이상이어야 기준 음성이 8초를 넘는다.
VOICE_DESCRIPTION_MAX_LEN = 300
VOICE_SAMPLE_MIN_LEN = 50
VOICE_SAMPLE_MAX_LEN = 80
