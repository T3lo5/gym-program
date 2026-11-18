package com.treinos.app.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
class MigrationTest {
    private val testDb = "migration-test"

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    private fun seedV1(db: SupportSQLiteDatabase) {
        db.execSQL(
            "INSERT INTO workout_plans (id, name, description, goal, daysPerWeek, isActive, " +
                "generatedByAi, createdAt, updatedAt, version, nextReviewAt, reviewAfterWeeks, source) " +
                "VALUES (1,'P','d','hypertrophy',4,1,0,1,2,1,NULL,4,'local')"
        )
        db.execSQL(
            "INSERT INTO workout_days (id, planId, name, dayOrder, notes, isRestDay) " +
                "VALUES (10,1,'A',0,'',0)"
        )
        db.execSQL(
            "INSERT INTO workout_plan_exercises (id, dayId, exerciseId, exerciseName, " +
                "primaryMuscles, equipment, exerciseOrder, sets, targetReps, targetRpe, " +
                "restSeconds, notes, isSupersetWithPrevious) " +
                "VALUES (100,10,'ex1','Ex','peito','barra',0,3,'8-12',NULL,90,'',0)"
        )
        db.execSQL(
            "INSERT INTO workout_sessions (id, planId, dayId, planName, dayName, status, " +
                "startedAt, endedAt, totalDurationSeconds, notes) " +
                "VALUES (50,1,10,'P','A','completed',1,10,9,'')"
        )
        db.execSQL(
            "INSERT INTO session_exercises (id, sessionId, exerciseId, exerciseName, " +
                "exerciseOrder, plannedSets, completedSets, restSeconds, startedAt, endedAt) " +
                "VALUES (500,50,'ex1','Ex',0,3,1,90,1,NULL)"
        )
        db.execSQL(
            "INSERT INTO set_logs (id, exerciseLogId, setNumber, reps, weightKg, rpe, " +
                "durationSeconds, distanceMeters, isCompleted, isWarmup, restTakenSeconds, completedAt) " +
                "VALUES (5000,500,1,10,40.0,8.0,NULL,NULL,1,0,90,2)"
        )
    }

    @Test
    @Throws(IOException::class)
    fun banco_novo_ja_e_v2() {
        val db = helper.createDatabase(testDb, 2)
        db.query("PRAGMA user_version").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(2, cursor.getInt(0))
        }
        db.query("PRAGMA table_info(workout_sessions)").use { cursor ->
            val columns = buildSet {
                while (cursor.moveToNext()) add(cursor.getString(cursor.getColumnIndexOrThrow("name")))
            }
            assertTrue(columns.containsAll(setOf("modality", "source", "title", "sessionRpe")))
        }
        db.close()
    }

    @Test
    @Throws(IOException::class)
    fun migracao_1_2_preserva_planos_e_sessoes() {
        helper.createDatabase(testDb, 1).let { db ->
            seedV1(db)
            db.close()
        }

        val migrated = helper.runMigrationsAndValidate(testDb, 2, true, AppDatabase.MIGRATION_1_2)
        assertEquals(1, migrated.rowCount("workout_plans"))
        assertEquals(1, migrated.rowCount("workout_days"))
        assertEquals(1, migrated.rowCount("workout_plan_exercises"))
        assertEquals(1, migrated.rowCount("workout_sessions"))
        assertEquals(1, migrated.rowCount("session_exercises"))
        assertEquals(1, migrated.rowCount("set_logs"))

        migrated.query(
            "SELECT modality, source, title FROM workout_sessions WHERE id = 50"
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("strength", cursor.getString(0))
            assertEquals("plan", cursor.getString(1))
            assertEquals("", cursor.getString(2))
        }
        migrated.close()
    }

    @Test
    @Throws(IOException::class)
    fun migracao_1_2_aplica_defaults() {
        helper.createDatabase(testDb, 1).let { db ->
            seedV1(db)
            db.close()
        }

        val migrated = helper.runMigrationsAndValidate(testDb, 2, true, AppDatabase.MIGRATION_1_2)
        migrated.query(
            "SELECT modality, weekday FROM workout_days WHERE id = 10"
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("strength", cursor.getString(0))
            assertTrue(cursor.isNull(1))
        }
        migrated.query(
            "SELECT metricType, targetDurationSeconds, targetDistanceMeters, " +
                "targetPaceSecondsPerKm, targetHrZone FROM workout_plan_exercises WHERE id = 100"
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("reps_weight", cursor.getString(0))
            assertTrue(cursor.isNull(1))
            assertTrue(cursor.isNull(2))
            assertTrue(cursor.isNull(3))
            assertNull(cursor.getString(4))
        }
        migrated.close()
    }

    @Test
    @Throws(IOException::class)
    fun migration2To3_preserva_planos_e_ativo() {
        helper.createDatabase(testDb, 2).let { db ->
            db.execSQL(
                "INSERT INTO workout_plans (id, name, description, goal, daysPerWeek, isActive, " +
                    "generatedByAi, createdAt, updatedAt, version, nextReviewAt, reviewAfterWeeks, source) " +
                    "VALUES (1,'Principal','d','hypertrophy',4,1,0,1,2,1,NULL,4,'local')"
            )
            db.execSQL(
                "INSERT INTO workout_plans (id, name, description, goal, daysPerWeek, isActive, " +
                    "generatedByAi, createdAt, updatedAt, version, nextReviewAt, reviewAfterWeeks, source) " +
                    "VALUES (2,'Antigo','d','hypertrophy',3,0,0,1,1,1,NULL,4,'local')"
            )
            db.execSQL(
                "INSERT INTO workout_days (id, planId, name, dayOrder, notes, isRestDay, modality, weekday) " +
                    "VALUES (10,1,'A',0,'',0,'strength',1)"
            )
            db.close()
        }

        val migrated = helper.runMigrationsAndValidate(testDb, 3, true, AppDatabase.MIGRATION_2_3)

        assertEquals(2, migrated.rowCount("workout_plans"))
        assertEquals(1, migrated.rowCount("workout_days"))
        migrated.query("SELECT id, kind, isActive FROM workout_plans ORDER BY id").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(1, cursor.getInt(0))
            assertEquals("main", cursor.getString(1))
            assertEquals(1, cursor.getInt(2))
            assertTrue(cursor.moveToNext())
            assertEquals(2, cursor.getInt(0))
            assertEquals("main", cursor.getString(1))
            assertEquals(0, cursor.getInt(2))
        }
        migrated.close()
    }

    @Test
    @Throws(IOException::class)
    fun migracao_3_4_preserva_linhas() {
        // H3-24: v3 já tem `kind` (2→3) e as colunas de modalidade (1→2).
        helper.createDatabase(testDb, 3).let { db ->
            db.execSQL(
                "INSERT INTO workout_plans (id, name, description, goal, daysPerWeek, isActive, " +
                    "generatedByAi, createdAt, updatedAt, version, nextReviewAt, reviewAfterWeeks, source, kind) " +
                    "VALUES (1,'Principal','d','hypertrophy',4,1,0,1,2,1,NULL,4,'local','main')"
            )
            db.execSQL(
                "INSERT INTO workout_days (id, planId, name, dayOrder, notes, isRestDay, modality, weekday) " +
                    "VALUES (10,1,'A',0,'',0,'strength',1)"
            )
            db.execSQL(
                "INSERT INTO workout_plan_exercises (id, dayId, exerciseId, exerciseName, " +
                    "primaryMuscles, equipment, exerciseOrder, sets, targetReps, targetRpe, " +
                    "restSeconds, notes, isSupersetWithPrevious, metricType) " +
                    "VALUES (100,10,'Barbell_Bench_Press','Supino','peito','barra',0,3,'8-12',NULL,90,'',0,'reps_weight')"
            )
            db.execSQL(
                "INSERT INTO workout_sessions (id, planId, dayId, planName, dayName, status, " +
                    "startedAt, endedAt, totalDurationSeconds, notes, modality, source, title) " +
                    "VALUES (50,1,10,'P','A','completed',1,10,9,'','strength','plan','A')"
            )
            db.close()
        }

        val migrated = helper.runMigrationsAndValidate(testDb, 4, true, AppDatabase.MIGRATION_3_4)

        // Nada se perde na migração aditiva.
        assertEquals(1, migrated.rowCount("workout_plans"))
        assertEquals(1, migrated.rowCount("workout_days"))
        assertEquals(1, migrated.rowCount("workout_plan_exercises"))
        assertEquals(1, migrated.rowCount("workout_sessions"))

        // Colunas novas nascem vazias (mídia do plano só em planos gerados de
        // agora em diante) e os campos antigos continuam intactos.
        migrated.query(
            "SELECT exerciseId, exerciseName, sets, instructions, images " +
                "FROM workout_plan_exercises WHERE id = 100"
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Barbell_Bench_Press", cursor.getString(0))
            assertEquals("Supino", cursor.getString(1))
            assertEquals(3, cursor.getInt(2))
            assertEquals("", cursor.getString(3))
            assertEquals("", cursor.getString(4))
        }
        migrated.close()
    }

    @Test
    @Throws(IOException::class)
    fun migracao_5_6_preserva_linhas() {
        helper.createDatabase(testDb, 5).let { db ->
            db.execSQL(
                "INSERT INTO workout_plans (id, name, description, goal, daysPerWeek, isActive, " +
                    "generatedByAi, createdAt, updatedAt, version, nextReviewAt, reviewAfterWeeks, source, kind) " +
                    "VALUES (1,'Principal','d','hypertrophy',4,1,0,1,2,1,NULL,4,'local','main')"
            )
            db.execSQL(
                "INSERT INTO workout_days (id, planId, name, dayOrder, notes, isRestDay, modality, weekday) " +
                    "VALUES (10,1,'A',0,'',0,'strength',1)"
            )
            db.execSQL(
                "INSERT INTO workout_plan_exercises (id, dayId, exerciseId, exerciseName, " +
                    "primaryMuscles, equipment, exerciseOrder, sets, targetReps, targetRpe, " +
                    "restSeconds, notes, isSupersetWithPrevious, metricType, instructions, images) " +
                    "VALUES (100,10,'Barbell_Bench_Press','Supino','peito','barra',0,3,'8-12',NULL,90,'',0,'reps_weight','','')"
            )
            db.execSQL(
                "INSERT INTO workout_sessions (id, planId, dayId, planName, dayName, status, " +
                    "startedAt, endedAt, totalDurationSeconds, notes, modality, source, title) " +
                    "VALUES (50,1,10,'P','A','in_progress',1,NULL,0,'','strength','plan','A')"
            )
            db.execSQL(
                "INSERT INTO session_exercises (id, sessionId, exerciseId, exerciseName, " +
                    "exerciseOrder, plannedSets, completedSets, restSeconds, startedAt, endedAt) " +
                    "VALUES (500,50,'Barbell_Bench_Press','Supino',0,3,1,90,1,NULL)"
            )
            db.execSQL(
                "INSERT INTO set_logs (id, exerciseLogId, setNumber, reps, weightKg, rpe, " +
                    "durationSeconds, distanceMeters, isCompleted, isWarmup, restTakenSeconds, completedAt) " +
                    "VALUES (5000,500,1,10,40.0,8.0,NULL,NULL,1,0,90,2)"
            )
            db.execSQL(
                "INSERT INTO exercise_usage_events (id, exerciseId, outcome, occurredAt, planId, dayId, source) " +
                    "VALUES (1,'Barbell_Bench_Press','completed',1,1,10,'generation')"
            )
            db.close()
        }

        val migrated = helper.runMigrationsAndValidate(testDb, 6, true, AppDatabase.MIGRATION_5_6)

        assertEquals(1, migrated.rowCount("workout_plans"))
        assertEquals(1, migrated.rowCount("workout_days"))
        assertEquals(1, migrated.rowCount("workout_plan_exercises"))
        assertEquals(1, migrated.rowCount("workout_sessions"))
        assertEquals(1, migrated.rowCount("session_exercises"))
        assertEquals(1, migrated.rowCount("set_logs"))
        assertEquals(1, migrated.rowCount("exercise_usage_events"))

        migrated.query(
            "SELECT exerciseId, exerciseName, plannedSets, completedSets, targetReps " +
                "FROM session_exercises WHERE id = 500"
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Barbell_Bench_Press", cursor.getString(0))
            assertEquals("Supino", cursor.getString(1))
            assertEquals(3, cursor.getInt(2))
            assertEquals(1, cursor.getInt(3))
            assertEquals("", cursor.getString(4))
        }
        migrated.close()
    }

    @Test
    @Throws(IOException::class)
    fun migracao_6_7_preserva_linhas_e_marca_warmup() {
        // H4-4: banco v6 com um aquecimento (isWarmup=1) e uma série de trabalho.
        helper.createDatabase(testDb, 6).let { db ->
            db.execSQL(
                "INSERT INTO workout_plans (id, name, description, goal, daysPerWeek, isActive, " +
                    "generatedByAi, createdAt, updatedAt, version, nextReviewAt, reviewAfterWeeks, source, kind) " +
                    "VALUES (1,'Principal','d','hypertrophy',4,1,0,1,2,1,NULL,4,'local','main')"
            )
            db.execSQL(
                "INSERT INTO workout_days (id, planId, name, dayOrder, notes, isRestDay, modality, weekday) " +
                    "VALUES (10,1,'A',0,'',0,'strength',1)"
            )
            db.execSQL(
                "INSERT INTO workout_plan_exercises (id, dayId, exerciseId, exerciseName, " +
                    "primaryMuscles, equipment, exerciseOrder, sets, targetReps, targetRpe, " +
                    "restSeconds, notes, isSupersetWithPrevious, metricType, instructions, images) " +
                    "VALUES (100,10,'Barbell_Bench_Press','Supino','peito','barra',0,3,'8-12',NULL,90,'',0,'reps_weight','','')"
            )
            db.execSQL(
                "INSERT INTO workout_sessions (id, planId, dayId, planName, dayName, status, " +
                    "startedAt, endedAt, totalDurationSeconds, notes, modality, source, title) " +
                    "VALUES (50,1,10,'P','A','completed',1,10,9,'','strength','plan','A')"
            )
            db.execSQL(
                "INSERT INTO session_exercises (id, sessionId, exerciseId, exerciseName, " +
                    "exerciseOrder, plannedSets, completedSets, restSeconds, startedAt, endedAt, targetReps) " +
                    "VALUES (500,50,'Barbell_Bench_Press','Supino',0,3,2,90,1,NULL,'')"
            )
            db.execSQL(
                "INSERT INTO set_logs (id, exerciseLogId, setNumber, reps, weightKg, rpe, " +
                    "durationSeconds, distanceMeters, isCompleted, isWarmup, restTakenSeconds, completedAt) " +
                    "VALUES (5000,500,1,10,40.0,8.0,NULL,NULL,1,1,90,2)"
            )
            db.execSQL(
                "INSERT INTO set_logs (id, exerciseLogId, setNumber, reps, weightKg, rpe, " +
                    "durationSeconds, distanceMeters, isCompleted, isWarmup, restTakenSeconds, completedAt) " +
                    "VALUES (5001,500,2,8,42.5,NULL,NULL,NULL,1,0,NULL,3)"
            )
            db.close()
        }

        val migrated = helper.runMigrationsAndValidate(testDb, 7, true, AppDatabase.MIGRATION_6_7)

        // Nada se perde na migração aditiva.
        assertEquals(1, migrated.rowCount("workout_plans"))
        assertEquals(1, migrated.rowCount("workout_days"))
        assertEquals(1, migrated.rowCount("workout_plan_exercises"))
        assertEquals(1, migrated.rowCount("workout_sessions"))
        assertEquals(1, migrated.rowCount("session_exercises"))
        assertEquals(2, migrated.rowCount("set_logs"))

        // Aquecimento vira setType='warmup'; trabalho vira 'normal'; RIR nasce NULL.
        migrated.query(
            "SELECT id, setType, rir, isWarmup FROM set_logs ORDER BY id"
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(5000, cursor.getInt(0))
            assertEquals("warmup", cursor.getString(1))
            assertTrue(cursor.isNull(2))
            assertEquals(1, cursor.getInt(3))
            assertTrue(cursor.moveToNext())
            assertEquals(5001, cursor.getInt(0))
            assertEquals("normal", cursor.getString(1))
            assertTrue(cursor.isNull(2))
            assertEquals(0, cursor.getInt(3))
        }
        // Os campos antigos continuam intactos.
        migrated.query(
            "SELECT reps, weightKg, rpe FROM set_logs WHERE id = 5001"
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(8, cursor.getInt(0))
            assertEquals(42.5, cursor.getDouble(1), 0.0001)
            assertTrue(cursor.isNull(2))
        }
        migrated.close()
    }

    @Test
    @Throws(IOException::class)
    fun migracao_7_8_adiciona_import_ref_e_indice() {
        // H4-7: banco v7 com uma sessão antiga, ainda sem importRef.
        helper.createDatabase(testDb, 7).let { db ->
            db.execSQL(
                "INSERT INTO workout_sessions (id, planId, dayId, planName, dayName, status, " +
                    "startedAt, endedAt, totalDurationSeconds, notes, modality, source, title) " +
                    "VALUES (50,1,10,'P','A','completed',1,10,9,'','strength','plan','A')"
            )
            db.close()
        }

        val migrated = helper.runMigrationsAndValidate(testDb, 8, true, AppDatabase.MIGRATION_7_8)

        // Nada se perde na migração aditiva e a coluna nasce NULL.
        assertEquals(1, migrated.rowCount("workout_sessions"))
        migrated.query("SELECT id, title, importRef FROM workout_sessions WHERE id = 50").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(50, cursor.getInt(0))
            assertEquals("A", cursor.getString(1))
            assertTrue(cursor.isNull(2))
        }

        // O índice único existe e é realmente UNIQUE.
        migrated.query(
            "SELECT sql FROM sqlite_master WHERE type = 'index' " +
                "AND name = 'index_workout_sessions_importRef'"
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertTrue(cursor.getString(0).contains("UNIQUE"))
        }

        // NULLs são distintos: duas sessões sem importRef coexistem...
        migrated.execSQL(
            "INSERT INTO workout_sessions (planId, dayId, planName, dayName, status, " +
                "startedAt, totalDurationSeconds, notes, modality, source, title) " +
                "VALUES (1,10,'P','A','completed',2,0,'','strength','manual','B')"
        )
        migrated.execSQL(
            "INSERT INTO workout_sessions (planId, dayId, planName, dayName, status, " +
                "startedAt, totalDurationSeconds, notes, modality, source, title) " +
                "VALUES (1,10,'P','A','completed',3,0,'','strength','manual','C')"
        )
        // ...e o mesmo importRef não.
        migrated.execSQL(
            "UPDATE workout_sessions SET importRef = 'strong:2026-11-10:treino a' WHERE id = 50"
        )
        var failure: Exception? = null
        try {
            migrated.execSQL(
                "INSERT INTO workout_sessions (planId, dayId, planName, dayName, status, " +
                    "startedAt, totalDurationSeconds, notes, modality, source, title, importRef) " +
                    "VALUES (1,10,'P','A','completed',4,0,'','strength','manual','D'," +
                    "'strong:2026-11-10:treino a')"
            )
        } catch (error: Exception) {
            failure = error
        }
        assertNotNull(failure)
        migrated.close()
    }

    /**
     * H4-8: a migration 8→9 cria a tabela `body_measurements` (aditiva) sem
     * tocar em nada existente. O nome acompanha a migração real desta spec
     * (MIGRATION_8_9, banco v9) — a tabela da spec cita "7_8" por engano
     * histórico.
     */
    @Test
    @Throws(IOException::class)
    fun migracao_8_9_cria_tabela_body_measurements() {
        // H4-8: banco v8 com uma sessão antiga; nada dela pode se perder.
        helper.createDatabase(testDb, 8).let { db ->
            db.execSQL(
                "INSERT INTO workout_sessions (id, planId, dayId, planName, dayName, status, " +
                    "startedAt, endedAt, totalDurationSeconds, notes, modality, source, title) " +
                    "VALUES (50,1,10,'P','A','completed',1,10,9,'','strength','plan','A')"
            )
            db.close()
        }

        val migrated = helper.runMigrationsAndValidate(testDb, 9, true, AppDatabase.MIGRATION_8_9)

        assertEquals(1, migrated.rowCount("workout_sessions"))
        assertEquals(0, migrated.rowCount("body_measurements"))

        // A tabela nova aceita medida com os defaults do contrato: notes '' e
        // source 'manual'; campos numéricos opcionais nascem NULL.
        migrated.execSQL(
            "INSERT INTO body_measurements (measuredAt, weightKg, waistCm) VALUES (1000, 82.4, 84.0)"
        )
        migrated.query(
            "SELECT measuredAt, weightKg, bodyFatPercent, photoPath, notes, source " +
                "FROM body_measurements"
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(1000, cursor.getLong(0))
            assertEquals(82.4, cursor.getDouble(1), 0.0001)
            assertTrue(cursor.isNull(2))
            assertTrue(cursor.isNull(3))
            assertEquals("", cursor.getString(4))
            assertEquals("manual", cursor.getString(5))
        }
        migrated.close()
    }

    @Test
    @Throws(IOException::class)
    fun migracao_8_9_cria_indice_measured_at() {
        helper.createDatabase(testDb, 8).close()

        val migrated = helper.runMigrationsAndValidate(testDb, 9, true, AppDatabase.MIGRATION_8_9)

        migrated.query(
            "SELECT sql FROM sqlite_master WHERE type = 'index' " +
                "AND name = 'index_body_measurements_measuredAt'"
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertTrue(cursor.getString(0).contains("measuredAt"))
        }

        // Regra 5: duas medidas no mesmo dia são permitidas — o índice não é UNIQUE.
        migrated.execSQL("INSERT INTO body_measurements (measuredAt) VALUES (1000)")
        migrated.execSQL("INSERT INTO body_measurements (measuredAt) VALUES (1000)")
        assertEquals(2, migrated.rowCount("body_measurements"))
        migrated.close()
    }

    /**
     * H4-12: a migration 9→10 adiciona `targetWeightKg` (aditiva) sem tocar
     * em nada existente. O nome acompanha a migração real desta spec
     * (MIGRATION_9_10, banco v10) — a fila do Horizon 4 consumiu 6→7 (H4-4),
     * 7→8 (H4-7) e 8→9 (H4-8) antes.
     */
    @Test
    @Throws(IOException::class)
    fun migracao_9_10_preserva_linhas_e_target_weight_nulo() {
        // Banco v9 (H4-8) com um plano, um dia e um exercício de plano.
        helper.createDatabase(testDb, 9).let { db ->
            db.execSQL(
                "INSERT INTO workout_plans (id, name, description, goal, daysPerWeek, isActive, " +
                    "generatedByAi, createdAt, updatedAt, version, nextReviewAt, reviewAfterWeeks, source, kind) " +
                    "VALUES (1,'Principal','d','hypertrophy',4,1,0,1,2,1,NULL,4,'local','main')"
            )
            db.execSQL(
                "INSERT INTO workout_days (id, planId, name, dayOrder, notes, isRestDay, modality, weekday) " +
                    "VALUES (10,1,'A',0,'',0,'strength',1)"
            )
            db.execSQL(
                "INSERT INTO workout_plan_exercises (id, dayId, exerciseId, exerciseName, " +
                    "primaryMuscles, equipment, exerciseOrder, sets, targetReps, targetRpe, " +
                    "restSeconds, notes, isSupersetWithPrevious, metricType, instructions, images) " +
                    "VALUES (100,10,'Barbell_Bench_Press','Supino','peito','barra',0,3,'8-12',NULL,90,'',0,'reps_weight','','')"
            )
            db.execSQL(
                "INSERT INTO workout_sessions (id, planId, dayId, planName, dayName, status, " +
                    "startedAt, endedAt, totalDurationSeconds, notes, modality, source, title) " +
                    "VALUES (50,1,10,'P','A','completed',1,10,9,'','strength','plan','A')"
            )
            db.close()
        }

        val migrated = helper.runMigrationsAndValidate(testDb, 10, true, AppDatabase.MIGRATION_9_10)

        // Nada se perde na migração aditiva.
        assertEquals(1, migrated.rowCount("workout_plans"))
        assertEquals(1, migrated.rowCount("workout_days"))
        assertEquals(1, migrated.rowCount("workout_plan_exercises"))
        assertEquals(1, migrated.rowCount("workout_sessions"))

        // A coluna nova nasce e os planos antigos ficam sem alvo definido.
        migrated.query(
            "SELECT exerciseId, exerciseName, sets, targetReps, targetWeightKg " +
                "FROM workout_plan_exercises WHERE id = 100"
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Barbell_Bench_Press", cursor.getString(0))
            assertEquals("Supino", cursor.getString(1))
            assertEquals(3, cursor.getInt(2))
            assertEquals("8-12", cursor.getString(3))
            assertTrue(cursor.isNull(4))
        }
        migrated.close()
    }

    /**
     * H5-1: a migration 10→11 cria apenas a outbox persistente. Plano/sessão
     * existentes não são alterados e mutations podem ser reinseridas depois.
     */
    @Test
    @Throws(IOException::class)
    fun migracao_10_11_preserva_dados_e_cria_outbox() {
        helper.createDatabase(testDb, 10).let { db ->
            db.execSQL(
                "INSERT INTO workout_plans (id, name, description, goal, daysPerWeek, isActive, " +
                    "generatedByAi, createdAt, updatedAt, version, nextReviewAt, reviewAfterWeeks, source, kind) " +
                    "VALUES (1,'Principal','d','hypertrophy',4,1,0,1,2,1,NULL,4,'local','main')"
            )
            db.execSQL(
                "INSERT INTO workout_sessions (id, planId, dayId, planName, dayName, status, " +
                    "startedAt, endedAt, totalDurationSeconds, notes, modality, source, title) " +
                    "VALUES (50,1,10,'P','A','completed',1,10,9,'','strength','plan','A')"
            )
            db.close()
        }

        val migrated = helper.runMigrationsAndValidate(testDb, 11, true, AppDatabase.MIGRATION_10_11)

        assertEquals(1, migrated.rowCount("workout_plans"))
        assertEquals(1, migrated.rowCount("workout_sessions"))
        assertEquals(0, migrated.rowCount("sync_outbox"))
        migrated.execSQL(
            "INSERT INTO sync_outbox " +
                "(mutationId, entity, entityId, operation, updatedAt, payloadJson, createdAt) " +
                "VALUES ('m1','plan','1','upsert',2,'{}',2)"
        )
        assertEquals(1, migrated.rowCount("sync_outbox"))
        migrated.close()
    }

    @Test
    @Throws(IOException::class)
    fun migracao_11_12_preserva_series_e_adiciona_revisao() {
        helper.createDatabase(testDb, 11).let { db ->
            db.execSQL(
                "INSERT INTO workout_sessions (id, planId, dayId, planName, dayName, status, " +
                    "startedAt, endedAt, totalDurationSeconds, notes, modality, source, title) " +
                    "VALUES (50,1,10,'P','A','in_progress',1,NULL,0,'','strength','plan','A')"
            )
            db.execSQL(
                "INSERT INTO session_exercises (id, sessionId, exerciseId, exerciseName, " +
                    "exerciseOrder, plannedSets, completedSets, restSeconds, startedAt, endedAt, targetReps) " +
                    "VALUES (500,50,'ex1','Ex',0,3,1,90,1,NULL,'8-12')"
            )
            db.execSQL(
                "INSERT INTO set_logs (id, exerciseLogId, setNumber, reps, weightKg, rpe, " +
                    "durationSeconds, distanceMeters, isCompleted, isWarmup, setType, rir, " +
                    "restTakenSeconds, completedAt) " +
                    "VALUES (5000,500,1,10,40.0,8.0,NULL,NULL,1,0,'normal',NULL,90,2)"
            )
            db.close()
        }

        val migrated = helper.runMigrationsAndValidate(testDb, 12, true, AppDatabase.MIGRATION_11_12)

        assertEquals(1, migrated.rowCount("set_logs"))
        migrated.query(
            "SELECT clientSetId, revisionAt, revisionOrigin, revisionId FROM set_logs WHERE id = 5000"
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("", cursor.getString(0))
            assertTrue(cursor.isNull(1))
            assertEquals("", cursor.getString(2))
            assertEquals("", cursor.getString(3))
        }
        migrated.close()
    }

    @Test
    @Throws(IOException::class)
    fun migration12To13_preserva_sessoes_e_campos_corrida_nulos() {
        helper.createDatabase(testDb, 12).let { db ->
            db.execSQL(
                "INSERT INTO workout_sessions (id, planId, dayId, planName, dayName, status, " +
                    "startedAt, endedAt, totalDurationSeconds, notes, modality, source, title, " +
                    "totalDistanceMeters, poolLengthMeters, stroke, sessionRpe) " +
                    "VALUES (50,1,10,'P','Corrida','completed',1,10,600,'','run','plan','A',5000.0,NULL,NULL,NULL)"
            )
            db.close()
        }

        val migrated = helper.runMigrationsAndValidate(testDb, 13, true, AppDatabase.MIGRATION_12_13)

        assertEquals(1, migrated.rowCount("workout_sessions"))
        migrated.query(
            "SELECT dayName, totalDistanceMeters, routePolyline, avgHeartRate, maxHeartRate " +
                "FROM workout_sessions WHERE id = 50"
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Corrida", cursor.getString(0))
            assertEquals(5000.0, cursor.getDouble(1), 0.0)
            assertTrue(cursor.isNull(2))
            assertTrue(cursor.isNull(3))
            assertTrue(cursor.isNull(4))
        }
        migrated.close()
    }

    private fun SupportSQLiteDatabase.rowCount(table: String): Int =
        query("SELECT COUNT(*) FROM $table").use { cursor ->
            assertTrue(cursor.moveToFirst())
            cursor.getInt(0)
        }
}
