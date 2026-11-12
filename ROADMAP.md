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

## 2. Onde estamos hoje (atualizado em 2026-11-11)

| Área | Status | Evidência |
|------|--------|-----------|
| Banco local (Room) | ✅ | Planos, dias, exercícios, sessões, séries |
| Execução de treino | ✅ | Timer de descanso, séries, RPE, carga, histórico (H1-2) |
| Geração via IA | ✅ | Backend + SenseNova + fallback local existe; robustez entregue (H3-22): prompt com o pool completo do request (sem cap de 136 IDs), parse tolerante, salvamento parcial de IDs inválidos, timeout 60 s + retry; diagnóstico no ar (H3-21: `fallbackReason` no contrato, log `[fallback]` e motivo no app); foco do usuário priorizado nas sessões (H3-23); **plano B (dois níveis) ativo em 2026-11-10**: gatilho de 22.000 chars disparou com request amplo (29.067 chars, IA levava 74,5s, fallback `reason=timeout`) e cortou o prompt para ~14.000 chars sem perder nenhum ID elegível (Tier A: 8 detalhados por músculo; Tier B: IDs agrupados por músculo); evolução planejada: seleção por nome (H3-25); geração assíncrona proposta (H3-40) |
| Foco muscular no plano principal | ✅ | No principal, foco é **prioridade**: cada sessão de força garante ≥ 1 exercício de cada grupo pedido antes do split do ciclo (`user_target_muscles` em `build_strength_session`/`build_hybrid_plan`), regra explícita no prompt e dica na GenerateScreen; exclusivo só no complementar (H3-16) (H3-23) |
| Sincronização app → backend | ✅ | Snapshot manual (`AppViewModel.syncNow`) |
| Restauração por `deviceId` | ✅ | `RestoreByDeviceId` valida o ID, baixa, só persiste o `deviceId` após sucesso e tem tela de confirmação em Ajustes (H1-7) |
| Download do backend | ✅ | `downloadSync` valida o snapshot antes de escrever e grava tudo em uma transação (`replaceAllData`); snapshot vazio mantém os dados locais (H1-7) |
| Testes + CI | ✅ | 35 arquivos de teste JVM (`:app`, `:protocol`, `:wear`) + gate (`./scripts/verify.sh`); Compose no Robolectric, Room em memória e contrato nos dois lados dentro do gate; instrumentados compilam no gate (H3-1) |
| Descoberta LAN | ✅ | Ajustes → "Buscar backends" (`NsdManager` em `_treinos._tcp.`); "Conectar" sonda `GET /health` e só salva a URL em 200; IP manual continua como fallback (H2-1) |
| Wear OS | 🟡 | MVP H2-2 entregue (módulo `:wear` no build, Data Layer + tela do relógio, dois APKs no gate), mas com lacunas de uso real: alvo de reps vazio, timer local não ligado, corrida/mobilidade sem tela coerente e fila offline incompleta — ver "Roadmap do relógio" no §4 |
| Backup/restore do usuário | ✅ | Exportar/importar ligados aos file pickers (SAF), pré-visualização + confirmação e `deviceId` do arquivo aplicado só após importação válida (H1-7) |
| Perfil no backup JSON | ✅ | `BackupProfile` no `schemaVersion = 3` (peso, altura e sexo viajam no arquivo; v1/v2 continuam aceitos e **não** limpam o perfil local); guarda de 25 MB ligada antes de ler o stream, com leitura limitada quando o provedor não informa o tamanho (H3-6, H3-7) |
| Sexo opcional no perfil | ✅ | Seletor em "Seu perfil (opcional)" com `Feminino`/`Masculino`/`Outro`/`Prefiro não informar`, salvo em `AppPreferences.sex` e enviado só quando preenchido; aparece no prompt da IA e **não** muda descanso, RPE, séries nem cargas (H3-7) |
| Backup na nuvem do usuário | 🟡 | O SAF já lista Drive/OneDrive/Dropbox no seletor (nenhum SDK, nenhuma credencial); falta validar com provedores reais e escrever o texto de ajuda (H3-8) |
| Backup automático do Android | ✅ | Rules ligadas no manifest (`fullBackupContent`/`dataExtractionRules`) com política de `<exclude>`: banco (`treinos.db`/`-wal`/`-shm`) fora de nuvem e transferência, `treinos_prefs.xml` fora da nuvem; `BackupRulesTest` confere os nomes contra as constantes de `AppDatabase`/`AppPreferences` (H3-10) |
| Imagem/vídeo de exercício | 🟡/❌ | A mídia que **já vem no plano** é usada e persistida no Room (v4), o catálogo é a fonte preferencial, resposta vazia não envenena mais o cache e o path é URL-encodado por segmento (H3-24, substituiu a H3-14); vídeo não |
| Migrações Room | ✅ | Room v2 com `MIGRATION_1_2` + `MigrationTest` (1→2 preservando linhas); `fallbackToDestructiveMigration` removido (H1-6) |
| Registro avulso (natação) | ✅ | `ActivityLog` valida e grava sessão manual (`source = "manual"`, `planId = dayId = 0`, zero exercícios); tela de registro/edição, card de atividade e filtro por modalidade no histórico, exclusão com confirmação (H2-5) |
| Plano híbrido / revisão por IA | 🟡 | H2-6 entregue, mas a geração híbrida atual funde modalidades do mesmo dia em uma sequência única e gera musculação incompleta; correção proposta em H3-11 |
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
| H3-11 | Geração híbrida por sessão e abas por modalidade | Modalidades podem continuar no mesmo dia, mas cada uma vira uma sessão independente, com início, execução e aba próprios: musculação com 4–8 exercícios e equipamento de academia, corrida e mobilidade com telas próprias; a resposta da IA é normalizada antes de salvar. `SessionKind`/`ModalityTab`, `groupSessionsByModality`, `defaultModalityTab`, `planVolumeLabel`, `sessionsOnWeekday`, `RunLogInput`/`RunLogValidation`/`validateRunLog`, `RunWorkoutScreen`, `MobilityScreen` modo rotina planejada; backend `build_strength_session`, `assign_weekdays`, `normalize_hybrid_plan`, `template_for`, `profile_adjustments` só `reps_weight`, `resolve_plan_ids` ignora corrida/mobilidade, `SYSTEM_PROMPT_HYBRID`, `ExerciseDTO.sets` até 20; testes `test_hybrid_plan.py`, `test_run_templates.py`, `test_profile_adjustments.py`, `test_contract_v2.py`, `WeeklyScheduleTest`, `SessionModeTest`, `RunMetricsTest`, `MobilityRoutineTest`, `WorkoutRepositorySessionTest`, `HistoryMetricsTest`, `HomeScreenTest`; [spec](docs/specs/H3-11-hybrid-sessions.md) |
| H3-6 | Perfil no backup JSON + guarda de tamanho | `BackupProfile` (`bodyWeightKg`/`heightCm`) dentro de `BackupFile.profile` e `schemaVersion = 3` no export, com `SUPPORTED_VERSIONS = setOf(1, 2, 3)` e faixas validadas (30–300 kg, 120–250 cm, NaN/Infinity rejeitados); `interface ProfileStore` em `RestoreContracts.kt`, implementada por `AppPreferences`; `importBackup(payload, deviceIdStore, profileStore)` só escreve o perfil quando `profile != null` — v1/v2 restauram planos e mantêm peso/altura locais, v3 com campo `null` limpa; nada é escrito se a validação ou `replaceAllData` falhar; `BackupCodec.kt` (`backupExportEncoder` com `encodeDefaults`, `backupImportDecoder` com `ignoreUnknownKeys`) colocado em produção para que os testes usem exatamente os `Json` do app; `BackupSizeGuard.readLimited` + `documentSize` (`OpenableColumns.SIZE`) em `TreinosApp.importLauncher` recusam 25 MB+ **antes** do decode, inclusive quando o provedor de nuvem não informa o tamanho; texto de Ajustes avisa que o arquivo contém peso e altura; testes `BackupSizeGuardTest` (9), `BackupValidatorTest` (v3/v4/faixas/NaN), `BackupSerializationTest` (round-trip, `null` explícito, chave desconhecida) e `BackupImportTest` (perfil restaurado, v2 não altera, falha não toca nas preferências); sexo **não** entrou — é a H3-7; [spec](docs/specs/H3-6-profile-in-backup.md) |
| H3-12 | Séries/reps visíveis em cada exercício | `planVolumeLabel` em `WeeklySchedule.kt` expõe `sets × targetReps` (ou só séries/reps quando um dos lados falta) para o app; `HomeScreen.kt` mostra o volume de cada exercício no card da sessão (incluindo “X séries” quando não há alvo) — corrida/mobilidade só exibem reps quando o alvo existir; teste `WeeklyScheduleTest.card_de_sessao_inclui_volume_de_series_reps`; commit `7a99520`; [spec](docs/specs/H3-12-series-reps-visiveis.md) |
| H3-13 | Título da sessão varia conforme o split | Backend `plan_builder.py` nomeia sessões de força pelo split aplicado (`Treino A — Empurrar`, `Treino B — Puxar`, `Treino C — Pernas e Core`, ciclo repetido pela ordem das sessões de força) em vez do nome genérico; corrida/mobilidade mantêm templates; testes em `back/tests/test_hybrid_plan.py`; Home exibe o `name` recebido; commit `3793b6f`; [spec](docs/specs/H3-13-titulo-sessao-split.md) |
| H3-15 | Múltiplos planos coexistindo na Home | Room v3 aditiva com `WorkoutPlanEntity.kind` (`main`/`extra`, schema `3.json` versionado); `WorkoutDao.observeActivePlanEntities` (main primeiro), `deactivateByKind`, `insertPlanWithDays` e `deletePlan` (CASCADE, sessões sem FK sobrevivem); `WorkoutRepository.observeActivePlans`/`saveGeneratedPlan(kind)`/`deleteExtraPlan` e `mapSnapshot` preservando múltiplos ativos (sem ativo → ativa o primeiro main); Home com chips de seleção (`resolveSelectedPlan` em `PlanSelection.kt`) e exclusão de extra com confirmação; GenerateScreen com modo "Substituir principal"/"Adicionar complementar"; `kind` aditivo no backup (schemaVersion continua 3, valor inválido rejeita o arquivo) e no sync app↔backend (default `main`, coluna aditiva no SQLite); revisão por IA segue apontando para o main; testes `MigrationTest` (2→3), `WorkoutDaoTest` (4), `RestoreDownloadTest` (3), `WorkoutRepositorySessionTest` (4), `Backup*Test`, `PlanSelectionTest` (5) e `back/tests/test_sync_contract.py` (kind round-trip + migração aditiva); [spec](docs/specs/H3-15-planos-multiplos.md) |
| H3-16 | Geração complementar: foco exclusivo e casa/peso corporal | Backend `GenerateRequest.focusMuscles` (1–4 grupos de `MUSCLE_ALIASES`; lista vazia, grupo inválido, run/mobility junto, dias fora de 1–6 ou duração fora de 15–120 ⇒ 422 citando o valor; sem foco, 2–6 dias e 30–120 min como antes) com `model_validator` condicional; `plan_builder.py`: `_day_groups` com foco exclusivo (sem template do split), `_build_pool` com filtro estrito de equipamento (`bodyweight` ⇒ `body only`/`none`, `home` ⇒ informado ∪ peso corporal, foco em academia segue o informado — a regra dos ≥ 20 nunca ignora o filtro), `build_strength_session` complementar (3–6 exercícios pela duração: 15→3, 20→4, 30→5, ≥45→6; sem rotação de `STRENGTH_SPLITS`; pool insuficiente ⇒ erro, nunca plano fora do foco), `build_hybrid_plan` só de força com nome `Complementar — <foco>` e `enforce_complementary_plan` removendo da resposta da IA o que saiu fora do foco/equipamento e reconstruindo sessão com < 3 exercícios; prompts `system_prompt_hybrid` + `build_hybrid_user_prompt` com as restrições; app `GeneratePlanRequest.focusMuscles`, `ComplementaryValidator` puro (espelha o backend), `GenerateScreen` no modo complementar (chips de foco exclusivo, modalidades ocultas com força fixa, durações 15/20/30/45, dias 1–6, botão desabilitado com hint) e `AppViewModel.generatePlan` montando o request e salvando `kind = "extra"` (nome `Complementar — Casa` aplicado localmente no complementar só de ambiente); fixture `contracts/generate_request_complementary.json` validado dos dois lados; testes `test_complementary_plan.py` (13), `test_contract_v2.py` (5 novos), `ComplementaryValidatorTest` (6) e `ContractTest` (2 novos); [spec](docs/specs/H3-16-geracao-complementar.md) |
| H3-10 | Regras de backup do Android ligadas e corretas | `AndroidManifest.xml` com `android:fullBackupContent="@xml/backup_rules"` e `android:dataExtractionRules="@xml/data_extraction_rules"`; os dois XMLs reescritos com política de `<exclude>` (nunca `<include>`, que escondia o caminho errado `treinos_database` em silêncio): `treinos.db`/`-wal`/`-shm` excluídos de nuvem **e** transferência de aparelho, `treinos_prefs.xml` excluído da nuvem e mantido na transferência; `AppDatabase.DATABASE_NAME` e `AppPreferences.PREFS_NAME` extraídos como constantes; `BackupRulesTest` (4 testes) compara os XMLs e o manifest contra as constantes; MANUAL §7/§6.4 e README documentam que o backup da plataforma não é caminho oficial de restore; [spec](docs/specs/H3-10-android-backup-rules.md) |
| H3-21 | Diagnóstico do fallback da IA | `parse_ai_plan` pura em `back/app/main.py` lança `AiFailure(reason, detail)` com códigos fechados (`no_api_key`, `http_error`, `timeout`, `json_parse`, `unresolved_ids`, `schema_validation`, `unknown`); `GenerateResponse.fallbackReason` aditivo — vazio quando `source = "ai"`, nunca vazio no local (invariante no endpoint); log estruturado `[fallback] reason=... detail=≤300` no lugar do `except` único que só imprimia no stdout; app decodifica `GeneratePlanResponse.fallbackReason` (null em backend antigo, `ignoreUnknownKeys`) e a mensagem de geração cita o motivo via `fallbackReasonLabel` ("… Motivo: resposta da IA sem IDs válidos."); fixture `contracts/generate_response.json` com o campo validado dos dois lados; testes `test_fallback_diagnostics.py` (6, endpoint com cliente HTTP falso), `ContractTest` (+2) e `FallbackReasonLabelTest` (3); [spec](docs/specs/H3-21-diagnostico-fallback-ia.md) |
| H3-37 | Idioma do app (PT/EN) com seletor | Extrair strings para `values/` (PT) + `values-en/`, `AppCompatActivity` + per-app locale com seletor em Ajustes, em fases com gate verde. Testes `StringParityTest` (chaves PT↔EN e placeholders), `HomeScreenTest` (locale en), `A11yTest` (content descriptions). Gate `./scripts/verify.sh` verde em ambas as fases e no final; [spec](docs/specs/H3-37-idioma-app.md) |
| H3-22 | Robustez da geração por IA | `plan_builder.catalog_summary_for_request` monta prompt por request com pool completo de força filtrado por equipamento/nível (sem cap por grupo; complementar só foco), priorizando grupos do request; `plan_builder.id_name_mismatches` detecta par `exerciseId`↔`exerciseName` divergente (match tolerante por normalização/fuzzy/lookup de nome→ID); `resolve_plan_ids` agora remove IDs inválidos (salvamento parcial) e retorna `dropped`; `main.parse_ai_plan` passou a ser tolerante (remove cercas de código e extrai primeiro objeto JSON balanceado), aceita `request`, registra `dropped_ids` (`unresolved_id`/`id_name_mismatch`), reconstrói dias de força com < 3 exercícios via `build_strength_session` com pool/restrições do request, e só cai em `unresolved_ids` total quando todos os exercícios de força originais viram inválidos sem bloco não-força aproveitável; prompt por request (`system_prompt_for_request`) substitui o resumo global capado e instrui a IA a informar `exerciseName` + preferir IDs listados; endpoint `/v1/generate-workout` usa timeout 60s + 1 retry apenas para `json_parse` e HTTP 429/5xx (retry de rede com 30s); **plano B (dois níveis) ativo em 2026-11-10**: gatilho de 22.000 chars disparou com request amplo (29.067 chars, IA levava 74,5s, fallback `reason=timeout`) e cortou o prompt para ~14.000 chars sem perder nenhum ID elegível (Tier A: 8 detalhados por músculo; Tier B: IDs agrupados por músculo; cobertura 585/585 verificada por teste); `test_fallback_diagnostics.py` ajustado ao novo contrato de `resolve_plan_ids` (H3-21 preservado para fallback total), `test_hybrid_plan.py` regressão preservada e `test_ai_robustez.py` criado (17 testes puros/endpoint fake cobrindo parse tolerante, salvamento parcial, resumo, retry 429, ID↔nome e plano B); [spec](docs/specs/H3-22-robustez-geracao-ia.md) |
| H3-40 | Geração assíncrona: pedir agora, receber pronto | Job no backend (202 + `jobId`, status/cancel, sweep de interrompidos), orçamento de IA de 600 s; app com card na Home (Atualizar/Cancelar), poll no refresh e na volta ao foreground, envelope sobrevive a processo morto; [spec](docs/specs/H3-40-geracao-assincrona.md) (implementado) |
| H3-41 | Troca de exercício em plano já gerado (swap) | Bottom sheet de troca: substituto do mesmo músculo primário e equipamento informado, 1-para-1, atômico, sem mudar séries/reps/ordem; não afeta sessão em andamento; testes `ExerciseSwapTest`; [spec](docs/specs/H3-41-troca-exercicio.md) |
| H3-42 | Revisão de precisão da geração com base no uso | `ExerciseUsageEvent` (Room v5, `MIGRATION_4_5`) registra concluído/pulado/trocado localmente; `ExerciseUsageWeights.usageWeightFor` (+1 concluído, −1 pulado/trocado, piso 0, teto 5, decaimento 30/60/90 dias); `WorkoutRepository.recordExerciseUsage`/`usageWeightsForCatalog` e `AppViewModel` expõem para a UI; backend `GenerateRequest.exerciseWeights` opcional e `catalog_summary_for_request` ordena cada grupo muscular por peso decrescente (aditivo, cobertura total preservada); testes `ExerciseUsageTest` (JVM) e `test_uso_precisao.py` (backend); gate verde; [spec](docs/specs/H3-42-revisao-precisao-uso.md) |
| H3-45 | Backup preserva mídia e a troca de plano reflete na Home | `BackupPlanExercise` ganha `instructions`/`images` (aditivo, sem bump); `BackupMapper` copia nos dois sentidos; `resolveExerciseMedia` funde catálogo + plano campo a campo (asset offline não esconde a mídia do plano); `confirmImport` limpa a seleção e `refreshPendingGeneration`/`acceptReview` selecionam o novo `main`; `extra` preserva; testes `BackupMapperTest`, `BackupSerializationTest`, `BackupImportTest`, `ExerciseMediaTest` e `GenerationJobFlowTest`; gate verde; [spec](docs/specs/H3-45-backup-midia-e-substituicao.md) |
| H3-44 | Substitutos de exercício disponíveis offline | `ExerciseMediaRepository.loadLocal()` cache-primeiro (cache expirado/ausente/corrompido → asset embarcado compacto), `mergeLocal`/`parseSwapCatalog` puros, cache atômico e TTL só como gatilho de refresh; `scripts/gen-swap-catalog.py` gera `app/src/main/assets/exercise_swap_catalog.json` a partir do backend; `AppViewModel.init` publica o catálogo local antes da rede; `WorkoutScreen` troca em `ModalBottomSheet` com busca, cartões selecionáveis, escopo em `FilterChip` e confirmação única — sem spinner e com estado vazio explícito; sem mudança de Room; testes `ExerciseMediaTest`, `ExerciseMediaLocalTest`, `SwapCatalogAssetTest` e `WorkoutScreenTest`; gate verde; [spec](docs/specs/H3-44-substitutos-offline.md) |
| H3-23 | Foco muscular respeitado no plano principal | `build_strength_session` ganha `user_target_muscles`: no caminho não complementar, os grupos da sessão passam a ser `grupos pedidos + grupos do split` (dedup, pedidos primeiro) — cada sessão de força garante ≥ 1 exercício de cada grupo pedido quando existir no pool (grupo sem candidato não consome slot, strict só para o pedido do usuário); `build_hybrid_plan` passa `user_target_muscles = _expand_muscles(targetMuscles)` em vez de descartá-lo; `build_hybrid_user_prompt` com a regra explícita de prioridade (só no principal); `GenerateScreen` mostra dica no modo principal com músculos selecionados ("APENAS o grupo" é o complementar); complementar H3-16 intocado (`test_complementar_continua_exclusivo`); testes `test_hybrid_plan.py` (+4: foco em cada sessão, dois grupos, split sem foco, grupo sem candidato) e `test_complementary_plan.py` (+1); [spec](docs/specs/H3-23-foco-muscular-plano-principal.md) |
| H3-24 | Imagens e descrição: plano como fonte + catálogo resiliente | `WorkoutExerciseDto` passa a ler `instructions`/`images` que o `enrich_plan` já embute no plano; Room v4 aditiva com `WorkoutPlanExerciseEntity.instructions`/`.images` (schema `4.json` versionado, `MIGRATION_3_4` sem destruição), `WorkoutRepository.saveGeneratedPlan` persiste a mídia e `WorkoutDaoTest` cobre o round-trip; `ExerciseMediaRepository` não cacheia mais catálogo vazio (`fetchFromBackend` devolve `null` no vazio; decisão pura em `mergeCatalog` — resposta vazia ou falha mantém o cache anterior) e `imageUrlFor` codifica cada segmento do path (cache guarda o path cru, sem dupla codificação); `resolveExerciseMedia` pura resolve catálogo primeiro e mídia do plano como fallback; `AppViewModel.openExerciseDetail` captura a mídia embutida do plano e `selectedMedia` combina catálogo + plano; conectar ou salvar a URL do backend recarrega o catálogo em fundo; backup e sync sem mudança (mídia é derivável do `exerciseId`); testes `MigrationTest.migracao_3_4_preserva_linhas`, `ExerciseMediaTest` (+3: cache, URL-encode, fallback do plano), `WorkoutDaoTest` (+1) e `ContractTest` (+2); [spec](docs/specs/H3-24-midia-exercicio-plano.md) |
| H3-27 | Integridade do registro | `ui/SetValidation.kt` puro (`validateSetInput`: carga não vazia deve parsear vírgula/ponto com `0 < peso ≤ 500`, reps inteiro 1–100, RPE vazio = `null` e preenchido 1–10 com uma casa decimal — inválido mostra erro no diálogo e **nada** é gravado); linha de série clicável abre `SetDialog` pré-preenchido ("Editar série N") com "Salvar alterações" (`WorkoutDao.updateSetLog`/`WorkoutRepository.updateSet`) e "Excluir série" com confirmação (`deleteSetLog` + `recalculateCompletedSets` realinhando o contador ao número real de séries); "Finalizar treino" exige confirmação ("Finalizar treino? N de M séries registradas" com Cancelar/Finalizar); "Abandonar treino" na top bar do Workout e no InProgressCard da Home, ambos com confirmação (`WorkoutRepository.deleteSession`, exercícios e séries por CASCADE, estado do Wear limpo); mobilidade planejada fecha a sessão `in_progress` existente (`finishPlannedMobilitySession` — UPDATE status/endedAt/duração, `TreinosApp` passa `planId`/`dayId` da sessão ativa) em vez de inserir segunda sessão fantasma; atalho sem plano mantém a sessão avulsa; botão vira "Exercício concluído — próximo" (avança, rola até o próximo incompleto e atenua o exercício cumprido); testes `SetValidationTest` (9), `WorkoutDaoTest` (+2), `WorkoutRepositorySessionTest` (+2: sessão única da mobilidade e abandonar remove sessão) e `WorkoutScreenTest` (+3: confirmação de finalizar, edição pela linha e carga inválida bloqueando salvamento); [spec](docs/specs/H3-27-integridade-registro.md) |
| H3-29 | Estados e erros padronizados | `UiState`/`UiError`/`uiErrorFrom`/`uiErrorMessage` em `ui/UiError.kt`; `AppViewModel.plansState`/`sessionsState`/`activeSessionState` (Loading até o primeiro emit do Room; `onStart{Loading}` a cada troca de sessão ativa) consumidos por Home, Histórico e RunWorkout; erro persistente com "Tentar de novo": geração na `GenerateScreen` (`retryGenerate` reexecuta a última geração com os mesmos parâmetros) e sync/download em Ajustes; `BackendClient` com exceções tipadas (`BackendConnectionException`/`BackendTimeoutException`/`BackendHttpException`/`BackendResponseException`) e corpo HTTP cru só em `Log.w` (Logcat); `ErrorState` com título + retry em `Common.kt`; RunWorkout sem sessão mostra "Sessão não encontrada" + voltar e `onFinish(sessionId, input)` elimina o no-op silencioso; ~19 pontos de `error.message` trocados por `uiErrorMessage` (erros locais mantêm o texto da ação); testes `UiErrorTest` (4), `HomeScreenTest` (+2) e `RunWorkoutScreenTest` (3); [spec](docs/specs/H3-29-estados-erros.md) |
| H3-30 | Geração sem atrito | `ui/GenerateConfig.kt` (`ModalityDraft` + `GenerateConfig` serializáveis e `GenerateConfigCodec` puro com `ignoreUnknownKeys`/`explicitNulls = false`; default pré-inclui "Musculação 4x"); `AppPreferences` com `generateConfigJson` (`generate_config_v1`) e `lastFallbackReason` (`last_fallback_reason`); `AppViewModel` restaura/expõe `generateConfig`, persiste a config e o motivo do fallback só em geração bem-sucedida (IA limpa o motivo; local grava o código do contrato, `unknown` quando ausente), `cancelGenerate` cancela o job da geração (salvamento é `@Transaction`, `CancellationException` não vira erro) e `GeneratePlanArgs` carrega a config para o "Tentar de novo"; `GenerateScreen` restaura a config (valores inválidos caem no default), CTA fixo em `bottomBar` com hint de validação junto (perfil inválido expande "Mais opções"), "Mais opções" colapsável com perfil/sexo/revisão/ambiente, seções de força (objetivo/nível/equipamento/foco/duração) ocultas sem musculação, `modalityToDto` enviando o goal do chip na força e overlay de geração com tempo decorrido + "A IA pode levar até 90 s" + Cancelar; Home cita o motivo persistido no selo ("Gerado localmente — resposta da IA sem IDs válidos"); testes `GenerateConfigTest` (6), `ContractTest.goal_da_modalidade_forca_segue_chip`, `GenerateScreenTest` (5, Robolectric), `HomeScreenTest` (+1) e `test_contract_v2.py` (+3); [spec](docs/specs/H3-30-geracao-sem-atrito.md) |
| H3-31 | Estado sobrevive a rotação, teclado e processo | `MobilitySessionEngine`/`MobilitySessionState`/`MobilityScreenState` puros (tick de 1 s, `TRANSITION_SECONDS` passa a valer com contagem regressiva e toque para pular; `start`/`tick`/`togglePause`/`skipTransition`/`goTo`); `AppViewModel` com `mobilityState` + `mobilityJob` (`startMobilityRoutine`, `startMobilityFromPreset` sem sobrescrever rotina em andamento, `toggleMobilityPause`, `mobilityPreviousMovement`, `mobilityNextMovement`, `clearMobilitySession`) e `runPlanTarget` combinando `activeSession` + `activePlan` via `runPlanTargetFor`; `WorkoutScreen` guarda só os ids (`exerciseToLogId`/`editSetId`) em `rememberSaveable` e rederiva os objetos da sessão; `SetDialog` com `rememberSaveable(key = key)` por diálogo; `LogActivityScreen` com `ActivityLogDraft` + `ActivityLogDraftSaver` (`listSaver`); `RunWorkoutScreen` com campos em `rememberSaveable`, cartão "Alvo do plano" no topo (`RunPlanTarget`/`formatRunPlanTarget`), distância em km (`parseDistanceKm`/`runDistanceKmToMeters`, uma casa decimal, vírgula ou ponto) e "Cancelar" com confirmação; mobilidade com "Descartar rotina?" na volta e player com transição de 5 s; `formatRunDistanceKm` (km a partir de 1 km, metros abaixo) em Home e Histórico; testes `RunLogTest` (2: km→m, km inválido), `MobilityTransitionTest` (2: transição de 5 s, pular transição), `ActivityLogSaverTest` (round-trip do `Saver`), `WorkoutScreenTest.dialogo_preserva_campos_apos_recreate` e `RunMetricsTest` ajustado ao contrato em km; [spec](docs/specs/H3-31-estado-sobrevive.md) |
| H3-32 | Componentes e consistência visual | `BusyButton` (spinner 18 dp em `LocalContentColor`, desabilitado ocupado) e `StatusText` (sucesso = primary + check, erro = error + alerta) em `Common.kt`, com `statusIsSuccess` puro por prefixo — 9 botões ocupados (7 em Ajustes, Generate, LogActivity) e 7 mensagens de Ajustes migrados; player de mobilidade com PlayArrow/Pause/SkipNext (`AppIcons` com vetores locais — sem `material-icons-extended`, release não usa minify) e `add` em "Registrar série"; `ExerciseImage` com `SubcomposeAsyncImage` + `clip(shapes.small)` + crossfade + fallback de letra escalado (nunca quadrado vazio), `contentDescription` no `semantics` do container para valer também em loading/erro; `ExerciseDetailSheet` com `muscleLabel`/`equipmentLabel` em PT e `fillMaxWidth().aspectRatio(1f)`; "Em andamento" em `tertiary`, chips do histórico em `FlowRow`, ellipsis onde a linha única é intencional na Home, `spacedBy(12.dp)` no Generate; `catalogIdForMovement` liga 19 movimentos de mobilidade aos IDs reais do catálogo stretching (respiração/caminhada ficam no fallback de letra) e `exerciseMedia` chega ao player via `TreinosApp`; ícone do app trocado pela arte `treinos.jpeg` (foreground na safe zone de 66% nos 5 buckets + round + legado colorido; background/tema intocados) como seção extra da spec por decisão do dono; testes `A11yTest` (+2, incluindo `A11yComposeTest`), `HistoryScreenTest.chips_em_flowrow_nao_transbordam`, `ExerciseImageTest` (2, Coil com interceptor de falha) e `MobilityRoutineTest` (+3 contra os 123 IDs stretching); [spec](docs/specs/H3-32-componentes-consistencia.md) |
| H3-33 | Tema, motion e formatação | `values-night/themes.xml` com `windowBackground` escuro (`#121212`) eliminando o flash branco no dark mode; dynamic color em SDK ≥ 31 com fallback verde para SDK < 31 (mantendo `ThemeColors` do `ContrastTest`); `transitionSpec` do `AnimatedContent` dirigido por `motionFor`/`motionDurationMillis`; formatadores `formatDecimal`/`formatKg` com locale fixo pt-BR; testes `FormattersTest` e casos de direção em `MotionTest`; [spec](docs/specs/H3-33-tema-motion-formatters.md) |
| H3-34 | Acessibilidade dinâmica e contraste real | `WorkoutScreen` (`liveRegion` início do descanso + anúncio "Série N salva — X reps, Y kg"; scroll no `SetDialog`), `GenerateScreen` (`toggleable(role = Checkbox)` no Row da modalidade + `supportingText` "Inteiro de 1 a 7" com filtro 1–7), `MobilityScreen` (`liveRegion` no marco do timer; `verticalScroll`), `RunWorkoutScreen` (`verticalScroll`), `SettingsScreen`/`Common.kt` (`liveRegion = Polite` nas mensagens de `StatusText`), `Contrast.kt` (blend de alpha) + `Theme.kt` (consts de cor), `ContrastTest` (selo IA, error/tertiary, errorContainer, alpha blend) e `A11yTest` (checkbox role/rótulo, liveRegion descanso/mobilidade, supporting text sessões); testes `ContrastTest` (+3) e `A11yTest` (+4); [spec](docs/specs/H3-34-acessibilidade-dinamica.md) |
| H3-17 | Alvo de reps e última série no pulso | Room v6 aditiva (`AppDatabase.MIGRATION_5_6`, schema `6.json`): `SessionExerciseEntity.targetReps` persistido; `WorkoutRepository.startSession` copia `WorkoutPlanExerciseEntity.targetReps` para a sessão; `WearSessionMapper` publica `targetReps` (não mais vazio) e `lastWeightKg/lastReps`; relógio mostra "Alvo: ..." só quando há valor e "Última: ..." via `WearFormat.formatLastSet` (kg inteiro/fracionário com vírgula, reps); testes `MigrationTest.migracao_5_6_preserva_linhas`, `WearSessionMapperTest` (alvo/última série), `WearFormatTest`; [spec](docs/specs/H3-17-alvo-reps-ultima-serie-pulso.md) |
| H3-18 | Timer de descanso contando no pulso | `WearTimer.syncFromState` pura decide Reset/Skip a partir de `WearWorkoutState` com chave `sessionId:exerciseIndex:setNumber:restRemainingSeconds`; `WorkoutWearScreen` mantém `WearTimerState` com `LaunchedEffect` de tick de 1 s e renderiza o descanso local; botão "Pular Descanso" aplica `Skip` local e envia `SKIP_REST`; `AppViewModel.startRest` grava `_restRemaining` síncrono antes do loop e `skipRest` publica estado; testes `WearTimerTest` (5 novos de sincronização); [spec](docs/specs/H3-18-timer-descanso-pulso.md) |

### Horizon 1 — Agora (próximas 2-4 semanas): proteger dados e fechar o core

Nada pendente: H1-1..H1-7 estão concluídos e listados em §4.1.

### Horizon 2 — Próximo (1-2 meses): multimodal, conectividade e histórico

Nada pendente: H2-1..H2-7 estão concluídos e listados em §4.1. A fila de produto
do horizonte 2 está fechada; o foco passa para qualidade/Horizon 3.

### Correções prioritárias (fixes — vêm ANTES de qualquer feature)

Reportadas em uso real em 2026-11-07 e confirmadas no código. **Prioridade
maior que qualquer feature listada neste roadmap, incluindo o MVP do relógio
(H3-17–H3-20).** Ordem de execução = ordem da tabela. **A fila está concluída:**
H3-21 (diagnóstico do fallback), H3-22 (robustez da IA), H3-23 (foco no
principal) e H3-24 (mídia do exercício) estão entregues — o log
`[fallback] reason=...`, o `fallbackReason` no contrato, o pool completo no
prompt, a prioridade de foco nas sessões de força e a mídia persistida com o
catálogo resiliente confirmam e corrigem as causas reais.

H3-21, H3-22, H3-23 e H3-24 estão concluídos e listados em §4.1.

A H3-14 (proposta, Could) continua **substituída** pela H3-24 e não será
implementada separadamente.

### Horizon 3 — Depois (2-4 meses): evolução e qualidade

| ID | Item | MoSCoW | Problema | Solução prevista | Esforço | Depende de | Spec |
|----|------|--------|----------|------------------|---------|------------|------|
| H3-17 | Alvo de reps e última série no pulso | **M** (concluída) | Relógio mostrava "Alvo: " vazio e ignorava a última carga/reps. | `session_exercises.targetReps` persistido (Room v6, `MIGRATION_5_6`), `startSession` copia do plano, mapper publica alvo e a tela mostra "Alvo"/"Última" (`WearFormat`). | 0,5 dia | H2-2 | [H3-17](docs/specs/H3-17-alvo-reps-ultima-serie-pulso.md) (concluída) |
| H3-18 | Timer de descanso contando no pulso | **M** (concluída) | `WearTimer` não estava ligado à tela; o número era estático. | `WearTimer.syncFromState` pura, `WorkoutWearScreen` com tick de 1 s e descanso local, `startRest` síncrono + `skipRest` publicando estado; [spec](docs/specs/H3-18-timer-descanso-pulso.md) (concluída) | 0,5 dia | H2-2 | [H3-18](docs/specs/H3-18-timer-descanso-pulso.md) (concluída) |
| H3-19 | Corrida e mobilidade no relógio | **M** | Sessões sem exercícios (H3-11) viram tela vazia no relógio. | Campo aditivo `modality` no `:protocol`; relógio distingue o tipo da sessão e mostra estado coerente (nome + "Em andamento" + finalizar). | 1 dia | H3-11 | [H3-19](docs/specs/H3-19-corrida-mobilidade-relogio.md) (proposta) |
| H3-20 | Robustez de conexão do relógio | **S** | Sem selo "Sem conexão", sem debounce e fila offline guarda só IDs. | Selo via capability + debounce 2 s + fila real de comandos reenviada ao reconectar. | 1–1,5 dia | H2-2 | [H3-20](docs/specs/H3-20-robustez-conexao-relogio.md) (proposta) |
| H3-8 | Backup na nuvem do usuário (SAF) | **C** | O usuário quer o backup no OneDrive/Drive/Dropbox dele. | Nenhum SDK, nenhum OAuth: o `CreateDocument`/`OpenDocument` já elenca os provedores do sistema. Validar com os três provedores, ligar o feedback de leitura (`BackupImportState.Reading`) e escrever o texto de ajuda. | 0,5 dia | H1-7, H3-6 | [H3-8](docs/specs/H3-8-cloud-saf-backup.md) |
| H3-14 | ~~Imagens dos exercícios no plano~~ **substituída pela H3-24** | **C** | Coberta pela H3-24 (mídia do plano + catálogo resiliente). | Não implementar separadamente. | — | H1-3 | [H3-24](docs/specs/H3-24-midia-exercicio-plano.md) |
| H3-4 | Backend na nuvem grátis (Oracle/Codespaces) | **W** | Backend só roda no PC local. | Deploy + Cloudflare Tunnel; toggle "Usar back remoto". Exige token antes de expor publicamente. | 2 dias | Backend Docker, `.env` seguro, **H3-10** | [H3-4](docs/specs/H3-4-cloud-backend.md) |
| H3-9 | Backup automático no destino escolhido | **W** | Reenviar sem abrir o seletor de novo. | URI persistida (`takePersistableUriPermission`, zero ocorrências hoje) + WorkManager + último upload visível + desligar ao perder permissão. **Nunca** importar automaticamente: o import é destrutivo. | 3 dias | H3-6, H3-8 | [H3-9](docs/specs/H3-9-auto-cloud-backup.md) |
| H3-25 | Seleção de exercícios por nome com resolução determinística | **C** | Exigir IDs exatos do catálogo é atrito: LLMs erram menos nomes do que IDs sintéticos, e o pool completo (H3-22) cresce o prompt. | O prompt passa a aceitar seleção por nome canônico; o backend resolve nome→ID com o índice existente (normalização + fuzzy do `resolve_plan_ids`); nome não resolvível segue o salvamento parcial da H3-22. Contrato de saída não muda (`exerciseId` continua preenchido na resposta). | 1 dia | H3-22 | [H3-25](docs/specs/H3-25-selecao-por-nome.md) |
| H3-26 | Loop do descanso: avisar, ficar visível e contar tempo real | **M** | O timer rola para fora da tela, não avisa quando acaba (sem som/vibração), a tela apaga, não pausa nem estende, morre no processo morto e conta por tick (atrasa em Doze); registrar série custa 2 toques mesmo sem mudar a carga. | Vibração/beep ao zerar (regra por ringer), faixa fixa com Pular/+15 s/Pausar, `keepScreenOn` na sessão, wall-clock com deadline persistido, háptico ao salvar série e botão "Repetir última série" de 1 toque. | 1,5–2 dias | — | [H3-26](docs/specs/H3-26-loop-descanso.md) (proposta) |
| H3-28 | Home do dia e histórico escaneável | **M** | O treino de hoje fica abaixo da dobra (~700 dp de cards antes), nada marca "hoje", a aba inicial cai no amanhã em dia de descanso, e no histórico a lista filtrada fica ~1 tela abaixo dos filtros. | Card-herói "Hoje" com "Iniciar" direto, badge "Hoje", aba preferindo hoje, ações secundárias compactas, rodapé só sem plano, destrutivo no overflow e chips do histórico logo após o calendário. | 1 dia | — | [H3-28](docs/specs/H3-28-home-do-dia-historico.md) |
| H3-29 | Estados e erros padronizados | **M** | Home/Histórico mostram "vazio falso" antes do Room emitir, todo erro é snackbar de ~4 s sem retry e com o mesmo visual de sucesso, e corpo HTTP cru chega à UI. | `UiState` Loading/Empty/Content, erro persistente na tela de origem com "Tentar de novo", mensagens acionáveis com detalhe só no Logcat. | 1 dia | — | [H3-29](docs/specs/H3-29-estados-erros.md) |
| H3-30 | Geração sem atrito | **M** | Primeiro treino exige formulário de ~14 seções com CTA no fundo e nada pré-selecionado; geração = spinner de 18 dp por até 90 s sem cancelar; fallback chega como snackbar fugaz; goal do chip pode ser ignorado. | Pré-incluir Musculação/última config, CTA fixo, "Mais opções" colapsável, seções de força condicionais, overlay com tempo + Cancelar e motivo do fallback persistido. | 1 dia | — | [H3-30](docs/specs/H3-30-geracao-sem-atrito.md) (concluída) |
| H3-31 | Estado sobrevive a rotação e processo | **M** | Zero `rememberSaveable`: rotação apaga diálogo de série, corrida, registro avulso e a rotina de mobilidade inteira; corrida não mostra o alvo do plano e pede metros. | `rememberSaveable`/`Saver` nos formulários, estado de mobilidade no ViewModel, transição de 5 s entre movimentos, alvo do plano e km na corrida. | 1 dia | H3-27 | [H3-31](docs/specs/H3-31-estado-sobrevive.md) (concluída) |
| H3-32 | Componentes e consistência visual | **S** | "Botão ocupado" copiado ~10 vezes, sucesso e falha na mesma cor, 7 ícones no app inteiro, imagem sem placeholder/erro e cantos/espaçamentos inconsistentes. | `BusyButton` e `StatusText` em Common.kt, ícones no player e na ação principal, "Em andamento" fora do vermelho, `ExerciseImage` resiliente, `spacedBy(12.dp)` e ellipsis/FlowRow. | 1 dia | — | [H3-32](docs/specs/H3-32-componentes-consistencia.md) (concluída) |
| H3-36 | Catálogo bilíngue PT/EN (mesmos IDs) | **M** | Nomes e instruções de exercícios só em inglês; o dataset PT-BR (fork CC0 do free-exercise-db) tem 873/876 IDs idênticos e não é aproveitado. | Catálogo com `namePt`/`instructionsPt` aditivos (merge por ID via script validado), `GenerateRequest.language` (ausente ⇒ `en`), mismatch tolerante a nome PT e app resolvendo mídia por idioma. | 1 dia | — | [H3-36](docs/specs/H3-36-catalogo-bilingue.md) (concluída) |
| H3-38 | Navegação em pilha e retomada | **M** | "Voltar" sempre vai para Home (salvar edição vinda do Histórico abandona o usuário lá), Histórico/Ajustes são TextButtons no topo (pior zona de alcance) e não há como retomar o treino em 1 toque. | Pilha mínima origem→destino no ViewModel, bottom bar Treinar/Histórico/Ajustes nos hubs e shortcut dinâmico de retomada. | 1 dia | — | [H3-38](docs/specs/H3-38-navegacao-pilha.md) (concluída) |
| H3-39 | Imagens dos alongamentos (mobilidade) | **C** | Movimentos de mobilidade não têm imagem quando o catálogo remoto não carrega (offline), deixando quadrado vazio/fallback de letra. | Drawables locais (`imageRes`) como fallback offline; catálogo remoto vence quando disponível; 16 drawables vetoriais em `res/drawable`; nenhum cambio de Room/backend. | 0,5 dia | H2-7, H3-32 | [H3-39](docs/specs/H3-39-imagens-mobilidade.md) (concluída) |
| H3-40 | Geração assíncrona: pedir agora, receber pronto | **M** | A IA quase sempre estoura o orçamento síncrono de 80 s e o dono recebe o fallback local; a geração prende o usuário na tela por até 90 s. | Job no backend (202 + `jobId`, status/cancel, sweep de interrompidos), orçamento de IA de 600 s; app sem overlay: card na Home com Atualizar/Cancelar, poll no refresh e na volta ao foreground, envelope sobrevive a processo morto. Offline-first intacto: sem WorkManager, sem permissão nova, chave só no backend. | 1,5–2 dias | H3-22, H3-30 | [H3-40](docs/specs/H3-40-geracao-assincrona.md) (implementado) |
| H3-43 | Troca de exercício durante o treino em andamento | **M** | Durante o treino não há como trocar um exercício que o usuário não quer/pode fazer; a única opção é abandonar a sessão inteira. | Botão "Trocar exercício" em cada card da sessão ativa (WorkoutScreen): substituto do mesmo músculo primário e equipamento informado, 1-para-1, sem mudar séries/reps/ordem. Diálogo de escopo: "Só hoje" (atualiza só a sessão atual, não mexe no plano) ou "Todos" (atualiza o plano salvo = todos os treinos futuros). Não afeta sessão em andamento quando escopo é ALL_FUTURE. Evento de uso registrado para calibrar a próxima geração (H3-42). Testes `SessionSwapTest` (JVM). | 1,5 dias | H3-41, H3-42 | (pedido do usuário, sem spec formal) |
| H3-44 | Substitutos de exercício disponíveis offline | **M** | O diálogo "Trocar exercício" mostra "Carregando catálogo…" e depende de rede: com o cache expirado o app espera o timeout do backend e, sem cache/na primeira instalação, não há substituto nenhum. | `loadLocal()` cache-primeiro (cache expirado ou ausente → asset embarcado compacto), TTL só dispara refresh em background; `AppViewModel` publica o catálogo local antes da rede; `WorkoutScreen` não mostra spinner e, sem catálogo, exibe estado explícito com "Atualizar". Sem mudança de Room. | 1 dia | H3-41, H3-43, H3-24, H1-3 | [H3-44](docs/specs/H3-44-substitutos-offline.md) (concluída) |
| H3-45 | Backup preserva mídia e a troca de plano reflete na Home | **M** | Exportar/importar backup perdia instruções e imagens dos exercícios; `resolveExerciseMedia` deixava o catálogo offline (sem mídia) esconder a mídia do plano; e um novo `main` não era selecionado quando um `extra` estava selecionado. | `BackupPlanExercise` ganha `instructions`/`images` (aditivo, sem bump); `resolveExerciseMedia` funde catálogo + plano campo a campo; import limpa a seleção e geração/aceitação de `main` seleciona o novo plano; `extra` preserva. | 1 dia | H3-24, H3-15 | [H3-45](docs/specs/H3-45-backup-midia-e-substituicao.md) (concluída) |

> H3-26..H3-39 nasceram da auditoria UX/UI de 2026-11-07
> ([docs/auditoria-ux-ui-2026-11-07.md](docs/auditoria-ux-ui-2026-11-07.md)) e do
> pedido de app bilíngue PT/EN — todas as specs estão em `pendente de
> aprovação` e nenhuma entra em execução antes da aprovação.

H3-1..H3-3, H3-5, **H3-6**, **H3-7**, **H3-10**, **H3-11**, **H3-12**, **H3-13**, **H3-15**
e **H3-16**, além de **H3-40**, **H3-41**, **H3-42**, **H3-43**, **H3-44** e **H3-45**, estão concluídos e listados em §4.1 — com H3-6, restaurar um backup
deixou de apagar o perfil; com H3-7, o perfil ganhou o sexo opcional sem tocar nas
regras numéricas; com H3-10, o backup automático do Android segue política
explícita (banco e prefs fora da nuvem) em vez do default do sistema; com H3-11,
a geração híbrida de cada modalidade vira uma sessão
independente com abas próprias (H2-6/H2-7); com H3-12, o volume (`sets × reps`)
aparece nos cards da Home; com H3-13, o título de cada sessão de força reflete o
split aplicado; com H3-15, planos principais e complementares coexistem na Home
com seletor; com H3-16, o plano complementar respeita foco exclusivo e
casa/peso corporal de ponta a ponta (contrato, fallback, IA e UI).
H3-4 e H3-9 continuam **Won't now** até priorização explícita.

### Roadmap do relógio (Wear OS)

O H2-2 entregou a base: módulo `:wear` no gate, Data Layer celular → relógio
(`WearWorkoutState`) e comandos do pulso (concluir série, pular descanso,
finalizar). O MVP do relógio, porém, não está fechado — o código atual tem
lacunas que aparecem no uso real. As specs pendentes do projeto (H3-8, H3-4,
H3-9) são detalhes futuros do **celular**; as **correções de geração e mídia
(H3-21–H3-24) vêm antes do MVP do pulso** (ver "Correções prioritárias" no
§4), e depois o foco volta a ser fechar o MVP do relógio.

**MVP do relógio (agora):**

| ID | Item | MoSCoW | Problema (evidência no código) | Solução prevista | Esforço | Depende de |
|----|------|--------|-------------------------------|------------------|---------|------------|
| H3-17 | Alvo de reps e última série no pulso | **M** | `WearSessionMapper` envia `targetReps = ""` — a tela mostra "Alvo: " vazio; `lastWeightKg`/`lastReps` viajam no protocolo mas a UI não exibe. | **Concluída:** `session_exercises.targetReps` persistido (Room v6, `MIGRATION_5_6`), `startSession` copia do plano, mapper publica alvo e a tela mostra "Alvo"/"Última" (`WearFormat`). | 0,5 dia | H2-2 |
| H3-18 | Timer de descanso contando no pulso | **M** | `WearTimer` (reducer puro, com testes) existe mas não é usado pela `WorkoutWearScreen`; o número na tela só muda quando chega estado novo do celular. | Ligar o reducer à tela: tick por segundo, reset com estado novo, `Skip` ligado ao `SKIP_REST`. | 0,5 dia | H2-2 |
| H3-19 | Corrida e mobilidade no relógio | **M** | Com a H3-11, corrida/mobilidade são sessões sem exercícios; o mapper produz tela vazia ("Série 1/0", nome em branco) nesses treinos. | O relógio distingue o tipo da sessão: força continua como hoje; corrida/mobilidade mostram nome da sessão, "em andamento" e finalizar (o que mais aparece — ex. duração decorrida — fica na spec). | 1 dia | H3-11 |
| H3-20 | Robustez de conexão do relógio | **S** | Bordas da H2-2 que ficaram de fora: sem selo "Sem conexão" (borda 3), sem debounce de 2 s no botão (borda 5 — o dedupe por `commandId` não protege de toque duplo) e a "fila" em `WearStateStore` guarda só IDs, nada reenvia (borda 6). | Selo de conexão na tela, botão desabilitado 2 s após o toque e fila real de comandos reenviada em `onPeerConnected`, aplicada uma única vez. | 1–1,5 dia | H2-2 |

Os quatro itens são pequenos e de alto valor: H3-17/H3-18/H3-20 não mudam o
contrato `:protocol` (os campos já existem); a H3-19 exige um campo aditivo
(`modality`), que é seguro porque os dois lados decodificam com
`ignoreUnknownKeys`. As quatro specs foram escritas em 2026-11-07 a partir de
[_TEMPLATE.md](docs/specs/_TEMPLATE.md) e aguardam aprovação — nenhuma entra em
execução antes disso.

**Depois do MVP (Won't now — nenhuma entra sem spec aprovada):** tiles,
complicações e widget; modo standalone (treinar sem o celular por uma sessão
inteira); editar carga/reps no relógio; métricas de corrida no pulso
(GPS/ritmo/FC — exigiria as permissões de sensor que a H2-2 explicitamente não
pede); notificações e watchface. São as melhorias detalhadas que o MVP não
precisa para ser útil amanhã.

---

## 5. Priorização consolidada (MoSCoW)

| Must Have (agora) | Should Have (próximo) | Could Have (depois) | Won't now |
|-------------------|-----------------------|---------------------|-----------|
| **Correções prioritárias: H3-21 ✅ H3-22 ✅ H3-23 ✅ H3-24 ✅ — fila concluída; segue o MVP do relógio** | | | |
| Fundação de dados (H1-6) ✅ | Registro avulso (H2-5) ✅ | Mobilidade (H2-7) ✅ | Backend na nuvem (H3-4) |
| Correções de restauração/backup (H1-7) ✅ | Plano híbrido (H2-6) ✅ | Polimento + animações (H3-2) ✅ | Export CSV/Sheets |
| Peso/altura (H1-5) ✅ | Descoberta LAN (H2-1) ✅ | Acessibilidade (H3-3) ✅ | Widget/complicação do relógio |
| Testes de UI/Room (H3-1) ✅ | Wear OS MVP (H2-2) ✅ | Nuvem via SAF (H3-8) | Backup automático (H3-9) |
| Execução por séries (H1-2) ✅ | Mídia dos exercícios (H1-3) ✅ | Nuvem via SAF (H3-8) | |
| Perfil no backup (H3-6) ✅ | Sexo opcional no perfil (H3-7) ✅ | | |
| Correção híbrida por sessão (H3-11) ✅ | Regras de backup do Android (H3-10) ✅ | | |
| Séries/reps visíveis (H3-12) ✅ | | | |
| Título varia conforme split (H3-13) ✅ | | | |
| Múltiplos planos (H3-15) ✅ | Geração complementar (H3-16) ✅ | | |
| Fechar o MVP do relógio (H3-17–H3-20) | | | |
| Geração assíncrona (H3-40) — implementado (2026-11-10) | | | |

---

## 6. Esforço e caminho crítico

```
Concluído:  H1-1..H1-7 (core, fundação de dados, restauração/backup) + H2-1..H2-7
            (Wear, download, descoberta LAN, histórico, registro avulso,
            plano híbrido, mobilidade) + H3-1/H3-2/H3-3/H3-5/H3-6/H3-7/H3-10/
            H3-11/H3-12/H3-13/H3-15/H3-16 (qualidade, progressão, perfil no
            backup/sexo, rules do Android ligadas, sessões híbridas por
            modalidade, volume visível, títulos por split, múltiplos planos na
            Home e geração complementar) + H3-21/H3-22/H3-23/H3-24 (diagnóstico do
            fallback, robustez da IA, foco muscular priorizado no principal e
            mídia do exercício com plano como fonte + catálogo resiliente)
Agora:      MVP do relógio H3-17 → H3-18 → H3-19 → H3-20 (fila de correções
            prioritárias H3-21–H3-24 concluída)
Depois:     H3-8 (nuvem via SAF)
            e H3-25 (seleção por nome na geração) continuam Could, sem
            prioridade agora
Qualidade:  H3-4 nuvem/token e H3-9 backup automático (Won't now até priorizar)
```

**Caminho crítico (produto):** H1-6 ✅ → H1-7 ✅ → H2-5 ✅ → H2-1 ✅ → H2-6 ✅ → H2-7 ✅ → H3-5 ✅ → H3-6 ✅ → H3-11 ✅ → H3-12 ✅ → H3-13 ✅ → H3-15 ✅ → H3-16 ✅ → **H3-21 ✅ → H3-22 ✅ → H3-23 ✅ → H3-24 ✅**.
O elo atual é o **MVP do relógio**: com as correções prioritárias
H3-21/H3-22/H3-23/H3-24 entregues (diagnóstico + robustez da IA + foco
priorizado no principal + mídia do exercício), segue H3-17 → H3-18 → H3-19 →
H3-20.
O elo de **qualidade/plataforma** estava em **H3-10** (concluída): com as rules
do Android ligadas e o nome do banco conferido por teste, o backup de plataforma
deixou de ser um caminho não escolhido — e a H3-4 perdeu seu pré-requisito.
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
| BT instável celular ↔ relógio | Média | Médio | Hoje o relógio mantém o último estado, mas a "fila" guarda só IDs e o comando pode se perder; H3-20 entrega fila real, selo de conexão e debounce |
| Android 15+ restringe LAN | Baixa | Alto | Manter fallback manual de IP; testar em preview |
| Desinstalar app apaga dados locais | Alta | Alto | H1-4 (backup JSON) + H1-1 (restore por deviceId); H1-7 ligou os botões e protege contra snapshot vazio |
| Restauração/backup apagar dados | Baixa | Alto | Mitigado em H1-7: validação do snapshot antes de apagar, transação única (`replaceAllData`), snapshot vazio não substitui e `deviceId` só após sucesso |
| “Dor nas costas” virar promessa médica | Média | Alto | H2-7: aviso, sinais de alerta e nenhum diagnóstico |
| Contrato v1/v2 divergir | Média | Médio | Fixtures v1/v2 e testes de contrato no gate |
| Perfil (peso/altura) fora do backup — restaurar apaga o perfil | Baixa | Médio | **Mitigado em H3-6:** `BackupProfile` no `schemaVersion` 3; v1/v2 importados sem limpar o que o arquivo não contém |
| Arquivo de backup passa a conter dados pessoais (peso/altura/sexo) | Média | Médio | Texto de Ajustes que avisa antes de exportar/compartilhar (H3-6), `MANUAL.md` §7 com os três campos que saem do aparelho (H3-7) e H3-10, que mantém o auto-backup do Android fora do caminho até haver policy explícita |
| Sexo vira regra determinística ou prescrição médica | Baixa | Alto | H3-7: campo opcional só no prompt; `profile_adjustments`/`suggest_initial_load` não recebem `sex` e `test_sexo_ignorado_no_plano_local` quebra se alguém ligar os dois |
| Backup automático do Android sem política | Alta | Médio | **Mitigado em H3-10:** rules referenciadas no manifest, nomes conferidos por teste (`BackupRulesTest`) contra as constantes de `AppDatabase`/`AppPreferences` e política explícita antes de a H3-4 existir |

---

## 9. Próximo passo imediato

Horizons 1 e 2 estão concluídos (H1-1..H1-7, H2-1..H2-7). Na sequência, H3-1..H3-3,
H3-5..H3-7, H3-10..H3-13, H3-15 e H3-16 também estão entregues: qualidade/testes,
progressão, perfil completo no backup (peso/altura/sexo), rules de backup do
Android ligadas com política explícita, geração híbrida por sessão
com abas, volume (`sets × reps`) visível nos cards, título de força por split,
múltiplos planos coexistindo na Home e geração complementar com foco exclusivo e
casa/peso corporal.

**Agora (Must — MVP do relógio):** **H3-17** (alvo de reps e última série no
pulso), **H3-18** (timer de descanso contando no relógio), **H3-19**
(corrida/mobilidade com tela coerente) e **H3-20** (selo de conexão, debounce
e fila offline real). As quatro specs foram escritas em 2026-11-07
([H3-17](docs/specs/H3-17-alvo-reps-ultima-serie-pulso.md),
[H3-18](docs/specs/H3-18-timer-descanso-pulso.md),
[H3-19](docs/specs/H3-19-corrida-mobilidade-relogio.md) e
[H3-20](docs/specs/H3-20-robustez-conexao-relogio.md)) e aguardam aprovação
antes de implementar.
**Implementado pelo dono (2026-11-10):** **H3-40** (geração assíncrona — pedir o
treino, sair do app e receber pronto ao voltar; [spec](docs/specs/H3-40-geracao-assincrona.md))
nasceu do uso real: a IA quase sempre estoura os 80 s e entrega fallback.
Aguarda aprovação; a ordem de execução frente ao MVP do relógio é decisão do
dono na aprovação.
**Correções prioritárias concluídas:** H3-21 (diagnóstico do fallback da IA),
H3-22 (robustez da geração por IA), H3-23 (foco muscular no plano principal) e
H3-24 (imagens e descrição do exercício vindas do plano + catálogo resiliente;
substitui a H3-14) — todas listadas em §4.1.
**Could (specs prontas, sem prioridade agora):** **H3-8** (validar/documentar
SAF com Drive/OneDrive/Dropbox — sem SDK).
**Won't now:** H3-4 (backend na nuvem com token — pré-requisito H3-10 já
cumprido), H3-9 (backup automático) e as funções futuras do relógio
(tiles/complicação, standalone, edição no pulso, métricas de corrida).

Nenhuma das specs acima listadas como propostas está aprovada para execução. Para
iniciar qualquer item, leia a spec em [docs/specs/](docs/specs/); se estiver
pendente, ajuste a partir de [docs/specs/_TEMPLATE.md](docs/specs/_TEMPLATE.md) e
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
- [H3-11 — Geração híbrida por sessão e abas por modalidade](docs/specs/H3-11-hybrid-sessions.md) — concluída
- [H3-12 — Séries/reps visíveis em cada exercício](docs/specs/H3-12-series-reps-visiveis.md) — concluída
- [H3-13 — Título da sessão varia conforme split](docs/specs/H3-13-titulo-sessao-split.md) — concluída
- [H3-14 — Imagens dos exercícios no plano](docs/specs/H3-14-imagens-exercicios.md) — **substituída pela H3-24** (ver linha acima)
- [H3-8 — Backup na nuvem do usuário via SAF](docs/specs/H3-8-cloud-saf-backup.md) — proposta, aguardando aprovação
- [H3-9 — Backup automático no destino escolhido](docs/specs/H3-9-auto-cloud-backup.md) — proposta (Won't now)
- [H3-10 — Regras de backup do Android](docs/specs/H3-10-android-backup-rules.md) — concluída
- [H3-15 — Múltiplos planos coexistindo na Home](docs/specs/H3-15-planos-multiplos.md) — concluída
- [H3-16 — Geração complementar: foco exclusivo e casa/peso corporal](docs/specs/H3-16-geracao-complementar.md) — concluída
- [H3-21 — Diagnóstico do fallback da IA](docs/specs/H3-21-diagnostico-fallback-ia.md) — concluída
- [H3-22 — Robustez da geração por IA](docs/specs/H3-22-robustez-geracao-ia.md) — concluída
- [H3-23 — Foco muscular respeitado no plano principal](docs/specs/H3-23-foco-muscular-plano-principal.md) — concluída
- [H3-24 — Imagens e descrição: plano como fonte + catálogo resiliente](docs/specs/H3-24-midia-exercicio-plano.md) — concluída (**substitui a H3-14**)
- [H3-25 — Seleção de exercícios por nome com resolução determinística](docs/specs/H3-25-selecao-por-nome.md) — proposta, aguardando aprovação (evolução da geração por IA; depende da H3-22)
- [H3-14 — Imagens dos exercícios no plano](docs/specs/H3-14-imagens-exercicios.md) — **substituída pela H3-24** (não implementar separadamente)
- [H3-17 — Alvo de reps e última série no pulso](docs/specs/H3-17-alvo-reps-ultima-serie-pulso.md) — proposta, aguardando aprovação (Room v5 aditiva; contrato `:protocol` intocado)
- [H3-18 — Timer de descanso contando no pulso](docs/specs/H3-18-timer-descanso-pulso.md) — proposta, aguardando aprovação (liga o `WearTimer` à tela; ajusta `startRest`/`skipRest` no celular)
- [H3-19 — Corrida e mobilidade no relógio](docs/specs/H3-19-corrida-mobilidade-relogio.md) — proposta, aguardando aprovação (campo aditivo `modality` no `:protocol`)
- [H3-20 — Robustez de conexão do relógio](docs/specs/H3-20-robustez-conexao-relogio.md) — proposta, aguardando aprovação (capability + debounce + fila real)
- [H3-26 — Loop do descanso](docs/specs/H3-26-loop-descanso.md) — proposta, aguardando aprovação (vibração/beep, faixa fixa, keepScreenOn, wall-clock, repetir última série)
- [H3-27 — Integridade do registro](docs/specs/H3-27-integridade-registro.md) — concluída (validação de série, editar/excluir, confirmar finalizar, abandonar, sessão fantasma da mobilidade)
- [H3-28 — Home do dia e histórico escaneável](docs/specs/H3-28-home-do-dia-historico.md) — concluída (card-herói "Hoje", badge, aba preferindo hoje, chips do histórico)
- [H3-29 — Estados e erros padronizados](docs/specs/H3-29-estados-erros.md) — concluída (`UiState` Loading/Empty/Content, erro persistente com "Tentar de novo", mensagens acionáveis)
- [H3-30 — Geração sem atrito](docs/specs/H3-30-geracao-sem-atrito.md) — concluída (defaults, CTA fixo, overlay com cancelar, fallback persistido)
- [H3-31 — Estado sobrevive a rotação e processo](docs/specs/H3-31-estado-sobrevive.md) — concluída (`rememberSaveable`/`Saver` nos formulários, mobilidade no ViewModel com transição de 5 s, alvo do plano e km na corrida)
- [H3-32 — Componentes e consistência visual](docs/specs/H3-32-componentes-consistencia.md) — concluída (`BusyButton`, `StatusText`, ícones, `ExerciseImage` resiliente, mídia de alongamento e troca do ícone do app na mesma spec)
- [H3-33 — Tema, motion e formatação](docs/specs/H3-33-tema-motion-formatters.md) — proposta, aguardando aprovação (`values-night`, dynamic color, ligar o Motion, locale fixo)
- [H3-34 — Acessibilidade dinâmica e contraste real](docs/specs/H3-34-acessibilidade-dinamica.md) — proposta, aguardando aprovação (`liveRegion`, `toggleable`, `ContrastTest` estendido)
- [H3-35 — Ajustes organizados e copy do usuário](docs/specs/H3-35-ajustes-copy.md) — proposta, aguardando aprovação (seções colapsáveis, AlertDialog no import, 5 km em mm:ss, shortcut)
- [H3-36 — Catálogo bilíngue PT/EN](docs/specs/H3-36-catalogo-bilingue.md) — concluída (`namePt`/`instructionsPt` aditivos via script validado, `GenerateRequest.language` ausente ⇒ `en`, mismatch duplo EN/PT, mídia do app por idioma)
- [H3-38 — Navegação em pilha e retomada](docs/specs/H3-38-navegacao-pilha.md) — proposta, aguardando aprovação (pilha origem→destino, bottom bar, shortcut de retomada)
- [H3-40 — Geração assíncrona: pedir agora, receber pronto](docs/specs/H3-40-geracao-assincrona.md) — implementado (2026-11-10) (job no backend com orçamento de 600 s; app com poll sob demanda; substitui parcialmente o overlay da H3-30 e a invariante de 90 s da H3-22)
- [H3-41 — Troca de exercício em plano já gerado (swap)](docs/specs/H3-41-troca-exercicio.md) — proposta, aguardando aprovação (troca 1-para-1 mesmo músculo/equipamento, atômica, sem sessão em andamento)
- [H3-42 — Revisão de precisão da geração com base no uso](docs/specs/H3-42-revisao-precisao-uso.md) — concluída (evento de uso local → peso → ordena catálogo do prompt; nada sai do aparelho)
- [H3-44 — Substitutos de exercício disponíveis offline](docs/specs/H3-44-substitutos-offline.md) — concluída (catálogo local cache-primeiro + asset embarcado; swap sem "carregando" e sem rede)
- [H3-45 — Backup preserva mídia e a troca de plano reflete na Home](docs/specs/H3-45-backup-midia-e-substituicao.md) — concluída (mídia no backup, fusão catálogo+plano e seleção do novo main)

Um item sem spec não entra em execução. A spec é o contrato que impede um modelo
mais simples de "compilar verde e entregar a coisa errada".
