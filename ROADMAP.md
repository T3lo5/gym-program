# Roadmap — Treinos App

App pessoal de treinos Android + Wear OS, **local-first + IA sob demanda**, custo alvo R$ 0/mês.

> **Fonte de verdade do produto.** O [SPRINT-PLAN.md](docs/SPRINT-PLAN.md) é histórico
> (pré-implementação) e não deve ser usado para decidir o próximo passo.
>
> Itens do roadmap só começam com uma spec em [docs/specs/](docs/specs/). Sem spec,
> o agente deve pedir antes de implementar.

---

## 1. Visão do produto

Um app que você usa **sem conta, sem login e sem internet** para treinar. O core
cobre musculação, corrida, mobilidade e registro avulso (natação). Tudo fica no
celular (Room/SQLite). O backend (FastAPI no seu PC/Codespaces/Oracle) só entra
para:

- gerar um plano novo via IA (SenseNova) com fallback local;
- revisar/ajustar uma semana com base no histórico, quando você pedir;
- sincronizar um snapshot dos seus dados (quando você tocar em "Sincronizar agora").

O relógio (Wear OS) mostra o treino em andamento sem precisar olhar o celular.

---

## 2. Onde estamos hoje (atualizado em 2026-11-05)

| Área | Status | Evidência |
|------|--------|-----------|
| Banco local (Room) | ✅ | Planos, dias, exercícios, sessões, séries |
| Execução de treino | ✅ | Timer de descanso, séries, RPE, carga, histórico (H1-2) |
| Geração via IA | ✅ | Backend + SenseNova + fallback local |
| Sincronização app → backend | ✅ | Snapshot manual (`AppViewModel.syncNow`) |
| Restauração por `deviceId` | ✅ | `RestoreByDeviceId` valida o ID, baixa, só persiste o `deviceId` após sucesso e tem tela de confirmação em Ajustes (H1-7) |
| Download do backend | ✅ | `downloadSync` valida o snapshot antes de escrever e grava tudo em uma transação (`replaceAllData`); snapshot vazio mantém os dados locais (H1-7) |
| Testes + CI | ✅ | 35 arquivos de teste JVM (`:app`, `:protocol`, `:wear`) + gate (`./scripts/verify.sh`); Compose no Robolectric, Room em memória e contrato nos dois lados dentro do gate; instrumentados compilam no gate (H3-1) |
| Descoberta LAN | ✅ | Ajustes → "Buscar backends" (`NsdManager` em `_treinos._tcp.`); "Conectar" sonda `GET /health` e só salva a URL em 200; IP manual continua como fallback (H2-1) |
| Wear OS | ✅ | Wear OS MVP (H2-2): módulo `:wear` no build, Data Layer + tela do relógio, dois APKs no gate |
| Backup/restore do usuário | ✅ | Exportar/importar ligados aos file pickers (SAF), pré-visualização + confirmação e `deviceId` do arquivo aplicado só após importação válida (H1-7) |
| Perfil no backup JSON | ✅ | `BackupProfile` no `schemaVersion = 3` (peso, altura e sexo viajam no arquivo; v1/v2 continuam aceitos e **não** limpam o perfil local); guarda de 25 MB ligada antes de ler o stream, com leitura limitada quando o provedor não informa o tamanho (H3-6, H3-7) |
| Sexo opcional no perfil | ✅ | Seletor em "Seu perfil (opcional)" com `Feminino`/`Masculino`/`Outro`/`Prefiro não informar`, salvo em `AppPreferences.sex` e enviado só quando preenchido; aparece no prompt da IA e **não** muda descanso, RPE, séries nem cargas (H3-7) |
| Backup na nuvem do usuário | 🟡 | O SAF já lista Drive/OneDrive/Dropbox no seletor (nenhum SDK, nenhuma credencial); falta validar com provedores reais e escrever o texto de ajuda (H3-8) |
| Backup automático do Android | 🟡 | `allowBackup="true"` sem `fullBackupContent`/`dataExtractionRules` no manifest; os XMLs apontam `treinos_database` e o banco é `treinos.db` (H3-10) |
| Imagem/vídeo de exercício | ✅/❌ | Imagens via catálogo (H1-3); vídeo não |
| Migrações Room | ✅ | Room v2 com `MIGRATION_1_2` + `MigrationTest` (1→2 preservando linhas); `fallbackToDestructiveMigration` removido (H1-6) |
| Registro avulso (natação) | ✅ | `ActivityLog` valida e grava sessão manual (`source = "manual"`, `planId = dayId = 0`, zero exercícios); tela de registro/edição, card de atividade e filtro por modalidade no histórico, exclusão com confirmação (H2-5) |
| Plano híbrido / revisão por IA | ✅ | H2-6 (concluído) |
| Mobilidade / alongamento | ✅ | `MobilityRoutine`/`MobilityRoutineTemplates`, `MobilityScreen` com timer/pausa/avanço, atalho na Home e sessão `modality = "mobility"` no histórico; testes `MobilityRoutineTest`/`MobilitySessionTest` (H2-7) |
| Polimento visual / animações | ✅ | `Motion.kt` + transições em `TreinosApp`, tema/estados em `Theme`/`Common`; testes `MotionTest` (H3-2) |
| Acessibilidade | ✅ | `A11y.kt`/`Contrast.kt` + semântica TalkBack/foco/fonte/contraste nas telas principais e componentes; testes `A11yTest`/`ContrastTest` (H3-3) |
| Testes de UI/Room/contrato | ✅ | Compose no Robolectric dentro do gate (`HistoryScreenTest`, `BackupFlowUiTest`), Room em memória (`WorkoutDaoTest`), contrato `contracts/generate_response.json` nos dois lados, `assembleDebugAndroidTest` no gate e cobertura com `COVERAGE=1` (H3-1) |
| Periodização e progressão de carga | ✅ | `ProgressionEngine` sugere carga/reps pelo melhor série das sessões dos últimos 42 dias (incremento por equipamento + RPE), `ReviewSchedule` adia `nextReviewAt` sem antecipar, card de revisão atrasada e aviso de deload na Home, sugestão pré-preenche o diálogo de série; testes `ProgressionEngineTest`/`ReviewScheduleTest` (H3-5) |
| Autenticação / login | ❌ | `deviceId` **não** é autenticação; é identificador de restore |

> A tabela envelhece rápido. Ao entregar um item, mova-o para §4.1 e atualize esta
> tabela no mesmo commit.

---

## 3. Princípios que norteiam o roadmap

1. **Local-first:** o app funciona 100% offline; a nuvem é opcional.
2. **Zero conta:** nunca exigir login/cadastro para usar o core.
3. **Custo R$ 0:** backend roda no seu PC ou em tiers gratuitos (Oracle/Codespaces).
4. **Privacidade:** chave da SenseNova fica só no backend; dados pessoais não saem do aparelho salvo quando você sincroniza.
5. **UI/UX primeiro:** o app precisa ser rápido de usar entre séries (toques mínimos, alvos grandes, leitura à luz do sol no relógio).
6. **Modalidade é dimensão, não fork:** academia, corrida, natação e mobilidade compartilham plano, histórico, backup e sync.

---

## 4. Roadmap por horizonte

Priorização MoSCoW: **M** = Must, **S** = Should, **C** = Could, **W** = Won't now.

### 4.1 Concluído

| ID | Item | Evidência |
|----|------|-----------|
| H1-1 | Recuperação por `deviceId` | `AppPreferences.deviceId`, `AppViewModel.downloadNow`, `WorkoutRepository.downloadSync`; [spec](docs/specs/H1-1-device-restore.md) |
| H1-2 | Execução por séries (não circuito) | `WorkoutScreen` registra série, `AppViewModel.logSet`, `ExerciseProgress` + testes; [spec](docs/specs/H1-2-set-execution.md) |
| H1-3 | Imagens dos exercícios | `ExerciseMediaRepository`, `ExerciseImage`, `ExerciseDetailSheet`, miniaturas em `WorkoutScreen`/`HistoryScreen`, botão “Atualizar catálogo” em `SettingsScreen`, `CatalogResponseDto`, Coil + cache 7 dias + testes; [spec](docs/specs/H1-3-exercise-media.md) |
| H1-4 | Exportar/importar backup JSON (manual) | Lógica de backup, validação, mapper e `replaceAllData`; botões e `deviceId` ligados em H1-7; [spec](docs/specs/H1-4-backup-json.md) |
| H1-5 | Peso/altura na geração | `ProfileValidator`, `profile_adjustments`, `suggest_initial_load`, prompt com perfil; [spec](docs/specs/H1-5-profile.md) |
| H2-2 | Wear OS MVP (Data Layer) | Módulo `:wear` no build; Data Layer envia sessão para o relógio; tela com timer e próximo exercício; dois APKs no gate; [spec](docs/specs/H2-2-wear-os.md) |
| H2-3 | Download/restauração do backend | `BackendClient.syncDownload`, `WorkoutRepository.downloadSync`, botão "Restaurar do backend"; [spec](docs/specs/H2-3-backend-download.md) |
| H1-6 | Fundação de dados: migração, backup v2 e sync | `AppDatabase.MIGRATION_1_2` sem `fallbackToDestructiveMigration`; schema `2.json` versionado e usado pelo `MigrationTest`; backup v2 (importa v1) com enums validados; sync com campos aditivos e download por `id` interno; migração aditiva no SQLite do backend; testes `MigrationTest`, `BackupSerializationTest`, `BackupValidatorTest`, `BackupMapperTest`, `back/tests/test_sync_contract.py`; [spec](docs/specs/H1-6-data-foundation.md) |
| H1-7 | Correções de restauração, backup e Ajustes | `WorkoutRepository.downloadSync` valida o snapshot (`mapSnapshot`) antes de escrever e usa a transação única `dao.replaceAllData`; snapshot vazio devolve `SyncDownloadResult.Empty` e mantém os dados locais; `RestoreByDeviceId` persiste o `deviceId` só após sucesso; `importBackup` aplica o `deviceId` do arquivo; file pickers (SAF) ligados em `TreinosApp.kt` com confirmação nas duas pontas; texto de Ajustes corrigido + card "Restaurar de outro aparelho"; testes `RestoreDownloadTest`, `BackupImportTest`, `RestoreDeviceIdTest`; [spec](docs/specs/H1-7-restore-backup-fixes.md) |
| H2-1 | Descoberta LAN automática (mDNS/NSD) | `BackendDiscoveryManager` (`NsdManager` em `_treinos._tcp.`, resolve com timeout de 5 s, TXT `version`, `stop()` ao sair da tela), `DiscoveredBackend` com `url`/`endpoint`/`isVersionKnown`, `BackendConnector` que sonda `GET /health` e só salva `AppPreferences.backendUrl` em 200 (falha mantém a URL anterior), `HttpBackendHealthChecker`, seção "Backend na rede local" em `SettingsScreen` com "Buscar backends"/"Parar"/"Conectar" e aviso de versão desconhecida, `ACCESS_LOCAL_NETWORK` no manifest; testes `DiscoveredBackendTest` e `BackendConnectorTest`; [spec](docs/specs/H2-1-lan-discovery.md) |
| H2-5 | Registro avulso genérico (natação primeiro) | `ActivityLog.kt` com `ActivityModality`, `ActivityLogInput`/`ActivityLogDraft`, `validateActivityLog` e pace; `WorkoutRepository.logActivity/updateActivity/deleteActivity/lastManualSession` sobre `WorkoutSessionEntity` com `source = "manual"`, `planId = dayId = 0` e zero exercícios; `LogActivityScreen` na rota `Screen.LogActivity`, botão "Registrar atividade" na Home, card de atividade + filtro por modalidade + exclusão com confirmação no histórico; `formatDistanceMeters`/`formatPacePer100m`/`formatPacePerKm`; testes `ActivityLogTest`, `ActivityFormatTest`, `ManualSessionDaoTest`, `BackupMapperTest.sessao_manual_round_trip` e `back/tests/test_sync_contract.py::test_sessao_manual_round_trip`; [spec](docs/specs/H2-5-adhoc-log.md) |
| H2-6 | Plano híbrido e revisão por IA | `WorkoutRepository.reviewPlan`, `build_hybrid_plan`, `run_templates.py`, `POST /v1/review-plan`; Generate/Home/History com seleção de modalidades; fallback local híbrido; testes backend (`test_hybrid_plan.py`, `test_contract_v2.py`) e app (`WeeklyScheduleTest`, `RunMetricsTest`); [spec](docs/specs/H2-6-hybrid-plan.md) |
| H2-4 | Histórico com calendário e métricas | `HistoryScreen` com calendário do mês, cards de métricas (treinos, séries, volume, duração), volume por músculo e filtro por dia; `HistoryMetrics` e `CalendarMonth` com regras de agregação; `MetricCard` em `Common.kt`; formatters `formatVolumeKg`, `formatMonthYear`, `formatDateShort` em `Formatters.kt`; mapeamento de músculo em `TreinosApp.kt`; testes `HistoryMetricsTest`, `CalendarMonthTest` e `ActivityFormatTest`; [spec](docs/specs/H2-4-history-calendar.md) |
| H2-7 | Mobilidade e alongamento | `MobilityRoutine`/`MobilityRoutineTemplates` com rotinas curtas, `MobilityScreen` com timer/pausa/avanço/finalização, atalho "Mobilidade" na Home, rota em `TreinosApp`, sessão `modality = "mobility"` via `WorkoutRepository`/histórico; testes `MobilityRoutineTest` e `MobilitySessionTest`; [spec](docs/specs/H2-7-mobility.md) |
| H3-2 | Polimento visual e animações | `Motion.kt` + transições em `TreinosApp`, ajustes em `Theme`/`Common`/telas para estados e identidade visual; testes `MotionTest`; [spec](docs/specs/H3-2-visual-polish.md) |
| H3-3 | Acessibilidade (TalkBack, fonte e contraste) | `A11y.kt`/`Contrast.kt` + semântica Compose nas telas principais (`Home`, `Generate`, `Workout`, `History`, `Settings`) e componentes compartilhados; testes `A11yTest`/`ContrastTest`; [spec](docs/specs/H3-3-accessibility.md) |
| H3-1 | Testes de UI/instrumentados + cobertura | `HistoryScreenTest` (4) e `BackupFlowUiTest` (3) rodam Compose no Robolectric dentro de `:app:testDebugUnitTest` — o gate passou a ver telas; `WorkoutDaoTest` (4) cobre plano completo, `deleteAllData` e `replaceAllData` atômico que preserva IDs em Room in-memory; `ContractTest` + `back/tests/test_contract.py` validam o fixture único `contracts/generate_response.json`; `:app:assembleDebugAndroidTest` entrou no `scripts/ci-android.sh` (instrumentados compilam no gate; `connectedDebugAndroidTest` continua fora, documentado em `docs/MANUAL.md`); `COVERAGE=1` gera `app/build/reports/coverage/test/debug/index.html` e o workflow sobe o artifact; correção de layout achada pelos testes: botões do card de backup com `weight(1f)` em `SettingsScreen`; [spec](docs/specs/H3-1-ui-instrumented-tests.md) |
| H3-5 | Periodização e progressão de carga | `ui/progression/ProgressionEngine.kt` (`ProgressionAction`, `ProgressionSet`/`ProgressionContext`, `suggestProgression`, `progressionHistory`, `roundToIncrement`, `cycleWeek`, `deloadSuggested`, `completedSessionsSincePlan`) com melhor série por sessão nos últimos 42 dias, incremento por equipamento (barra 5/2,5 kg, halter/máquina 2,5 kg, peso corporal → +1 rep) e gatilhos de RPE (≤8,5 aumenta, ≥9,5 reduz), arredondamento em múltiplos de 2,5 kg entre 2,5 e 500; `ui/progression/ReviewSchedule.kt` (`postponeReviewDate`, `REVIEW_POSTPONE_WEEKS = 2`) adia `nextReviewAt` sem nunca antecipar a data salva; `WorkoutDao.updatePlanReviewDate` + `WorkoutRepository.postponeReview` (sem mudança de schema Room); `AppViewModel.progressionSuggestions`/`planCycle`/`postponeReview`; `AddSetDialog` pré-preenche a carga sugerida e mostra o motivo, Home ganhou `ReviewPlanCard` (revisão atrasada) e `DeloadNoticeCard` (semana do ciclo); o app **não** altera o plano sozinho nem sincroniza sugestões; testes `ProgressionEngineTest` (16) e `ReviewScheduleTest` (8); [spec](docs/specs/H3-5-progression.md) |
| H3-7 | Sexo opcional no perfil | `AppPreferences.sex` (chave `sex`) + `interface ProfileStore` com `sex`; `ProfileValidator.SEX_VALUES`/`normalizeSex` e `Result.sex` (rótulo não reconhecido vira `null`, nunca erro de peso/altura); seletor de 4 rótulos (`Feminino`/`Masculino`/`Outro`/`Prefiro não informar`) sem texto livre em "Seu perfil (opcional)" de `GenerateScreen` + linha no card "Perfil salvo"; `AppViewModel.generatePlan` persiste (incluindo limpar) e envia `GeneratePlanRequest.sex`; backend `GenerateRequest.sex: Optional[Literal[...]]` (fora do enum → 422) e linha `- Sexo biológico:` apenas quando o campo vem, em `build_user_prompt`; `plan_builder.py` **intocado** — `profile_adjustments`/`suggest_initial_load` não recebem `sex` (regressão zero); `sex` no bloco `profile` do backup sem bump de `schemaVersion` (v3 sem a chave decodifica `null`, valor inválido rejeita o arquivo inteiro); `MANUAL.md` §7 nomeia peso, altura e sexo como os campos que saem do aparelho; testes `ProfileValidatorTest` (5), `ContractTest` (2), `BackupValidatorTest` (3), `BackupSerializationTest` (2), `BackupImportTest` (2) e `test_profile_adjustments.py` (4); [spec](docs/specs/H3-7-profile-sex.md) |
| H3-6 | Perfil no backup JSON + guarda de tamanho | `BackupProfile` (`bodyWeightKg`/`heightCm`) dentro de `BackupFile.profile` e `schemaVersion = 3` no export, com `SUPPORTED_VERSIONS = setOf(1, 2, 3)` e faixas validadas (30–300 kg, 120–250 cm, NaN/Infinity rejeitados); `interface ProfileStore` em `RestoreContracts.kt`, implementada por `AppPreferences`; `importBackup(payload, deviceIdStore, profileStore)` só escreve o perfil quando `profile != null` — v1/v2 restauram planos e mantêm peso/altura locais, v3 com campo `null` limpa; nada é escrito se a validação ou `replaceAllData` falhar; `BackupCodec.kt` (`backupExportEncoder` com `encodeDefaults`, `backupImportDecoder` com `ignoreUnknownKeys`) colocado em produção para que os testes usem exatamente os `Json` do app; `BackupSizeGuard.readLimited` + `documentSize` (`OpenableColumns.SIZE`) em `TreinosApp.importLauncher` recusam 25 MB+ **antes** do decode, inclusive quando o provedor de nuvem não informa o tamanho; texto de Ajustes avisa que o arquivo contém peso e altura; testes `BackupSizeGuardTest` (9), `BackupValidatorTest` (v3/v4/faixas/NaN), `BackupSerializationTest` (round-trip, `null` explícito, chave desconhecida) e `BackupImportTest` (perfil restaurado, v2 não altera, falha não toca nas preferências); sexo **não** entrou — é a H3-7; [spec](docs/specs/H3-6-profile-in-backup.md) |

### Horizon 1 — Agora (próximas 2-4 semanas): proteger dados e fechar o core

Nada pendente: H1-1..H1-7 estão concluídos e listados em §4.1.

### Horizon 2 — Próximo (1-2 meses): multimodal, conectividade e histórico

Nada pendente: H2-1..H2-7 estão concluídos e listados em §4.1. A fila de produto
do horizonte 2 está fechada; o foco passa para qualidade/Horizon 3.

### Horizon 3 — Depois (2-4 meses): evolução e qualidade

| ID | Item | MoSCoW | Problema | Solução prevista | Esforço | Depende de | Spec |
|----|------|--------|----------|------------------|---------|------------|------|
| H3-4 | Backend na nuvem grátis (Oracle/Codespaces) | **W** | Backend só roda no PC local. | Deploy + Cloudflare Tunnel; toggle "Usar back remoto". Exige token antes de expor publicamente. | 2 dias | Backend Docker, `.env` seguro, **H3-10** | [H3-4](docs/specs/H3-4-cloud-backend.md) |
| H3-8 | Backup na nuvem do usuário (SAF) | **C** | O usuário quer o backup no OneDrive/Drive/Dropbox dele. | Nenhum SDK, nenhum OAuth: o `CreateDocument`/`OpenDocument` já elenca os provedores do sistema. Validar com os três provedores, ligar o feedback de leitura (`BackupImportState.Reading`) e escrever o texto de ajuda. | 0,5 dia | H1-7, H3-6 | [H3-8](docs/specs/H3-8-cloud-saf-backup.md) |
| H3-9 | Backup automático no destino escolhido | **W** | Reenviar sem abrir o seletor de novo. | URI persistida (`takePersistableUriPermission`, zero ocorrências hoje) + WorkManager + último upload visível + desligar ao perder permissão. **Nunca** importar automaticamente: o import é destrutivo. | 3 dias | H3-6, H3-8 | [H3-9](docs/specs/H3-9-auto-cloud-backup.md) |
| H3-10 | Regras de backup do Android ligadas | **S** | `allowBackup="true"` sem `fullBackupContent`/`dataExtractionRules`: os XMLs existem mas estão inertes, e apontam para `treinos_database` quando o banco real é `treinos.db` (`AppDatabase.kt:55`). Ligar como estão excluiria o banco em silêncio. | Referenciar as duas rules, policy explícita por domínio (nuvem fora de banco e prefs; transferência de aparelho mantém prefs) e teste que compara o nome do banco com as rules. | 0,5 dia | nada; **pré-requisito da H3-4** | [H3-10](docs/specs/H3-10-android-backup-rules.md) |

H3-1..H3-3, H3-5, **H3-6** e **H3-7** estão concluídos e listados em §4.1 — com
H3-6, o Horizon 3 não tem mais **Must** em aberto e restaurar um backup deixou de
apagar o perfil; com H3-7, o perfil ganhou o sexo opcional sem tocar nas regras
numéricas. H3-4 e H3-9 continuam **Won't now** até priorização explícita.

---

## 5. Priorização consolidada (MoSCoW)

| Must Have (agora) | Should Have (próximo) | Could Have (depois) | Won't now |
|-------------------|-----------------------|---------------------|-----------|
| Fundação de dados (H1-6) ✅ | Registro avulso (H2-5) ✅ | Mobilidade (H2-7) ✅ | Backend na nuvem (H3-4) |
| Correções de restauração/backup (H1-7) ✅ | Plano híbrido (H2-6) ✅ | Polimento + animações (H3-2) ✅ | Progressão automática de carga (H3-5) ✅ |
| Peso/altura (H1-5) ✅ | Descoberta LAN (H2-1) ✅ | Acessibilidade (H3-3) ✅ | Export CSV/Sheets |
| Testes de UI/Room (H3-1) ✅ | Wear OS MVP (H2-2) ✅ | Nuvem via SAF (H3-8) | Widget/complicação do relógio |
| Execução por séries (H1-2) ✅ | Mídia dos exercícios (H1-3) ✅ | | Backup automático (H3-9) |
| Perfil no backup (H3-6) ✅ | Sexo opcional no perfil (H3-7) ✅ | | |
| | Regras de backup do Android (H3-10) | | |

---

## 6. Esforço e caminho crítico

```
Concluído:  H1-1..H1-7 (core, fundação de dados, restauração/backup) + H2-1..H2-7
            (Wear, download, descoberta LAN, histórico, registro avulso,
            plano híbrido, mobilidade) + H3-1/H3-2/H3-3 (testes de UI/Room/
            contrato, polimento e acessibilidade) + H3-5 (progressão de carga)
            + H3-6 (perfil no backup v3 e guarda de tamanho)
            + H3-7 (sexo opcional no perfil, sem regressão nas regras numéricas)
Agora:      nada — o único Must em aberto (H3-6) foi concluído
Próximo:    H3-10 (rules do Android ligadas), pré-requisito da H3-4
Depois:     H3-8 (nuvem via SAF — validar/documentar)
Qualidade:  H3-4 nuvem/token e H3-9 backup automático (Won't now até priorizar)
```

**Caminho crítico (produto):** H1-6 ✅ → H1-7 ✅ → H2-5 ✅ → H2-1 ✅ → H2-6 ✅ → H2-7 ✅ → H3-5 ✅ → H3-6 ✅.
O próximo elo é **H3-10**: sem as rules do Android ligadas (e com o nome do banco
correto), o backup de plataforma continua inerte e a H3-4 não pode ser exposta.
**H3-7** entrou em paralelo como previsto — o campo `sex` foi no
`BackupProfile` da H3-6 sem novo bump de versão.
O gate (`./scripts/verify.sh`) roda em todo commit e, desde H3-1, também enxerga
UI (Compose no Robolectric), Room em memória, migração e o contrato app↔backend;
os testes instrumentados compilam no gate e rodam fora dele com
`./gradlew :app:connectedDebugAndroidTest`.

---

## 7. Critérios de aceite gerais (Definição de Pronto)

- [ ] Spec em `docs/specs/<ID>.md` aprovada (obrigatória para itens do roadmap)
- [ ] `./scripts/verify.sh` verde (build + testes + lint + assinatura + backend)
- [ ] Testes JVM novos para toda lógica pura; teste de backend quando `back/` mudar
- [ ] Smoke test manual no device físico quando houver UI nova
- [ ] `README.md`, `docs/MANUAL.md`, `ROADMAP.md` e a spec atualizados
- [ ] Nenhum código novo nos diretórios legados (`shared/`, `com.treinos.shared`)

---

## 8. Riscos e mitigação (resumo)

| Risco | Prob. | Impacto | Mitigação |
|-------|-------|---------|-----------|
| `deviceId` não é autenticação | Média | Alto | Tratar como backup pessoal; não expor backend público sem token (H3-4) |
| Migração Room apagar dados | Alta | Alto | H1-6: migração real + teste 1→2; remover `fallbackToDestructiveMigration` |
| SenseNova muda API/limite | Baixa | Médio | Backend agnóstico (OpenAI-compatible); fallback local em 1 commit |
| Catálogo não cobre corrida/natação | Alta | Médio | Templates locais para corrida; natação como registro (H2-5/H2-6) |
| BT instável celular ↔ relógio | Média | Médio | Relógio opera autônomo com fila local; reconcilia ao reconectar |
| Android 15+ restringe LAN | Baixa | Alto | Manter fallback manual de IP; testar em preview |
| Desinstalar app apaga dados locais | Alta | Alto | H1-4 (backup JSON) + H1-1 (restore por deviceId); H1-7 ligou os botões e protege contra snapshot vazio |
| Restauração/backup apagar dados | Baixa | Alto | Mitigado em H1-7: validação do snapshot antes de apagar, transação única (`replaceAllData`), snapshot vazio não substitui e `deviceId` só após sucesso |
| “Dor nas costas” virar promessa médica | Média | Alto | H2-7: aviso, sinais de alerta e nenhum diagnóstico |
| Contrato v1/v2 divergir | Média | Médio | Fixtures v1/v2 e testes de contrato no gate |
| Perfil (peso/altura) fora do backup — restaurar apaga o perfil | Baixa | Médio | **Mitigado em H3-6:** `BackupProfile` no `schemaVersion` 3; v1/v2 importados sem limpar o que o arquivo não contém |
| Arquivo de backup passa a conter dados pessoais (peso/altura/sexo) | Média | Médio | Texto de Ajustes que avisa antes de exportar/compartilhar (H3-6), `MANUAL.md` §7 com os três campos que saem do aparelho (H3-7) e H3-10, que mantém o auto-backup do Android fora do caminho até haver policy explícita |
| Sexo vira regra determinística ou prescrição médica | Baixa | Alto | H3-7: campo opcional só no prompt; `profile_adjustments`/`suggest_initial_load` não recebem `sex` e `test_sexo_ignorado_no_plano_local` quebra se alguém ligar os dois |
| Backup automático do Android sem política | Alta | Médio | H3-10: rules referenciadas no manifest, nomes conferidos por teste e pré-requisito da H3-4 antes de existir token |

---

## 9. Próximo passo imediato

Horizons 1 e 2 estão concluídos (H1-1..H1-7, H2-1..H2-7). No Horizon 3, além de
H3-1/H3-2/H3-3 e **H3-5 (periodização e progressão de carga)**, o último **Must**
em aberto — **H3-6 (perfil no backup JSON + guarda de tamanho)** — foi concluído:
o `schemaVersion` 3 leva peso e altura, backups v1/v2 continuam aceitos sem
limpar o perfil local, e o limite de 25 MB agora é checado **antes** de ler o
arquivo. Todos estão em §4.1.

O próximo passo é **H3-10 (regras de backup do Android)**: os dois XMLs existem,
não estão referenciados no manifest e apontam para `treinos_database` quando o
banco real é `treinos.db` — ligar como estão excluiria o banco em silêncio. É
também o pré-requisito de **H3-4**. **H3-7 (sexo opcional no perfil)** foi
implementada como previsto: campo novo em `AppPreferences`/`GenerateScreen`/prompt
da IA, sem regressão na tabela de IMC nem nos coeficientes de carga, e entrou no
`BackupProfile` criado pela H3-6 sem novo bump de versão. O que ficou de fora, e
vale saber: o sexo ainda **não** alcança a IA no caminho híbrido (o app sempre
manda `modalities`, e `build_hybrid_user_prompt` já não leva peso/altura desde a
H1-5) — seguir esse prompt é escopo de uma spec nova.

Sobre nuvem do usuário: **H3-8** não é SDK nem OAuth — o SAF já lista Drive,
OneDrive e Dropbox, então o item é validar e documentar o caminho (a H3-6 já fez
o arquivo ser completo o suficiente para isso valer a pena). **H3-4** (backend na
nuvem com token) e **H3-9** (backup automático) continuam **Won't now** até
priorização explícita.

As specs **H3-8, H3-9 e H3-10** estão propostas e **aguardam aprovação**;
nenhuma foi implementada. As da **H3-6** e da **H3-7** estão concluídas.

Para iniciar qualquer item, leia a spec correspondente em
[docs/specs/](docs/specs/); se não existir ou estiver pendente de aprovação,
crie/ajuste a partir de [docs/specs/_TEMPLATE.md](docs/specs/_TEMPLATE.md) e
confirme antes de implementar.

---

## 10. Especificações

- [_TEMPLATE.md](docs/specs/_TEMPLATE.md) — formato obrigatório
- [H1-1 — Recuperação por deviceId](docs/specs/H1-1-device-restore.md) — concluída (retroativa)
- [H1-2 — Execução por séries](docs/specs/H1-2-set-execution.md) — concluída (retroativa)
- [H1-3 — Imagens dos exercícios](docs/specs/H1-3-exercise-media.md) — concluída
- [H1-4 — Backup JSON](docs/specs/H1-4-backup-json.md) — concluída (lógica e UI via H1-7)
- [H1-5 — Peso e altura](docs/specs/H1-5-profile.md) — concluída
- [H1-6 — Fundação de dados](docs/specs/H1-6-data-foundation.md) — concluída
- [H1-7 — Correções de restauração, backup e Ajustes](docs/specs/H1-7-restore-backup-fixes.md) — concluída
- [H2-1 — Descoberta LAN](docs/specs/H2-1-lan-discovery.md) — concluída
- [H2-2 — Wear OS MVP](docs/specs/H2-2-wear-os.md) — concluída
- [H2-3 — Download do backend](docs/specs/H2-3-backend-download.md) — concluída (retroativa)
- [H2-4 — Histórico com calendário](docs/specs/H2-4-history-calendar.md) — concluída
- [H2-5 — Registro avulso](docs/specs/H2-5-adhoc-log.md) — concluída
- [H2-6 — Plano híbrido](docs/specs/H2-6-hybrid-plan.md) — concluída
- [H2-7 — Mobilidade](docs/specs/H2-7-mobility.md) — concluída
- [H3-1 — Testes de UI, Room e contrato](docs/specs/H3-1-ui-instrumented-tests.md) — concluída
- [H3-2 — Polimento visual](docs/specs/H3-2-visual-polish.md) — concluída
- [H3-3 — Acessibilidade](docs/specs/H3-3-accessibility.md) — concluída
- [H3-4 — Backend na nuvem com token](docs/specs/H3-4-cloud-backend.md) — pronta para implementar
- [H3-5 — Progressão de carga](docs/specs/H3-5-progression.md) — concluída
- [H3-6 — Perfil no backup e guarda de tamanho](docs/specs/H3-6-profile-in-backup.md) — concluída
- [H3-7 — Sexo opcional no perfil](docs/specs/H3-7-profile-sex.md) — concluída
- [H3-8 — Backup na nuvem do usuário via SAF](docs/specs/H3-8-cloud-saf-backup.md) — proposta, aguardando aprovação
- [H3-9 — Backup automático no destino escolhido](docs/specs/H3-9-auto-cloud-backup.md) — proposta (Won't now)
- [H3-10 — Regras de backup do Android](docs/specs/H3-10-android-backup-rules.md) — proposta, aguardando aprovação

Um item sem spec não entra em execução. A spec é o contrato que impede um modelo
mais simples de "compilar verde e entregar a coisa errada".
