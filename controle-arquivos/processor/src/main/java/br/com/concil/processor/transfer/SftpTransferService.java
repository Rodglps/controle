package br.com.concil.processor.transfer;

import com.jcraft.jsch.*;
import lombok.extern.slf4j.Slf4j;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;

/**
 * Transfere arquivo de um SFTP de origem para um SFTP de destino via streaming.
 */
@Slf4j
public class SftpTransferService implements AutoCloseable {

    private final Session session;
    private final ChannelSftp channel;

    public SftpTransferService(Map<String, String> creds) throws JSchException {
        JSch jsch = new JSch();
        session = jsch.getSession(creds.get("username"), creds.get("host"),
                Integer.parseInt(creds.getOrDefault("port", "22")));
        session.setPassword(creds.get("password"));
        session.setConfig("StrictHostKeyChecking", "no");
        session.connect(30_000);
        channel = (ChannelSftp) session.openChannel("sftp");
        channel.connect(10_000);
    }

    public void upload(InputStream inputStream, String remotePath) throws SftpException {
        log.info("Iniciando upload SFTP: {}", remotePath);
        ensureDirectoryExists(remotePath);
        channel.put(inputStream, remotePath, ChannelSftp.OVERWRITE);
        log.info("Upload SFTP concluído: {}", remotePath);
    }

    public InputStream download(String remotePath) throws SftpException {
        return channel.get(remotePath);
    }

    private void ensureDirectoryExists(String remotePath) {
        String dir = remotePath.substring(0, remotePath.lastIndexOf('/'));
        try {
            channel.stat(dir);
        } catch (SftpException e) {
            try {
                channel.mkdir(dir);
            } catch (SftpException ex) {
                log.warn("Não foi possível criar diretório remoto {}: {}", dir, ex.getMessage());
            }
        }
    }

    @Override
    public void close() {
        if (channel != null && channel.isConnected()) channel.disconnect();
        if (session != null && session.isConnected()) session.disconnect();
    }
}
