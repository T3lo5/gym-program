# Backend sob demanda

Este guia prepara o backend para rodar só quando necessário. Não cria conta
Oracle, Codespaces, Cloudflare ou túnel: esses recursos dependem da
configuração pessoal que ainda não existe.

## Configuração mínima

```bash
cp back/.env.example back/.env
```

Para execução local/LAN:

```dotenv
MDNS_ENABLED=true
API_TOKEN=
PUBLIC_BASE_URL=
INSTANCE_MODE=local
```

Para URL pública HTTPS:

```dotenv
API_TOKEN=<token-aleatorio-32-caracteres-ou-mais>
PUBLIC_BASE_URL=https://exemplo.tunel.cloudflare.com
INSTANCE_MODE=remote
```

O backend falha no startup quando `PUBLIC_BASE_URL` começa com `https://` e
`API_TOKEN` está vazio. `/health` permanece público, mas não retorna caminho do
banco, token ou chave da IA.

## Docker local

```bash
docker compose up -d treinos-backend
docker compose ps
curl http://localhost:3000/health
docker compose restart treinos-backend
```

Os dados persistem em `back/data/treinos.db`. Faça backup antes de trocar de
máquina ou volume:

```bash
docker compose stop treinos-backend
cp back/data/treinos.db back/data/treinos.db.backup
docker compose start treinos-backend
```

## Wake/sleep

### Oracle/cloud pessoal já existente

1. Ligue a instância apenas para gerar/sincronizar.
2. Suba o container com Docker Compose.
3. Configure o túnel ou proxy HTTPS.
4. Configure a URL HTTPS e o mesmo token em Ajustes.
5. Pare o container ou a instância depois do uso.

Não adicionar fallback automático para o backend remoto: falha de rede mantém o
app e os dados locais intactos.

### Codespaces

1. Abra o Codespace.
2. Rode `docker compose up -d treinos-backend`.
3. Exponha a porta 3000 somente como HTTPS privado/publico com proteção.
4. Use a URL gerada no app.
5. Feche o Codespace e pare o container antes de desistir da sessão.

Codespace sem túnel ativo não aparece como backend conectado.

### Cloudflare Tunnel

1. Configure o túnel apontando para `http://127.0.0.1:3000`.
2. Defina `PUBLIC_BASE_URL` com a URL HTTPS do túnel.
3. Defina `API_TOKEN` forte e reinicie o backend.
4. Teste sem token:

```bash
curl -i https://exemplo.tunel.cloudflare.com/v1/sync/status
```

5. Teste com token:

```bash
curl -i -H "Authorization: Bearer SEU_TOKEN" \
  https://exemplo.tunel.cloudflare.com/v1/sync/status
```

O Health não exige token. Todo `/v1/*` exige Bearer quando `API_TOKEN` está ativo.

### Tailscale

1. Instale Tailscale apenas nos dispositivos confiáveis.
2. Use o endereço HTTPS configurado pelo próprio ambiente.
3. Defina o mesmo `API_TOKEN`.
4. Se o Tailnet/Tailscale estiver desligado, o app mantém o modo local.

Tailscale não substitui o Bearer token em instâncias publicamente acessíveis.

## App

Em Ajustes → Sincronização:

- **Rede local:** URL HTTP da LAN ou emulador.
- **Remoto:** URL HTTPS e o mesmo `API_TOKEN`.
- Desativar remoto volta imediatamente para a URL local.
- Erro HTTP 401 significa token inválido ou ausente.
- Erro de rede não apaga planos, sessões ou medidas locais.

## Segurança e rollback

- Nunca commitar `back/.env` ou o token.
- Rotacionar o token se ele vazar.
- Backup manual de `back/data/treinos.db` antes de replace, migração ou mudança
  de provedor.
- Reverter o modo remoto não apaga os dados locais.
