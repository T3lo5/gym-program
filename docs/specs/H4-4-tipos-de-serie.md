# H4-4 — Tipos de série: aquecimento, drop set, AMRAP e RIR

## Status

| Campo | Valor |
|---|---|
| Item do roadmap | `H4-4` |
| MoSCoW | `S` |
| Versão alvo | `1.5.0` |
| Spec aprovada em | `aprovada` |
| Implementação | `concluída` |

## Objetivo

Registrar a intenção de cada série (aquecimento, drop set, AMRAP) e o esforço
em RIR, para que o aquecimento não infle volume/progresso e as séries especiais
fiquem identificáveis no treino e no histórico.

## Fora de escopo

- Contrato `:protocol` e relógio: o pulso continua registrando série normal
  (`WearCommandKind.LOG_SET`), sem tipo nem RIR.
- Gerar drop set/AMRAP automaticamente: `back/app/plan_builder.py` não conhece
  tipo de série (preenche reps/carga/descanso, sem `setType`).
- Tipo de série no plano (`WorkoutPlanExerciseEntity`): o tipo é por série, não
  por exercício.
- Recalcular PR/1RM (H4-1) usando RIR, ou qualquer heurística de "série válida
  para PR" além de ignorar warm-up (comportamento atual).
- UI de configuração global de tipos (ex.: renomear rótulos).

## Arquivos e símbolos que devem mudar

| Arquivo | Mudança |
|---|---|
| `app/src/main/java/com/treinos/app/data/local/Entities.kt` | `SetLogEntity` ganha `setType: String = "normal"` e `rir: Int? = null`; `isWarmup` permanece e passa a ser derivado na escrita |
| `app/src/main/java/com/treinos/app/data/local/AppDatabase.kt` | `version = 7` + `MIGRATION_6_7` (duas colunas aditivas + backfill `setType='warmup' WHERE isWarmup=1`), registrada em `addMigrations` |
| `app/schemas/com.treinos.app.data.local.AppDatabase/7.json` | schema exportado (fonte única versionada) |
| `app/src/main/java/com/treinos/app/data/local/WorkoutDao.kt` | `recalculateCompletedSets` (:195) passa a contar `WHERE exerciseLogId = :id AND isWarmup = 0` |
| `app/src/main/java/com/treinos/app/data/WorkoutRepository.kt` | `logSet` (:447) ganha `setType`/`rir`, grava `isWarmup = setType == "warmup"` e só chama `incrementCompletedSets` para série de trabalho; `updateSet` (:511)/`updateHistorySet` (:539) recalculam `completedSets` quando o tipo muda; `mapSnapshot` (:875) e `toSyncSnapshot` (:1187) mapeiam os campos |
| `app/src/main/java/com/treinos/app/ui/AppViewModel.kt` | `logSet` (:1325/:1347), `updateSet` (:1401) e `updateHistorySet` (:1432) carregam `setType`/`rir` |
| `app/src/main/java/com/treinos/app/ui/SetValidation.kt` | `SetInput`/`SetInputErrors` ganham `setType`/`rir`; `validateSetInput` valida RIR `0..10` e normaliza tipo desconhecido para `"normal"` |
| `app/src/main/java/com/treinos/app/ui/components/SetEditDialog.kt` | seletor de tipo (Normal/Aquecimento/Drop set/AMRAP) + campo RIR; `onConfirm` passa a receber `SetInput` |
| `app/src/main/java/com/treinos/app/ui/screens/WorkoutScreen.kt` | `onLogSet`/`onUpdateSet` (:109/:120) recebem tipo/RIR; `setDetailText` (:1044) mostra o rótulo do tipo e o RIR |
| `app/src/main/java/com/treinos/app/ui/screens/HistoryScreen.kt` | `onUpdateSet` (:119) e a folha de edição de séries repassam tipo/RIR ao `SetEditDialog` |
| `app/src/main/java/com/treinos/app/ui/TreinosApp.kt` | `onLogSet` (:352) e `onUpdateSet` (:365) ligados às assinaturas novas |
| `app/src/main/java/com/treinos/app/ui/progression/ProgressionEngine.kt` | `progressionHistory` (:168) deriva `rpe = set.rpe ?: set.rir?.let { 10.0 - it }`; warm-up continua filtrado em `bestSetPerSession` (:208) |
| `app/src/main/java/com/treinos/app/data/backup/BackupDtos.kt` | `BackupSetLog` (:128) ganha `setType: String = "normal"` e `rir: Int? = null` |
| `app/src/main/java/com/treinos/app/data/backup/BackupMapper.kt` | `toDto` (:110) e `toEntity` (:216) mapeiam os dois campos |
| `app/src/main/java/com/treinos/app/data/backup/BackupValidator.kt` | valida `setType` na whitelist e `rir` em `0..10` (junto da checagem de RPE, :288) |
| `app/src/main/java/com/treinos/app/data/remote/BackendDtos.kt` | `SyncSetLogDto` (:206, envio) e `SyncDownloadSetLogDto` (:332, restauração) ganham `setType: String = "normal"` e `rir: Int? = null` |
| `back/app/main.py` | `SyncSetLog` (:600) ganha `setType: str = "normal"` e `rir: Optional[int] = None` |
| `back/app/database.py` | `synced_set_logs` (:142) ganha `set_type`/`rir`; `_ensure_columns` (:204) cobre a tabela; INSERT (:569), SELECT (:762) e mapping (:886) |
| `app/src/main/res/values/strings.xml` + `values-en/strings.xml` | chaves novas (`workout_set_dialog_type_*`, `workout_set_dialog_rir`, `workout_set_type_warmup/drop/amrap`) — `StringParityTest` exige os dois arquivos |
| testes | ver tabela abaixo |

## Modelo de dados / contrato

```text
Room v6 → v7 (aditivo, sem destruição; padrão H3-17/H3-24/H3-42):
  set_logs:
    + setType TEXT NOT NULL DEFAULT 'normal'   -- 'normal' | 'warmup' | 'drop' | 'amrap'
    + rir     INTEGER                          -- 0..10; NULL = esforço não informado
  Backfill: UPDATE set_logs SET setType = 'warmup' WHERE isWarmup = 1

Compatibilidade:
  isWarmup continua existindo e é DERIVADO na escrita:
    isWarmup = (setType == 'warmup')
  Leitores atuais seguem usando isWarmup, sem mudança:
    HistoryMetrics.countsAsMetric (HistoryMetrics.kt:136)
    WorkoutSummary.countsAsSummarySet (WorkoutSummary.kt:57)
    ProgressionEngine.bestSetPerSession (ProgressionEngine.kt:208; filtro em :216)

Backup JSON (BackupSetLog):
  + "setType": "normal"   (default quando ausente em backups antigos)
  + "rir": null           (default quando ausente)
  O validador rejeita setType fora da whitelist e rir fora de 0..10.

Sync app → backend (SyncSetLogDto / SyncSetLog / synced_set_logs):
  + setType / set_type  TEXT NOT NULL DEFAULT 'normal'
  + rir / rir           INTEGER
  Backend novo aplica _ensure_columns; backend antigo ignora os campos extras
  (Pydantic usa os defaults) — degradação aceita e documentada.

Contrato :protocol (relógio): SEM mudança (protocolVersion = 1).
```

> **Divergência com o roadmap:** o item H4-4 diz "sem migration". Verificado o
> schema (`app/schemas/.../6.json`), `set_logs` não tem coluna para tipo nem
> RIR — só `isWarmup`. Persistir drop set/AMRAP/RIR exige colunas aditivas, e
> Room sem `fallbackToDestructiveMigration` exige `MIGRATION_6_7`. Esta spec
> corrige a premissa. Se o dono mantiver "zero migration", o escopo cai para
> "aquecimento via `isWarmup` + RIR derivado de RPE" e drop set/AMRAP saem.

## Regras e casos de borda

- Série `warmup` **não** incrementa `completedSets` no `logSet`; o usuário pode
  registrar quantas séries de aquecimento quiser sem "completar" o exercício.
- `recalculateCompletedSets` (chamado ao excluir/editar série) conta só
  `isWarmup = 0`; virar warm-up remove a série do progresso, deixar de ser
  warm-up devolve a série ao progresso.
- `warmup` continua fora de volume, contagem de séries, progressão e PR
  (comportamento atual preservado por `isWarmup`); `drop` e `amrap` contam como
  série de trabalho em todos eles.
- `rir` vazio = `null` (não informado), nunca `0`.
- `rir` não inteiro, negativo ou > 10 → erro no diálogo e nada é gravado (mesmo
  padrão do H3-27 em `SetValidation.kt`).
- `setType` desconhecido vindo do formulário é normalizado para `"normal"`;
  vindo de backup é rejeitado pelo validador; vindo do sync cai em `"normal"`.
- `progressionHistory` usa `rpe` quando presente; sem `rpe` e com `rir`, usa
  `10 - rir` (RIR é a outra face do RPE). Warm-up continua filtrado antes disso.
- Migração preserva todas as linhas; `rir` fica `NULL` e `setType` recebe
  `'warmup'` para quem tinha `isWarmup = 1`; o restante vira `'normal'`.
- Restauração/sync normaliza a dupla: `isWarmup = (setType == "warmup")`; DTO
  antigo com `isWarmup = true` e sem `setType` vira `setType = "warmup"` (nunca
  os dois divergentes no banco).
- Backup antigo sem os campos decodifica com defaults — importar não falha.
- Sync com backend antigo: os campos extras são ignorados; restaurar do backend
  devolve `setType='normal'`/`rir=null` para essas séries (degradação aceita).

## Critérios de aceite

1. **Dado** o diálogo de série, **quando** o usuário escolhe "Aquecimento" e
   salva, **então** a série é gravada com `setType='warmup'` e `isWarmup=true`,
   sem incrementar `completedSets`.
2. **Dado** um exercício planejado 3×8-12, **quando** o usuário registra 2
   aquecimentos + 3 séries normais, **então** o card mostra 3/3 concluído e o
   volume do histórico conta apenas as 3 séries normais.
3. **Dado** o diálogo de série, **quando** o usuário escolhe "Drop set" ou
   "AMRAP" e salva, **então** a série conta como série de trabalho e aparece
   rotulada como "Drop set" / "AMRAP" no treino e no histórico.
4. **Dado** o diálogo de série, **quando** o usuário digita RIR 2, **então** a
   série é gravada com `rir=2` e a sugestão de progressão trata o esforço como
   RPE 8.
5. **Dado** um banco na v6 com séries marcadas `isWarmup=1`, **quando** a
   migração 6→7 roda, **então** todas as linhas são preservadas e essas séries
   ficam com `setType='warmup'`.
6. **Dado** o diálogo com RIR 11 (ou não inteiro), **quando** o usuário
   confirma, **então** o salvamento é bloqueado com mensagem e nada é gravado.
7. **Dado** um backup antigo sem `setType`/`rir`, **quando** importado,
   **então** as séries viram `setType='normal'`/`rir=null` sem erro.
8. **Dado** um treino com drop set e RIR, **quando** sincronizado com o
   backend e restaurado, **então** `setType` e `rir` voltam idênticos.

## Testes a criar

| Arquivo | Testes |
|---|---|
| `app/src/test/java/com/treinos/app/ui/SetValidationTest.kt` | `rir_vazio_e_null`, `rir_0_a_10_valido`, `rir_fora_da_faixa_bloqueia`, `rir_nao_inteiro_bloqueia`, `set_type_desconhecido_vira_normal` |
| `app/src/test/java/com/treinos/app/data/local/MigrationTest.kt` | `migracao_6_7_preserva_linhas_e_marca_warmup` |
| `app/src/test/java/com/treinos/app/data/WorkoutRepositorySessionTest.kt` | `logSet_warmup_nao_incrementa_completed_sets`, `logSet_drop_e_amrap_contam`, `updateSet_para_warmup_recalcula_completed_sets`, `delete_warmup_nao_altera_completed_sets` |
| `app/src/test/java/com/treinos/app/ui/progression/ProgressionEngineTest.kt` | `rir_vira_rpe_na_historia`, `warmup_com_rir_continua_ignorado` |
| `app/src/test/java/com/treinos/app/data/backup/BackupValidatorTest.kt` | `rejeita_set_type_invalido`, `rejeita_rir_fora_da_faixa`, `backup_antigo_sem_set_type_e_valido` |
| `app/src/test/java/com/treinos/app/data/backup/BackupMapperTest.kt` | `set_type_e_rir_sobrevivem_ao_round_trip` |
| `app/src/test/java/com/treinos/app/ui/screens/WorkoutScreenTest.kt` | `serie_warmup_mostra_rotulo`, `serie_drop_mostra_rotulo` |
| `back/tests/test_sync_contract.py` | `test_sync_preserva_set_type_e_rir` |

## Verificação manual

1. Iniciar um treino de força, abrir o diálogo de série, marcar "Aquecimento",
   salvar: o card mostra a série como aquecimento e o contador de séries
   planejadas **não** avança.
2. Registrar 3 séries normais: o card completa 3/3 e o botão vira "Exercício
   concluído".
3. Marcar uma série como "Drop set" e outra como "AMRAP"; conferir os rótulos
   no treino e no Histórico.
4. Digitar RIR 2 numa série e conferir que a sugestão de progressão trata como
   RPE 8; digitar RIR 11 e conferir o bloqueio com mensagem.
5. Exportar backup, reinstalar, importar: tipos e RIR continuam iguais.
6. Caminho de erro: abrir um banco v6 com aquecimentos antigos e confirmar que
   a migração preserva tudo e os aquecimentos continuam fora do volume.

## Comando de verificação

```bash
./scripts/verify.sh
```

## Riscos e rollback

- **Maior risco:** a migração 6→7 e o recálculo de `completedSets`. Colunas
  aditivas com default, backfill de warm-up e teste de migração no gate; sem
  `fallbackToDestructiveMigration`. Rollback exige voltar `version`,
  `MIGRATION_6_7`, schema `7.json`, o `recalculateCompletedSets` e os campos no
  mesmo commit.
- **Divergência de premissa:** o roadmap promete "sem migration"; esta spec
  prova que não é possível. Aprovar a spec é aprovar a migração — se não, o
  escopo precisa encolher (ver bloco de divergência).
- **Sync/backup:** esquecer um dos dois caminhos perde tipo/RIR em exportação
  ou restauração. Os testes de round-trip e de contrato cobrem os dois.
- **Progresso do treino:** mudar o que conta em `completedSets` altera o
  comportamento visível do H3-27; o teste de repositório e a verificação manual
  protegem contra regressão.
- **Backend antigo:** aparelho atualizado contra backend velho ignora os campos
  novos e restaura sem tipo/RIR — degradação aceita, sem crash (Pydantic com
  defaults).
