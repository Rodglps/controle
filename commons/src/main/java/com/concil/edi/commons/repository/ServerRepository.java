package com.concil.edi.commons.repository;

import com.concil.edi.commons.entity.Server;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for Server entity operations.
 */
@Repository
public interface ServerRepository extends JpaRepository<Server, Long> {
    
    /**
     * Find all active servers.
     * @return List of servers with flg_active = 1
     */
    List<Server> findByFlgActive(Integer flgActive);
}
