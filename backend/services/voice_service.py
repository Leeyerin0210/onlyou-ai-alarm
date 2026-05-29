import os
import json
from io import BytesIO
from elevenlabs.client import ElevenLabs

class VoiceEngine:
    def __init__(self):
        # ElevenLabs Client 초기화
        # 환경 변수에서 ELEVENLABS_API_KEY를 자동으로 읽거나 명시적으로 주입할 수 있습니다.
        self.api_key = os.getenv("ELEVENLABS_API_KEY")
        if not self.api_key:
            print("Warning: ELEVENLABS_API_KEY is not set. Voice synthesis will fail.")
        self.client = ElevenLabs(api_key=self.api_key)
        
        base_dir = os.path.dirname(os.path.dirname(__file__)) # backend/
        self.reference_dir = os.path.join(base_dir, "reference_voices")
        os.makedirs(self.reference_dir, exist_ok=True)

    def design_voice_preview(self, text: str, instruct: str):
        """
        텍스트 프롬프트를 바탕으로 음성을 디자인하고 미리보기 오디오를 반환합니다.
        (ElevenLabs의 Text-to-Voice Design 기능 활용)
        반환값: (미리보기_오디오_바이트, generated_voice_id)
        """
        try:
            # ElevenLabs Voice Design은 최소 글자 수 제한이 엄격합니다.
            # voice_description: 최소 20자
            # text: 최소 100자
            
            # 1. Description 패딩 (최소 20자)
            padded_instruct = instruct
            if len(padded_instruct) < 20:
                padded_instruct += " 자연스럽고 감정이 풍부하게 말하는 목소리입니다."
                
            # 2. Text 패딩 (최소 100자)
            padded_text = text
            if len(padded_text) < 100:
                # 텍스트가 짧으면 미리보기용 기본 텍스트를 덧붙여 100자를 채웁니다.
                padding_sentence = " " + "이 목소리는 ElevenLabs의 음성 디자인 인공지능 모델을 통해 만들어진 샘플입니다. 어떻게 들리시나요? 마음에 드신다면 이 목소리를 저장해서 계속 사용할 수 있습니다."
                while len(padded_text) < 100:
                    padded_text += padding_sentence

            # ElevenLabs Voice Design 호출
            result = self.client.text_to_voice.design(
                text=padded_text,
                voice_description=padded_instruct,
            )
            
            # 생성된 프리뷰 중 첫 번째를 사용
            if result.previews and len(result.previews) > 0:
                preview = result.previews[0]
                audio_bytes = preview.audio_base_64 # Base64 encoded string
                import base64
                decoded_audio = base64.b64decode(audio_bytes)
                return decoded_audio, preview.generated_voice_id
            else:
                raise Exception("No previews generated")
                
        except Exception as e:
            print(f"Error in design_voice_preview: {e}")
            raise

    def create_voice_from_preview(self, persona_id: str, generated_voice_id: str, voice_name: str, voice_description: str):
        """
        미리보기로 만든 음성이 마음에 들 경우, 이를 실제 Voice로 생성하고 ID를 매핑합니다.
        """
        try:
            new_voice = self.client.text_to_voice.create(
                voice_name=voice_name,
                voice_description=voice_description,
                generated_voice_id=generated_voice_id
            )
            
            voice_id = new_voice.voice_id
            
            # persona_id 와 voice_id 매핑 저장
            meta_path = os.path.join(self.reference_dir, f"{persona_id}.json")
            with open(meta_path, "w", encoding="utf-8") as f: 
                json.dump({"voice_id": voice_id}, f, ensure_ascii=False)
                
            return voice_id
        except Exception as e:
            print(f"Error in create_voice_from_preview: {e}")
            raise

    def synthesize_voice(self, text: str, persona_id: str):
        """
        기존에 생성된 페르소나의 Voice ID로 텍스트를 음성으로 변환(합성)합니다.
        """
        meta_path = os.path.join(self.reference_dir, f"{persona_id}.json")
        if not os.path.exists(meta_path):
            raise FileNotFoundError(f"Voice mapping not found for {persona_id}")
            
        with open(meta_path, "r", encoding="utf-8") as f:
            voice_id = json.load(f)["voice_id"]
            
        try:
            # ElevenLabs TTS API 호출 (스트리밍이 아닌 전체 파일 반환 방식)
            # multilingual_v2가 한국어 등을 잘 지원합니다.
            audio_generator = self.client.text_to_speech.convert(
                voice_id=voice_id,
                output_format="mp3_44100_128",
                text=text,
                model_id="eleven_multilingual_v2",
            )
            
            # 제너레이터 결과를 바이트 배열로 합치기
            audio_bytes = b""
            for chunk in audio_generator:
                audio_bytes += chunk
                
            buffer = BytesIO(audio_bytes)
            buffer.seek(0)
            return buffer
            
        except Exception as e:
            print(f"Error in synthesize_voice: {e}")
            raise

voice_engine = VoiceEngine()
