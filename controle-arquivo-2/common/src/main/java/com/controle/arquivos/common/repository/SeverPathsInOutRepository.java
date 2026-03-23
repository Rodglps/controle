package com.controle.arquivos.common.repository;

import com.controle.arquivos.common.domain.entity.SeverPathsInOut;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repositório JPA para SeverPathsInOut.
 * Gerencia mapeamento entre caminhos de origem e destino.
 */
@Repository
public interface SeverPathsInOutRepository extends JpaRepository<SeverPathsInOut, Long> {
}
