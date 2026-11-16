# H4-9 — Health Connect (escrever treino, ler peso/passos)

## Status

| Campo | Valor |
|---|---|
| Item do roadmap | `H4-9` |
| MoSCoW | `S` |
| Versão alvo | `1.5.0` |
| Spec aprovada em | `2026-11-15` (pedido do dono de implementar a fila de specs) |
| Implementação | `concluída em 2026-11-15` |
| Depende de | — (é a primeira feature de saúde do app) |

**Origem:** levantamento de mercado de 2026-11-14 (Horizon 4, Tier 2). O
`AndroidManifest.xml` não tem nenhuma permissão de saúde e o app fica isolado do
ecossistema Android: um treino concluído não aparece no Health Connect e o peso
registrado em outros apps não chega aqui.

**Relacionado:** [H4-8](H4-8-peso-medidas.md) cria a tabela
`body_measurements`; esta spec **grava** nela as leituras de peso do Health
Connect (mesma entidade, `source = "health_connect"`). Nada vai para o backend.

## Objetivo

O usuário conecta o app ao Health Connect uma vez e, a partir daí, cada treino
concluído aparece no ecossistema Android e o peso/passos do sistema podem ser
lidos para dentro do app — tudo on-device, sem conta, sem nuvem e sem backend.

## Fora de escopo

- Escrever séries, cargas ou segmentos por exercício no Health Connect: o
  registro é uma `ExerciseSessionRecord` por sessão concluída (tipo, início,
  fim, título, notas, RPE). Séries não têm representação padrão no Health
  Connect.
- Ler frequência cardíaca, distância, calorias, sono ou qualquer outro tipo
  além de **peso** e **passos**.
- Escrever peso/medidas de volta no Health Connect (só leitura).
- Corrida com GPS/FC (H4-17, Won't now) ou permissões de localização/sensor.
- Envio de qualquer dado de saúde ao backend, à IA ou a terceiros.
- Health Connect no relógio (`:wear`); a integração é só no `:app`.
- Migração retroativa: sessões concluídas **antes** de conectar não são
  reescritas automaticamente (o usuário pode reexportar via ação explícita, se
  houver; ver regra 8).

## Arquivos e símbolos que devem mudar

| Arquivo | Mudança |
|---|---|
| `app/build.gradle.kts` | `implementation("androidx.health.connect:connect-client:1.1.0")` (versão fixa; ver Riscos). |
| `app/src/main/AndroidManifest.xml` | Permissões `android.permission.health.READ_WEIGHT`, `READ_STEPS` e `WRITE_EXERCISE`; `<activity-alias>` para `androidx.health.connect.action.SHOW_PERMISSIONS_RATIONALE`; `<activity-alias>` para `androidx.health.ACTION_SHOW_PERMISSIONS_RATIONALE` (Android 13−); `<activity-alias>` de `androidx.health.connect.ACTION_VIEW_PERMISSION_USAGE` (Android 14+) apontando para `MainActivity`. |
| `app/src/main/java/com/treinos/app/data/health/HealthConnectDtos.kt` | **Novo.** Tipos puros `HealthSessionDraft`, `HealthWeightSample`, `HealthStepsSample`, `HealthConnectAvailability`, `HealthConnectPermissionState`. |
| `app/src/main/java/com/treinos/app/data/health/HealthRecordMapper.kt` | **Novo.** Funções puras que convertem `SessionWithExercises`/`WorkoutSessionEntity` em `HealthSessionDraft` e os `Record`s lidos em `HealthWeightSample`/`HealthStepsSample`. **Não** importa o SDK do Health Connect (testável em JVM pura). |
| `app/src/main/java/com/treinos/app/data/health/HealthConnectGateway.kt` | **Novo.** Interface fina (`availability()`, `grantedPermissions()`, `writeSession(draft)`, `readWeight(from, to)`, `readSteps(from, to)`) + implementação `HealthConnectClientGateway` que usa `HealthConnectClient`. |
| `app/src/main/java/com/treinos/app/data/WorkoutRepository.kt` | Injeção opcional do gateway; `writeCompletedSessionToHealth(sessionId)`; `syncHealthWeight(...)`; `syncHealthSteps(...)`. |
| `app/src/main/java/com/treinos/app/TreinosApplication.kt` | Construir o gateway no container (só se `HealthConnectClient.getSdkStatus(context) == SDK_AVAILABLE`). |
| `app/src/main/java/com/treinos/app/ui/AppViewModel.kt` | `healthConnectState: StateFlow<HealthConnectUiState>`, `healthConnectMessage`, `refreshHealthConnect()`, `requestHealthPermissions()`, `syncHealthData()`. |
| `app/src/main/java/com/treinos/app/ui/MainActivity.kt` | `registerForActivityResult(PermissionController.createRequestPermissionResultContract())` e o pedido de permissão disparado pelo VM (mesmo padrão do `POST_NOTIFICATIONS` da H3-58). |
| `app/src/main/java/com/treinos/app/ui/screens/SettingsScreen.kt` | Seção "Health Connect": disponibilidade, conectar/gerenciar, permissões concedidas, últimos valores lidos e aviso de privacidade. |
| `app/src/main/res/values/strings.xml` e `app/src/main/res/values-en/strings.xml` | Títulos, estados (indisponível, precisa atualizar o app do Health Connect, sem permissão, conectado), aviso de privacidade e mensagens (paridade obrigatória). |
| `app/src/main/java/com/treinos/app/data/local/Entities.kt` | `BodyMeasurementEntity.source` aceita `"health_connect"` (a entidade é criada pela H4-8; se a H4-9 entrar antes, esta spec cria a tabela como na H4-8). |
| `app/src/test/java/com/treinos/app/data/health/HealthRecordMapperTest.kt` | **Novo.** Mapeamento puro (JVM). |
| `app/src/test/java/com/treinos/app/data/health/HealthConnectGatewayTest.kt` (Robolectric) | **Novo.** Disponibilidade, permissões e chamadas com um cliente fake. |

Não edite `shared/` nem `com.treinos.shared/` (legado).

## Modelo de dados / contrato

### Dependência e compatibilidade de build

```kotlin
implementation("androidx.health.connect:connect-client:1.1.0")
```

- `1.1.0` é a **última versão estável** publicada no Google Maven
  (`androidx.health.connect:connect-client`; a linha 1.2.x está em alpha).
- `minSdk` do app é 26 e o `connect-client` declara `minSdk 26` — compatível.
- **Verificar no primeiro build** se `1.1.0` exige `compileSdk 35` (o app usa
  `compileSdk = 34`). Se exigir, o bump de `compileSdk`/`targetSdk` e a
  instalação de `platforms;android-35`/`build-tools;35.0.0` no `Dockerfile`
  fazem parte desta entrega (o `Dockerfile` hoje instala só `android-34`); sem
  isso o build falha no gate. O `targetSdk` pode continuar 34 se o bump for só
  de `compileSdk`.

### Permissões

```xml
<uses-permission android:name="android.permission.health.READ_WEIGHT" />
<uses-permission android:name="android.permission.health.READ_STEPS" />
<uses-permission android:name="android.permission.health.WRITE_EXERCISE" />
```

Conjunto pedido em runtime (`Set<String>`):

```kotlin
val HEALTH_PERMISSIONS: Set<String> = setOf(
    HealthPermission.getReadPermission(WeightRecord::class),
    HealthPermission.getReadPermission(StepsRecord::class),
    HealthPermission.getWritePermission(ExerciseSessionRecord::class),
)
```

### Mapeamento puro (sem SDK no import)

```kotlin
// data/health/HealthConnectDtos.kt
enum class HealthConnectAvailability { AVAILABLE, PROVIDER_UPDATE_REQUIRED, UNAVAILABLE }

data class HealthSessionDraft(
    val clientRecordId: String,   // "treinos:session:<id>"
    val startedAtMillis: Long,
    val endedAtMillis: Long,
    val exerciseType: Int,        // constante do Health Connect, resolvida no gateway
    val title: String?,
    val notes: String?,
    val rpe: Float?,              // sessionRpe, quando 1.0..10.0
)

data class HealthWeightSample(val timeMillis: Long, val weightKg: Double)
data class HealthStepsSample(val startMillis: Long, val endMillis: Long, val count: Long)

// data/health/HealthRecordMapper.kt (puro)
fun toHealthSessionDraft(session: WorkoutSessionEntity): HealthSessionDraft?
fun healthExerciseTypeFor(modality: String, stroke: String?): Int
fun toWeightSamples(records: List<RawWeight>): List<HealthWeightSample>
fun toStepsSamples(records: List<RawSteps>): List<HealthStepsSample>
```

O gateway é quem toca o SDK:

```kotlin
interface HealthConnectGateway {
    fun availability(): HealthConnectAvailability
    suspend fun grantedPermissions(): Set<String>
    suspend fun writeSession(draft: HealthSessionDraft)
    suspend fun readWeight(fromMillis: Long, toMillis: Long): List<HealthWeightSample>
    suspend fun readSteps(fromMillis: Long, toMillis: Long): List<HealthStepsSample>
}
```

### Regras de escrita/leitura

- **Escrita:** uma `ExerciseSessionRecord` por sessão `status == "completed"`,
  com `metadata = Metadata.manualEntry(clientRecordId = "treinos:session:<id>")`
  — reescrever a mesma sessão **atualiza** o registro em vez de duplicar
  (`clientRecordId` é a chave de idempotência do Health Connect).
- **Tipo de exercício:** `strength` → `EXERCISE_TYPE_STRENGTH_TRAINING` (70);
  `run` → `EXERCISE_TYPE_RUNNING` (56); `swim` → `EXERCISE_TYPE_SWIMMING_POOL`
  (74) ou `EXERCISE_TYPE_SWIMMING_OPEN_WATER` (73) conforme o campo `stroke`;
  `mobility` → `EXERCISE_TYPE_STRETCHING` (71); `other` →
  `EXERCISE_TYPE_OTHER_WORKOUT` (0).
- **Leitura de peso:** `readRecords(ReadRecordsRequest(WeightRecord::class,
  timeRangeFilter = TimeRangeFilter.between(from, to)))`; cada
  `WeightRecord.weight.inKilograms` vira `HealthWeightSample` e é gravado em
  `body_measurements` com `source = "health_connect"`.
- **Leitura de passos:** `aggregate(AggregateRequest(metrics = setOf(
  StepsRecord.COUNT_TOTAL), timeRangeFilter = ...))` para o total do período; o
  valor é exibido na tela (não cria entidade no Room).
- **Período padrão:** últimos 30 dias; o usuário pode pedir "sincronizar agora".

## Regras e casos de borda

1. **Disponibilidade:** `HealthConnectClient.getSdkStatus(context)` →
   `SDK_AVAILABLE` mostra "Conectar"; `SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED`
   mostra "Atualize o Health Connect" com intent para a loja;
   `SDK_UNAVAILABLE` (API < 28 ou perfil de trabalho) esconde a seção inteira —
   nunca crasha.
2. **Sem permissão:** escrita/leitura não é tentada; a UI mostra "Conecte para
   sincronizar". Nenhum dado é gravado no Health Connect sem permissão.
3. **Permissão parcial:** o usuário pode conceder só `WRITE_EXERCISE`. A UI
   mostra o que está concedido e habilita só o que dá para fazer (escrever sem
   ler peso, por exemplo).
4. **Sessão sem `endedAt`:** não é escrita (o Health Connect exige
   `startTime < endTime`); sessão `in_progress` nunca é escrita.
5. **Sessão com duração zero:** `endedAt == startedAt` → não escreve (evita
   `IllegalArgumentException` do `require(startTime.isBefore(endTime))`).
6. **RPE:** só entra quando `sessionRpe` está em `1.0..10.0`; fora disso vira
   `null` (o record aceita `0.0..10.0`, mas o app usa a faixa de RPE dele).
7. **Idempotência:** reescrever a mesma sessão usa o mesmo `clientRecordId`;
   nenhuma duplicata no Health Connect.
8. **Falha na escrita:** não bloqueia o fim do treino nem o fluxo local. A
   sessão continua salva no Room; a UI mostra "Não foi possível enviar ao
   Health Connect" com "Tentar de novo". Erros de permissão
   (`SecurityException`) e de quota (`RateLimitExceededException`) são
   distinguidos na mensagem.
9. **Peso duplicado:** um `WeightRecord` lido cujo `time` já existe em
   `body_measurements` (mesmo `measuredAt` e `source = "health_connect"`) é
   ignorado — sincronizar duas vezes não duplica.
10. **Peso fora da faixa:** leitura abaixo de 30 kg ou acima de 300 kg é
    descartada (mesma faixa de `MeasurementValidator`).
11. **Passos:** `count` fora de `1..1_000_000` é descartado; período sem
    registros mostra "Sem dados de passos".
12. **Sem conta e sem rede:** todo o fluxo é local; modo avião não altera nada.
13. **Nada de saúde sai do aparelho:** nenhum campo de saúde entra no backup
    JSON, no `SyncRequestDto` ou em log. O peso lido do Health Connect fica no
    Room local.
14. **Play/privacidade:** a seção tem aviso de privacidade e o app precisa da
    política de privacidade publicada antes do release (fora do escopo de
    código, mas bloqueia a publicação).

## Critérios de aceite

1. **Dado** um aparelho sem Health Connect instalado, **quando** o usuário abre
   Ajustes, **então** a seção mostra "Atualize o Health Connect" (ou fica
   oculta, se indisponível) e o app não crasha.
2. **Dado** o Health Connect disponível e sem permissões, **quando** o usuário
   toca "Conectar", **então** o diálogo do sistema pede as 3 permissões e, ao
   conceder, a seção mostra "Conectado".
3. **Dado** `WRITE_EXERCISE` concedido, **quando** o usuário finaliza um treino
   de musculação, **então** uma `ExerciseSessionRecord` de força aparece no
   Health Connect com início, fim e título iguais aos da sessão.
4. **Dado** o mesmo treino finalizado duas vezes (reabrir e finalizar),
   **então** continua havendo **um** registro no Health Connect.
5. **Dado** `READ_WEIGHT` concedido e um peso registrado no Health Connect,
   **quando** o usuário toca "Sincronizar agora", **então** o peso aparece em
   "Peso e medidas" com `source = health_connect`.
6. **Dado** que o usuário sincroniza duas vezes sem peso novo, **então** não há
   medida duplicada.
7. **Dado** `READ_STEPS` concedido, **quando** sincroniza, **então** a tela
   mostra o total de passos do período (30 dias).
8. **Dado** que o usuário nega as permissões, **então** a seção mostra "Conecte
   para sincronizar" e nenhuma escrita é tentada.
9. **Dado** que o Health Connect está indisponível (API < 28), **então** a seção
   não aparece e o app funciona normalmente.
10. **Dado** o app em modo avião, **quando** finaliza um treino, **então** o
    registro local acontece e a escrita no Health Connect funciona (é local).
11. **Dado** um backup JSON exportado, **então** ele não contém nenhum campo
    vindo do Health Connect além do que já está em `body_measurements`.

## Testes a criar

| Arquivo | Testes |
|---|---|
| `app/src/test/java/com/treinos/app/data/health/HealthRecordMapperTest.kt` | `sessao_concluida_vira_draft`, `sessao_em_andamento_nao_vira_draft`, `sessao_sem_ended_at_nao_vira_draft`, `duracao_zero_nao_vira_draft`, `client_record_id_e_estavel_por_sessao`, `tipo_strength_e_strength_training`, `tipo_run_e_running`, `tipo_swim_pool_e_open_water`, `tipo_mobility_e_stretching`, `tipo_desconhecido_e_other`, `rpe_fora_da_faixa_vira_nulo`, `peso_abaixo_de_30_e_descartado`, `peso_acima_de_300_e_descartado`, `passos_fora_da_faixa_descartados`, `amostras_em_ordem_cronologica` |
| `app/src/test/java/com/treinos/app/data/health/HealthConnectGatewayTest.kt` (Robolectric) | `availability_mapeia_sdk_status`, `sem_permissao_nao_escreve`, `permissao_parcial_habilita_so_o_concedido`, `escrita_usa_client_record_id`, `leitura_de_peso_mapeia_kilogramas`, `leitura_de_passos_usa_total`, `erro_de_permissao_nao_propaga_excecao` |

`HealthRecordMapperTest` é JVM puro (não importa o SDK). O gateway é testado com
um cliente fake; o teste de integração real exige emulador com Health Connect e
fica na verificação manual.

## Verificação manual

1. Instale o app em um emulador/dispositivo com Health Connect (Android 14+ ou
   o app Health Connect instalado em Android 9+).
2. Ajustes → Health Connect: confira o estado inicial e o aviso de privacidade.
3. Toque "Conectar" e conceda as 3 permissões; confira "Conectado".
4. Finalize um treino de musculação e abra o Health Connect → Exercícios:
   confira a sessão com tipo, início, fim e título.
5. Finalize/reedite o mesmo treino e confira que não há sessão duplicada.
6. Registre um peso no Health Connect (ou em outro app que escreva nele) e toque
   "Sincronizar agora": confira o peso em "Peso e medidas".
7. Sincronize de novo e confira que não duplicou.
8. Caminho de erro: em Ajustes do Android, revogue a permissão de peso e toque
   "Sincronizar agora" → confira a mensagem e que o app não crasha.
9. Desative o Health Connect (ou use um aparelho sem ele) e confira a seção
   oculta/"atualize".
10. Ative o modo avião e repita os passos 4 e 6.

## Comando de verificação

```bash
./scripts/verify.sh
```

## Riscos e rollback

- **Dependência nova:** `androidx.health.connect:connect-client:1.1.0` (Google
  Maven, versão fixa). É a única forma de falar com o Health Connect; sem ela
  não há integração. O risco de build é o `compileSdk`: se `1.1.0` exigir 35, o
  `Dockerfile` precisa instalar `platforms;android-35`/`build-tools;35.0.0` e o
  `app/build.gradle.kts` precisa subir `compileSdk`. Verificar antes de
  implementar; o gate falha cedo se faltar.
- **Revisão do Play:** apps que usam Health Connect precisam de política de
  privacidade e declaração de uso; a permissão `READ_STEPS`/`READ_WEIGHT`/
  `WRITE_EXERCISE` é considerada sensível. Escopo restrito (3 permissões, sem
  conta, sem backend) reduz o risco, mas a publicação pode exigir revisão — é
  risco de release, não de código.
- **Dados de saúde vazando:** mitigado por não incluir nenhum campo de saúde no
  backup, no sync do backend ou em logs; o peso lido vira `body_measurements`
  local. Teste de serialização do backup cobre a ausência de campos novos.
- **Falha do Health Connect no meio do treino:** a escrita é best-effort e
  posterior ao `finishSession` local; nunca bloqueia o fluxo de treino. Erros
  viram mensagem com "Tentar de novo".
- **Duplicatas no Health Connect:** `clientRecordId` estável por sessão é a
  trava; teste de gateway confere o id usado.
- **Rollback:** remover a dependência, as permissões do manifest e o pacote
  `data/health/`, além da seção em Ajustes. Nenhum dado local é apagado; os
  registros já escritos no Health Connect permanecem lá (o usuário pode apagá-los
  no próprio Health Connect) e as medidas com `source = "health_connect"`
  continuam válidas no Room.

