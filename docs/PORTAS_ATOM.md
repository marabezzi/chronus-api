# Registro de portas ATOM — 2026-11-07

Esta aplicação reserva portas **exclusivas no host** para coexistir com outros sistemas ATOM no mesmo computador. Portas **internas** de contêineres isolados podem se repetir.

## Este repositório

API Spring Boot nativa e contêiner em 8113. Variável SERVER_PORT configura o processo e CHRONUS_API_PORT a porta externa do Compose; em produção, alinhe as duas quando mudar a interna.

## Verificação no computador

```bash
docker ps --format 'table {{.Names}}\t{{.Ports}}'
sudo ss -ltnp
```

Não execute stacks simultâneos com mapeamentos coincidentes. Revise os arquivos `.env` locais: valores definidos neles prevalecem sobre padrões do repositório.

**HTTPS em produção:** apenas um gateway/proxy deve ocupar 80/443 no mesmo IP; mantenha os ingressos existentes até configurar um proxy compartilhado. Alterar portas públicas pode invalidar desafios ACME/Let's Encrypt e requer homologação antes do merge.
