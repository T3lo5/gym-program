# H3-46 — Feedback háptico nas ações de treino

## Status

| Campo | Valor |
|---|---|
| Item do roadmap | `H3-46` |
| MoSCoW | `S` |
| Spec aprovada em | `2026-11-13` |
| Implementação | `concluída` |

**Origem:** pedido de criar a spec de háptico, a partir das lacunas observadas
na auditoria UX/UI de 2026-11-07
([relatório](../auditoria-ux-ui-2026-11-07.md), §1.7) e do uso real em treino.

**Relacionado:** [H3-26](H3-26-loop-descanso.md) cobre o alerta de fim de
descanso (vibração/beep por ringer, regra pura em `Feedback.kt`), `keepScreenOn`
e o deadline/wall-clock do descanso. Esta spec **não** redefine o alerta de fim
de descanso — ela complementa o H3-26 com hápticos de interação nas ações que o
usuário executa durante a sessão.

## Objetivo

Durante o treino, as ações importantes (registrar/editar série, controlar o
descanso, avançar/finalizar/abandonar) devolvem um feedback tátil curto e
consistente, para que o usuário confirme o gesto sem precisar olhar a tela
entre séries.

## Fora de escopo

- Vibração/beep ao **zerar** o descanso e regra por modo de toque/silencioso —
  escopo da [H3-26](H3-26-loop-descanso.md) (não duplicar `restFeedbackForRinger`
  nem o player de fim de descanso).
- Hápticos no relógio (Wear OS) — depende do MVP/UX do pulso (H3-17–H3-20).
- Sons de UI (além do que H3-26 já prevê para o descanso).
- Preferência do usuário para ligar/desligar háptico (pode ser item futuro).
- Qualquer mudança de lógica de negócio, persistência, Room, backend ou contrato
  de rede.

## Arquivos e símbolos que devem mudar

| Arquivo | Mudança |
|---|---|
| `app/src/main/java/com/treinos/app/ui/Feedback.kt` | Centralizar helpers puros/de disparo de háptico de UI (ex.: tipos de evento → `HapticFeedbackType`/intensidade), reutilizáveis pelas telas; sem conflitar com `RestFeedbackPlayer`/regra de ringer do H3-26. |
| `app/src/main/java/com/treinos/app/ui/screens/WorkoutScreen.kt` | Aplicar háptico consistente em: salvar série (novo), salvar edição de série, excluir série (após confirmação), repetir última série, controles de descanso (+15 s, pausar/retomar, pular), avançar ao concluir exercício, confirmar finalizar treino e confirmar abandonar treino. Remover chamadas ad-hoc duplicadas se houver. |
| `app/src/main/java/com/treinos/app/ui/screens/RunWorkoutScreen.kt` | Háptico ao confirmar salvar/finalizar corrida (ação concluída) e ao confirmar descarte/cancelamento, quando aplicável. |
| `app/src/main/java/com/treinos/app/ui/screens/MobilityScreen.kt` | Háptico em controles do player (pausar/retomar, avançar/voltar movimento, pular transição, finalizar/descartar) onde o toque representa uma ação concluída. |
| `app/src/test/java/com/treinos/app/ui/FeedbackRuleTest.kt` (ou `HapticFeedbackTest.kt`) | Testes da regra pura de mapeamento evento→feedback (sem depender de dispositivo). |
| `app/src/test/java/com/treinos/app/ui/screens/WorkoutScreenTest.kt` (Robolectric) | Cobertura dos pontos de disparo nas ações críticas do treino, conforme viável no ambiente de teste. |

> Caminhos exatos a confirmar na implementação; se um controle citado não existir
> na tela atual, ele fica fora (não inventar UI).

## Modelo de dados / contrato

Sem mudança de banco, preferências, rede ou contrato. O mapeamento de eventos de
háptico é **puro e local** (ex.: enum/sealed `HapticEvent` →
`HapticFeedbackType` ou padrão de vibração leve via API de composição), exposto
de forma testável:

```text
HapticEvent.SetSaved        -> confirmação leve/média (sucesso de registro)
HapticEvent.SetEdited       -> confirmação leve
HapticEvent.SetDeleted      -> confirmação leve (ação destrutiva já confirmada)
HapticEvent.RepeatLastSet   -> confirmação (igual a salvar)
HapticEvent.RestAdjusted    -> toque leve (+15 s / pausar / retomar / pular)
HapticEvent.ExerciseAdvanced-> confirmação ao avançar exercício concluído
HapticEvent.WorkoutFinished -> confirmação ao concluir treino
HapticEvent.WorkoutAbandoned-> confirmação ao abandonar (após diálogo)
```

Valores exatos (tipo Compose) definidos na implementação, desde que consistentes
e documentados nos testes.

## Regras e casos de borda

- **Consistência:** a mesma classe de ação usa o mesmo padrão tátil em toda a
  tela de treino (ex.: toda confirmação de "registro salvo" = mesmo evento).
- **Momento do disparo:** o háptico ocorre no ponto em que a ação é efetivamente
  confirmada (após validação passar e/ou após o usuário confirmar um diálogo
  destrutivo), nunca em toque que falha por validação.
- **Validação com erro:** campo inválido no diálogo de série (carga/reps/RPE fora
  da regra da H3-27) **não** dispara háptico de sucesso.
- **Idempotência percebida:** toque rápido repetido não deve produzir rajadas
  longas/bloqueantes; usar o mecanismo nativo de háptico do Compose (não loops).
- **Respeito ao sistema:** usar `LocalHapticFeedback`/APIs de composição; se o
  sistema não entregar háptico, o app segue sem erro (falha silenciosa, sem
  crash).
- **Sem duplicação com H3-26:** fim de descanso (`remaining == 0`) continua
  responsabilidade do alerta da H3-26; controles de descanso durante a contagem
  usam o evento `RestAdjusted` desta spec.
- **Acessibilidade:** háptico é reforço, nunca o único canal — rótulos/estado
  visual e semântica existentes permanecem obrigatórios (H3-3/H3-34).

## Critérios de aceite

1. **Dado** uma série válida salva no diálogo, **quando** o salvamento é
   confirmado, **então** ocorre um feedback háptico curto (e não ocorre se a
   validação falhar).
2. **Dado** o botão "Repetir última série", **quando** a série é registrada em
   1 toque, **então** ocorre o mesmo feedback de confirmação de série salva.
3. **Dado** descanso ativo, **quando** o usuário toca em +15 s, pausar/retomar
   ou pular, **então** cada ação confirmada produz um feedback tátil leve.
4. **Dado** exercício com séries planejadas concluídas, **quando** o usuário
   avança para o próximo ("Exercício concluído — próximo"), **então** ocorre
   feedback de confirmação de avanço.
5. **Dado** finalizar ou abandonar treino, **quando** o usuário confirma no
   diálogo, **então** ocorre feedback háptico; cancelar o diálogo não dispara.
6. **Dado** edição de uma série existente salva com sucesso, **quando** o
   diálogo fecha, **então** ocorre feedback de confirmação; excluir série só
   vibra após a confirmação da exclusão.
7. **Dado** aparelho/ambiente sem suporte a háptico, **quando** qualquer ação
   acima ocorre, **então** o app não trava nem lança exceção.

## Testes a criar

| Arquivo | Testes |
|---|---|
| `app/src/test/java/com/treinos/app/ui/HapticFeedbackTest.kt` (ou `FeedbackRuleTest.kt`) | `evento_salvar_serie_mapeia_confirmacao`, `evento_controle_descanso_mapeia_toque_leve`, `evento_avanco_e_fim_treinam_confirmacao`, `mapa_cobre_eventos_de_treino_sem_nulos` |
| `app/src/test/java/com/treinos/app/ui/screens/WorkoutScreenTest.kt` (Robolectric) | `salvar_serie_valida_dispara_haptico`, `serie_invalida_nao_dispara_haptico_sucesso`, `repetir_ultima_serie_dispara_haptico`, `cancelar_finalizar_nao_dispara_haptico` (conforme instrumentação viável no fake de `HapticFeedback`) |

## Verificação manual

1. Treino real (aparelho físico): registrar série, repetir última, editar e
   excluir → sentir confirmação tátil apenas quando a ação conclui.
2. Durante descanso: tocar +15 s, pausar, retomar, pular → toque leve em cada
   controle.
3. Concluir exercício e avançar; finalizar treino e abandonar (confirmar e
   cancelar) → háptico só na confirmação.
4. Digitar carga inválida no diálogo de série → salvar rejeitado sem háptico de
   sucesso.

## Comando de verificação

```bash
./scripts/verify.sh
```

## Riscos e rollback

- **Excesso de vibração** cansa no uso em academia: limitar a eventos de ação
  concluída/controle explícito (não vibrar em scroll, foco ou seleção passiva).
- **Interação com H3-26:** manter o alerta de fim de descanso no
  `RestFeedbackPlayer`/regras de ringer da H3-26; esta spec usa apenas háptico
  de UI de composição. Se H3-26 evoluir, revisar que não há dois disparos no
  mesmo instante.
- **Testabilidade:** `LocalHapticFeedback` em Compose pode exigir fake no teste;
  priorizar a regra pura de mapeamento em JVM e cobrir disparos críticos em
  Robolectric onde houver infraestrutura.
- **Rollback:** reverter para chamadas diretas anteriores (ou remover os novos
  pontos) sem impacto em dados; helpers novos ficam isolados em `Feedback.kt`.
