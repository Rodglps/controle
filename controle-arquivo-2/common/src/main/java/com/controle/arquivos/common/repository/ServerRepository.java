package com.controle.arquivos.common.repository;

import com.controle.arquivos.common.domain.entity.Server;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repositório JPA para Server.
 * Gerencia servidores de origem e destino de arquivos.
 */
@Repository
public interface ServerRepository extends JpaRepository<Server, Long> {
}
