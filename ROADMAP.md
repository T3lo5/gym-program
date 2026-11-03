# Roadmap — Treinos App

App pessoal de musculação Android + Wear OS, **local-first + IA sob demanda**, custo alvo R$ 0/mês.

---

## 1. Visão do produto

Um app que você usa **sem conta, sem login e sem internet** para treinar. Tudo fica no celular (Room/SQLite). O backend (FastAPI no seu PC/Codespaces/Oracle) só entra para:

- gerar um plano novo via IA (SenseNova) com fallback local;
- sincronizar um snapshot dos seus dados (quando você tocar em "Sincronizar agora").

O relógio (Wear OS) mostra o treino em andamento sem precisar olhar o celular.

---

## 2. Onde estamos hoje (estado atual)

| Área | Status |
|------|--------|
| Banco local (Room) | ✅ Planos, dias, exercícios, sessões, séries |
| Execução de treino | ✅ Timer de descanso, séries, RPE, carga, histórico |
| Geração via IA | ✅ Backend + SenseNova + fallback local |
| Sincronização | ✅ Snapshot manual app → backend |
| Descoberta LAN | 📋 mDNS no backend; app usa URL configurável |
| Wear OS | 🚧 Módulo existe, não no APK atual |
| Backup/restore do usuário | ❌ Não há exportação manual de dados |
| Imagem/vídeo de exercício | ❌ Catálogo sem mídia |
| Execução por séries (não circuito) | ❌ Fluxo obriga circuito sequencial |
| Recuperação de deviceId | ❌ Desinstalar = dados órfãos no backend |

---

## 3. Princípios que norteiam o roadmap

1. **Local-first:** o app funciona 100% offline; a nuvem é opcional.
2. **Zero conta:** nunca exigir login/cadastro para usar o core.
3. **Custo R$ 0:** backend roda no seu PC ou em tiers gratuitos (Oracle/Codespaces).
4. **Privacidade:** chave da SenseNova fica só no backend; dados pessoais não saem do aparelho salvo quando você sincroniza.
5. **UI/UX primeiro:** o app precisa ser rápido de usar entre séries (toques mínimos, alvos grandes, leitura à luz do sol no relógio).

---

## 4. Roadmap por horizonte

Use este quadro para decidir o que entra em cada sprint. Tudo abaixo está priorizado com MoSCoW (M = Must, S = Should, C = Could, W = Won't now).

### Horizon 1 — Agora (Próximas 2-4 semanas): estabilizar o core

| ID | Item | MoSCoW | Problema | Solução prevista | Esforço | Depende de | UI/UX |
|----|------|--------|----------|------------------|---------|------------|-------|
| H1-1 | Recuperação de backup / login por deviceId | **M** | Desinstalar/reinstalar gera novo `deviceId`; treinos no backend ficam órfãos. | Exibir `deviceId` nos Ajustes (copiar); permitir informar `deviceId` antigo ou login por e-mail para associar aparelho aos dados sincronizados. | 3 dias | `AppPreferences`, `WorkoutRepository.downloadSync`, `SettingsScreen` | Tela de Ajustes com campo visível, botão "Copiar deviceId", formulário "Restaurar por deviceId/e-mail" com feedback de sucesso/erro. |
| H1-2 | Acompanhamento por séries (não circuito) | **M** | Hoje obriga circuito sequencial; não dá marcar "série 1 de 4", descansar, ir para série 2 do mesmo exercício. | Nova tela de execução por exercício: controle de séries/reps ("1/4 séries", "8-12 reps"), temporizador de descanso entre séries, navega para próximo exercício só após concluir todas as séries do atual. | 5 dias | `WorkoutScreen` (nova), `WorkoutDao` (progresso por série), `WorkoutRepository` (sessão por exercício) | Timer grande e visível, botão "Próxima série" destacado, indicador visual de série atual/total, marcação de série concluída com check. |
| H1-3 | Imagens e/ou vídeos dos exercícios | **S** | Usuário não reconhece o exercício pelo nome (ex: "Bent-Arm Dumbbell Pullover"). | Campo `imageUrl` e `videoUrl` na entidade do exercício; exibir no detalhe do exercício e durante a execução. | 4 dias | `WorkoutPlanExerciseEntity`, `ExerciseDetailScreen` (nova), backend catálogo com mídia | Miniatura redonda no picker, imagem em tela cheia no detalhe, ícone de play sobre vídeo, carregamento progressivo com placeholder. |
| H1-4 | Exportar/importar backup JSON (manual) | **S** | Não há como o usuário salvar seus próprios dados fora do app. | Botão "Exportar backup" nos Ajustes → salva JSON local; "Importar backup" → lê arquivo e restaura no Room. | 3 dias | `WorkoutRepository`, `SettingsScreen` | Seletor de arquivo do sistema, confirmação antes de sobrescrever, toast de sucesso com contagem de itens restaurados. |
| H1-5 | Peso e altura na geração de treino | **S** | A IA gera o treino "no pelo": não sabe peso nem altura do usuário, então não consegue calibrar cargas iniciais, RPE, séries ou descanso de forma personalizada. | App: campos **Peso** e **Altura** na tela de geração (salvos em `AppPreferences`, enviados em `GeneratePlanRequest`). Backend: incluir peso/altura no `user_prompt` e no `SYSTEM_PROMPT`, e usá-los no `build_local_plan` para sugerir cargas/descanso baseados no perfil. | 3 dias | `GenerateScreen`, `GeneratePlanRequest`, `AppPreferences`, `back/app/main.py`, `back/app/plan_builder.py` | Campos de número com unidades (kg, cm) na tela de geração, resumo do perfil exibido antes de gerar, validação de valores realistas. |

### Horizon 2 — Próximo (1-2 meses): conectividade e relógio

| ID | Item | MoSCoW | Problema | Solução prevista | Esforço | Depende de | UI/UX |
|----|------|--------|----------|------------------|---------|------------|-------|
| H2-1 | Descoberta LAN automática (mDNS) | **S** | Usuário precisa digitar IP:porta do backend no celular. | Backend anuncia `_treinos._tcp.local`; app lista instâncias encontradas e conecta com 1 toque. Fallback mantém IP manual. | 3 dias | `BackendDiscoveryManager`, `BackendClient`, tela de configuração de backend | Lista de backends encontrados na rede com "Conectar", estado de carregamento, aviso quando nenhum é encontrado. |
| H2-2 | Wear OS MVP (Data Layer) | **S** | Relógio não está no APK; usuário precisa do celular para ver o treino. | Módulo Wear incluído no APK; Data Layer envia sessão (exercício atual, série, próxima, timer descanso); tela do relógio com exercício atual, timer de descanso grande, próximo exercício. | 5 dias | `wear` module, Compose for Wear OS, Data Layer API | Alvos ≥48dp, timer grande, vibração no fim do descanso, cores de alto contraste para luz solar. |
| H2-3 | Sincronização bidirecional completa (download/restauração) | **S** | Hoje só envia app → backend; não restaura do backend para o app. | Endpoint de download no backend; app restaura planos/séries do backend após login por deviceId. | 3 dias | H1-1, `WorkoutRepository.downloadSync` | Tela de sincronização com progresso, "Última sincronização", botão "Restaurar do backup na nuvem". |
| H2-4 | Histórico com calendário e métricas | **C** | Histórico é uma lista simples; não dá ver evolução. | Visualização por calendário, cards de treinos passados, total de séries, duração, volume por músculo. | 4 dias | `HistoryScreen`, Room queries | Calendário com dias treinados destacados, cards expansíveis, gráfico simples de volume semanal. |

### Horizon 3 — Depois (2-4 meses): evolução e qualidade

| ID | Item | MoSCoW | Problema | Solução prevista | Esforço | Depende de | UI/UX |
|----|------|--------|----------|------------------|---------|------------|-------|
| H3-1 | Testes (unit + UI + instrumented) + CI | **M** | Sem testes automatizados; regressões passam despercebidas. | Testes de ViewModel/UseCase, Compose UI testing, testes Room instrumentados; GitHub Actions: lint + test + build APK + Wear APK. | 5 dias | Módulos `app` e `wear` estáveis | Nenhuma UI nova; qualidade percebida via estabilidade. |
| H3-2 | Polimento visual e animações | **C** | Telas funcionais mas sem identidade visual refinada. | Animações Compose (SharedElement, AnimatedVisibility), transições entre telas, tema verde consistente, estados de loading e empty states. | 4 dias | `Theme.kt`, telas existentes | Micro-animações ao concluir série, transições suaves entre dias/exercícios, empty states ilustrados. |
| H3-3 | Acessibilidade (A11y) | **C** | Sem contentDescription, scaling de fonte, TalkBack nas telas principais. | contentDescription em todos os botões/ícones, suporte a escala de fonte do sistema, navegação TalkBack nas 4 tabs principais. | 3 dias | Telas existentes | App usável por leitores de tela, texto redimensionável sem quebrar layout. |
| H3-4 | Backend na nuvem grátis (Oracle/Codespaces) | **W** | Backend só roda no PC local; não acessa de fora da LAN. | Deploy em Oracle Always Free ou Codespaces + Cloudflare Tunnel; toggle "Usar back remoto" + URL armazenada com segurança. | 2 dias | Backend Docker, `.env` seguro | Toggle nos Ajustes, campo de URL remota, aviso de segurança para uso fora da LAN. |
| H3-5 | Periodização e progressão de carga | **W** | Plano é estático; não há evolução automática de carga/séries. | Mesociclos, sugestão de aumento de carga com base em RPE/séries concluídas, data de revisão do plano. | 5 dias | Histórico completo, H2-4 | Tela de "Progressão" com sugestões de próxima carga, alerta "Chegou a hora de revisar o plano". |

---

## 5. Priorização consolidada (MoSCoW)

| Must Have (agora) | Should Have (próximo) | Could Have (depois) | Won't now |
|-------------------|-----------------------|---------------------|-----------|
| Recuperação deviceId / login | Descoberta LAN mDNS | Testes + CI | Backend na nuvem |
| Execução por séries (não circuito) | Wear OS MVP | Polimento + animações | Periodização automática |
| Imagens/vídeos dos exercícios | Sync bidirecional (download) | Acessibilidade (A11y) | Gráficos avançados |
| Exportar/importar backup JSON | Histórico com calendário | | Export CSV/Sheets |
| Peso e altura na geração | | | Widget/complicação do relógio |

---

## 6. Esforço e caminho crítico

```
Semana 1-2: H1-1 (deviceId) → desbloqueia tudo de sincronização/backup
Semana 2-3: H1-2 (séries)    → core de execução usável no dia a dia
Semana 3-4: H1-3 (mídia)     → UX de reconhecimento de exercício
Semana 3-4: H1-4 (backup JSON) → segurança de dados do usuário
Semana 3-4: H1-5 (peso/altura) → IA calibra plano ao perfil físico
Mês 2:      H2-1 (LAN) + H2-2 (Wear) → conectividade + relógio
Mês 2-3:    H2-3 (sync bidirecional) → restauração real na nuvem
Mês 3+:     H3-1 (testes/CI) + H3-2 (polimento) + H3-3 (A11y)
```

**Caminho crítico:** H1-1 → H1-2 → H2-1 → H2-2 → H3-1.  
Não comece polimento (H3-2) antes de ter testes (H3-1), senão regressões mascaram o progresso.

---

## 7. Critérios de aceite gerais (Definição de Pronto)

- [ ] Código no `main` via PR aprovado, sem `// TODO` bloqueante
- [ ] `./gradlew :app:lintDebug :app:test :app:connectedAndroidTest` verde
- [ ] APK instalado no device físico + Wear APK no relógio (quando aplicar)
- [ ] Smoke test manual da funcionalidade entregue
- [ ] README/MANUAL/ROADMAP atualizados com o novo estado

---

## 8. Riscos e mitigação (resumo)

| Risco | Prob. | Impacto | Mitigação |
|-------|-------|---------|-----------|
| SenseNova muda API/limite | Baixa | Médio | Backend agnóstico (OpenAI-compatible); fallback local em 1 commit |
| BT instável celular ↔ relógio | Média | Médio | Relógio opera autônomo com fila local; reconcilia ao reconectar |
| Android 15+ restringe LAN | Baixa | Alto | Manter fallback manual de IP; testar em preview |
| Desinstalar app apaga dados locais | Alta | Alto | H1-4 (backup JSON manual) + H1-1 (recuperação deviceId) |
| Catálogo sem mídia | Média | Baixo | Imagens via URL do free-exercise-db; vídeo opcional |

---

## 9. Próximo passo imediato

Iniciar **H1-1 (Recuperação de backup / login por deviceId)** — é o item de maior prioridade porque desbloqueia backup, sincronização e restore seguro sem perder os dados do usuário quando ele reinstala o app.

Quer que eu detalhe o plano de execução de um item específico (arquivos, tarefas, critérios de aceite) ou abra as issues no repositório para cada item do Horizon 1?
