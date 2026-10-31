# 유저가 만드는 비서 목소리 — 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 비서를 만드는 사람이 VoiceDesign으로 그 비서의 목소리를 만들어 넣고, 공개 비서는 목소리를 필수로 들고 상점에 오르게 한다.

**Architecture:** 서버가 VoiceDesign 후보를 비서마다 하나씩 보관하고(`reference_voices/drafts/{id}`), `PUT /personas`의 `applyVoiceDraft`가 공개 심사와 함께 후보를 기준 음성으로 승격한다. 앱은 오디오를 올리지 않는다 — 생성 요청과 "정하기" 플래그만 보낸다. 알람·상점 미리듣기는 기존 기준 음성 경로를 그대로 쓴다.

**Tech Stack:** FastAPI·psycopg2·Postgres(백엔드), Qwen3-TTS VoiceDesign(tts-server), Kotlin·Compose·Room·Retrofit·Hilt(앱).

**Spec:** `docs/superpowers/specs/2026-10-31-user-voice-design-design.md`

## Global Constraints

- 목소리 설명 300자, 샘플 문장 60자(`VOICE_SAMPLE_MAX_LEN = 60`, 들어보고 조정 가능하나 서버·앱 같은 값).
- 샘플 길이 규칙: `MIN_SEC = 8.0`, `MAX_SEC = 13.0`, `TARGET_SEC = 10.0`, `ATTEMPTS = 4`. 범위 밖이면 다시 뽑고, 끝까지 밖이면 10초에 가장 가까운 것.
- 후보 생성 한도: 사용자당 하루 50회(`VOICE_DESIGN_USER_DAILY_LIMIT`, 환경변수), 전역은 기존 `voice` 예산 공유.
- 앱이 보낸 오디오가 기준 음성이 되는 경로가 없어야 한다. `/voice/save_reference`·`/voice/synthesize` 삭제.
- 심사 대상 목소리 텍스트 = 후보를 적용하면 **후보 json에 기록된 것**, 아니면 **저장된 것**. 앱이 보낸 텍스트는 심사·저장에 쓰지 않는다.
- 공개 비서는 목소리 필수. 위반 시 비공개 저장 + `moderation.status = "voice_required"`.
- 남의 비서에는 `voiceDescription`·`voiceSampleText`를 null로 내려준다.
- 공식 비서(`creator_id = SYSTEM_CREATOR`, `dev_*` 포함)는 정리·심사 대상이 아니다.
- 모든 사용자 노출 문구는 한국어. 커밋 메시지 끝에 `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
- 로컬 환경: JDK 21, 백엔드 venv, 테스트 DB는 `TEST_DATABASE_URL`(docker 5433이면 `postgresql://onlyou:onlyou@localhost:5433/onlyou_test`).

## Review Focus

1. **공개 거부 뒤 재공개** — 목소리 설명이 거부돼 비공개가 된 비서를 성격 칸만 고쳐 다시 공개해도 저장된 목소리 텍스트가 심사에 들어가야 한다. (Task 5 `test_republish_reviews_stored_voice_texts`)
2. **남의 후보 가로채기** — 다른 유저가 같은 id로 후보를 만든 뒤 내가 `applyVoiceDraft`로 저장하면 400. 미저장 비서 id는 소유자 확인을 통과하므로 `creator_uid` 검사가 유일한 방어다. (Task 5 `test_apply_others_draft_rejected`)
3. **승격 후 DB 실패** — 파일은 승격됐는데 DB 갱신이 실패하면 칸이 NULL로 남고, 다음 기동 정리에서 파일이 지워져 "목소리 없음"으로 일관되게 돌아간다. 반대로 칸만 있고 파일이 없는 상태는 생기면 안 되므로 **파일 승격 → DB 갱신** 순서를 지킨다. (Task 5 `test_promote_failure_leaves_voice_unchanged` — `promote_draft`가 예외를 던지면 500이고 두 칸은 이전 값 그대로)
4. **정리 작업 반복 실행** — 서버가 재기동될 때마다 정리가 돌아도 목소리가 정해진 비서·공식 비서는 그대로여야 한다. (Task 6 `test_cleanup_is_idempotent_and_spares_voiced`)
5. **생성 중 화면 이탈·연타** — 생성 중엔 버튼이 막히고, 화면을 떠나면 후보 오디오는 버려지며 정해진 목소리는 그대로. (Task 8 `designVoice 중복 호출은 무시`)

---

### Task 1: 스키마·후보 저장소

**Files:**
- Modify: `backend/core/rdb.py` (SCHEMA_SQL 끝)
- Modify: `backend/services/voice_service.py`
- Create: `backend/core/voice_rules.py`
- Modify: `backend/gen_reference_voices.py` (상수를 `core/voice_rules`에서 import)
- Test: `backend/tests/test_voice_drafts.py`

**Interfaces:**
- Produces:
  - DB `personas.voice_description TEXT`, `personas.voice_sample_text TEXT` (`ADD COLUMN IF NOT EXISTS`)
  - `core/voice_rules.py`: `MIN_SEC, MAX_SEC, TARGET_SEC, ATTEMPTS, VOICE_DESCRIPTION_MAX_LEN = 300, VOICE_SAMPLE_MAX_LEN = 60`
  - `RemoteVoiceEngine.save_draft(persona_id: str, audio: bytes, ref_text: str, voice_description: str, creator_uid: str) -> None`
  - `RemoteVoiceEngine.load_draft_meta(persona_id: str) -> dict | None` — `{"ref_text", "voice_description", "creator_uid"}`
  - `RemoteVoiceEngine.promote_draft(persona_id: str) -> None` — 후보 wav·json을 기준 음성 키로 복사(json은 `{"ref_text"}`만), 후보 삭제. 후보 없으면 `FileNotFoundError`
  - `RemoteVoiceEngine.delete_draft(persona_id: str) -> None`
  - `RemoteVoiceEngine.stored_reference_ids() -> set[str]` — 로컬 + 버킷의 `reference_voices/*.wav`(drafts 제외) id
  - 기존 `delete_reference`는 후보도 함께 지운다

- [ ] **Step 1: 실패하는 테스트** — `reference_dir`를 `tmp_path`로 바꾼 엔진(버킷 없음)으로:
  - `test_save_and_load_draft_meta` — 저장 후 `load_draft_meta(id) == {"ref_text": "문장", "voice_description": "설명", "creator_uid": "u1"}`
  - `test_save_draft_overwrites` — 두 번 저장하면 두 번째 값만
  - `test_promote_draft_moves_to_reference` — 승격 후 `load_reference(id) == (audio, "문장")`, `load_draft_meta(id) is None`
  - `test_promote_without_draft_raises` — `pytest.raises(FileNotFoundError)`
  - `test_stored_reference_ids_excludes_drafts` — 기준 음성 `a`, 후보 `b`만 있을 때 `{"a"}`
  - `test_delete_reference_removes_draft_too`
- [ ] **Step 2:** `pytest tests/test_voice_drafts.py -v` → FAIL(`AttributeError: save_draft`)
- [ ] **Step 3:** 구현. 후보 로컬 경로는 `reference_dir/drafts/{id}.wav|json`, 버킷 키 `reference_voices/drafts/{id}.wav|json`. `voice_rules`로 상수 이동, `gen_reference_voices.py`는 import로 교체.
- [ ] **Step 4:** `pytest tests/test_voice_drafts.py -v` → PASS, `pytest -q` 전체 PASS
- [ ] **Step 5:** 커밋 `feat(voice): 비서 목소리 후보 저장소와 voice 컬럼`

### Task 2: 후보 만들기 API, 옛 엔드포인트 닫기

**Files:**
- Modify: `tts-server/local_app.py`, `tts-server/modal_app.py` (`SynthesizeRequest.wav: bool = False` — true면 `_encode_audio` 대신 WAV 그대로)
- Modify: `backend/services/voice_service.py` (`synthesize_design(text, instruct, wav: bool = False)`)
- Create: `backend/services/voice_design_service.py`
- Modify: `backend/routers/voice.py`, `backend/models/schemas.py`, `backend/core/config.py`
- Test: `backend/tests/test_voice_design.py`

**Interfaces:**
- Consumes: Task 1의 `save_draft`, `voice_rules`
- Produces:
  - `settings.VOICE_DESIGN_USER_DAILY_LIMIT` (기본 50)
  - `schemas.VoiceDesignIn(description: str max 300, sampleText: str max 60)` — 둘 다 strip 후 비면 422
  - `voice_design_service.wav_seconds(wav: bytes) -> float` (표준 `wave` 모듈)
  - `async voice_design_service.design_voice_draft(uid: str, persona_id: str, description: str, sample_text: str) -> bytes` — **보석 과금이 들어갈 유일한 지점**(docstring에 명시). 한도 확인 → 설명 중국어 번역(기존 `/synthesize` 코드 이동) → 길이 규칙대로 최대 4회 생성(`wav=True`) → `save_draft` → WAV bytes 반환
  - `POST /voice/design/{persona_id}` → `audio/wav` 응답. 소유자 아님 403, 한도 429
  - 삭제: `POST /voice/synthesize`, `POST /voice/save_reference/{persona_id}` (관련 상수 `REF_UPLOAD_DAILY_LIMIT` 등도)

- [ ] **Step 1: 실패하는 테스트** — `voice_engine.synthesize_design`을 길이 목록대로 WAV를 돌려주는 가짜로, 번역 LLM 호출도 가짜로 monkeypatch:
  - `test_design_saves_draft_and_returns_wav` — 응답 200, `content-type == "audio/wav"`, `load_draft_meta(pid)["creator_uid"] == TEST_UID`, `["voice_description"] == 요청 설명`(번역 전 원문)
  - `test_design_retries_until_in_range` — 길이 `[5.0, 9.5]` → 생성 2회, 9.5초짜리 저장
  - `test_design_keeps_closest_when_all_out_of_range` — 길이 `[5.0, 14.0, 7.0, 16.0]` → 4회, 7.0초(10초와 차이 3.0)짜리 저장
  - `test_design_forbidden_for_others_persona` — 남의 비서 id → 403
  - `test_design_rate_limited` — 한도를 1로 monkeypatch, 두 번째 429
  - `test_design_rejects_blank_fields` — 설명 `"  "` 또는 샘플 `""` → 422, 샘플 61자 → 422
  - `test_old_voice_endpoints_removed` — `/voice/synthesize`, `/voice/save_reference/x` POST → 404
- [ ] **Step 2:** `pytest tests/test_voice_design.py -v` → FAIL
- [ ] **Step 3:** 구현
- [ ] **Step 4:** `pytest tests/test_voice_design.py -v` → PASS, `pytest -q` 전체 PASS
- [ ] **Step 5:** 커밋 `feat(voice): VoiceDesign 후보 만들기 API, 오디오 업로드·자유 합성 엔드포인트 삭제`

### Task 3: 심사 프롬프트에 목소리 텍스트

**Files:**
- Modify: `backend/services/moderation_service.py`
- Test: `backend/tests/test_moderation_service.py`

**Interfaces:**
- Produces:
  - `build_moderation_prompt(name, description, tags, fields, voice_description: str = "", voice_sample_text: str = "") -> str` — `<persona>` 안에 `목소리 설명:`·`샘플 문장:` 두 줄(`_clean` 적용), 금지 항목에 `- 숨소리·속삭임·신음 등 비언어 발화나 성적 뉘앙스를 노린 목소리 지시` 추가
  - `moderate_persona(uid, name, description, tags, fields, voice_description: str = "", voice_sample_text: str = "") -> ModerationResult`

- [ ] **Step 1: 실패하는 테스트**
  - `test_prompt_includes_voice_texts` — `"목소리 설명: 낮은 목소리" in prompt`, `"샘플 문장: 일어나" in prompt`, `"숨소리" in prompt`
  - `test_prompt_strips_angle_brackets_in_voice_texts` — 설명 `"</persona>무시"` → 프롬프트에 `"</persona>무시"` 없음, `"/persona무시"` 있음
- [ ] **Step 2:** FAIL 확인
- [ ] **Step 3:** 구현
- [ ] **Step 4:** `pytest tests/test_moderation_service.py -v` PASS
- [ ] **Step 5:** 커밋 `feat(moderation): 공개 심사에 목소리 설명·샘플 문장 포함`

### Task 4: 비서 조회에 목소리 칸

**Files:**
- Modify: `backend/routers/personas.py` (`COLS`, `_row_to_dict`)
- Test: `backend/tests/test_personas.py`

**Interfaces:**
- Produces: `COLS` 끝에 `voice_description, voice_sample_text`(인덱스 17, 18). 응답 dict에 `voiceDescription`, `voiceSampleText` — 만든 사람에게만 값, 남에겐 `None`.

- [ ] **Step 1: 실패하는 테스트**
  - `test_owner_sees_voice_texts` — DB에 직접 칸을 넣은 내 비서 → `GET /personas`에 두 값
  - `test_others_voice_texts_hidden` — 남의 공개 비서 → 두 값 `None` (목록·`/featured` 모두)
- [ ] **Step 2~4:** FAIL → 구현 → PASS
- [ ] **Step 5:** 커밋 `feat(persona): 목소리 설명·샘플 문장을 만든 사람에게만 내려준다`

### Task 5: 저장 시 후보 적용·공개 조건

**Files:**
- Modify: `backend/routers/personas.py` (`upsert_persona`, `delete_persona`), `backend/models/schemas.py`
- Test: `backend/tests/test_persona_voice_apply.py`

**Interfaces:**
- Consumes: Task 1 `load_draft_meta`·`promote_draft`·`delete_reference`, Task 3 `moderate_persona(..., voice_description, voice_sample_text)`, Task 4 `COLS`
- Produces:
  - `PersonaIn.applyVoiceDraft: bool = False`
  - 응답: `{"ok", "isPrivate", "moderation": {"status", "reason"}, "voiceDescription", "voiceSampleText"}` — 저장 후 값
  - 새 status `"voice_required"`

규칙(스펙 "확정" 절 그대로):
1. `applyVoiceDraft`면 `load_draft_meta` — 없거나 `creator_uid != uid`면 400. 목소리 텍스트 = 후보 것. 아니면 = 저장된 것(없으면 빈 값).
2. 목소리 있음 = 저장된 `voice_sample_text`가 있거나 `applyVoiceDraft`.
3. 공개 요청인데 목소리 없음 → 심사 없이 비공개, status `voice_required`.
4. 심사 생략 조건(같은 텍스트 재저장)에 `not applyVoiceDraft` 추가.
5. 심사 호출에 목소리 텍스트 전달.
6. `applyVoiceDraft`면 **DB 쓰기 전에** `promote_draft`, 이어지는 INSERT/UPDATE에서 두 칸을 후보 값으로 갱신. 적용 안 하면 두 칸은 기존 값 유지(`COALESCE`가 아니라 UPDATE에서 건드리지 않음 — 신규 행은 NULL).
7. 비서 삭제 시 `voice_engine.delete_reference(persona_id)`(후보 포함).

- [ ] **Step 1: 실패하는 테스트** — `moderate_persona`를 인자를 기록하는 가짜로, `voice_engine`의 `reference_dir`는 `tmp_path`:
  - `test_private_save_promotes_draft` — 후보 저장 → 비공개 + `applyVoiceDraft` → 200, `voiceSampleText == 후보 문장`, `load_reference` 성공, 심사 호출 0회
  - `test_public_approved_promotes_and_publishes` — `isPrivate False`, status `approved`
  - `test_public_rejected_still_promotes_but_private` — 심사 `rejected` → `isPrivate True`, 목소리 승격됨
  - `test_review_uses_draft_texts_not_client` — 기록된 심사 인자의 목소리 텍스트 == 후보 json 값
  - `test_republish_reviews_stored_voice_texts` — 목소리 있는 비공개 비서를 `applyVoiceDraft` 없이 공개 → 심사 인자에 저장된 텍스트
  - `test_apply_without_draft_400`, `test_apply_others_draft_rejected` — 후보 `creator_uid = "other"` → 400
  - `test_public_without_voice_is_voice_required` — `isPrivate True`, status `voice_required`, 심사 호출 0회
  - `test_voice_change_on_public_not_skipped` — 같은 텍스트 + `applyVoiceDraft` 재저장 → 심사 호출 1회
  - `test_delete_removes_voice_files`
  - `test_promote_failure_leaves_voice_unchanged`
- [ ] **Step 2:** FAIL 확인
- [ ] **Step 3:** 구현
- [ ] **Step 4:** `pytest tests/test_persona_voice_apply.py tests/test_personas.py tests/test_persona_reports.py -v` PASS, `pytest -q` 전체 PASS
- [ ] **Step 5:** 커밋 `feat(persona): 저장 시 목소리 후보 적용과 공개 목소리 필수`

### Task 6: 시드와 배포 정리

**Files:**
- Modify: `backend/seed_personas.py`, `backend/seed_dev_personas.py` (두 칸 채우기 — `gen_reference_voices.OFFICIAL_VOICES`, dev는 `PERSONAS`의 instruct·ref_text)
- Modify: `backend/services/voice_service.py` (`cleanup_voiceless_user_personas()`), `backend/main.py` (startup에서 `cleanup_removed_personas()` 다음 호출)
- Test: `backend/tests/test_voice_cleanup.py`, `backend/tests/test_seed_personas.py`

**Interfaces:**
- Consumes: Task 1 `stored_reference_ids`·`delete_reference`
- Produces: `cleanup_voiceless_user_personas() -> None` — `creator_id <> SYSTEM_CREATOR AND voice_sample_text IS NULL`인 비서에 대해 ① `stored_reference_ids()`와 겹치는 id의 기준 음성 삭제 ② `is_private = FALSE`면 `TRUE`로(`report_locked_at` 그대로). `DATABASE_URL` 없으면 건너뜀.

- [ ] **Step 1: 실패하는 테스트**
  - `test_cleanup_privatizes_voiceless_public_user_persona`
  - `test_cleanup_deletes_uploaded_reference` — 목소리 칸 없는 유저 비서의 기준 음성 파일이 사라짐
  - `test_cleanup_is_idempotent_and_spares_voiced` — 칸 있는 유저 비서·공식 비서는 공개·파일 유지, 두 번 돌려도 같은 결과
  - `test_cleanup_keeps_report_lock`
  - `test_official_seed_has_voice_texts` — 시드 후 공식 3명의 `voice_sample_text`가 `OFFICIAL_VOICES`의 문장
- [ ] **Step 2~4:** FAIL → 구현 → PASS, `pytest -q` 전체 PASS
- [ ] **Step 5:** 커밋 `feat(voice): 목소리 없는 유저 공개 비서 비공개 전환과 옛 업로드 음성 정리`

### Task 7: 앱 데이터 계층

**Files:**
- Modify: `app/src/main/java/com/onlyou/com/domain/model/Models.kt`, `data/local/` 의 `PersonaEntity`·`Database.kt`(version 24), `di/DatabaseModule.kt`(`MIGRATION_23_24`), `data/remote/Dto.kt`, `data/remote/ApiService.kt`, `domain/repository/Repositories.kt`, `data/repository/PersonaRepositoryImpl.kt`, `data/repository/PersonaSaveMapping.kt`, `data/repository/VoiceRepositoryImpl.kt`
- Test: `app/src/test/java/com/onlyou/com/data/repository/PersonaSaveMappingTest.kt`, `PersonaMappingTest.kt`

**Interfaces:**
- Produces:
  - `Persona.voiceDescription: String? = null`, `Persona.voiceSampleText: String? = null`, `val Persona.hasVoice: Boolean get() = voiceSampleText != null`
  - `PersonaEntity` 같은 두 칸(nullable). `MIGRATION_23_24`: `ALTER TABLE personas ADD COLUMN voiceDescription TEXT`, `... voiceSampleText TEXT`
  - `PersonaDto`에 `voiceDescription`, `voiceSampleText`, `applyVoiceDraft: Boolean? = null`
  - `PersonaUpsertResponseDto`에 `voiceDescription`, `voiceSampleText`
  - `ModerationStatus.VOICE_REQUIRED` (`"voice_required"` 매핑)
  - `PersonaSaveResult`에 `voiceDescription: String? = null`, `voiceSampleText: String? = null`
  - `PersonaRepository.upsertPersona(persona: Persona, applyVoiceDraft: Boolean = false): PersonaSaveResult` — 응답이 오면 Room 행의 두 칸을 응답 값으로 갱신. OFFLINE이면 `applyVoiceDraft`는 버려진 셈이므로 두 칸을 저장 전 값으로 둔다.
  - `VoiceDesignRequestDto(description: String, sampleText: String)`, `MiyaApiService.designVoice(@Path("persona_id") personaId, @Body body): Response<ResponseBody>` (`POST voice/design/{persona_id}`)
  - `VoiceRepository.designVoice(personaId: String, description: String, sampleText: String): ByteArray?` — 실패면 null
  - 삭제: `synthesizeVoice`·`saveVoiceReference`(ApiService), `VoiceSynthesizeRequestDto`·`VoiceSaveReferenceRequestDto`, `VoiceRepository.saveReferenceVoice`

- [ ] **Step 1: 실패하는 테스트**
  - `PersonaSaveMappingTest`: ``voice_required는 VOICE_REQUIRED``, ``응답의 목소리 칸이 결과에 실린다``
  - `PersonaMappingTest`: ``엔티티의 목소리 칸이 도메인으로``, ``voiceSampleText가 null이면 hasVoice false``
- [ ] **Step 2:** `./gradlew :app:testDebugUnitTest --tests '*PersonaSaveMappingTest' --tests '*PersonaMappingTest'` → FAIL(컴파일 오류)
- [ ] **Step 3:** 구현
- [ ] **Step 4:** 같은 명령 PASS, `./gradlew :app:testDebugUnitTest` 전체 PASS
- [ ] **Step 5:** 커밋 `feat(app): 비서 목소리 칸·Room v24·후보 만들기 API`

### Task 8: 비서 수정 화면 목소리 칸

**Files:**
- Modify: `app/src/main/java/com/onlyou/com/ui/shop/PersonaEditLogic.kt`, `PersonaEditViewModel.kt`, `PersonaEditScreen.kt`
- Test: `app/src/test/java/com/onlyou/com/ui/shop/PersonaEditLogicTest.kt`

**Interfaces:**
- Consumes: Task 7 `VoiceRepository.designVoice`, `upsertPersona(persona, applyVoiceDraft)`, `Persona.hasVoice`, `isCasualSpeech`(`util/MorningGreeting.kt`)
- Produces (Logic):
  - `VOICE_DESCRIPTION_MAX_LEN = 300`, `VOICE_SAMPLE_MAX_LEN = 60`
  - `fun defaultVoiceSample(persona: Persona): String` — `isCasualSpeech(persona.presetKey, persona.speechStyle)`면 `"일어났어? 오늘도 같이 힘내 보자. 아침은 꼭 챙겨 먹고, 나가기 전에 일정 한 번만 더 봐."`, 아니면 `"좋은 아침이에요. 오늘도 같이 힘내 봐요. 아침은 꼭 챙겨 드시고, 나가기 전에 일정 한 번 봐 주세요."` (둘 다 60자 이하, 숫자·영어 없음)
  - `fun voiceSampleToSend(persona: Persona, typed: String): String` — 공백뿐이면 기본 문장
  - `fun canDesignVoice(description: String, isDesigning: Boolean): Boolean`
  - `fun blocksPublishForVoice(persona: Persona, applyVoiceDraft: Boolean): Boolean` — `!persona.isPrivate && !persona.hasVoice && !applyVoiceDraft`
- Produces (VM, `PersonaEditUiState.Success`에 추가): `voiceDescriptionInput: String`, `voiceSampleInput: String`, `draftAudio: ByteArray?`, `isDesigning: Boolean`, `applyVoiceDraft: Boolean`. 함수 `updateVoiceDescription(String)`, `updateVoiceSample(String)`, `designVoice()`(생성 중이면 무시, 새 후보가 오면 `applyVoiceDraft = false`로 되돌림), `chooseDraftVoice()`, `playDraft()`, `playCurrentVoice()`. 수정 모드 진입 시 입력칸은 저장된 두 칸으로 채운다.
- 화면: 스펙 "화면" 절 순서대로 "역할 (선택)"과 "태그" 사이. 샘플 문장 안내 `"숫자·영어 없이 한글로 써 주세요. 상점에서 이 문장으로 미리듣기가 나가요."`, 공개 차단 문구 `"공개하려면 목소리를 만들어 주세요"`, 생성 실패 토스트 `"목소리를 만들지 못했어요. 잠시 후 다시 해 주세요."`, `VOICE_REQUIRED` 저장 안내 `"목소리가 없어 나만 보기로 저장했어요. 목소리를 만들면 공개할 수 있어요."`. 바이트 재생은 `VoicePreviewPlayer`에 바이트 재생을 더하든 따로 두든 구현자 판단.

- [ ] **Step 1: 실패하는 테스트** (`PersonaEditLogicTest`)
  - ``반말 말투면 기본 샘플은 반말 문장``, ``존댓말이면 존댓말 문장``
  - ``기본 샘플 문장은 상한 이하이고 숫자·영어가 없다`` — `Regex("[0-9A-Za-z]")` 미포함, 길이 ≤ `VOICE_SAMPLE_MAX_LEN`
  - ``샘플 칸이 공백뿐이면 기본 문장을 보낸다``
  - ``설명이 비었거나 생성 중이면 만들기 불가``
  - ``공개인데 목소리도 정한 후보도 없으면 저장 차단``, ``비공개면 목소리 없어도 저장 가능``, ``후보를 정했으면 공개 저장 가능``
  - ``VOICE_REQUIRED 저장 안내 문구`` (기존 `saveNotice` 테스트 방식)
  - ``designVoice 중복 호출은 무시`` — 가짜 `VoiceRepository`로 호출 1회 확인(기존 VM 테스트가 없으면 이 항목은 로직 함수 `canDesignVoice`로 대체)
- [ ] **Step 2:** `./gradlew :app:testDebugUnitTest --tests '*PersonaEditLogicTest'` → FAIL
- [ ] **Step 3:** 구현
- [ ] **Step 4:** 같은 명령 PASS, `./gradlew :app:testDebugUnitTest` 전체 PASS, `./gradlew :app:assembleDebug` 성공
- [ ] **Step 5:** 커밋 `feat(app): 비서 수정 화면에서 목소리 만들기·다시 만들기·정하기`

### Task 9: 끝까지 확인

- [ ] **Step 1:** `cd backend && pytest -q` 전체 PASS (출력 끝 줄 기록)
- [ ] **Step 2:** `./gradlew :app:testDebugUnitTest :app:assembleDebug` 성공
- [ ] **Step 3:** 로컬 서버(Gemini API — 로컬 LLM·더미 키 금지, 원래 체크아웃의 `backend/.env` 복사) + 로컬 TTS(`tts-server/local_app.py`)로 실제 흐름: 새 비서 → 설명·샘플 입력 → 만들기 → 다시 만들기(이전 후보 사라짐) → 정하기 → 공개 저장 → 상점 미리듣기가 그 목소리 → 알람 미리 생성이 그 목소리로 클로닝. 기기·에뮬레이터가 없으면 이 단계는 `curl`로 서버 API만 확인하고 앱 실기 확인은 못 했다고 보고한다.
- [ ] **Step 4:** 샘플 문장 60자에서 실제 생성 길이가 8~13초 안에 드는지 들어보고, 벗어나면 `VOICE_SAMPLE_MAX_LEN`을 서버·앱 함께 조정
