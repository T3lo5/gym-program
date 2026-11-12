# H3-19 — Corrida e mobilidade no relógio

## Status

| Campo | Valor |
|---|---|
| Item do roadmap | `H3-19` |
| MoSCoW | `M` |
| Spec aprovada em | `2026-11-12` |
| Implementação | `concluída em 2026-11-12` |

## Objetivo

Treinar corrida ou mobilidade com o celular no bolso não vira tela quebrada no
pulso: o relógio mostra **o que está em andamento** e permite finalizar — cada
modalidade com uma tela coerente com o que a sessão é.

## Contexto (estado atual verificado)

- **Com a H3-11, corrida/mobilidade são sessões sem exercícios** (execução e
  aba próprios no celular). O `WearSessionMapper` com lista vazia produz
  `exerciseName = ""`, `setNumber = 1`, `plannedSets = 0` (`WearSessionMapper.kt`
  L35–48) — o próprio teste `ignora_sessao_sem_exercicios` documenta o estado.
- **A tela do pulso quebra nesses treinos:** `WorkoutWearScreen` renderiza
  `"Série 1 / 0"`, `"Alvo: "` e nome em branco (`WorkoutWearActivity.kt`
  L142–154) — não há distinção de tipo de sessão.
- **O celular já publica esses estados:** o `combine(inProgressSession, ...)`
  (`AppViewModel.kt` L399–409) publica qualquer sessão em andamento, incluindo
  corrida/mobilidade.
- **A modalidade já existe no banco:** `WorkoutSessionEntity.modality`
  (`Entities.kt` L110, default `"strength"`), copiada de `day.modality` em
  `startSession` (`WorkoutRepository.kt` L219). O que falta é **viajar no
  protocolo**.
- **Comandos já são seguros nesses treinos:** `LOG_SET` em sessão sem
  exercícios é no-op (`AppViewModel.kt` L425–427, `exercicio == null`);
  `FINISH_WORKOUT` finaliza normalmente.

## Fora de escopo

- Métricas de corrida no pulso (GPS/ritmo/FC) — Won't now no roadmap; exigiria
  as permissões de sensor que a H2-2 explicitamente não pede.
- Duração decorrida na tela do pulso (exigiria campo `startedAt` no protocolo;
  fica para um item futuro se o uso real pedir).
- Passos da rotina de mobilidade no pulso (o celular já tem `MobilityScreen`
  com timer próprio).
- Modo standalone (treinar sem o celular) — Won't now.
- Mudar `SessionKind`/telas do celular.

## Arquivos e símbolos que devem mudar

| Arquivo | Mudança |
|---|---|
| `protocol/src/main/kotlin/com/treinos/protocol/WearProtocol.kt` | `WearWorkoutState` ganha `@SerialName("modality") val modality: String = "strength"` (campo aditivo com default) |
| `app/src/main/java/com/treinos/app/data/wear/WearSessionMapper.kt` | Publica `modality = session.session.modality` |
| `wear/src/main/java/com/treinos/wear/ui/WearSessionKind.kt` (novo) | Enum `WearSessionKind` + `from(modality: String)`: `run`/`mobility` reconhecidos; **qualquer outro valor (incluindo desconhecido) → `Strength`** |
| `wear/src/main/java/com/treinos/wear/ui/WorkoutWearActivity.kt` | `WorkoutWearScreen` ramifica por `WearSessionKind`: força mantém o layout atual; corrida/mobilidade mostram nome do dia, "Em andamento" e apenas "Finalizar Treino" |
| `protocol/src/test/kotlin/com/treinos/protocol/WearProtocolTest.kt` | Round-trip com `modality` + payload antigo sem o campo decodificando com default |
| `app/src/test/java/com/treinos/app/data/wear/WearSessionMapperTest.kt` | `modality` propagada; sessão de corrida sem exercícios produz estado coerente |
| `wear/src/test/java/com/treinos/wear/ui/WearSessionKindTest.kt` (novo) | Mapeamento determinístico da modalidade |

## Modelo de dados / contrato

```text
:protocol v1 (mantido) — campo aditivo:
  WearWorkoutState:
    + modality String = "strength"   -- "strength" | "run" | "mobility" (texto do Room)

Compatibilidade (ambos os lados decodificam com ignoreUnknownKeys):
  celular novo → relógio antigo : campo desconhecido é ignorado (comportamento atual)
  celular antigo → relógio novo : default "strength" → layout de força (igual a hoje)
  Os dois APKs saem juntos no gate; a janela de mistura é só entre installs.

Tela do pulso (determinística):
  Strength  → layout atual (séries, alvo, descanso, 3 botões)
  Run/Mobility → dayName + "Em andamento" + botão "Finalizar Treino"
             (sem séries, alvo, descanso, "Concluir Série" ou "Pular Descanso")
  finished/null → "Treino Concluído!" (inalterado)
```

## Regras e casos de borda

- Modalidade desconhecida ou vazia → `WearSessionKind.Strength` (layout atual;
  nunca crash com valor futuro tipo `"swimming"`).
- Sessão de força que por qualquer motivo chega sem exercícios → continua o
  layout de força com "Série 1/0" (estado atual mantido; a H3-19 não tenta
  adivinar tipo por `exerciseCount` — a modalidade é a fonte).
- `LOG_SET` não existe na tela de corrida/mobilidade; mesmo se chegasse comando
  de série de um relógio antigo, o celular já ignora (`exercicio == null`).
- `FINISH_WORKOUT` em corrida/mobilidade finaliza a sessão pelo fluxo existente
  do celular (`finishWorkout`), que salva o histórico da modalidade.
- Estado nulo ou `status = "finished"` → tela de conclusão, antes da ramificação
  por modalidade.
- O texto exibido em corrida/mobilidade é `dayName` (ex.: "Corrida leve") — o
  mesmo título que o celular mostra na aba da sessão.

## Critérios de aceite

1. **Dado** uma sessão de corrida em andamento, **quando** o estado chega ao pulso, **então** o relógio mostra o nome do dia, "Em andamento" e o botão "Finalizar Treino" — sem "Série 1/0", "Alvo: " ou nome em branco.
2. **Dado** uma sessão de mobilidade em andamento, **quando** o estado chega ao pulso, **então** o mesmo layout coerente de corrida/mobilidade aparece.
3. **Dado** uma sessão de força em andamento, **quando** o estado chega ao pulso, **então** o layout de força é exatamente o de hoje (nenhuma regressão visual).
4. **Dado** um payload do celular sem o campo `modality` (APK antigo), **quando** o relógio novo decodifica, **então** `modality` vale `"strength"` e a tela não quebra.
5. **Dado** um payload com `modality` desconhecida (ex.: `"swimming"`), **quando** o relógio decodifica, **então** a sessão é tratada como força (layout atual), sem crash.
6. **Dado** uma corrida em andamento no pulso, **quando** o usuário toca "Finalizar Treino", **então** a sessão é finalizada no celular e o pulso passa a mostrar "Treino Concluído!".

## Testes a criar

| Arquivo | Testes |
|---|---|
| `protocol/src/test/kotlin/com/treinos/protocol/WearProtocolTest.kt` | `modality_vai_no_round_trip`, `payload_sem_modality_decodifica_com_default` |
| `app/src/test/java/com/treinos/app/data/wear/WearSessionMapperTest.kt` | `modality_da_sessao_vai_no_estado`, `sessao_de_corrida_sem_exercicios_mapeia_coerente` |
| `wear/src/test/java/com/treinos/wear/ui/WearSessionKindTest.kt` | `strength_por_default`, `run_reconhecida`, `mobility_reconhecida`, `desconhecida_vira_strength`, `vazia_vira_strength` |

## Verificação manual

1. Iniciar uma corrida pelo celular com o relógio pareado: o pulso mostra o
   nome da sessão, "Em andamento" e "Finalizar Treino".
2. Iniciar uma sessão de mobilidade: mesmo layout coerente.
3. Finalizar a corrida pelo pulso: a sessão termina no celular (histórico com
   `modality = "run"`) e o pulso mostra "Treino Concluído!".
4. Iniciar um treino de força: o pulso mantém séries/alvo/descanso como hoje.
5. Caminho de erro: gerar um payload com modalidade inventada (ou instalar o
   celular antigo) → o relógio não quebra (layout de força ou default).

## Comando de verificação

```bash
./scripts/verify.sh
```

## Riscos e rollback

- Único item do MVP do relógio que toca o contrato `:protocol` — campo
  **aditivo com default**, o padrão que o roadmap declara seguro
  (`ignoreUnknownKeys` nos dois lados; `WearProtocolTest` já cobre campo
  desconhecido e versão). `protocolVersion` continua `1`, então o guard de
  versão do `WearSyncService` (L62, L81) não rejeita.
- Risco de mistura de APKs (celular novo + relógio velho) degrada para o
  comportamento atual, não para algo pior.
- Rollback: reverter o campo no protocolo + mapper + tela no mesmo commit;
  nenhum schema, backup ou sync é tocado.
