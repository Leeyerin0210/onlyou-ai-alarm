"""비서 기준 음성(클로닝 레퍼런스) 생성 — Qwen3-TTS VoiceDesign으로 만든다 (torch·qwen-tts 필요).

    python gen_reference_voices.py                          # 공식 비서 3명, 없는 것만
    python gen_reference_voices.py --force                  # 있어도 다시 만든다
    python gen_reference_voices.py --dev                    # 로컬 개발용 dev_* 20명 (seed_dev_personas)
    python gen_reference_voices.py --only official_casual_warm --force
    python gen_reference_voices.py --model <VoiceDesign 모델 폴더>   # 기본은 Hugging Face에서 받는다

클로닝(1.7B-Base)은 기준 음성이 10초 안팎일 때 음색·억양을 가장 안정적으로 따라온다 — 5초 남짓이면
문장마다 목소리가 흔들린다. 그래서 기준 문장은 두세 문장(마흔~쉰 음절)으로 쓰고,
생성 결과가 MIN_SEC~MAX_SEC를 벗어나면 다시 뽑는다. 기준 문장에는 숫자·영어를 쓰지 않는다(순한글).

결과는 reference_voices/{id}.wav + {id}.json({"ref_text"}) — 공식 비서 것은 커밋하고, dev_*는 gitignore 대상이다.
"""
import argparse
import json
import os

from core.voice_rules import ATTEMPTS, MAX_SEC, MIN_SEC, TARGET_SEC

DEFAULT_MODEL = "Qwen/Qwen3-TTS-12Hz-1.7B-VoiceDesign"

# (persona_id, 음성 지시, 기준 문장). 성별은 기존 공식 음성에 맞췄다.
OFFICIAL_VOICES = [
    ("official_polite_brief",
     "A calm, clear female voice, polite and composed, moderate pace, "
     "like a trusted secretary going over the day's schedule.",
     "좋은 아침입니다. 오늘 첫 일정은 오전 열 시 회의예요. "
     "오후 세 시에 미팅이 하나 더 있으니, 노트북은 미리 챙겨 두세요."),
    ("official_casual_warm",
     "A soft, warm female voice, casual and affectionate, gentle morning tone, "
     "like a close friend waking you up.",
     "일어났어? 오늘 하루도 같이 힘내 보자. 아침은 꼭 챙겨 먹고. "
     "어제 늦게까지 일했으니까, 오늘은 너무 무리하지 마."),
    ("official_casual_blunt",
     "A low, gruff male voice, casual and blunt, short clipped sentences, dry and matter-of-fact.",
     "또 늦잠이야? 빨리 일어나. 아홉 시 회의 있잖아. "
     "어제 챙겨 둔 서류 잊지 말고, 우산도 들고 가. 오후에 비 온대."),
]

OUT_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "reference_voices")


def generate(voices, model_dir: str = DEFAULT_MODEL, force: bool = False):
    """voices: (persona_id, instruct, ref_text) 목록. 이미 있는 파일은 force가 아니면 건너뛴다."""
    import soundfile as sf
    import torch
    from qwen_tts import Qwen3TTSModel

    todo = [v for v in voices if force or not os.path.exists(os.path.join(OUT_DIR, f"{v[0]}.wav"))]
    if not todo:
        print("만들 음성이 없다 (다시 만들려면 --force)")
        return
    device = "mps" if torch.backends.mps.is_available() else "cpu"
    model = Qwen3TTSModel.from_pretrained(model_dir, device_map=device, dtype=torch.float32)
    for pid, instruct, ref_text in todo:
        best = None  # (목표 길이와의 차이, wav, sr)
        for _ in range(ATTEMPTS):
            wavs, sr = model.generate_voice_design(text=ref_text, language="Korean", instruct=instruct)
            sec = len(wavs[0]) / sr
            if best is None or abs(sec - TARGET_SEC) < best[0]:
                best = (abs(sec - TARGET_SEC), wavs[0], sr)
            if MIN_SEC <= sec <= MAX_SEC:
                break
        _, wav, sr = best
        sec = len(wav) / sr
        sf.write(os.path.join(OUT_DIR, f"{pid}.wav"), wav, sr)
        with open(os.path.join(OUT_DIR, f"{pid}.json"), "w", encoding="utf-8") as f:
            json.dump({"ref_text": ref_text}, f, ensure_ascii=False)
        warn = "" if MIN_SEC <= sec <= MAX_SEC else f"  ⚠ {MIN_SEC:g}~{MAX_SEC:g}초 밖 — 기준 문장 길이를 조정할 것"
        print(f"voice: {pid} {sec:.1f}s{warn}", flush=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--dev", action="store_true", help="공식 비서 대신 로컬 dev_* 비서 음성을 만든다")
    parser.add_argument("--force", action="store_true", help="이미 있는 음성도 다시 만든다")
    parser.add_argument("--only", nargs="+", metavar="ID", help="이 persona_id만 만든다")
    parser.add_argument("--model", default=DEFAULT_MODEL, help="VoiceDesign 모델 경로 또는 Hugging Face ID")
    args = parser.parse_args()

    if args.dev:
        from seed_dev_personas import PERSONAS
        voices = [(pid, instruct, ref_text) for pid, *_, instruct, ref_text in PERSONAS]
    else:
        voices = OFFICIAL_VOICES
    if args.only:
        unknown = set(args.only) - {v[0] for v in voices}
        if unknown:
            parser.error(f"모르는 persona_id: {', '.join(sorted(unknown))}")
        voices = [v for v in voices if v[0] in args.only]
    generate(voices, args.model, args.force)


if __name__ == "__main__":
    main()
