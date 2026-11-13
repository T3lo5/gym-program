# H3-49 — Instalação e atualização por script (celular e relógio)

## Status

| Campo | Valor |
|---|---|
| Item do roadmap | `H3-49` |
| MoSCoW | `S` |
| Spec aprovada em | `pendente de aprovação` |
| Implementação | `pendente` |

## Objetivo

Instalar ou atualizar os APKs do repositório no celular e no relógio com um
comando, sem desinstalar e sem precisar relembrar os comandos de `adb` a cada
release.

## Fora de escopo

- Assinatura estável (H3-48 — pré-requisito: sem ela o `-r` falha entre builds).
- Auto-update via Obtainium (H3-50) e in-app updater (H3-51).
- Instalação via Play Store ou companion automático do Wear OS.
- Desinstalação automatizada (nunca existe neste escopo).

## Arquivos e símbolos que devem mudar

| Arquivo | Mudança |
|---|---|
| `scripts/install.sh` | novo: `adb install -r` do APK do celular (default `dist/treinos.apk`; caminho aceito como `$1`) |
| `scripts/install-wear.sh` | novo: conecta o relógio por ADB e instala `dist/wear.apk` — rota Wi-Fi `adb connect <ip>:5555` (IP como `$1`) ou rota Bluetooth (`--bt`: `adb forward tcp:4444 localabstract:/adb-hub` + `adb connect localhost:4444`) |
| `docs/MANUAL.md` | ampliar as seções de instalação existentes (~105, ~117-118, ~204) com "Instalar/atualizar pelo computador" e "Instalar/atualizar no relógio" (Opções do desenvolvedor no relógio, Depuração ADB, Depuração por Wi-Fi/Bluetooth) |

## Modelo de dados / contrato

Nenhum. Scripts de shell + documentação.

## Regras e casos de borda

- Nenhum script desinstala nada, nunca. Em `INSTALL_FAILED_UPDATE_INCOMPATIBLE`
  o script explica: a assinatura mudou → fazer backup, desinstalar manualmente
  e reinstalar — e aponta a H3-48 como correção definitiva da causa.
- Sem `adb` no PATH → mensagem com o pacote `platform-tools`, exit 1.
- Sem dispositivo conectado (celular) ou sem IP/`--bt` (relógio) → mensagem com
  os passos de conexão, exit 1, nada instala.
- `install-wear.sh` espera o dispositivo aparecer após `adb connect` (timeout
  curto e mensagem de falha explícita; o modo "Depuração por Wi-Fi" do relógio
  precisa estar ativo).
- Scripts executáveis (`chmod +x`), sem dependência além de `adb` e do APK em
  `dist/`.

## Critérios de aceite

1. **Dado** `dist/treinos.apk` existente e celular conectado, **quando**
   `./scripts/install.sh`, **então** instala por cima preservando os dados.
2. **Dado** nenhum dispositivo conectado, **quando** `./scripts/install.sh`,
   **então** mensagem clara de conexão e exit 1 (nada instala).
3. **Dado** relógio com "Depuração por Wi-Fi" ativa e IP informado, **quando**
   `./scripts/install-wear.sh <ip>`, **então** conecta e instala
   `dist/wear.apk` por cima.
4. **Dado** `INSTALL_FAILED_UPDATE_INCOMPATIBLE`, **quando** qualquer script
   roda, **então** a mensagem orienta backup antes de desinstalar e o script
   sai com erro sem desinstalar nada.

## Testes a criar

Nenhum (scripts de shell + documentação). A prova é a verificação manual e o
gate continuar verde.

| Arquivo | Testes |
|---|---|
| — | — |

## Verificação manual

1. Celular: rodar `./scripts/install.sh` duas vezes seguidas — a segunda é
   update por cima (dados preservados).
2. Relógio: seguir a seção nova do manual (Wi-Fi), rodar
   `./scripts/install-wear.sh <ip>`, confirmar o app atualizado no relógio.
3. Caminho de erro: desconectar o relógio no meio da instalação → mensagem
   clara e exit diferente de 0.

## Comando de verificação

```bash
./scripts/verify.sh
```

## Riscos e rollback

- Mudança aditiva (scripts novos + seção de manual); rollback = remover os
  arquivos e a seção.
- Risco real: o usuário desinstalar sem backup ao ver o erro — por isso a
  mensagem coloca o backup ANTES da desinstalação e nunca automatiza a
  desinstalação.
