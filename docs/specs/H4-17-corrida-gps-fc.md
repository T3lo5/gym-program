# H4-17 — Corrida com GPS, FC e intervalados

## Status

| Campo | Valor |
|---|---|
| Item do roadmap | `H4-17` |
| MoSCoW | `W` |
| Versão alvo | `1.5.0` |
| Spec aprovada em | `pendente` |
| Implementação | `pendente` |

## Objetivo

Registrar a **decisão de produto** de manter corrida com GPS, frequência
cardíaca e intervalados **fora da 1.5.0**: o item exige permissões de
localização/sensor que a H2-2 recusou e contraria a promessa de "sem
sensores". Ele só entra em execução se o dono priorizar explicitamente.

## Contexto (estado atual verificado)

- A corrida é **registro manual**: `RunWorkoutScreen` pede duração (min),
  distância (km) e RPE e chama `finishRunWorkout(sessionId, durationSeconds,
  distanceMeters, rpe)` (`AppViewModel.kt` L1484); o pace é calculado só na
  finalização (`RunLog.validateRunLog`, L30–52).
- O manifesto do celular **não tem** permissão de localização nem de sensor:
  só `INTERNET`, `ACCESS_NETWORK_STATE`, `ACCESS_LOCAL_NETWORK`, `VIBRATE`,
  `POST_NOTIFICATIONS` e `REQUEST_INSTALL_PACKAGES`
  (`app/src/main/AndroidManifest.xml` L5–18).
- O manifesto do relógio **não tem** `BODY_SENSORS`, `ACTIVITY_RECOGNITION`
  nem `FOREGROUND_SERVICE_HEALTH` — a H2-2 recusou sensores de propósito
  (H2-2, borda 12).
- O `MANUAL.md` §3.3 promete "não acessa sua localização" (L339); o
  `ROADMAP.md` §8 registra o risco "GPS/FC na corrida quebra a promessa de
  'sem sensores'" (L415).
- O relógio não tem tela de métricas de corrida: a H3-19 mostra só o nome da
  sessão, "Em andamento" e "Finalizar Treino".

## Fora de escopo (desta entrega)

- Implementar GPS, ritmo em tempo real, zonas de FC, intervalados, cues de
  áudio ou integração com Strava/Nike/Garmin.
- Adicionar **qualquer** permissão ou dependência nova ao app ou ao relógio.
- Alterar a tela de corrida manual (`RunWorkoutScreen`), o `RunLog` ou o
  `finishRunWorkout`.
- Sincronizar atividades com serviços de terceiros.
- Watchface, tile ou notificação de corrida (H4-14).

## Arquivos e símbolos que devem mudar

Nesta entrega (documental, sem código):

| Arquivo | Mudança |
|---|---|
| `docs/MANUAL.md` | §7 (Segurança e privacidade) ganha a decisão explícita: o app não acessa localização nem sensores; se a H4-17 for priorizada, a permissão será pedida só no fluxo da corrida e documentada aqui |
| `docs/specs/H4-17-corrida-gps-fc.md` | Esta spec (decisão + desenho futuro) |
| `ROADMAP.md` | Mantém a H4-17 como **Won't now** (alteração feita pelo agente principal, não por esta spec) |

### Se priorizado (desenho, **não** aprovado — não implementar agora)

| Arquivo | Mudança |
|---|---|
| `app/build.gradle.kts` | `implementation("com.google.android.gms:play-services-location:21.3.0")` (versão fixa; confirmar a estável vigente na aprovação — o repo já usa Play Services `18.1.0` no `:wear`) |
| `app/src/main/AndroidManifest.xml` | `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_LOCATION`, `BODY_SENSORS` e `ACTIVITY_RECOGNITION`, pedidas em runtime só no fluxo da corrida |
| `app/src/main/java/com/treinos/app/ui/run/RunTrackingEngine.kt` (novo) | Reducer puro de rota/ritmo/zonas de FC e das fases dos intervalados |
| `app/src/main/java/com/treinos/app/ui/screens/RunWorkoutScreen.kt` | Modo ao vivo (mapa/ritmo/FC) ao lado do registro manual atual |
| `app/src/main/java/com/treinos/app/data/local/Entities.kt` | Campos aditivos de rota/FC na sessão (migration explícita + teste, como manda a H1-6) |
| `wear/src/main/java/com/treinos/wear/ui/WorkoutWearActivity.kt` | FC/ritmo no pulso (depende de H4-16 para operar sem o celular) |

## Modelo de dados / contrato

```text
Nesta entrega: nenhum contrato novo.
  :protocol, Room, backup, backend e manifests ficam exatamente como estão.
  Nenhuma dependência nova; nenhuma permissão nova.

Se priorizado (desenho, não aprovado):
  Permissões, pedidas em runtime apenas na tela de corrida:
    ACCESS_FINE_LOCATION / ACCESS_COARSE_LOCATION  → rota e distância
    BODY_SENSORS (ou Health Services)              → frequência cardíaca
    FOREGROUND_SERVICE + FOREGROUND_SERVICE_LOCATION → gravar com a tela apagada
    ACTIVITY_RECOGNITION                            → passos/cadência (opcional)
  Dependência: com.google.android.gms:play-services-location:21.3.0
    Justificativa: o projeto já usa Play Services (`play-services-wearable:18.1.0`)
    e o `FusedLocationProviderClient` entrega rota/ritmo com menos bateria que o
    `LocationManager` puro. Alternativa sem Play Services: `android.location`
    (custo maior de bateria e de código).
  Dados novos (Room, migration aditiva + teste):
    workout_sessions: routePolyline? (String), avgHeartRate? (Int), maxHeartRate? (Int)
    set_logs já tem distanceMeters/durationSeconds (Entities.kt L167–168)
  Intervalados: lista de blocos (aquecimento, tiro, recuperação) no plano de
    corrida; o `RunTrackingEngine` decide a fase pelo tempo/distância decorridos.
```

## Regras e casos de borda

- **Regra da decisão:** enquanto a H4-17 for `W`, nenhum arquivo de código,
  manifest ou `build.gradle.kts` muda por causa dela; o gate continua verde.
- Se priorizada, a permissão é pedida **só** ao abrir a corrida; negar a
  permissão cai no registro manual atual (a tela não quebra e continua
  finalizando com duração/distância digitadas).
- Sem sinal de GPS: a distância fica nula e o pace não é calculado (mesma
  regra do `validateRunLog`); a sessão ainda pode ser finalizada.
- Sem sensor de FC: as zonas ficam vazias e a tela não mostra FC (nunca
  inventa valor).
- Intervalados com distância-alvo e sem GPS: a fase avança por tempo, não por
  distância.
- Cues de áudio: só quando o usuário ligar explicitamente; sem permissão de
  notificação o app não depende delas.
- Rota/FC nunca saem do aparelho: sem backend, sem conta e sem Strava.
- Rollback da decisão: se o dono priorizar, esta spec vira item de execução e
  o `ROADMAP.md` move a H4-17 de §5 (Won't now) para o horizonte ativo.

## Critérios de aceite

1. **Dado** o repositório na 1.5.0, **quando** `./scripts/verify.sh` roda,
   **então** o gate termina com a linha de sucesso e nenhum manifest ganhou
   permissão de localização/sensor.
2. **Dado** `app/src/main/AndroidManifest.xml` e
   `wear/src/main/AndroidManifest.xml`, **quando** inspecionados, **então**
   não contêm `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`, `BODY_SENSORS`,
   `ACTIVITY_RECOGNITION` nem `FOREGROUND_SERVICE_LOCATION`.
3. **Dado** `docs/MANUAL.md` §7, **quando** lido, **então** registra a decisão
   de não acessar localização/sensores e o que mudaria se a H4-17 for
   priorizada.
4. **Dado** `ROADMAP.md` §5, **quando** lido, **então** a H4-17 continua na
   coluna **Won't now**.
5. **Dado** a corrida atual, **quando** o usuário finaliza com duração e
   distância digitadas, **então** o comportamento é o de hoje (nenhuma
   regressão).

## Testes a criar

| Arquivo | Testes |
|---|---|
| — (entrega documental) | Nenhum teste de código: esta entrega não altera lógica. A prova é o gate verde e a inspeção dos manifests. |
| `app/src/test/java/com/treinos/app/ui/run/RunTrackingEngineTest.kt` (só se priorizado) | `avanca_intervalado_por_tempo`, `sem_gps_nao_calcula_pace`, `sem_fc_nao_inventa_zona`, `permissao_negada_cai_no_manual` |

## Verificação manual

1. Rodar `./scripts/verify.sh` e conferir a linha final de sucesso.
2. Abrir `app/src/main/AndroidManifest.xml` e `wear/src/main/AndroidManifest.xml`
   e confirmar que não há permissão de localização/sensor.
3. Abrir `docs/MANUAL.md` §7 e confirmar a decisão registrada.
4. Abrir `ROADMAP.md` §5 e confirmar a H4-17 em **Won't now**.
5. Fazer uma corrida com registro manual (duração + distância) e confirmar que
   o fluxo atual não regrediu.

## Comando de verificação

```bash
./scripts/verify.sh
```

## Riscos e rollback

- **Quebra da promessa de privacidade:** é o risco central; por isso a H4-17
  fica `W` e a decisão é explícita. Se priorizada, a permissão é pedida só no
  fluxo da corrida e documentada em `MANUAL.md` §7.
- **Revisão da Play Store:** permissões de localização em segundo plano e de
  saúde exigem declaração de uso; o escopo deve ser justificado no formulário
  da loja.
- **Bateria/consumo:** GPS + FC contínuos consomem bateria; mitigar com
  `FusedLocationProviderClient`, intervalos de atualização e foreground
  service com tipo declarado.
- **Rollback:** como a entrega é documental, reverter é remover o parágrafo do
  `MANUAL.md` §7 e esta spec; nenhum dado do usuário, schema, contrato ou
  dependência é tocado.
