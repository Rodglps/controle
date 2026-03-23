package br.com.concil.common.repository;

import br.com.concil.common.entity.FileOriginClientProcessing;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FileOriginClientProcessingRepository extends JpaRepository<FileOriginClientProcessing, Long> {
}
