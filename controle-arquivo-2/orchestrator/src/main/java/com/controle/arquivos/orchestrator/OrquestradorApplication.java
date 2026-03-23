package com.controle.arquivos.orchestrator;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Classe principal do Pod Orquestrador.
 * 
 * Responsável por:
 * - Inicializar o contexto Spring Boot
 * - Habilitar scheduling para coleta periódica de arquivos
 * - Configurar component scan para todos os serviços
 * 
 * Requisitos: 1.1
 */
@SpringBootApplication(scanBasePackages = {
    "com.controle.arquivos.orchestrator",
    "com.controle.arquivos.common"
})
@EnableScheduling
public class OrquestradorApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrquestradorApplication.class, args);
    }
}
