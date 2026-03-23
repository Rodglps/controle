package com.controle.arquivos.common.domain.entity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testes unitários para a entidade CustomerIdentification.
 * Valida campos obrigatórios e comportamento de lifecycle callbacks.
 * 
 * Valida: Requisitos 1.1, 3.1
 */
class CustomerIdentificationTest {

    @Test
    void deveCriarCustomerIdentificationComCamposObrigatorios() {
        CustomerIdentification customer = CustomerIdentification.builder()
                .customerName("Empresa ABC")
                .processingWeight(10)
                .build();

        assertThat(customer.getCustomerName()).isEqualTo("Empresa ABC");
        assertThat(customer.getProcessingWeight()).isEqualTo(10);
    }

    @Test
    void deveInicializarProcessingWeightZeroNoPrePersist() {
        CustomerIdentification customer = CustomerIdentification.builder()
                .customerName("Empresa ABC")
                .build();

        customer.onCreate();

        assertThat(customer.getProcessingWeight()).isEqualTo(0);
    }

    @Test
    void deveManterProcessingWeightSeJaDefinido() {
        CustomerIdentification customer = CustomerIdentification.builder()
                .customerName("Empresa ABC")
                .processingWeight(50)
                .build();

        customer.onCreate();

        assertThat(customer.getProcessingWeight()).isEqualTo(50);
    }

    @Test
    void deveInicializarActiveTrueNoPrePersist() {
        CustomerIdentification customer = CustomerIdentification.builder()
                .customerName("Empresa ABC")
                .processingWeight(10)
                .build();

        customer.onCreate();

        assertThat(customer.getActive()).isTrue();
        assertThat(customer.getCreatedAt()).isNotNull();
        assertThat(customer.getUpdatedAt()).isNotNull();
    }

    @Test
    void deveManterActiveSeJaDefinido() {
        CustomerIdentification customer = CustomerIdentification.builder()
                .customerName("Empresa ABC")
                .processingWeight(10)
                .active(false)
                .build();

        customer.onCreate();

        assertThat(customer.getActive()).isFalse();
    }

    @Test
    void deveAtualizarUpdatedAtNoPreUpdate() throws InterruptedException {
        CustomerIdentification customer = CustomerIdentification.builder()
                .customerName("Empresa ABC")
                .processingWeight(10)
                .build();

        customer.onCreate();
        var createdAt = customer.getCreatedAt();
        var updatedAt = customer.getUpdatedAt();

        Thread.sleep(10);
        customer.onUpdate();

        assertThat(customer.getCreatedAt()).isEqualTo(createdAt);
        assertThat(customer.getUpdatedAt()).isAfter(updatedAt);
    }

    @Test
    void deveSuportarNomeClienteLongo() {
        String nomeLongo = "A".repeat(200);
        CustomerIdentification customer = CustomerIdentification.builder()
                .customerName(nomeLongo)
                .processingWeight(10)
                .build();

        assertThat(customer.getCustomerName()).hasSize(200);
    }

    @Test
    void deveSuportarProcessingWeightNegativo() {
        CustomerIdentification customer = CustomerIdentification.builder()
                .customerName("Empresa ABC")
                .processingWeight(-1)
                .build();

        assertThat(customer.getProcessingWeight()).isEqualTo(-1);
    }

    @Test
    void deveSuportarProcessingWeightAlto() {
        CustomerIdentification customer = CustomerIdentification.builder()
                .customerName("Empresa ABC")
                .processingWeight(1000)
                .build();

        assertThat(customer.getProcessingWeight()).isEqualTo(1000);
    }
}
