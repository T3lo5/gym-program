# Treinos App — App Pessoal de Treinos (Local-first + IA)

App Android + Wear OS para musculação, 100% offline, com geração de treinos via IA sob demanda (backend no PC/Codespaces/Oracle Free Tier).

**Stack:** Kotlin + Jetpack Compose + Room + OkHttp + kotlinx.serialization + Compose for Wear OS + FastAPI (Python) + SenseNova API

> 📖 **Manual completo de utilização:** [docs/MANUAL.md](docs/MANUAL.md) — instalação do APK, execução do backend (Docker ou Python), testes, rede local e solução de problemas.
>
> 🗺️ **Roadmap e specs:** [ROADMAP.md](ROADMAP.md) (o que fazer) e
> [docs/specs/](docs/specs/) (como cada item deve ser feito). O plano antigo em
> [docs/SPRINT-PLAN.md](docs/SPRINT-PLAN.md) é histórico.
>
> ✅ **Estado atual:** o app cria o plano sob demanda no backend (IA ou fallback local), salva tudo no SQLite do aparelho, executa o treino registrando cada série e mostra o histórico. O backend devolve também a data de revisão do plano. Os dados só vão para o banco do backend quando você toca em **Sincronizar agora**.

---

## 🎯 Características

| Feature | Status |
|---|---|
| 📱 App Android (celular) | ✅ Telas de plano, execução, histórico e ajustes |
| ⌚ App Wear OS (Galaxy Watch FE) | 🚧 Estrutura antiga, fora do APK atual |
| 💾 Banco local (Room/SQLite) | ✅ Planos, sessões e séries gravados na hora |
| 🏋️ Treino offline | ✅ Execução com registro de séries e descanso |
| 🤖 Geração de treino sob demanda | ✅ IA + fallback local + data de revisão |
| 🔄 Sincronização manual app → backend | ✅ Snapshot de planos, treinos e séries |
| ♻️ Restauração por `deviceId` | ✅ Botão "Restaurar do backend" nos Ajustes |
| 💾 Backup JSON manual | 📋 H1-4 — [spec](docs/specs/H1-4-backup-json.md) |
| 🔗 Descoberta LAN (mDNS) | 📋 No backend; app usa URL configurável |
| ⏱ Timer de descanso no relógio | 📋 Sprint futura |
| ☁️ Backend grátis (Codespaces/Oracle) | ✅ Docker Compose |

---

## 📁 Estrutura do Projeto

```
TreinosApp/
├── settings.gradle.kts          # Build atual: somente :app
├── build.gradle.kts             # Versões centralizadas
├── gradle.properties
├── docker-compose.yml           # Backend + rede
├── ROADMAP.md                   # Fonte de verdade do produto
├── docs/specs/                  # Specs por item (template + H1-4)
├── scripts/                     # Gate de CI (verify.sh, ci-android.sh, ci-backend.sh)
├── app/                         # App Android (único módulo no APK)
│   └── src/main/java/com/treinos/app/
│       ├── data/local/          # Room: entities, DAO, database
│       ├── data/remote/         # BackendClient + DTOs
│       ├── data/prefs/          # AppPreferences
│       ├── data/WorkoutRepository.kt
│       └── ui/                  # ViewModel + screens Compose
├── shared/                      # ⚠️ legado, fora do build (ver LEGACY.md)
├── wear/                        # ⚠️ legado, fora do build (ver LEGACY.md)
└── back/                        # Backend FastAPI (Python)
    ├── app/main.py              # /health, /v1/generate-workout, /v1/catalog
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
# (O módulo wear ainda não está incluído no settings.gradle.kts atual)
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
`dist/treinos.apk.sha256`, relatórios de teste e lint em `app/build/reports/`.

Atalhos:

```bash
SKIP_BACKEND=1 ./scripts/verify.sh   # só Android
./scripts/ci-backend.sh              # só backend
```

**Download direto (sempre a última versão):**

```
https://github.com/T3lo5/gym-program/releases/latest/download/treinos.apk
```

O checksum fica em `treinos.apk.sha256`, na mesma release.

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

> O código ativo está em `app/src/main/java/com/treinos/app/`. Os diretórios
> `shared/`, `wear/` e `app/src/main/java/com/treinos/shared/` são **legados** e
> não entram no build/APK atual (ver `LEGACY.md` em cada um).

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
- **Prompt Engineering:** catálogo resumido injetado no system prompt
- **Validação:** IDs de exercício conferidos contra o catálogo local
- **Testes:** `back/tests/` (Ruff + Pytest no gate)

---

## 📊 Banco de Exercícios (free-exercise-db)

- **Fonte:** https://github.com/yuhonas/free-exercise-domínio público (Unlicense)
- **876 exercícios** com: id, nome, força, nível, mecânica, equipamento, músculos primários/secundários, instruções, categoria, imagens
- **Embutido no APK** via `ExerciseCatalog.kt` (auto-gerado por `scripts/import-exercises.js`)
- **Imagens:** Servidas via `https://raw.githubusercontent.com/yuhonas/free-exercise-db/main/exercises/<id>/0.jpg`

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

## 📝 Sprints

| Sprint | Entregável |
|---|---|
| **S0** (feito) | Fundação: Room, catálogo 876 exercícios, Docker backend, navegação base |
| **S1** (em andamento) | CRUD treinos offline completo (editor, execução, histórico) |
| **S2** | Integração "Gerar com IA" + validação |
| **S3** | Descoberta LAN automática (mDNS) + fallback manual |
| **S4** | Wear OS MVP: timer descanso, próximo exercício, sync Data Layer |
| **S5** | Polimento: animações, A11y, testes, CI |
| **S6** (opcional) | Backend na nuvem grátis (Codespaces/Oracle) |

---

## 🛠️ Desenvolvimento

```bash
# Gerar catálogo atualizado (se free-exercise-db mudar)
node scripts/import-exercises.js back/app/exercise_catalog.json shared/src/main/java/com/treinos/shared/data/

# Build backend
cd back && docker build -t treinos-backend .

# Logs backend
docker logs -f treinos-backend

# ADB reverse (dev no emulador)
adb reverse tcp:3000 tcp:3000
```

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
