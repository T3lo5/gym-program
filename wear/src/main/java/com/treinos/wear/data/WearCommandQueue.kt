package com.treinos.wear.data

import com.treinos.protocol.WearCommand
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

class WearCommandQueue(
    private val capacity: Int = DEFAULT_CAPACITY,
    private val json: Json = Json { ignoreUnknownKeys = true },
) {

    private val commands = ArrayDeque<WearCommand>()

    fun add(command: WearCommand) {
        commands.addLast(command)
        while (commands.size > capacity) {
            commands.removeFirst()
        }
    }

    fun removeById(commandId: String): Boolean {
        val iterator = commands.iterator()
        while (iterator.hasNext()) {
            if (iterator.next().commandId == commandId) {
                iterator.remove()
                return true
            }
        }
        return false
    }

    fun all(): List<WearCommand> = commands.toList()

    fun isEmpty(): Boolean = commands.isEmpty()

    fun size(): Int = commands.size

    fun encode(): String =
        json.encodeToString(ListSerializer(WearCommand.serializer()), commands.toList())

    companion object {
        const val DEFAULT_CAPACITY = 20

        fun decode(raw: String?, json: Json = Json { ignoreUnknownKeys = true }): WearCommandQueue {
            val queue = WearCommandQueue(json = json)
            if (raw.isNullOrBlank()) return queue
            val decoded = runCatching {
                json.decodeFromString(ListSerializer(WearCommand.serializer()), raw)
            }.getOrNull() ?: return queue
            decoded.forEach { queue.add(it) }
            return queue
        }
    }
}