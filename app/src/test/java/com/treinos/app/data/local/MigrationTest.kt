package com.treinos.app.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
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

    private fun SupportSQLiteDatabase.rowCount(table: String): Int =
        query("SELECT COUNT(*) FROM $table").use { cursor ->
            assertTrue(cursor.moveToFirst())
            cursor.getInt(0)
        }
}
