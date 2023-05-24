package gov.uspto.pe2e.cpc.wms.migration.engine.constant;


import org.json.JSONObject;

import gov.uspto.pe2e.cpc.wms.migration.engine.repository.mysql.ProcessMigrationInfo;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
@Data
public class APIResponse {
	
	private JSONObject jsonObject;
	private ProcessMigrationInfo migrationInfo;

}
