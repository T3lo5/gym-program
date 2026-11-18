# H5-3 — Deploy do backend sob demanda

## Status

| Campo | Valor |
|---|---|
| Item do roadmap | `H5-3` |
| MoSCoW | `S` |
| Versão alvo | `Horizon 5` |
| Spec aprovada em | `2026-11-18` |
| Implementação | `concluída em 2026-11-18` |
| Depende de | H3-4 |

## Objetivo

Permitir ligar o backend só quando necessário em Oracle, Codespaces ou outro ambiente pessoal gratuito, com URL remota segura e recuperação simples.

## Fora de escopo

- Alta disponibilidade ou autoscaling comercial.
- Criação automatizada da conta Oracle.
- Domínio próprio ou certificado gerenciado pelo app.
- Migração automática de dados para provedor externo.
- Modo SaaS multiusuário.

## Arquivos e símbolos que devem mudar

| Arquivo | Mudança |
|---|---|
| `docs/DEPLOY-ON-DEMAND.md` | Processo operacional de Oracle/Codespaces/Cloudflare/Tailscale, wake, sleep e backup. |
| `docker-compose.yml` | Healthcheck, persistência, API token e política de restart declarados. |
| `back/.env.example` | Variáveis públicas/remotas, expiração de sessão e orçamento do worker. |
| `back/app/main.py` | Startup coerente com banco persistente e health público mínimo. |
| `app/src/main/java/com/treinos/app/data/prefs/AppPreferences.kt` | Modos local/LAN/remoto e URL remota separada da LAN. |
| `app/src/main/java/com/treinos/app/ui/screens/SettingsScreen.kt` | Teste de conexão, última disponibilidade e orientação de modo remoto. |
| `scripts/deploy-*.sh` | Scripts opcionais de inicialização/parada quando houver procedimento reproduzível. |

## Modelo de dados / contrato

```text
GET /health
200 {
  "status": "healthy",
  "timestamp": "<ISO>",
  "model": "<model>",
  "catalogSize": <n>
}

Variáveis operacionais:
  API_TOKEN=<obrigatório em URL pública>
  PUBLIC_BASE_URL=<https://...>
  DATABASE_PATH=<arquivo persistente>
  AI_TIMEOUT_TOTAL_SECONDS=<segundos>
  INSTANCE_MODE=<local|lan|remote>
```

Um deploy público deve falhar no startup se `PUBLIC_BASE_URL=https://...` e `API_TOKEN` estiver vazio.

## Regras e casos de borda

- Backend local pode rodar sem token.
- Backend acessível publicamente exige HTTPS e token.
- Health não exige token, mas não revela rotas, banco, chaves ou dados.
- URL remota com barra final é normalizada.
- O app mantém URL LAN/local separada da URL remota.
- Falha DNS/timeout mantém dados locais e oferece retry.
- Reinício do backend não apaga `back/data/treinos.db`.
- Instância substituída sem volume persistente recebe aviso antes de ser usada.
- Codespaces sem túnel ativo não é considerado conectado.

## Critérios de aceite

1. **Dado** deploy com URL pública e token vazio, **quando** o processo inicia, **então** ele falha com mensagem clara.
2. **Dado** URL HTTPS configurada e token correto, **quando** o app testa conexão, **então** recebe `/health` 200.
3. **Dado** túnel desligado, **quando** o app gera ou sincroniza, **então** recebe erro acionável sem apagar dados locais.
4. **Dado** reinício do Docker, **quando** o volume está montado, **então** SQLite e jobs persistem.
5. **Dado** execução local sem API token, **então** LAN continua funcionando.

## Testes a criar

| Arquivo | Testes |
|---|---|
| `back/tests/test_deploy_config.py` | `test_public_url_requires_token`, `test_local_url_allows_empty_token`, `test_database_path_is_persistent`, `test_health_redacted` |
| `app/src/test/java/com/treinos/app/data/prefs/BackendSelectionTest.kt` | `remote_fallback_preserved`, `lan_selection_preserved`, `invalid_remote_url_rejected` |

## Verificação manual

1. Subir o backend localmente e confirmar funcionamento sem token.
2. Configurar URL pública HTTPS e token, abrir túnel e testar `/health` e geração.
3. Encerrar o túnel e confirmar fallback seguro no app.
4. Reiniciar o container e confirmar dados persistidos no volume.
5. Validar backup da pasta `back/data` antes de apagar uma instância.

## Comando de verificação

```bash
./scripts/verify.sh
```

## Riscos e rollback

- URL pública gratuita pode mudar; o app precisa permitir atualização manual.
- Cloudflare Tunnel não substitui uma autenticação forte.
- A conta cloud pode interromper o volume; backup de `treinos.db` é obrigatório.
- Reverter o deploy não altera os dados locais do celular.
