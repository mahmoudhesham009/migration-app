package gov.uspto.pe2e.cpc.wms.migration.engine.adapter;

import java.sql.Date;
import java.time.LocalDate;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
@Data
public class MigrationInputData {

	private String fromDate;
	private String toDate;
	private String ins;
	private boolean singleProcess;
	private String processUUID;
}
