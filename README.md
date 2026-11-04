# Treinos App — App Pessoal de Treinos (Local-first + IA)

App Android + Wear OS para musculação, 100% offline, com geração de treinos via IA sob demanda (backend no PC/Codespaces/Oracle Free Tier).

**Stack:** Kotlin + Jetpack Compose + Room + OkHttp + kotlinx.serialization + Compose for Wear OS + FastAPI (Python) + SenseNova API

> Imagens dos exercícios: [yuhonas/free-exercise-db](https://github.com/yuhonas/free-exercise-db) (Unlicense).
>
> 🗺️ **Roadmap e specs:** [ROADMAP.md](ROADMAP.md) (o que fazer) e
> [docs/specs/](docs/specs/) (como cada item deve ser feito). O plano antigo em
> [docs/SPRINT-PLAN.md](docs/SPRINT-PLAN.md) é histórico.
>
> ✅ **Estado atual:** o app cria o plano sob demanda no backend (IA ou fallback local), salva tudo no SQLite do aparelho, executa o treino registrando cada série e mostra o histórico. O backend devolve também a data de revisão do plano. Os dados só vão para o banco do backend quando você toca em **Sincronizar agora**; o botão **Restaurar do backend** traz o snapshot de volta. O backup JSON está implementado na lógica (H1-4); a ligação dos botões de exportar/importar e a restauração atômica são o H1-7.

---

## 🎯 Características

| Feature | Status |
|---|---|
| 📱 App Android (celular) | ✅ Telas de plano, execução, histórico e ajustes |
| ⌚ App Wear OS (Galaxy Watch FE) | ✅ MVP (H2-2), APK separado `dist/wear.apk` |
| 💾 Banco local (Room/SQLite) | ✅ Planos, sessões e séries gravados na hora |
| 🏋️ Treino offline | ✅ Execução com registro de séries e descanso |
| 🤖 Geração de treino sob demanda | ✅ IA + fallback local + data de revisão |
| 🔄 Sincronização manual app → backend | ✅ Snapshot de planos, treinos e séries |
| ♻️ Restauração por `deviceId` | 🟡 Download local funciona; restaurar outro ID e atomicidade em H1-7 |
| 💾 Backup JSON manual | 🟡 Lógica pronta (H1-4); botões de exportar/importar em H1-7 |
| 🔗 Descoberta LAN (mDNS) | 📋 No backend; app usa URL configurável |
| ⏱ Timer de descanso no relógio | ✅ MVP junto com H2-2 |
| 🏃 Corrida, natação e mobilidade | 📋 H2-5 a H2-7 (specs no roadmap) |
| ☁️ Backend grátis (Codespaces/Oracle) | ✅ Docker Compose |

---

## 📁 Estrutura do Projeto

```
TreinosApp/
├── settings.gradle.kts          # Build: :app, :protocol e :wear
├── build.gradle.kts             # Versões centralizadas
├── gradle.properties
├── docker-compose.yml           # Backend + rede
├── ROADMAP.md                   # Fonte de verdade do produto
├── docs/specs/                  # Specs por item (template + H1..H3)
├── scripts/                     # Gate de CI (verify.sh, ci-android.sh, ci-backend.sh)
├── app/                         # App Android do celular (módulo :app)
│   └── src/main/java/com/treinos/app/
│       ├── data/local/          # Room: entities, DAO, database
│       ├── data/remote/         # BackendClient + DTOs
│       ├── data/prefs/          # AppPreferences
│       ├── data/WorkoutRepository.kt
│       └── ui/                  # ViewModel + screens Compose
├── protocol/                    # Contrato Kotlin/JVM celular ↔ relógio
├── wear/                        # App Wear OS (módulo :wear, ativo)
├── shared/                      # ⚠️ legado, fora do build (ver LEGACY.md)
└── back/                        # Backend FastAPI (Python)
    ├── app/main.py              # /health, /v1/generate-workout, /v1/catalog, /v1/sync
    ├── app/exercise_catalog.json
    └── tests/                   # Pytest
```

---

## 🚀 Como Rodar

### Pré-requisitos
- Android Studio Koala+ (JDK 17, Android SDK 34)
- Docker + Docker Compose
- Chave da API SenseNova (gratuita em https://platform.sensenova.ai)

### 1. Backend (no seu PC)
```bash
# Na raiz do projeto (onde está o docker-compose.yml)
cp back/.env.example back/.env
# Edite back/.env e coloque sua SENSENOVA_API_KEY

docker compose up -d --build
# Teste:
curl http://localhost:3000/health
# {"status":"healthy","catalogSize":876,"model":"sensenova-6.8-flash-lite"}
```

> Passo a passo completo, incluindo modo sem Docker e solução de problemas:
> [docs/MANUAL.md](docs/MANUAL.md).

### 2. App Android
```bash
# No Android Studio:
# 1. Open project → selecione a pasta raiz
# 2. Wait for Gradle sync
# 3. Run 'app' no emulador (API 34) ou device físico
```

### 3. Conexão Celular ↔ PC (LAN)
- Celular e PC na **mesma Wi-Fi**
- No emulador: usa `10.0.2.2:3000` automaticamente
- No device físico: app detecta via mDNS ou você digita `192.168.x.x:3000`
- Android 14+: concede permissão "Acesso à rede local" quando solicitado

### 4. Wear OS (Galaxy Watch FE)
```bash
# No relógio: Settings → Developer options → ADB debugging ON
# No PC:
adb pair <watch-ip>:<port>
adb connect <watch-ip>:5555
# No Android Studio: Run 'wear' module
# O gate também gera dist/wear.apk junto com dist/treinos.apk
```

---

## 📦 CI/CD — APK automático

Toda vez que houver um push na `main`, o GitHub Actions:

1. Compila o APK com `clean` (`assembleDebug`).
2. Roda os testes unitários (`testDebugUnitTest`).
3. Roda o Android Lint (`lintDebug`).
4. Verifica a assinatura do APK.
5. Publica o arquivo na release **`latest`** do repositório.
6. Também guarda o APK como artifact do workflow (14 dias).

O job `Backend checks` roda Ruff, import do FastAPI e Pytest em paralelo; a
release só é publicada se os dois jobs passarem.

> 🤖 **Agentes de código:** leia [AGENTS.md](AGENTS.md). A regra é única:
> `./scripts/verify.sh` verde antes de qualquer commit/PR.

### Verificar localmente (mesmo gate do CI)

```bash
./scripts/verify.sh
```

O comando constrói a imagem Docker (`Dockerfile`), roda dentro dela o mesmo
script que o CI usa (`scripts/ci-android.sh`) e depois valida o backend
(`scripts/ci-backend.sh`). Ele usa `clean`, então build/cache antigo do host
não esconde erro de compilação. Saídas: `dist/treinos.apk`,
`dist/wear.apk`, `dist/dist.sha256`, relatórios de teste e lint em
`app/build/reports/` e `wear/build/reports/`.

Atalhos:

```bash
SKIP_BACKEND=1 ./scripts/verify.sh   # só Android
./scripts/ci-backend.sh              # só backend
```

**Download direto (sempre a última versão):**

```
https://github.com/T3lo5/gym-program/releases/latest/download/treinos.apk
https://github.com/T3lo5/gym-program/releases/latest/download/wear.apk
```

Os checksums ficam em `treinos.apk.sha256` e `wear.apk.sha256`, na mesma release.

### Disparar manualmente

Na aba **Actions** do GitHub, abra o workflow **Android APK** e clique em
**Run workflow**. O APK será publicado na release `latest` ao final.

### Pull requests

PRs compilam o APK para validar o código, mas **não** publicam release.
O APK fica disponível como artifact da execução.

### Assinatura

O CI usa a chave de debug do Android, que é mantida em cache entre as
execuções. Isso permite instalar uma versão nova por cima da anterior sem
desinstalar o app. Se o cache expirar, pode ser necessário desinstalar a
versão antiga antes de instalar a nova.

Para builds assinados com chave própria, configure os secrets
`RELEASE_KEYSTORE_BASE64`, `RELEASE_KEYSTORE_PASSWORD`, `RELEASE_KEY_ALIAS`
e `RELEASE_KEY_PASSWORD` e troque o workflow para usar `assembleRelease`.

---

## 🏗️ Arquitetura

> O código ativo está em `app/src/main/java/com/treinos/app/`, `wear/` e
> `protocol/`. Os diretórios `shared/` e `app/src/main/java/com/treinos/shared/`
> são **legados** e não entram no build (ver `LEGACY.md` em cada um).

### App Android (`app`)
- **Room:** `WorkoutPlanEntity`, `WorkoutDayEntity`, `WorkoutPlanExerciseEntity`,
  `WorkoutSessionEntity`, `SessionExerciseEntity`, `SetLogEntity` em
  `data/local/Entities.kt`
- **DAO:** `WorkoutDao` com Flows reativos + `suspend functions`
- **Repositório:** `WorkoutRepository` (fonte única de dados)
- **Rede:** OkHttp + kotlinx.serialization (`BackendClient`, `BackendDtos`)
- **Preferências:** `AppPreferences` (deviceId, URL do backend, último sync)
- **Estado/UI:** `AppViewModel` + Compose; navegação por `enum Screen`
- **Telas:** `HomeScreen`, `GenerateScreen`, `WorkoutScreen`, `HistoryScreen`,
  `SettingsScreen`

### Backend (`back`)
- **FastAPI** + Uvicorn em Docker
- **Endpoints principais:**
  - `GET /health` — health check + tamanho do catálogo
  - `GET /v1/catalog` — catálogo completo
  - `POST /v1/generate-workout` — gera treino validado (IA + fallback local)
  - `GET /v1/sync/download` — restaura snapshot por `deviceId`
  - `POST /v1/sync` — recebe o snapshot do app
  - `GET /v1/sync/status` e `GET /v1/sync/plans` — inspecionam o snapshot
- **Prompt Engineering:** catálogo resumido injetado no system prompt
- **Validação:** IDs de exercício conferidos contra o catálogo local
- **Testes:** `back/tests/` (Ruff + Pytest no gate)

---

## 📊 Banco de Exercícios (free-exercise-db)

- **Fonte:** https://github.com/yuhonas/free-exercise-db (Unlicense)
- **876 exercícios** com: id, nome, força, nível, mecânica, equipamento, músculos primários/secundários, instruções, categoria, imagens
- **Onde vive:** `back/app/exercise_catalog.json`; o backend usa o catálogo para gerar e validar planos
- **No app:** o catálogo é baixado de `GET /v1/catalog` sob demanda e cacheado localmente (H1-3); não é mais embutido no APK
- **Imagens:** servidas via `https://raw.githubusercontent.com/yuhonas/free-exercise-db/main/exercises/<id>/0.jpg` e cacheadas por 7 dias

---

## 🤖 Geração de Treino via IA

**Fluxo:**
1. Usuário clica "Gerar com IA" no editor
2. App envia contexto: objetivo, dias/semana, equipamentos, músculos-alvo, nível, limitações
3. Backend monta prompt com catálogo resumido → chama SenseNova (`sensenova-6.8-flash-lite`)
4. IA retorna `WorkoutPlanDTO` (JSON schema estrito)
5. Backend valida IDs contra catálogo → retorna pro app
6. App abre editor pré-preenchido para revisão → salva no Room

**Configuração SenseNova:**
```env
SENSENOVA_API_KEY=sk-xxx
SENSENOVA_BASE_URL=https://token.sensenova.ai/v1
MODEL_ID=sensenova-6.8-flash-lite
```
> Dica: Use `"reasoning": {"effort": "none"}` no request para JSON mode (doc oficial recomenda desligar reasoning para JSON estruturado).

---

## 🆓 Hospedagem Grátis do Backend

| Opção | Custo | Ideal para |
|---|---|---|
| **PC local (Docker)** | R$ 0 | Desenvolvimento, uso na LAN |
| **GitHub Codespaces** | 120h/mês grátis | "Sobe quando quer, fecha depois" |
| **Oracle Always Free** | 2 OCPU + 12GB RAM pra sempre | Backend 24/7 na nuvem |

---

## 🗺️ Roadmap

O [ROADMAP.md](ROADMAP.md) é a fonte de verdade do produto; cada item tem uma
spec em [docs/specs/](docs/specs/) definindo como ele deve ser feito.

| Horizonte | Foco |
|---|---|
| Concluído | H1-1 a H1-5, H2-2 (Wear OS) e H2-3 (download do backend) |
| Agora | H1-6 fundação de dados e H1-7 correções de restauração/backup |
| Próximo | H2-5 registro avulso (natação), H2-6 plano híbrido, H2-1 LAN |
| Depois | H2-7 mobilidade, H2-4 histórico com calendário, H3-1 a H3-5 |

> `docs/SPRINT-PLAN.md` é histórico e **não** deve ser usado para decidir o
> próximo passo.

---

## 🛠️ Desenvolvimento

```bash
# Backend local
docker compose up -d --build
docker logs -f treinos-backend

# Build dos módulos ativos
./gradlew :app:assembleDebug
./gradlew :wear:assembleDebug

# Gate completo (build + testes + lint + assinatura + backend)
./scripts/verify.sh

# ADB reverse (dev no emulador)
adb reverse tcp:3000 tcp:3000
```

> O catálogo fica versionado em `back/app/exercise_catalog.json`; o app baixa
> via `GET /v1/catalog` e mantém cache local. Não há mais catálogo Kotlin
> embutido no APK.

---

## 📄 Licença

- **Código do app:** MIT (seu uso pessoal)
- **Catálogo de exercícios:** Unlicense (domínio público) — https://github.com/yuhonas/free-exercise-db
- **SenseNova API:** Termos da plataforma (https://platform.sensenova.ai)

---

## 🤝 Contribuição

Projeto pessoal — PRs bem-vindos para melhorias de UX, correções de bugs, ou novos exercícios PT-BR no catálogo.

---

**Desenvolvido para uso pessoal — custo R$ 0/mês 💪**# CI trigger
