package br.com.concil.orchestrator.sftp;

import com.jcraft.jsch.*;
import lombok.extern.slf4j.Slf4j;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

/**
 * Cliente SFTP baseado em JSch. Cada instância gerencia uma sessão.
 * Deve ser fechado após uso (try-with-resources ou chamada explícita a disconnect()).
 */
@Slf4j
public class SftpClient implements AutoCloseable {

    private final Session session;
    private final ChannelSftp channel;

    public SftpClient(String host, int port, String user, String password) throws JSchException {
        JSch jsch = new JSch();
        session = jsch.getSession(user, host, port);
        session.setPassword(password);
        session.setConfig("StrictHostKeyChecking", "no");
        session.connect(30_000);

        channel = (ChannelSftp) session.openChannel("sftp");
        channel.connect(10_000);
        log.info("SFTP conectado: {}@{}:{}", user, host, port);
    }

    /**
     * Lista nomes de arquivos (não diretórios) em um caminho remoto.
     */
    public List<SftpFileEntry> listFiles(String remotePath) throws SftpException {
        List<SftpFileEntry> result = new ArrayList<>();
        @SuppressWarnings("unchecked")
        Vector<ChannelSftp.LsEntry> entries = channel.ls(remotePath);
        for (ChannelSftp.LsEntry entry : entries) {
            if (!entry.getAttrs().isDir()) {
                result.add(new SftpFileEntry(
                        entry.getFilename(),
                        remotePath + "/" + entry.getFilename(),
                        entry.getAttrs().getMTime() * 1000L,
                        entry.getAttrs().getSize()
                ));
            }
        }
        return result;
    }

    public ChannelSftp getChannel() {
        return channel;
    }

    @Override
    public void close() {
        if (channel != null && channel.isConnected()) channel.disconnect();
        if (session != null && session.isConnected()) session.disconnect();
        log.info("SFTP desconectado");
    }
}
