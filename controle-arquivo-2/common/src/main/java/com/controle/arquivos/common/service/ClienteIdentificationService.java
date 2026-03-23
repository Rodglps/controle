package com.controle.arquivos.common.service;

import com.controle.arquivos.common.domain.entity.CustomerIdentification;
import com.controle.arquivos.common.domain.entity.CustomerIdentificationRule;
import com.controle.arquivos.common.domain.enums.TipoCriterio;
import com.controle.arquivos.common.repository.CustomerIdentificationRepository;
import com.controle.arquivos.common.repository.CustomerIdentificationRuleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Serviço responsável pela identificação de cliente usando regras baseadas no nome do arquivo.
 * Implementa lógica de aplicação de TODAS as regras ativas (AND lógico) e desempate por peso.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ClienteIdentificationService {

    private final CustomerIdentificationRuleRepository ruleRepository;
    private final CustomerIdentificationRepository customerRepository;

    /**
     * Identifica o cliente com base no nome do arquivo e ID do adquirente.
     * 
     * @param nomeArquivo Nome do arquivo a ser analisado
     * @param idAdquirente ID do adquirente
     * @return Optional contendo o cliente identificado, ou empty se nenhum cliente for identificado
     */
    public Optional<CustomerIdentification> identificar(String nomeArquivo, Long idAdquirente) {
        if (nomeArquivo == null || nomeArquivo.isEmpty()) {
            log.warn("Nome de arquivo vazio ou nulo fornecido para identificação");
            return Optional.empty();
        }

        if (idAdquirente == null) {
            log.warn("ID de adquirente nulo fornecido para identificação");
            return Optional.empty();
        }

        // Carregar todas as regras ativas para o adquirente
        List<CustomerIdentificationRule> regras = ruleRepository.findActiveByAcquirerId(idAdquirente);
        
        if (regras.isEmpty()) {
            log.warn("Nenhuma regra ativa encontrada para adquirente {}", idAdquirente);
            return Optional.empty();
        }

        // Agrupar regras por customer_identification_id
        Map<Long, List<CustomerIdentificationRule>> regrasPorCliente = regras.stream()
            .collect(Collectors.groupingBy(CustomerIdentificationRule::getCustomerIdentificationId));

        // Lista de IDs de clientes que satisfazem TODAS as regras
        List<Long> clientesCandidatosIds = new ArrayList<>();

        // Para cada cliente candidato, verificar se TODAS as suas regras são satisfeitas
        for (Map.Entry<Long, List<CustomerIdentificationRule>> entry : regrasPorCliente.entrySet()) {
            Long customerIdentificationId = entry.getKey();
            List<CustomerIdentificationRule> regrasDoCliente = entry.getValue();

            boolean todasRegrasAtendidas = regrasDoCliente.stream()
                .allMatch(regra -> aplicarRegra(regra, nomeArquivo));

            if (todasRegrasAtendidas) {
                clientesCandidatosIds.add(customerIdentificationId);
                
                log.debug("Cliente {} satisfaz todas as regras para arquivo {}", 
                    customerIdentificationId, nomeArquivo);
            }
        }

        if (clientesCandidatosIds.isEmpty()) {
            log.info("Nenhum cliente identificado para arquivo {} e adquirente {}", 
                nomeArquivo, idAdquirente);
            return Optional.empty();
        }

        // Buscar dados completos dos clientes candidatos
        List<CustomerIdentification> clientesCandidatos = customerRepository.findActiveByIds(clientesCandidatosIds);

        if (clientesCandidatos.isEmpty()) {
            log.warn("Clientes candidatos {} não encontrados ou inativos no banco", clientesCandidatosIds);
            return Optional.empty();
        }

        // Se múltiplos clientes satisfazem as regras, desempatar por num_processing_weight
        if (clientesCandidatos.size() > 1) {
            log.warn("Múltiplos clientes ({}) satisfazem as regras para arquivo {}. Aplicando desempate por peso.",
                clientesCandidatos.size(), nomeArquivo);
            
            // Selecionar cliente com maior processing_weight
            CustomerIdentification clienteComMaiorPeso = clientesCandidatos.stream()
                .max(Comparator.comparing(CustomerIdentification::getProcessingWeight))
                .orElse(clientesCandidatos.get(0));
            
            log.info("Cliente {} selecionado por maior peso ({}) para arquivo {}",
                clienteComMaiorPeso.getId(), clienteComMaiorPeso.getProcessingWeight(), nomeArquivo);
            
            return Optional.of(clienteComMaiorPeso);
        }

        return Optional.of(clientesCandidatos.get(0));
    }

    /**
     * Aplica uma regra de identificação ao nome do arquivo.
     * 
     * @param regra Regra a ser aplicada
     * @param nomeArquivo Nome do arquivo
     * @return true se a regra é satisfeita, false caso contrário
     */
    public boolean aplicarRegra(CustomerIdentificationRule regra, String nomeArquivo) {
        if (regra == null || nomeArquivo == null) {
            return false;
        }

        // Extrair substring se posições forem especificadas
        String valorParaComparar = extrairSubstring(nomeArquivo, 
            regra.getStartingPosition(), 
            regra.getEndingPosition());

        String valorEsperado = regra.getValue();
        if (valorEsperado == null) {
            log.warn("Valor esperado nulo na regra {}", regra.getId());
            return false;
        }

        TipoCriterio criterio = regra.getCriterionTypeEnum();
        
        return aplicarCriterio(criterio, valorParaComparar, valorEsperado);
    }

    /**
     * Extrai substring do nome do arquivo usando posições inicial e final.
     * 
     * @param nomeArquivo Nome do arquivo
     * @param posicaoInicio Posição inicial (1-indexed, pode ser null)
     * @param posicaoFim Posição final (1-indexed, pode ser null)
     * @return Substring extraída ou o nome completo se posições não forem especificadas
     */
    private String extrairSubstring(String nomeArquivo, Integer posicaoInicio, Integer posicaoFim) {
        if (posicaoInicio == null && posicaoFim == null) {
            return nomeArquivo;
        }

        int inicio = (posicaoInicio != null && posicaoInicio > 0) ? posicaoInicio - 1 : 0;
        int fim = (posicaoFim != null && posicaoFim > 0) ? posicaoFim : nomeArquivo.length();

        // Validar limites
        if (inicio >= nomeArquivo.length()) {
            log.warn("Posição inicial {} excede tamanho do arquivo {}", posicaoInicio, nomeArquivo.length());
            return "";
        }

        if (fim > nomeArquivo.length()) {
            fim = nomeArquivo.length();
        }

        if (inicio >= fim) {
            log.warn("Posição inicial {} >= posição final {} para arquivo {}", inicio, fim, nomeArquivo);
            return "";
        }

        return nomeArquivo.substring(inicio, fim);
    }

    /**
     * Aplica o critério de comparação entre o valor extraído e o valor esperado.
     * 
     * @param criterio Tipo de critério (COMECA-COM, TERMINA-COM, CONTEM, IGUAL)
     * @param valorExtraido Valor extraído do nome do arquivo
     * @param valorEsperado Valor esperado da regra
     * @return true se o critério é satisfeito, false caso contrário
     */
    private boolean aplicarCriterio(TipoCriterio criterio, String valorExtraido, String valorEsperado) {
        if (valorExtraido == null || valorEsperado == null) {
            return false;
        }

        return switch (criterio) {
            case COMECA_COM -> valorExtraido.startsWith(valorEsperado);
            case TERMINA_COM -> valorExtraido.endsWith(valorEsperado);
            case CONTEM -> valorExtraido.contains(valorEsperado);
            case IGUAL -> valorExtraido.equals(valorEsperado);
        };
    }
}
