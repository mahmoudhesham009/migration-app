package gov.uspto.pe2e.cpc.wms.migration.engine.repository.mysql;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MigrationJobRepoSpringImpl extends JpaRepository<MigrationJob, Integer> {
}
