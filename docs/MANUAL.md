# Manual de Utilização — Treinos App

**Versão do manual:** 1.2 — 04/11/2026<br>
**Público:** qualquer pessoa que queira instalar o app ou rodar o backend localmente.

Este manual explica, passo a passo:

- como baixar e instalar o aplicativo Android;
- como atualizar ou remover o aplicativo;
- como subir o backend no seu computador com Docker ou com Python;
- como testar o backend pelo navegador ou por comandos;
- como conectar o celular ao backend na mesma rede Wi-Fi;
- o que fazer quando algo dá errado.

---

## ⚠️ Estado atual do projeto

O projeto está em desenvolvimento. Para evitar expectativa errada, veja o que
já funciona nesta versão:

| Parte | Situação atual |
|---|---|
| Instalação do APK | ✅ Funciona |
| Abertura do app | ✅ Funciona |
| Atualização do APK | ✅ Funciona (com a mesma assinatura) |
| Backend: saúde, catálogo e documentação | ✅ Funciona |
| Backend: geração de treino sob demanda | ✅ Funciona com IA ou com plano local de reserva |
| Telas de plano, execução, histórico e ajustes no app | ✅ Funciona |
| Registro de séries no SQLite do aparelho | ✅ Funciona |
| Sincronização manual app → backend | ✅ Funciona pelo botão **Sincronizar agora** |
| Restauração do backend por `deviceId` | ✅ Funciona pelo botão **Restaurar do backend** |
| Backup JSON manual | 🟡 Lógica pronta; botões de exportar/importar em correção (H1-7) |
| Sugestão de carga e alerta de revisão | ✅ Funciona sem internet, calculado no aparelho (H3-5) |
| Módulo Wear OS | ✅ MVP (H2-2), APK separado `wear.apk` |

> **Resumo:** o app já cria treinos, executa o treino, registra as séries no
> SQLite do aparelho e mostra o histórico. O backend é chamado **somente**
> quando você gera um treino novo ou toca em **Sincronizar**. A geração
> devolve o plano e a data em que ele deve ser revisto; se a IA não estiver
> disponível, o backend monta um plano válido com o catálogo local. A lógica de
> backup JSON existe; a ligação dos botões de exportar/importar é concluída em
> H1-7.

---

## Índice

1. [Visão geral](#1-visão-geral)
2. [Início rápido](#2-início-rápido)
3. [Parte A — App Android (front)](#parte-a--app-android-front)
4. [Parte B — Backend (back)](#parte-b--backend-back)
5. [Conectar o celular ao backend](#5-conectar-o-celular-ao-backend)
6. [Uso no dia a dia](#6-uso-no-dia-a-dia)
7. [Segurança e privacidade](#7-segurança-e-privacidade)
8. [Solução de problemas](#8-solução-de-problemas)
9. [Referência rápida de comandos](#9-referência-rápida-de-comandos)
10. [Perguntas frequentes](#10-perguntas-frequentes)
11. [Glossário](#11-glossário)

---

## 1. Visão geral

O projeto tem duas partes:

```text
┌──────────────────────┐        HTTP         ┌──────────────────────────┐
│  Celular Android     │  ───────────────►   │  Backend no seu PC       │
│  (APK Treinos)       │                     │  FastAPI + Python        │
└──────────────────────┘                     └────────────┬─────────────┘
                                                          │ HTTPS
                                                          ▼
                                             ┌──────────────────────────┐
                                             │  API SenseNova (IA)      │
                                             └──────────────────────────┘
```

- **App Android (front):** aplicativo que você instala no celular.
- **Backend (back):** programa que roda no seu computador. Ele guarda o
  catálogo com 876 exercícios e conversa com a IA quando você pede um treino.
- **SenseNova:** serviço externo de inteligência artificial usado para gerar
  treinos. Precisa de uma chave de API e de internet.

### O que você precisa instalar?

| Objetivo | Precisa de |
|---|---|
| Só instalar e abrir o app | Um celular Android 8.0 ou superior |
| Usar os recursos de IA | Backend rodando + chave SenseNova + internet |
| Mexer no código | Android Studio, JDK 17, Android SDK 34, Python ou Docker |

---

## 2. Início rápido

### 2.1 Quero apenas instalar o app

1. Abra este link no celular:

   **https://github.com/T3lo5/gym-program/releases/latest/download/treinos.apk**

2. Baixe o arquivo `treinos.apk`.
3. Toque no arquivo baixado.
4. Se o Android bloquear, permita "instalar apps desconhecidos" para o
   navegador ou gerenciador de arquivos.
5. Toque em **Instalar** e depois em **Abrir**.

Pronto. O app abre na tela **Treinos**. Na primeira vez não há plano: toque em
**Gerar meu primeiro treino** e siga o formulário. Detalhes em
[Primeiro uso](#33-primeiro-uso).

> Para o relógio Wear OS, baixe também o `wear.apk` no mesmo link de release
> (`.../releases/latest/download/wear.apk`) e instale no relógio.

> Detalhes e solução de problemas: [Parte A](#parte-a--app-android-front).

### 2.2 Quero subir o backend

No computador, dentro da pasta do projeto:

```bash
# 1. Crie o arquivo de configuração
cp back/.env.example back/.env

# 2. Edite back/.env e coloque sua chave da SenseNova
#    (use qualquer editor de texto)

# 3. Suba o backend
docker compose up -d --build

# 4. Teste
curl http://localhost:3000/health
```

Se aparecer algo como:

```json
{"status":"healthy","timestamp":"...","model":"sensenova-6.8-flash-lite","catalogSize":876}
```

o backend está funcionando.

> Detalhes, instalação do Docker e modo sem Docker:
> [Parte B](#parte-b--backend-back).

### 2.3 Quero testar tudo

Checklist mínimo:

- [ ] Docker Desktop (ou Docker Engine) instalado e aberto.
- [ ] Arquivo `back/.env` criado com a chave da SenseNova.
- [ ] `docker compose up -d --build` executado na raiz do projeto.
- [ ] `curl http://localhost:3000/health` retornando `"status":"healthy"`.
- [ ] Página `http://localhost:3000/docs` abrindo no navegador.
- [ ] APK `treinos.apk` instalado no celular.
- [ ] Celular e computador na mesma rede Wi-Fi (para acesso pela rede).

---

## Parte A — App Android (front)

### 3. Requisitos do app

| Item | Mínimo | Recomendado |
|---|---|---|
| Sistema | Android 8.0 (API 26) | Android 10 ou superior |
| Espaço livre | ~30 MB | ~100 MB |
| Internet | Para baixar e atualizar | Para gerar treino com IA e sincronizar/restaurar |
| Conta | Não precisa | — |
| Root | Não precisa | — |

O APK atual é um **build de debug**, gerado automaticamente pelo GitHub
Actions. Ele não vem da Play Store e é assinado com uma chave de teste.

### 3.1 Baixar o APK

**Opção 1 — direto no celular (mais fácil):**

1. Abra o navegador do celular.
2. Acesse:

   ```
   https://github.com/T3lo5/gym-program/releases/latest/download/treinos.apk
   ```

3. O download começa automaticamente.

**Opção 2 — pelo computador:**

1. Abra a página de releases:

   ```
   https://github.com/T3lo5/gym-program/releases/latest
   ```

2. Baixe `treinos.apk`.
3. Transfira para o celular por cabo USB, Google Drive, Telegram ou e-mail.

> Para o relógio, baixe `wear.apk` na mesma página de releases.

**Conferir o checksum (opcional, mas recomendado):**

Baixe também o arquivo `treinos.apk.sha256` e compare com o hash do APK.

Linux:

```bash
sha256sum treinos.apk
```

macOS:

```bash
shasum -a 256 treinos.apk
```

Windows PowerShell:

```powershell
Get-FileHash .\treinos.apk -Algorithm SHA256
```

O valor deve ser igual ao que está em `treinos.apk.sha256`.

### 3.2 Instalar no celular

O caminho pode mudar um pouco conforme a marca do aparelho, mas o princípio é
sempre o mesmo.

1. Abra o arquivo `treinos.apk` (Downloads ou gerenciador de arquivos).
2. O Android pode mostrar um aviso de segurança. Toque em **Configurações**
   ou **Permitir**.
3. Ative a opção **Permitir instalação de apps desconhecidos** para o app que
   está abrindo o APK (Chrome, Firefox, Arquivos etc.).
4. Volte e toque em **Instalar**.
5. Se aparecer o Google Play Protect, escolha **Instalar mesmo assim** ou
   **Mais detalhes → Instalar mesmo assim**.
6. Toque em **Abrir**.

> **Importante:** instale apenas o APK deste repositório. Arquivos APK podem
> conter malware; não instale APKs de origem desconhecida.

**Onde fica a opção de fontes desconhecidas?**

- Android 8 a 15: **Configurações → Apps → Acesso especial → Instalar apps
  desconhecidos**.
- Alguns fabricantes: **Configurações → Segurança → Instalar apps
  desconhecidos** ou **Privacidade → Instalação de apps desconhecidos**.

### 3.3 Primeiro uso

Ao abrir o app pela primeira vez, a tela **Treinos** aparece sem nenhum plano.
O app:

- não pede login;
- não pede cadastro;
- não mostra anúncios;
- não acessa sua localização;
- salva tudo no próprio aparelho.

#### 3.3.1 Gerar um treino (backend sob demanda)

1. Na tela inicial, toque em **Gerar meu primeiro treino**.
2. Escolha:
   - **Objetivo** (hipertrofia, força, resistência, potência, perda de gordura
     ou condicionamento);
   - **Dias por semana** (2 a 6);
   - **Seu nível** (iniciante, intermediário ou avançado);
   - **Equipamentos disponíveis** (pode marcar vários);
   - **Foco muscular** (opcional);
   - **Duração da sessão**;
   - **Revisar o plano em** (2, 4, 6 ou 8 semanas).
3. Toque em **Gerar treino**.

O app chama o backend naquele momento. O backend devolve:

- o plano completo (dias e exercícios);
- a origem do plano: **com IA** ou **gerado localmente** (quando a IA não está
  disponível);
- a data de revisão (`nextReviewAt`), calculada a partir das semanas escolhidas.

O plano é salvo no SQLite do aparelho e a tela inicial passa a mostrar:

- o nome do plano ativo;
- o objetivo e quantos dias por semana;
- a **próxima revisão** (em vermelho se a data já passou);
- um cartão por dia de treino, com o botão **Iniciar treino**.

Se a data de revisão já passou, a tela inicial mostra também o cartão **Revisar
plano** com duas saídas: **Gerar novo treino** (o fluxo acima) ou **Adiar 2
semanas**, que empurra a data salva sem nunca antecipá-la. Quando o plano entra
na semana 6 do ciclo e você já concluiu pelo menos 8 treinos, aparece o aviso
**Ciclo do plano**, sugerindo reduzir 1 série por exercício; **Entendi** só
esconde o aviso nesta sessão de uso.

> **Importante:** gerar um treino novo substitui o plano ativo no aparelho.
> O plano anterior continua no banco local e será enviado na próxima
> sincronização se você optar por enviar tudo.

#### 3.3.2 Fazer o treino e registrar as séries

1. Toque em **Iniciar treino** no dia escolhido.
2. Cada exercício mostra as séries planejadas e o descanso sugerido.
3. Toque em **Registrar série**.
4. Preencha:
   - **Repetições** (obrigatório para a série ficar completa);
   - **Carga (kg)**;
   - **RPE** (opcional).
5. Toque em **Salvar série**.

Quando você já tem histórico suficiente do mesmo exercício (duas sessões nos
últimos 42 dias), o diálogo de série mostra uma linha **Sugestão:** com a carga
ou as repetições e o motivo — por exemplo
`Sugestão: 42,5 kg — Duas sessões no topo da faixa com RPE confortável.`. A
sugestão também pré-preenche o campo **Carga (kg)** apenas quando ele está
vazio, e o que você digitar prevalece. O app **não** altera o plano sozinho: a
sugestão é calculada no aparelho, a partir das séries que você registrou, e não
vai para o backend.

A série é gravada **imediatamente** no SQLite do aparelho. Depois de salvar:

- o app inicia um **contador de descanso** com o tempo do exercício;
- você pode tocar em **Pular descanso** a qualquer momento;
- a série aparece na lista do exercício com reps, carga e RPE.

Se precisar sair no meio do treino, toque na seta de voltar. O treino continua
**em andamento** e aparece na tela inicial com o botão **Retomar treino**.

Para encerrar, toque em **Finalizar treino**. O app grava o horário de término
e a duração total no aparelho.

#### 3.3.3 Histórico

Na tela inicial, toque em **Histórico**.

- Cada cartão mostra o dia, o plano, a data de início, a duração e o total de
  séries.
- Toque no cartão para expandir e ver cada exercício e cada série registrada.
- Treinos ainda não finalizados aparecem como **Em andamento**.

#### 3.3.4 Ajustes e sincronização manual

Na tela inicial, toque em **Ajustes**.

**Backend:**

1. Confira a **URL do backend**.
   - Emulador: `http://10.0.2.2:3000`.
   - Celular físico: `http://IP_DO_COMPUTADOR:3000`
     (ex.: `http://192.168.0.10:3000`).
2. Toque em **Salvar**.
3. Toque em **Testar conexão**. O app mostra o modelo e o tamanho do catálogo.

**Sincronizar agora:**

- O app monta um snapshot com **todos os planos, treinos e séries** locais.
- Envia para `POST /v1/sync` no backend.
- O backend grava tudo no SQLite dele (`back/data/treinos.db`).
- A resposta mostra quantos planos, treinos e séries foram recebidos.
- A data do último envio aparece na tela.

O envio **nunca é automático**: enquanto você não tocar em **Sincronizar
agora**, o banco do backend não recebe nenhum dado do app.

**Restaurar do backend e backup JSON:**

- **Restaurar do backend** baixa o snapshot do `deviceId` exibido em Ajustes e
  **substitui** o banco local. Use antes de limpar os dados ou reinstalar.
- **Exportar backup** gera um arquivo JSON com planos, sessões, séries e o
  perfil (peso, altura e sexo, quando informados). Você escolhe o destino na
  tela de arquivos do sistema,
  inclusive uma pasta do Google Drive, OneDrive ou Dropbox.
- **Importar backup** lê o arquivo e substitui os dados locais, depois reescreve
  o perfil. A operação é destrutiva: exporte antes de importar.
- O backup JSON é o caminho mais seguro: ele não depende de o backend estar no
  ar nem de o `deviceId` ter sido sincronizado antes.

> **Segredo não vai no arquivo:** o JSON do backup nunca contém `apiToken`,
> `backendUrl` ou qualquer chave. Se você editar o backup à mão, mantenha esses
> campos fora.

#### 3.3.5 Funcionamento sem internet

Sem backend ou sem rede, o app continua:

- abrindo;
- mostrando o plano já salvo;
- permitindo executar o treino;
- gravando séries no SQLite;
- mostrando o histórico.

Ficam indisponíveis apenas **gerar um treino novo** e **sincronizar**.

### 3.4 Atualizar o app

1. Baixe o novo `treinos.apk` pelo mesmo link.
2. Toque no arquivo.
3. Toque em **Atualizar** (não em desinstalar).
4. Abra o app normalmente.

O CI mantém a mesma chave de assinatura entre execuções, então normalmente a
atualização instala por cima, preservando os dados.

**Se aparecer "app não instalado" ou erro de assinatura:**

1. Desinstale a versão antiga:
   **Configurações → Apps → Treinos → Desinstalar**.
2. Instale o novo APK.
3. Atenção: desinstalar apaga os dados locais do app.

### 3.5 Desinstalar o app

1. Abra **Configurações** do Android.
2. Vá em **Apps** (ou **Aplicativos**).
3. Encontre **Treinos**.
4. Toque em **Desinstalar**.

### 3.6 Rodar no emulador

Se você não tem um celular Android, pode usar o emulador do Android Studio.

1. Instale o **Android Studio**.
2. Abra o **Device Manager** e crie um dispositivo virtual com **API 34**.
3. Inicie o emulador.
4. Instale o APK:

   ```bash
   adb install -r treinos.apk
   ```

5. O app aparecerá na gaveta de aplicativos do emulador.

Para abrir pelo Android Studio, veja a seção
[Compilar o app do zero](#37-compilar-o-app-do-zero).

### 3.7 Compilar o app do zero

Use esta seção se você quer modificar o código ou gerar seu próprio APK.

**Pré-requisitos:**

- Android Studio (versão recente);
- JDK 17;
- Android SDK 34 (o Android Studio instala pelo SDK Manager);
- conexão com a internet para baixar dependências.

**Passos:**

1. Abra o Android Studio.
2. Escolha **Open** e selecione a pasta raiz do projeto
   (`test-personal`).
3. Aguarde o **Gradle Sync** terminar.
4. Para gerar o APK pelo terminal, na raiz do projeto:

   Linux/macOS:

   ```bash
   ./gradlew :app:assembleDebug
   ```

   Windows:

   ```bat
   gradlew.bat :app:assembleDebug
   ```

5. O APK será gerado em:

   ```
   app/build/outputs/apk/debug/app-debug.apk
   ```

6. Instale no celular conectado por USB:

   ```bash
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```

**Se o Gradle não encontrar o SDK:**

Crie o arquivo `local.properties` na raiz do projeto com o caminho do SDK.

Linux/macOS:

```properties
sdk.dir=/home/SEU_USUARIO/Android/Sdk
```

Windows:

```properties
sdk.dir=C\:\\Users\\SEU_USUARIO\\AppData\\Local\\Android\\Sdk
```

> Normalmente o Android Studio cria esse arquivo automaticamente. Ele não
> deve ser commitado no Git.

**Gerar um APK de release:**

```bash
./gradlew :app:assembleRelease
```

O APK de release só será assinado se as variáveis de assinatura estiverem
configuradas. Para testes, use o APK de debug publicado pelo CI.

**Testes que o gate não roda:**

`./scripts/verify.sh` roda os testes JVM (incluindo as telas Compose no Robolectric e
o Room em memória) e **compila** os testes instrumentados, mas não os executa: o
Docker do gate não tem emulador. Para rodar no celular/emulador conectado por USB:

```bash
adb devices          # confirma que o aparelho aparece
./gradlew :app:connectedDebugAndroidTest
```

Isso é opcional e continua fora do gate — um build verde não depende dele.

**Cobertura (informativa):**

```bash
COVERAGE=1 ./scripts/ci-android.sh
```

Gera `app/build/reports/coverage/test/debug/index.html` a partir dos testes JVM. O
número não reprova o build: o gate falha por teste quebrado, não por percentual.

### 3.8 Problemas comuns do app

| Problema | Causa provável | Solução |
|---|---|---|
| "App não instalado" | Versão antiga com assinatura diferente, pouco espaço ou APK corrompido | Desinstale a versão antiga, confira o espaço e baixe o APK novamente |
| "Pacote inválido" | Download incompleto | Baixe de novo e confira o checksum |
| Play Protect bloqueia | APK fora da Play Store | **Mais detalhes → Instalar mesmo assim** |
| Não abre a opção de instalar | Permissão de fontes desconhecidas desativada | Ative para o navegador/gerenciador de arquivos |
| App abre e fecha | Versão incompatível ou APK corrompido | Confira Android 8.0+, reinstale e envie o log se persistir |
| "Não foi possível falar com o backend" | Backend desligado ou URL errada | Confira `curl http://localhost:3000/health`, o IP e a mesma rede Wi-Fi |
| Treino gerado não aparece | Resposta do backend falhou antes de salvar | Toque em **Testar conexão** em Ajustes e gere de novo |
| Sincronizou, mas o backend parece vazio | URL aponta para outro banco ou outro dispositivo | Confira `GET /v1/sync/status?deviceId=...` e o arquivo `back/data/treinos.db` |
| Não atualiza | Assinatura diferente | Desinstale e instale de novo (apaga dados) |

---

## Parte B — Backend (back)

### 4. O que o backend faz

O backend é uma API feita em **FastAPI** (Python). Ele:

- carrega o catálogo local com 876 exercícios;
- responde a testes de saúde;
- entrega o catálogo para o app;
- monta o pedido para a IA, valida a resposta e corrige IDs de exercícios;
- se a IA falhar, não estiver configurada ou devolver um ID inválido, monta um
  **plano local de reserva** com o catálogo (o app sempre recebe um treino);
- devolve o plano com a **data de revisão** (`nextReviewAt`);
- guarda cada geração e cada snapshot sincronizado em SQLite;
- registra um serviço de descoberta na rede local (mDNS), quando habilitado.

**Endpoints disponíveis:**

| Método | Caminho | Para que serve |
|---|---|---|
| GET | `/health` | Verificar se o backend está no ar |
| GET | `/v1/catalog` | Baixar o catálogo completo (876 exercícios) |
| GET | `/v1/catalog/summary` | Resumo do catálogo |
| POST | `/v1/generate-workout` | Gerar um treino sob demanda (IA ou fallback local) |
| POST | `/v1/sync` | Receber o snapshot manual enviado pelo app |
| GET | `/v1/sync/download?deviceId=...` | Baixar o snapshot para restaurar no app |
| GET | `/v1/sync/status?deviceId=...` | Contar planos, treinos e séries sincronizados |
| GET | `/v1/sync/plans?deviceId=...` | Listar os planos sincronizados do aparelho |
| GET | `/docs` | Documentação interativa (Swagger UI) |
| GET | `/redoc` | Documentação alternativa |

**Porta padrão:** `3000`.

**Banco do backend:** SQLite em `back/data/treinos.db` (no Docker, o diretório
`./back/data` é montado em `/app/data`). Ele guarda:

- `plan_generations` — histórico de planos gerados;
- `devices` — aparelhos que já sincronizaram;
- `synced_plans`, `synced_plan_days`, `synced_plan_exercises`;
- `synced_sessions`, `synced_session_exercises`, `synced_set_logs`.

> O banco do backend só é populado com dados do app quando o usuário toca em
> **Sincronizar agora**. Gerar um treino grava apenas o registro da geração.

### 4.1 Requisitos do backend

Escolha **uma** das formas de execução:

**Opção 1 — Docker (recomendado para qualquer usuário):**

- Docker Desktop (Windows/macOS) ou Docker Engine + Compose (Linux);
- 2 GB de RAM livre;
- internet na primeira execução (para baixar a imagem e as dependências).

**Opção 2 — Python direto no computador:**

- Python 3.11 ou 3.12 (recomendado);
- `pip`;
- internet para instalar as dependências.

**Chave da SenseNova:**

- Opcional. Com a chave, a geração tenta usar a IA.
- Obtenha em: https://platform.sensenova.ai/console/keys
- Sem a chave, o backend sobe normalmente e a geração usa o **plano local de
  reserva** montado com o catálogo. Saúde, catálogo, documentação e
  sincronização continuam funcionando.

### 4.2 Opção 1 — Subir com Docker (recomendado)

#### Passo 1 — Instalar o Docker

- Windows/macOS: https://www.docker.com/products/docker-desktop/
- Linux: https://docs.docker.com/engine/install/

Depois de instalar, abra o Docker Desktop (Windows/macOS) e confirme que ele
está rodando. No terminal:

```bash
docker --version
docker compose version
```

Você deve ver as versões instaladas. Se `docker compose` não existir, atualize
o Docker Desktop ou instale o plugin Compose.

#### Passo 2 — Criar o arquivo de configuração

Na **raiz do projeto** (a pasta que contém `docker-compose.yml`):

Linux/macOS:

```bash
cp back/.env.example back/.env
```

Windows (Prompt de Comando):

```bat
copy back\.env.example back\.env
```

Windows (PowerShell):

```powershell
Copy-Item back\.env.example back\.env
```

#### Passo 3 — Editar o `back/.env`

Abra `back/.env` em qualquer editor de texto (Bloco de Notas, VS Code etc.) e
preencha:

```env
SENSENOVA_API_KEY=sk-sua-chave-aqui
SENSENOVA_BASE_URL=https://token.sensenova.ai/v1
MODEL_ID=sensenova-6.8-flash-lite
# No Docker o compose força false; no modo Python deixe true.
MDNS_ENABLED=true
```

Regras importantes:

- não coloque espaços antes ou depois do `=`;
- não use aspas, a menos que o valor tenha espaços;
- nunca compartilhe sua chave;
- nunca commite o arquivo `.env` no Git (ele já está no `.gitignore`);
- se você não tiver chave, deixe o valor de exemplo e o backend ainda sobe,
  mas a geração de treino não funciona.

#### Passo 4 — Subir o backend

Ainda na raiz do projeto:

```bash
docker compose up -d --build
```

O que esse comando faz:

- `up`: cria e inicia os containers;
- `-d`: roda em segundo plano (você pode fechar o terminal);
- `--build`: reconstrói a imagem com o código atual.

A primeira execução pode levar de 1 a 3 minutos, porque baixa a imagem do
Python e instala as dependências.

#### Passo 5 — Conferir se subiu

```bash
docker compose ps
```

Você deve ver o container `treinos-backend` com status `Up` e, depois de
alguns segundos, `(healthy)`.

Teste a saúde:

```bash
curl http://localhost:3000/health
```

Resposta esperada:

```json
{
  "status": "healthy",
  "timestamp": "2026-11-02T17:50:57.026999Z",
  "model": "sensenova-6.8-flash-lite",
  "catalogSize": 876
}
```

No Windows PowerShell, se `curl` não estiver disponível:

```powershell
Invoke-RestMethod http://localhost:3000/health
```

#### Passo 6 — Abrir a documentação

No navegador do computador:

```
http://localhost:3000/docs
```

Essa página permite testar todos os endpoints sem instalar nada. É a forma
mais fácil de usar o backend pela primeira vez.

#### Comandos do dia a dia com Docker

```bash
# Ver logs em tempo real
docker compose logs -f treinos-backend

# Parar o backend (mantém o container)
docker compose stop

# Iniciar de novo
docker compose start

# Parar e remover o container/rede
docker compose down

# Atualizar o código e reconstruir
git pull
docker compose up -d --build

# Ver o status
docker compose ps
```

> O comando `docker compose down` não apaga o catálogo nem o arquivo `.env`.
> Ele apenas remove o container e a rede.

### 4.3 Opção 2 — Subir com Python (sem Docker)

Use esta opção se você não quer instalar Docker ou se precisa do mDNS
funcionando na rede local.

#### Passo 1 — Instalar o Python

- Baixe em: https://www.python.org/downloads/
- Recomendado: Python 3.11 ou 3.12.
- No Windows, marque **Add Python to PATH** durante a instalação.

Confira:

```bash
python --version
```

#### Passo 2 — Criar o ambiente virtual

Entre na pasta `back`:

```bash
cd back
```

Linux/macOS:

```bash
python3 -m venv .venv
source .venv/bin/activate
```

Windows PowerShell:

```powershell
python -m venv .venv
.venv\Scripts\Activate.ps1
```

Windows Prompt de Comando:

```bat
python -m venv .venv
.venv\Scripts\activate.bat
```

#### Passo 3 — Instalar as dependências

```bash
python -m pip install --upgrade pip
pip install -r requirements.txt
```

#### Passo 4 — Configurar o `.env`

Se ainda não existe:

Linux/macOS:

```bash
cp .env.example .env
```

Windows:

```bat
copy .env.example .env
```

Edite `back/.env` e coloque sua chave, como na seção do Docker.

O backend carrega esse arquivo automaticamente. Você **não precisa** exportar
as variáveis manualmente.

#### Passo 5 — Iniciar o backend

Ainda dentro de `back`, com o ambiente virtual ativado:

```bash
python -m uvicorn app.main:app --host 0.0.0.0 --port 3000 --reload
```

- `--host 0.0.0.0`: aceita conexões de outros aparelhos da rede;
- `--port 3000`: porta do backend;
- `--reload`: reinicia automaticamente quando você altera o código.

Teste em outro terminal:

```bash
curl http://localhost:3000/health
```

Para parar, pressione `Ctrl+C` no terminal.

> Se o mDNS reclamar de multicast, o servidor continua funcionando. Para
> desativar o aviso, defina `MDNS_ENABLED=false` no `.env` ou no ambiente.

### 4.4 Testar o backend

#### Saúde

```bash
curl http://localhost:3000/health
```

#### Catálogo

```bash
curl http://localhost:3000/v1/catalog
```

O retorno é um JSON grande. Para contar os exercícios:

Linux/macOS:

```bash
curl -s http://localhost:3000/v1/catalog | python3 -c "import sys,json; print(len(json.load(sys.stdin)['exercises']))"
```

Windows PowerShell:

```powershell
(Invoke-RestMethod http://localhost:3000/v1/catalog).exercises.Count
```

Resultado esperado: `876`.

#### Documentação interativa

Abra no navegador:

```
http://localhost:3000/docs
```

Você verá todos os endpoints. Para testar a geração de treino:

1. Clique em **POST /v1/generate-workout**.
2. Clique em **Try it out**.
3. Substitua o JSON de exemplo por um pedido seu.
4. Clique em **Execute**.
5. Veja a resposta em **Server response**.

#### Gerar um treino por comando

```bash
curl -X POST http://localhost:3000/v1/generate-workout \
  -H "Content-Type: application/json" \
  -d '{
    "deviceId": "meu-aparelho",
    "goal": "hypertrophy",
    "daysPerWeek": 3,
    "availableEquipment": ["dumbbell", "body only"],
    "targetMuscles": ["chest", "back"],
    "injuriesOrLimitations": "",
    "currentLevel": "beginner",
    "sessionDurationMinutes": 45,
    "preferredSplit": "full_body",
    "reviewAfterWeeks": 4
  }'
```

**Campos do pedido:**

| Campo | Valores aceitos | Obrigatório |
|---|---|---|
| `goal` | `hypertrophy`, `strength`, `endurance`, `power`, `fat_loss`, `general` | Não (padrão: `hypertrophy`) |
| `daysPerWeek` | 2 a 6 | Não (padrão: 4) |
| `availableEquipment` | Lista, ex.: `["dumbbell", "barbell", "body only"]` | Não |
| `targetMuscles` | Lista, ex.: `["chest", "back", "legs"]` | Não |
| `injuriesOrLimitations` | Texto livre | Não |
| `currentLevel` | `beginner`, `intermediate`, `advanced` | Não (padrão: `intermediate`) |
| `sessionDurationMinutes` | 30 a 120 | Não (padrão: 60) |
| `preferredSplit` | `full_body`, `upper_lower`, `push_pull_legs`, `bro_split` ou `null` | Não |
| `deviceId` | Identificador do aparelho (o app envia um UUID) | Não (padrão: `default`) |
| `reviewAfterWeeks` | 1 a 12 | Não (padrão: 4) |

**Resposta de sucesso (resumida):**

```json
{
  "workout": {
    "name": "Plano de Hipertrofia — 3x/semana",
    "description": "...",
    "goal": "hypertrophy",
    "daysPerWeek": 3,
    "days": [
      {
        "name": "Treino A",
        "dayOrder": 0,
        "exercises": [
          {
            "exerciseId": "Dumbbell_Bicep_Curl",
            "exerciseOrder": 0,
            "sets": 3,
            "targetReps": "8-12",
            "targetRpe": 8.0,
            "restSeconds": 90,
            "notes": "",
            "isSupersetWithPrevious": false,
            "exerciseName": "Dumbbell Bicep Curl",
            "primaryMuscles": ["biceps"],
            "equipment": "dumbbell"
          }
        ]
      }
    ]
  },
  "generationTimeMs": 12345,
  "modelUsed": "sensenova-6.8-flash-lite",
  "planId": 1,
  "nextReviewAt": "2026-11-30",
  "reviewAfterWeeks": 4,
  "source": "ai"
}
```

**Campos importantes da resposta:**

| Campo | Significado |
|---|---|
| `source` | `ai` quando a IA respondeu; `local` quando foi usado o plano de reserva |
| `nextReviewAt` | Data (ISO `AAAA-MM-DD`) em que o plano deve ser revisto/trocado |
| `reviewAfterWeeks` | Quantas semanas foram usadas no cálculo |
| `planId` | ID da geração registrada em `plan_generations` |
| `exerciseName` | Nome legível do exercício, já enriquecido pelo backend |
| `primaryMuscles` | Músculos principais, para exibição no app |
| `equipment` | Equipamento do exercício |

**Se a IA devolver IDs inválidos:** o backend tenta normalizar e aproximar o ID
do catálogo (por exemplo, `Push-Up` vira `Pushups`). Se ainda assim não
resolver, cai automaticamente no plano local de reserva. A resposta sempre
traz um treino válido.

#### Sincronizar dados de teste por comando

O app envia esse mesmo formato ao tocar em **Sincronizar agora**:

```bash
curl -X POST http://localhost:3000/v1/sync \
  -H "Content-Type: application/json" \
  -d '{
    "deviceId": "meu-aparelho",
    "plans": [
      {
        "localId": 1,
        "name": "Meu plano",
        "goal": "hypertrophy",
        "daysPerWeek": 1,
        "isActive": true,
        "days": [
          {
            "localId": 2,
            "name": "Treino A",
            "dayOrder": 0,
            "exercises": [
              {
                "exerciseId": "Pushups",
                "exerciseOrder": 0,
                "sets": 3,
                "targetReps": "8-12",
                "exerciseName": "Pushups",
                "primaryMuscles": ["chest"],
                "equipment": "body only"
              }
            ]
          }
        ]
      }
    ],
    "sessions": []
  }'
```

Resposta:

```json
{"ok": true, "plans": 1, "sessions": 0, "setLogs": 0, "syncedAt": "..."}
```

Depois confira:

```bash
curl "http://localhost:3000/v1/sync/status?deviceId=meu-aparelho"
```

### 4.5 Parar, reiniciar e limpar

**Docker:**

```bash
docker compose stop          # para
docker compose start         # inicia de novo
docker compose restart       # reinicia
docker compose down          # remove container e rede
docker compose up -d --build # sobe atualizado
```

**Python:**

- Pare com `Ctrl+C`.
- Para subir de novo, ative o ambiente virtual e rode o `uvicorn` novamente.

### 4.6 Atualizar o backend

Se você usa Docker:

```bash
git pull
docker compose up -d --build
```

Se você usa Python:

```bash
git pull
cd back
source .venv/bin/activate   # Windows: .venv\Scripts\Activate.ps1
pip install -r requirements.txt
python -m uvicorn app.main:app --host 0.0.0.0 --port 3000 --reload
```

---

## 5. Conectar o celular ao backend

> **Onde configurar:** no app, abra **Ajustes**, informe a URL do backend e
> toque em **Salvar** e **Testar conexão**. O restante desta seção explica como
> descobrir o endereço correto e testar a API por fora do app.

### 5.1 Mesma rede Wi-Fi

O celular e o computador precisam estar na **mesma rede Wi-Fi**.

1. Descubra o IP do computador.

   Linux:

   ```bash
   hostname -I
   ```

   macOS:

   ```bash
   ipconfig getifaddr en0
   ```

   Windows:

   ```bat
   ipconfig
   ```

   Procure o **Endereço IPv4**, algo como `192.168.1.42`.

2. No celular, o endereço do backend será:

   ```
   http://192.168.1.42:3000
   ```

3. Teste no navegador do celular:

   ```
   http://192.168.1.42:3000/health
   ```

   Se aparecer o JSON de saúde, a conexão está funcionando.

### 5.2 Emulador Android

O emulador não enxerga `localhost` do computador. Use o endereço especial:

```
http://10.0.2.2:3000
```

### 5.3 Celular conectado por USB (adb reverse)

Se o celular estiver conectado por USB com depuração ativada:

```bash
adb reverse tcp:3000 tcp:3000
```

Depois, no celular, use:

```
http://127.0.0.1:3000
```

Isso também funciona quando o Wi-Fi bloqueia a comunicação entre aparelhos.

### 5.4 Firewall do computador

Se o celular não conseguir acessar:

- **Windows:** permita o Docker ou o Python na rede privada no firewall.
- **macOS:** permita conexões de entrada para o Docker/Python em
  **Ajustes → Rede → Firewall**.
- **Linux (ufw):**

  ```bash
  sudo ufw allow 3000/tcp
  ```

> Não abra a porta 3000 para a internet. Ela deve ficar apenas na rede local.

### 5.5 Descoberta automática (mDNS)

O backend tenta anunciar o serviço `_treinos._tcp.local.` na rede local e o app
do celular procura esse serviço em **Ajustes → Backend na rede local →
"Buscar backends"**. A busca só começa quando você toca no botão (nada roda em
segundo plano) e para sozinha ao sair da tela.

- Cada resultado mostra o nome, o `host:porta` e, quando o anúncio traz o TXT
  `version`, a versão. Sem esse campo aparece o aviso "versão não informada".
- **Conectar** faz um `GET /health` no endereço descoberto e **só substitui** a
  URL salva se o health responder. Se não responder, a URL anterior continua
  exatamente como estava.
- Funciona melhor no modo **Python nativo** (`uvicorn`), com `MDNS_ENABLED=true`.
- No **Docker**, o mDNS fica desativado por padrão, porque a rede bridge do
  Docker não repassa multicast. Nesse cenário o anúncio não chega ao celular:
  use o campo de URL manual (ex.: `http://192.168.0.10:3000`). O acesso por IP
  continua funcionando normalmente.
- Para ativar no Docker, seria necessário usar `network_mode: host` (Linux) ou
  configurar multicast manualmente; isso não é necessário para o uso normal.

---

## 6. Uso no dia a dia

### 6.1 Ligar tudo pela manhã

1. Abra o Docker Desktop (se usar Docker).
2. Na raiz do projeto:

   ```bash
   docker compose up -d
   ```

3. Confira:

   ```bash
   curl http://localhost:3000/health
   ```

4. Abra o app no celular.

### 6.2 Desligar tudo

```bash
docker compose stop
```

Se quiser remover o container:

```bash
docker compose down
```

### 6.3 Atualizar o app e o backend

**App:**

1. Baixe o novo APK no link fixo.
2. Instale por cima.

**Backend:**

```bash
git pull
docker compose up -d --build
```

### 6.4 Backup

- O catálogo (`back/app/exercise_catalog.json`) está versionado no Git.
- O arquivo `back/.env` contém sua chave e **não** vai para o Git. Guarde uma
  cópia segura se precisar.
- **Dados do app:** ficam no SQLite interno do aparelho (`treinos.db` do Room).
  Desinstalar o app apaga esses dados. Use **Exportar backup** em Ajustes para
  gerar um JSON e **Importar backup** para restaurá-lo depois — inclusive num
  aparelho recém-formatado. O arquivo (formato `schemaVersion = 3`) contém
  planos, dias, exercícios, séries, sessões, o `deviceId` **e o seu perfil: peso,
  altura e sexo**. Qualquer pessoa com o arquivo lê isso; não compartilhe o JSON como
  quem compartilha uma foto.
  - Arquivos acima de 25 MB são recusados **antes** da leitura, com mensagem de
    tamanho. Isso vale também para backup importado direto do Google Drive,
    OneDrive ou Dropbox — o app abre esses provedores pela mesma tela de arquivos
    do sistema, sem conta nem SDK de nuvem.
  - Backups antigos (`schemaVersion` 1 ou 2) continuam importando. Como eles não
    falam de perfil, o peso, a altura e o sexo **que já estão no aparelho são
    mantidos** — restaurar um backup velho não apaga seu perfil. Um `profile` de
    v3 sem a chave `sex` significa "não informado" e limpa só o sexo (arquivo
    escrito antes da H3-7 não conhece o campo).
  - `sex` fora dos valores conhecidos (`male`, `female`, `other`,
    `undisclosed`) faz o import **rejeitar o arquivo inteiro**, sem tocar nos
    dados nem no perfil salvos.
  - A importação é **destrutiva**: ela substitui todo o banco local. Exporte antes.
  - Também é possível usar **Restaurar do backend**, desde que você já tenha
    sincronizado o `deviceId`.
- **Dados do backend:** ficam em `back/data/treinos.db`. Para backup, copie
  esse arquivo com o backend parado:

  ```bash
  docker compose stop
  cp back/data/treinos.db back/data/treinos-backup-$(date +%F).db
  docker compose start
  ```

  No modo Python, o arquivo fica no mesmo caminho padrão (ou no caminho
  definido em `DATABASE_PATH`).

> A sincronização é um snapshot: o backend guarda a última versão enviada de
> cada plano/treino/série, identificada pelo ID local do aparelho. Restaurar do
> backend substitui o banco local pelo snapshot — exporte um JSON antes se
> quiser um caminho de volta garantido.

### 6.5 Reset total

**App:**

1. Desinstale o app.
2. Instale o APK novo.

**Backend Docker:**

```bash
docker compose down
docker compose up -d --build
```

**Backend Python:**

```bash
cd back
rm -rf .venv
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
```

> No Windows, use `.venv\Scripts\Activate.ps1` no lugar de
> `source .venv/bin/activate`.

---

## 7. Segurança e privacidade

- **O que sai do aparelho ao gerar um treino.** Além dos parâmetros da semana
  (objetivo, dias, equipamentos, foco, nível, duração), o request leva os campos
  que você preencheu em **Seu perfil (opcional)**: **peso**, **altura** e
  **sexo**. Eles viram texto no prompt que o backend envia à SenseNova, e o
  backend guarda o request na tabela `generations` do banco dele.
  - **O padrão é não informar.** Deixe o campo de sexo vazio e nada sobre isso
    sai do aparelho; o mesmo valendo para peso e altura.
  - **"Prefiro não informar"** é uma opção legítima do seletor, ao lado de
    Feminino, Masculino e Outro. Nenhum deles gera erro nem altera as regras
    numéricas do app: descanso, RPE, séries e cargas continuam calculados só
    por IMC, sem coeficiente por sexo.
  - Peso, altura e sexo **não** vão em `/v1/sync` nem para o relógio, e você
    pode limpar a seleção a qualquer momento na tela de geração.
- **A chave da SenseNova fica apenas no backend.** Ela não é enviada para o
  APK.
- **Nunca commite `back/.env`.** O arquivo já está no `.gitignore`.
- Se a chave vazar, revogue no painel da SenseNova e gere outra.
- O backend não tem autenticação. Use apenas na sua rede local ou via VPN.
- O CORS está aberto (`*`) para facilitar o desenvolvimento local. Se for
  expor o backend, restrinja as origens.
- Não exponha a porta `3000` diretamente na internet. Se precisar de acesso
  remoto, use Tailscale, WireGuard ou um túnel com autenticação.
- O APK é um build de debug de teste. Para distribuição real, configure uma
  chave de release própria.

---

## 8. Solução de problemas

### 8.1 App

| Sintoma | O que verificar | Solução |
|---|---|---|
| Download não começa | Conexão ou bloqueio do navegador | Abra o link no Chrome/Firefox e permita downloads |
| "Pacote inválido" | Arquivo incompleto | Baixe novamente e confira o checksum |
| "App não instalado" | Assinatura, espaço ou versão | Desinstale a versão antiga e reinstale |
| Play Protect bloqueia | APK fora da loja | **Mais detalhes → Instalar mesmo assim** |
| App não aparece | Instalação não concluída | Abra a gaveta de apps e procure "Treinos" |
| Tela só com "Treinos App" | Versão atual | Comportamento esperado |
| App fecha sozinho | Incompatibilidade | Confira Android 8.0+ e reinstale |
| Não atualiza | Chave de assinatura mudou | Desinstale e instale de novo |

### 8.2 Backend com Docker

| Sintoma | Causa provável | Solução |
|---|---|---|
| `Cannot connect to the Docker daemon` | Docker não está rodando | Abra o Docker Desktop e aguarde iniciar |
| `docker compose: command not found` | Compose não instalado | Atualize o Docker Desktop ou instale o plugin |
| `port is already allocated` | Porta 3000 ocupada | Pare o outro processo ou troque a porta no `docker-compose.yml` |
| Container reinicia sozinho | Erro no código ou dependência | Veja `docker compose logs treinos-backend` |
| Status `unhealthy` | Backend ainda iniciando ou falhou | Aguarde 30s; se persistir, veja os logs |
| `Conflict. The container name "/treinos-backend"` | Container antigo com o mesmo nome | `docker rm -f treinos-backend` e suba de novo |
| `permission denied` no Linux | Usuário fora do grupo docker | `sudo usermod -aG docker $USER` e faça logout/login |
| `.env` não encontrado | Arquivo não criado | `cp back/.env.example back/.env` na raiz |
| Chave não carregada | `.env` no lugar errado | O arquivo deve ser `back/.env` |

**Ver a porta 3000 ocupada:**

Linux/macOS:

```bash
lsof -i :3000
```

Windows:

```bat
netstat -ano | findstr :3000
taskkill /PID NUMERO_DO_PID /F
```

**Mudar a porta do Docker:**

No `docker-compose.yml`, altere:

```yaml
ports:
  - "3001:3000"
```

Depois use `http://localhost:3001`. Se for acessar pelo celular, use a porta
3001 também.

### 8.3 Backend com Python

| Sintoma | Causa provável | Solução |
|---|---|---|
| `python: command not found` | Python não instalado/PATH | Instale o Python e marque "Add to PATH" |
| `No module named uvicorn` | Ambiente virtual não ativado | Ative o `.venv` e instale as dependências |
| `No module named zeroconf` | Dependências incompletas | `pip install -r requirements.txt` |
| Erro ao instalar `zeroconf` no Windows | Falta compilador | Instale as Build Tools do Visual Studio ou use Docker |
| Porta ocupada | Outro processo usando 3000 | Use `--port 3001` ou pare o outro processo |
| mDNS falha | Rede sem multicast | O servidor continua; use o IP direto |
| `.env` ignorado | Arquivo em outro diretório | Confirme `back/.env`; o backend carrega automaticamente |

### 8.4 Erros da API

| Resposta | Significado | Solução |
|---|---|---|
| `500 API key not configured on server` | `SENSENOVA_API_KEY` vazia | Preencha `back/.env` e reinicie |
| `500 Exercise catalog not loaded` | Catálogo não encontrado | Confirme `back/app/exercise_catalog.json` |
| `500 Invalid exercise ID returned by AI` | A IA devolveu ID fora do catálogo | Tente de novo; limitação conhecida |
| `502 SenseNova API error` | Chave inválida, cota ou instabilidade | Confira a chave e o painel da SenseNova |
| `504 Timeout calling SenseNova API` | A IA demorou mais de 60s | Tente novamente ou reduza o pedido |
| `422 Unprocessable Entity` | JSON fora do schema | Confira os campos e valores aceitos |

---

## 9. Referência rápida de comandos

### App

```bash
# Gerar APK debug
./gradlew :app:assembleDebug

# Instalar no dispositivo conectado
adb install -r app/build/outputs/apk/debug/app-debug.apk

# Ver dispositivos conectados
adb devices

# Redirecionar a porta do backend para o celular via USB
adb reverse tcp:3000 tcp:3000
```

### Backend com Docker

```bash
# Subir
docker compose up -d --build

# Status
docker compose ps

# Logs
docker compose logs -f treinos-backend

# Parar
docker compose stop

# Iniciar de novo
docker compose start

# Remover container e rede
docker compose down

# Testar saúde
curl http://localhost:3000/health
```

### Backend com Python

```bash
cd back
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
python -m uvicorn app.main:app --host 0.0.0.0 --port 3000 --reload
```

### URLs úteis

| URL | O que é |
|---|---|
| `http://localhost:3000/health` | Saúde do backend |
| `http://localhost:3000/docs` | Documentação interativa |
| `http://localhost:3000/v1/catalog` | Catálogo completo |
| `http://10.0.2.2:3000` | Backend visto pelo emulador Android |
| `http://IP_DO_PC:3000` | Backend visto pelo celular na Wi-Fi |

---

## 10. Perguntas frequentes

**Preciso pagar alguma coisa?**
O app e o backend são gratuitos. A SenseNova pode ter plano gratuito com
limites; consulte o site oficial.

**Preciso de internet para usar o app?**
Só para gerar um treino novo e para sincronizar. Executar treinos, registrar
séries e ver o histórico funcionam offline, porque tudo fica no SQLite do
aparelho.

**Funciona no iPhone?**
Não. O projeto é Android, com um app separado para Wear OS.

**Funciona no meu relógio?**
Sim, no Galaxy Watch FE: o app do relógio é o `wear.apk`, distribuído junto com
o `treinos.apk` (H2-2).

**Onde ficam meus dados?**
No celular: banco Room/SQLite interno do app, preenchido na hora em que você
salva cada série. No computador: `back/data/treinos.db`, preenchido apenas
quando você toca em **Sincronizar agora**.

**A sincronização é automática?**
Não. O app só envia dados ao backend quando você toca no botão. Gerar um treino
também chama o backend, mas isso apenas registra a geração; não envia suas
séries.

**O que é a data de revisão?**
É a data devolvida pelo backend (`nextReviewAt`) indicando quando o plano deve
ser revisto ou trocado. Ela aparece na tela inicial; se passar, o app mostra
"Revisão atrasada" e você pode gerar um treino novo.

**Posso usar sem backend?**
Sim, para treinar com um plano que já esteja salvo. Sem backend você não
consegue gerar um plano novo nem sincronizar.

**Posso usar o backend sem a chave da SenseNova?**
Sim. A geração usa o plano local de reserva montado com o catálogo. Com a
chave, o backend tenta primeiro a IA.

**O APK é seguro?**
Ele é gerado pelo GitHub Actions a partir deste repositório. Ainda assim, é
um build de debug de teste. Confira o checksum e instale apenas se confiar na
origem.

**Por que o APK é de debug?**
Porque o objetivo atual é testar rápido. Para distribuição real, configure uma
chave de release.

**Como atualizo o backend?**
`git pull` e depois `docker compose up -d --build` (ou reinstale as
dependências no modo Python).

**O que faço se a porta 3000 estiver ocupada?**
Pare o processo que está usando a porta ou troque para `3001` no
`docker-compose.yml`.

**Como acesso o backend de outro computador?**
Use o IP do computador na rede local, por exemplo `http://192.168.1.42:3000`,
e libere a porta no firewall.

---

## 11. Glossário

| Termo | Significado |
|---|---|
| **APK** | Arquivo de instalação de app Android |
| **Backend** | Programa que roda no computador e fornece dados/serviços |
| **Frontend** | Parte visual, o app no celular |
| **Docker** | Ferramenta que empacota e executa o backend em um container |
| **Container** | Ambiente isolado onde o backend roda |
| **FastAPI** | Framework Python usado no backend |
| **Uvicorn** | Servidor que executa a aplicação FastAPI |
| **Endpoint** | Endereço da API, como `/health` |
| **JSON** | Formato de texto usado para trocar dados |
| **mDNS** | Descoberta automática de serviços na rede local |
| **SenseNova** | Serviço de IA usado para gerar os treinos |
| **`localhost`** | "Este computador" |
| **`10.0.2.2`** | Endereço do computador visto pelo emulador Android |
| **`adb`** | Ferramenta do Android para instalar apps e acessar o celular |
| **IP** | Endereço do computador/celular na rede |
| **Firewall** | Proteção que pode bloquear conexões de rede |

---

## Precisa de mais ajuda?

- Abra uma issue no repositório:
  https://github.com/T3lo5/gym-program/issues
- Inclua:
  - o que você tentou fazer;
  - a mensagem de erro completa;
  - o sistema operacional;
  - se usou Docker ou Python;
  - a saída de `docker compose logs treinos-backend`, se aplicável.

---

**Última atualização:** 02/11/2026
