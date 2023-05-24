package gov.uspto.pe2e.cpc.wms.migration.engine.repository.oracle;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ActHiTaskinstRepository extends JpaRepository<ActHiTaskinst, String> {

	Optional<ActHiTaskinst> findById(String id);

}
