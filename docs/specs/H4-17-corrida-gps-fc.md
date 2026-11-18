# H4-17 — Corrida com GPS, FC e intervalados

## Status

| Campo | Valor |
|---|---|
| Item do roadmap | `H4-17` |
| MoSCoW | `S` |
| Versão alvo | `1.10.0` |
| Spec aprovada em | `2026-11-18` |
| Implementação | `concluída` |

## Objetivo

O usuário acompanha uma corrida ao vivo no celular com distância por GPS,
ritmo, frequência cardíaca disponível no Health Connect e intervalos por
tempo, sem transformar o registro manual atual em requisito.

## Fora de escopo

- Mapa interativo, navegação, áudio e integração com Strava/Nike/Garmin.
- Sensor direto de frequência cardíaca no relógio nesta entrega.
- Zonas automáticas sem idade/FC máxima configurada pelo usuário.
- Intervalados por distância quando o GPS estiver indisponível.
- Execução do treino intervalado no modo standalone do relógio.

## Arquivos e símbolos que devem mudar

| Arquivo | Mudança |
|---|---|
| `app/build.gradle.kts` | `play-services-location:21.3.0`; Health Connect já presente ganha leitura de FC |
| `app/src/main/AndroidManifest.xml` | Permissões de GPS em runtime; rastreamento ativo somente com a corrida aberta |
| `app/src/main/java/com/treinos/app/ui/run/RunTrackingEngine.kt` | Novo reducer puro de GPS, ritmo e intervalos |
| `app/src/main/java/com/treinos/app/ui/screens/RunWorkoutScreen.kt` | Ativar rastreamento, cartões ao vivo e intervalo por tempo |
| `app/src/main/java/com/treinos/app/data/local/Entities.kt` | Campos aditivos de rota/FC na sessão |
| `app/src/main/java/com/treinos/app/data/local/AppDatabase.kt` | Migration aditiva `12 -> 13` |
| `app/src/main/java/com/treinos/app/data/health/HealthConnectGateway.kt` | Leitura de FC disponível no Health Connect |
| `protocol/src/main/kotlin/com/treinos/protocol/WearProtocol.kt` | Campos opcionais de corrida no estado do relógio |
| `app/src/main/java/com/treinos/app/data/wear/WearSessionMapper.kt` | Publicar distância/ritmo/FC/intervalo no estado |
| `wear/src/main/java/com/treinos/wear/ui/WorkoutWearActivity.kt` | Mostrar resumo ao vivo da corrida no pulso |
| `app/src/test/java/com/treinos/app/ui/run/RunTrackingEngineTest.kt` | Casos puros do rastreamento |
| `app/src/test/java/com/treinos/app/data/local/MigrationTest.kt` | Migration `12 -> 13` |
| `protocol/src/test/kotlin/com/treinos/protocol/WearProtocolTest.kt` | Compatibilidade dos campos opcionais |

## Modelo de dados / contrato

```text
WorkoutSessionEntity (aditivo):
  routePolyline: String? = null
  avgHeartRate: Int? = null
  maxHeartRate: Int? = null

WearWorkoutState (aditivo, defaults):
  liveDistanceMeters: Double? = null
  livePaceSecondsPerKm: Int? = null
  liveHeartRate: Int? = null
  intervalPhase: String? = null
  intervalRemainingSeconds: Int? = null

protocolVersion permanece 1.
```

## Regras e casos de borda

1. GPS só é solicitado ao ativar rastreamento na tela de corrida; negar
   mantém o registro manual atual.
2. Sem sinal GPS, distância e pace ao vivo ficam vazios e o usuário ainda
   pode informar duração/distância manualmente.
3. FC vem apenas do Health Connect, em amostras disponíveis e com a
   permissão concedida; sem sensor/permissão não inventa valor.
4. O rastreador ignora localizações imprecisas ou com salto implausível.
5. Intervalado padrão: aquecimento 5 min → tiro 1 min → recuperação 1 min,
   repetido até o usuário finalizar; sem GPS, fases avançam por tempo.
6. Rotas, FC e intervalos não são enviados ao backend nem a terceiros.
7. Celular antigo ignora campos novos; relógio antigo descodifica defaults.
8. O cancelamento do rastreamento desmonta callbacks e não altera o formulário
   manual salvo.

## Critérios de aceite

1. **Dado** uma corrida ativa, **quando** o usuário concede GPS e o aparelho
   recebe posições válidas, **então** a distância e o ritmo ao vivo são
   recalculados.
2. **Dado** GPS negado ou sem sinal, **quando** o usuário finaliza,
   **então** a corrida manual continua funcionando sem crash.
3. **Dado** permissão de FC concedida, **quando** há amostra no período,
   **então** média e máximo da corrida são persistidos.
4. **Dado** nenhuma amostra de FC ou permissão negada, **então** os campos
   ficam `null`.
5. **Dado** intervalo iniciado, **quando** a fase expira, **então** o reducer
   avança para aquecimento, tiro ou recuperação pelo tempo decorrido.
6. **Dado** Room na versão 12, **quando** o app abre na versão 13,
   **então** a migration preserva todas as sessões existentes.

## Testes a criar

| Arquivo | Testes |
|---|---|
| `app/src/test/java/com/treinos/app/ui/run/RunTrackingEngineTest.kt` | `ignora_localizacao_invalida`, `soma_distancia_haversine`, `calcula_pace`, `avanca_intervalado_por_tempo`, `sem_gps_mantem_registro_manual` |
| `app/src/test/java/com/treinos/app/data/local/MigrationTest.kt` | `migration12To13_preserva_sessoes_e_campos_corrida` |
| `protocol/src/test/kotlin/com/treinos/protocol/WearProtocolTest.kt` | `wear_state_corrida_decodifica_campos_antigos`, `wear_state_corrida_round_trip` |

## Verificação manual

1. Abrir uma corrida, ativar rastreamento e conceder GPS; mover-se em área
   segura e conferir distância/ritmo.
2. Negar GPS e confirmar que duração/distância/RPE continuam finalizando.
3. Conceder FC no Health Connect e finalizar uma corrida com amostra; conferir
   média/máximo e ausência de valor sem amostra.
4. Iniciar intervalos e confirmar transição por tempo.
5. Abrir o relógio durante a corrida e conferir resumo ou ausência neutra.

## Comando de verificação

```bash
./scripts/verify.sh
```

## Riscos e rollback

- **Privacidade/bateria:** a localização só começa por opt-in e termina ao
  sair/cancelar; nenhuma rota sai do aparelho.
- **Revisão da Play Store:** localização em primeiro plano exige justificativa
  e declaração de uso; o formulário deve refletir o comportamento real.
- **Device sem GPS/Health Connect:** interface neutra e registro manual intacto.
- **Rollback:** remover a tela ativa e os campos aditivos; manter migration
  aditiva permite voltar sem apagar dados.
