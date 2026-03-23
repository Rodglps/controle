package br.com.concil.common.repository;

import br.com.concil.common.entity.JobConcurrencyControl;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface JobConcurrencyControlRepository extends JpaRepository<JobConcurrencyControl, Long> {

    Optional<JobConcurrencyControl> findByDesJobNameAndFlgActive(String desJobName, Integer flgActive);

    @Modifying
    @Query("UPDATE JobConcurrencyControl j SET j.desStatus = :status, j.datUpdate = CURRENT_TIMESTAMP WHERE j.desJobName = :jobName AND j.flgActive = 1")
    int updateStatus(@Param("jobName") String jobName, @Param("status") String status);
}
