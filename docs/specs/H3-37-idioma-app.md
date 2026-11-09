# H3-37 — Idioma do app: PT/EN com seletor

## Status

| Campo | Valor |
|---|---|
| Item do roadmap | `H3-37` |
| MoSCoW | `S` |
| Spec aprovada em | `2026-11-09` |
| Implementação | `concluída` |

**Origem:** pedido do usuário (2026-11-07) — "um app que funcione tanto em
português quanto em inglês, deixando o usuário selecionar a língua, já que
temos os dois idiomas usando os mesmos IDs" + auditoria ([relatório](../auditoria-ux-ui-2026-11-07.md) §P4.10).

## Objetivo

O usuário escolhe o idioma da interface (Português/English) em Ajustes — e
aparelhos em inglês já nascem com a UI em inglês, sem configurar nada.

## Contexto (estado atual verificado)

- Todo texto da UI é hardcoded em português no Kotlin (zero
  `stringResource`/`R.string` no módulo); `strings.xml` tem 57 strings mortas.
- `appcompat:1.6.1` já é dependência (`app/build.gradle.kts:122`) — o backport
  de per-app locale não exige dependência nova; `MainActivity` é
  `ComponentActivity` (precisa virar `AppCompatActivity`).
- `minSdk 26`: persistir a escolha em < Android 13 exige
  `AppLocalesMetadataHolderService` no manifest.
- O conteúdo de exercício (nomes/instruções) segue o idioma via H3-36.

## Fora de escopo

- Wear (segue PT por enquanto); backend (H3-36); línguas além de PT/EN;
  layouts RTL; tradução das specs/docs.

## Arquivos e símbolos que devem mudar

| Arquivo | Mudança |
|---|---|
| `app/src/main/res/values/strings.xml` | reescrito: strings PT da UI (default) |
| `app/src/main/res/values-en/strings.xml` (novo) | tradução EN 1:1 (mesmas chaves) |
| `app/src/main/java/com/treinos/app/ui/MainActivity.kt` | `AppCompatActivity`; aplica `AppCompatDelegate.setApplicationLocales` |
| `app/src/main/AndroidManifest.xml` | `AppLocalesMetadataHolderService` (persistência < 33) + `android:localeConfig` |
| `app/src/main/res/xml/locales_config.xml` (novo) | `pt-BR` e `en` declarados |
| `app/src/main/java/com/treinos/app/ui/screens/SettingsScreen.kt` | seletor "Idioma do app": Padrão do sistema / Português / English |
| `app/src/main/java/com/treinos/app/ui/AppViewModel.kt` | mensagens/snackbars via recursos (contexto da `Application`) |
| `app/src/main/java/com/treinos/app/ui/Formatters.kt` | datas/labels seguem o locale do app (H3-33 fixa o formato numérico) |

## Modelo de dados / contrato

- Sem mudança de rede/banco. A preferência de idioma é gerida pelo sistema
  (per-app locale) — não por `AppPreferences` (evita duas fontes de verdade);
  Android 13+ integra com as configurações do sistema.
- Paridade de recursos: toda chave em `values/strings.xml` existe em
  `values-en/strings.xml` (teste garante).

## Regras e casos de borda

- Default = PT (`values/`); aparelho com sistema em inglês e **sem** seleção
  explícita recebe `values-en` automaticamente (fallback de recursos).
- Seletor: "Padrão do sistema / Português / English" — aplicar via
  `AppCompatDelegate.setApplicationLocales` (recria a activity; estado de
  formulário já sobrevive pela H3-31).
- Mensagens do ViewModel (snackbars/erros) saem de recursos, não de string
  literal — o ViewModel recebe o contexto da `Application` que já possui.
- Fases de extração (cada uma com gate verde): **Fase 1** — execução
  (Workout/Run/Mobility) + Home; **Fase 2** — Generate/History/LogActivity/
  Settings + componentes + mensagens do ViewModel.
- Placeholders usam `%1$s`/`%1$d` (revisar plurais com `plurals` onde houver
  contagem de séries).
- Formatters de data passam a usar o locale do app; números continuam pt-BR
  fixos (H3-33) — decisão explícita: dados de treino não mudam de formato.

## Critérios de aceite

1. **Dado** aparelho em inglês sem seleção, **quando** abre o app, **então** a
   UI aparece em inglês.
2. **Dado** seletor em Ajustes, **quando** escolhe "English", **então** toda a
   UI (incluindo snackbars e diálogos) muda para inglês sem reiniciar o app.
3. **Dado** idioma English, **quando** gera um plano com `language = "en"`
   (H3-36), **então** nomes/instruções vêm em inglês.
4. **Dado** idioma Português, **quando** usa o app, **então** nada mudou em
   relação ao comportamento atual.
5. **Dado** o build, **quando** o teste de paridade roda, **então** toda chave
   PT tem equivalente EN (e vice-versa).

## Testes a criar

| Arquivo | Testes |
|---|---|
| `app/src/test/java/.../StringParityTest.kt` | `chaves_pt_tem_equivalente_en`, `sem_placeholder_faltando` |
| `app/src/test/java/.../HomeScreenTest.kt` (Robolectric, locale en) | `home_em_ingles_com_locale_en` |
| `app/src/test/java/.../A11yTest.kt` | `content_descriptions_localizados` |

## Verificação manual

1. Trocar o idioma em Ajustes → percorrer todas as telas → nada em português
   no modo English (checklist das 8 telas + diálogos + snackbars).
2. Android 13+: conferir que o idioma escolhido aparece em
   Configurações do sistema → Apps → Treinos → Idioma.

## Comando de verificação

```bash
./scripts/verify.sh
```

## Riscos e rollback

- Extração mecânica longa (~200+ strings): fazer por fases com o gate verde
  entre elas; strings perdidas aparecem como chave crua no teste de paridade.
  Rollback por fase (a UI volta ao hardcoded da fase anterior).
