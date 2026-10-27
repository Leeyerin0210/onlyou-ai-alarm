"""로컬 개발 DB 전용 공개 비서(dev_*) 20명 시드. 상점 테마·인기 TOP5를 채워 보기 위한 것.

    DATABASE_URL=... python seed_dev_personas.py             # DB만
    python seed_dev_personas.py --voices <VoiceDesign 모델 폴더>  # 기준 음성 생성 (torch·qwen-tts 필요)
    python seed_dev_personas.py --images                      # 사진 생성 (Gemini, GEMINI_API_KEY 필요)
    python seed_dev_personas.py --images-local                # 사진 생성 (Mac 로컬 Stable Diffusion, diffusers 필요)

음성은 reference_voices/dev_*, 사진은 persona_images/dev_* 에 쓴다 — gitignore 대상이라 커밋되지 않는다.
이미 있는 파일은 건너뛴다(다시 만들려면 파일을 지울 것).
"""
import json
import os
import sys
from contextlib import closing

# (id, 이름, preset_key, 호칭, primary, secondary, 태그, 사용수, 음성 지시, 기준 문장)
PERSONAS = [
    ("dev_seoa", "서아", "polite_brief", "사용자님", "#7FA7D9", "#EEF4FB", ["차분", "아침", "일정"], 812,
     "A calm, clear female voice, composed and gentle, moderate pace, like a trusted secretary.",
     "좋은 아침이에요. 오늘은 오전 열 시 회의 하나만 있어요."),
    ("dev_jiho", "지호", "polite_brief", "님", "#4F6D7A", "#E6EEF1", ["차분", "직설"], 455,
     "A low, steady male voice, polite and concise, speaking evenly without much emotion.",
     "확인했습니다. 오후 세 시 일정은 내일로 옮겨 두었습니다."),
    ("dev_yuna", "유나", "polite_brief", "사용자님", "#B5A1D8", "#F4F0FA", ["차분", "다정"], 390,
     "A soft, warm female voice, polite and reassuring, slightly slow and soothing.",
     "오늘 하루 수고 많으셨어요. 내일 일정은 제가 정리해 둘게요."),
    ("dev_hyunwoo", "현우", "polite_brief", "님", "#2E5E8C", "#E3ECF5", ["동기부여", "일정"], 640,
     "A confident, energetic male voice, polite and crisp, upbeat and motivating.",
     "오늘 목표 세 가지 중 두 개 끝내셨어요. 하나만 더 하시면 됩니다."),
    ("dev_nari", "나리", "polite_brief", "사용자님", "#E9A86B", "#FDF3E9", ["아침", "동기부여"], 527,
     "A bright, clear female voice, polite and cheerful, lively morning energy.",
     "일어날 시간이에요! 오늘 날씨 맑대요. 산책하기 딱 좋아요."),
    ("dev_taemin", "태민", "polite_brief", "님", "#6C8E5E", "#EDF3EA", ["차분", "아침"], 210,
     "A gentle, deep male voice, polite and calm, relaxed and unhurried.",
     "천천히 일어나셔도 괜찮아요. 첫 일정까지 한 시간 남았어요."),
    ("dev_sora", "소라", "casual_warm", "너", "#FF9FB2", "#FFF0F3", ["다정", "아침"], 980,
     "A sweet, warm female voice, casual and affectionate, like a close friend.",
     "일어났어? 어제 늦게 잤지. 오늘은 아침 꼭 챙겨 먹어."),
    ("dev_minjun", "민준", "casual_warm", "너", "#F2B880", "#FFF5EC", ["다정", "유머"], 755,
     "A friendly, warm male voice, casual and playful, with a smile in the tone.",
     "야, 오늘 발표 잘했다며? 내가 그럴 줄 알았어."),
    ("dev_haeun", "하은", "casual_warm", "너", "#F7C6D9", "#FEF4F8", ["다정", "차분"], 432,
     "A soft, gentle female voice, casual and caring, quiet and tender.",
     "오늘 좀 힘들었지? 괜찮아, 일단 푹 자고 내일 얘기하자."),
    ("dev_seojun", "서준", "casual_warm", "너", "#8FC1A9", "#EEF7F2", ["동기부여", "다정"], 610,
     "An upbeat, bright male voice, casual and encouraging, energetic and warm.",
     "할 수 있어! 운동 딱 이십 분만 하자. 나도 같이 할게."),
    ("dev_ara", "아라", "casual_warm", "너", "#FFD166", "#FFF8E5", ["아침", "유머"], 870,
     "A lively, cheerful female voice, casual and bubbly, quick and playful.",
     "굿모닝! 오늘 운세 봤는데 대박이래. 얼른 일어나 봐."),
    ("dev_dohyun", "도현", "casual_warm", "너", "#A3B18A", "#F2F5EE", ["유머", "아침"], 305,
     "A relaxed, warm male voice, casual and humorous, laid-back pace.",
     "알람 세 번째야. 이번엔 진짜 일어나야 돼, 농담 아니고."),
    ("dev_chaewon", "채원", "casual_warm", "너", "#C9ADA7", "#F8F2F1", ["동기부여", "다정"], 268,
     "A clear, warm female voice, casual and supportive, steady and encouraging.",
     "어제보다 일찍 일어났네? 오늘 하루 잘 풀릴 것 같아."),
    ("dev_gun", "건", "casual_blunt", "야", "#4A4E69", "#E9E9EF", ["직설", "유머"], 720,
     "A low, gruff male voice, casual and blunt, short sentences, dry tone.",
     "또 늦잠이냐. 회의 삼십 분 남았다. 빨리 씻어."),
    ("dev_rina", "리나", "casual_blunt", "야", "#9A8C98", "#F3F0F3", ["직설", "동기부여"], 498,
     "A sharp, cool female voice, casual and blunt, brisk and matter-of-fact.",
     "그거 오늘까지라며. 핑계 대지 말고 지금 시작해."),
    ("dev_jaeyoung", "재영", "casual_blunt", "야", "#5C677D", "#E8EBF0", ["직설", "차분"], 186,
     "A flat, calm male voice, casual and terse, unimpressed but not unkind.",
     "우산 챙겨. 오후에 비 온대. 말했다."),
    ("dev_bom", "봄", "casual_blunt", "너", "#E07A5F", "#FBEDE9", ["유머", "직설"], 560,
     "A husky, playful female voice, casual and teasing, a bit sarcastic.",
     "알람 끄고 다시 자는 거 다 봤거든. 일어나라."),
    ("dev_kangmin", "강민", "casual_blunt", "야", "#3D5A80", "#E5EBF2", ["동기부여", "직설"], 344,
     "A strong, firm male voice, casual and blunt, like a strict coach.",
     "어제도 안 했잖아. 오늘은 무조건 한다. 일어나."),
    ("dev_yeonwoo", "연우", "casual_blunt", "너", "#81B29A", "#EDF5F0", ["유머", "아침"], 233,
     "A dry, deadpan female voice, casual and blunt, with subtle humor.",
     "아침이야. 해는 떴고 너만 안 일어났어."),
    ("dev_siwoo", "시우", "polite_brief", "님", "#577590", "#E7EDF2", ["유머", "일정"], 152,
     "A smooth, light male voice, polite with a gentle humorous touch, moderate pace.",
     "오늘 일정은 다섯 개예요. 커피 두 잔은 필요하실 것 같네요."),
]

# 사진 외모 묘사 — 성별은 음성 지시와 맞춘다. 연령을 특정하지 않는다(성인으로만).
APPEARANCE = {
    "dev_seoa": "a Korean woman with neat shoulder-length black hair, navy cardigan, calm gentle smile",
    "dev_jiho": "a Korean man with short neat black hair, white shirt and dark blazer, composed expression",
    "dev_yuna": "a Korean woman with long wavy brown hair, cream knit sweater, soft reassuring smile",
    "dev_hyunwoo": "a Korean man with short side-parted hair, light blue shirt, confident bright smile",
    "dev_nari": "a Korean woman with a high ponytail, orange hoodie, cheerful wide smile",
    "dev_taemin": "a Korean man with soft wavy hair, olive green sweater, relaxed gentle smile",
    "dev_sora": "a Korean woman with long straight black hair and bangs, pink sweater, sweet warm smile",
    "dev_minjun": "a Korean man with tousled brown hair, beige hoodie, playful grin",
    "dev_haeun": "a Korean woman with a loose low bun, lavender cardigan, tender quiet smile",
    "dev_seojun": "a Korean man with short sporty hair, mint athletic jacket, encouraging smile",
    "dev_ara": "a Korean woman with a short bob haircut, yellow sweater, bubbly laughing expression",
    "dev_dohyun": "a Korean man with messy medium hair, sage green shirt, easygoing lopsided smile",
    "dev_chaewon": "a Korean woman with medium-length straight hair tucked behind ears, dusty rose blouse, supportive smile",
    "dev_gun": "a Korean man with short cropped hair, black t-shirt, unimpressed slight frown",
    "dev_rina": "a Korean woman with sleek straight black hair, charcoal blazer, cool sharp expression",
    "dev_jaeyoung": "a Korean man with medium straight hair and glasses, gray sweatshirt, deadpan neutral face",
    "dev_bom": "a Korean woman with a messy short wolf cut, terracotta jacket, teasing smirk",
    "dev_kangmin": "a Korean man with a buzz cut, navy training jacket, firm determined look",
    "dev_yeonwoo": "a Korean woman with shoulder-length layered hair, green striped shirt, deadpan half-smile",
    "dev_siwoo": "a Korean man with soft parted hair, slate blue knit, gentle amused smile",
}
IMAGE_STYLE = (
    "Semi-realistic digital painting portrait of {who}, an adult. Head and shoulders, facing the viewer, "
    "centered, square composition. Soft natural window light, bright blurred minimal interior background "
    "with a hint of {color}. Clean smooth illustration style, no text, no watermark."
)


def seed_db():
    from core.presets import PRESETS
    from core.rdb import get_conn, init_schema
    from core.tags import normalize_tags
    from seed_personas import SYSTEM_CREATOR  # 공식 비서와 같은 작성자로 상점에 노출

    init_schema()
    with closing(get_conn()) as conn, conn.cursor() as cur:
        for pid, name, preset, call, c1, c2, tags, usage, _, _ in PERSONAS:
            assert preset in PRESETS, f"미지의 preset_key: {preset}"
            cur.execute(
                "INSERT INTO personas (id, name, description, user_call_sign, primary_hex, secondary_hex, "
                "creator_id, preset_key, is_private, tags, usage_count, image_url) "
                "VALUES (%s,%s,%s,%s,%s,%s,%s,%s, FALSE, %s, %s, %s) "
                "ON CONFLICT (id) DO UPDATE SET name=EXCLUDED.name, description=EXCLUDED.description, "
                "user_call_sign=EXCLUDED.user_call_sign, primary_hex=EXCLUDED.primary_hex, "
                "secondary_hex=EXCLUDED.secondary_hex, preset_key=EXCLUDED.preset_key, "
                "tags=EXCLUDED.tags, usage_count=EXCLUDED.usage_count, image_url=EXCLUDED.image_url",
                (pid, name, PRESETS[preset].description, call, c1, c2,
                 SYSTEM_CREATOR, preset, normalize_tags(tags), usage,
                 # 사진(--images)은 persona_images/{id}.jpg — main.py가 /persona-images로 서빙
                 f"persona-images/{pid}.jpg"),
            )
    print(f"seeded {len(PERSONAS)} dev personas")


def generate_voices(model_dir: str):
    import soundfile as sf
    import torch
    from qwen_tts import Qwen3TTSModel

    out_dir = os.path.join(os.path.dirname(os.path.abspath(__file__)), "reference_voices")
    device = "mps" if torch.backends.mps.is_available() else "cpu"
    model = Qwen3TTSModel.from_pretrained(model_dir, device_map=device, dtype=torch.float32)
    for pid, name, *_, instruct, ref_text in PERSONAS:
        wav_path = os.path.join(out_dir, f"{pid}.wav")
        if os.path.exists(wav_path):
            continue
        wavs, sr = model.generate_voice_design(text=ref_text, language="Korean", instruct=instruct)
        sf.write(wav_path, wavs[0], sr)
        with open(os.path.join(out_dir, f"{pid}.json"), "w", encoding="utf-8") as f:
            json.dump({"ref_text": ref_text}, f, ensure_ascii=False)
        print(f"voice: {pid} ({name})", flush=True)


def generate_images():
    """Gemini로 사진 생성 → persona_images/{id}.jpg (512px, macOS sips로 변환). 있는 건 건너뛴다."""
    import subprocess
    import tempfile

    from google import genai

    from core.config import settings

    out_dir = os.path.join(os.path.dirname(os.path.abspath(__file__)), "persona_images")
    client = genai.Client(api_key=settings.GEMINI_API_KEY)
    for pid, name, _, _, color, *_ in PERSONAS:
        jpg_path = os.path.join(out_dir, f"{pid}.jpg")
        if os.path.exists(jpg_path):
            continue
        res = client.models.generate_content(
            model="gemini-2.5-flash-image",
            contents=IMAGE_STYLE.format(who=APPEARANCE[pid], color=color),
        )
        data = next(p.inline_data.data for p in res.candidates[0].content.parts if p.inline_data)
        with tempfile.NamedTemporaryFile(suffix=".png") as tmp:
            tmp.write(data)
            tmp.flush()
            subprocess.run(["sips", "-s", "format", "jpeg", "-s", "formatOptions", "80",
                            "-Z", "512", tmp.name, "--out", jpg_path], check=True, capture_output=True)
        print(f"image: {pid} ({name})", flush=True)


SD_MODEL = "Lykon/dreamshaper-8"  # SD 1.5 계열, 512px 네이티브, CreativeML OpenRAIL-M
SD_PROMPT = (
    "semi-realistic digital painting portrait of {who}, adult, head and shoulders, facing viewer, centered, "
    "bright warm daylight from a window, cozy bright minimal room background, {color} accents, "
    "clean smooth illustration, soft shading, high quality"
)
SD_NEGATIVE = ("lowres, blurry face, deformed, extra fingers, text, watermark, nsfw, child, teen, "
               "cleavage, multiple people, dark, gloomy")


def generate_images_local():
    """Gemini 없이 Mac에서 Stable Diffusion으로 사진 생성 (torch·diffusers 필요). 있는 건 건너뛴다."""
    import torch
    from diffusers import DPMSolverMultistepScheduler, StableDiffusionPipeline

    out_dir = os.path.join(os.path.dirname(os.path.abspath(__file__)), "persona_images")
    device = "mps" if torch.backends.mps.is_available() else "cpu"
    pipe = StableDiffusionPipeline.from_pretrained(
        SD_MODEL, dtype=torch.float16 if device == "mps" else torch.float32,
        variant="fp16", use_safetensors=True, safety_checker=None,
    )
    # 모델 기본 스케줄러(deis)는 karras 시그마와 같이 못 쓴다
    pipe.scheduler = DPMSolverMultistepScheduler.from_config(
        pipe.scheduler.config, use_karras_sigmas=True, algorithm_type="dpmsolver++")
    pipe = pipe.to(device)
    for i, (pid, name, _, _, color, *_) in enumerate(PERSONAS):
        jpg_path = os.path.join(out_dir, f"{pid}.jpg")
        if os.path.exists(jpg_path):
            continue
        img = pipe(SD_PROMPT.format(who=APPEARANCE[pid], color=color), negative_prompt=SD_NEGATIVE,
                   num_inference_steps=25, guidance_scale=6.5, width=512, height=512,
                   generator=torch.Generator("cpu").manual_seed(1000 + i)).images[0]
        img.save(jpg_path, quality=85)
        print(f"image: {pid} ({name})", flush=True)


if __name__ == "__main__":
    if len(sys.argv) == 3 and sys.argv[1] == "--voices":
        generate_voices(sys.argv[2])
    elif len(sys.argv) == 2 and sys.argv[1] == "--images":
        generate_images()
    elif len(sys.argv) == 2 and sys.argv[1] == "--images-local":
        generate_images_local()
    else:
        seed_db()
