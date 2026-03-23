package br.com.concil.common.repository;

import br.com.concil.common.entity.FileOriginClient;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FileOriginClientRepository extends JpaRepository<FileOriginClient, Long> {
}
