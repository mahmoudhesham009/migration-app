package gov.uspto.pe2e.cpc.wms.migration.engine.repository.mysql;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ExceptionProcessRepoSpringImpl extends JpaRepository<ExceptionProcess, Integer> {

	Optional<ExceptionProcess> findByProcessInstanceId(String processInstanceId);

	@Query(value = "select * from EXCEPTION_PROCESS where JOB_ID = ?1", nativeQuery = true)
	List<ExceptionProcess> findByJobId(String jobId);

}
