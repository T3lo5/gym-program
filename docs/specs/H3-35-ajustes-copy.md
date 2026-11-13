# H3-35 — Ajustes organizados e copy do usuário

## Status

| Campo | Valor |
|---|---|
| Item do roadmap | `H3-35` |
| MoSCoW | `S` |
| Spec aprovada em | `2026-11-09` (implementada no commit `95d6709`) |
| Implementação | `concluída em 2026-11-09` (commit `95d6709`) |

**Origem:** auditoria UX/UI de 2026-11-07 ([relatório](../auditoria-ux-ui-2026-11-07.md) §P2.8, P2.9 e P4.9).

## Objetivo

Ajustes agrupa por intenção, protege a ação mais destrutiva do app e fala a
língua do usuário — sem jargão de infraestrutura; e retomar um treino em
andamento custa 1 toque no launcher.

## Contexto (estado atual verificado)

- 6 cards flat de infraestrutura com peso visual igual (~2 telas de rolagem):
  Backend (`SettingsScreen.kt:166`), mDNS (`:236`), Sync (`:348`), Restaurar
  (`:432`), Catálogo (`:497`), Backup (`:535`).
- "Restaurar do backend" e restore por deviceId têm AlertDialog, mas o import
  destrutivo vai da pré-visualização direto para **"Substituir tudo"**
  (`:596-624`) — a única ação crítica sem confirmação.
- Dica de emulador vazou para produção: "No emulador, use
  http://10.0.2.2:3000" (`:176-178`); jargão: "backend", "ID deste aparelho:
  $deviceId" (`:228`), "_treinos._tcp… rede bridge" (`:246-248`), "Benchmark
  5 km (s, opcional)" em segundos (`GenerateScreen.kt:311-312`), RPE sem
  explicação (`LogActivityScreen.kt:149`).
- Manifest só tem MAIN/LAUNCHER (`AndroidManifest.xml:23-31`) — sem shortcut
  para retomar treino.

## Fora de escopo

- Remover funcionalidades; auto-backup (H3-9, Won't now); mudar comportamento
  de sync/backup; deep links de conteúdo (H3-38 cobre o shortcut).

## Arquivos e símbolos que devem mudar

| Arquivo | Mudança |
|---|---|
| `app/src/main/java/com/treinos/app/ui/screens/SettingsScreen.kt` | seções colapsáveis; AlertDialog no import; copy sem jargão |
| `app/src/main/java/com/treinos/app/ui/screens/GenerateScreen.kt` | campo 5 km aceita `mm:ss`; helper de RPE |
| `app/src/main/java/com/treinos/app/ui/screens/LogActivityScreen.kt` | helper de RPE |
| `app/src/main/java/com/treinos/app/ui/RunLog.kt` | `parse5k(value): Int?` puro (mm:ss ou segundos crus) |
| `app/src/main/AndroidManifest.xml` + `app/src/main/res/xml/shortcuts.xml` (novo) | shortcut dinâmico "Retomar treino" |
| `app/src/main/java/com/treinos/app/ui/MainActivity.kt` + `AppViewModel.kt` | tratar o extra do shortcut → `resumeWorkout` |
| `docs/MANUAL.md` | dica de emulador/IP movida para cá |

## Modelo de dados / contrato

- Shortcut dinâmico: id `resume_workout`, intent para `MainActivity` com
  extra `extra_resume_session = true`; publicado via
  `ShortcutManagerCompat.pushDynamicShortcut` quando
  `inProgressSession != null` e removido quando não há sessão.
- `parse5k`: aceita `"25:30"` → 1530, `"1530"` → 1530, rejeita `"25:70"`,
  vazio e negativo. Sem mudança de rede/banco.

## Regras e casos de borda

- Seções de Ajustes: **Treino** (catálogo de exercícios), **Sincronização**
  (backend + descoberta + sync + restaurar — colapsável, colapsado por
  padrão), **Backup** (exportar/importar/restaurar por deviceId).
- "Substituir tudo" exige AlertDialog com o resumo do que será substituído
  (padrão de diálogo já usado no mesmo arquivo).
- Copy: "backend" → "seu servidor (o computador com o Treinos)"; "ID deste
  aparelho" → "Código deste aparelho (para restaurar)"; descoberta sem
  "_treinos._tcp" e sem "rede bridge"; RPE com helper "Esforço de 1 (leve) a
  10 (máximo)".
- Benchmark 5 km: aceita `mm:ss` (e segundos crus, compatibilidade); exibe o
  formato aceito no label.
- Shortcut: só aparece com sessão em andamento; tocar abre direto a tela de
  treino (sem passar pela Home).

## Critérios de aceite

1. **Dado** Ajustes aberta, **quando** rola, **então** vê 3 seções com
   Sincronização colapsada por padrão.
2. **Dado** pré-visualização de import, **quando** toca "Substituir tudo",
   **então** um AlertDialog confirma antes de qualquer escrita.
3. **Dado** usuário digitando "25:30" no 5 km, **quando** salva, **então** o
   valor gravado é 1530 s e o campo aceita o formato.
4. **Dado** treino em andamento, **quando** pressiona o ícone do app, **então**
   o shortcut "Retomar treino" aparece e abre a tela de treino direto.
5. **Dado** qualquer tela com "backend" hoje, **quando** lê o texto, **então**
   não há jargão de infraestrutura (backend/deviceId/_treinos._tcp/10.0.2.2).

## Testes a criar

| Arquivo | Testes |
|---|---|
| `app/src/test/java/.../RunLogTest.kt` | `parse5k_mm_ss`, `parse5k_segundos_crus`, `parse5k_invalido_null` |
| `app/src/test/java/.../SettingsScreenTest.kt` (Robolectric) | `import_exige_confirmacao`, `secoes_colapsaveis` |

## Verificação manual

1. Percorrer Ajustes ponta a ponta → sem jargão; import com confirmação.
2. Iniciar treino → checar launcher (toque longo no ícone) → shortcut
   presente → tocar → tela de treino direta.

## Comando de verificação

```bash
./scripts/verify.sh
```

## Riscos e rollback

- Shortcut dinâmico é inofensivo se o extra for ignorado por versões antigas
  do código. Copy altera textos de teste existentes — atualizá-los junto.
  Rollback trivial (UI + manifest).
