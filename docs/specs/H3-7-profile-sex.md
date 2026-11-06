# H3-7 — Sexo opcional no perfil

## Status

| Campo | Valor |
|---|---|
| Item do roadmap | `H3-7` |
| MoSCoW | `S` |
| Spec aprovada em | `2026-11-06` (proposta de 2026-11-05; aprovada pelo dono do projeto com o escopo das decisões acima) |
| Implementação | `concluída` em 2026-11-06 — gate `./scripts/verify.sh` verde |
| Depende de | H1-5 (peso e altura); H3-6 (perfil no backup v3) |

## Decisões de execução (registradas na implementação)

- **Backup incluído.** A tabela de arquivos não listava `BackupDtos.kt`,
  `BackupValidator.kt`, `WorkoutRepository.kt` nem `RestoreContracts.kt`
  (`ProfileStore`), mas a regra 11 e a verificação manual 5 exigem `sex` no
  bloco `profile` com rejeição de valor fora do enum. Esses 4 arquivos entraram.
- **`sex` só no `build_user_prompt`.** O caminho que o app usa hoje (`modalities`
  preenchidas) cai em `build_hybrid_user_prompt`, que já não leva peso/altura/IMC
  desde a H1-5. Mudar isso seria escopo além da spec; fica como follow-up
  candidato (o campo viaja no request, mas não alcança a IA no plano híbrido).
- **Rótulo "Outro" em vez de "Outro / prefiro escrever".** A regra 3 proíbe
  texto livre; um rótulo que promete "prefiro escrever" mentiria para a UI.
  `normalizeSex` aceita as duas grafias.
- **Semântica de import.** `profile` de v3 sem a chave `sex` decodifica como
  `null` e, como peso/altura na H3-6, é lido como "não informado" — o valor
  salvo é limpo. Distinguir chave ausente de `null` explícito exigiria mexer no
  decoder; o custo (reaparecer dado sensível que o usuário apagou) seria pior.
- **`undisclosed` vai no prompt** como "prefiro não informar" (leitura literal da
  regra da spec: a linha aparece quando `sex` existir).
- **Sem texto interpretando a escolha** na UI, além do resumo "Sexo: <rótulo>"
  que repete a seleção salva (critério 9).

## Objetivo

Deixar o usuário informar sexo, opcionalmente, para que a IA calibre o plano com
uma informação que hoje ela não tem — sem transformar o app em prescrição médica
e sem mudar nenhuma regra determinística que já existe.

## Decisão de escopo (aprovada)

Sexo entra **só** no prompt da IA e na UI de perfil. Ficam **intactas**:

- a tabela de IMC → descanso/RPE/séries (`back/app/plan_builder.py:105-147`),
  definida por `bmi = peso / altura²`, que não depende de sexo;
- os coeficientes de sugestão inicial de carga 0,25×/0,5×
  (`back/app/plan_builder.py:150-179`).

**Por quê:** diferenciar essas regras por sexo exigiria coeficientes que ninguém
pode justificar neste projeto, e a H1-5 proíbe explicitamente "recomendação
médica" (linhas 21 e 177). Manter as regras determinísticas inalteradas também
preserva o "regressão zero" que `back/tests/test_profile_adjustments.py` já
garante hoje.

## Fora de escopo

- Nada de cálculo por sexo: IMC ajustado, gordura corporal, água diária, calorias,
  zona de frequência cardíaca, 1RM.
- Identidade de gênero: o campo é uma informação fisiológica de uso opcional e a
  redação da UI deve respeitar isso (ver regras).
- Histórico de peso/altura/sexo ao longo do tempo; continua sendo preferência
  única, como na H1-5.
- Mudança no schema Room, no `protocol/` (app↔relógio), nos DTOs de sync ou no
  banco do backend — o perfil não vive em nenhum desses lugares.
- Enviar sexo ao backend em `/v1/sync`; ele vai apenas no `POST
  /v1/generate-workout`.

## Arquivos e símbolos que devem mudar

| Arquivo | Mudança |
|---|---|
| `app/src/main/java/com/treinos/app/data/prefs/AppPreferences.kt` | `var sex: String?` com chave `sex` (`:87-88` já têm o padrão) |
| `app/src/main/java/com/treinos/app/ui/ProfileValidator.kt` | `SEX_VALUES` + `normalizeSex(text)`; `Result` ganha `sex` |
| `app/src/main/java/com/treinos/app/ui/screens/GenerateScreen.kt` | Campo de seleção em "Seu perfil (opcional)" (`:363`) e resumo (`:400-430`) |
| `app/src/main/java/com/treinos/app/ui/TreinosApp.kt` | Repassar `savedSex` (`:67-68`) e o valor escolhido (`:185-187`) |
| `app/src/main/java/com/treinos/app/ui/AppViewModel.kt` | `generatePlan` (`:452-508`) persiste e inclui no request |
| `app/src/main/java/com/treinos/app/data/remote/BackendDtos.kt` | `GeneratePlanRequest.sex: String? = null` ao lado de `:17-18` |
| `back/app/main.py` | `GenerateRequest.sex: Optional[Literal[...]]` (`:214-215`) + linha no `build_user_prompt` (`:404-431`) |
| `app/src/test/java/com/treinos/app/ui/ProfileValidatorTest.kt` | Normalização e valores inválidos |
| `back/tests/test_profile_adjustments.py` | Prompt com/sem sexo; **e** que o plano local não mudou |
| `docs/MANUAL.md` | §7 Segurança e privacidade: o que sai do aparelho |

Não edite `shared/` nem `com.treinos.shared/` (legado).

## Modelo de dados / contrato

```kotlin
// ProfileValidator.kt
val SEX_VALUES = setOf("male", "female", "other", "undisclosed")
fun normalizeSex(text: String?): String?   // rótulo da UI -> valor de contrato; null quando vazio/desconhecido

data class Result(valid: Boolean, error: String?, bodyWeightKg: Double?, heightCm: Int?, sex: String?)

// BackendDtos.kt
val sex: String? = null
```

```python
# back/app/main.py
sex: Optional[Literal["male", "female", "other", "undisclosed"]] = None
```

Rótulos da UI (pt-BR) → valores do contrato:

| Rótulo | Valor |
|---|---|
| Feminino | `female` |
| Masculino | `male` |
| Outro / prefiro escrever | `other` |
| Prefiro não informar | `undisclosed` |
| Campo vazio | `null` (não enviado) |

Prompt da IA, quando `sex` existir:

```text
- Sexo biológico: feminino
- Use o perfil para calibrar descanso, RPE, volume e cargas iniciais.
  Sugestões de carga são ponto de partida, não prescrição médica.
```

Sem sexo, a linha não aparece. As três linhas de peso/altura/IMC da H1-5 ficam
como estão.

## Regras e casos de borda

1. Omitir é o default. Ausente → `null` → request sem o campo → backend gera
   exatamente como hoje.
2. Nenhum valor de sexo produz erro de validação. Rótulo não reconhecido vira
   `null` (ou `other` se a UI permitir texto livre — ver regra 3), nunca "Peso
   inválido".
3. `other` e `undisclosed` são escolhas legítimas e devem estar disponíveis sem
   texto livre, para o campo não virar pergunta aberta sobre o corpo de ninguém.
4. Sexo não preenche nem limpa peso/altura: os três campos são independentes e a
   regra 4 da H1-5 (perfil incompleto continua válido) permanece.
5. Ajuste determinístico de IMC **não** considera sexo (decisão de escopo). Um
   request com `sex` e sem peso/altura não pode mudar plano algum.
6. `back/app/plan_builder.py` recebe `sex` e o ignora nas regras locais; a única
   mudança possível no plano local é zero.
7. Backend recebe valor fora do enum → `422` (Pydantic), igual às faixas de
   peso/altura da H1-5.
8. App antigo enviando sem `sex` → backend novo continua funcionando
   (`Optional`, default `None`).
9. Logs do backend não imprimem o campo; ele vai no prompt, não no log.
10. Privacidade: o campo sai do aparelho para o backend e chega à SenseNova como
    parte do prompt. Isso é divulgação a terceiro e deve estar descrito no
    `docs/MANUAL.md` §7 ao lado de peso/altura, com a opção "Prefiro não
    informar" mencionada.
11. Se a H3-6 já estiver implementada, `sex` entra no bloco `profile` do backup
    com default `null` — sem bump de `schemaVersion`, porque o decoder de v3 já
    tolera chave ausente e chave desconhecida.
12. `protocol/`, `wear/`, `SyncRequestDto`, `SyncDownloadDto` e o schema SQLite
    não ganham campo algum.

## Critérios de aceite

1. **Dado** que o usuário escolhe "Feminino" e gera, **quando** o request sai,
   **então** `sex = "female"`.
2. **Dado** que o app é reiniciado, **quando** abre a geração, **então** a
   seleção volta em "Feminino".
3. **Dado** que o usuário deixa o campo vazio, **então** `sex = null` e o JSON do
   request não afeta o comportamento atual.
4. **Dado** `sex = "female"` com peso e altura ausentes, **quando** o backend
   monta o plano local, **então** descanso/RPE/séries são idênticos aos de um
   request sem sexo.
5. **Dado** peso 82,5 e altura 178 com `sex = "male"`, **quando** o prompt é
   montado, **então** contém a linha de sexo junto de peso, altura e IMC.
6. **Dado** `sex = null`, **então** o prompt não menciona sexo.
7. **Dado** `sex = "qualquer-coisa"` no request, **então** o backend responde 422.
8. **Dado** um request antigo sem `sex`, **quando** recebido pelo backend novo,
   **então** 200 e plano igual ao atual.
9. **Dado** que o usuário escolhe "Prefiro não informar", **quando** gera, **então**
   `sex = "undisclosed"` e a UI não mostra nenhum texto interpretando a escolha.

## Testes criados

| Arquivo | Testes |
|---|---|
| `app/src/test/java/com/treinos/app/ui/ProfileValidatorTest.kt` | `normaliza_rotulos_de_sexo`, `sexo_vazio_vira_null`, `rotulo_desconhecido_vira_null`, `sexo_nao_invalida_peso_ou_altura`, `perfil_sem_sexo_continua_valido` |
| `back/tests/test_profile_adjustments.py` | `test_sexo_ignorado_no_plano_local`, `test_prompt_inclui_sexo`, `test_prompt_omite_sexo_null`, `test_sexo_fora_do_enum_da_422` |
| `app/src/test/java/com/treinos/app/data/remote/ContractTest.kt` | `request_sem_sexo_decode_ok`, `request_com_sexo_serializa` |
| `app/src/test/java/com/treinos/app/data/backup/BackupValidatorTest.kt` | `aceita_v3_com_sexo_valido`, `rejeita_sexo_fora_do_enum_no_perfil`, `aceita_v3_sem_sexo_informado` (regra 11) |
| `app/src/test/java/com/treinos/app/data/backup/BackupSerializationTest.kt` | `v3_round_trip_preserva_sexo`, `v3 sem chave sex decodifica null (sem bump de versao)` (regra 11) |
| `app/src/test/java/com/treinos/app/data/backup/BackupImportTest.kt` | `importa backup v3 restaura sexo do perfil`, `sexo invalido no backup e rejeitado sem alterar perfil` (verificação manual 5) |

`test_sexo_ignorado_no_plano_local` também afirma que `sex` não é parâmetro de
`profile_adjustments` nem de `suggest_initial_load`: se alguém calibrar carga por
sexo depois, o teste quebra em vez de virar promessa silenciosa.

## Verificação manual

**Status da implementação:** os 5 passos abaixo precisam de aparelho/emulador e
**não foram executados**. O que se pode provar sem dispositivo ficou nos testes
acima: 1 e 3 em `ContractTest`/`ProfileValidatorTest`, 4 e 5 no backend
(`test_sexo_ignorado_no_plano_local`, `test_sexo_fora_do_enum_da_422`), 2 na
chave de preferência lida por `GenerateScreen` (`savedSex`) + `BackupImportTest`,
5 (backup) em `BackupValidatorTest`/`BackupImportTest`. Falta conferir no
aparelho: persistência visual da seleção depois de reiniciar (critério 2) e o
comportamento 9 ("Prefiro não informar" sem texto interpretando).

1. Gere com "Feminino" + 82,5 kg/178 cm; confira o request no log do backend
   (`docker compose logs`) sem expor o valor — só a presença da chave.
2. Gere sem sexo e compare o plano local com o de antes da mudança: idêntico.
3. Reinicie o app e confirme a seleção persistida.
4. Escolha "Prefiro não informar" e confirme `undisclosed` no request.
5. Se a H3-6 estiver no ar: exporte, edite o JSON trocando `sex` por valor
   inválido, importe e confirme a rejeição.

## Comando de verificação

```bash
./scripts/verify.sh
```

## Riscos e rollback

- **Leitura determinista do sexo:** o risco é a UI ou o prompt sugerirem que sexo
  define capacidade. Mitigação: texto neutro, sem títulos de "objetivo ideal", e
  nenhuma mudança nas regras numéricas.
- **Sensibilidade do dado:** atributo mais sensível que peso/altura, enviado a um
  backend e a um provedor de IA. Mitigação: default é não informar, o campo é
  removível a qualquer momento, e o `docs/MANUAL.md` §7 passa a nomear os três
  campos que saem do aparelho.
- **Regressão no fallback:** os coeficientes não mudam, e
  `test_sexo_ignorado_no_plano_local` existe justamente para provar isso.
- **Rollback:** remover o campo do request/prompt e a chave de preferência.
  `sex` some do `profile` do backup sem afetar v3 (default `null`). Nenhum dado
  do usuário é migrado ou perdido.
