# H2-5 — Registro avulso genérico (natação primeiro)

## Status

| Campo | Valor |
|---|---|
| Item do roadmap | `H2-5` |
| MoSCoW | `S` |
| Spec aprovada em | `2026-11-04` |
| Implementação | `pendente` |
| Depende de | `H1-6` (campos `modality`, `source`, `title`, `totalDistanceMeters`, `poolLengthMeters`, `stroke`, `sessionRpe` e backup/sync v2) e `H1-7` (round-trip de backup/restauração confiável) |

## Objetivo

Permitir que o usuário registre uma atividade que **não veio de um plano** —
natação passada pelo professor, aula de ginástica, corrida avulsa — com resumo
e notas, sem criar um segundo histórico, um segundo backup ou um segundo modelo
de dados.

## Fora de escopo

- Gerar plano de natação, catálogo de natação ou validação de pace de natação.
- Blocos estruturados (`8 x 100 m`), splits por raia ou cronômetro de piscina.
- GPS, frequência cardíaca ao vivo, integração com relógio ou Strava.
- Fotos/anexos do quadro da academia.
- Plano híbrido com dias por modalidade (isso é H2-6).
- Editar ou apagar sessões que vieram de um plano (`source = "plan"`).
- IA adaptando o treino a partir dos registros (H2-6 consome esses dados).

## Arquivos e símbolos que devem mudar

| Arquivo | Mudança |
|---|---|
| `app/src/main/java/com/treinos/app/data/local/WorkoutDao.kt` | `deleteSession(id)`; leitura continua via `observeSessionEntities()` |
| `app/src/main/java/com/treinos/app/data/WorkoutRepository.kt` | `logActivity(...)`, `updateActivity(...)`, `deleteActivity(...)` |
| `app/src/main/java/com/treinos/app/ui/ActivityLog.kt` | **Novo.** `ActivityLogInput`, `ActivityLogValidation`, `validateActivityLog(...)` e cálculo de pace (lógica pura) |
| `app/src/main/java/com/treinos/app/ui/AppViewModel.kt` | Estado do formulário, ações de abrir/salvar/editar/excluir, `Screen.LogActivity` |
| `app/src/main/java/com/treinos/app/ui/TreinosApp.kt` | Rota da nova tela e passagem de callbacks |
| `app/src/main/java/com/treinos/app/ui/screens/LogActivityScreen.kt` | **Novo.** Formulário de registro/edição |
| `app/src/main/java/com/treinos/app/ui/screens/HomeScreen.kt` | Botão “Registrar atividade” |
| `app/src/main/java/com/treinos/app/ui/screens/HistoryScreen.kt` | Card para sessão manual (sem exercícios) e filtro por modalidade |
| `app/src/main/java/com/treinos/app/ui/Formatters.kt` | `formatDistanceMeters`, `formatPacePer100m` (se ainda não existirem) |
| `app/src/test/java/com/treinos/app/ui/ActivityLogTest.kt` | **Novo.** Validação e pace |
| `app/src/test/java/com/treinos/app/ui/ActivityFormatTest.kt` | **Novo.** Formatação de distância/pace |
| `app/src/test/java/com/treinos/app/data/local/ManualSessionDaoTest.kt` | **Novo.** Room em memória/Robolectric para sessão avulsa |

Não edite `shared/` nem `com.treinos.shared/` (legado). Não altere o backend
além do que H1-6 já cobre.

## Modelo de dados / contrato

### Entrada pura (UI)

```kotlin
enum class ActivityModality(val storageValue: String, val label: String) {
    STRENGTH("strength", "Musculação"),
    RUN("run", "Corrida"),
    SWIM("swim", "Natação"),
    MOBILITY("mobility", "Mobilidade"),
    OTHER("other", "Outra"),
}

data class ActivityLogInput(
    val modality: ActivityModality = ActivityModality.SWIM,
    val title: String = "",
    val startedAt: Long = System.currentTimeMillis(),
    val durationMinutes: Int = 45,
    val distanceMeters: Double? = null,
    val poolLengthMeters: Int? = null,   // 25, 50 ou null
    val stroke: String? = null,          // crawl, costas, peito, borboleta, misto, outro
    val rpe: Double? = null,             // 1.0 .. 10.0
    val notes: String = "",
)
```

### Sessão gravada

`WorkoutSessionEntity` com:

| Campo | Valor |
|---|---|
| `planId`, `dayId` | `0` |
| `planName` | `""` |
| `dayName` | `title` (para compatibilidade com telas antigas) |
| `title` | título informado ou default da modalidade |
| `modality` | `input.modality.storageValue` |
| `source` | `"manual"` |
| `status` | `"completed"` |
| `startedAt` | `input.startedAt` |
| `endedAt` | `startedAt + durationMinutes * 60_000` |
| `totalDurationSeconds` | `durationMinutes * 60L` |
| `totalDistanceMeters` | `input.distanceMeters` |
| `poolLengthMeters` | `input.poolLengthMeters` |
| `stroke` | `input.stroke` |
| `sessionRpe` | `input.rpe` |
| `notes` | `input.notes` |

**Não** criar `SessionExerciseEntity` nem `SetLogEntity` para o registro
avulso. A sessão manual tem zero exercícios por definição.

### Pace

Para natação e corrida, quando houver distância > 0 e duração > 0:

```text
pacePor100m = totalDurationSeconds / (distanceMeters / 100.0)
```

Exibir `mm:ss/100m` para natação. Para corrida, exibir `mm:ss/km`
(`totalDurationSeconds / (distanceMeters / 1000.0)`). Arredondar para o segundo
mais próximo. Sem distância, não mostrar pace.

## Regras e casos de borda

1. Modalidade é obrigatória; ao abrir, o default é `SWIM`.
2. Título vazio usa o label da modalidade (`Natação`, `Corrida`, `Musculação`,
   `Mobilidade`, `Outra`). Em `OTHER`, título vazio vira `Atividade`.
3. Duração válida: `1..600` minutos. `0`, negativa ou acima de 600 → erro inline.
4. Distância é opcional: vazia → `null`; `0` → `null`; faixa aceita
   `0..100000` m. Distância negativa → erro.
5. RPE é opcional: vazio → `null`; faixa `1.0..10.0`; aceita passo de `0.5`.
6. Data/hora não pode estar mais de 5 minutos no futuro; datas retroativas são
   permitidas (registrar o treino de ontem).
7. Notas têm limite de 2000 caracteres; acima disso → erro inline.
8. `poolLengthMeters` só aceita `25`, `50` ou `null`; só se aplica a `SWIM`.
9. `stroke` só se aplica a `SWIM`; valores aceitos: `crawl`, `costas`, `peito`,
   `borboleta`, `misto`, `outro`.
10. “Salvar” fica desabilitado enquanto a gravação corre; toque duplo não cria
    duas sessões.
11. Editar mantém o mesmo `id` da sessão e atualiza os campos; não cria linha
    nova.
12. Excluir só é permitido para `source = "manual"`; exige confirmação e remove
    a sessão (sem exercícios, o cascade não é necessário, mas `deleteSession`
    deve funcionar com zero filhos).
13. Sessão manual nunca aparece em “Treino em andamento”: ela nasce
    `completed` e `in_progress` continua filtrando `status == "in_progress"`.
14. Histórico com sessão manual não pode calcular “0 séries” nem tentar expandir
    exercícios; renderiza um card de atividade.
15. Sessão manual com `planId = 0` e `dayId = 0` sobrevive a backup, sync e
    download (coberto por H1-6, mas precisa de teste de integração aqui).
16. “Copiar último treino” preenche o formulário com a última sessão manual da
    mesma modalidade; se não houver, o botão não aparece.
17. App reiniciado no meio do formulário perde o rascunho (fora de escopo).
18. Fuso horário: persistir epoch millis; exibir no fuso local.

## Critérios de aceite

1. **Dado** que o usuário toca em “Registrar atividade”, escolhe Natação, 45 min,
   1.500 m e salva, **então** a sessão aparece no histórico como
   “Natação • 45 min • 1.500 m” e o pace é `3:00/100m`.
2. **Dado** um registro só com duração, **quando** salva, **então** é aceito e
   não mostra pace.
3. **Dado** duração 0, **quando** tenta salvar, **então** vê erro inline e nada
   é gravado.
4. **Dado** RPE 11, **quando** tenta salvar, **então** vê erro inline.
5. **Dado** data de amanhã, **quando** tenta salvar, **então** vê erro; data de
   ontem é aceita.
6. **Dado** uma sessão manual salva, **quando** edita a distância, **então** o
   mesmo card é atualizado, sem duplicar.
7. **Dado** uma sessão manual, **quando** exclui e confirma, **então** ela some
   do histórico e do backup seguinte.
8. **Dado** que o app é reiniciado, **quando** abre o histórico, **então** a
   sessão manual continua lá, offline.
9. **Dado** que o usuário exporta backup, desinstala, reinstala e importa,
   **então** a sessão manual volta com modalidade, distância, piscina, stroke,
   RPE e notas.
10. **Dado** que o usuário sincroniza e restaura do backend, **então** a sessão
    manual volta com `planId = 0` e `dayId = 0`.

## Testes a criar

| Arquivo | Testes |
|---|---|
| `app/src/test/java/com/treinos/app/ui/ActivityLogTest.kt` | `aceita_so_duracao`, `calcula_pace_por_100m`, `nao_calcula_pace_sem_distancia`, `rejeita_duracao_zero`, `rejeita_duracao_acima_de_600`, `rejeita_rpe_fora_da_faixa`, `rejeita_data_futura`, `aceita_data_retroativa`, `titulo_vazio_usa_default`, `outra_modalidade_usa_atividade`, `rejeita_notas_longas`, `rejeita_piscina_invalida`, `rejeita_stroke_fora_da_lista` |
| `app/src/test/java/com/treinos/app/ui/ActivityFormatTest.kt` | `formata_metros`, `formata_pace_100m`, `formata_pace_km`, `formata_duracao_minutos` |
| `app/src/test/java/com/treinos/app/data/local/ManualSessionDaoTest.kt` | `insere_sessao_manual_sem_exercicios`, `le_sessao_manual`, `atualiza_sessao_manual`, `remove_sessao_manual` |
| `app/src/test/java/com/treinos/app/data/backup/BackupMapperTest.kt` | `sessao_manual_round_trip` (complementa H1-6) |
| `back/tests/test_sync_contract.py` | `test_sessao_manual_round_trip` (complementa H1-6) |

`ManualSessionDaoTest` usa Room em memória com Robolectric; não precisa de
device.

## Verificação manual

1. Abra “Registrar atividade” e confira o default Natação.
2. Salve 45 min, 1.500 m, piscina 25 m, crawl, RPE 7, notas “8 x 100 m”.
3. Confira o card no histórico e o pace `3:00/100m`.
4. Edite a distância para 1.200 m e confirme que o pace muda e não duplica.
5. Salve um registro só com duração e confirme que não há pace.
6. Tente data futura, RPE 11 e duração 0; confirme os erros.
7. Exporte backup, desinstale, reinstale e importe; confira a sessão.
8. Sincronize, apague os dados e restaure do backend; confira a sessão.
9. Exclua a sessão e confirme que some do histórico e do backup seguinte.

## Comando de verificação

```bash
./scripts/verify.sh
```

## Riscos e rollback

- **Histórico assumir exercícios:** o card manual precisa ser um caminho
  separado; teste com zero exercícios.
- **Sessão sem plano no sync:** `planId = 0` já é default, mas o round-trip
  precisa de teste para não virar órfã.
- **Exclusão acidental:** confirmação obrigatória; não excluir sessões de plano.
- **Scope creep para blocos/splits:** o v1 é resumo + notas. Blocos ficam para
  uma spec futura, se o uso real pedir.
- **Rollback:** remover a tela e as ações; as sessões manuais já gravadas
  continuam válidas como sessões com `source = "manual"` e zero exercícios.
