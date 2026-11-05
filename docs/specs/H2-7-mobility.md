# H2-7 — Mobilidade e alongamento

## Status

| Campo | Valor |
|---|---|
| Item do roadmap | `H2-7` |
| MoSCoW | `C` |
| Spec aprovada em | `2026-11-05` |
| Implementação | `concluída` (`c2830cd feat: H2-7 mobilidade e alongamento`) |
| Depende de | H1-6 (campos `modality`/`metricType`/`targetDurationSeconds`) |

## Objetivo

Oferecer rotinas curtas de mobilidade e alongamento — por exemplo, 10 a 15
minutos para costas e quadril — com timer por movimento, para o usuário usar
como recuperação ou complemento do treino, sem prometer tratamento médico.

## Fora de escopo

- Diagnóstico, tratamento ou reabilitação de dor.
- Prescrição para lesão específica ou “curar dor nas costas”.
- Fisioterapia, quiropraxia, RPG ou acompanhamento profissional.
- Alongamento com carga, PNF ou técnicas que exijam supervisão.
- Vídeos/animações; o v1 usa nome, texto e timer.
- Wear OS.

## Arquivos e símbolos que devem mudar

| Arquivo | Mudança |
|---|---|
| `app/src/main/java/com/treinos/app/ui/MobilityRoutine.kt` | **Novo.** Templates locais e lógica pura de duração/ordem |
| `app/src/main/java/com/treinos/app/ui/screens/MobilityScreen.kt` | **Novo.** Lista de rotinas e player com timer |
| `app/src/main/java/com/treinos/app/ui/AppViewModel.kt` | Iniciar/avançar/pausar/finalizar rotina |
| `app/src/main/java/com/treinos/app/ui/TreinosApp.kt` | Rota da tela |
| `app/src/main/java/com/treinos/app/ui/screens/HomeScreen.kt` | Atalho “Mobilidade” |
| `app/src/main/java/com/treinos/app/data/WorkoutRepository.kt` | Criar sessão de mobilidade (`modality = "mobility"`) |
| `app/src/test/java/com/treinos/app/ui/MobilityRoutineTest.kt` | **Novo.** Templates e duração |
| `app/src/test/java/com/treinos/app/ui/MobilitySessionTest.kt` | **Novo.** Sessão gerada |

Não edite `shared/` nem `com.treinos.shared/` (legado). Não altere o backend:
mobilidade é local e offline.

## Modelo de dados / contrato

```kotlin
data class MobilityRoutine(
    val id: String,
    val name: String,
    val focus: String,          // "back", "hips", "full_body", "post_run"
    val description: String,
    val movements: List<MobilityMovement>,
)

data class MobilityMovement(
    val catalogId: String?,     // id do catálogo de alongamento, quando existir
    val name: String,
    val durationSeconds: Int,   // 20..60
    val side: String?,          // "left" / "right" / null
    val cue: String,            // instrução curta em PT-BR
)
```

Rotinas iniciais (curadas, não geradas por IA):

| ID | Nome | Foco | Duração alvo |
|---|---|---|---|
| `back_10` | Costas e quadril — 10 min | `back` | 10 min |
| `hips_10` | Quadril e posterior — 10 min | `hips` | 10 min |
| `full_15` | Corpo inteiro — 15 min | `full_body` | 15 min |
| `post_run_8` | Pós-corrida — 8 min | `post_run` | 8 min |

Regras:

- Cada rotina tem 6 a 15 movimentos; cada movimento dura 20 a 60 s.
- A duração exibida é a soma dos movimentos + transições de 5 s; o total fica
  entre 5 e 15 min.
- O player mostra nome, instrução, tempo restante, próximo movimento e
  pausar/retomar/avançar/voltar.
- Ao finalizar, cria uma `WorkoutSessionEntity` com `modality = "mobility"`,
  `source = "manual"`, `status = "completed"`, `title` = nome da rotina,
  `totalDurationSeconds` = tempo efetivamente decorrido e `notes` = foco.
- Não cria `SetLogEntity` por movimento no v1; o resumo fica na sessão.
- O histórico mostra a rotina como atividade, sem “0 séries”.
- Nenhuma rotina usa equipamento além de colchonete/foam roll; se usar, o
  template deve dizer.

## Regras e casos de borda

1. Rotina com 0 movimentos → não aparece na lista.
2. Movimento com duração fora de 20–60 s → template inválido; teste falha.
3. Soma dos movimentos + transições difere da duração alvo em mais de 60 s →
   template inválido.
4. Pausar e sair → a sessão não é gravada até finalizar; o estado do player é
   local e pode ser perdido (fora de escopo persistir).
5. Avançar além do último movimento → finaliza a rotina.
6. Voltar no primeiro movimento → permanece no primeiro.
7. Finalizar antes do fim → grava o tempo decorrido e marca `completed`.
8. Rotina iniciada duas vezes → duas sessões separadas; não bloquear.
9. Nenhuma promessa de cura: a tela deve exibir o aviso “não substitui
   avaliação profissional” e os sinais de alerta.
10. Sinais de alerta (dor irradiada, formigamento, perda de força, trauma,
    febre) → orientar a procurar profissional e não continuar a rotina.
11. Catálogo em inglês → o template usa nome PT-BR curado e `catalogId` apenas
    para eventual mídia.
12. Sem backend/offline → todas as rotinas funcionam.

## Critérios de aceite

1. **Dado** que o usuário abre “Mobilidade”, **então** vê as 4 rotinas com foco e
   duração.
2. **Dado** que inicia “Costas e quadril — 10 min”, **então** vê o primeiro
   movimento, a instrução e o timer.
3. **Dado** que o timer termina, **então** avança automaticamente para o próximo.
4. **Dado** que pausa e retoma, **então** o tempo continua de onde parou.
5. **Dado** que finaliza, **então** a sessão aparece no histórico como
   “Mobilidade • 10 min”, sem séries.
6. **Dado** que o app está offline, **então** a rotina funciona normalmente.
7. **Dado** que o usuário relata dor irradiada, **então** vê o aviso para procurar
   profissional e não recebe diagnóstico.

## Testes a criar

| Arquivo | Testes |
|---|---|
| `app/src/test/java/com/treinos/app/ui/MobilityRoutineTest.kt` | `templates_tem_movimentos_validos`, `duracao_soma_movimentos_e_transicoes`, `nao_ha_movimento_duplicado_na_mesma_rotina`, `foco_back_tem_movimentos_de_costas`, `rotina_vazia_e_rejeitada` |
| `app/src/test/java/com/treinos/app/ui/MobilitySessionTest.kt` | `sessao_usa_modality_mobility`, `sessao_nao_cria_series`, `finalizar_antes_do_fim_grava_tempo_decorrido` |

## Verificação manual

1. Abra “Mobilidade” e confira as 4 rotinas.
2. Inicie “Costas e quadril — 10 min” e deixe o timer correr.
3. Pause, retome, avance e volte.
4. Finalize antes do fim e confira o histórico.
5. Ative o modo avião e repita.
6. Confira o aviso de segurança e os sinais de alerta.

## Comando de verificação

```bash
./scripts/verify.sh
```

## Riscos e rollback

- **Promessa médica:** o maior risco. A UI deve dizer que é bem-estar/recuperação,
  não tratamento; nunca prometer “melhorar dor nas costas”.
- **Catálogo em inglês:** usar nomes curados em PT-BR; não despejar os 123 itens
  do catálogo na UI.
- **Rotina longa demais:** 10–15 min é o alvo; 30–40 min vira treino e foge do
  caso de uso.
- **Rollback:** remover a tela e as rotinas; sessões de mobilidade já gravadas
  continuam no histórico como atividades.
