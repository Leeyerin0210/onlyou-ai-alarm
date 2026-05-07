import firebase_admin
from firebase_admin import credentials
from firebase_admin import firestore
import os

# Firebase 초기화
# serviceAccountKey.json 파일이 backend 폴더에 있어야 합니다.
cred = credentials.Certificate('serviceAccountKey.json')
firebase_admin.initialize_app(cred)

db = firestore.client()

def seed_personas():
    print("--- Seeding Personas ---")
    personas_ref = db.collection('personas')
    
    # 1. 미야 (기본 비서)
    miya_default = {
        "id": "miya_default",
        "name": "미야",
        "prompt": "너는 친절하고 다정한 개인 비서 '미야'야. 주인의 일정을 관리하고 항상 밝은 모습으로 응원해줘.",
        "description": "코네(Conne)의 기본 비서입니다. 다정한 성격으로 당신의 하루를 챙겨줍니다.",
        "voiceTone": 1.0,
        "voiceSpeed": 1.0,
        "voicePrompt": "다정하고 친절한 어조로",
        "userCallSign": "주인님",
        "imageUrl": "https://images.unsplash.com/photo-1544005313-94ddf0286df2?auto=format&fit=crop&q=80&w=200", # 임시 이미지
        "themeColors": {
            "primaryHex": "#FFB7C5",
            "secondaryHex": "#FFF0F5"
        }
    }
    
    # 2. 루나 (지적인 비서)
    luna_cool = {
        "id": "luna_cool",
        "name": "루나",
        "prompt": "너는 차분하고 지적인 비서 '루나'야. 효율적인 일정 관리와 논리적인 대화를 선호해.",
        "description": "차분하고 논리적인 루나와 함께 효율적으로 하루를 관리해 보세요.",
        "voiceTone": 0.8,
        "voiceSpeed": 1.1,
        "voicePrompt": "차분하고 지적인 어조로",
        "userCallSign": "사용자님",
        "imageUrl": "https://images.unsplash.com/photo-1438761681033-6461ffad8d80?auto=format&fit=crop&q=80&w=200", # 임시 이미지
        "themeColors": {
            "primaryHex": "#6495ED",
            "secondaryHex": "#F0F8FF"
        }
    }

    personas_ref.document(miya_default["id"]).set(miya_default)
    personas_ref.document(luna_cool["id"]).set(luna_cool)
    print("Personas seeded successfully.")

def seed_test_user():
    print("--- Seeding Test User ---")
    # 실제 Firebase Auth UID를 알 수 없으므로, 앱에서 사용할 테스트용 문서를 생성합니다.
    # 만약 로그인한 상태라면 해당 UID로 문서를 만들어야 합니다.
    users_ref = db.collection('users')
    
    test_user = {
        "selectedPersonaId": "miya_default",
        "lastLogin": firestore.SERVER_TIMESTAMP
    }
    
    # 테스트를 위해 고정된 ID 하나 생성
    users_ref.document("test_user_001").set(test_user)
    print("Test user seeded successfully.")

if __name__ == "__main__":
    seed_personas()
    seed_test_user()
    print("Done!")
