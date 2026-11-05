# H3-6 — Perfil no backup JSON e guarda de tamanho

## Status

| Campo | Valor |
|---|---|
| Item do roadmap | `H3-6` |
| MoSCoW | `M` |
| Spec aprovada em | `aprovada` (proposta de 2026-11-05) |
| Implementação | `concluída` em 2026-11-05 — `./scripts/verify.sh` verde (`VERIFICAÇÃO COMPLETA OK — build, testes, lint e assinatura`) |
| Depende de | H1-4 (backup JSON), H1-5 (perfil), H1-7 (restauração) |

## Desvios documentados

Implementada com três desvios em relação à redação original:

1. **`sex` não entrou nesta spec.** A tabela de contrato listava
   `BackupProfile.sex` e `ProfileStore.sex`. Deixamos os campos de fora: quem
   cria o campo é a H3-7, e o mecanismo de compatibilidade já garante isso sem
   novo bump — `profile` tem default `null`, o export usa `encodeDefaults` e o
   import usa `ignoreUnknownKeys`. Adicionar `sex` depois é uma linha no DTO e
   uma no `ProfileStore`.
2. **Codec explícito em vez de `Json { ... }` inline.** Criamos
   `app/src/main/java/com/treinos/app/data/backup/BackupCodec.kt` com
   `backupExportEncoder` (`encodeDefaults = true`) e `backupImportDecoder`
   (`ignoreUnknownKeys = true`). `WorkoutRepository.exportBackup` e
   `AppViewModel.prepareImport` usam essas duas instâncias. Motivo: o teste que
   cobria a regra 10 só passou a valer quando export e import passaram a usar
   exatamente os mesmos `Json` do código de produção — com blocos inline, o teste
   codificava com um JSON e decodificava com outro e a divergência ficava invisível.
3. **Guarda de tamanho em `BackupSizeGuard.kt`.** Além de chamar
   `BackupValidator.validateSize` quando o provedor informa o tamanho, a leitura
   limitada (chunks de 8 KiB, retorna `null` ao estourar) mora em
   `app/src/main/java/com/treinos/app/data/backup/BackupSizeGuard.kt`, testada em
   `BackupSizeGuardTest.kt`. `TreinosApp.importLauncher` consulta
   `OpenableColumns.SIZE` e, se ausente, delega para `readLimited`.

Os nomes dos testes criados diferem dos sugeridos em "Testes a criar"; a
cobertura é a mesma (round-trip v3, v2 sem perfil, chave desconhecida, faixas do
perfil, NaN, perfil todo nulo, import inválido não toca nas preferências, limite
de tamanho com e sem tamanho conhecido).

## Objetivo

Quem restaura um backup hoje recebe planos, sessões e séries — e perde peso e
altura, porque o perfil nunca entrou no arquivo. Esta spec coloca o perfil no
backup e liga a guarda de tamanho que a H1-4 especificou mas nunca ativou.

## Contexto verificado

- `BackupFile` tem apenas `schemaVersion/exportedAt/app/deviceId/plans/sessions`
  (`app/src/main/java/com/treinos/app/data/backup/BackupDtos.kt:5-13`).
- O export grava `schemaVersion = 2`
  (`app/src/main/java/com/treinos/app/data/WorkoutRepository.kt:570`) e lê só
  `preferences.deviceId` (`:577`).
- O import aplica **somente** `deviceId`
  (`app/src/main/java/com/treinos/app/data/WorkoutRepository.kt:624`). Perfil não
  volta.
- Perfil mora em `SharedPreferences`
  (`app/src/main/java/com/treinos/app/data/prefs/AppPreferences.kt:49` e `:59`),
  chaves `body_weight_kg` / `height_cm` (`:87-88`).
- `BackupValidator.validateSize` existe
  (`app/src/main/java/com/treinos/app/data/backup/BackupValidator.kt:20`), tem
  teste, e **não é chamado por nenhum código de produção**. O import lê o stream
  inteiro antes de validar
  (`app/src/main/java/com/treinos/app/ui/TreinosApp.kt:126`).
- O import decodifica com `Json` default
  (`app/src/main/java/com/treinos/app/ui/AppViewModel.kt:992`), que **rejeita**
  chave desconhecida — a H1-4 (§"Contrato", linhas 147-156) diz que chaves
  desconhecidas devem ser ignoradas. Divergência spec↔código.

## Fora de escopo

- Upload automático, agendamento ou qualquer nuvem (H3-8/H3-9).
- Criptografia, senha ou compressão do arquivo (permanece fora, como na H1-4).
- `backendUrl`, `lastSyncAt`, `apiToken` ou qualquer segredo no backup — proibido
  pela H1-4 e pela regra 12 da H3-4.
- Histórico de peso corporal ao longo do tempo (H1-5 mantém: sem gráfico).
- Mudança de schema Room — o perfil não é tabela.
- Merge de perfis: a importação substitui, como já substitui o resto.

## Arquivos e símbolos que devem mudar

| Arquivo | Mudança |
|---|---|
| `app/src/main/java/com/treinos/app/data/backup/BackupDtos.kt` | Novo `BackupProfile`; `BackupFile.profile: BackupProfile? = null` |
| `app/src/main/java/com/treinos/app/data/WorkoutRepository.kt` | `exportBackup` (`:526`) lê perfil das preferências e grava `schemaVersion = 3`; `importBackup` (`:590`) restaura o perfil |
| `app/src/main/java/com/treinos/app/data/RestoreContracts.kt` | Novo `interface ProfileStore` ao lado de `DeviceIdStore` (`:25`) |
| `app/src/main/java/com/treinos/app/data/prefs/AppPreferences.kt` | Implementar `ProfileStore` (getters/setters já existem em `:49` e `:59`) |
| `app/src/main/java/com/treinos/app/data/backup/BackupValidator.kt` | `SUPPORTED_VERSIONS = setOf(1, 2, 3)` (`:13`) + validar faixas do perfil |
| `app/src/main/java/com/treinos/app/ui/AppViewModel.kt` | `prepareImport` (`:986`) decodifica com `Json { ignoreUnknownKeys = true }` |
| `app/src/main/java/com/treinos/app/ui/TreinosApp.kt` | `importLauncher` (`:116`) checa tamanho antes de ler o conteúdo |
| `app/src/main/java/com/treinos/app/ui/screens/SettingsScreen.kt` | Texto de ajuda do card "Backup e restauração (JSON)" passa a dizer que o perfil vai no arquivo |
| `app/src/test/java/com/treinos/app/data/backup/BackupSerializationTest.kt` | Round-trip v3, v2 sem perfil, compatibilidade nos dois sentidos |
| `app/src/test/java/com/treinos/app/data/backup/BackupValidatorTest.kt` | Versão 3 aceita, 4 rejeitada, faixas do perfil |
| `app/src/test/java/com/treinos/app/data/backup/BackupImportTest.kt` | Perfil restaurado; falha não toca nas preferências |
| `app/src/test/java/com/treinos/app/ui/BackupSizeGuardTest.kt` | **Novo.** Rejeita arquivo acima de 25 MB, aceita tamanho desconhecido com leitura limitada |

Não edite `shared/` nem `com.treinos.shared/` (legado).

## Modelo de dados / contrato

```kotlin
// BackupDtos.kt
@Serializable
data class BackupFile(
    val schemaVersion: Int,
    val exportedAt: String,
    val app: BackupApp,
    val deviceId: String,
    val plans: List<BackupPlan>,
    val sessions: List<BackupSession>,
    val profile: BackupProfile? = null,
)

@Serializable
data class BackupProfile(
    val bodyWeightKg: Double? = null,
    val heightCm: Int? = null,
    val sex: String? = null,          // H3-7 (não implementado nesta spec)
)

// RestoreContracts.kt
interface ProfileStore {
    var bodyWeightKg: Double?
    var heightCm: Int?
    var sex: String?                  // H3-7
}
```

`importBackup(payload: BackupFile, deviceIdStore: DeviceIdStore = preferences,
profileStore: ProfileStore = preferences)`.

Versões:

| `schemaVersion` | Origem | Importação |
|---|---|---|
| 1 | app antigo | aceita; `profile` ausente → preferências locais intocadas |
| 2 | app atual | aceita; idem |
| 3 | a partir desta spec | `profile` sempre presente (campos podem ser `null`) |
| ≥ 4 | futuro | rejeita com "versão incompatível", sem escrever nada |

A H3-7 adiciona `sex` dentro de `BackupProfile` **sem** novo bump: o campo tem
default `null`, então um arquivo v3 escrito antes de `sex` existir decodifica, e
um v3 escrito depois decodifica em app que ainda não conhece `sex` porque o
decoder passa a usar `ignoreUnknownKeys = true`.

Guarda de tamanho:

```kotlin
// TreinosApp.kt — antes de ler o conteúdo
val size = contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)
    ?.use { c -> if (c.moveToFirst()) c.getLong(0).takeIf { it >= 0 } else null }
```

- `size != null && size > MAX_FILE_BYTES` → erro, não lê.
- `size == null` (provedor de nuvem que não reporta) → ler no máximo
  `MAX_FILE_BYTES + 1` bytes com contador; estourou → erro.
- `MAX_FILE_BYTES` (25 MB) deixa de ser constante órfã: `BackupValidator` expõe o
  limite e o chamador usa `validateSize`.

## Regras e casos de borda

1. Export sempre escreve `profile`; campo não informado vira `null` explícito
   (`encodeDefaults = true` já está ativo em `WorkoutRepository.kt:582`).
2. Import de v1/v2 (`profile == null`) **não limpa** o perfil local. Restaurar
   um backup velho não pode apagar dados que o arquivo nunca contém.
3. Import de v3 com `profile` presente sobrescreve cada campo exatamente como no
   arquivo — inclusive `null`, que limpa. É a diferença entre "arquivo não fala
   do assunto" e "arquivo diz que não está informado".
4. Perfil é escrito **depois** de `replaceAllData` ter cometido a transação, na
   mesma janela em que o `deviceId` já é aplicado (`WorkoutRepository.kt:624`).
   Validação ou escrita do banco falhando, nada muda: nem Room, nem `deviceId`,
   nem perfil.
5. `bodyWeightKg` fora de 30–300, `heightCm` fora de 120–250, `sex` fora do
   conjunto permitido → backup inválido, mensagem da H1-7, dados intactos.
   Reaproveitar as faixas da H1-5, não inventar novas.
6. NaN/Infinity em `bodyWeightKg` → inválido.
7. `profile` com todos os campos nulos é válido (usuário sem perfil).
8. Arquivo ≥ 25 MB → rejeitado **antes** do decode; a mensagem é de tamanho, não
   "arquivo inválido".
9. Arquivo de tamanho desconhecido e conteúdo acima do limite → rejeitado durante
   a leitura limitada.
10. JSON com chave que o app não conhece (ex.: `avatar` escrito por uma versão
    futura com v3) → importado normalmente, chave ignorada.
11. `app.packageName` divergente continua barrando primeiro — a ordem de validação
    da H1-4 não muda.
12. Texto de Ajustes precisa dizer que o arquivo contém peso, altura e (após a
    H3-7) sexo, para não induzir o usuário a compartilhar o JSON sem saber.

## Critérios de aceite

1. **Dado** peso 82,5 e altura 178 salvos, **quando** exporta, **então** o JSON
   tem `schemaVersion: 3` e `profile.bodyWeightKg = 82.5`, `profile.heightCm = 178`.
2. **Dado** nenhum perfil salvo, **quando** exporta, **então** `profile` está
   presente com os campos em `null`.
3. **Dado** um app com perfil preenchido, **quando** importa um v3 sem perfil,
   **então** peso e altura ficam `null` (o arquivo disse "não informado").
4. **Dado** um app com perfil preenchido, **quando** importa um v2 válido,
   **então** planos/sessões são substituídos e peso/altura **continuam** salvos.
5. **Dado** um v3 com peso e altura, **quando** importa em app recém-instalado,
   **então** a tela de geração já abre com os campos preenchidos.
6. **Dado** `profile.bodyWeightKg = 29`, **quando** importa, **então** dá erro de
   validação e o banco não muda.
7. **Dado** um arquivo de 30 MB, **quando** importa, **então** vê a mensagem de
   tamanho e nenhum byte é decodificado como JSON.
8. **Dado** um URI cujo tamanho o provedor não informa, com 30 MB de conteúdo,
   **quando** importa, **então** a leitura limitada rejeita antes do estouro de
   memória.
9. **Dado** um v3 com uma chave extra desconhecida, **quando** importa, **então**
   o import funciona.
10. **Dado** um `schemaVersion: 4`, **então** a mensagem menciona as versões
    suportadas (1, 2 ou 3) e nada muda.

## Testes a criar

| Arquivo | Testes |
|---|---|
| `BackupSerializationTest.kt` | `export_inclui_perfil_v3`, `import_v2_sem_perfil_vira_null`, `v3_com_chave_desconhecida_decodifica`, `v3_perfil_com_null_preserva_null` |
| `BackupValidatorTest.kt` | `aceita_versao_tres`, `rejeita_versao_quatro`, `rejeita_peso_fora_da_faixa_no_perfil`, `rejeita_altura_fora_da_faixa_no_perfil`, `aceita_perfil_totalmente_nulo` |
| `BackupImportTest.kt` | `restaura_perfil_no_store`, `v2_nao_altera_perfil`, `falha_de_validacao_mantem_perfil`, `perfil_so_escreve_depois_do_commit` |
| `BackupSizeGuardTest.kt` | `rejeita_acima_do_limite`, `tamanho_desconhecido_usa_leitura_limitada`, `aceita_abaixo_do_limite` |

## Verificação manual

1. Preencha 82,5 kg / 178 cm, exporte, abra o JSON e confira `profile`.
2. Zere os campos em Ajustes → Gerar, exporte e confira `profile` com `null`.
3. Importe o backup do passo 1 num app com campos zerados; confira que voltaram.
4. Importe um backup gerado por um app antigo (v2, se ainda houver) e confirme
   que o perfil local **não** foi apagado.
5. Renomeie um JSON grande (>25 MB) para `.json` e importe: a mensagem deve ser
   de tamanho, não de formato.
6. Gere um backup, edite o JSON adicionando `"chaveNova": 1` e mantenha v3:
   importe e confirme sucesso.

## Comando de verificação

```bash
./scripts/verify.sh
```

## Riscos e rollback

- **Exposição de dados pessoais:** o arquivo passa a conter peso/altura. Mitiga
  o risco o texto de Ajustes (regra 12) e a H3-10, que mantém o auto-backup do
  Android fora do caminho.
- **Quebra de compatibilidade:** v1/v2 continuam aceitos; o único risco é um app
  antigo tentando abrir um v3, que falha com a mensagem de versão — comportamento
  correto e já coberto pela H1-7.
- **Rollback:** devolver `schemaVersion` para 2 e remover `profile` do DTO. Um v3
  já exportado volta a ser rejeitado pela porta da versão, sem corromper nada;
  nenhum dado do usuário é perdido no rollback, porque o banco não mudou.
