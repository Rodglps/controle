package com.controle.arquivos.common.domain.entity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testes unitários para a entidade Layout.
 * Valida campos obrigatórios e comportamento de lifecycle callbacks.
 * 
 * Valida: Requisitos 1.1, 3.1
 */
class LayoutTest {

    @Test
    void deveCriarLayoutComCamposObrigatorios() {
        Layout layout = Layout.builder()
                .layoutName("Layout CIELO CSV")
                .layoutType("CSV")
                .build();

        assertThat(layout.getLayoutName()).isEqualTo("Layout CIELO CSV");
        assertThat(layout.getLayoutType()).isEqualTo("CSV");
    }

    @Test
    void devePermitirDescriptionOpcional() {
        Layout layout = Layout.builder()
                .layoutName("Layout CIELO CSV")
                .layoutType("CSV")
                .description("Layout para arquivos CSV da CIELO")
                .build();

        assertThat(layout.getDescription()).isEqualTo("Layout para arquivos CSV da CIELO");
    }

    @Test
    void deveInicializarActiveTrueNoPrePersist() {
        Layout layout = Layout.builder()
                .layoutName("Layout CIELO CSV")
                .layoutType("CSV")
                .build();

        layout.onCreate();

        assertThat(layout.getActive()).isTrue();
        assertThat(layout.getCreatedAt()).isNotNull();
        assertThat(layout.getUpdatedAt()).isNotNull();
    }

    @Test
    void deveManterActiveSeJaDefinido() {
        Layout layout = Layout.builder()
                .layoutName("Layout CIELO CSV")
                .layoutType("CSV")
                .active(false)
                .build();

        layout.onCreate();

        assertThat(layout.getActive()).isFalse();
    }

    @Test
    void deveAtualizarUpdatedAtNoPreUpdate() throws InterruptedException {
        Layout layout = Layout.builder()
                .layoutName("Layout CIELO CSV")
                .layoutType("CSV")
                .build();

        layout.onCreate();
        var createdAt = layout.getCreatedAt();
        var updatedAt = layout.getUpdatedAt();

        Thread.sleep(10);
        layout.onUpdate();

        assertThat(layout.getCreatedAt()).isEqualTo(createdAt);
        assertThat(layout.getUpdatedAt()).isAfter(updatedAt);
    }

    @Test
    void deveSuportarNomeLayoutLongo() {
        String nomeLongo = "A".repeat(100);
        Layout layout = Layout.builder()
                .layoutName(nomeLongo)
                .layoutType("CSV")
                .build();

        assertThat(layout.getLayoutName()).hasSize(100);
    }

    @Test
    void deveSuportarDescricaoLonga() {
        String descricaoLonga = "A".repeat(500);
        Layout layout = Layout.builder()
                .layoutName("Layout CIELO CSV")
                .layoutType("CSV")
                .description(descricaoLonga)
                .build();

        assertThat(layout.getDescription()).hasSize(500);
    }

    @Test
    void deveSuportarDiferentesTiposLayout() {
        String[] tipos = {"CSV", "TXT", "JSON", "XML", "OFX"};
        
        for (String tipo : tipos) {
            Layout layout = Layout.builder()
                    .layoutName("Layout " + tipo)
                    .layoutType(tipo)
                    .build();

            assertThat(layout.getLayoutType()).isEqualTo(tipo);
        }
    }
}
