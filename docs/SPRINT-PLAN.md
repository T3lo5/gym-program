# Plano de Sprints — App Pessoal de Treinos (Local-first + Back Opcional)

> ⚠️ **DOCUMENTO HISTÓRICO — SUPERADO.**
> Este arquivo descreve o plano original (pré-implementação) e **não reflete o
> estado atual**. A fonte de verdade é [ROADMAP.md](../ROADMAP.md); as regras de
> execução e verificação estão em [AGENTS.md](../AGENTS.md). Mantido apenas como
> registro. Não use os IDs `S0-*`/`S5-*` para decidir o próximo passo.

**Objetivo:** App Android + Wear OS que roda 100% offline, com geração de treinos via IA sob demanda (back no PC / Codespaces / Oracle).
**Custo alvo:** R$ 0/mês
**Stack:** Kotlin + Jetpack Compose + Room + Retrofit/Ktor + Compose for Wear OS + Node.js/FastAPI back (Docker)

---

## Sprint 0 — Fundação & Dados (1-2 dias)

| ID | Task | Critério de aceite |
|---|---|---|
| S0-1 | Clonar `free-exercise-db` e gerar `exercises.json` único | `dist/exercises.json` válido no repo do app |
| S0-2 | Criar script Kotlin/Node para importar JSON → Room entities (`Exercise`, `MuscleGroup`, `Equipment`) | DB local popula em <2s no device |
| S0-3 | Definir schema do treino (`WorkoutPlan`, `WorkoutDay`, `ExerciseSet`, `SetTarget`) + migration Room | `WorkoutDao` compila, testes unitários passam |
| S0-4 | Configurar projeto Android (KSP, Compose, Hilt/Koin, Room, Retrofit, Wear module) | `./gradlew assembleDebug` builda sem warnings |
| S0-5 | `docker-compose.yml` do back (Node/FastAPI + healthcheck) + `Dockerfile` | `docker compose up -d` sobe em <10s, `GET /health` retorna 200 |

**Entregável:** App abre, mostra lista de exercícios (offline), back roda localmente.

---

## Sprint 1 — Core Offline: CRUD de Treinos (2-3 dias)

| ID | Task | Critério de aceite |
|---|---|---|
| S1-1 | Tela "Meus Treinos" (lista de `WorkoutPlan` com pull-to-refresh) | Lista vazia → botão "Novo treino" |
| S1-2 | Editor de treino: criar/editar dias, exercícios, séries, repetições, carga, RPE, descanso | Salva no Room, navegação fluida |
| S1-3 | Tela de execução ("Iniciar treino"): timer de descanso, contagem de séries, marcação de concluído, notas | Persiste progresso ao sair/voltar |
| S1-4 | Histórico simples: calendário + cards de treinos passados | Filtro por semana/mês |
| S1-5 | Settings: unidades (kg/lb), tema, backup/restore JSON (export/import) | Arquivo JSON round-trip idêntico |

**Entregável:** App **completo para uso offline** — você já treina sem internet.

---

## Sprint 2 — Back de IA: Geração de Treino (2-3 dias)

| ID | Task | Critério de aceite |
|---|---|---|
| S2-1 | Endpoint `POST /v1/generate-workout` no back: recebe contexto (goal, dias/semana, equipamentos, histórico, dores/lesões), monta prompt, chama sensenova, valida JSON contra schema | Retorna `WorkoutPlanDTO` válido |
| S2-2 | Prompt engineering v1: system prompt com catálogo resumido (ids + nomes + músculos + equipamento), few-shot de 2 treinos | IA **só referencia IDs existentes** do catálogo |
| S2-3 | Integração app→back: botão "Gerar com IA" na tela "Novo treino", mostra loading, trata erros (timeout, JSON inválido, quota) | Treino gerado aparece no editor para revisão |
| S2-4 | Cache local da última geração (Room) + botão "Regenerar" | Funciona offline se já gerou antes |
| S2-5 | Configurar `SENSENOVA_API_KEY` via env no back (não no app) | Chave nunca sai do back |

**Entregável:** Fluxo "clica → IA gera → você revisa → salva" funcionando na LAN.

---

## Sprint 3 — Descoberta & Conexão LAN (1-2 dias)

| ID | Task | Critério de aceite |
|---|---|---|
| S3-1 | Network security config: cleartext para `192.168.0.0/16`, `10.0.0.0/8`, `172.16.0.0/12`, `*.local` | App fala HTTP com back no PC sem erro de cleartext |
| S3-2 | Permissão runtime `ACCESS_LOCAL_NETWORK` (Android 14+) com rationale UI | Popup aparece uma vez, app lembra escolha |
| S3-3 | Descoberta mDNS/NSD opcional: back anuncia `_treinos._tcp`, app lista instâncias | "Encontrado back em 192.168.1.42:3000" |
| S3-4 | Fallback manual: tela "Configurar back" com IP:porta + botão "Testar conexão" | Salva preferência, usa na próxima vez |
| S3-5 | `adb reverse` documentado no README para dev | Dev roda `adb reverse tcp:3000 tcp:3000` e usa `localhost:3000` |

**Entregável:** App encontra e conecta no back do PC **sem digitar IP** (ou com 1 clique no fallback).

---

## Sprint 4 — Galaxy Watch FE (Wear OS) — MVP (2-3 dias)

| ID | Task | Critério de aceite |
|---|---|---|
| S4-1 | Module `wear` + Compose for Wear OS + Data Layer dependency | `wear:assembleDebug` builda |
| S4-2 | Protocolo Data Layer: `WorkoutSession` (exercício atual, série, próxima, timer descanso) + `ControlMessage` (next/prev/pause/skip) | Celular → relógio <500ms no BT |
| S4-3 | Tela do relógio: **exercício atual** (nome, série X de Y, alvo reps/RPE), **timer de descanso** (countdown grande, vibração no fim), **próximo exercício** (preview) | Legível em luz solar, touch alvos ≥48dp |
| S4-4 | Sincronização: ao "Iniciar treino" no celular, envia sessão completa pro relógio; relógio opera autônomo (fila local) | Se BT cai, relógio continua; reconecta sincroniza estado |
| S4-5 | HR (opcional): ler `HeartRate` do Health Connect no celular → envia pro relógio via Data Layer a cada 10s | Mostra bpm no canto; se indisponível, esconde graciosamente |

**Entregável:** Relógio mostra o treino **sem precisar olhar o celular**.

---

## Sprint 5 — Polimento & Qualidade de Vida (1-2 dias)

| ID | Task | Critério de aceite |
|---|---|---|
| S5-1 | Animações/transições Compose (SharedElement, AnimatedVisibility) | 60fps no device médio |
| S5-2 | Acessibilidade: contentDescription, scaling de fonte, TalkBack nas telas principais | Checklist A11y passa |
| S5-3 | Testes: unit (ViewModel/UseCase), UI (Compose Testing), instrumented (Room) | `./gradlew test connectedAndroidTest` verde |
| S5-4 | CI GitHub Actions: lint + test + build APK + build Wear APK | Badge verde no README |
| S5-5 | README completo: arquitetura, como rodar back, como instalar no relógio, troubleshooting LAN | Onboarding de 5 min para "eu do futuro" |

---

## Sprint 6 (Opcional) — Back na Nuvem Grátis (1 dia)

| ID | Task | Critério de aceite |
|---|---|---|
| S6-1 | **Opção A — GitHub Codespaces:** devcontainer + `devcontainer.json` + script `up.sh` que sobe back e expõe porta | `gh codespace create` → `./up.sh` → app conecta na URL pública |
| S6-2 | **Opção B — Oracle Always Free:** Terraform/Resource Manager para provisionar VM A1 Flex + Docker + Cloudflare Tunnel (grátis) | Back acessível em `https://treinos.seudominio.tk` |
| S6-3 | App: toggle "Usar back remoto" + armazenar URL base (Keychain/EncryptedSharedPrefs) | Troca LAN ↔ nuvem em 1 tap |

---

## Sprint 7 (Opcional) — Extras "Nice to Have"

| ID | Task | Prioridade |
|---|---|---|
| S7-1 | Planejamento de periodização (mesociclos, progressão de carga automática) | Média |
| S7-2 | Gráficos de volume/intensidade por músculo (MPAndroidChart/Compose) | Baixa |
| S7-3 | Exportar treino para CSV/Google Sheets | Baixa |
| S7-4 | Widget Android "Próximo treino" | Baixa |
| S7-5 | Complicação no mostrador do relógio (próximo exercício) | Média |

---

## Priorização (MoSCoW)

| Must Have (S0-S2) | Should Have (S3-S4) | Could Have (S5) | Won't Now (S6-S7) |
|---|---|---|---|
| Banco local + exercícios | Descoberta LAN automática | Animações polidas | Back na nuvem (Codespaces/Oracle) |
| CRUD treinos offline | Watch: execução + timer | A11y + testes + CI | Periodização automática |
| Geração IA (LAN) | HR via Health Connect | Widget/complicação | Gráficos avançados |
| Schema válido (catálogo) | | Backup/restore JSON | Export CSV/Sheets |

---

## Estimativa de Esforço Total

| Fase | Dias úteis | Acumulado |
|---|---|---|
| S0 Fundação | 2 | 2 |
| S1 Core Offline | 3 | 5 |
| S2 Back IA | 3 | 8 |
| S3 LAN | 2 | 10 |
| S4 Watch MVP | 3 | 13 |
| S5 Polimento | 2 | 15 |
| **Total MVP usável** | | **~3 semanas (part-time)** |
| S6 Nuvem (opcional) | 1 | 16 |
| S7 Extras | variável | — |

---

## Definição de Pronto (DoD) por Sprint

- [ ] Código no `main` (PR aprovado, sem `// TODO` bloqueante)
- [ ] `./gradlew lintDebug test connectedAndroidTest` passa
- [ ] APK instalado no device físico + Wear APK no relógio
- [ ] Checklist manual da sprint executado (smoke test)
- [ ] Documentação (README/CHANGELOG) atualizada

---

## Riscos & Mitigação (resumo)

| Risco | Prob. | Impacto | Mitigação |
|---|---|---|---|
| Sensenova muda API/limite | Baixa | Médio | Back agnóstico (OpenAI-compatible), fallback em 1 commit |
| Oracle recolhe instância idle | Média | Baixo | Usar Codespaces para "sob demanda"; cron ping se 24/7 |
| BT instável celular↔relógio | Média | Médio | Relógio opera autônomo com fila local; reconcilia ao reconectar |
| Android 15+ restringe LAN | Baixa | Alto | Testar em preview; manter fallback manual de IP |
| Catálogo free-exercise-db incompleto | Baixa | Baixo | Back complementa com IA (já sabe nomes PT-BR) |

---

## Próximo Passo Imediato

> **Iniciar Sprint 0** — Clonar o `free-exercise-db`, gerar o JSON combinado e criar o projeto Android com Room + module Wear. Quer que eu gere o `build.gradle.kts` base + estrutura de pastas + script de importação do catálogo?