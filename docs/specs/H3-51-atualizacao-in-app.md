# H3-51 — Atualização dentro do app (in-app updater)

## Status

| Campo | Valor |
|---|---|
| Item do roadmap | `H3-51` |
| MoSCoW | `C` |
| Spec aprovada em | `2026-11-13` |
| Implementação | `concluída` |

## Objetivo

O próprio app avisa que existe versão nova no GitHub Releases e instala a
atualização sem sair para um app externo.

## Fora de escopo

- Backend novo ou endpoint próprio de versão (a fonte é a API pública do
  GitHub).
- Módulo `:wear`.
- Rollback/downgrade de versão.
- Assinatura estável (H3-48 — pré-requisito; sem ela o update falha com
  `INSTALL_FAILED_UPDATE_INCOMPATIBLE`).

## Arquivos e símbolos que devem mudar

| Arquivo | Mudança |
|---|---|
| `app/src/main/java/com/treinos/app/updater/ReleaseChecker.kt` | novo: busca `releases/latest` na API do GitHub (repositório em constante de build), extrai `tag_name`, `versionCode` do release e as URLs de `treinos.apk` e `dist.sha256` |
| `app/src/main/java/com/treinos/app/updater/UpdateInstaller.kt` | novo: download em cache interno, conferência do SHA-256 e disparo da instalação via `ACTION_VIEW` + `FileProvider` |
| `app/src/main/java/com/treinos/app/ui/screens/SettingsScreen.kt` | card "Nova versão disponível" (versão nova + botão "Baixar e instalar") e botão "Verificar atualização" |
| `app/src/main/AndroidManifest.xml` | `REQUEST_INSTALL_PACKAGES` |
| `app/src/test/java/com/treinos/app/updater/ReleaseParserTest.kt` | testes JVM do parser |
| `app/src/test/java/com/treinos/app/updater/VersionCompareTest.kt` | testes JVM da comparação de versão |

## Modelo de dados / contrato

```text
GET https://api.github.com/repos/<owner>/<repo>/releases/latest
→ { "tag_name": "...", "assets": [
     { "name": "treinos.apk", "browser_download_url": "..." },
     { "name": "dist.sha256", "browser_download_url": "..." } ] }

Sem autenticação (rate limit 60/h por IP — suficiente para uso pessoal).
Checagem: ao abrir Ajustes + botão manual; nunca polling em loop.
```

## Regras e casos de borda

- Sem rede, 403 (rate limit) ou resposta sem o asset `treinos.apk` →
  silencioso (nunca bloqueia o uso); o botão manual mostra o erro discreto.
- `versionCode` remoto ≤ local → nenhum card.
- Download em cache interno; hash conferido contra o `dist.sha256` do release
  antes de instalar; hash divergente → descarta o arquivo e mostra erro.
- A instalação usa o fluxo do sistema (o Android pede a permissão "instalar
  apps desconhecidos" no momento do primeiro update).
- Offline-first intacto: nada roda em loop/background contínuo; sem
  WorkManager.

## Critérios de aceite

1. **Dado** release publicado com `versionCode` maior, **quando** Ajustes abre
   (ou o botão "Verificar" é tocado), **então** aparece o card "Nova versão
   disponível" com a versão nova.
2. **Dado** sem rede, **quando** verificar, **então** erro discreto no botão
   manual e o app funciona normalmente.
3. **Dado** APK baixado com hash válido, **quando** "Baixar e instalar",
   **então** o fluxo de instalação do sistema abre; **dado** hash divergente,
   **então** o arquivo é descartado com erro e nada instala.
4. **Dado** `versionCode` remoto ≤ local, **quando** verificar, **então**
   nenhum card aparece.

## Testes a criar

| Arquivo | Testes |
|---|---|
| `app/src/test/java/com/treinos/app/updater/ReleaseParserTest.kt` | `parse_extrai_apk_e_sha256`, `parse_sem_asset_apk_retorna_vazio`, `parse_resposta_malformada_nao_lanca` |
| `app/src/test/java/com/treinos/app/updater/VersionCompareTest.kt` | `remoto_maior_mostra_card`, `igual_nao_mostra`, `remoto_menor_nao_mostra`, `local_invalido_tratado_como_zero` |

## Verificação manual

1. Publicar release com `versionCode` maior → card aparece, instala por cima,
   dados preservados.
2. Modo avião → sem card; botão manual mostra erro discreto.

## Comando de verificação

```bash
./scripts/verify.sh
```

## Riscos e rollback

- `REQUEST_INSTALL_PACKAGES` é permissão sensível: irrelevante fora da Play
  Store (uso pessoal/sideload), mas se um dia houver publicação em loja, a
  política exige justificativa — decidir remover a feature antes disso.
- Rollback: feature isolada atrás do card em Ajustes; remover o card e o
  pacote `updater` restaura o comportamento anterior.
- Rate limit da API do GitHub: a checagem é sob demanda (Ajustes + botão), não
  periódica.
