# H3-47 — UX frontend: formulários, diálogos e ações destrutivas

## Status

| Campo | Valor |
|---|---|
| Item do roadmap | `H3-47` |
| MoSCoW | `S` |
| Spec aprovada em | `2026-11-13` |
| Implementação | `concluída` |

**Origem:** pedido de criar a spec "UX FE" e achados da auditoria UX/UI de
2026-11-07 ([relatório](../auditoria-ux-ui-2026-11-07.md), §P2.4, P2.6, P2.9,
P3.2, P3.5, P4.9), priorizando lacunas de frontend ainda não cobertas por specs
concluídas.

**Relacionado (não duplicar):** estados/erro/retry gerais — [H3-29](H3-29-estados-erros.md);
fluxo de geração — [H3-30](H3-30-geracao-sem-atrito.md); estado sobrevivendo a
recriação — [H3-31](H3-31-estado-sobrevive.md); componentes/consistência visual —
[H3-32](H3-32-componentes-consistencia.md); acessibilidade dinâmica/contraste —
[H3-34](H3-34-acessibilidade-dinamica.md); organização/copy de Ajustes e
shortcut — [H3-35](H3-35-ajustes-copy.md) (pendente); navegação em pilha/bottom
bar — [H3-38](H3-38-navegacao-pilha.md) (pendente). Esta spec foca o
comportamento de **formulários e confirmações** no frontend, onde ainda há
atrito ou risco de ação acidental.

## Objetivo

Preencher e confirmar ações no app é previsível: campos mostram o que é esperado
e o que está errado no lugar certo, o teclado não esconde a ação principal, e
ações que apagam ou substituem dados pedem confirmação clara antes de executar.

## Fora de escopo

- Reestruturar Ajustes em seções, renomear jargão ou alterar o conteúdo textual
  amplo de Ajustes — [H3-35](H3-35-ajustes-copy.md).
- Mudar navegação, destinos, bottom bar ou origem/retorno — [H3-38](H3-38-navegacao-pilha.md).
- Catálogo de erros de rede/sync, mapeamento de exceções e retry persistente —
  [H3-29](H3-29-estados-erros.md).
- Lógica de geração (payload, fallback, jobs) — [H3-30](H3-30-geracao-sem-atrito.md)/H3-40.
- Novo visual/tema/motion ou redesign amplo de telas — [H3-32](H3-32-componentes-consistencia.md)/H3-33.
- Regras de domínio/validação de negócio já definidas (ex.: faixas da H3-27 para
  séries; contrato de import/restore) — esta spec trata da **apresentação e do
  fluxo de confirmação** no frontend, não de afrouxar validações.

## Arquivos e símbolos que devem mudar

| Arquivo | Mudança |
|---|---|
| `app/src/main/java/com/treinos/app/ui/screens/GenerateScreen.kt` | Campos com rótulo/placeholder/unidades claros; validação inline com `supportingText`/`isError` junto ao campo; seções/controles condicionais não induzirem configuração irrelevante; CTA principal alcançável com teclado/scroll (coerente com H3-30). |
| `app/src/main/java/com/treinos/app/ui/screens/LogActivityScreen.kt` | Rótulos e unidades explícitos (duração, distância, pace/esforço quando exibidos), erros inline no campo afetado, ação de salvar visível acima do teclado. |
| `app/src/main/java/com/treinos/app/ui/screens/RunWorkoutScreen.kt` | Campos de registro com unidades/ajuda e validação inline; confirmação ao sair/descartar com alterações não salvas quando aplicável. |
| `app/src/main/java/com/treinos/app/ui/screens/SettingsScreen.kt` | Toda ação destrutiva ou que substitui dados (ex.: importar/substituir backup, restaurar do backend/deviceId, exclusões equivalentes que existirem) exige `AlertDialog` de confirmação com resumo do efeito; botões destrutivos visualmente distinguidos do primário seguro. |
| `app/src/main/java/com/treinos/app/ui/screens/HomeScreen.kt` | Ações destrutivas (ex.: excluir plano complementar) mantidas fora do fluxo acidental: confirmação clara e, quando fizer sentido, alocadas em menu/overflow em vez de ação primária proeminente (respeitando decisões já tomadas na H3-28). |
| `app/src/main/java/com/treinos/app/ui/screens/HistoryScreen.kt` | Exclusão/edições com efeito de perda de registro confirmadas com contexto (o que será removido). |
| `app/src/main/java/com/treinos/app/ui/components/Common.kt` (ou componente dedicado) | Reuso de padrão de diálogo de confirmação destrutiva (título, mensagem de impacto, Cancelar + ação explícita) para evitar variação entre telas. |
| `app/src/test/java/com/treinos/app/ui/screens/GenerateScreenTest.kt` (Robolectric) | Validação inline e alcance/estado do CTA em cenários de erro. |
| `app/src/test/java/com/treinos/app/ui/screens/SettingsScreenTest.kt` (Robolectric) | Confirmação obrigatória antes de importação/restauração destrutiva. |
| `app/src/test/java/com/treinos/app/ui/screens/LogActivityScreenTest.kt` ou equivalente (Robolectric) | Erro inline no campo correto e submissão bloqueada quando inválido. |

> Lista indicativa: a implementação deve cobrir os pontos concretos encontrados
> no código e pode ajustar caminhos/nomes de teste ao padrão do módulo.

## Modelo de dados / contrato

Sem mudança de schema Room, backup, sync ou API. Regras de formulário expostas ao
usuário devem espelhar as validações já existentes no domínio/UI (ex.:
`ComplementaryValidator`, `ProfileValidator`, `validateSetInput`,
`validateActivityLog`, `RunLog`), sem criar segunda fonte de verdade divergente.

## Regras e casos de borda

- **Erro inline:** campo inválido mostra mensagem de apoio (`supportingText`) e
  estado de erro no próprio campo; o erro some ou atualiza quando o valor passa a
  ser válido. Não depender apenas de snackbar para erro de formulário.
- **Submit bloqueado:** ação de salvar/gerar/confirmar não prossegue com dados
  inválidos; o foco/scroll leva o usuário ao primeiro problema quando viável.
- **Teclado e rolagem:** telas/diálogos de formulário permanecem roláveis com
  `imePadding`/ajuste de insets para que o botão de confirmação não fique
  encoberto (reforço onde faltar, sem regredir H3-31/H3-34).
- **Confirmação destrutiva:** importar/substituir, restaurar dados remotos ou por
  código, e excluir plano/sessão/registro exigem diálogo com: o que será afetado,
  opção de cancelar (sem efeito) e ação de confirmação rotulada de forma explícita
  (nunca um toque único direto para a operação que apaga/substitui).
- **Estado ocupado:** enquanto uma ação assíncrona estiver em andamento, o gatilho
  correspondente usa estado ocupado/desabilitado consistente (`BusyButton`/padrão
  H3-32) para evitar duplo envio.
- **Campos condicionais:** controles específicos de uma modalidade/objetivo só
  aparecem ou só são exigidos quando relevantes (ex.: opções de força não devem
  bloquear um fluxo só de corrida, conforme regras já existentes).
- **Texto e unidade:** rótulos deixam explícita a unidade/formato esperado quando
  não for óbvio (ex.: duração, distância, esforço), em pt-BR, sem jargão técnico
  desnecessário (alinhado à direção da H3-35).

## Critérios de aceite

1. **Dado** um campo obrigatório ou fora da faixa em Generate/LogActivity/Run,
   **quando** o usuário tenta submeter, **então** o erro aparece inline no campo
   correspondente e a submissão não acontece.
2. **Dado** um formulário longo com teclado aberto, **quando** o usuário rola ou
   edita o último campo, **então** o botão de confirmar/salvar continua
   alcançável.
3. **Dado** a ação "Substituir tudo" na importação de backup, **quando** o
   usuário toca nela, **então** um `AlertDialog` descreve o impacto e só executa
   a substituição após confirmação explícita; cancelar não altera dados.
4. **Dado** uma restauração (backend ou por código de aparelho) que substitui
   dados locais, **quando** acionada, **então** há confirmação prévia com resumo
   do efeito.
5. **Dado** a exclusão de um plano complementar, sessão ou registro no histórico,
   **quando** o usuário solicita excluir, **então** a remoção só ocorre após
   confirmação; a ação não é disparada por toque único acidental no corpo da
   lista.
6. **Dado** uma ação de salvar/gerar em andamento, **quando** o usuário tenta
   tocar novamente, **então** o controle permanece em estado ocupado e não duplica
   a operação.

## Testes a criar

| Arquivo | Testes |
|---|---|
| `app/src/test/java/com/treinos/app/ui/screens/GenerateScreenTest.kt` | `campo_invalido_mostra_erro_inline`, `submit_bloqueado_com_erro`, `secao_forca_oculta_sem_modalidade_forca` (quando aplicável ao estado atual) |
| `app/src/test/java/com/treinos/app/ui/screens/LogActivityScreenTest.kt` (ou novo) | `erro_inline_no_campo_de_duracao_ou_distancia`, `salvar_desabilitado_ou_bloqueado_enquanto_invalido` |
| `app/src/test/java/com/treinos/app/ui/screens/SettingsScreenTest.kt` | `importar_exige_confirmacao`, `restaurar_backend_exige_confirmacao`, `cancelar_confirmacao_nao_substitui_dados` |
| `app/src/test/java/com/treinos/app/ui/screens/HomeScreenTest.kt` | `excluir_plano_complementar_exige_confirmacao` (reforço/regressão da H3-28) |
| `app/src/test/java/com/treinos/app/ui/screens/HistoryScreenTest.kt` | `excluir_registro_exige_confirmacao` |

## Verificação manual

1. Generate: deixar campo obrigatório inválido → erro inline e CTA sem efeito até
   corrigir; com teclado aberto, confirmar continua visível.
2. Registro de atividade/corrida: informar valor inválido → mensagem no campo;
   corrigir → mensagem some e salva.
3. Ajustes: iniciar importação e restauração → conferir que cada caminho
   destrutivo pede confirmação e que cancelar não altera nada.
4. Home/Histórico: acionar exclusão → apenas confirmar remove; cancelar mantém o
   item.

## Comando de verificação

```bash
./scripts/verify.sh
```

## Riscos e rollback

- **Sobreposição com H3-35/H3-38:** manter o escopo restrito a formulários e
  confirmações; se uma mudança entrar no território de Ajustes/navegação,
  coordenar com aquelas specs em vez de duplicar.
- **Regressão de fluxo:** diálogos extras adicionam um toque — aceitável para
  ações destrutivas, mas não aplicar a fluxos de alta frequência (ex.: salvar
  série). Cobrir com testes de UI para garantir que "cancelar" é sempre neutro.
- **Divergência de validação:** reutilizar validadores puros existentes para que
  a mensagem reflita a regra real.
- **Rollback:** mudanças localizadas em telas/componentes de UI, reversíveis sem
  migração de dados.
