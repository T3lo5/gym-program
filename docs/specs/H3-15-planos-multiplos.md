# H3-15 — Múltiplos planos coexistindo na Home

## Status

| Campo | Valor |
|---|---|
| Item do roadmap | `H3-15` |
| MoSCoW | `M` |
| Spec aprovada em | `2026-11-06` |
| Implementação | `concluída` |

## Objetivo

Gerar um treino extra (ex.: abdômen, casa) sem perder o acesso ao plano principal
de academia: todos os planos ativos ficam visíveis e executáveis na Home.

## Contexto (estado atual verificado)

- `WorkoutPlanEntity` já tem `isActive` e o banco já guarda vários planos;
  `WorkoutDao.replaceActivePlan` (L73–91) **desativa** os anteriores, não apaga.
- A UI só enxerga um plano: `observeActivePlanEntity()` (L15–16) com `LIMIT 1`.
- Backup (`BackupFile.plans: List<BackupPlan>`) e sync (`buildSyncPayload` envia
  `getAllPlanEntities()`) **já viajam com todos os planos**.
- `mapSnapshot` (WorkoutRepository L399–402) normaliza o restore para **um único
  ativo** — é isso que impede a coexistência após restaurar.
- Histórico já aceita sessões de qualquer `planId` (sem FK; nomes denormalizados).
- Decisão de produto (2026-11-06): todos os planos acessíveis na Home, sem
  "troca de plano ativo"; planos complementares são **só de força** (H3-16).

## Fora de escopo

- Geração com foco exclusivo (abdômen) e ambiente casa/peso corporal — é a
  **H3-16**, que depende desta spec. Aqui o modo "complementar" existe, mas a
  geração ainda usa os campos atuais da GenerateScreen.
- Renomear planos, reordenar, duplicar ou mesclar planos.
- Seletor de planos no Wear OS (o relógio recebe a sessão em execução; nada muda).
- Revisão por IA (`reviewPlan`) de planos complementares — continua apontando
  para o plano principal (`kind = "main"`).
- Apagar o plano principal pela Home (só complementares são excluíveis aqui).
- Limite máximo de planos ativos (sem cap nesta spec; a exclusão manual resolve).

## Arquivos e símbolos que devem mudar

| Arquivo | Mudança |
|---|---|
| `app/src/main/java/com/treinos/app/data/local/Entities.kt` | `WorkoutPlanEntity` ganha `@ColumnInfo(defaultValue = "main") val kind: String = "main"` (`main` \| `extra`) |
| `app/src/main/java/com/treinos/app/data/local/AppDatabase.kt` | `version = 3`, `MIGRATION_2_3` aditiva (`ALTER TABLE workout_plans ADD COLUMN kind TEXT NOT NULL DEFAULT 'main'`); schema `3.json` versionado |
| `app/src/main/java/com/treinos/app/data/local/WorkoutDao.kt` | Novo `observeActivePlanEntities(): Flow<List<WorkoutPlanEntity>>` (`isActive = 1`, main primeiro, depois `updatedAt DESC`); `replaceActivePlan` passa a desativar **só planos do mesmo `kind`** (`deactivateByKind`); novo `insertPlanWithDays` transacional sem desativar nada (extras); `deletePlan(planId)` |
| `app/src/main/java/com/treinos/app/data/WorkoutRepository.kt` | `observeActivePlans(): Flow<List<WorkoutPlanWithDetails>>`; `saveGeneratedPlan(response, kind)` — `main` substitui o main anterior, `extra` só insere; `deleteExtraPlan(planId)` com guarda `kind == "extra"`; `mapSnapshot` preserva todos os `isActive` (remove a normalização L399–402; se nenhum vier ativo, ativa o primeiro `main`) e mapeia `kind`; `buildSyncPayload` inclui `kind` |
| `app/src/main/java/com/treinos/app/ui/AppViewModel.kt` | `activePlans: StateFlow<List<WorkoutPlanWithDetails>>`, `selectedPlanId: StateFlow<Long?>`, `selectedPlan` derivado (padrão: primeiro `main`); `generatePlan` repassa o modo; `selectPlan(id)`, `deleteExtraPlan(id)`; `activePlan` atual vira o `selectedPlan` para os consumidores existentes |
| `app/src/main/java/com/treinos/app/ui/screens/HomeScreen.kt` | Seletor de planos (chips com o nome) acima das abas de modalidade quando há > 1 plano ativo; card de revisão/deload e "Gerar novo treino" operam sobre o plano **selecionado**; ação "Excluir plano" (só `extra`) com diálogo de confirmação |
| `app/src/main/java/com/treinos/app/ui/screens/GenerateScreen.kt` | Alternância "Substituir meu plano principal" / "Adicionar plano complementar" no início do formulário; o modo viaja no `onGenerate` |
| `app/src/main/java/com/treinos/app/data/remote/BackendDtos.kt` | `SyncPlanDto` ganha `kind: String = "main"` (aditivo) |
| `app/src/main/java/com/treinos/app/data/backup/BackupDtos.kt` | `BackupPlan` ganha `kind: String = "main"` (aditivo, **sem bump** de `schemaVersion`, precedente H3-7) |
| `app/src/main/java/com/treinos/app/data/backup/BackupMapper.kt` | `toDto`/`toEntity` mapeiam `kind` |
| `app/src/main/java/com/treinos/app/data/backup/BackupValidator.kt` | `kind` ausente → `"main"`; valor fora de `{main, extra}` rejeita o arquivo inteiro (padrão dos enums) |
| `back/app/database.py` | Coluna aditiva `kind TEXT NOT NULL DEFAULT 'main'` na tabela de planos (migração aditiva, precedente H1-6); `sync_snapshot` e `get_sync_download_data` carregam `kind` |
| `back/app/main.py` | DTOs de sync com `kind` opcional (default `"main"`) |
| `app/src/test/java/com/treinos/app/data/local/MigrationTest.kt` | Teste 2→3 preservando linhas |
| `app/src/test/java/com/treinos/app/data/local/WorkoutDaoTest.kt` | Múltiplos ativos, replace por kind, delete de extra |
| `app/src/test/java/com/treinos/app/data/RestoreDownloadTest.kt` | Restore preserva múltiplos ativos |
| `app/src/test/java/com/treinos/app/data/backup/Backup*Test.kt` | Round-trip e validação de `kind` |
| `back/tests/test_sync_contract.py` | Round-trip de `kind` no snapshot |

## Modelo de dados / contrato

```text
Room v2 → v3 (aditiva, sem destructive fallback):
  ALTER TABLE workout_plans ADD COLUMN kind TEXT NOT NULL DEFAULT 'main';
  -- valores: 'main' (plano principal) | 'extra' (complementar)

Backup JSON (schemaVersion continua 3):
  plans[i].kind: "main" | "extra"   // ausente em arquivos antigos → "main"
  plans[i].isActive: boolean         // vários true agora são válidos

Sync (app ↔ backend), aditivo nos dois sentidos:
  SyncPlanDto.kind: String = "main"
  snapshot sem kind (app/backend antigo) → "main"

Semântica de isActive:
  - pode haver N planos ativos (1 main + N extras);
  - gerar main desativa só os mains anteriores;
  - gerar extra não desativa nada;
  - restore preserva os flags; se nenhum ativo, ativa o primeiro main.
```

## Regras e casos de borda

- Gerar um novo plano **principal** mantém todos os extras ativos e executáveis.
- Gerar um complementar **nunca** desativa nem apaga plano algum.
- Excluir um extra apaga plano/dias/exercícios (CASCADE) e **preserva as sessões
  do histórico** (sem FK; `planName`/`dayName` já denormalizados).
- Excluir extra exige confirmação; o plano `main` não oferece essa ação.
- Seleção padrão na Home: primeiro `main` ativo; se o plano selecionado for
  excluído, a seleção volta para o main.
- Restore de snapshot antigo (sem `kind`, um único ativo): comportamento
  idêntico ao atual — nenhum plano vira `extra` sozinho.
- Snapshot vazio continua não substituindo os dados locais (H1-7, intocado).
- `kind` inválido no backup rejeita o arquivo **antes** de tocar no banco.
- App antigo importing backup novo: `ignoreUnknownKeys` já ignora `kind`
  (BackupCodec); app novo importando backup antigo: default `"main"`.
- Migração 2→3: todo plano existente vira `main`; o ativo continua ativo.

## Critérios de aceite

1. **Dado** um plano principal de academia ativo, **quando** o usuário gera um
   plano em modo "complementar", **então** a Home mostra os dois planos e ambos
   podem ser executados, com o principal intacto.
2. **Dado** dois planos ativos, **quando** o usuário gera um novo plano
   principal, **então** o main anterior é desativado e o complementar continua
   ativo e selecionável.
3. **Dado** um extra com sessões registradas, **quando** o usuário o exclui e
   confirma, **então** o plano some da Home e as sessões continuam no histórico
   com o nome do plano.
4. **Dado** um backup/export com 1 main + 2 extras ativos, **quando** exportado
   e importado (ou sincronizado e restaurado), **então** os três planos voltam
   ativos, com os mesmos `kind`.
5. **Dado** um banco Room v2 com planos, **quando** o app atualiza para v3,
   **então** todas as linhas sobrevivem com `kind = 'main'` e o ativo segue ativo.
6. **Dado** um backup com `kind: "qualquer-coisa"`, **quando** importado,
   **então** a importação falha com erro claro e nada é escrito.

## Testes a criar

| Arquivo | Testes |
|---|---|
| `app/src/test/java/com/treinos/app/data/local/MigrationTest.kt` | `migration2To3_preserva_planos_e_ativo` |
| `app/src/test/java/com/treinos/app/data/local/WorkoutDaoTest.kt` | `multiplos_planos_ativos`, `replace_desativa_so_mesmo_kind`, `insert_extra_nao_desativa`, `delete_extra_preserva_sessoes` |
| `app/src/test/java/com/treinos/app/data/RestoreDownloadTest.kt` | `restore_preserva_multiplos_ativos`, `restore_sem_ativo_ativa_primeiro_main`, `restore_snapshot_antigo_sem_kind` |
| `app/src/test/java/com/treinos/app/data/WorkoutRepositorySessionTest.kt` | `gerar_extra_nao_toca_no_main`, `gerar_main_mantem_extras` |
| `app/src/test/java/com/treinos/app/data/backup/BackupSerializationTest.kt` | `kind_round_trip`, `kind_ausente_vira_main` |
| `app/src/test/java/com/treinos/app/data/backup/BackupValidatorTest.kt` | `kind_invalido_rejeita_arquivo` |
| `app/src/test/java/com/treinos/app/data/backup/BackupMapperTest.kt` | `kind_mapeado_dto_entity` |
| `app/src/test/java/com/treinos/app/ui/PlanSelectionTest.kt` (novo, lógica pura) | `padrao_primeiro_main`, `selecao_sobrevive_a_geracao`, `excluir_selecionado_volta_ao_main` |
| `back/tests/test_sync_contract.py` | `test_kind_round_trip`, `test_snapshot_sem_kind_vira_main` |

## Verificação manual

1. Gerar plano de academia → gerar segundo plano em modo complementar →
   conferir os dois chips na Home e executar um dia de cada.
2. Excluir o complementar (confirmar) → conferir histórico preservado.
3. Exportar backup → importar em seguida → conferir os dois planos ativos.
4. "Restaurar do backend" com snapshot multi-plano → mesmos planos ativos.
5. Caminho de erro: importar backup com `kind` inválido → mensagem de erro,
   dados locais intactos.

## Comando de verificação

```bash
./scripts/verify.sh
```

## Riscos e rollback

- **Migração Room**: aditiva com default; teste 2→3 no gate (precedente H1-6).
  Rollback = reverter o commit; o banco v3 não abre em app v2 — documentar que
  downgrade exige restore de backup (mesma situação da migração 1→2).
- **Restore multi-ativo muda invariante antiga**: testes de restore antigos que
  assumem um único ativo precisam ser atualizados junto, não ignorados.
- **Consumidores de `activePlan`** (progressão, Wear, revisão): apontar todos
  para o plano selecionado/main no mesmo commit; grep por `activePlan` antes de
  concluir para não deixar consumidor órfão.
- Exclusão de extra é destrutiva por natureza: confirmação obrigatória e
  sessões preservadas limitam o dano; sem exclusão de main nesta spec.