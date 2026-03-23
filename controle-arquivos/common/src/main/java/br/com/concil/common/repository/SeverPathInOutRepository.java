package br.com.concil.common.repository;

import br.com.concil.common.entity.SeverPathInOut;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface SeverPathInOutRepository extends JpaRepository<SeverPathInOut, Long> {

    @Query("""
        SELECT spio FROM SeverPathInOut spio
        JOIN FETCH spio.origin origin
        JOIN FETCH origin.server s
        JOIN FETCH spio.destination dest
        JOIN FETCH dest.server ds
        WHERE s.desServerType = 'SFTP'
          AND s.desServerOrigin = 'EXTERNO'
          AND origin.desPathType = 'ORIGIN'
          AND spio.flgActive = 1
          AND origin.flgActive = 1
          AND s.flgActive = 1
    """)
    List<SeverPathInOut> findActiveOriginSftpPaths();
}
