package gov.uspto.pe2e.cpc.wms.migration.engine.repository.mysql;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
@Data
public class MigrateTask {

	private String taskId;

	private String taskDefKey;

	private String taskName;

}
