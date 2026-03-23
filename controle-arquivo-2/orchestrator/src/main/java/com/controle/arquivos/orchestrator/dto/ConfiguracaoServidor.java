package com.controle.arquivos.orchestrator.dto;

import com.controle.arquivos.common.domain.entity.Server;
import com.controle.arquivos.common.domain.entity.SeverPaths;
import com.controle.arquivos.common.domain.enums.TipoLink;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO que representa uma configuração completa de servidor para coleta de arquivos.
 * Contém informações sobre o caminho de origem, servidor de origem e servidor de destino.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConfiguracaoServidor {

    /**
     * ID do mapeamento origem-destino (sever_paths_in_out)
     */
    private Long idMapeamento;

    /**
     * Caminho de origem onde os arquivos serão coletados
     */
    private SeverPaths caminhoOrigem;

    /**
     * Servidor de origem (SFTP externo)
     */
    private Server servidorOrigem;

    /**
     * Servidor de destino (S3, SFTP interno, etc)
     */
    private Server servidorDestino;

    /**
     * Tipo de link (PRINCIPAL ou SECUNDARIO)
     */
    private TipoLink tipoLink;
}
