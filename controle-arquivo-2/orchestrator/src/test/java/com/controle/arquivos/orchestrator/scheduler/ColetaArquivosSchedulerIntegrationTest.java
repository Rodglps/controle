package com.controle.arquivos.orchestrator.scheduler;

import com.controle.arquivos.orchestrator.config.SchedulingConfig;
import com.controle.arquivos.orchestrator.service.OrquestradorService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testes de integração para verificar que o scheduler está configurado corretamente.
 * Verifica que o Spring Boot carrega o scheduler e a configuração de scheduling.
 */
@SpringBootTest(classes = {
    ColetaArquivosScheduler.class,
    SchedulingConfig.class
})
@TestPropertySource(properties = {
    "app.scheduler.coleta.cron=0 0 0 * * *"  // Cron que nunca executa durante o teste
})
class ColetaArquivosSchedulerIntegrationTest {

    @Autowired
    private ApplicationContext applicationContext;

    @MockBean
    private OrquestradorService orquestradorService;

    @Test
    void contextLoads() {
        // Verifica que o contexto Spring carrega corretamente
        assertThat(applicationContext).isNotNull();
    }

    @Test
    void schedulerBeanExists() {
        // Verifica que o bean do scheduler foi criado
        ColetaArquivosScheduler scheduler = applicationContext.getBean(ColetaArquivosScheduler.class);
        assertThat(scheduler).isNotNull();
    }

    @Test
    void schedulingConfigExists() {
        // Verifica que a configuração de scheduling foi carregada
        SchedulingConfig config = applicationContext.getBean(SchedulingConfig.class);
        assertThat(config).isNotNull();
    }

    @Test
    void schedulingIsEnabled() {
        // Verifica que @EnableScheduling está ativo
        boolean hasEnableScheduling = applicationContext.getBeansWithAnnotation(EnableScheduling.class)
                .values().stream()
                .anyMatch(bean -> bean instanceof SchedulingConfig);
        
        assertThat(hasEnableScheduling).isTrue();
    }
}
