package com.treinos.app.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.treinos.app.ui.ActivityLogInput
import com.treinos.app.ui.ActivityModality
import com.treinos.app.ui.toManualSession
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ManualSessionDaoTest {

    private lateinit var database: AppDatabase
    private lateinit var dao: WorkoutDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.workoutDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun swimInput(id: Long = 0L) = ActivityLogInput(
        modality = ActivityModality.SWIM,
        title = "Natação do professor",
        startedAt = 1_760_000_000_000L,
        durationMinutes = 45,
        distanceMeters = 1_500.0,
        poolLengthMeters = 25,
        stroke = "crawl",
        rpe = 7.0,
        notes = "8 x 100 m",
    ).toManualSession(id)

    @Test
    fun insere_sessao_manual_sem_exercicios() = runTest {
        val sessionId = dao.insertSession(swimInput())

        assertNotNull(dao.getSession(sessionId))
        assertEquals(0, dao.getAllSessionExercises().count { it.sessionId == sessionId })
        assertEquals(0, dao.completedSetsForSession(sessionId))
        assertEquals(0L, dao.getSession(sessionId)!!.planId)
        assertEquals(0L, dao.getSession(sessionId)!!.dayId)
        assertEquals(MANUAL_SESSION_SOURCE, dao.getSession(sessionId)!!.source)
    }

    @Test
    fun le_sessao_manual() = runTest {
        dao.insertSession(swimInput())
        dao.insertSession(
            swimInput().copy(id = 0L, modality = "run", title = "Corrida", dayName = "Corrida")
        )

        val sessions = dao.observeSessionEntities().first()

        assertEquals(2, sessions.size)
        val swim = sessions.first { it.modality == "swim" }
        assertEquals(1_500.0, swim.totalDistanceMeters!!, 0.0)
        assertEquals(25, swim.poolLengthMeters)
        assertEquals("crawl", swim.stroke)
        assertEquals(7.0, swim.sessionRpe!!, 0.0)
        assertEquals("8 x 100 m", swim.notes)
        assertEquals("Natação do professor", swim.title)
    }

    @Test
    fun atualiza_sessao_manual() = runTest {
        val sessionId = dao.insertSession(swimInput())

        dao.updateSession(swimInput(id = sessionId).copy(totalDistanceMeters = 1_200.0))

        val sessions = dao.getAllSessionEntities()
        assertEquals(1, sessions.size)
        assertEquals(1_200.0, sessions.first().totalDistanceMeters!!, 0.0)
        assertEquals(sessionId, sessions.first().id)
    }

    @Test
    fun remove_sessao_manual() = runTest {
        val sessionId = dao.insertSession(swimInput())

        dao.deleteSession(sessionId)

        assertNull(dao.getSession(sessionId))
        assertTrue(dao.getAllSessionEntities().isEmpty())
    }
}
