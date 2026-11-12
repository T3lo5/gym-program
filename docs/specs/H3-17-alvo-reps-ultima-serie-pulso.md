# H3-17 — Alvo de reps e última série no pulso

## Status

| Campo | Valor |
|---|---|
| Item do roadmap | `H3-17` |
| MoSCoW | `M` |
| Spec aprovada em | `2026-11-12` |
| Implementação | `concluída` |

## Objetivo

Durante o treino, o relógio mostra **para que serve a série** (alvo de reps do
plano) e **onde você parou** (carga e reps da última série) — sem precisar
tirar o celular do bolso.

## Contexto (estado atual verificado)

- **O protocolo já tem os campos:** `WearWorkoutState` carrega `targetReps`
  (`WearProtocol.kt` L19) e `lastWeightKg`/`lastReps` (L20–21). Nada do
  contrato `:protocol` precisa mudar.
- **O mapper envia o alvo vazio de propósito:** `WearSessionMapper.toWearState`
  grava `targetReps = ""` hardcoded (`WearSessionMapper.kt` L51) — a tela mostra
  `"Alvo: "` sem valor (`WorkoutWearActivity.kt` L152). `lastWeightKg`/`lastReps`
  são mapeados (L52–53) mas a tela não os exibe.
- **Causa de fundo:** `SessionExerciseEntity`
  (`app/.../data/local/Entities.kt` L133–146) **não tem** `targetReps`. O plano
  tem (`WorkoutPlanExerciseEntity.targetReps`, L77), mas `startSession`
  (`WorkoutRepository.kt` L226–239) copia plano → sessão descartando o alvo.
  Não há de onde puxar o texto do plano no momento do `toWearState`.
- **Teste atual documenta o bug:** `WearSessionMapperTest.mapeia_primeiro_exercicio`
  afirma `assertEquals("", estado.targetReps)` — a spec inverte essa expectativa.
- **Ajuste de schema:** o banco já está na v5 com `MIGRATION_4_5`/schema `5.json`
  (H3-42). Esta entrega passa para **v5→v6** (`MIGRATION_5_6`, schema `6.json`).

## Fora de escopo

- Mudança no contrato `:protocol` (os três campos já existem; `protocolVersion`
  continua `1`).
- `targetReps` no sync app↔backend e no backup JSON: o alvo é derivável do
  plano no momento de iniciar a sessão; sessões históricas restauradas do
  backend continuam sem alvo (não afetam a execução, que sempre começa de um
  plano local). Documentado como degradação aceitável.
- Editar carga/reps no relógio (Won't now no roadmap).
- Mudar como o celular calcula/exibe o alvo (`WorkoutScreen` intocado).

## Arquivos e símbolos que devem mudar

| Arquivo | Mudança |
|---|---|
| `app/src/main/java/com/treinos/app/data/local/Entities.kt` | `SessionExerciseEntity` ganha `targetReps: String = ""` com `@ColumnInfo(defaultValue = "")` |
| `app/src/main/java/com/treinos/app/data/local/AppDatabase.kt` | `version = 6` + `MIGRATION_5_6` (`ALTER TABLE session_exercises ADD COLUMN targetReps TEXT NOT NULL DEFAULT ''`), adicionada à lista de migrations |
| `app/schemas/com.treinos.app.data.local.AppDatabase/6.json` | Schema exportado (fonte única versionada) |
| `app/src/main/java/com/treinos/app/data/WorkoutRepository.kt` | `startSession` copia `exercise.targetReps` ao criar `SessionExerciseEntity` |
| `app/src/main/java/com/treinos/app/data/wear/WearSessionMapper.kt` | `targetReps = ex?.targetReps ?: ""` (o mesmo texto do plano) |
| `wear/src/main/java/com/treinos/wear/ui/WorkoutWearActivity.kt` | Linha "Alvo: X" só quando `targetReps` não vazio; nova linha "Última: X kg × Y reps" quando `lastWeightKg`/`lastReps` existirem |
| `wear/src/main/java/com/treinos/wear/ui/WearFormat.kt` (novo) | `formatLastSet(weightKg: Double?, reps: Int?): String?` puro — formata a linha da última série |
| `app/src/test/java/com/treinos/app/data/local/MigrationTest.kt` | Migração 5→6 preservando linhas |
| `app/src/test/java/com/treinos/app/data/wear/WearSessionMapperTest.kt` | Expectativa do alvo invertida + casos novos |
| `wear/src/test/java/com/treinos/wear/ui/WearFormatTest.kt` (novo) | Formatação da última série |

## Modelo de dados / contrato

```text
Room v5 → v6 (aditivo, sem destruição):
  session_exercises:
    + targetReps TEXT NOT NULL DEFAULT ''   -- o mesmo texto do plano ("8-12", "falha", ...)

Fluxo do valor:
  plano (WorkoutPlanExerciseEntity.targetReps)
    → startSession copia para session_exercises.targetReps
    → WearSessionMapper publica em WearWorkoutState.targetReps
    → tela do relógio exibe "Alvo: <targetReps>"

Contrato :protocol: SEM mudança (protocolVersion = 1; campos já existem).
Backup/sync: SEM mudança.
```

## Regras e casos de borda

- Migração 5→6 preserva todas as linhas; sessões criadas antes da atualização
  ficam com `targetReps = ''` → a linha "Alvo" **não aparece** no relógio
  (degradação igual a hoje, não é erro).
- `targetReps` em branco no plano (exercício sintético ou plano antigo) → linha
  "Alvo" omitida; nunca exibir "Alvo: " vazio.
- `lastWeightKg` e `lastReps` nulos (nenhuma série logada) → linha "Última"
  omitida.
- Só um dos dois presente (ex.: reps sem carga, caso do peso corporal) → a
  linha mostra só o que existe: "Última: × 10 reps" / "Última: 55 kg ×".
- Formatação da carga (`formatLastSet`): inteiro sem casa decimal quando
  `weightKg` é inteiro (55 → "55 kg"); um decimal quando não é (52,5 →
  "52,5 kg"); vírgula decimal pt-BR; sem zeros à direita.
- `mapSnapshot` (restore do backend) cria `SessionExerciseEntity` sem alvo —
  campo novo com default `''`, sem mudança de contrato (ver Fora de escopo).
- UX no relógio (Galaxy Watch FE, tela redonda/glance): alvo e última em linhas
  curtas e legíveis, sem empurrar os botões principais para fora da área visível;
  nome do exercício/dia mantém truncamento com `maxLines` + ellipsis. A tela de
  série prioriza leitura rápida e ações de 1 toque.

## Critérios de aceite

1. **Dado** um plano com "Supino reto, 3 × 8-12", **quando** a sessão é iniciada e o estado chega ao relógio, **então** o pulso mostra "Alvo: 8-12".
2. **Dado** uma sessão com última série 55 kg × 10 reps, **quando** o estado chega ao relógio, **então** o pulso mostra "Última: 55 kg × 10 reps".
3. **Dado** o app morto e reaberto (processo novo) com a sessão retomada, **quando** o estado chega ao relógio, **então** o alvo continua aparecendo (persistido em `session_exercises`, não rederivado do plano).
4. **Dado** um app na v5 com sessões existentes, **quando** a migração 5→6 roda, **então** todas as linhas são preservadas e `targetReps` vale `''`.
5. **Dado** um exercício sem `targetReps` no plano, **quando** a sessão roda no pulso, **então** nenhuma linha "Alvo" aparece (nada de "Alvo: " vazio).

## Testes a criar

| Arquivo | Testes |
|---|---|
| `app/src/test/java/com/treinos/app/data/local/MigrationTest.kt` | `migracao_5_6_preserva_linhas` |
| `app/src/test/java/com/treinos/app/data/wear/WearSessionMapperTest.kt` | `mapeia_primeiro_exercicio` com alvo real (substitui expectativa vazia), `alvo_vazio_quando_sessao_sem_alvo`; `inclui_ultima_carga_e_reps` mantido |
| `wear/src/test/java/com/treinos/wear/ui/WearFormatTest.kt` | `ultima_serie_completa`, `ultima_serie_somente_reps`, `ultima_serie_somente_carga`, `carga_inteira_sem_decimais`, `carga_fracionaria_com_virgula`, `sem_nenhuma_ultima_serie_retorna_nulo` |

## Verificação manual

1. Gerar um plano de força, iniciar a sessão com o relógio pareado: o pulso
   mostra "Alvo: 8-12" (ou o alvo do plano) e "Série 1/3".
2. Logar uma série no celular: o pulso passa a mostrar "Última: <carga> kg ×
   <reps>" e "Série 2/3".
3. Matar o app do celular, reabrir, retomar o treino: o alvo continua no pulso.
4. Caminho de erro: iniciar sessão de um plano antigo (criado antes da
   atualização) → nenhuma linha "Alvo" aparece, sem crash.

## Comando de verificação

```bash
./scripts/verify.sh
```

## Riscos e rollback

- Migração Room é o maior risco: coluna aditiva com default vazio, teste de
  migração no gate, sem `fallbackToDestructiveMigration` (padrão H1-6/H3-15/
  H3-24/H3-42). Rollback exige voltar `version`/`MIGRATION_5_6`/schema `6.json`
  e remover o campo no mesmo commit.
- O texto do alvo é livre (vem do plano/IA): a tela trata como texto opaco,
  sem parse — nenhum formato novo pode quebrar o relógio.
- Degradação conhecida: sessões restauradas do backend ficam sem alvo até a
  próxima sessão iniciada do plano (aceito nesta spec; sync é item separado).
