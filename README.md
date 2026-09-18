# Cross Border API

Serviço de remessa entre usuários PF e PJ: converte Real em Dólar com a cotação oficial do Banco Central e transfere o valor convertido, tudo em uma única transação.

**Java 21 · Spring Boot 4 · Maven · H2 em memória**

```bash
./mvnw spring-boot:run    # sobe em http://localhost:8080
./mvnw test               # roda os testes
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

| O quê | Onde |
|---|---|
| API | http://localhost:8080 |
| Swagger UI | http://localhost:8080/swagger-ui.html |
| Console do H2 | http://localhost:8080/h2-console — JDBC URL `jdbc:h2:mem:crossborderdb`, usuário `sa`, senha vazia |

> A aplicação também abre o servidor TCP do H2 na porta **9092** (conveniência de desenvolvimento, para consultar o banco pelo terminal). Se essa porta estiver ocupada, desligue com `--app.h2-tcp-server.enabled=false`
> (`java -jar ... --app.h2-tcp-server.enabled=false` ou `./mvnw spring-boot:run -Dspring-boot.run.arguments=--app.h2-tcp-server.enabled=false`).

## Como rodar os testes

```bash
./mvnw test
```

Nenhum teste chama o BCB de verdade. O que está coberto:

- **Regras de negócio** (Mockito): remessa (validações, conversão, débito/crédito, ledger, limite diário, idempotência), cotação (cache, fallback, erros) e extrato.
- **Strategy do limite diário**: resolução da política de PF e de PJ.
- **Cliente do BCB**: resposta normal, dia sem cotação e erro 500, simulados com `MockRestServiceServer`. Um teste separado prova que a chamada **estoura o timeout** contra um servidor que nunca responde.
- **Integração com banco real (H2)**: lock otimista das carteiras (duas atualizações concorrentes) e subida do contexto, que confere as entidades contra o `schema.sql`.
- **Tratamento de erros**: mapeamento das exceções para os códigos HTTP.

---

## Endpoints

> **Ao testar pelo Swagger:** um usuário novo tem saldo zero, então faça `POST /users/{id}/wallets/deposit` antes de remeter. `POST /remittances` exige o header `Idempotency-Key` (o Swagger já mostra o campo): use uma chave **nova** a cada remessa. Repetir a mesma chamada devolve a remessa original; reusar a chave com outro valor dá `422`. A cotação do dia só sai por volta das 13h; antes disso, e em fins de semana e feriados, vale a última disponível.

| Método | Rota | Para quê |
|---|---|---|
| `POST` | `/users` | Cadastra usuário (PF ou PJ) e cria as carteiras BRL e USD |
| `GET` | `/users`, `/users/{id}` | Lista / busca usuários |
| `GET` | `/users/{id}/wallets` | Saldos em BRL e USD |
| `POST` | `/users/{id}/wallets/deposit` | Deposita saldo (auxiliar de teste) |
| `POST` | `/remittances` | **Cria a remessa** (exige `Idempotency-Key`) |
| `GET` | `/remittances/{id}` | Busca uma remessa |
| `GET` | `/users/{id}/remittances` | Remessas enviadas pelo usuário; `?remittanceId=` filtra uma |
| `GET` | `/users/{id}/ledger-entries` | Extrato do usuário; `?currency=BRL` filtra a moeda |
| `GET` | `/remittances/{id}/ledger-entries` | Os 2 lançamentos (débito e crédito) de uma remessa |
| `GET` | `/exchange-rates/{data}` | Cotação aplicada em uma data (`AAAA-MM-DD`) e a data real dela |

Erros seguem sempre o mesmo formato (`timestamp`, `status`, `error`, `message`, `fieldErrors`):

| Código | Quando |
|---|---|
| `400` | Campo inválido ou ausente, header ausente, id ou data em formato errado |
| `404` | Usuário ou remessa inexistente |
| `409` | E-mail/documento já cadastrado, carteira alterada por outra operação, chave de idempotência em uso |
| `422` | Regra de negócio: saldo insuficiente, limite diário, remessa para si mesmo, cotação indisponível, chave reutilizada com outros dados |

---

## O que o enunciado pede e como foi feito

| Requisito | Como foi atendido |
|---|---|
| Remessa = conversão + transferência, revertida em qualquer inconsistência | `RemittanceService` em **uma única `@Transactional`**: débito, crédito, remessa e lançamentos são gravados juntos, ou nada é gravado |
| Cotação do BCB, campo `cotacaoCompra` | `BcbExchangeRateClient` (API PTAX). A cotação usada fica gravada na remessa |
| Fim de semana: usar a última cotação | Fallback dia a dia, até 7 dias para trás. Cobre também feriado, "hoje antes da publicação" e BCB fora do ar |
| Validar saldo antes da remessa | `422 Saldo insuficiente` |
| Usuário com nome, e-mail, senha, CPF (PF) ou CNPJ (PJ); e-mail e documento únicos | Constraints `UNIQUE` no banco; duplicidade → `409`; senha com BCrypt |
| Limite diário: PF R$ 10 mil, PJ R$ 50 mil | **Strategy**: `PfDailyLimitPolicy` e `PjDailyLimitPolicy` implementam `DailyLimitPolicy`. Soma o BRL já enviado no dia; passou do limite → `422` |
| Sem restrição entre PF e PJ | Qualquer combinação é aceita |
| Carteira em Real e em Dólar | Criadas automaticamente, com saldo zero, no cadastro |
| Banco em memória | H2. O schema é escrito à mão em `schema.sql` e o Hibernate só **valida** (`ddl-auto=validate`) |

**Interpretações que tomei onde o enunciado é aberto:**
- **Remessa para o próprio usuário é bloqueada** (`422`): "remessa entre eles" pressupõe duas partes.
- **O limite diário** conta o valor em BRL das remessas do remetente no dia corrente do servidor.
- **Remessa que falha não é gravada.** Só o erro HTTP volta, e nada fica pela metade.

## Além do enunciado

- **Idempotência.** `POST /remittances` exige `Idempotency-Key`; há `UNIQUE (sender_id, idempotency_key)` no banco. Repetir a chamada devolve a remessa original; mesma chave com dados diferentes é recusada; duas requisições simultâneas com a mesma chave → uma leva `409`. Medido localmente: sem a chave, dois cliques com 50 ms de intervalo debitavam **duas vezes** (12 de 12 tentativas); com ela, uma.
- **Lock otimista** (`@Version`) nas carteiras: duas remessas concorrentes na mesma carteira, uma leva `409` em vez de sobrescrever o saldo.
- **Ledger imutável** (partida dobrada): cada remessa grava um `DEBIT` em BRL na carteira de quem envia e um `CREDIT` em USD na de quem recebe. Só leitura na API.
- **Cache das cotações** na tabela `exchange_rates` (uma por dia). É um cache em banco, não Spring Cache/Caffeine.
- **Cliente do BCB resiliente**: timeouts de 3 s (conexão) e 5 s (leitura); erro ou silêncio da API vira fallback, nunca um 500.
- **Consultas extras** (remessas, extrato, cotação por data) e logs nos pontos de negócio, sem dados pessoais.

## Modelo de dados

| Tabela | Papel |
|---|---|
| `users` | Dados do usuário; `email` e `document` únicos; `user_type` PF/PJ |
| `wallets` | **Uma linha por usuário e moeda** (`UNIQUE user_id, currency`); `balance` e `version` (lock otimista) |
| `remittances` | A remessa: valores em BRL e USD, cotação usada, `idempotency_key` |
| `ledger_entries` | Lançamentos imutáveis: carteira, moeda, `DEBIT`/`CREDIT`, valor, `reference_type`/`reference_id` (a remessa de origem) |
| `exchange_rates` | Cache da cotação: uma por dia (`quote_date` único) |

---

## Limitações e o que ficou de fora

- **USD → BRL não foi implementado.** O modelo já está preparado (coluna `direction`), mas faltam decisões de produto: qual cotação usar na volta (compra ou venda) e como contar o limite diário.
- **Uma cotação por dia.** A PTAX de fechamento sai por volta das 13h; antes disso vale a do dia anterior, e a variação durante o dia não é refletida (em 17/09 o dólar variou cerca de 0,5% entre a abertura e as 11h). Os boletins intradiários do BCB seriam uma melhoria.
- **Sem autenticação.** Senhas são guardadas com BCrypt, mas não existe login; qualquer chamador lê qualquer usuário.
- **CPF/CNPJ**: a unicidade é garantida, mas os dígitos verificadores não são validados.
- **Banco em memória**: os dados somem ao reiniciar e não é possível escalar horizontalmente (cada instância teria o seu banco). Em produção seria um banco externo (ex.: PostgreSQL).
- **Depósito é auxiliar de teste** e não gera lançamento no ledger; por isso o extrato de quem depositou não fecha com o saldo.
- **Sem paginação** no extrato; **chaves de idempotência não expiram** (o padrão de mercado é uns 24 h, em tabela própria).
- **Dias sem cotação não são guardados em cache**: em fins de semana, cada consulta chama o BCB 2 vezes antes de achar a sexta no cache.
- `GET /exchange-rates/{data}` com datas anteriores a jul/1994 devolve valores na moeda da época (o BCB tem histórico antigo).

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
