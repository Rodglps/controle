package br.com.concil.common.repository;

import br.com.concil.common.entity.CustomerIdentification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface CustomerIdentificationRepository extends JpaRepository<CustomerIdentification, Long> {

    @Query("""
        SELECT ci FROM CustomerIdentification ci
        JOIN FETCH ci.rules r
        WHERE ci.idtAcquirer = :idtAcquirer
          AND ci.flgActive = 1
          AND r.flgActive = 1
        ORDER BY ci.numProcessingWeight DESC NULLS LAST
    """)
    List<CustomerIdentification> findActiveByAcquirer(@Param("idtAcquirer") Long idtAcquirer);
}
