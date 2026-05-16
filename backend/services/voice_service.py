import os
import json
import io
import torch
import soundfile as sf
from typing import Optional
from huggingface_hub import snapshot_download

# Qwen-TTS 패키지
try:
    from qwen_tts import Qwen3TTSModel
except ImportError:
    print("Warning: qwen_tts not installed. Voice synthesis will be disabled.")
    Qwen3TTSModel = None

class VoiceEngine:
    def __init__(self):
        self.design_model = None
        self.clone_model = None
        self.device = "cuda:0" if torch.cuda.is_available() else "cpu"
        
        base_dir = os.path.dirname(os.path.dirname(__file__)) # backend/
        self.design_model_path = os.path.join(base_dir, "Qwen3-TTS-12Hz-1.7B-VoiceDesign")
        self.clone_model_path = "Qwen/Qwen3-TTS-12Hz-0.6B-Base"
        
        self.reference_dir = os.path.join(base_dir, "reference_voices")
        self.prompt_cache = {}
        os.makedirs(self.reference_dir, exist_ok=True)

    def load_design_model(self):
        if Qwen3TTSModel and self.design_model is None:
            if not os.path.exists(self.design_model_path):
                print(f"--- Model not found. Downloading from Hugging Face: Qwen/Qwen3-TTS-12Hz-1.7B-VoiceDesign ---")
                try:
                    snapshot_download(
                        repo_id="Qwen/Qwen3-TTS-12Hz-1.7B-VoiceDesign",
                        local_dir=self.design_model_path,
                        local_dir_use_symlinks=False
                    )
                except Exception as e:
                    print(f"Error downloading model: {e}")
                    return

            print(f"--- Loading Design Model (1.7B) ---")
            try:
                self.design_model = Qwen3TTSModel.from_pretrained(self.design_model_path, device_map=self.device, dtype=torch.bfloat16 if torch.cuda.is_available() else torch.float32, attn_implementation="sdpa")
            except Exception as e:
                print(f"Error loading design model: {e}")

    def load_clone_model(self):
        if Qwen3TTSModel and self.clone_model is None:
            print(f"--- Loading Clone Model (0.6B) ---")
            try:
                self.clone_model = Qwen3TTSModel.from_pretrained(self.clone_model_path, device_map=self.device, dtype=torch.bfloat16 if torch.cuda.is_available() else torch.float32, attn_implementation="sdpa")
            except Exception as e:
                print(f"Error loading clone model: {e}")

    def synthesize_design(self, text: str, instruct: str):
        self.load_design_model()
        wavs, sr = self.design_model.generate_voice_design(text=text, language="Korean", instruct=instruct)
        buffer = io.BytesIO()
        sf.write(buffer, wavs[0], sr, format='WAV')
        buffer.seek(0)
        return buffer

    def save_master_wav(self, persona_id: str, audio_data: bytes, ref_text: str):
        self.load_clone_model()
        audio_path = os.path.join(self.reference_dir, f"{persona_id}.wav")
        meta_path = os.path.join(self.reference_dir, f"{persona_id}.json")
        with open(audio_path, "wb") as f: f.write(audio_data)
        with open(meta_path, "w", encoding="utf-8") as f: json.dump({"ref_text": ref_text}, f, ensure_ascii=False)
        prompt_items = self.clone_model.create_voice_clone_prompt(ref_audio=audio_path, ref_text=ref_text, x_vector_only_mode=False)
        self.prompt_cache[persona_id] = prompt_items

    def synthesize_clone(self, text: str, persona_id: str):
        self.load_clone_model()
        prompt_items = self.prompt_cache.get(persona_id)
        if prompt_items is None:
            audio_path = os.path.join(self.reference_dir, f"{persona_id}.wav")
            meta_path = os.path.join(self.reference_dir, f"{persona_id}.json")
            if not os.path.exists(audio_path): raise FileNotFoundError(f"Master WAV not found for {persona_id}")
            with open(meta_path, "r", encoding="utf-8") as f: ref_text = json.load(f)["ref_text"]
            prompt_items = self.clone_model.create_voice_clone_prompt(ref_audio=audio_path, ref_text=ref_text, x_vector_only_mode=False)
            self.prompt_cache[persona_id] = prompt_items
        wavs, sr = self.clone_model.generate_voice_clone(text=text, language="Korean", voice_clone_prompt=prompt_items)
        buffer = io.BytesIO()
        sf.write(buffer, wavs[0], sr, format='WAV')
        buffer.seek(0)
        return buffer

voice_engine = VoiceEngine()
