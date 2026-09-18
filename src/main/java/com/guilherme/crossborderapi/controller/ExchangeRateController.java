package com.guilherme.crossborderapi.controller;

import com.guilherme.crossborderapi.dto.exchangerate.ExchangeRateQuote;
import com.guilherme.crossborderapi.dto.exchangerate.ExchangeRateResponse;
import com.guilherme.crossborderapi.exception.handler.ErrorResponse;
import com.guilherme.crossborderapi.service.ExchangeRateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/exchange-rates")
@RequiredArgsConstructor
@Tag(name = "Cotação", description = "Cotação do dólar usada nas remessas, vinda do Banco Central")
public class ExchangeRateController {

    private final ExchangeRateService exchangeRateService;

    @Operation(
            summary = "Cotação do dólar em uma data",
            description = "Devolve a cotação de compra (`cotacaoCompra`) que seria aplicada a uma remessa feita "
                    + "naquela data. É a PTAX de fechamento do Banco Central: uma por dia útil, publicada por "
                    + "volta das 13h. Sem cotação no dia (fim de semana, feriado, ou hoje antes da publicação), "
                    + "vale a última disponível: compare `requestedDate` com `quoteDate` para ver se houve "
                    + "fallback. Se a cotação ainda não estiver em cache, consulta o BCB e a guarda."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Cotação encontrada",
                    content = @Content(schema = @Schema(implementation = ExchangeRateResponse.class))),
            @ApiResponse(responseCode = "400", description = "Data em formato inválido (use AAAA-MM-DD)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "422", description = "Data futura, ou nenhuma cotação nos últimos 7 dias",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @SecurityRequirements
    @GetMapping("/{data}")
    public ResponseEntity<ExchangeRateResponse> getExchangeRate(
            @Parameter(description = "Data no formato AAAA-MM-DD", example = "2026-09-17")
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data) {
        ExchangeRateQuote quote = exchangeRateService.getQuote(data);
        return ResponseEntity.ok(ExchangeRateResponse.from(data, quote));
    }
}
