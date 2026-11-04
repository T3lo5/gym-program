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

## 2. Onde estamos hoje (atualizado em 2026-11-04)

| Área | Status | Evidência |
|------|--------|-----------|
| Banco local (Room) | ✅ | Planos, dias, exercícios, sessões, séries |
| Execução de treino | ✅ | Timer de descanso, séries, RPE, carga, histórico (H1-2) |
| Geração via IA | ✅ | Backend + SenseNova + fallback local |
| Sincronização app → backend | ✅ | Snapshot manual (`AppViewModel.syncNow`) |
| Restauração por `deviceId` | ✅ | `RestoreByDeviceId` valida o ID, baixa, só persiste o `deviceId` após sucesso e tem tela de confirmação em Ajustes (H1-7) |
| Download do backend | ✅ | `downloadSync` valida o snapshot antes de escrever e grava tudo em uma transação (`replaceAllData`); snapshot vazio mantém os dados locais (H1-7) |
| Testes + CI | 🟡 | 19 arquivos de teste JVM (`:app`, `:protocol`, `:wear`) + gate (`./scripts/verify.sh`); falta UI/instrumentado |
| Descoberta LAN | 📋 | mDNS no backend; app usa URL configurável |
| Wear OS | ✅ | Wear OS MVP (H2-2): módulo `:wear` no build, Data Layer + tela do relógio, dois APKs no gate |
| Backup/restore do usuário | ✅ | Exportar/importar ligados aos file pickers (SAF), pré-visualização + confirmação e `deviceId` do arquivo aplicado só após importação válida (H1-7) |
| Imagem/vídeo de exercício | ✅/❌ | Imagens via catálogo (H1-3); vídeo não |
| Migrações Room | ✅ | Room v2 com `MIGRATION_1_2` + `MigrationTest` (1→2 preservando linhas); `fallbackToDestructiveMigration` removido (H1-6) |
| Registro avulso (natação) | ✅ | `ActivityLog` valida e grava sessão manual (`source = "manual"`, `planId = dayId = 0`, zero exercícios); tela de registro/edição, card de atividade e filtro por modalidade no histórico, exclusão com confirmação (H2-5) |
| Plano híbrido / revisão por IA | 📋 | H2-6 (proposta) |
| Mobilidade / alongamento | 📋 | H2-7 (proposta) |
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
| H2-5 | Registro avulso genérico (natação primeiro) | `ActivityLog.kt` com `ActivityModality`, `ActivityLogInput`/`ActivityLogDraft`, `validateActivityLog` e pace; `WorkoutRepository.logActivity/updateActivity/deleteActivity/lastManualSession` sobre `WorkoutSessionEntity` com `source = "manual"`, `planId = dayId = 0` e zero exercícios; `LogActivityScreen` na rota `Screen.LogActivity`, botão "Registrar atividade" na Home, card de atividade + filtro por modalidade + exclusão com confirmação no histórico; `formatDistanceMeters`/`formatPacePer100m`/`formatPacePerKm`; testes `ActivityLogTest`, `ActivityFormatTest`, `ManualSessionDaoTest`, `BackupMapperTest.sessao_manual_round_trip` e `back/tests/test_sync_contract.py::test_sessao_manual_round_trip`; [spec](docs/specs/H2-5-adhoc-log.md) |

### Horizon 1 — Agora (próximas 2-4 semanas): proteger dados e fechar o core

Nada pendente: H1-1..H1-7 estão concluídos e listados em §4.1. O Horizon 1
fechou com H1-7 (restauração/backup não apagam mais dados e a UI está ligada);
H2-5 também já está em §4.1, então a fila continua em H2-1 e H2-4.

### Horizon 2 — Próximo (1-2 meses): multimodal, conectividade e histórico

| ID | Item | MoSCoW | Problema | Solução prevista | Esforço | Depende de | Spec |
|----|------|--------|----------|------------------|---------|------------|------|
| H2-6 | Plano híbrido e revisão por IA | **S** | Semana real mistura academia, corrida e natação; o app só planeja musculação. | Semana com modalidades e `weekday`, geração de corrida com templates, revisão por IA com histórico e fallback local. | 6-8 dias | H1-6, H2-5 (H2-7 opcional) | [H2-6](docs/specs/H2-6-hybrid-plan.md) (proposta) |
| H2-1 | Descoberta LAN automática (mDNS) | **S** | Usuário precisa digitar IP:porta do backend. | Backend anuncia `_treinos._tcp.local`; app lista instâncias e conecta com 1 toque. Fallback mantém IP manual. | 3 dias | `BackendDiscoveryManager`, `BackendClient`, tela de configuração | [H2-1](docs/specs/H2-1-lan-discovery.md) |
| H2-4 | Histórico com calendário e métricas | **C** | Histórico é lista simples. | Calendário, cards de treinos, total de séries, duração, volume por músculo e métricas por modalidade. | 4 dias | `HistoryScreen`, Room queries | [H2-4](docs/specs/H2-4-history-calendar.md) |
| H2-7 | Mobilidade e alongamento | **C** | Usuário quer rotina curta para costas/quadril, mas o app não oferece. | Rotinas curadas de 5-15 min com timer, aviso de segurança e registro no histórico. | 3-4 dias | H1-6 | [H2-7](docs/specs/H2-7-mobility.md) (proposta) |

### Horizon 3 — Depois (2-4 meses): evolução e qualidade

| ID | Item | MoSCoW | Problema | Solução prevista | Esforço | Depende de | Spec |
|----|------|--------|----------|------------------|---------|------------|------|
| H3-1 | Testes de UI/instrumentados + cobertura | **M** | Gate cobre lógica pura e build, mas não UI, Room nem migrações. Parcial: 16 arquivos JVM + gate. | Compose UI testing, Robolectric/instrumented para Room e migrações, testes de contrato app↔backend; manter Wear coberto. | 5 dias | Módulos estáveis | [H3-1](docs/specs/H3-1-ui-instrumented-tests.md) |
| H3-2 | Polimento visual e animações | **C** | Telas funcionais, sem identidade refinada. | Animações Compose, transições, tema consistente, loading/empty states. | 4 dias | `Theme.kt`, telas existentes | [H3-2](docs/specs/H3-2-visual-polish.md) |
| H3-3 | Acessibilidade (A11y) | **C** | Sem `contentDescription`, escala de fonte e TalkBack nas telas principais. | `contentDescription`, fonte escalável, navegação TalkBack nas 4 tabs. | 3 dias | Telas existentes | [H3-3](docs/specs/H3-3-accessibility.md) |
| H3-4 | Backend na nuvem grátis (Oracle/Codespaces) | **W** | Backend só roda no PC local. | Deploy + Cloudflare Tunnel; toggle "Usar back remoto". Exige token antes de expor publicamente. | 2 dias | Backend Docker, `.env` seguro | [H3-4](docs/specs/H3-4-cloud-backend.md) |
| H3-5 | Periodização e progressão de carga | **W** | Plano é estático; não evolui carga/séries. | Mesociclos, sugestão de carga por RPE/séries, alerta de revisão. | 5 dias | Histórico completo, H2-4 | [H3-5](docs/specs/H3-5-progression.md) |

---

## 5. Priorização consolidada (MoSCoW)

| Must Have (agora) | Should Have (próximo) | Could Have (depois) | Won't now |
|-------------------|-----------------------|---------------------|-----------|
| Fundação de dados (H1-6) ✅ | Registro avulso (H2-5) ✅ | Mobilidade (H2-7) | Backend na nuvem (H3-4) |
| Correções de restauração/backup (H1-7) ✅ | Plano híbrido (H2-6) | Histórico com calendário (H2-4) | Progressão automática de carga (H3-5) |
| Peso/altura (H1-5) ✅ | Descoberta LAN (H2-1) | Polimento + animações (H3-2) | Export CSV/Sheets |
| Testes de UI/Room (H3-1) | Wear OS MVP (H2-2) ✅ | Acessibilidade (H3-3) | Widget/complicação do relógio |
| Execução por séries (H1-2) ✅ | Mídia dos exercícios (H1-3) ✅ | | |

---

## 6. Esforço e caminho crítico

```
Concluído:  H1-1..H1-7 (core, fundação de dados, restauração/backup) + H2-2 Wear
            + H2-3 download + H2-5 registro avulso (natação/aula no histórico)
Agora:      H2-1 descoberta LAN      → conectar sem digitar IP:porta
Próximo:    H2-6 plano híbrido       → semana com academia + corrida + revisão
Depois:     H2-4 histórico, H2-7 mobilidade
Qualidade:  H3-1..H3-5
```

**Caminho crítico:** H1-6 ✅ → H1-7 ✅ → H2-5 ✅ → H2-6 (proposta, exige aprovação).
O gate (`./scripts/verify.sh`) roda em todo commit; H3-1 cobre o que ele ainda
não vê (UI, Room, migração e contrato).

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

---

## 9. Próximo passo imediato

Implementar **H2-1 (descoberta LAN automática, mDNS)** —
[docs/specs/H2-1-lan-discovery.md](docs/specs/H2-1-lan-discovery.md): o backend
anuncia `_treinos._tcp.local` e o app lista instâncias, mantendo o IP manual como
fallback. **H2-4 (histórico com calendário)** também está pronta para implementar.

H2-5 (registro avulso genérico, natação primeiro) está concluída: a sessão avulsa
é gravada como `WorkoutSessionEntity` com `source = "manual"`, `planId = dayId = 0`
e zero exercícios, tem resumo (duração, distância, pace, RPE, notas), edição,
exclusão com confirmação e filtro por modalidade no histórico, e sobrevive a
backup e sync (testes `ActivityLogTest`, `ActivityFormatTest`,
`ManualSessionDaoTest`, `sessao_manual_round_trip` no `BackupMapperTest` e no
contrato do backend).

**H2-6** e **H2-7** são propostas e não devem começar sem aprovação explícita.

Para iniciar qualquer item, leia a spec correspondente em
[docs/specs/](docs/specs/); se não existir, crie a partir de
[docs/specs/_TEMPLATE.md](docs/specs/_TEMPLATE.md).

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
- [H2-1 — Descoberta LAN](docs/specs/H2-1-lan-discovery.md) — pronta para implementar
- [H2-2 — Wear OS MVP](docs/specs/H2-2-wear-os.md) — concluída
- [H2-3 — Download do backend](docs/specs/H2-3-backend-download.md) — concluída (retroativa)
- [H2-4 — Histórico com calendário](docs/specs/H2-4-history-calendar.md) — pronta para implementar
- [H2-5 — Registro avulso](docs/specs/H2-5-adhoc-log.md) — concluída
- [H2-6 — Plano híbrido](docs/specs/H2-6-hybrid-plan.md) — proposta
- [H2-7 — Mobilidade](docs/specs/H2-7-mobility.md) — proposta
- [H3-1 — Testes de UI, Room e contrato](docs/specs/H3-1-ui-instrumented-tests.md) — parcial (16 arquivos JVM + gate + migração Room e contrato app↔backend; falta UI)
- [H3-2 — Polimento visual](docs/specs/H3-2-visual-polish.md) — pronta para implementar
- [H3-3 — Acessibilidade](docs/specs/H3-3-accessibility.md) — pronta para implementar
- [H3-4 — Backend na nuvem com token](docs/specs/H3-4-cloud-backend.md) — pronta para implementar
- [H3-5 — Progressão de carga](docs/specs/H3-5-progression.md) — pronta para implementar

Um item sem spec não entra em execução. A spec é o contrato que impede um modelo
mais simples de "compilar verde e entregar a coisa errada".
