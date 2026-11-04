# H1-5 — Peso e altura na geração de treino

## Status

| Campo | Valor |
|---|---|
| Item do roadmap | `H1-5` |
| MoSCoW | `S` |
| Spec aprovada em | `2026-11-03` |
| Implementação | `concluída` |
| Depende de | nada (H1-4 não é pré-requisito) |

## Objetivo

Permitir que o usuário informe peso e altura uma vez e use esse perfil para
calibrar o plano gerado (descanso, RPE, volume e sugestão inicial de carga),
tanto na IA quanto no fallback local.

## Fora de escopo

- Cálculo de 1RM, percentual de gordura ou recomendação médica.
- Prescrever carga “correta” — a sugestão é ponto de partida, não prescrição.
- Histórico de peso corporal ou gráficos de composição corporal.
- Alterar o schema Room (o perfil fica em `AppPreferences`).
- Auth/conta: o perfil é local e vai ao backend só no request de geração.

## Arquivos e símbolos que devem mudar

| Arquivo | Mudança |
|---|---|
| `app/src/main/java/com/treinos/app/data/prefs/AppPreferences.kt` | `bodyWeightKg: Double?` e `heightCm: Int?` com chaves novas |
| `app/src/main/java/com/treinos/app/data/remote/BackendDtos.kt` | `GeneratePlanRequest.bodyWeightKg` e `heightCm` |
| `app/src/main/java/com/treinos/app/ui/screens/GenerateScreen.kt` | Campos numéricos, validação, resumo do perfil |
| `app/src/main/java/com/treinos/app/ui/TreinosApp.kt` | Repassar os novos parâmetros |
| `app/src/main/java/com/treinos/app/ui/AppViewModel.kt` | Persistir perfil e montar o request |
| `back/app/main.py` | `GenerateRequest` + `build_user_prompt(...)` com perfil |
| `back/app/plan_builder.py` | `profile_adjustments(...)` aplicado ao plano local |
| `back/tests/test_profile_adjustments.py` | **Novo.** Testes das regras |
| `app/src/test/java/com/treinos/app/ui/ProfileValidatorTest.kt` | **Novo.** Validação pura dos campos |

Não edite `shared/` nem `com.treinos.shared/` (legado).

## Modelo de dados / contrato

```kotlin
// AppPreferences
var bodyWeightKg: Double?   // null = não informado
var heightCm: Int?          // null = não informado

// GeneratePlanRequest (app -> backend)
val bodyWeightKg: Double? = null
val heightCm: Int? = null
```

```python
# GenerateRequest (backend)
bodyWeightKg: Optional[float] = Field(default=None, ge=30.0, le=300.0)
heightCm: Optional[int] = Field(default=None, ge=120, le=250)
```

Regras:

- Os dois campos são opcionais. Ajuste de perfil só acontece quando **ambos**
  estão presentes e dentro das faixas.
- App valida antes de enviar. Backend valida de novo e responde `422` se
  inválido.
- Nenhuma migration Room; o perfil é preferência local.
- O request continua compatível: clientes antigos enviam `null`.

### Ajustes determinísticos do fallback local

Calcule `bmi = bodyWeightKg / (heightCm / 100.0) ** 2`.

| IMC | Descanso | RPE | Séries |
|---|---|---|---|
| `< 18.5` | `+15s` | `-0.5` (mín. 5.0) | sem mudança |
| `18.5 – 24.9` | sem mudança | sem mudança | sem mudança |
| `25.0 – 29.9` | `+15s` | sem mudança | sem mudança |
| `>= 30.0` | `+30s` | sem mudança | `-1` (mín. 2) |

Aplicar apenas em `build_local_plan`, depois dos valores por objetivo
(`REST_BY_GOAL`, `RPE_BY_GOAL`, `SETS_BY_GOAL`). A IA recebe o perfil no prompt e
pode calibrar de forma própria.

### Sugestão inicial de carga (texto, sem schema novo)

Quando `bodyWeightKg` existir e o equipamento for `barbell`, `dumbbell`,
`machine` ou `cable`:

- região superior (`chest`, `shoulders`, `biceps`, `triceps`, `lats`,
  `middle back`, `traps`): `0.25 * bodyWeightKg`;
- região inferior (`quadriceps`, `hamstrings`, `glutes`, `calves`):
  `0.5 * bodyWeightKg`;
- arredondar para múltiplo de `2.5 kg`, mínimo `2.5`;
- acrescentar ao `notes` do exercício:
  `Carga inicial sugerida: X kg (ajuste conforme percepção).`

Se o equipamento for `body only`, `bands` ou `kettlebells`, não sugerir carga.
Não sobrescrever `notes` existente: concatenar com espaço.

### Prompt da IA

Extrair `build_user_prompt(request) -> str` em `back/app/main.py` para ser
testável. O prompt deve conter, quando houver perfil:

```
- Peso corporal: 82.5 kg
- Altura: 178 cm
- IMC: 26.0
- Use o perfil para calibrar descanso, RPE, volume e cargas iniciais.
  Sugestões de carga são ponto de partida, não prescrição médica.
```

Sem perfil, não mencionar peso/altura.

## Regras e casos de borda

1. Peso `0`, negativo, `NaN` ou fora de 30–300 → erro de validação; não envia.
2. Altura `0`, negativa ou fora de 120–250 → erro de validação; não envia.
3. Campo vazio → `null` (válido; gera sem perfil).
4. Um campo preenchido e o outro vazio → salva o preenchido, mas não aplica
   ajuste de IMC; o resumo mostra “perfil incompleto”.
5. Preferências antigas sem as chaves → `null`, sem crash.
6. Valores decimais usam vírgula ou ponto na UI; normalizar para ponto antes de
   converter (`82,5` → `82.5`).
7. Backend sem perfil → plano idêntico ao atual (regressão zero).
8. `notes` já preenchido pela IA → sugestão de carga é concatenada, não
   substituída.
9. IMC extremo (ex.: 300 kg / 120 cm) não deve gerar `sets < 2` nem `rpe < 5`.
10. A UI não bloqueia a geração por perfil ausente.

## Critérios de aceite

1. **Dado** que o usuário digita 82,5 kg e 178 cm e gera, **quando** o request
   sai, **então** `bodyWeightKg = 82.5` e `heightCm = 178`.
2. **Dado** que o app é reiniciado, **quando** abre a tela de geração, **então**
   os campos vêm preenchidos com os valores salvos.
3. **Dado** peso 20 kg, **quando** tenta gerar, **então** vê erro inline e o
   request não é enviado.
4. **Dado** um request sem perfil, **quando** o backend monta o plano local,
   **então** descanso/RPE/séries são iguais aos atuais.
5. **Dado** IMC 31, **quando** o backend monta o plano local, **então** cada
   exercício tem `+30s` de descanso e uma série a menos (mínimo 2).
6. **Dado** IMC 17, **quando** monta o plano local, **então** `+15s` de descanso
   e RPE `-0.5` (mínimo 5.0).
7. **Dado** peso 80 kg, **quando** monta um exercício de barra para pernas,
   **então** `notes` contém `Carga inicial sugerida: 40.0 kg`.
8. **Dado** peso 80 kg e exercício de peso corporal, **então** não há sugestão de
   carga.
9. **Dado** peso 82,5 e altura 178, **quando** o prompt é montado, **então** ele
   contém os três valores (peso, altura, IMC).

## Testes a criar

| Arquivo | Testes |
|---|---|
| `app/src/test/java/com/treinos/app/ui/ProfileValidatorTest.kt` | `aceita_peso_e_altura_validos`, `rejeita_peso_fora_da_faixa`, `rejeita_altura_fora_da_faixa`, `campo_vazio_vira_null`, `aceita_virgula_decimal`, `perfil_incompleto_e_valido` |
| `back/tests/test_profile_adjustments.py` | `test_sem_perfil_nao_altera_plano`, `test_imc_baixo_aumenta_descanso_reduz_rpe`, `test_imc_alto_aumenta_descanso_reduz_series`, `test_sugestao_carga_superior`, `test_sugestao_carga_inferior`, `test_peso_corporal_sem_sugestao`, `test_prompt_inclui_perfil`, `test_limites_de_validacao` |

## Verificação manual

1. Ajustes/Gerar: digite 82,5 kg e 178 cm; gere um treino.
2. Confira no backend (`docker compose logs`) que o request tem os valores.
3. Veja o resumo do perfil antes de gerar e o plano com descanso ajustado.
4. Reinicie o app e confirme que os campos persistem.
5. Apague os campos, gere e confirme que o plano volta ao comportamento atual.
6. Digite 10 kg / 300 cm e confirme o erro inline.

## Comando de verificação

```bash
./scripts/verify.sh
```

## Riscos e rollback

- **Interpretação médica:** a UI deve deixar claro que a sugestão é ponto de
  partida; não prometer precisão de carga.
- **Regressão do fallback:** testes sem perfil garantem que o plano atual não
  muda.
- **Compatibilidade:** campos opcionais com default `null`; app antigo continua
  funcionando.
- **Rollback:** remover os campos do request/prompt e as chaves de preferência;
  nenhum dado existente é migrado.
