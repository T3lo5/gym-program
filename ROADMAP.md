# Roadmap — Treinos App

App pessoal de musculação Android + Wear OS, **local-first + IA sob demanda**, custo alvo R$ 0/mês.

> **Fonte de verdade do produto.** O [SPRINT-PLAN.md](docs/SPRINT-PLAN.md) é histórico
> (pré-implementação) e não deve ser usado para decidir o próximo passo.
>
> Itens do roadmap só começam com uma spec em [docs/specs/](docs/specs/). Sem spec,
> o agente deve pedir antes de implementar.

---

## 1. Visão do produto

Um app que você usa **sem conta, sem login e sem internet** para treinar. Tudo fica no celular (Room/SQLite). O backend (FastAPI no seu PC/Codespaces/Oracle) só entra para:

- gerar um plano novo via IA (SenseNova) com fallback local;
- sincronizar um snapshot dos seus dados (quando você tocar em "Sincronizar agora").

O relógio (Wear OS) mostra o treino em andamento sem precisar olhar o celular.

---

## 2. Onde estamos hoje (atualizado em 2026-11-03)

| Área | Status | Evidência |
|------|--------|-----------|
| Banco local (Room) | ✅ | Planos, dias, exercícios, sessões, séries |
| Execução de treino | ✅ | Timer de descanso, séries, RPE, carga, histórico |
| Geração via IA | ✅ | Backend + SenseNova + fallback local |
| Sincronização app → backend | ✅ | Snapshot manual (`AppViewModel.syncNow`) |
| Restauração por `deviceId` | ✅ | `AppViewModel.restoreByDeviceId` + botão em Ajustes |
| Download do backend | ✅ | `WorkoutRepository.downloadSync` + botão em Ajustes |
| Testes + CI | 🟡 | 7 testes JVM + gate (`./scripts/verify.sh`); falta UI/instrumentado |
| Descoberta LAN | 📋 | mDNS no backend; app usa URL configurável |
| Wear OS | 🚧 | Módulo existe, fora do build/APK atual |
| Backup/restore do usuário | ✅ | Exportação/importação JSON manual (H1-4) |
| Imagem/vídeo de exercício | ❌ | Catálogo sem mídia |
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

---

## 4. Roadmap por horizonte

Priorização MoSCoW: **M** = Must, **S** = Should, **C** = Could, **W** = Won't now.

### 4.1 Concluído

| ID | Item | Evidência |
|----|------|-----------|
| H1-1 | Recuperação por `deviceId` | `SettingsScreen` exibe o ID; `AppViewModel.restoreByDeviceId` restaura do backend |
| H1-2 | Execução por séries (não circuito) | `WorkoutScreen` registra série, `AppViewModel.logSet`, `ExerciseProgress` + testes |
| H1-4 | Exportar/importar backup JSON (manual) | `BackupDtos`, `BackupMapper`, `BackupValidator`; `WorkoutDao.replaceAllData`; `WorkoutRepository.exportBackup/importBackup`; `AppViewModel` estados/ações; `SettingsScreen` UI + file pickers; testes JVM em `app/src/test/.../data/backup/` |
| H1-5 | Peso e altura na geração de treino | `AppPreferences.bodyWeightKg/heightCm`; `GeneratePlanRequest.bodyWeightKg/heightCm`; `ProfileValidator`; `GenerateScreen` campos + resumo; `AppViewModel` valida/persiste/monta request; `TreinosApp` repassa parâmetros; backend `GenerateRequest` + `build_user_prompt`; `plan_builder.profile_adjustments`/`suggest_initial_load`; testes JVM em `app/src/test/.../ui/ProfileValidatorTest.kt`; testes backend em `back/tests/test_profile_adjustments.py` |
| H2-3 | Download/restauração do backend | `BackendClient.syncDownload`, `WorkoutRepository.downloadSync`, botão "Restaurar do backend" |
| H3-1 | Testes + CI (parcial) | 7 testes JVM, Android Lint, Ruff/Pytest e `./scripts/verify.sh` no CI |

### Horizon 1 — Agora (próximas 2-4 semanas): proteger dados e fechar o core

| ID | Item | MoSCoW | Problema | Solução prevista | Esforço | Depende de | Spec |
|----|------|--------|----------|------------------|---------|------------|------|
| H1-3 | Imagens e/ou vídeos dos exercícios | **S** | Usuário não reconhece o exercício pelo nome. | Campo `imageUrl`/`videoUrl`, exibição no detalhe e na execução, cache offline e fallback. | 4 dias | `WorkoutPlanExerciseEntity`, `ExerciseDetailScreen` (nova), catálogo | [H1-3](docs/specs/H1-3-exercise-media.md) |

### Horizon 2 — Próximo (1-2 meses): conectividade e relógio

| ID | Item | MoSCoW | Problema | Solução prevista | Esforço | Depende de | Spec |
|----|------|--------|----------|------------------|---------|------------|------|
| H2-1 | Descoberta LAN automática (mDNS) | **S** | Usuário precisa digitar IP:porta do backend. | Backend anuncia `_treinos._tcp.local`; app lista instâncias e conecta com 1 toque. Fallback mantém IP manual. | 3 dias | `BackendDiscoveryManager`, `BackendClient`, tela de configuração | [H2-1](docs/specs/H2-1-lan-discovery.md) |
| H2-2 | Wear OS MVP (Data Layer) | **S** | Relógio não está no APK. | Incluir `wear` no build; Data Layer envia sessão (exercício, série, timer); tela com timer e próximo exercício. | 5 dias | `wear` module, Compose for Wear OS, Data Layer API | [H2-2](docs/specs/H2-2-wear-os.md) |
| H2-4 | Histórico com calendário e métricas | **C** | Histórico é lista simples. | Calendário, cards de treinos, total de séries, duração, volume por músculo. | 4 dias | `HistoryScreen`, Room queries | [H2-4](docs/specs/H2-4-history-calendar.md) |

### Horizon 3 — Depois (2-4 meses): evolução e qualidade

| ID | Item | MoSCoW | Problema | Solução prevista | Esforço | Depende de | Spec |
|----|------|--------|----------|------------------|---------|------------|------|
| H3-1 | Testes de UI/instrumentados + cobertura | **M** | Gate cobre lógica pura e build, mas não UI, Room nem migrações. | Compose UI testing, Robolectric/instrumented para Room, testes de contrato app↔backend; manter Wear coberto quando entrar no build. | 5 dias | Módulos estáveis | [H3-1](docs/specs/H3-1-ui-instrumented-tests.md) |
| H3-2 | Polimento visual e animações | **C** | Telas funcionais, sem identidade refinada. | Animações Compose, transições, tema consistente, loading/empty states. | 4 dias | `Theme.kt`, telas existentes | [H3-2](docs/specs/H3-2-visual-polish.md) |
| H3-3 | Acessibilidade (A11y) | **C** | Sem `contentDescription`, escala de fonte e TalkBack nas telas principais. | `contentDescription`, fonte escalável, navegação TalkBack nas 4 tabs. | 3 dias | Telas existentes | [H3-3](docs/specs/H3-3-accessibility.md) |
| H3-4 | Backend na nuvem grátis (Oracle/Codespaces) | **W** | Backend só roda no PC local. | Deploy + Cloudflare Tunnel; toggle "Usar back remoto". Exige token antes de expor publicamente. | 2 dias | Backend Docker, `.env` seguro | [H3-4](docs/specs/H3-4-cloud-backend.md) |
| H3-5 | Periodização e progressão de carga | **W** | Plano é estático; não evolui carga/séries. | Mesociclos, sugestão de carga por RPE/séries, alerta de revisão. | 5 dias | Histórico completo, H2-4 | [H3-5](docs/specs/H3-5-progression.md) |

---

## 5. Priorização consolidada (MoSCoW)

| Must Have (agora) | Should Have (próximo) | Could Have (depois) | Won't now |
|-------------------|-----------------------|---------------------|-----------|
| Backup JSON (H1-4) | Mídia dos exercícios (H1-3) | Histórico com calendário (H2-4) | Backend na nuvem (H3-4) |
| Peso/altura na geração (H1-5) ✅ | Testes de UI/instrumentados (H3-1) | Polimento + animações (H3-2) | Progressão automática de carga (H3-5) |
| | Descoberta LAN (H2-1) | Acessibilidade (H3-3) | Export CSV/Sheets |
| | Wear OS MVP (H2-2) | | Widget/complicação do relógio |

---

## 6. Esforço e caminho crítico

```
Concluído:  H1-4 backup JSON         → proteção dos dados do usuário
Agora:      H1-5 peso/altura         → IA calibra o plano ao perfil
Semana 2:   H1-3 mídia               → reconhecimento do exercício
Mês 2:      H2-1 LAN + H2-2 Wear     → conectividade + relógio
Mês 2-3:    H2-4 histórico           → evolução visível
Mês 3+:     H3-1 UI/instrumentados   → cobertura além do gate atual
```

**Caminho crítico:** H1-5 → H1-3 → H2-1 → H2-2.
O gate (`./scripts/verify.sh`) roda em todo commit; H3-1 cobre o que ele ainda
não vê (UI, Room, contrato).

---

## 7. Critérios de aceite gerais (Definição de Pronto)

- [ ] Spec em `docs/specs/<ID>.md` aprovada (obrigatória para itens do roadmap)
- [ ] `./scripts/verify.sh` verde (build + testes + lint + assinatura + backend)
- [ ] Testes JVM novos para toda lógica pura; teste de backend quando `back/` mudar
- [ ] Smoke test manual no device físico quando houver UI nova
- [ ] `README.md`, `docs/MANUAL.md`, `ROADMAP.md` e a spec atualizados
- [ ] Nenhum código novo nos diretórios legados (`shared/`, `wear/`, `com.treinos.shared`)

---

## 8. Riscos e mitigação (resumo)

| Risco | Prob. | Impacto | Mitigação |
|-------|-------|---------|-----------|
| `deviceId` não é autenticação | Média | Alto | Tratar como backup pessoal; não expor backend público sem token (H3-4) |
| SenseNova muda API/limite | Baixa | Médio | Backend agnóstico (OpenAI-compatible); fallback local em 1 commit |
| BT instável celular ↔ relógio | Média | Médio | Relógio opera autônomo com fila local; reconcilia ao reconectar |
| Android 15+ restringe LAN | Baixa | Alto | Manter fallback manual de IP; testar em preview |
| Desinstalar app apaga dados locais | Alta | Alto | H1-4 (backup JSON) + H1-1 (restore por deviceId) |
| Catálogo sem mídia | Média | Baixo | Imagens via URL do free-exercise-db; vídeo opcional |

---

## 9. Próximo passo imediato

Implementar **H1-5 (peso/altura na geração de treino)** seguindo
[docs/specs/H1-5-profile.md](docs/specs/H1-5-profile.md). A spec já define
contrato, regras de IMC, prompt e testes.

Depois: H1-3 (mídia) → H2-1 (LAN) → H2-2 (Wear). Para iniciar qualquer item,
leia a spec correspondente em [docs/specs/](docs/specs/); se não existir, crie a
partir de [docs/specs/_TEMPLATE.md](docs/specs/_TEMPLATE.md).

---

## 10. Especificações

- [_TEMPLATE.md](docs/specs/_TEMPLATE.md) — formato obrigatório
- [H1-4 — Backup JSON](docs/specs/H1-4-backup-json.md) — concluída
- [H1-5 — Peso e altura](docs/specs/H1-5-profile.md) — pronta para implementar
- [H1-3 — Imagens dos exercícios](docs/specs/H1-3-exercise-media.md) — pronta para implementar
- [H2-1 — Descoberta LAN](docs/specs/H2-1-lan-discovery.md) — pronta para implementar
- [H2-2 — Wear OS MVP](docs/specs/H2-2-wear-os.md) — pronta para implementar
- [H2-4 — Histórico com calendário](docs/specs/H2-4-history-calendar.md) — pronta para implementar
- [H3-1 — Testes de UI, Room e contrato](docs/specs/H3-1-ui-instrumented-tests.md) — pronta para implementar
- [H3-2 — Polimento visual](docs/specs/H3-2-visual-polish.md) — pronta para implementar
- [H3-3 — Acessibilidade](docs/specs/H3-3-accessibility.md) — pronta para implementar
- [H3-4 — Backend na nuvem com token](docs/specs/H3-4-cloud-backend.md) — pronta para implementar
- [H3-5 — Progressão de carga](docs/specs/H3-5-progression.md) — pronta para implementar

Um item sem spec não entra em execução. A spec é o contrato que impede um modelo
mais simples de "compilar verde e entregar a coisa errada".
