# Cross Border API

Serviço de remessa entre usuários PF e PJ: converte Real em Dólar com a cotação oficial do Banco Central e transfere o valor convertido, tudo em uma única transação.

**Java 21 · Spring Boot 4 · Maven · H2 em memória**

```bash
./mvnw spring-boot:run    # sobe em http://localhost:8080
./mvnw verify             # roda os testes e gera o relatório de cobertura
docker build -t cross-border-api:local . && docker run --rm -p 8080:8080 cross-border-api:local
```
Swagger: http://localhost:8080/swagger-ui.html

---

## Como rodar

Requisito: **JDK 21** (`java -version`). No Windows, use `mvnw.cmd`.

```bash
./mvnw spring-boot:run
```

Ou gerando o jar:

```bash
./mvnw clean package                              # compila e roda os testes (-DskipTests para pular)
java -jar target/crossborder-api-0.0.1-SNAPSHOT.jar
```

Ou com Docker (não precisa de JDK instalado):

```bash
docker build -t cross-border-api:local .
docker run --rm -p 8080:8080 cross-border-api:local
```

| O quê | Onde |
|---|---|
| API | http://localhost:8080 |
| Swagger UI | http://localhost:8080/swagger-ui.html |
| Health check | http://localhost:8080/actuator/health |
| Console do H2 | http://localhost:8080/h2-console — JDBC URL `jdbc:h2:mem:crossborderdb`, usuário `sa`, senha vazia |

> A aplicação também abre o servidor TCP do H2 na porta **9092** (conveniência de desenvolvimento, para consultar o banco pelo terminal). Se essa porta estiver ocupada, desligue com `--app.h2-tcp-server.enabled=false`
> (`java -jar ... --app.h2-tcp-server.enabled=false` ou `./mvnw spring-boot:run -Dspring-boot.run.arguments=--app.h2-tcp-server.enabled=false`).

## Como rodar os testes

```bash
./mvnw verify
```

Além dos testes, gera o relatório de cobertura (JaCoCo) em `target/site/jacoco/index.html`: cerca de **97% das linhas** e **91% dos branches**. Só o `./mvnw test` roda os testes, sem o relatório.

Nenhum teste chama o BCB de verdade. O que está coberto:

- **Endpoints (integração ponta a ponta)**: os 12 endpoints via MockMvc, passando por controller, service e H2 real, com só o BCB simulado. Cobre sucesso e erros (`400`, `404`, `409`, `422`), limites PF/PJ, idempotência e fallback de cotação. O **rollback** é testado forçando uma falha *depois* do débito e do crédito (ao gravar a remessa e o lançamento): o saldo volta, nada fica gravado e a mesma chave pode ser reenviada.
- **Regras de negócio (Mockito)**: cadastro e depósito, remessa (validações, conversão, débito/crédito, ledger, limite diário, idempotência), cotação (cache, fallback, erros) e extrato.
- **Strategy do limite diário**: resolução da política de PF e de PJ.
- **Cliente do BCB**: resposta normal, dia sem cotação e erro 500, simulados com `MockRestServiceServer`. Um teste separado prova que a chamada **estoura o timeout** contra um servidor que nunca responde.
- **Banco real (H2)**: lock otimista das carteiras (duas atualizações concorrentes) e subida do contexto, que confere as entidades contra o `schema.sql`.
- **Tratamento de erros**: mapeamento das exceções para os códigos HTTP, inclusive rota inexistente, método e `Content-Type` não suportados (`404`, `405`, `415`), conflitos (`409`) e erro inesperado (`500`, sem vazar detalhes).

---

## Endpoints

> **Ao testar pelo Swagger:** cadastre um usuário (`POST /users`), faça `POST /auth/login` e cole o `accessToken` no botão **Authorize**; sem isso, os endpoints protegidos respondem `401`. Um usuário novo tem saldo zero, então faça `POST /users/{id}/wallets/deposit` antes de remeter. `POST /remittances` exige o header `Idempotency-Key` (o Swagger já mostra o campo): use uma chave **nova** a cada remessa. Repetir a mesma chamada devolve a remessa original; reusar a chave com outro valor dá `422`. A cotação do dia só sai por volta das 13h; antes disso, e em fins de semana e feriados, vale a última disponível.

| Método | Rota | Para quê |
|---|---|---|
| `POST` | `/auth/login` | Troca e-mail e senha por um token JWT (público) |
| `POST` | `/users` | Cadastra usuário (PF ou PJ) e cria as carteiras BRL e USD (público) |
| `GET` | `/users`, `/users/{id}` | Lista / busca usuários |
| `GET` | `/users/{id}/wallets` | Saldos em BRL e USD |
| `POST` | `/users/{id}/wallets/deposit` | Deposita saldo (auxiliar de teste) |
| `POST` | `/remittances` | **Cria a remessa** (exige `Idempotency-Key`; o remetente é o usuário do token) |
| `GET` | `/remittances/{id}` | Busca uma remessa |
| `GET` | `/users/{id}/remittances` | Remessas enviadas pelo usuário; `?remittanceId=` filtra uma |
| `GET` | `/users/{id}/ledger-entries` | Extrato do usuário; `?currency=BRL` filtra a moeda |
| `GET` | `/remittances/{id}/ledger-entries` | Os 2 lançamentos (débito e crédito) de uma remessa |
| `GET` | `/exchange-rates/{data}` | Cotação aplicada em uma data (`AAAA-MM-DD`) e a data real dela (público) |

Só são públicos o login, o cadastro, a cotação, o Swagger e o health. O resto exige o header `Authorization: Bearer <token>`. O token é um JWT (HS256) que expira em 1 h.

**Quem pode ver o quê:** rotas `/users/{id}/...` (carteiras, depósito, extrato, remessas enviadas) só funcionam para o próprio usuário; o `{id}` diferente do token dá `403`. Em `POST /remittances` o remetente **é o usuário do token** (não existe `senderId` no body). Uma remessa e seu extrato só aparecem para quem enviou ou recebeu; para os demais, `404`. `GET /users` lista todos os usuários **de propósito**, para facilitar os testes; em produção seria restrito.

Erros seguem sempre o mesmo formato (`timestamp`, `status`, `error`, `message`, `fieldErrors`):

| Código | Quando |
|---|---|
| `400` | Campo inválido ou ausente, header ausente, id ou data em formato errado |
| `401` | Sem token, token inválido ou expirado, ou e-mail/senha incorretos no login |
| `403` | `/users/{id}/...` de outro usuário |
| `404` | Usuário ou remessa inexistente (ou remessa de que o usuário não participa) |
| `409` | E-mail/documento já cadastrado, carteira alterada por outra operação, chave de idempotência em uso |
| `422` | Regra de negócio: saldo insuficiente, limite diário, remessa para si mesmo, cotação indisponível, chave reutilizada com outros dados |
| `503` | API do BCB fora do ar (e sem a cotação do dia em cache); é só tentar de novo |

---

## O que foi solicitado e como foi feito

| Requisito | Como foi atendido |
|---|---|
| Remessa = conversão + transferência, revertida em qualquer inconsistência | `RemittanceService` em **uma única `@Transactional`**: débito, crédito, remessa e lançamentos são gravados juntos, ou nada é gravado |
| Cotação do BCB, campo `cotacaoCompra` | `BcbExchangeRateClient` (API PTAX). A cotação usada fica gravada na remessa, junto com o **dia a que ela pertence** (`quoteDate`): numa remessa de domingo, `exchangeRate` é a de sexta e `quoteDate` mostra isso |
| Fim de semana: usar a última cotação | Fallback dia a dia, até 7 dias para trás, como o enunciado pede ("a última cotação obtida"). O 7 é escolha nossa: um fim de semana precisa de até 3 dias (segunda antes das 13h volta para a sexta) e feriados prolongados como o Carnaval, de uns 5. Cobre também feriado e "hoje antes da publicação". Isso vale quando o BCB **responde** que não há cotação; se ele **não responde**, é outro caso (ver o cliente do BCB, abaixo) |
| Validar saldo antes da remessa | `422 Saldo insuficiente` |
| Usuário com nome, e-mail, senha, CPF (PF) ou CNPJ (PJ); e-mail e documento únicos | Constraints `UNIQUE` no banco; duplicidade → `409`; senha com BCrypt |
| Limite diário: PF R$ 10 mil, PJ R$ 50 mil | **Strategy**: `PfDailyLimitPolicy` e `PjDailyLimitPolicy` implementam `DailyLimitPolicy`. Soma o BRL já enviado no dia; passou do limite → `422` |
| Sem restrição entre PF e PJ | Qualquer combinação é aceita |
| Carteira em Real e em Dólar | Criadas automaticamente, com saldo zero, no cadastro |
| Banco em memória | H2. O schema é escrito à mão em `schema.sql` e o Hibernate só **valida** (`ddl-auto=validate`) |

**Interpretações que tomei onde o enunciado é aberto:**
- **Remessa para o próprio usuário é bloqueada** (`422`): "remessa entre eles" pressupõe duas partes.
- **O limite diário** conta o valor em BRL das remessas do remetente no dia corrente do servidor (a imagem Docker usa o fuso `America/Sao_Paulo`).
- **Remessa que falha não é gravada.** Só o erro HTTP volta, e nada fica pela metade.

## Além do que foi solicitado

- **Autenticação JWT** (Spring Security, sem sessão no servidor). O segredo de assinatura vem de `app.security.jwt.secret`; erros de autenticação (`401`) usam o mesmo formato de erro da API.
- **Idempotência.** `POST /remittances` exige `Idempotency-Key`; há `UNIQUE (sender_id, idempotency_key)` no banco. Repetir a chamada devolve a remessa original; mesma chave com dados diferentes é recusada; duas requisições simultâneas com a mesma chave → uma leva `409`. Medido localmente: sem a chave, dois cliques com 50 ms de intervalo debitavam **duas vezes** (12 de 12 tentativas); com ela, uma.
- **Lock otimista** (`@Version`) nas carteiras: duas remessas concorrentes na mesma carteira, uma leva `409` em vez de sobrescrever o saldo.
- **Ledger imutável** (partida dobrada): cada remessa grava um `DEBIT` em BRL na carteira de quem envia e um `CREDIT` em USD na de quem recebe. Só leitura na API.
- **Cache das cotações em duas camadas.** (1) **Spring Cache com Caffeine** (`@Cacheable` no `getQuote`), em memória, com TTL de 1 dia e no máximo 500 datas: a segunda remessa do dia não toca nem o banco nem o BCB. (2) A tabela `exchange_rates` (uma cotação por dia), que guarda o **histórico** e é de onde sai o fallback "última cotação obtida". Só entra no cache em memória a cotação do **dia exato**, porque ela não muda depois de publicada; um fallback (ex.: hoje antes das 13h devolve a de ontem) ficaria desatualizado depois da publicação. Erros também não são cacheados.
- **Cliente do BCB resiliente**: timeouts de 3 s (conexão) e 5 s (leitura). O sistema distingue *"o BCB respondeu que não há cotação"* (fim de semana ou feriado: fallback para a última cotação) de *"o BCB não respondeu"* (erro, timeout, 5xx ou resposta fora do formato esperado). No segundo caso ele **falha direto com `503`** e uma mensagem clara, sem debitar nada e sem voltar dias às cegas: sem resposta do BCB não dá para saber se o dia é útil ou não, e converter dinheiro com uma cotação antiga seria pior que pedir para tentar de novo.
- **Consultas extras** (remessas, extrato, cotação por data) e logs nos pontos de negócio, sem dados pessoais.

## Docker, Kubernetes e CI/CD

**Docker.** `Dockerfile` multi-stage (build com JDK 21, execução com JRE 21). O container roda como usuário **não-root**, com `TZ=America/Sao_Paulo` (o "dia" do limite diário e da cotação é o do Brasil) e sem o servidor TCP do H2.

**Kubernetes.** Manifestos em `k8s/` (Kustomize): `Deployment`, `Service` e `ConfigMap`.

O segredo de assinatura do JWT vem de um `Secret` que **não é versionado**. Crie-o antes do deploy; sem ele o pod não sobe (`CreateContainerConfigError`), em vez de rodar com um segredo conhecido:

```bash
kubectl create secret generic cross-border-api-jwt --from-literal=secret="$(openssl rand -base64 48)"
```

```bash
docker build -t cross-border-api:local .
kubectl apply -k k8s/
kubectl rollout status deployment/cross-border-api
kubectl port-forward svc/cross-border-api 8080:8080    # Swagger em http://localhost:8080/swagger-ui.html
```

Se o cluster não enxergar a imagem local: no kind, `kind load docker-image cross-border-api:local`; no minikube, `minikube image load cross-border-api:local`.

- **1 réplica de propósito.** O banco é H2 em memória: com mais de um pod, cada um teria os seus próprios dados. Com um banco externo dá para escalar.
- **Probes** de startup, liveness e readiness em `/actuator/health/*` (Actuator; só o `health` é exposto).
- **Endurecimento:** usuário não-root, raiz somente leitura, sem capabilities, limite de memória (a JVM usa 75% como heap).
- O `ConfigMap` desliga o console do H2 (sem autenticação) e o log de SQL.
- Trocar o segredo invalida os tokens já emitidos: `kubectl delete secret cross-border-api-jwt`, crie de novo e `kubectl rollout restart deployment/cross-border-api`.

**CI/CD.** GitHub Actions (`.github/workflows/ci.yml`), a cada push na `main` e em todo pull request:
1. `./mvnw verify`: compila e roda todos os testes;
2. valida os manifestos (`kustomize` + `kubeconform`);
3. builda a imagem e faz um **smoke test**: sobe o container com um segredo JWT aleatório, espera o `readiness`, confere que uma rota protegida sem token dá `401` e que cadastro e login devolvem um token.

Em push na `main`, a imagem também é publicada no GitHub Container Registry (`ghcr.io/<dono>/<repo>`, tags `latest` e `sha-...`). O **deploy não é automático**, porque não há cluster para recebê-lo: é o `kubectl apply -k k8s/` acima, apontando para a imagem do registry com `kustomize edit set image cross-border-api=ghcr.io/<dono>/<repo>:<tag>`.

## Modelo de dados

| Tabela | Papel |
|---|---|
| `users` | Dados do usuário; `email` e `document` únicos; `user_type` PF/PJ |
| `wallets` | **Uma linha por usuário e moeda** (`UNIQUE user_id, currency`); `balance` e `version` (lock otimista) |
| `remittances` | A remessa: valores em BRL e USD, cotação usada e o dia dela (`quote_date`), `idempotency_key` |
| `ledger_entries` | Lançamentos imutáveis: carteira, moeda, `DEBIT`/`CREDIT`, valor, `reference_type`/`reference_id` (a remessa de origem) |
| `exchange_rates` | Histórico das cotações do BCB: uma por dia (`quote_date` único); também serve de cache persistente |

---

## Melhorias

- **USD → BRL não foi implementado.** O modelo já está preparado (coluna `direction`), mas faltam decisões de produto: qual cotação usar na volta (compra ou venda) e como contar o limite diário.
- **Uma cotação por dia.** A PTAX de fechamento sai por volta das 13h; antes disso vale a do dia anterior, e a variação durante o dia não é refletida (em 17/09 o dólar variou cerca de 0,5% entre a abertura e as 11h). Os boletins intradiários do BCB seriam uma melhoria.
- **`GET /users` lista todos os usuários** (nome, e-mail e documento) para qualquer usuário logado, de propósito, para facilitar os testes. Em produção seria restrito, por exemplo a um perfil administrador.
- **Console e porta 9092 do H2 ligados por padrão**, para facilitar a avaliação do banco. Eles dão acesso sem token (leem hashes de senha e alteram saldos), então em um ambiente real seriam desligados, deixando o console só em um profile de desenvolvimento. No Kubernetes o `ConfigMap` já os desliga.
- **Segredo do JWT.** O valor do `application.properties` é só para desenvolvimento; em qualquer ambiente real, defina `APP_SECURITY_JWT_SECRET` (mínimo 32 caracteres). No Kubernetes ele vem de um `Secret` criado à mão, sem versionar. Uma melhoria seria um gerenciador de segredos externo (Vault, Sealed Secrets) e rotação de chaves.
- **CPF/CNPJ**: a unicidade é garantida, mas os dígitos verificadores não são validados.
- **Banco em memória**: os dados somem ao reiniciar e não é possível escalar horizontalmente (cada instância teria o seu banco). Em produção seria um banco externo (ex.: PostgreSQL).
- **Depósito é auxiliar de teste** e não gera lançamento no ledger; por isso o extrato de quem depositou não fecha com o saldo.
- **Sem paginação** no extrato; **chaves de idempotência não expiram** (o padrão de mercado é uns 24 h, em tabela própria).
- **Dias sem cotação não são guardados em cache**: em fins de semana, e hoje antes das 13h, cada consulta pergunta ao BCB pelo dia sem cotação (até 2 vezes no fim de semana) antes de achar a última no banco. Um cache negativo de vida curta resolveria.

<details>
<summary><b>Estrutura do projeto</b></summary>

```
src/main/java/com/guilherme/crossborderapi
├── controller/   REST: User, Remittance, Ledger, ExchangeRate
├── service/      regras de negócio (dailylimit/: Strategy do limite PF/PJ)
├── repository/   Spring Data JPA
├── entity/       User, Wallet, Remittance, LedgerEntry, ExchangeRate
├── dto/          requests e responses
├── client/       cliente HTTP da API do BCB
├── config/       RestClient (timeouts), OpenAPI, BCrypt, servidor TCP do H2
└── exception/    exceções de negócio e handler/ (respostas de erro padronizadas)
src/main/resources/schema.sql   DDL explícito do banco
```

</details>
