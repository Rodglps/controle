package com.controle.arquivos.common.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Filtro que adiciona correlationId ao MDC para todas as requisições HTTP.
 * O correlationId é usado para rastrear logs relacionados a uma mesma operação.
 * 
 * Se a requisição já possui um header X-Correlation-Id, ele é usado.
 * Caso contrário, um novo UUID é gerado.
 * 
 * **Valida: Requisitos 20.2**
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    private static final String CORRELATION_ID_HEADER = "X-Correlation-Id";
    private static final String CORRELATION_ID_MDC_KEY = "correlationId";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        try {
            // Obter correlationId do header ou gerar novo
            String correlationId = request.getHeader(CORRELATION_ID_HEADER);
            if (correlationId == null || correlationId.trim().isEmpty()) {
                correlationId = UUID.randomUUID().toString();
            }

            // Adicionar ao MDC para que apareça em todos os logs
            MDC.put(CORRELATION_ID_MDC_KEY, correlationId);

            // Adicionar ao response header para que o cliente possa rastrear
            response.setHeader(CORRELATION_ID_HEADER, correlationId);

            // Continuar a cadeia de filtros
            filterChain.doFilter(request, response);

        } finally {
            // Sempre limpar o MDC após a requisição
            MDC.remove(CORRELATION_ID_MDC_KEY);
        }
    }
}
