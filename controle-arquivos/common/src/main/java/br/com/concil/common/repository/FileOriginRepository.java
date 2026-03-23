package br.com.concil.common.repository;

import br.com.concil.common.entity.FileOrigin;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface FileOriginRepository extends JpaRepository<FileOrigin, Long> {

    Optional<FileOrigin> findByDesFileNameAndIdtAcquirerAndDatTimestampFileAndFlgActive(
            String desFileName, Long idtAcquirer, LocalDateTime datTimestampFile, Integer flgActive);
}
