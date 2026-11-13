# H3-58 — Notificação do fim do descanso com o app em segundo plano

## Status

| Campo | Valor |
|---|---|
| Item do roadmap | `H3-58` |
| MoSCoW | `C` |
| Spec aprovada em | `pendente de aprovação` (proposta de 2026-11-13) |
| Implementação | `pendente` |

**Origem:** fora de escopo declarado da H3-26 (que cobriu vibração/beep com
o app em primeiro plano, keepScreenOn e wall-clock) + varredura de polimento
de 2026-11-13. Quem sai do app durante o descanso não é avisado quando o
timer zera.

## Objetivo

Com o app em segundo plano e um descanso ativo, o usuário recebe uma
notificação local que mostra o tempo restante e muda para "Descanso
concluído" quando o timer zera.

## Fora de escopo

- Foreground service (o timer continua sendo wall-clock do processo vivo).
- AlarmManager/agendamento fora do processo: se o processo morre, não há
  notificação — mesma fronteira da H3-26.
- Som/vibração custom na notificação (usa o padrão do canal).
- Wear.

## Arquivos e símbolos que devem mudar

| Arquivo | Mudança |
|---|---|
| `app/src/main/java/com/treinos/app/notify/RestNotifier.kt` (novo) | canal "Descanso", postar/atualizar/cancelar a notificação; política pura extraída para teste |
| `app/src/main/AndroidManifest.xml` | `<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />` |
| `app/src/main/java/com/treinos/app/MainActivity.kt` | pedir `POST_NOTIFICATIONS` (API 33+) quando um treino com descanso começar; informar ao VM entrar/sair de primeiro plano |
| `app/src/main/java/com/treinos/app/ui/AppViewModel.kt` | observação do restante do descanso (padrão da H3-26) + flag de background → decide postar/atualizar/cancelar |
| `app/src/main/java/com/treinos/app/TreinosApplication.kt` | criar o canal no startup |
| `app/src/main/res/values/strings.xml` + `values-en/strings.xml` | título/texto da notificação |
| `app/src/test/java/com/treinos/app/ui/RestNotifierPolicyTest.kt` (novo) | política pura |

## Modelo de dados / contrato

Canal `rest_timer` (IMPORTANCE_DEFAULT). Notificação com id fixo
(`REST_NOTIFICATION_ID`) atualizada in place: "Descanso: X s restantes" ao
ir para segundo plano → "Descanso concluído — próxima série" ao zerar.
Ação opcional "Pular descanso" via PendingIntent para o mesmo fluxo do botão
da tela.

## Regras e casos de borda

- Posta/atualiza somente quando: descanso ativo + app em segundo plano +
  permissão concedida.
- Não há tick por segundo na notificação: posta ao ir para background com o
  restante atual e atualiza exatamente uma vez, ao zerar.
- Ao voltar para o primeiro plano, cancela imediatamente.
- Pular/estender/finalizar limpa a notificação (mesmos caminhos da H3-26).
- Permissão negada: nada aparece e nenhum aviso extra é mostrado.
- Processo morto em background: nada (fronteira documentada).

## Critérios de aceite

1. **Dado** descanso de 90 s, **quando** o app vai para segundo plano com
   permissão, **então** a notificação aparece com o restante; ao zerar,
   muda para "Descanso concluído".
2. **Dado** permissão negada, **quando** o app vai para segundo plano,
   **então** nada aparece.
3. **Dado** notificação visível, **quando** voltar ao app ou pular o
   descanso, **então** ela some.
4. **Dado** descanso encerrado com o app em primeiro plano, **quando**
   finalizar, **então** nenhuma notificação é postada.

## Testes a criar

| Arquivo | Testes |
|---|---|
| `app/src/test/java/com/treinos/app/ui/RestNotifierPolicyTest.kt` | `posta_so_em_background_com_descanso`, `zera_para_concluido_uma_vez`, `cancela_ao_voltar_ou_pular`, `sem_permissao_nada` |

## Verificação manual

1. Treino com descanso → sair do app (Home do sistema) → conferir
   notificação → voltar → some.
2. Negar a permissão em Ajustes → repetir → nada aparece.
3. Pular o descanso com o app em background → notificação some.

## Comando de verificação

```bash
./scripts/verify.sh
```

## Riscos e rollback

Permissão runtime nova (API 33+): pedir em contexto (início do treino), não
no startup. OEMs agressivos podem matar o processo — fronteira documentada.
Rollback: `git revert`; sem dados.
