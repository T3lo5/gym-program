# H3-48 — Assinatura estável e versionamento dos APKs (celular e relógio)

## Status

| Campo | Valor |
|---|---|
| Item do roadmap | `H3-48` |
| MoSCoW | `M` |
| Spec aprovada em | `2026-11-13` |
| Implementação | `concluída` |

> **Atualização 2026-11-15 — numeração única.** O `versionCode` deixou de ser
> `git rev-list --count HEAD` (local) / `run_number` (CI) e passou a ser
> **minutos desde 2020-01-01T00:00:00Z**, calculado por `scripts/version.sh` —
> a mesma fonte no CI e no build local. O `versionName` passou a vir de
> `version.properties` (hoje `1.5.0`). Motivo: as duas sequências divergiam
> (release 166 × local 228), o updater in-app (H3-51) dizia "já atualizado" e
> instalar o release falhava com `INSTALL_FAILED_VERSION_DOWNGRADE`.

## Objetivo

Toda atualização do app (celular e relógio) instala por cima da anterior, sem
desinstalar e sem perder os dados locais, em qualquer ambiente (Docker local,
CI ou máquina do dono).

## Contexto (diagnóstico de 2026-11-13)

O mecanismo já existe no repositório, mas está inerte:

1. `app/build.gradle.kts:48` resolve `file("app/debug.keystore")` relativo ao
   **diretório do módulo** (`app/`) e procura `app/app/debug.keystore`, que não
   existe; o arquivo real é `app/debug.keystore` (versionado desde `8c3e0ce`).
   O `if (debugKeystoreFile.exists())` (linha 129) falha em silêncio e o APK
   cai na keystore efêmera do ambiente — o comentário das linhas 46-47
   ("app updates install over each other") nunca valeu.
2. O gate local roda com `HOME=/tmp` e `ANDROID_USER_HOME=/tmp/.android`
   (`scripts/verify.sh:48-54`): cada build local gera uma debug.keystore nova
   → `INSTALL_FAILED_UPDATE_INCOMPATIBLE` → desinstalar (e perder o Room).
3. O CI paliativo cacheia `~/.android/debug.keystore`
   (`.github/workflows/android.yml:46-50`), mas caches do GitHub expiram após
   7 dias sem uso — depois de uma evicção os releases mudam de assinatura.
4. `:wear` não tem `signingConfigs` (assina com a keystore do ambiente) e
   `versionCode` é fixo em `1` (`wear/build.gradle.kts:15-16`), ignorando o
   `-PversionCode` que o `ci-android.sh` já passa (linha 55).

A keystore versionada abre com as credenciais do build (`treinos123`, alias
`treinos-debug`); SHA-256 do certificado:
`71:A5:A2:AC:B0:D1:42:B6:E2:45:82:EA:8C:24:58:F6:9D:69:E8:45:86:B9:AC:0C:49:38:C1:F1:09:EA:74:6F`.

## Fora de escopo

- Keystore de release/produção, Play Store e publicação em loja.
- Rotação de chaves, senhas novas ou keystore nova.
- Scripts de instalação e manual do relógio (H3-49).
- Auto-update via Obtainium (H3-50) e in-app updater (H3-51).

## Arquivos e símbolos que devem mudar

| Arquivo | Mudança |
|---|---|
| `app/build.gradle.kts` | `debugKeystoreFile = file("debug.keystore")` (caminho correto relativo ao módulo) |
| `wear/build.gradle.kts` | `signingConfigs` com a mesma keystore versionada (`file("../app/debug.keystore")`) aplicada ao `buildType` debug; `versionCode`/`versionName` lendo `-PversionCode`/`-PversionName` com default `1`/`"1.0.0"` |
| `scripts/ci-android.sh` | `VERSION_CODE`/`VERSION_NAME` derivados de `scripts/version.sh` (fonte única; ver nota de numeração única); verificação do fingerprint do certificado esperado nos dois APKs |
| `scripts/version.sh` (novo) | fonte única de `versionCode` (minutos desde 2020-01-01) e `versionName` (lê `version.properties`) |
| `version.properties` (novo) | fonte única do `versionName` (hoje `1.5.0`) |
| `.github/workflows/android.yml` | "Compute version" chama `scripts/version.sh` (fim do `run_number`); comentário de que o corpo do release é contrato do updater (H3-51) |
| `scripts/verify.sh` | repassa `VERSION_CODE`/`VERSION_NAME` ao container só quando definidos — forçar `1` anularia a derivação local do `ci-android.sh` |
| `.github/workflows/android.yml` | remover o passo "Cache debug signing key" (redundante com a keystore versionada) |

## Modelo de dados / contrato

Nenhum. Sem mudança em `:protocol`, Room ou backend.

## Regras e casos de borda

- A assinatura do `:app` e do `:wear` passa a ser idêntica em qualquer ambiente
  porque vem do arquivo versionado, não do ambiente.
- `DEBUG_KEYSTORE_PASSWORD`/`DEBUG_KEY_ALIAS`/`DEBUG_KEY_PASSWORD` continuam
  sobrescrevíveis por ambiente (comportamento atual preservado).
- `versionCode` é monotônico no tempo (`scripts/version.sh`), igual no CI e no
  build local, então `adb install -r` de um build mais novo nunca é rejeitado
  por versão. (Superado em 2026-11-15: antes o local usava o contador de commits
  e o CI o `run_number`, sequências que divergiam e quebravam a atualização.)
- Se `app/debug.keystore` não existir (checkout parcial), o comportamento atual
  se mantém (assinatura do ambiente), e o gate falha na verificação do
  certificado esperado — o erro deixa de ser silencioso.

## Critérios de aceite

1. **Dado** um checkout limpo sem `~/.android/debug.keystore`, **quando**
   `./scripts/verify.sh` termina, **então** `apksigner verify --print-certs
   dist/treinos.apk` e `dist/wear.apk` mostram o SHA-256 `71:A5:A2:AC:…74:6F`.
2. **Dado** dois `./scripts/verify.sh` consecutivos, **quando** o segundo APK é
   instalado com `adb install -r` sobre o primeiro, **então** instala sem
   desinstalar (mesma assinatura, dados preservados).
3. **Dado** o workflow sem o passo de cache, **quando** o release roda,
   **então** os APKs publicados têm a mesma assinatura dos builds locais e o
   workflow continua chamando `scripts/ci-android.sh`.
4. **Dado** `-PversionCode=42`, **quando** `:wear:assembleDebug`, **então**
   `aapt2 dump badging wear/build/outputs/apk/debug/wear-debug.apk` mostra
   `versionCode='42'`; sem a propriedade, `1`.
5. **Dado** dois builds locais em commits diferentes, **quando** comparados,
   **então** o `versionCode` do commit mais novo é maior.

## Testes a criar

Nenhum teste de unidade novo: a mudança é de configuração de build. A prova é
o gate (`apksigner verify` nos dois APKs) e a verificação manual.

| Arquivo | Testes |
|---|---|
| — | — |

## Verificação manual

1. Backup local em dia (H1-7) — a **primeira** instalação após esta mudança
   exige desinstalação única (a assinatura muda da efêmera para a versionada).
2. `./scripts/verify.sh` duas vezes; `adb install -r dist/treinos.apk` na
   segunda → instala por cima, dados preservados.
3. `apksigner verify --print-certs dist/wear.apk` → fingerprint esperado.

## Comando de verificação

```bash
./scripts/verify.sh
```

## Riscos e rollback

- Migração: a primeira instalação pós-fix requer desinstalar uma única vez
  (perde dados locais — fazer backup antes). Depois disso, todo update é por
  cima.
- Reverter o commit restaura o comportamento atual (builds efêmeros), sem dano
  adicional.
- Remover o cache do workflow não afeta o resto do job: a keystore vem do
  repositório e o job continua delegando para `scripts/ci-android.sh`.
