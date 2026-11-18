package com.treinos.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class SyncOutboxTest {
    @Test
    fun mutation_id_e_deterministico_para_mesmo_conteudo() {
        val first = stableSyncMutationId("plan", "12", "upsert", """{"name":"P"}""")
        val second = stableSyncMutationId("plan", "12", "upsert", """{"name":"P"}""")

        assertEquals(first, second)
    }

    @Test
    fun conteudo_diferente_muda_mutation_id() {
        val first = stableSyncMutationId("plan", "12", "upsert", """{"name":"P"}""")
        val changed = stableSyncMutationId("plan", "12", "upsert", """{"name":"Novo"}""")

        assertNotEquals(first, changed)
    }

    @Test
    fun delete_tem_id_distinto_de_upsert() {
        val upsert = stableSyncMutationId("session", "8", "upsert", "{}")
        val delete = stableSyncMutationId("session", "8", "delete", "{}")

        assertNotEquals(upsert, delete)
    }
}
