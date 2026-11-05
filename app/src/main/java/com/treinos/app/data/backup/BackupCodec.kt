package com.treinos.app.data.backup

import kotlinx.serialization.json.Json

/**
 * Encoder do export. `encodeDefaults` escreve os campos `null` do perfil, que é
 * como o import distingue "arquivo não fala do perfil" (v1/v2, chave ausente) de
 * "usuário não informou" (v3, `null` explícito).
 */
val backupExportEncoder = Json { encodeDefaults = true }

/**
 * Decoder usado pelo import. Chaves que esta versão não conhece são ignoradas —
 * contrato da H1-4 (§"Contrato") que o import precisa cumprir para um arquivo
 * escrito por uma versão futura não virar "arquivo inválido".
 */
val backupImportDecoder = Json { ignoreUnknownKeys = true }
