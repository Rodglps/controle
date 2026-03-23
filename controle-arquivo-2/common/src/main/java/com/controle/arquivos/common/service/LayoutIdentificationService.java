package com.controle.arquivos.common.service;

import com.controle.arquivos.common.domain.entity.Layout;
import com.controle.arquivos.common.domain.entity.LayoutIdentificationRule;
import com.controle.arquivos.common.domain.enums.OrigemValor;
import com.controle.arquivos.common.domain.enums.TipoCriterio;
import com.controle.arquivos.common.repository.LayoutIdentificationRuleRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Serviço responsável pela identificação de layout usando regras baseadas no nome do arquivo ou conteúdo do header.
 * Implementa lógica de aplicação de TODAS as regras ativas (AND lógico).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LayoutIdentificationService {

    private static final int HEADER_SIZE_BYTES = 7000;

    private final LayoutIdentificationRuleRepository ruleRepository;
    private final EntityManager entityManager;

    /**
     * Identifica o layout com base no nome do arquivo, conteúdo do header, ID do cliente e ID do adquirente.
     * 
     * @param nomeArquivo Nome do arquivo a ser analisado
     * @param headerStream InputStream para ler os primeiros 7000 bytes (pode ser null se não houver regras HEADER)
     * @param idCliente ID do cliente
     * @param idAdquirente ID do adquirente
     * @return Optional contendo o layout identificado, ou empty se nenhum layout for identificado
     */
    public Optional<Layout> identificar(String nomeArquivo, InputStream headerStream, Long idCliente, Long idAdquirente) {
        if (nomeArquivo == null || nomeArquivo.isEmpty()) {
            log.warn("Nome de arquivo vazio ou nulo fornecido para identificação de layout");
            return Optional.empty();
        }

        if (idCliente == null) {
            log.warn("ID de cliente nulo fornecido para identificação de layout");
            return Optional.empty();
        }

        if (idAdquirente == null) {
            log.warn("ID de adquirente nulo fornecido para identificação de layout");
            return Optional.empty();
        }

        // Carregar todas as regras ativas para o cliente e adquirente
        List<LayoutIdentificationRule> regras = ruleRepository.findActiveByClientIdAndAcquirerId(idCliente, idAdquirente);
        
        if (regras.isEmpty()) {
            log.warn("Nenhuma regra ativa encontrada para cliente {} e adquirente {}", idCliente, idAdquirente);
            return Optional.empty();
        }

        // Ler header se necessário (se houver regras com HEADER)
        String headerContent = null;
        boolean hasHeaderRules = regras.stream()
            .anyMatch(r -> r.getValueOrigin() == OrigemValor.HEADER);
        
        if (hasHeaderRules) {
            if (headerStream == null) {
                log.warn("InputStream de header é nulo mas existem regras HEADER para cliente {} e adquirente {}", 
                    idCliente, idAdquirente);
                return Optional.empty();
            }
            
            try {
                headerContent = lerHeader(headerStream);
            } catch (IOException e) {
                log.error("Erro ao ler header do arquivo {}: {}", nomeArquivo, e.getMessage(), e);
                return Optional.empty();
            }
        }

        // Agrupar regras por layout_id
        Map<Long, List<LayoutIdentificationRule>> regrasPorLayout = regras.stream()
            .collect(Collectors.groupingBy(LayoutIdentificationRule::getLayoutId));

        // Lista de IDs de layouts que satisfazem TODAS as regras
        List<Long> layoutsCandidatosIds = new ArrayList<>();

        // Para cada layout candidato, verificar se TODAS as suas regras são satisfeitas
        for (Map.Entry<Long, List<LayoutIdentificationRule>> entry : regrasPorLayout.entrySet()) {
            Long layoutId = entry.getKey();
            List<LayoutIdentificationRule> regrasDoLayout = entry.getValue();

            boolean todasRegrasAtendidas = true;
            
            for (LayoutIdentificationRule regra : regrasDoLayout) {
                String origem = regra.getValueOrigin() == OrigemValor.FILENAME ? nomeArquivo : headerContent;
                
                if (!aplicarRegra(regra, origem, headerContent != null ? headerContent : nomeArquivo)) {
                    todasRegrasAtendidas = false;
                    break;
                }
            }

            if (todasRegrasAtendidas) {
                layoutsCandidatosIds.add(layoutId);
                
                log.debug("Layout {} satisfaz todas as regras para arquivo {}", 
                    layoutId, nomeArquivo);
            }
        }

        if (layoutsCandidatosIds.isEmpty()) {
            log.info("Nenhum layout identificado para arquivo {}, cliente {} e adquirente {}", 
                nomeArquivo, idCliente, idAdquirente);
            return Optional.empty();
        }

        // Buscar dados completos do primeiro layout candidato
        // Se múltiplos layouts satisfazem as regras, retornar o primeiro (ordenado por layout_id)
        Long layoutId = layoutsCandidatosIds.stream().min(Long::compareTo).orElse(layoutsCandidatosIds.get(0));
        
        if (layoutsCandidatosIds.size() > 1) {
            log.warn("Múltiplos layouts ({}) satisfazem as regras para arquivo {}. Selecionando layout {}.",
                layoutsCandidatosIds.size(), nomeArquivo, layoutId);
        }

        Layout layout = entityManager.find(Layout.class, layoutId);
        
        if (layout == null || !layout.getActive()) {
            log.warn("Layout {} não encontrado ou inativo no banco", layoutId);
            return Optional.empty();
        }

        log.info("Layout {} identificado para arquivo {}", layoutId, nomeArquivo);
        return Optional.of(layout);
    }

    /**
     * Aplica uma regra de identificação ao conteúdo (nome do arquivo ou header).
     * 
     * @param regra Regra a ser aplicada
     * @param origem Origem do valor (FILENAME ou HEADER)
     * @param conteudo Conteúdo a ser analisado (nome do arquivo ou header)
     * @return true se a regra é satisfeita, false caso contrário
     */
    public boolean aplicarRegra(LayoutIdentificationRule regra, String origem, String conteudo) {
        if (regra == null || conteudo == null) {
            return false;
        }

        // Determinar qual conteúdo usar baseado na origem da regra
        String valorParaComparar;
        if (regra.getValueOrigin() == OrigemValor.FILENAME) {
            valorParaComparar = origem;
        } else if (regra.getValueOrigin() == OrigemValor.HEADER) {
            valorParaComparar = conteudo;
        } else {
            log.warn("Origem de valor não suportada: {}", regra.getValueOrigin());
            return false;
        }

        // Extrair substring se posições forem especificadas
        valorParaComparar = extrairSubstring(valorParaComparar, 
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
     * Lê os primeiros 7000 bytes do InputStream e converte para String.
     * 
     * @param headerStream InputStream do arquivo
     * @return String contendo os primeiros 7000 bytes
     * @throws IOException Se houver erro na leitura
     */
    private String lerHeader(InputStream headerStream) throws IOException {
        byte[] buffer = new byte[HEADER_SIZE_BYTES];
        int bytesRead = headerStream.read(buffer, 0, HEADER_SIZE_BYTES);
        
        if (bytesRead <= 0) {
            log.warn("Nenhum byte lido do header");
            return "";
        }
        
        // Converter apenas os bytes lidos para String
        return new String(buffer, 0, bytesRead, StandardCharsets.UTF_8);
    }

    /**
     * Extrai substring do conteúdo usando posições inicial e final.
     * 
     * @param conteudo Conteúdo a ser extraído
     * @param posicaoInicio Posição inicial (1-indexed, pode ser null)
     * @param posicaoFim Posição final (1-indexed, pode ser null)
     * @return Substring extraída ou o conteúdo completo se posições não forem especificadas
     */
    private String extrairSubstring(String conteudo, Integer posicaoInicio, Integer posicaoFim) {
        if (posicaoInicio == null && posicaoFim == null) {
            return conteudo;
        }

        int inicio = (posicaoInicio != null && posicaoInicio > 0) ? posicaoInicio - 1 : 0;
        int fim = (posicaoFim != null && posicaoFim > 0) ? posicaoFim : conteudo.length();

        // Validar limites
        if (inicio >= conteudo.length()) {
            log.warn("Posição inicial {} excede tamanho do conteúdo {}", posicaoInicio, conteudo.length());
            return "";
        }

        if (fim > conteudo.length()) {
            fim = conteudo.length();
        }

        if (inicio >= fim) {
            log.warn("Posição inicial {} >= posição final {} para conteúdo", inicio, fim);
            return "";
        }

        return conteudo.substring(inicio, fim);
    }

    /**
     * Aplica o critério de comparação entre o valor extraído e o valor esperado.
     * 
     * @param criterio Tipo de critério (COMECA-COM, TERMINA-COM, CONTEM, IGUAL)
     * @param valorExtraido Valor extraído do conteúdo
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
