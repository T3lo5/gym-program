# H3-1 — Testes de UI, Room e contrato (cobertura além do gate atual)

## Status

| Campo | Valor |
|---|---|
| Item do roadmap | `H3-1` |
| MoSCoW | `M` |
| Spec aprovada em | `2026-11-03` |
| Implementação | `em andamento` (16 arquivos JVM + gate; falta UI/Room/contrato) |
| Depende de | H1-4 (fluxo de backup) e H2-4 (histórico) para os testes de UI |

## Objetivo

Fazer o gate enxergar o que hoje ele não vê: telas Compose, acesso ao Room e o
contrato JSON entre app e backend — sem exigir emulador no `./scripts/verify.sh`.

## Fora de escopo

- Exigir `connectedAndroidTest` no gate (o Docker não tem emulador).
- Testes end-to-end com backend real/SenseNova.
- Testes de Wear OS (entram junto com H2-2).
- Meta de cobertura que reprova o build. O relatório é informativo; o gate
  continua reprovando por teste quebrado, não por percentual.
- Testes de performance, screenshot testing ou detox.

## Arquivos e símbolos que devem mudar

| Arquivo | Mudança |
|---|---|
| `app/build.gradle.kts` | Dependências de teste, `isIncludeAndroidResources`, cobertura |
| `app/src/main/java/com/treinos/app/data/local/AppDatabase.kt` | `exportSchema = true` |
| `app/schemas/com.treinos.app.data.local.AppDatabase/1.json` | **Novo.** Schema exportado (commitado) |
| `app/src/test/resources/robolectric.properties` | **Novo.** `sdk=34` |
| `app/src/test/java/com/treinos/app/ui/HistoryScreenTest.kt` | **Novo.** Compose + Robolectric |
| `app/src/test/java/com/treinos/app/ui/BackupFlowUiTest.kt` | **Novo.** Estados de import/export |
| `app/src/test/java/com/treinos/app/data/local/WorkoutDaoTest.kt` | **Novo.** Room em memória |
| `app/src/test/java/com/treinos/app/data/remote/ContractTest.kt` | **Novo.** Decodifica o fixture compartilhado |
| `contracts/generate_response.json` | **Novo.** Fixture única app ↔ backend |
| `back/tests/test_contract.py` | **Novo.** Valida o mesmo fixture no modelo Pydantic |
| `app/src/androidTest/java/com/treinos/app/ui/WorkoutScreenDeviceTest.kt` | **Novo.** Instrumentado (opcional no device) |
| `app/src/androidTest/java/com/treinos/app/data/local/AppDatabaseMigrationTest.kt` | **Novo.** Instrumentado (migrations futuras) |
| `scripts/ci-android.sh` | Rodar cobertura quando `COVERAGE=1`; nada de emulador |
| `.github/workflows/android.yml` | Subir o relatório de cobertura como artifact |
| `docs/MANUAL.md` | Como rodar `connectedDebugAndroidTest` localmente |

Não edite `shared/` nem `com.treinos.shared/` (legado).

## Modelo de dados / contrato

### Dependências (versões fixas)

```kotlin
testImplementation("junit:junit:4.13.2")
testImplementation("org.robolectric:robolectric:4.12.2")
testImplementation("androidx.test:core-ktx:1.5.0")
testImplementation("androidx.test.ext:junit:1.1.5")
testImplementation("androidx.room:room-testing:2.6.1")
testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.0")
testImplementation(platform("androidx.compose:compose-bom:2024.04.01"))
testImplementation("androidx.compose.ui:ui-test-junit4")
debugImplementation("androidx.compose.ui:ui-test-manifest")

androidTestImplementation("androidx.test.ext:junit:1.1.5")
androidTestImplementation("androidx.test:runner:1.5.2")
androidTestImplementation("androidx.test:rules:1.5.0")
androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
androidTestImplementation(platform("androidx.compose:compose-bom:2024.04.01"))
androidTestImplementation("androidx.compose.ui:ui-test-junit4")
```

```kotlin
android {
    testOptions {
        unitTests.isReturnDefaultValues = true
        unitTests.isIncludeAndroidResources = true
    }
    buildTypes {
        debug { enableUnitTestCoverage = true }
    }
}

kapt {
    arguments { arg("room.schemaLocation", "$projectDir/schemas") }
}

sourceSets.getByName("androidTest").assets.srcDir("$projectDir/schemas")
```

### Fixture de contrato

`contracts/generate_response.json` (raiz do repo) deve ser um
`GeneratePlanResponse` válido:

```json
{
  "workout": {
    "name": "Full Body A",
    "description": "Plano de contrato",
    "goal": "hypertrophy",
    "daysPerWeek": 2,
    "days": [
      {
        "name": "Treino A",
        "dayOrder": 0,
        "isRestDay": false,
        "exercises": [
          {
            "exerciseId": "Barbell_Bench_Press",
            "exerciseOrder": 0,
            "sets": 3,
            "targetReps": "8-12",
            "targetRpe": 8.0,
            "restSeconds": 90,
            "notes": "",
            "isSupersetWithPrevious": false,
            "exerciseName": "Supino reto",
            "primaryMuscles": ["chest"],
            "equipment": "barbell"
          }
        ]
      }
    ]
  },
  "generationTimeMs": 1200,
  "modelUsed": "contrato",
  "planId": 42,
  "nextReviewAt": "2026-12-01",
  "reviewAfterWeeks": 4,
  "source": "local"
}
```

Regras:

- O app lê com `Json { ignoreUnknownKeys = true }` e decodifica em
  `GeneratePlanResponse`. Campo obrigatório ausente → teste falha.
- O backend valida o mesmo arquivo com `GenerateResponse.model_validate(...)`.
- O fixture é a única fonte; não duplicar o JSON em dois lugares.
- Caminho no teste JVM: `File("../contracts/generate_response.json")`.
- Caminho no teste Python: `Path(__file__).resolve().parents[2] / "contracts"`.
- `robolectric.properties` fixa `sdk=34` para não variar com o host.
- `AppDatabase` exporta schema; o JSON versionado é o contrato das migrations.

## Regras e casos de borda

1. Teste Robolectric não pode depender de rede, relógio real ou backend.
2. Toda tela testada recebe dados falsos determinísticos (`SessionWithExercises`
   montado no teste), nunca o `AppViewModel` real.
3. `WorkoutDaoTest` usa `Room.inMemoryDatabaseBuilder(...).allowMainThreadQueries()`
   e fecha no `@After`.
4. Testes de Room cobrem: inserir plano completo, `deleteAllData` e
   `replaceAllData` preservando IDs.
5. `AppDatabaseMigrationTest` usa `MigrationTestHelper`; enquanto a versão for
   `1`, o teste apenas abre o banco e valida o schema exportado.
6. Nenhum teste usa `Thread.sleep`; timers usam `kotlinx-coroutines-test`.
7. Teste de UI falha se um texto/label esperado sumir, não apenas se compilar.
8. `connectedDebugAndroidTest` continua fora do gate; o `docs/MANUAL.md` deve
   deixar isso explícito.
9. Cobertura é gerada com `COVERAGE=1`, mas o build não falha por percentual.
10. Se o Robolectric baixar Android jars na primeira execução, o cache do
    Gradle (`./.gradle-docker`) deve ser preservado no CI.
11. Nenhum teste pode apagar dados reais: usar banco em memória ou `filesDir`
    temporário.
12. O fixture de contrato não pode conter dados pessoais nem token.

## Critérios de aceite

1. **Dado** `./scripts/verify.sh`, **quando** roda, **então**
   `:app:testDebugUnitTest` executa também os testes Robolectric de UI e Room,
   sem emulador.
2. **Dado** que alguém remove o texto “Nenhum treino registrado” do
   `HistoryScreen`, **quando** roda o gate, **então** o teste de UI falha.
3. **Dado** que alguém quebra `replaceAllData`, **então** o teste de Room falha
   antes de chegar ao device.
4. **Dado** que o backend renomeia `nextReviewAt`, **então** o teste de contrato
   (app ou backend) falha.
5. **Dado** `COVERAGE=1 ./scripts/ci-android.sh`, **então** o relatório HTML é
   gerado em `app/build/reports/coverage/test/debug/index.html`.
6. **Dado** um device/emulador conectado, **quando** roda
   `./gradlew :app:connectedDebugAndroidTest`, **então** os testes
   instrumentados passam.
7. **Dado** o schema exportado, **então** ele está commitado em
   `app/schemas/` e o build não o altera silenciosamente.

## Testes a criar

| Arquivo | Testes |
|---|---|
| `app/src/test/java/com/treinos/app/ui/HistoryScreenTest.kt` | `mostra_estado_vazio`, `mostra_sessao_concluida`, `expande_e_recolhe_sessao`, `mostra_metricas_do_mes` |
| `app/src/test/java/com/treinos/app/ui/BackupFlowUiTest.kt` | `mostra_confirmacao_de_import`, `mostra_erro_de_versao`, `cancela_sem_alterar` |
| `app/src/test/java/com/treinos/app/data/local/WorkoutDaoTest.kt` | `insere_plano_completo`, `delete_all_data_limpa_tudo`, `replace_all_data_preserva_ids`, `replace_all_data_e_atomico` |
| `app/src/test/java/com/treinos/app/data/remote/ContractTest.kt` | `decodifica_fixture_de_contrato`, `fixture_tem_campos_obrigatorios`, `ignora_campo_novo_desconhecido` |
| `back/tests/test_contract.py` | `test_fixture_e_um_generate_response_valido`, `test_campos_obrigatorios_presentes`, `test_source_permitido` |
| `app/src/androidTest/java/com/treinos/app/ui/WorkoutScreenDeviceTest.kt` | `registra_serie_no_dispositivo`, `timer_de_descanso_aparece` |
| `app/src/androidTest/java/com/treinos/app/data/local/AppDatabaseMigrationTest.kt` | `abre_banco_na_versao_atual` |

## Verificação manual

1. Rode `./scripts/verify.sh` e confira no log que os testes Robolectric
   aparecem em `testDebugUnitTest`.
2. Abra `app/build/reports/tests/testDebugUnitTest/index.html`.
3. Rode `COVERAGE=1 ./scripts/ci-android.sh` e abra o relatório de cobertura.
4. Conecte um device/emulador e rode
   `./gradlew :app:connectedDebugAndroidTest`; confirme que o gate não depende
   disso.
5. Altere temporariamente um texto esperado e confirme que o teste falha (depois
   reverta).

## Comando de verificação

```bash
./scripts/verify.sh
```

## Riscos e rollback

- **Peso do Robolectric:** primeira execução baixa jars Android; o cache do
  Gradle precisa sobreviver. Se o CI ficar instável, fixar a versão e usar o
  cache — não remover o teste.
- **Flakiness de UI:** usar `waitUntil`/`waitForIdle`, nunca `Thread.sleep`.
- **Schema exportado:** se `room.schemaLocation` não estiver configurado, o
  build falha; o schema deve ser commitado junto.
- **Cobertura virar meta:** o relatório é informativo; não adicionar threshold
  que incentive testar getter.
- **Rollback:** remover as dependências de teste, os novos arquivos de teste e
  o `exportSchema = true`. O app e o gate voltam ao estado atual.
