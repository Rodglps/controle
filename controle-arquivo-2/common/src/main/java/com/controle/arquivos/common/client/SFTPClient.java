package com.controle.arquivos.common.client;

import com.controle.arquivos.common.config.SftpProperties;
import com.jcraft.jsch.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Vector;

/**
 * Cliente SFTP para conexão, listagem e download de arquivos.
 * 
 * Utiliza JSch para implementar operações SFTP com suporte a streaming.
 * Credenciais devem ser obtidas do VaultClient antes de conectar.
 */
@Slf4j
@Component
public class SFTPClient {

    private final SftpProperties sftpProperties;
    private final JSch jsch;
    
    private Session session;
    private ChannelSftp channelSftp;

    public SFTPClient(SftpProperties sftpProperties) {
        this.sftpProperties = sftpProperties;
        this.jsch = new JSch();
    }

    /**
     * Conecta ao servidor SFTP usando credenciais fornecidas.
     * 
     * @param host Hostname ou IP do servidor SFTP
     * @param port Porta do servidor SFTP
     * @param credenciais Credenciais obtidas do Vault
     * @throws SFTPException se houver erro de conexão
     */
    public void conectar(String host, int port, VaultClient.Credenciais credenciais) {
        try {
            log.info("Conectando ao servidor SFTP: {}:{}", host, port);
            
            // Criar sessão
            session = jsch.getSession(credenciais.getUsername(), host, port);
            session.setPassword(credenciais.getPassword());
            
            // Configurar propriedades da sessão
            Properties config = new Properties();
            config.put("StrictHostKeyChecking", sftpProperties.isStrictHostKeyChecking() ? "yes" : "no");
            
            if (sftpProperties.getKnownHostsFile() != null) {
                jsch.setKnownHosts(sftpProperties.getKnownHostsFile());
            }
            
            session.setConfig(config);
            session.setTimeout(sftpProperties.getTimeout());
            
            // Conectar
            session.connect(sftpProperties.getSessionTimeout());
            
            // Abrir canal SFTP
            Channel channel = session.openChannel("sftp");
            channel.connect(sftpProperties.getChannelTimeout());
            channelSftp = (ChannelSftp) channel;
            
            log.info("Conexão SFTP estabelecida com sucesso: {}:{}", host, port);
            
        } catch (JSchException e) {
            log.error("Erro ao conectar ao servidor SFTP {}:{} - {}", host, port, e.getMessage());
            desconectar();
            throw new SFTPException("Falha ao conectar ao servidor SFTP", e);
        }
    }

    /**
     * Lista arquivos em um diretório remoto.
     * 
     * @param caminho Caminho do diretório no servidor SFTP
     * @return Lista de metadados dos arquivos
     * @throws SFTPException se houver erro ao listar arquivos
     */
    public List<ArquivoMetadata> listarArquivos(String caminho) {
        validarConexao();
        
        try {
            log.debug("Listando arquivos no caminho: {}", caminho);
            
            @SuppressWarnings("unchecked")
            Vector<ChannelSftp.LsEntry> entries = channelSftp.ls(caminho);
            
            List<ArquivoMetadata> arquivos = new ArrayList<>();
            
            for (ChannelSftp.LsEntry entry : entries) {
                // Ignorar diretórios e entradas especiais (. e ..)
                if (entry.getAttrs().isDir() || 
                    entry.getFilename().equals(".") || 
                    entry.getFilename().equals("..")) {
                    continue;
                }
                
                SftpATTRS attrs = entry.getAttrs();
                ArquivoMetadata metadata = new ArquivoMetadata(
                    entry.getFilename(),
                    attrs.getSize(),
                    attrs.getMTime() * 1000L // Converter para milissegundos
                );
                
                arquivos.add(metadata);
            }
            
            log.debug("Encontrados {} arquivos no caminho: {}", arquivos.size(), caminho);
            return arquivos;
            
        } catch (SftpException e) {
            log.error("Erro ao listar arquivos no caminho: {} - {}", caminho, e.getMessage());
            throw new SFTPException("Falha ao listar arquivos", e);
        }
    }

    /**
     * Obtém InputStream para download streaming de um arquivo.
     * 
     * O InputStream retornado deve ser fechado pelo chamador após uso.
     * 
     * @param caminho Caminho completo do arquivo no servidor SFTP
     * @return InputStream para leitura do arquivo
     * @throws SFTPException se houver erro ao obter InputStream
     */
    public InputStream obterInputStream(String caminho) {
        validarConexao();
        
        try {
            log.debug("Obtendo InputStream para arquivo: {}", caminho);
            
            InputStream inputStream = channelSftp.get(caminho);
            
            log.debug("InputStream obtido com sucesso para arquivo: {}", caminho);
            return inputStream;
            
        } catch (SftpException e) {
            log.error("Erro ao obter InputStream para arquivo: {} - {}", caminho, e.getMessage());
            throw new SFTPException("Falha ao obter InputStream do arquivo", e);
        }
    }

    /**
     * Obtém OutputStream para upload streaming de um arquivo.
     * 
     * O OutputStream retornado deve ser fechado pelo chamador após uso.
     * 
     * @param caminho Caminho completo do arquivo no servidor SFTP
     * @return OutputStream para escrita do arquivo
     * @throws SFTPException se houver erro ao obter OutputStream
     */
    public java.io.OutputStream obterOutputStream(String caminho) {
        validarConexao();
        
        try {
            log.debug("Obtendo OutputStream para arquivo: {}", caminho);
            
            java.io.OutputStream outputStream = channelSftp.put(caminho);
            
            log.debug("OutputStream obtido com sucesso para arquivo: {}", caminho);
            return outputStream;
            
        } catch (SftpException e) {
            log.error("Erro ao obter OutputStream para arquivo: {} - {}", caminho, e.getMessage());
            throw new SFTPException("Falha ao obter OutputStream do arquivo", e);
        }
    }

    /**
     * Desconecta do servidor SFTP e libera recursos.
     */
    public void desconectar() {
        try {
            if (channelSftp != null && channelSftp.isConnected()) {
                channelSftp.disconnect();
                log.debug("Canal SFTP desconectado");
            }
            
            if (session != null && session.isConnected()) {
                session.disconnect();
                log.debug("Sessão SFTP desconectada");
            }
            
        } catch (Exception e) {
            log.warn("Erro ao desconectar SFTP: {}", e.getMessage());
        } finally {
            channelSftp = null;
            session = null;
        }
    }

    /**
     * Verifica se está conectado ao servidor SFTP.
     * 
     * @return true se conectado, false caso contrário
     */
    public boolean isConectado() {
        return session != null && session.isConnected() && 
               channelSftp != null && channelSftp.isConnected();
    }

    /**
     * Valida se há conexão ativa, lançando exceção se não houver.
     */
    private void validarConexao() {
        if (!isConectado()) {
            throw new SFTPException("Não há conexão ativa com o servidor SFTP");
        }
    }

    /**
     * Classe para representar metadados de um arquivo.
     */
    public static class ArquivoMetadata {
        private final String nome;
        private final long tamanho;
        private final long timestamp;

        public ArquivoMetadata(String nome, long tamanho, long timestamp) {
            this.nome = nome;
            this.tamanho = tamanho;
            this.timestamp = timestamp;
        }

        public String getNome() {
            return nome;
        }

        public long getTamanho() {
            return tamanho;
        }

        public long getTimestamp() {
            return timestamp;
        }

        @Override
        public String toString() {
            return String.format("ArquivoMetadata{nome='%s', tamanho=%d, timestamp=%d}", 
                nome, tamanho, timestamp);
        }
    }

    /**
     * Exceção específica para erros SFTP.
     */
    public static class SFTPException extends RuntimeException {
        public SFTPException(String message) {
            super(message);
        }

        public SFTPException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
