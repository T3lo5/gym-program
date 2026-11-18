package com.treinos.app.data

import java.security.MessageDigest

/** H5-1: ID estável para dedupe do mesmo conteúdo em reenviios. */
internal fun stableSyncMutationId(
    entity: String,
    entityId: String,
    operation: String,
    payloadJson: String,
): String {
    val digest = MessageDigest.getInstance("SHA-256")
        .digest("$entity|$entityId|$operation|$payloadJson".toByteArray(Charsets.UTF_8))
    return digest.joinToString("") { byte -> "%02x".format(byte) }
}
