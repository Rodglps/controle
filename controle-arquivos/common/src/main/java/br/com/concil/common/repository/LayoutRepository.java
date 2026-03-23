package br.com.concil.common.repository;

import br.com.concil.common.entity.Layout;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface LayoutRepository extends JpaRepository<Layout, Long> {

    @Query("""
        SELECT l FROM Layout l
        JOIN FETCH l.identificationRules r
        WHERE l.idtAcquirer = :idtAcquirer
          AND l.flgActive = 1
          AND r.flgActive = 1
    """)
    List<Layout> findActiveByAcquirer(@Param("idtAcquirer") Long idtAcquirer);
}
