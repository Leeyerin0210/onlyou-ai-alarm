# Server Calendar Sync Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make AI-extracted schedules persist to a per-user Firestore collection and sync across devices, closing the gap where `AiScheduleEntity` only lives in local Room.

**Architecture:** All Firestore sync logic lives inside `ScheduleRepositoryImpl` (Android). `insertSchedule`/`updateSchedule`/`deleteSchedule` write to Room first (synchronous, source of truth for the UI), then fire-and-forget push to `users/{uid}/schedules/{id}` in Firestore. A new `syncSchedules()` method pulls remote data on app start / schedule tab entry, mirroring the existing `PersonaRepositoryImpl.syncPersonas()` pattern. No backend (FastAPI) or `ChatRepositoryImpl`/`ScheduleViewModel` call-site changes are needed — everything funnels through the existing `ScheduleRepository` interface.

**Tech Stack:** Kotlin, Jetpack Compose, Room 2.6.1, Hilt, Firebase Firestore/Auth (already in the project), JUnit4 (no new mocking library — see Global Constraints).

## Global Constraints

- Firestore path is `users/{uid}/schedules/{scheduleId}` (subcollection, not a top-level collection) — from spec §3.
- Login is mandatory before any schedule screen is reachable (`MainActivity.kt` gates on `currentUser`), so `auth.currentUser?.uid` is always non-null in practice when these methods run; code must still null-check defensively rather than assume.
- No WorkManager, no new mocking library (mockk, etc.) — spec explicitly rejected WorkManager as over-engineering, and this codebase has zero Firebase-mocking precedent. Firestore's built-in offline write queue is relied upon instead (spec §4.1).
- `pendingSync` is a **local-only** Room bookkeeping field — never written to the Firestore document itself (spec §3 table).
- Reminder-related fields (`reminderOffsetMinutes`, etc.) are explicitly OUT of scope for this plan (spec §5, scope-keeping decision).
- Delete-sync and account-switch cache clearing are explicitly OUT of scope (spec §5 "알려진 한계") — do not implement them.
- Existing Room migration convention: explicit `Migration` objects using raw `ALTER TABLE ... ADD COLUMN` (see `MIGRATION_13_14`, `MIGRATION_14_15` in `DatabaseModule.kt`) registered via `.addMigrations(...)`, with `.fallbackToDestructiveMigration()` kept as a catch-all for unregistered hops. Follow this exact style.
- Current Room DB version is 17 (`MiyaDatabase`, `Entities.kt`). This plan bumps it to 18.

## File Structure

- `gradle/libs.versions.toml` — add `androidx-room-testing` library entry (reuses existing `room` version).
- `app/build.gradle.kts` — add `androidTestImplementation(libs.androidx.room.testing)`; add `ksp { arg("room.schemaLocation", ...) }` block to enable schema export.
- `app/src/main/java/com/onlyou/com/data/local/Entities.kt` — add `updatedAt: Long` and `pendingSync: Boolean` to `AiScheduleEntity`.
- `app/src/main/java/com/onlyou/com/data/local/Database.kt` — add two `AiScheduleDao` queries (`updatePendingSync`, `getPendingSchedulesOnce`); bump `@Database(version = 18, exportSchema = true)`.
- `app/src/main/java/com/onlyou/com/di/DatabaseModule.kt` — add `MIGRATION_17_18`, register it.
- `app/src/androidTest/java/com/onlyou/com/data/local/ScheduleMigrationTest.kt` — new, `MigrationTestHelper`-based test for `MIGRATION_17_18`.
- `app/src/main/java/com/onlyou/com/data/repository/ScheduleRepositoryImpl.kt` — add top-level pure functions (`aiScheduleEntityToFirestoreMap`, `mapToScheduleEntity`, `isRemoteNewer`); inject `FirebaseFirestore`/`FirebaseAuth`; wire push into insert/update/delete; add `syncSchedules()`.
- `app/src/test/java/com/onlyou/com/data/repository/ScheduleSyncMappingTest.kt` — new, JUnit tests for the pure functions above.
- `app/src/main/java/com/onlyou/com/domain/repository/Repositories.kt` — add `suspend fun syncSchedules()` to `ScheduleRepository` interface.
- `app/src/main/java/com/onlyou/com/ui/schedule/ScheduleViewModel.kt` — call `repository.syncSchedules()` in `init`.

---

### Task 1: Enable Room schema export and capture the v17 baseline

Room's `MigrationTestHelper` (used in Task 2) needs a JSON snapshot of the *pre-migration* schema to construct an old-version database. This project currently has `exportSchema = false`, so no snapshot has ever existed. This task turns on export and generates `17.json` before any entity changes are made, so the snapshot accurately reflects the current (unmigrated) schema.

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`
- Modify: `app/src/main/java/com/onlyou/com/data/local/Database.kt:139` (`exportSchema = false` → `true`, keep `version = 17` for this task only)

**Interfaces:**
- Produces: `app/schemas/com.onlyou.com.data.local.MiyaDatabase/17.json` on disk, consumed by Task 2's `ScheduleMigrationTest`.

- [ ] **Step 1: Add the room-testing library coordinate**

In `gradle/libs.versions.toml`, add this line inside the `[libraries]` section, right after `androidx-room-compiler`:

```toml
androidx-room-testing = { group = "androidx.room", name = "room-testing", version.ref = "room" }
```

- [ ] **Step 2: Wire the dependency and schema export location into the app module**

In `app/build.gradle.kts`, add this block at the top level (sibling to `android { }` and `dependencies { }`, anywhere after the `plugins { }` block):

```kotlin
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}
```

Then, inside the `dependencies { }` block, find the line `androidTestImplementation(libs.androidx.compose.ui.test.junit4)` and add directly below it:

```kotlin
    androidTestImplementation(libs.androidx.room.testing)
```

- [ ] **Step 3: Turn on schema export**

In `app/src/main/java/com/onlyou/com/data/local/Database.kt`, change:

```kotlin
@Database(
    entities = [
        AlarmEntity::class,
        DDayEntity::class,
        AiScheduleEntity::class,
        PersonaEntity::class,
        MemoryEntity::class,
        ChatMessageEntity::class,
        AlarmVoiceChunkEntity::class,
    ],
    version = 17,
    exportSchema = false,
)
```

to:

```kotlin
@Database(
    entities = [
        AlarmEntity::class,
        DDayEntity::class,
        AiScheduleEntity::class,
        PersonaEntity::class,
        MemoryEntity::class,
        ChatMessageEntity::class,
        AlarmVoiceChunkEntity::class,
    ],
    version = 17,
    exportSchema = true,
)
```

(Version stays at 17 for this step — Task 2 bumps it to 18 after the schema snapshot exists.)

- [ ] **Step 4: Generate the schema snapshot**

Run:
```bash
./gradlew :app:kspDebugKotlin
```
Expected: build succeeds, and a new file appears at `app/schemas/com.onlyou.com.data.local.MiyaDatabase/17.json`. Verify with:
```bash
ls app/schemas/com.onlyou.com.data.local.MiyaDatabase/
```
Expected output: `17.json`

- [ ] **Step 5: Commit**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts app/src/main/java/com/onlyou/com/data/local/Database.kt app/schemas
git commit -m "chore: enable Room schema export and capture v17 baseline"
```

---

### Task 2: Add `updatedAt`/`pendingSync` columns via Migration 17→18, with a migration test

**Files:**
- Modify: `app/src/main/java/com/onlyou/com/data/local/Entities.kt:33-45` (`AiScheduleEntity`)
- Modify: `app/src/main/java/com/onlyou/com/data/local/Database.kt:36-51` (`AiScheduleDao`), `:128-139` (`@Database` version bump)
- Modify: `app/src/main/java/com/onlyou/com/di/DatabaseModule.kt`
- Create: `app/src/androidTest/java/com/onlyou/com/data/local/ScheduleMigrationTest.kt`

**Interfaces:**
- Consumes: `app/schemas/com.onlyou.com.data.local.MiyaDatabase/17.json` (from Task 1).
- Produces: `AiScheduleEntity.updatedAt: Long`, `AiScheduleEntity.pendingSync: Boolean`; `AiScheduleDao.updatePendingSync(id: String, pending: Boolean)`; `AiScheduleDao.getPendingSchedulesOnce(): List<AiScheduleEntity>`; `MIGRATION_17_18` — all consumed by Task 4/5.

- [ ] **Step 1: Write the failing migration test**

Create `app/src/androidTest/java/com/onlyou/com/data/local/ScheduleMigrationTest.kt`:

```kotlin
package com.onlyou.com.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ScheduleMigrationTest {
    private val testDbName = "schedule-migration-test"

    @get:Rule
    val helper: MigrationTestHelper =
        MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            MiyaDatabase::class.java.canonicalName!!,
            FrameworkSQLiteOpenHelperFactory(),
        )

    @Test
    fun migrate17To18_preservesExistingRowAndDefaultsNewColumns() {
        var db = helper.createDatabase(testDbName, 17)
        db.execSQL(
            """
            INSERT INTO ai_schedules (id, date, endDate, startTime, timeHint, repeatDays, title, description, location, isAlarmEnabled)
            VALUES ('sched-1', '2026-07-10', NULL, '09:00', NULL, '', '병원 예약', NULL, NULL, 1)
            """.trimIndent(),
        )
        db.close()

        db = helper.runMigrationsAndValidate(testDbName, 18, true, MIGRATION_17_18)

        val cursor = db.query("SELECT title, updatedAt, pendingSync FROM ai_schedules WHERE id = 'sched-1'")
        assertTrue(cursor.moveToFirst())
        assertEquals("병원 예약", cursor.getString(cursor.getColumnIndexOrThrow("title")))
        assertEquals(0L, cursor.getLong(cursor.getColumnIndexOrThrow("updatedAt")))
        assertEquals(1, cursor.getInt(cursor.getColumnIndexOrThrow("pendingSync")))
        cursor.close()
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run:
```bash
./gradlew :app:connectedDebugAndroidTest --tests "com.onlyou.com.data.local.ScheduleMigrationTest"
```
Expected: FAIL — compile error (`MIGRATION_17_18` unresolved) or, once that's stubbed, a Room "no migration found" / missing-column error. (Requires a connected emulator or device.)

- [ ] **Step 3: Add the new columns to the entity**

In `app/src/main/java/com/onlyou/com/data/local/Entities.kt`, change:

```kotlin
@Entity(tableName = "ai_schedules")
data class AiScheduleEntity(
    @PrimaryKey val id: String,
    val date: LocalDate?,
    val endDate: LocalDate?,
    val startTime: LocalTime?,
    val timeHint: String?,
    val repeatDays: Set<DayOfWeek>,
    val title: String,
    val description: String?,
    val location: String?,
    val isAlarmEnabled: Boolean,
)
```

to:

```kotlin
@Entity(tableName = "ai_schedules")
data class AiScheduleEntity(
    @PrimaryKey val id: String,
    val date: LocalDate?,
    val endDate: LocalDate?,
    val startTime: LocalTime?,
    val timeHint: String?,
    val repeatDays: Set<DayOfWeek>,
    val title: String,
    val description: String?,
    val location: String?,
    val isAlarmEnabled: Boolean,
    val updatedAt: Long = 0L,
    val pendingSync: Boolean = true,
)
```

- [ ] **Step 4: Add the DAO queries**

In `app/src/main/java/com/onlyou/com/data/local/Database.kt`, inside `interface AiScheduleDao`, add after `deleteSchedule`:

```kotlin
    @Query("UPDATE ai_schedules SET pendingSync = :pending WHERE id = :id")
    suspend fun updatePendingSync(id: String, pending: Boolean)

    @Query("SELECT * FROM ai_schedules WHERE pendingSync = 1")
    suspend fun getPendingSchedulesOnce(): List<AiScheduleEntity>
```

Then bump the database version:

```kotlin
@Database(
    entities = [
        AlarmEntity::class,
        DDayEntity::class,
        AiScheduleEntity::class,
        PersonaEntity::class,
        MemoryEntity::class,
        ChatMessageEntity::class,
        AlarmVoiceChunkEntity::class,
    ],
    version = 18,
    exportSchema = true,
)
```

- [ ] **Step 5: Add the migration and register it**

In `app/src/main/java/com/onlyou/com/di/DatabaseModule.kt`, add after `MIGRATION_14_15`:

```kotlin
val MIGRATION_17_18 = object : Migration(17, 18) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE ai_schedules ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE ai_schedules ADD COLUMN pendingSync INTEGER NOT NULL DEFAULT 1")
    }
}
```

Then update the builder call:

```kotlin
            .addMigrations(MIGRATION_13_14, MIGRATION_14_15, MIGRATION_17_18)
```

- [ ] **Step 6: Run the test to verify it passes**

```bash
./gradlew :app:connectedDebugAndroidTest --tests "com.onlyou.com.data.local.ScheduleMigrationTest"
```
Expected: PASS

- [ ] **Step 7: Generate the v18 schema snapshot**

```bash
./gradlew :app:kspDebugKotlin
ls app/schemas/com.onlyou.com.data.local.MiyaDatabase/
```
Expected: both `17.json` and `18.json` present.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/onlyou/com/data/local/Entities.kt app/src/main/java/com/onlyou/com/data/local/Database.kt app/src/main/java/com/onlyou/com/di/DatabaseModule.kt app/src/androidTest/java/com/onlyou/com/data/local/ScheduleMigrationTest.kt app/schemas
git commit -m "feat: add updatedAt/pendingSync columns to ai_schedules (migration 17->18)"
```

---

### Task 3: Pure Firestore mapping and conflict-resolution functions

These are plain functions with no Firestore/Android dependency at call time (they take/return primitives and `AiScheduleEntity`), so they're unit-testable on the host JVM without mocking — see Global Constraints on why no mocking library is introduced.

**Files:**
- Modify: `app/src/main/java/com/onlyou/com/data/repository/ScheduleRepositoryImpl.kt`
- Create: `app/src/test/java/com/onlyou/com/data/repository/ScheduleSyncMappingTest.kt`

**Interfaces:**
- Consumes: `AiScheduleEntity` (from Task 2, now with `updatedAt`/`pendingSync`).
- Produces: `aiScheduleEntityToFirestoreMap(entity: AiScheduleEntity): Map<String, Any?>`, `mapToScheduleEntity(id: String, data: Map<String, Any?>): AiScheduleEntity?`, `isRemoteNewer(localUpdatedAt: Long, remoteUpdatedAt: Long): Boolean` — all top-level `internal` functions in `ScheduleRepositoryImpl.kt`, consumed by Task 4 and Task 5.

- [ ] **Step 1: Write the failing tests**

Create `app/src/test/java/com/onlyou/com/data/repository/ScheduleSyncMappingTest.kt`:

```kotlin
package com.onlyou.com.data.repository

import com.google.firebase.Timestamp
import com.onlyou.com.data.local.AiScheduleEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.util.Date

class ScheduleSyncMappingTest {

    @Test
    fun `toFirestoreMap serializes dates and times as ISO strings`() {
        val entity = AiScheduleEntity(
            id = "sched-1",
            date = LocalDate.of(2026, 7, 10),
            endDate = null,
            startTime = LocalTime.of(9, 0),
            timeHint = null,
            repeatDays = emptySet(),
            title = "병원 예약",
            description = null,
            location = "강남역",
            isAlarmEnabled = true,
            updatedAt = 1_720_000_000_000L,
            pendingSync = true,
        )

        val map = aiScheduleEntityToFirestoreMap(entity)

        assertEquals("2026-07-10", map["date"])
        assertEquals("09:00", map["startTime"])
        assertEquals("병원 예약", map["title"])
        assertEquals("강남역", map["location"])
        assertEquals(true, map["isAlarmEnabled"])
        assertEquals(1_720_000_000_000L, map["updatedAt"])
        assertTrue(!map.containsKey("pendingSync"))
    }

    @Test
    fun `mapToScheduleEntity parses a well-formed remote document`() {
        val remoteData = mapOf(
            "date" to "2026-07-10",
            "endDate" to null,
            "startTime" to "09:00",
            "timeHint" to null,
            "repeatDays" to listOf("MONDAY", "WEDNESDAY"),
            "title" to "병원 예약",
            "description" to null,
            "location" to "강남역",
            "isAlarmEnabled" to true,
            "updatedAt" to Timestamp(Date(1_720_000_000_000L)),
        )

        val entity = mapToScheduleEntity("sched-1", remoteData)

        requireNotNull(entity)
        assertEquals("sched-1", entity.id)
        assertEquals(LocalDate.of(2026, 7, 10), entity.date)
        assertEquals(LocalTime.of(9, 0), entity.startTime)
        assertEquals(setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY), entity.repeatDays)
        assertEquals("병원 예약", entity.title)
        assertEquals(1_720_000_000_000L, entity.updatedAt)
        assertTrue(!entity.pendingSync)
    }

    @Test
    fun `mapToScheduleEntity returns null when title is missing`() {
        val entity = mapToScheduleEntity("sched-1", mapOf("date" to "2026-07-10"))
        assertNull(entity)
    }

    @Test
    fun `mapToScheduleEntity ignores unparseable date instead of throwing`() {
        val entity = mapToScheduleEntity("sched-1", mapOf("title" to "약속", "date" to "not-a-date"))
        requireNotNull(entity)
        assertNull(entity.date)
    }

    @Test
    fun `isRemoteNewer returns true only when remote timestamp is strictly greater`() {
        assertTrue(isRemoteNewer(localUpdatedAt = 100L, remoteUpdatedAt = 200L))
        assertEquals(false, isRemoteNewer(localUpdatedAt = 200L, remoteUpdatedAt = 200L))
        assertEquals(false, isRemoteNewer(localUpdatedAt = 200L, remoteUpdatedAt = 100L))
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

```bash
./gradlew :app:testDebugUnitTest --tests "com.onlyou.com.data.repository.ScheduleSyncMappingTest"
```
Expected: FAIL with unresolved references (`aiScheduleEntityToFirestoreMap`, `mapToScheduleEntity`, `isRemoteNewer` don't exist yet).

- [ ] **Step 3: Implement the pure functions**

In `app/src/main/java/com/onlyou/com/data/repository/ScheduleRepositoryImpl.kt`, add these imports at the top (alongside the existing ones):

```kotlin
import com.google.firebase.Timestamp
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
```

Then add these top-level functions below the imports, above `class ScheduleRepositoryImpl`:

```kotlin
internal fun aiScheduleEntityToFirestoreMap(entity: AiScheduleEntity): Map<String, Any?> = mapOf(
    "date" to entity.date?.toString(),
    "endDate" to entity.endDate?.toString(),
    "startTime" to entity.startTime?.toString(),
    "timeHint" to entity.timeHint,
    "repeatDays" to entity.repeatDays.map { it.name },
    "title" to entity.title,
    "description" to entity.description,
    "location" to entity.location,
    "isAlarmEnabled" to entity.isAlarmEnabled,
    "updatedAt" to entity.updatedAt,
)

internal fun mapToScheduleEntity(id: String, data: Map<String, Any?>): AiScheduleEntity? {
    val title = data["title"] as? String ?: return null
    return AiScheduleEntity(
        id = id,
        date = (data["date"] as? String)?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
        endDate = (data["endDate"] as? String)?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
        startTime = (data["startTime"] as? String)?.let { runCatching { LocalTime.parse(it) }.getOrNull() },
        timeHint = data["timeHint"] as? String,
        repeatDays = (data["repeatDays"] as? List<*>)
            ?.mapNotNull { day -> runCatching { DayOfWeek.valueOf(day as String) }.getOrNull() }
            ?.toSet()
            ?: emptySet(),
        title = title,
        description = data["description"] as? String,
        location = data["location"] as? String,
        isAlarmEnabled = data["isAlarmEnabled"] as? Boolean ?: false,
        updatedAt = (data["updatedAt"] as? Timestamp)?.toDate()?.time ?: 0L,
        pendingSync = false,
    )
}

internal fun isRemoteNewer(localUpdatedAt: Long, remoteUpdatedAt: Long): Boolean =
    remoteUpdatedAt > localUpdatedAt
```

- [ ] **Step 4: Run the tests to verify they pass**

```bash
./gradlew :app:testDebugUnitTest --tests "com.onlyou.com.data.repository.ScheduleSyncMappingTest"
```
Expected: PASS (5 tests)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/onlyou/com/data/repository/ScheduleRepositoryImpl.kt app/src/test/java/com/onlyou/com/data/repository/ScheduleSyncMappingTest.kt
git commit -m "feat: add pure Firestore mapping/conflict-resolution functions for schedules"
```

---

### Task 4: Wire Firestore push into insert/update/delete

This task connects the pure functions from Task 3 to real Firestore calls. There is no unit test for this task's wiring itself (it requires a live/emulated Firestore, which this project has no infra for — see Global Constraints); it's covered by manual verification in Task 6.

**Files:**
- Modify: `app/src/main/java/com/onlyou/com/data/repository/ScheduleRepositoryImpl.kt`

**Interfaces:**
- Consumes: `aiScheduleEntityToFirestoreMap` (Task 3), `AiScheduleDao.updatePendingSync` (Task 2), `FirebaseFirestore`, `FirebaseAuth` (both already Hilt-provided in `DatabaseModule.kt`/`AppModule.kt`).
- Produces: `ScheduleRepositoryImpl` now requires `FirebaseFirestore` and `FirebaseAuth` constructor params — consumed automatically by Hilt (no manual wiring needed elsewhere, per existing `@Binds` in `AppModule.kt`).

- [ ] **Step 1: Read the current file to confirm line numbers before editing**

The current `ScheduleRepositoryImpl.kt` (pre-Task-3) is:

```kotlin
package com.onlyou.com.data.repository

import com.onlyou.com.data.local.AiScheduleDao
import com.onlyou.com.data.local.AiScheduleEntity
import com.onlyou.com.domain.model.AiSchedule
import com.onlyou.com.domain.repository.ScheduleRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class ScheduleRepositoryImpl
    @Inject
    constructor(
        private val scheduleDao: AiScheduleDao,
    ) : ScheduleRepository {
        override fun getAllSchedules(): Flow<List<AiSchedule>> =
            scheduleDao.getAllSchedules().map { entities ->
                entities.map { it.toDomainModel() }
            }

        override suspend fun insertSchedule(schedule: AiSchedule) {
            scheduleDao.insertSchedule(schedule.toEntity())
        }

        override suspend fun updateSchedule(schedule: AiSchedule) {
            scheduleDao.updateSchedule(schedule.toEntity())
        }

        override suspend fun deleteSchedule(schedule: AiSchedule) {
            scheduleDao.deleteSchedule(schedule.toEntity())
        }

        private fun AiScheduleEntity.toDomainModel() =
            AiSchedule(
                id = id,
                date = date,
                endDate = endDate,
                startTime = startTime,
                timeHint = timeHint,
                repeatDays = repeatDays,
                title = title,
                description = description,
                location = location,
                isAlarmEnabled = isAlarmEnabled,
            )

        private fun AiSchedule.toEntity() =
            AiScheduleEntity(
                id = id,
                date = date,
                endDate = endDate,
                startTime = startTime,
                timeHint = timeHint,
                repeatDays = repeatDays,
                title = title,
                description = description,
                location = location,
                isAlarmEnabled = isAlarmEnabled,
            )
    }
```

(By this point in the plan, Task 3 has already added the three top-level functions above this class — leave those untouched.)

- [ ] **Step 2: Replace the class body**

Replace the whole `class ScheduleRepositoryImpl { ... }` block with:

```kotlin
class ScheduleRepositoryImpl
    @Inject
    constructor(
        private val scheduleDao: AiScheduleDao,
        private val firestore: com.google.firebase.firestore.FirebaseFirestore,
        private val auth: com.google.firebase.auth.FirebaseAuth,
    ) : ScheduleRepository {
        private val syncScope = kotlinx.coroutines.CoroutineScope(
            kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO,
        )

        override fun getAllSchedules(): Flow<List<AiSchedule>> =
            scheduleDao.getAllSchedules().map { entities ->
                entities.map { it.toDomainModel() }
            }

        override suspend fun insertSchedule(schedule: AiSchedule) {
            val entity = schedule.toEntity()
            scheduleDao.insertSchedule(entity)
            pushToFirestore(entity)
        }

        override suspend fun updateSchedule(schedule: AiSchedule) {
            val entity = schedule.toEntity()
            scheduleDao.updateSchedule(entity)
            pushToFirestore(entity)
        }

        override suspend fun deleteSchedule(schedule: AiSchedule) {
            scheduleDao.deleteSchedule(schedule.toEntity())
            val uid = auth.currentUser?.uid ?: return
            syncScope.launch {
                try {
                    firestore.collection("users").document(uid)
                        .collection("schedules").document(schedule.id)
                        .delete()
                        .await()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        private fun pushToFirestore(entity: AiScheduleEntity) {
            val uid = auth.currentUser?.uid ?: return
            syncScope.launch {
                try {
                    firestore.collection("users").document(uid)
                        .collection("schedules").document(entity.id)
                        .set(aiScheduleEntityToFirestoreMap(entity))
                        .await()
                    scheduleDao.updatePendingSync(entity.id, false)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        private fun AiScheduleEntity.toDomainModel() =
            AiSchedule(
                id = id,
                date = date,
                endDate = endDate,
                startTime = startTime,
                timeHint = timeHint,
                repeatDays = repeatDays,
                title = title,
                description = description,
                location = location,
                isAlarmEnabled = isAlarmEnabled,
            )

        private fun AiSchedule.toEntity() =
            AiScheduleEntity(
                id = id,
                date = date,
                endDate = endDate,
                startTime = startTime,
                timeHint = timeHint,
                repeatDays = repeatDays,
                title = title,
                description = description,
                location = location,
                isAlarmEnabled = isAlarmEnabled,
                updatedAt = System.currentTimeMillis(),
                pendingSync = true,
            )
    }
```

Add these two imports at the top of the file, alongside the ones from Task 3:

```kotlin
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
```

- [ ] **Step 3: Verify it compiles**

```bash
./gradlew :app:compileDebugKotlin
```
Expected: BUILD SUCCESSFUL. (Hilt will resolve the two new constructor params automatically since `FirebaseFirestore` and `FirebaseAuth` are already provided as singletons in `DatabaseModule.kt`/`AppModule.kt` — no DI module changes needed.)

- [ ] **Step 4: Run the full unit test suite to confirm nothing broke**

```bash
./gradlew :app:testDebugUnitTest
```
Expected: PASS (includes the Task 3 tests, unaffected by this change).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/onlyou/com/data/repository/ScheduleRepositoryImpl.kt
git commit -m "feat: push schedule writes to Firestore on insert/update/delete"
```

---

### Task 5: Add `syncSchedules()` pull and wire it into the ViewModel

**Files:**
- Modify: `app/src/main/java/com/onlyou/com/domain/repository/Repositories.kt:29-37` (`ScheduleRepository` interface)
- Modify: `app/src/main/java/com/onlyou/com/data/repository/ScheduleRepositoryImpl.kt`
- Modify: `app/src/main/java/com/onlyou/com/ui/schedule/ScheduleViewModel.kt:37-48` (`init` block)

**Interfaces:**
- Consumes: `mapToScheduleEntity`, `isRemoteNewer` (Task 3), `AiScheduleDao.getPendingSchedulesOnce` (Task 2), `pushToFirestore` (Task 4, private — reused within the same class).
- Produces: `ScheduleRepository.syncSchedules(): Unit` (suspend), called by `ScheduleViewModel.init`.

- [ ] **Step 1: Add the method to the domain interface**

In `app/src/main/java/com/onlyou/com/domain/repository/Repositories.kt`, change:

```kotlin
interface ScheduleRepository {
    fun getAllSchedules(): Flow<List<AiSchedule>>

    suspend fun insertSchedule(schedule: AiSchedule)

    suspend fun updateSchedule(schedule: AiSchedule)

    suspend fun deleteSchedule(schedule: AiSchedule)
}
```

to:

```kotlin
interface ScheduleRepository {
    fun getAllSchedules(): Flow<List<AiSchedule>>

    suspend fun insertSchedule(schedule: AiSchedule)

    suspend fun updateSchedule(schedule: AiSchedule)

    suspend fun deleteSchedule(schedule: AiSchedule)

    suspend fun syncSchedules() // Firestore와 로컬 DB 동기화 (pull + 재시도)
}
```

- [ ] **Step 2: Implement `syncSchedules()` in the repository**

In `app/src/main/java/com/onlyou/com/data/repository/ScheduleRepositoryImpl.kt`, add this method inside the class, after `deleteSchedule`:

```kotlin
        override suspend fun syncSchedules() {
            val uid = auth.currentUser?.uid ?: return

            // 1. 이전에 전송 실패했던 로컬 항목 재시도
            scheduleDao.getPendingSchedulesOnce().forEach { pushToFirestore(it) }

            // 2. 원격 목록 pull
            try {
                val snapshot = kotlinx.coroutines.withTimeout(5000L) {
                    firestore.collection("users").document(uid)
                        .collection("schedules")
                        .get()
                        .await()
                }
                val localById = scheduleDao.getAllSchedulesOnce().associateBy { it.id }
                snapshot.documents.forEach { doc ->
                    val remote = mapToScheduleEntity(doc.id, doc.data ?: emptyMap()) ?: return@forEach
                    val local = localById[doc.id]
                    if (local == null || isRemoteNewer(local.updatedAt, remote.updatedAt)) {
                        scheduleDao.insertSchedule(remote)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
```

- [ ] **Step 3: Verify it compiles**

```bash
./gradlew :app:compileDebugKotlin
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Call it from the ViewModel**

In `app/src/main/java/com/onlyou/com/ui/schedule/ScheduleViewModel.kt`, change the `init` block from:

```kotlin
        init {
            viewModelScope.launch {
                networkMonitor.isOnline.collectLatest { isOnline ->
                    _uiState.update { it.copy(isOnline = isOnline) }
                }
            }
            viewModelScope.launch {
                repository.getAllSchedules().collect { list ->
                    _uiState.update { it.copy(schedules = list) }
                }
            }
        }
```

to:

```kotlin
        init {
            viewModelScope.launch {
                networkMonitor.isOnline.collectLatest { isOnline ->
                    _uiState.update { it.copy(isOnline = isOnline) }
                }
            }
            viewModelScope.launch {
                repository.getAllSchedules().collect { list ->
                    _uiState.update { it.copy(schedules = list) }
                }
            }
            viewModelScope.launch {
                repository.syncSchedules()
            }
        }
```

- [ ] **Step 5: Run the full test suite**

```bash
./gradlew :app:testDebugUnitTest
```
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/onlyou/com/domain/repository/Repositories.kt app/src/main/java/com/onlyou/com/data/repository/ScheduleRepositoryImpl.kt app/src/main/java/com/onlyou/com/ui/schedule/ScheduleViewModel.kt
git commit -m "feat: pull schedules from Firestore on app start / schedule tab entry"
```

---

### Task 6: Manual end-to-end verification

No further automated tests are added here — this task is a manual checklist matching the spec's testing strategy (spec §6), since it requires a real logged-in Firebase account and either two devices or a device + Firebase console.

**Files:** None (verification only).

- [ ] **Step 1: Two-device / cross-session sync**
  1. Log into the app with a real Google account on a device or emulator.
  2. In chat, say something that creates a schedule (e.g. "다음주 화요일 3시에 치과 예약").
  3. Open the Firebase Console → Firestore → `users/{your-uid}/schedules` and confirm a document with matching `title`/`date` appears within a few seconds.
  4. Force-stop and relaunch the app (or install on a second device/emulator with the same account).
  5. Open the schedule tab. Confirm the schedule appears without having been re-created.

- [ ] **Step 2: Offline creation + later sync**
  1. Enable airplane mode on the device.
  2. Create a schedule via chat (or manual entry if available).
  3. Confirm it appears immediately in the local Schedule tab.
  4. Disable airplane mode, then reopen the Schedule tab (triggering `syncSchedules()`).
  5. Check Firebase Console — confirm the document now exists in Firestore.

- [ ] **Step 3: Delete propagation**
  1. Delete a schedule from the Schedule tab.
  2. Confirm the corresponding Firestore document is removed from the console (allow a few seconds for the fire-and-forget delete).

- [ ] **Step 4: Firestore security rule (manual, outside this repo)**

This repository has no `firestore.rules` file or Firebase CLI project config (`firebase.json`) — security rules for this project are managed directly in the Firebase Console, not as code here. Before relying on this feature with real user data, add a rule scoping `users/{uid}/schedules/{scheduleId}` reads/writes to `request.auth.uid == uid`, matching whatever pattern is already used for the `users/{uid}` document (check the Firebase Console → Firestore → Rules tab). This step cannot be automated or verified from within this codebase — flag it to whoever has Firebase Console access.

- [ ] **Step 5: Record results**

If all of Steps 1-3 pass, the feature is done. If Step 4 hasn't been done yet, note it as an explicit follow-up before shipping to real users — the sync will work in testing regardless (Firebase projects typically start in permissive test mode), but production rollout without the rule leaves the `schedules` subcollection unprotected.

---

## Plan Self-Review

**Spec coverage:**
- §2 (Android writes directly to Firestore, single choke point in `ScheduleRepositoryImpl`) → Tasks 4, 5.
- §3 (data model, `users/{uid}/schedules/{id}`, field mapping, `updatedAt`/`pendingSync`, Room migration) → Tasks 1, 2, 3.
- §4.1 (push: Room-first, fire-and-forget, `pendingSync` retry safety net) → Task 4.
- §4.2 (pull: retry pending, timeout, upsert by `updatedAt`, missing-remote items left alone) → Task 5.
- §5 (known limitations: no delete-sync, no ghost-doc retry, no account-switch cache clear, no reminder fields) → explicitly called out as out-of-scope in Global Constraints and Task 6 Step 3/4 notes; not implemented anywhere in Tasks 1-5.
- §6 (Room migration test, repository unit test, manual two-device/offline verification) → Tasks 2 (migration test), 3 (pure-function unit tests), 6 (manual verification).

**Placeholder scan:** No TBD/TODO markers; every step has concrete code or an exact command with expected output.

**Type consistency:** `AiScheduleEntity` fields (`updatedAt: Long`, `pendingSync: Boolean`) are introduced in Task 2 and used identically in Tasks 3, 4, 5. Function names (`aiScheduleEntityToFirestoreMap`, `mapToScheduleEntity`, `isRemoteNewer`) are defined once in Task 3 and referenced by those exact names in Tasks 4 and 5. `ScheduleRepository.syncSchedules()` signature matches between the Task 5 interface addition and its `ScheduleRepositoryImpl` implementation.
