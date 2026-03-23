package com.controle.arquivos.orchestrator.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Configuração para habilitar scheduling no Orquestrador.
 * Permite o uso de @Scheduled em componentes.
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
