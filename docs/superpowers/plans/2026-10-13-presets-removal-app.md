# 성격 프리셋 폐지 — 앱 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 비서 수정 화면을 프리셋 선택에서 말투·성격·역할 3칸 입력으로 바꾸고, 공개 심사 결과 안내·신고·잠금 표시를 붙인다.

**Architecture:** Room v23에 세 칸·잠금·공식 여부 컬럼을 더하고 DTO·도메인 매핑을 넓힌다. 저장 결과는 서버 응답(`moderation`)을 `PersonaSaveResult`로 바꿔 화면에 돌려준다. 판단 로직(저장 가능 여부·안내 문구·신고 가능 여부)은 순수 함수로 분리해 JVM 테스트로 검증한다.

**Tech Stack:** Kotlin · Jetpack Compose · Room · Retrofit/Gson · Hilt · JUnit4

**Spec:** [docs/superpowers/specs/2026-10-13-presets-removal-design.md](../specs/2026-10-13-presets-removal-design.md)

**서버 짝 계획:** [2026-10-13-presets-removal-server.md](2026-10-13-presets-removal-server.md) — 이 앱 계획은 서버 계획의 API(`PUT /personas` 응답의 `moderation`, `POST /personas/{id}/report`, `GET /personas`의 `speechStyle`·`personality`·`role`·`reportLocked`·`isOfficial`)를 전제한다.

## Global Constraints

- 세 칸: 말투(필수)·성격(필수)·역할(선택), 칸당 **300자**.
- 저장 버튼 활성 조건: 이름·말투·성격이 공백이 아님.
- 안내 문구(그대로 사용):
  - `approved` → `상점에 공개됐어요`
  - `rejected` → `상점에 공개할 수 없는 내용이 있어요: {사유}. 나만 보기로 저장했어요.`
  - `unavailable` → `지금은 공개할 수 없어요. 잠시 후 다시 시도해 주세요. (나만 보기로 저장했어요)`
  - `locked` → `신고가 누적되어 공개할 수 없는 비서예요`
- 신고 버튼: 남이 만든 **공개** 비서에만. 공식 비서(`isOfficial`)·내 비서에는 없음.
- 신고 사유 선택지: `부적절한 내용` / `실존 인물 사칭` / `기타`(직접 입력, 200자).
- 잠긴 비서: 공개 토글 비활성 + 한 줄 이유, 내 비서 목록에 `공개 불가` 배지.
- `presetKey` 필드와 Room 컬럼은 이번에 **지우지 않는다**(서버와 같은 이유).
- 앱 단위 테스트: `JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew testDebugUnitTest`
- Room 마이그레이션 테스트는 에뮬레이터/기기에서: `JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew connectedDebugAndroidTest --tests '*PersonaFieldsMigrationTest*'`. 기기가 없으면 실행하지 못한 사실을 리포트에 적는다.
- 워크트리에 `local.properties`, `app/google-services.json`이 없으면 원래 체크아웃(`/Users/yerin/AndroidStudioProjects/onlyou-ai-alarm`)에서 복사한다(gitignore 대상).
- 모든 커밋 메시지는 실행 세션이 지정한 attribution 줄로 끝낸다 (작성 시점 값: `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`).

## Review Focus

- **서버가 공개 요청을 비공개로 바꿔 저장** — 로컬 `isPrivate`가 `false`로 남으면 유저는 공개됐다고 믿는다. 서버 응답의 `isPrivate`로 로컬을 덮어써야 한다. (Task 2 테스트)
- **오프라인 저장** — 서버에 닿지 못하면 심사 안내 대신 조용히 로컬 저장만 하고, 다음 동기화에서 서버 상태를 받는다. 오류 다이얼로그로 유저를 막지 않는다. (Task 2 테스트)
- **서버가 모르는 `moderation.status`나 필드 누락(Gson null)** — 크래시 없이 안내 없음으로 처리. (Task 2 테스트)
- **구버전 형식으로 내려온 비서(세 칸이 빈 문자열)** — 수정 화면을 열면 저장 버튼이 비활성이라 유저가 막힌다. 서버가 `GET /personas`에서 항상 세 칸을 채워 보내므로(서버 Task 5) 앱은 받은 값을 그대로 쓰면 된다 — 매핑에서 null을 빈 문자열로만 바꾼다. (Task 1 테스트)
- **공백만 입력한 필수 칸** — 저장 버튼이 켜지면 서버가 400을 돌려준다. `isBlank`로 판정한다. (Task 3 테스트)

## File Structure

- `app/src/main/java/com/onlyou/com/data/local/Entities.kt` — `PersonaEntity` 컬럼 5개
- `app/src/main/java/com/onlyou/com/data/local/Database.kt` — `version = 23`
- `app/src/main/java/com/onlyou/com/di/DatabaseModule.kt` — `MIGRATION_22_23`
- `app/src/main/java/com/onlyou/com/domain/model/Models.kt` — `Persona` 필드, `ModerationStatus`, `PersonaSaveResult`
- `app/src/main/java/com/onlyou/com/data/remote/Dto.kt` — `PersonaDto` 필드, `PersonaUpsertResponseDto`, `ModerationDto`, `PersonaReportDto`; `PresetDto` 삭제
- `app/src/main/java/com/onlyou/com/data/remote/ApiService.kt` — `upsertPersona` 응답 타입, `reportPersona`; `getPresets` 삭제
- `app/src/main/java/com/onlyou/com/data/repository/PersonaRepositoryImpl.kt` — 매핑, 저장 결과, 신고
- `app/src/main/java/com/onlyou/com/data/repository/PersonaSaveMapping.kt` (신규) — 응답 → `PersonaSaveResult` 순수 함수
- `app/src/main/java/com/onlyou/com/domain/repository/Repositories.kt` — `upsertPersona` 반환형, `reportPersona`
- `app/src/main/java/com/onlyou/com/ui/shop/PersonaEditLogic.kt` (신규) — 저장 가능 여부, 안내 문구
- `app/src/main/java/com/onlyou/com/ui/shop/PersonaEditScreen.kt`, `PersonaEditViewModel.kt` — 세 칸 UI, 저장 결과 다이얼로그
- `app/src/main/java/com/onlyou/com/ui/shop/ShopLogic.kt` — `canReport`
- `app/src/main/java/com/onlyou/com/ui/shop/ShopScreen.kt`, `ShopViewModel.kt` — 상세 세 칸·신고
- `app/src/main/java/com/onlyou/com/ui/shop/MyPersonasScreen.kt` — 잠금 배지
- 삭제: `app/src/main/java/com/onlyou/com/ui/shop/PresetPicker.kt`
- 테스트: `PersonaMappingTest.kt`, `PersonaSaveMappingTest.kt`(신규), `PersonaEditLogicTest.kt`(신규), `ShopLogicTest.kt`, androidTest `PersonaFieldsMigrationTest.kt`(신규)

---

## Task 1: 데이터 계층 — Room v23, DTO, 도메인 매핑

**Files:**
- Modify: `Entities.kt`, `Database.kt`, `DatabaseModule.kt`, `Models.kt`, `Dto.kt`, `PersonaRepositoryImpl.kt`(매핑 함수만)
- Test: `app/src/test/java/com/onlyou/com/data/repository/PersonaMappingTest.kt`, `app/src/androidTest/java/com/onlyou/com/data/local/PersonaFieldsMigrationTest.kt` (신규)

**Interfaces:**
- Consumes: 없음
- Produces:
  - `Persona.speechStyle: String = ""`, `Persona.personality: String = ""`, `Persona.role: String = ""`, `Persona.reportLocked: Boolean = false`, `Persona.isOfficial: Boolean = false`
  - `PersonaEntity` 같은 이름의 컬럼 5개, `MIGRATION_22_23`
  - `PersonaDto.speechStyle/personality/role: String?`, `PersonaDto.reportLocked: Boolean?`, `PersonaDto.isOfficial: Boolean?`

- [ ] **Step 1: 실패하는 테스트 작성**

`PersonaMappingTest.kt`에 추가:

```kotlin
    @Test
    fun `세 칸과 잠금·공식 여부가 도메인으로 전달되고 왕복 후에도 보존된다`() {
        val e = entity("casual_warm").copy(
            speechStyle = "반말", personality = "툴툴댐", role = "룸메이트",
            reportLocked = true, isOfficial = false,
        )
        val d = e.toDomain()
        assertEquals("반말", d.speechStyle)
        assertEquals("툴툴댐", d.personality)
        assertEquals("룸메이트", d.role)
        assertEquals(true, d.reportLocked)
        assertEquals(e.speechStyle, d.toEntity().speechStyle)
        assertEquals(true, d.toEntity().reportLocked)
    }

    @Test
    fun `동기화 매핑은 서버의 세 칸과 플래그를 쓰고 null은 기본값으로 바꾼다`() {
        val full = com.onlyou.com.data.remote.PersonaDto(
            id = "p1", name = "미야", speechStyle = "반말", personality = "다정", role = "",
            reportLocked = true, isOfficial = true,
        ).toEntity(isSelected = false)
        assertEquals("반말", full.speechStyle)
        assertEquals("다정", full.personality)
        assertEquals(true, full.reportLocked)
        assertEquals(true, full.isOfficial)

        val empty = com.onlyou.com.data.remote.PersonaDto(id = "p2", name = "x").toEntity(isSelected = false)
        assertEquals("", empty.speechStyle)
        assertEquals("", empty.personality)
        assertEquals("", empty.role)
        assertEquals(false, empty.reportLocked)
        assertEquals(false, empty.isOfficial)
    }
```

`app/src/androidTest/java/com/onlyou/com/data/local/PersonaFieldsMigrationTest.kt` (신규):

```kotlin
package com.onlyou.com.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.onlyou.com.di.MIGRATION_22_23
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PersonaFieldsMigrationTest {
    private val testDbName = "persona-fields-migration-test"

    @get:Rule
    val helper: MigrationTestHelper =
        MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            MiyaDatabase::class.java.canonicalName!!,
            FrameworkSQLiteOpenHelperFactory(),
        )

    @Test
    fun migrate22To23_addsEmptyFieldsAndPreservesPersona() {
        var db = helper.createDatabase(testDbName, 22)
        db.execSQL(
            """
            INSERT INTO personas (id, name, prompt, description, voiceTone, voiceSpeed, voicePrompt, userCallSign, isSelected, imageUrl, primaryHex, secondaryHex, creatorId, usageCount, isPrivate, presetKey, tags)
            VALUES ('p1', '미야', '', '설명', 1.0, 1.0, '', '주인님', 1, NULL, NULL, NULL, 'uid-1', 3, 0, 'casual_warm', '["아침"]')
            """.trimIndent(),
        )
        db.close()

        db = helper.runMigrationsAndValidate(testDbName, 23, true, MIGRATION_22_23)

        val c = db.query(
            "SELECT name, presetKey, tags, speechStyle, personality, role, reportLocked, isOfficial FROM personas WHERE id = 'p1'",
        )
        assertTrue(c.moveToFirst())
        assertEquals("미야", c.getString(0))
        assertEquals("casual_warm", c.getString(1))
        assertEquals("[\"아침\"]", c.getString(2))
        assertEquals("", c.getString(3))
        assertEquals("", c.getString(4))
        assertEquals("", c.getString(5))
        assertEquals(0, c.getInt(6))
        assertEquals(0, c.getInt(7))
        c.close()
    }
}
```

- [ ] **Step 2: 실패 확인**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew testDebugUnitTest --tests '*PersonaMappingTest*'`
Expected: FAIL — 컴파일 오류 `No parameter with name 'speechStyle' found`

- [ ] **Step 3: 엔티티·DB 버전·마이그레이션**

`Entities.kt`의 `PersonaEntity` 끝(`val tags: String = "[]",` 아래)에 추가:

```kotlin
    // 성격 세 칸 — v23에서 추가 (스펙: 2026-10-13-presets-removal-design.md)
    val speechStyle: String = "",
    val personality: String = "",
    val role: String = "",
    // 신고 누적으로 잠김(공개 불가) / 공식 비서 여부 — 서버가 내려주는 값
    val reportLocked: Boolean = false,
    val isOfficial: Boolean = false,
```

`Database.kt`: `version = 22` → `version = 23`.

`DatabaseModule.kt`의 `MIGRATION_21_22` 아래에 추가하고 `.addMigrations(...)` 목록 끝에 `MIGRATION_22_23`을 넣는다:

```kotlin
// 성격 프리셋 폐지 — 스펙: docs/superpowers/specs/2026-10-13-presets-removal-design.md
val MIGRATION_22_23 = object : Migration(22, 23) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE personas ADD COLUMN speechStyle TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE personas ADD COLUMN personality TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE personas ADD COLUMN role TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE personas ADD COLUMN reportLocked INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE personas ADD COLUMN isOfficial INTEGER NOT NULL DEFAULT 0")
    }
}
```

로컬 세 칸은 비어 있어도 된다 — 다음 동기화에서 서버가 채운 값을 받는다.

- [ ] **Step 4: 도메인·DTO**

`Models.kt`의 `Persona`에서 `presetKey` 주석을 바꾸고 끝에 필드를 추가한다:

```kotlin
    // 구버전 서버 호환용으로만 남은 값. 성격은 아래 세 칸이 정한다(다음 릴리스에서 제거).
    val presetKey: String = "",
```

```kotlin
    // 성격 세 칸 — 서버가 시스템 프롬프트에 그대로 넣는다
    val speechStyle: String = "",
    val personality: String = "",
    val role: String = "",
    val reportLocked: Boolean = false,
    val isOfficial: Boolean = false,
```

`Dto.kt`의 `PersonaDto` 끝(`val imageUrl: String? = null,` 아래)에 추가:

```kotlin
    // 성격 세 칸. 송신 시 신 앱은 항상 채워 보낸다. 수신 시 Gson이 null을 넣을 수 있다.
    val speechStyle: String? = null,
    val personality: String? = null,
    val role: String? = null,
    // 서버 관리 값 — 앱이 보내도 서버가 무시한다
    val reportLocked: Boolean? = null,
    val isOfficial: Boolean? = null,
```

- [ ] **Step 5: 매핑**

`PersonaRepositoryImpl.kt` 하단 매핑 함수 세 개를 수정한다.

`PersonaEntity.toDomain()`의 `imageUrl = imageUrl,` 아래에 추가:

```kotlin
        speechStyle = speechStyle,
        personality = personality,
        role = role,
        reportLocked = reportLocked,
        isOfficial = isOfficial,
```

`Persona.toEntity()`의 `tags = gson.toJson(tags),` 아래에 추가:

```kotlin
        speechStyle = speechStyle,
        personality = personality,
        role = role,
        reportLocked = reportLocked,
        isOfficial = isOfficial,
```

`PersonaDto.toEntity(isSelected)`의 `tags = gson.toJson(tags.orEmpty()),` 아래에 추가:

```kotlin
        // Gson이 null을 넣을 수 있으므로 방어적으로 처리한다
        speechStyle = speechStyle ?: "",
        personality = personality ?: "",
        role = role ?: "",
        reportLocked = reportLocked ?: false,
        isOfficial = isOfficial ?: false,
```

- [ ] **Step 6: 테스트·빌드 확인**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew testDebugUnitTest`
Expected: BUILD SUCCESSFUL, 실패 0. `app/schemas/com.onlyou.com.data.local.MiyaDatabase/23.json`이 생성됐는지 확인(`ls app/schemas/com.onlyou.com.data.local.MiyaDatabase/`).

기기가 연결돼 있으면: `JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew connectedDebugAndroidTest --tests '*PersonaFieldsMigrationTest*'` → PASS. 없으면 리포트에 "미실행"으로 적는다.

- [ ] **Step 7: 커밋**

```bash
git add app/src/main/java/com/onlyou/com/data app/src/main/java/com/onlyou/com/di/DatabaseModule.kt app/src/main/java/com/onlyou/com/domain/model/Models.kt app/schemas app/src/test/java/com/onlyou/com/data/repository/PersonaMappingTest.kt app/src/androidTest/java/com/onlyou/com/data/local/PersonaFieldsMigrationTest.kt
git commit -m "$(cat <<'EOF'
feat(app): Room v23 — 비서 성격 세 칸·잠금·공식 여부 컬럼

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
EOF
)"
```

---

## Task 2: 저장 결과 — 서버 `moderation`을 화면까지

**Files:**
- Create: `app/src/main/java/com/onlyou/com/data/repository/PersonaSaveMapping.kt`
- Modify: `Models.kt`, `Dto.kt`, `ApiService.kt`, `Repositories.kt`, `PersonaRepositoryImpl.kt`(`upsertPersona`)
- Test: `app/src/test/java/com/onlyou/com/data/repository/PersonaSaveMappingTest.kt` (신규)

**Interfaces:**
- Consumes: Task 1 `Persona` 세 칸
- Produces:
  - `enum class ModerationStatus { APPROVED, REJECTED, UNAVAILABLE, LOCKED, SKIPPED, OFFLINE }`
  - `data class PersonaSaveResult(val status: ModerationStatus, val reason: String = "", val isPrivate: Boolean? = null)`
  - `PersonaUpsertResponseDto(ok: Boolean?, isPrivate: Boolean?, moderation: ModerationDto?)`, `ModerationDto(status: String?, reason: String?)`
  - `internal fun PersonaUpsertResponseDto?.toSaveResult(): PersonaSaveResult`
  - `PersonaRepository.upsertPersona(persona: Persona): PersonaSaveResult`
  - `MiyaApiService.upsertPersona(...)`: `Response<PersonaUpsertResponseDto>`

- [ ] **Step 1: 실패하는 테스트 작성**

`app/src/test/java/com/onlyou/com/data/repository/PersonaSaveMappingTest.kt` (신규):

```kotlin
package com.onlyou.com.data.repository

import com.onlyou.com.data.remote.ModerationDto
import com.onlyou.com.data.remote.PersonaUpsertResponseDto
import com.onlyou.com.domain.model.ModerationStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class PersonaSaveMappingTest {
    private fun res(status: String?, reason: String? = null, isPrivate: Boolean? = true) =
        PersonaUpsertResponseDto(ok = true, isPrivate = isPrivate, moderation = ModerationDto(status, reason))

    @Test
    fun `서버 상태 문자열을 enum으로 바꾼다`() {
        assertEquals(ModerationStatus.APPROVED, res("approved", isPrivate = false).toSaveResult().status)
        assertEquals(ModerationStatus.REJECTED, res("rejected", "사칭").toSaveResult().status)
        assertEquals(ModerationStatus.UNAVAILABLE, res("unavailable").toSaveResult().status)
        assertEquals(ModerationStatus.LOCKED, res("locked").toSaveResult().status)
        assertEquals(ModerationStatus.SKIPPED, res("skipped").toSaveResult().status)
    }

    @Test
    fun `거부 사유와 서버 isPrivate를 그대로 담는다`() {
        val r = res("rejected", "실존 인물 사칭", isPrivate = true).toSaveResult()
        assertEquals("실존 인물 사칭", r.reason)
        assertEquals(true, r.isPrivate)
    }

    @Test
    fun `모르는 상태나 moderation 누락은 SKIPPED로 처리한다`() {
        assertEquals(ModerationStatus.SKIPPED, res("brand_new").toSaveResult().status)
        assertEquals(ModerationStatus.SKIPPED, res(null).toSaveResult().status)
        assertEquals(
            ModerationStatus.SKIPPED,
            PersonaUpsertResponseDto(ok = true, isPrivate = false, moderation = null).toSaveResult().status,
        )
    }

    @Test
    fun `응답 본문이 없으면 OFFLINE`() {
        val r = (null as PersonaUpsertResponseDto?).toSaveResult()
        assertEquals(ModerationStatus.OFFLINE, r.status)
        assertEquals(null, r.isPrivate)
    }
}
```

- [ ] **Step 2: 실패 확인**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew testDebugUnitTest --tests '*PersonaSaveMappingTest*'`
Expected: FAIL — 컴파일 오류 `Unresolved reference: PersonaUpsertResponseDto`

- [ ] **Step 3: 도메인 타입**

`Models.kt`의 `Persona` 아래에 추가:

```kotlin
/** 비서 저장 시 서버 공개 심사 결과. OFFLINE은 서버에 닿지 못해 로컬에만 저장된 경우. */
enum class ModerationStatus { APPROVED, REJECTED, UNAVAILABLE, LOCKED, SKIPPED, OFFLINE }

/** isPrivate는 서버가 최종 결정한 공개 여부 — 공개 요청이 비공개로 바뀌었을 수 있다. */
data class PersonaSaveResult(
    val status: ModerationStatus,
    val reason: String = "",
    val isPrivate: Boolean? = null,
)
```

- [ ] **Step 4: DTO·API**

`Dto.kt`의 `PersonaDto` 아래에 추가:

```kotlin
// PUT /personas/{id} 응답 — 서버 공개 심사 결과를 담는다
data class ModerationDto(
    val status: String? = null,
    val reason: String? = null,
)

data class PersonaUpsertResponseDto(
    val ok: Boolean? = null,
    val isPrivate: Boolean? = null,
    val moderation: ModerationDto? = null,
)
```

`ApiService.kt`의 `upsertPersona` 반환형을 바꾼다:

```kotlin
    suspend fun upsertPersona(
        @retrofit2.http.Path("id") id: String,
        @Body body: PersonaDto,
    ): Response<com.onlyou.com.data.remote.PersonaUpsertResponseDto>
```

- [ ] **Step 5: 매핑 함수**

`app/src/main/java/com/onlyou/com/data/repository/PersonaSaveMapping.kt` (신규):

```kotlin
package com.onlyou.com.data.repository

import com.onlyou.com.data.remote.PersonaUpsertResponseDto
import com.onlyou.com.domain.model.ModerationStatus
import com.onlyou.com.domain.model.PersonaSaveResult

/** 응답 본문이 없으면(네트워크 실패·비정상 응답) OFFLINE — 로컬 저장만 된 상태. */
internal fun PersonaUpsertResponseDto?.toSaveResult(): PersonaSaveResult {
    if (this == null) return PersonaSaveResult(ModerationStatus.OFFLINE)
    val status = when (moderation?.status) {
        "approved" -> ModerationStatus.APPROVED
        "rejected" -> ModerationStatus.REJECTED
        "unavailable" -> ModerationStatus.UNAVAILABLE
        "locked" -> ModerationStatus.LOCKED
        // skipped와 모르는 값은 안내 없이 넘어간다
        else -> ModerationStatus.SKIPPED
    }
    return PersonaSaveResult(status = status, reason = moderation?.reason.orEmpty(), isPrivate = isPrivate)
}
```

- [ ] **Step 6: 리포지토리**

`Repositories.kt`: `suspend fun upsertPersona(persona: Persona)` → `suspend fun upsertPersona(persona: Persona): com.onlyou.com.domain.model.PersonaSaveResult`.

`PersonaRepositoryImpl.upsertPersona`의 `// 2. 원격 저장` 이후를 교체한다:

```kotlin
            // 2. 원격 저장 — 서버가 공개 요청을 비공개로 바꿨을 수 있으므로 응답으로 로컬을 맞춘다
            val result = try {
                api.upsertPersona(
                    updatedPersona.id,
                    com.onlyou.com.data.remote.PersonaDto(
                        id = updatedPersona.id,
                        name = updatedPersona.name,
                        description = updatedPersona.description,
                        userCallSign = updatedPersona.userCallSign,
                        primaryHex = updatedPersona.themeColors?.primaryHex,
                        secondaryHex = updatedPersona.themeColors?.secondaryHex,
                        creatorId = updatedPersona.creatorId,
                        usageCount = updatedPersona.usageCount,
                        isPrivate = updatedPersona.isPrivate,
                        updatedAt = System.currentTimeMillis(),
                        tags = updatedPersona.tags,
                        speechStyle = updatedPersona.speechStyle,
                        personality = updatedPersona.personality,
                        role = updatedPersona.role,
                    ),
                ).body().toSaveResult()
            } catch (e: Exception) {
                e.printStackTrace()
                com.onlyou.com.domain.model.PersonaSaveResult(com.onlyou.com.domain.model.ModerationStatus.OFFLINE)
            }
            val serverPrivate = result.isPrivate
            if (serverPrivate != null && serverPrivate != entity.isPrivate) {
                personaDao.upsertPersona(entity.copy(isPrivate = serverPrivate))
            }
            return result
        }
```

(`presetKey`는 더 이상 보내지 않는다 — 서버는 세 칸이 오면 신 앱 경로로 처리한다.)

- [ ] **Step 7: 호출부 컴파일 맞추기**

`PersonaEditViewModel.savePersona()`는 Task 3에서 결과를 쓰도록 바꾼다. 이 태스크에서는 반환값을 무시해도 컴파일된다(`personaRepository.upsertPersona(...)` 그대로).

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew testDebugUnitTest`
Expected: BUILD SUCCESSFUL, 실패 0

- [ ] **Step 8: 커밋**

```bash
git add app/src/main/java/com/onlyou/com app/src/test/java/com/onlyou/com/data/repository/PersonaSaveMappingTest.kt
git commit -m "$(cat <<'EOF'
feat(app): 비서 저장이 서버 공개 심사 결과를 돌려주고 로컬 공개 여부를 맞춘다

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
EOF
)"
```

---

## Task 3: 비서 수정 화면 — 세 칸 입력과 심사 결과 안내

**Files:**
- Create: `app/src/main/java/com/onlyou/com/ui/shop/PersonaEditLogic.kt`
- Modify: `PersonaEditScreen.kt`, `PersonaEditViewModel.kt`
- Delete: `app/src/main/java/com/onlyou/com/ui/shop/PresetPicker.kt`; `Dto.kt`의 `PresetDto`; `ApiService.kt`의 `getPresets`
- Test: `app/src/test/java/com/onlyou/com/ui/shop/PersonaEditLogicTest.kt` (신규)

**Interfaces:**
- Consumes: Task 1 `Persona` 세 칸·`reportLocked`; Task 2 `PersonaSaveResult`, `ModerationStatus`
- Produces:
  - `const val PERSONA_FIELD_MAX_LEN = 300`
  - `fun isPersonaSavable(p: Persona): Boolean`
  - `fun saveNotice(result: PersonaSaveResult): String?`
  - `PersonaEditUiState.Saved(val notice: String?)` (기존 `object Saved` 대체)

- [ ] **Step 1: 실패하는 테스트 작성**

`app/src/test/java/com/onlyou/com/ui/shop/PersonaEditLogicTest.kt` (신규):

```kotlin
package com.onlyou.com.ui.shop

import com.onlyou.com.domain.model.ModerationStatus
import com.onlyou.com.domain.model.Persona
import com.onlyou.com.domain.model.PersonaSaveResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PersonaEditLogicTest {
    private fun p(name: String = "미야", speech: String = "반말", personality: String = "다정", role: String = "") =
        Persona(id = "p1", name = name, description = "", speechStyle = speech, personality = personality, role = role)

    @Test
    fun `이름·말투·성격이 있으면 저장 가능, 역할은 선택`() {
        assertTrue(isPersonaSavable(p()))
        assertTrue(isPersonaSavable(p(role = "룸메이트")))
    }

    @Test
    fun `필수 칸이 비었거나 공백뿐이면 저장 불가`() {
        assertFalse(isPersonaSavable(p(name = " ")))
        assertFalse(isPersonaSavable(p(speech = "")))
        assertFalse(isPersonaSavable(p(personality = "   ")))
    }

    @Test
    fun `칸 길이 상한을 넘으면 저장 불가`() {
        assertFalse(isPersonaSavable(p(personality = "가".repeat(PERSONA_FIELD_MAX_LEN + 1))))
        assertTrue(isPersonaSavable(p(personality = "가".repeat(PERSONA_FIELD_MAX_LEN))))
    }

    @Test
    fun `심사 결과별 안내 문구`() {
        assertEquals("상점에 공개됐어요", saveNotice(PersonaSaveResult(ModerationStatus.APPROVED)))
        assertEquals(
            "상점에 공개할 수 없는 내용이 있어요: 실존 인물 사칭. 나만 보기로 저장했어요.",
            saveNotice(PersonaSaveResult(ModerationStatus.REJECTED, "실존 인물 사칭")),
        )
        assertEquals(
            "지금은 공개할 수 없어요. 잠시 후 다시 시도해 주세요. (나만 보기로 저장했어요)",
            saveNotice(PersonaSaveResult(ModerationStatus.UNAVAILABLE)),
        )
        assertEquals("신고가 누적되어 공개할 수 없는 비서예요", saveNotice(PersonaSaveResult(ModerationStatus.LOCKED)))
    }

    @Test
    fun `비공개 저장과 오프라인 저장은 안내 없음`() {
        assertNull(saveNotice(PersonaSaveResult(ModerationStatus.SKIPPED)))
        assertNull(saveNotice(PersonaSaveResult(ModerationStatus.OFFLINE)))
    }
}
```

- [ ] **Step 2: 실패 확인**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew testDebugUnitTest --tests '*PersonaEditLogicTest*'`
Expected: FAIL — `Unresolved reference: isPersonaSavable`

- [ ] **Step 3: `PersonaEditLogic.kt` 작성**

```kotlin
package com.onlyou.com.ui.shop

import com.onlyou.com.domain.model.ModerationStatus
import com.onlyou.com.domain.model.Persona
import com.onlyou.com.domain.model.PersonaSaveResult

// 서버 core/persona_fields.MAX_FIELD_LEN과 같은 값
const val PERSONA_FIELD_MAX_LEN = 300

/** 공백만 있는 필수 칸은 서버가 400으로 거부하므로 isBlank로 판정한다. */
fun isPersonaSavable(p: Persona): Boolean =
    p.name.isNotBlank() &&
        p.speechStyle.isNotBlank() &&
        p.personality.isNotBlank() &&
        listOf(p.speechStyle, p.personality, p.role).all { it.length <= PERSONA_FIELD_MAX_LEN }

/** 저장 후 보여줄 안내. null이면 안내 없이 화면을 닫는다. */
fun saveNotice(result: PersonaSaveResult): String? =
    when (result.status) {
        ModerationStatus.APPROVED -> "상점에 공개됐어요"
        ModerationStatus.REJECTED -> "상점에 공개할 수 없는 내용이 있어요: ${result.reason}. 나만 보기로 저장했어요."
        ModerationStatus.UNAVAILABLE -> "지금은 공개할 수 없어요. 잠시 후 다시 시도해 주세요. (나만 보기로 저장했어요)"
        ModerationStatus.LOCKED -> "신고가 누적되어 공개할 수 없는 비서예요"
        ModerationStatus.SKIPPED, ModerationStatus.OFFLINE -> null
    }
```

- [ ] **Step 4: ViewModel 수정**

`PersonaEditViewModel.kt`:
- `api: MiyaApiService` 생성자 인자, `_presets`/`presets`, `loadPresets()`와 관련 import(`MiyaApiService`, `PresetDto`)를 삭제한다.
- 신규 생성 모드의 `Persona(...)`에서 `presetKey = "",` 줄을 삭제한다.
- `savePersona()`를 교체:

```kotlin
        fun savePersona() {
            val currentState = _uiState.value
            if (currentState is PersonaEditUiState.Success) {
                viewModelScope.launch {
                    val result = personaRepository.upsertPersona(currentState.persona)
                    _uiState.value = PersonaEditUiState.Saved(saveNotice(result))
                }
            }
        }
```

- `deletePersona()`의 `_uiState.value = PersonaEditUiState.Saved` → `PersonaEditUiState.Saved(null)`.
- `sealed class PersonaEditUiState`의 `object Saved : PersonaEditUiState()`를 교체:

```kotlin
    /** notice가 있으면 안내 다이얼로그를 띄운 뒤 화면을 닫는다. */
    data class Saved(val notice: String?) : PersonaEditUiState()
```

- [ ] **Step 5: 화면 수정**

`PersonaEditScreen.kt`:

1) `val presets by viewModel.presets.collectAsState()`와 `viewModel.loadPresets()` 호출, `PersonaEditContent`의 `presets` 인자(호출부·시그니처 모두), `PresetDto` import를 삭제한다.

2) 저장 후 처리 블록을 교체:

```kotlin
    val saved = uiState as? PersonaEditUiState.Saved
    if (saved != null) {
        if (saved.notice == null) {
            LaunchedEffect(Unit) { onBack() }
        } else {
            AlertDialog(
                onDismissRequest = onBack,
                confirmButton = { TextButton(onClick = onBack) { Text("확인") } },
                text = { Text(saved.notice) },
            )
        }
    }
```

(`androidx.compose.material3.AlertDialog`, `TextButton` import 추가.)

3) 저장 버튼 활성 조건을 교체:

```kotlin
                    val isSavable = isPersonaSavable(uiState.persona)
```

4) `// 성격 프리셋 섹션` `Column { ... }` 블록 전체를 아래로 교체:

```kotlin
                    // 성격 세 칸 — 서버가 시스템 프롬프트에 그대로 넣는다
                    Column {
                        SectionLabel("말투")
                        CustomCounterTextField(
                            value = persona.speechStyle,
                            onValueChange = { onUpdatePersona(persona.copy(speechStyle = it)) },
                            maxLength = PERSONA_FIELD_MAX_LEN,
                            placeholder = "예) 반말, 짧고 툭툭",
                            minLines = 2,
                        )
                    }
                    Column {
                        SectionLabel("성격")
                        CustomCounterTextField(
                            value = persona.personality,
                            onValueChange = { onUpdatePersona(persona.copy(personality = it)) },
                            maxLength = PERSONA_FIELD_MAX_LEN,
                            placeholder = "예) 겉으로는 무뚝뚝한데 은근히 챙겨줌",
                            minLines = 3,
                        )
                    }
                    Column {
                        SectionLabel("역할 (선택)")
                        CustomCounterTextField(
                            value = persona.role,
                            onValueChange = { onUpdatePersona(persona.copy(role = it)) },
                            maxLength = PERSONA_FIELD_MAX_LEN,
                            placeholder = "예) 아침에 깨워주고 일정 챙겨주는 룸메이트",
                            minLines = 2,
                        )
                    }
```

5) 공개 여부 `Switch`를 잠금에 맞춘다:

```kotlin
                            Switch(
                                checked = persona.isPrivate || persona.reportLocked,
                                onCheckedChange = { onUpdatePersona(persona.copy(isPrivate = it)) },
                                enabled = !persona.reportLocked,
                                colors = SwitchDefaults.colors(checkedThumbColor = brandPurple, checkedTrackColor = Color(0xFFEBE0FF))
                            )
```

같은 `Column(modifier = Modifier.weight(1f))` 안, 설명 `Text("상점에 이 비서가 표시되지 않으며 ...")` 아래에 추가:

```kotlin
                                if (persona.reportLocked) {
                                    Spacer(Modifier.height(4.dp))
                                    Text("신고가 누적되어 공개할 수 없는 비서예요", fontSize = 11.sp, color = Color.Red)
                                }
```

- [ ] **Step 6: 프리셋 UI 삭제**

```bash
git rm app/src/main/java/com/onlyou/com/ui/shop/PresetPicker.kt
```

`Dto.kt`의 `// Presets ...` 주석과 `data class PresetDto(...)`를 삭제하고, `ApiService.kt`의 `getPresets()` 선언(위 `@GET("presets")` 줄 포함)을 삭제한다.

Run: `grep -rn "PresetDto\|PresetPicker\|getPresets\|loadPresets" app/src`
Expected: 결과 없음

- [ ] **Step 7: 테스트·빌드 확인**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew testDebugUnitTest`
Expected: BUILD SUCCESSFUL, 실패 0

- [ ] **Step 8: 커밋**

```bash
git add -A app/src/main/java/com/onlyou/com/ui/shop app/src/main/java/com/onlyou/com/data/remote app/src/test/java/com/onlyou/com/ui/shop/PersonaEditLogicTest.kt
git commit -m "$(cat <<'EOF'
feat(app): 비서 수정 화면을 말투·성격·역할 입력으로 바꾸고 공개 심사 결과 안내

프리셋 선택 UI와 /presets 호출을 삭제했다. 잠긴 비서는 공개 토글을 막는다.

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
EOF
)"
```

---

## Task 4: 상점 상세 신고와 잠금 표시

**Files:**
- Modify: `Dto.kt`, `ApiService.kt`, `Repositories.kt`, `PersonaRepositoryImpl.kt`, `ShopLogic.kt`, `ShopViewModel.kt`, `ShopScreen.kt`, `MyPersonasScreen.kt`
- Test: `app/src/test/java/com/onlyou/com/ui/shop/ShopLogicTest.kt`

**Interfaces:**
- Consumes: Task 1 `Persona.isOfficial`·`reportLocked`·세 칸
- Produces:
  - `PersonaReportDto(reason: String)`, `MiyaApiService.reportPersona(id: String, body: PersonaReportDto): Response<Unit>`
  - `PersonaRepository.reportPersona(personaId: String, reason: String): Boolean`
  - `fun canReport(p: Persona, currentUserId: String?): Boolean` (ShopLogic.kt)
  - `ShopUiState.reportedIds: Set<String>`, `ShopViewModel.reportPersona(persona: Persona, reason: String)`

- [ ] **Step 1: 실패하는 테스트 작성**

`ShopLogicTest.kt`의 `p(...)` 헬퍼에 `official: Boolean = false` 인자를 추가하고 `Persona(...)`에 `isOfficial = official`을 넘긴다. 그리고 추가:

```kotlin
    @Test
    fun `신고는 남이 만든 공개 비서에만`() {
        assertEquals(true, canReport(p("a", creator = "other"), "me"))
        assertEquals(false, canReport(p("a", creator = "me"), "me"))
        assertEquals(false, canReport(p("a", creator = "other", private = true), "me"))
        assertEquals(false, canReport(p("a", creator = "other", official = true), "me"))
        assertEquals(false, canReport(p("a", creator = null), "me"))
    }

    @Test
    fun `로그인 정보가 없으면 신고 버튼을 숨긴다`() {
        assertEquals(false, canReport(p("a", creator = "other"), null))
    }
```

- [ ] **Step 2: 실패 확인**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew testDebugUnitTest --tests '*ShopLogicTest*'`
Expected: FAIL — `Unresolved reference: canReport`

- [ ] **Step 3: 순수 함수**

`ShopLogic.kt` 끝에 추가:

```kotlin
/** 신고 버튼 노출 조건 — 서버도 같은 조건(내 것·비공개·공식 비서 불가)으로 400을 낸다. */
fun canReport(p: Persona, currentUserId: String?): Boolean =
    currentUserId != null &&
        p.creatorId != null &&
        p.creatorId != currentUserId &&
        !p.isPrivate &&
        !p.isOfficial
```

- [ ] **Step 4: API·리포지토리**

`Dto.kt`에 추가:

```kotlin
data class PersonaReportDto(
    val reason: String,
)
```

`ApiService.kt`의 `selectPersona` 아래에 추가:

```kotlin
    @POST("personas/{id}/report")
    suspend fun reportPersona(
        @retrofit2.http.Path("id") id: String,
        @Body body: com.onlyou.com.data.remote.PersonaReportDto,
    ): Response<Unit>
```

`Repositories.kt`의 `PersonaRepository`에 추가:

```kotlin
    /** 공개 비서 신고. 서버가 접수(200)하면 true. */
    suspend fun reportPersona(personaId: String, reason: String): Boolean
```

`PersonaRepositoryImpl`에 추가:

```kotlin
        override suspend fun reportPersona(personaId: String, reason: String): Boolean =
            try {
                api.reportPersona(personaId, com.onlyou.com.data.remote.PersonaReportDto(reason.take(200))).isSuccessful
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
```

- [ ] **Step 5: ViewModel**

`ShopUiState`에 필드 추가:

```kotlin
    // 이번 세션에 신고한 비서 — 버튼 비활성화용. 서버가 중복 신고를 무시하므로 영구 저장은 불필요하다.
    val reportedIds: Set<String> = emptySet(),
    val reportMessage: String? = null,
```

`ShopViewModel`에 추가:

```kotlin
        fun reportPersona(persona: Persona, reason: String) {
            viewModelScope.launch {
                val ok = personaRepository.reportPersona(persona.id, reason)
                _uiState.update {
                    if (ok) {
                        it.copy(reportedIds = it.reportedIds + persona.id, reportMessage = "신고가 접수됐어요")
                    } else {
                        it.copy(reportMessage = "신고를 보내지 못했어요. 잠시 후 다시 시도해 주세요.")
                    }
                }
            }
        }

        fun consumeReportMessage() {
            _uiState.update { it.copy(reportMessage = null) }
        }
```

- [ ] **Step 6: 상세 바텀시트**

`ShopScreen.kt`의 `PersonaDetailSheetContent` 시그니처에 인자 추가:

```kotlin
    canReport: Boolean,
    alreadyReported: Boolean,
    onReport: (String) -> Unit,
```

호출부(`ModalBottomSheet` 안)에 전달:

```kotlin
                canReport = canReport(uiState.selectedPersona!!, uiState.currentUserId),
                alreadyReported = uiState.selectedPersona!!.id in uiState.reportedIds,
                onReport = { reason -> viewModel.reportPersona(uiState.selectedPersona!!, reason) },
```

`PersonaDetailSheetContent` 본문에서 태그 `Row` 블록 아래에 세 칸 표시를 추가:

```kotlin
        PersonaFieldsSummary(persona)
```

맨 아래 `Button(...) { Text(if (persona.isSelected) ...) }` 뒤에 신고 진입점을 추가:

```kotlin
        if (canReport) {
            var showReport by remember { mutableStateOf(false) }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = { showReport = true }, enabled = !alreadyReported) {
                Text(if (alreadyReported) "신고했어요" else "신고하기", color = colors.neutral, fontSize = 13.sp)
            }
            if (showReport) {
                ReportDialog(
                    onDismiss = { showReport = false },
                    onSubmit = { reason -> showReport = false; onReport(reason) },
                )
            }
        }
```

같은 파일에 컴포저블 두 개를 추가:

```kotlin
@Composable
private fun PersonaFieldsSummary(persona: Persona) {
    val colors = MiyaTheme.colors
    val rows = listOf("말투" to persona.speechStyle, "성격" to persona.personality, "역할" to persona.role)
        .filter { it.second.isNotBlank() }
    if (rows.isEmpty()) return
    Spacer(Modifier.height(16.dp))
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(colors.surfaceB).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        rows.forEach { (label, value) ->
            Text("$label · $value", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceA)
        }
    }
}

private val REPORT_REASONS = listOf("부적절한 내용", "실존 인물 사칭", "기타")

@Composable
private fun ReportDialog(onDismiss: () -> Unit, onSubmit: (String) -> Unit) {
    var selected by remember { mutableStateOf(REPORT_REASONS.first()) }
    var etc by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("이 비서를 신고할까요?") },
        text = {
            Column {
                REPORT_REASONS.forEach { reason ->
                    Row(
                        Modifier.fillMaxWidth().clickable { selected = reason },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = selected == reason, onClick = { selected = reason })
                        Text(reason)
                    }
                }
                if (selected == "기타") {
                    OutlinedTextField(
                        value = etc,
                        onValueChange = { etc = it.take(200) },
                        placeholder = { Text("사유를 적어주세요") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSubmit(if (selected == "기타") "기타: ${etc.trim()}" else selected) }) {
                Text("신고")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}
```

(필요한 import: `androidx.compose.material3.AlertDialog`, `RadioButton`, `OutlinedTextField`, `TextButton`, `androidx.compose.runtime.getValue/setValue/mutableStateOf/remember`. 이미 있는 것은 중복 추가하지 않는다.)

`ShopScreen` 본문(상단, `uiState` 수집 아래)에 신고 결과 스낵바/토스트를 연결한다. 이 화면이 이미 `SnackbarHostState`를 쓰면 그것을, 아니면 `Toast`를 쓴다:

```kotlin
    val context = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(uiState.reportMessage) {
        uiState.reportMessage?.let {
            android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_SHORT).show()
            viewModel.consumeReportMessage()
        }
    }
```

- [ ] **Step 7: 내 비서 목록 잠금 배지**

`MyPersonasScreen.kt`의 카드에서 `Text(text = persona.name, ...)`를 `Row`로 감싸 배지를 붙인다:

```kotlin
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = persona.name,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MiyaTheme.colors.onSurfaceA
                                    )
                                    if (persona.reportLocked) {
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            "공개 불가",
                                            fontSize = 11.sp,
                                            color = Color.Red,
                                            modifier = Modifier
                                                .border(1.dp, Color.Red, RoundedCornerShape(6.dp))
                                                .padding(horizontal = 6.dp, vertical = 2.dp),
                                        )
                                    }
                                }
```

(필요한 import: `androidx.compose.foundation.border`, `androidx.compose.foundation.shape.RoundedCornerShape`, `androidx.compose.foundation.layout.Spacer/width/padding` — 이미 있는 것은 생략.)

- [ ] **Step 8: 테스트·빌드 확인**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew testDebugUnitTest`
Expected: BUILD SUCCESSFUL, 실패 0

- [ ] **Step 9: 커밋**

```bash
git add app/src/main/java/com/onlyou/com app/src/test/java/com/onlyou/com/ui/shop/ShopLogicTest.kt
git commit -m "$(cat <<'EOF'
feat(app): 상점 상세에 성격 세 칸 표시와 신고, 내 비서 잠금 배지

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
EOF
)"
```

---

## Task 5: 전체 검증과 실기 확인

**Files:**
- 없음(검증 태스크). 문제가 발견되면 해당 태스크 파일을 고치고 그 태스크 이름으로 커밋한다.

**Interfaces:**
- Consumes: Task 1~4, 서버 계획 전체(로컬 서버)

- [ ] **Step 1: 단위 테스트 전체**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew testDebugUnitTest`
Expected: BUILD SUCCESSFUL, 실패 0. 결과 수를 리포트에 적는다(`find app/build/test-results/testDebugUnitTest -name '*.xml' | xargs grep -ho 'tests="[0-9]*"'`).

- [ ] **Step 2: 디버그 빌드**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 잔여 참조 확인**

Run: `grep -rn "presetKey" app/src/main/java | grep -v "Entities.kt\|Models.kt\|Dto.kt\|PersonaRepositoryImpl.kt"`
Expected: 결과 없음 — `presetKey`는 저장 계층의 호환 필드로만 남는다.

- [ ] **Step 4: 마이그레이션 테스트 (기기 있을 때)**

Run: `adb devices` — 기기/에뮬레이터가 있으면 `JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew connectedDebugAndroidTest --tests '*PersonaFieldsMigrationTest*'` → PASS. 없으면 리포트에 "기기 없음 — 미실행"으로 적는다.

- [ ] **Step 5: 실기 확인 (기기 있을 때)**

로컬 서버(서버 계획 적용, `DEV_TRUST_TOKENS=1`)에 붙여 다음을 확인하고 결과를 리포트에 적는다. 기기가 없으면 생략하고 그 사실을 적는다.
- 새 비서: 말투·성격을 채우기 전 저장 버튼 비활성 → 채우면 활성
- 공개로 저장 → 실제 심사 결과에 맞는 안내(로컬에서 Gemini 키가 더미면 `unavailable` 안내)
- 비공개로 저장 → 안내 없이 닫힘
- 남의 공개 비서 상세 → 세 칸 표시, 신고 → "신고가 접수됐어요", 버튼 "신고했어요"로 비활성
- 공식 비서 상세 → 신고 버튼 없음
