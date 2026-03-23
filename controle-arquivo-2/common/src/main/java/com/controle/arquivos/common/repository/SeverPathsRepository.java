package com.controle.arquivos.common.repository;

import com.controle.arquivos.common.domain.entity.SeverPaths;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repositório JPA para SeverPaths.
 * Gerencia caminhos de diretórios em servidores.
 */
@Repository
public interface SeverPathsRepository extends JpaRepository<SeverPaths, Long> {
}
