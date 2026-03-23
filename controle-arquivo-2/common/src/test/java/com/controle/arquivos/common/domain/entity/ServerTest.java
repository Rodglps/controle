package com.controle.arquivos.common.domain.entity;

import com.controle.arquivos.common.domain.enums.OrigemServidor;
import com.controle.arquivos.common.domain.enums.TipoServidor;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testes unitários para a entidade Server.
 * Valida campos obrigatórios e comportamento de lifecycle callbacks.
 * 
 * Valida: Requisitos 1.1, 3.1
 */
class ServerTest {

    @Test
    void deveCriarServerComCamposObrigatorios() {
        Server server = Server.builder()
                .serverCode("SFTP-CIELO")
                .vaultCode("vault-prod")
                .vaultSecret("secret/sftp/cielo")
                .serverType(TipoServidor.SFTP)
                .serverOrigin(OrigemServidor.EXTERNO)
                .build();

        assertThat(server.getServerCode()).isEqualTo("SFTP-CIELO");
        assertThat(server.getVaultCode()).isEqualTo("vault-prod");
        assertThat(server.getVaultSecret()).isEqualTo("secret/sftp/cielo");
        assertThat(server.getServerType()).isEqualTo(TipoServidor.SFTP);
        assertThat(server.getServerOrigin()).isEqualTo(OrigemServidor.EXTERNO);
    }

    @Test
    void deveInicializarActiveTrueNoPrePersist() {
        Server server = Server.builder()
                .serverCode("SFTP-CIELO")
                .vaultCode("vault-prod")
                .vaultSecret("secret/sftp/cielo")
                .serverType(TipoServidor.SFTP)
                .serverOrigin(OrigemServidor.EXTERNO)
                .build();

        server.onCreate();

        assertThat(server.getActive()).isTrue();
        assertThat(server.getCreatedAt()).isNotNull();
        assertThat(server.getUpdatedAt()).isNotNull();
    }

    @Test
    void deveManterActiveSeJaDefinido() {
        Server server = Server.builder()
                .serverCode("SFTP-CIELO")
                .vaultCode("vault-prod")
                .vaultSecret("secret/sftp/cielo")
                .serverType(TipoServidor.SFTP)
                .serverOrigin(OrigemServidor.EXTERNO)
                .active(false)
                .build();

        server.onCreate();

        assertThat(server.getActive()).isFalse();
    }

    @Test
    void deveAtualizarUpdatedAtNoPreUpdate() throws InterruptedException {
        Server server = Server.builder()
                .serverCode("SFTP-CIELO")
                .vaultCode("vault-prod")
                .vaultSecret("secret/sftp/cielo")
                .serverType(TipoServidor.SFTP)
                .serverOrigin(OrigemServidor.EXTERNO)
                .build();

        server.onCreate();
        var createdAt = server.getCreatedAt();
        var updatedAt = server.getUpdatedAt();

        Thread.sleep(10);
        server.onUpdate();

        assertThat(server.getCreatedAt()).isEqualTo(createdAt);
        assertThat(server.getUpdatedAt()).isAfter(updatedAt);
    }

    @Test
    void deveSuportarTodosTiposServidor() {
        for (TipoServidor tipo : TipoServidor.values()) {
            Server server = Server.builder()
                    .serverCode("TEST-" + tipo)
                    .vaultCode("vault-prod")
                    .vaultSecret("secret/test")
                    .serverType(tipo)
                    .serverOrigin(OrigemServidor.EXTERNO)
                    .build();

            assertThat(server.getServerType()).isEqualTo(tipo);
        }
    }

    @Test
    void deveSuportarTodasOrigensServidor() {
        for (OrigemServidor origem : OrigemServidor.values()) {
            Server server = Server.builder()
                    .serverCode("TEST-" + origem)
                    .vaultCode("vault-prod")
                    .vaultSecret("secret/test")
                    .serverType(TipoServidor.SFTP)
                    .serverOrigin(origem)
                    .build();

            assertThat(server.getServerOrigin()).isEqualTo(origem);
        }
    }
}
