package com.controle.arquivos.common.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Testes unitários para CorrelationIdFilter.
 * 
 * **Valida: Requisitos 20.2**
 */
class CorrelationIdFilterTest {

    private CorrelationIdFilter filter;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private FilterChain filterChain;

    @BeforeEach
    void setUp() {
        filter = new CorrelationIdFilter();
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        filterChain = mock(FilterChain.class);
        MDC.clear();
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void deveGerarCorrelationIdQuandoNaoFornecido() throws ServletException, IOException {
        // When
        filter.doFilterInternal(request, response, filterChain);

        // Then
        String correlationId = response.getHeader("X-Correlation-Id");
        assertThat(correlationId).isNotNull();
        assertThat(correlationId).isNotEmpty();
        
        // Verificar que o filterChain foi chamado
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void deveUsarCorrelationIdDoHeaderQuandoFornecido() throws ServletException, IOException {
        // Given
        String expectedCorrelationId = "test-correlation-id-123";
        request.addHeader("X-Correlation-Id", expectedCorrelationId);

        // When
        filter.doFilterInternal(request, response, filterChain);

        // Then
        String correlationId = response.getHeader("X-Correlation-Id");
        assertThat(correlationId).isEqualTo(expectedCorrelationId);
        
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void deveAdicionarCorrelationIdAoMDC() throws ServletException, IOException {
        // Given
        String expectedCorrelationId = "test-correlation-id-456";
        request.addHeader("X-Correlation-Id", expectedCorrelationId);

        // When
        filter.doFilterInternal(request, response, new FilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest servletRequest, 
                               jakarta.servlet.ServletResponse servletResponse) {
                // Verificar que o MDC foi configurado durante a execução do filtro
                String mdcCorrelationId = MDC.get("correlationId");
                assertThat(mdcCorrelationId).isEqualTo(expectedCorrelationId);
            }
        });

        // Then - MDC deve ser limpo após a execução
        assertThat(MDC.get("correlationId")).isNull();
    }

    @Test
    void deveLimparMDCMesmoQuandoOcorreExcecao() throws ServletException, IOException {
        // Given
        FilterChain throwingChain = mock(FilterChain.class);
        doThrow(new ServletException("Test exception")).when(throwingChain).doFilter(any(), any());

        // When/Then
        try {
            filter.doFilterInternal(request, response, throwingChain);
        } catch (ServletException e) {
            // Esperado
        }

        // MDC deve ser limpo mesmo com exceção
        assertThat(MDC.get("correlationId")).isNull();
    }

    @Test
    void deveGerarNovoCorrelationIdQuandoHeaderVazio() throws ServletException, IOException {
        // Given
        request.addHeader("X-Correlation-Id", "");

        // When
        filter.doFilterInternal(request, response, filterChain);

        // Then
        String correlationId = response.getHeader("X-Correlation-Id");
        assertThat(correlationId).isNotNull();
        assertThat(correlationId).isNotEmpty();
        
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void deveGerarNovoCorrelationIdQuandoHeaderApenasEspacos() throws ServletException, IOException {
        // Given
        request.addHeader("X-Correlation-Id", "   ");

        // When
        filter.doFilterInternal(request, response, filterChain);

        // Then
        String correlationId = response.getHeader("X-Correlation-Id");
        assertThat(correlationId).isNotNull();
        assertThat(correlationId).isNotEmpty();
        assertThat(correlationId.trim()).isNotEmpty();
        
        verify(filterChain).doFilter(request, response);
    }
}
