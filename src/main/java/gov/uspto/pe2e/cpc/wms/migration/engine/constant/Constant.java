package gov.uspto.pe2e.cpc.wms.migration.engine.constant;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import gov.uspto.pe2e.cpc.wms.migration.engine.repository.mysql.MigrateProcess;
import gov.uspto.pe2e.cpc.wms.migration.engine.repository.mysql.MigrateProcessRepoSpringImpl;
import gov.uspto.pe2e.cpc.wms.migration.engine.repository.mysql.OldProcess;
import gov.uspto.pe2e.cpc.wms.migration.engine.repository.mysql.OldProcessRepoSpringImpl;
import gov.uspto.pe2e.cpc.wms.migration.engine.repository.mysql.OriginalProcessInfo;
import gov.uspto.pe2e.cpc.wms.migration.engine.repository.mysql.OriginalProcessInfoRepoSpringImpl;
import gov.uspto.pe2e.cpc.wms.migration.engine.repository.mysql.ProcessMigrationInfo;
import gov.uspto.pe2e.cpc.wms.migration.engine.repository.mysql.ProcessMigrationInfoRepoSpringImpl;

@Component
public class Constant {

	private static int jmsCount = 0;

	@Autowired
	private OldProcessRepoSpringImpl oldProcessRepoSpringImpl;
	@Autowired
	private MigrateProcessRepoSpringImpl migrateProcessRepoSpringImpl;
	@Autowired
	private ProcessMigrationInfoRepoSpringImpl processMigrationInfoRepoSpringImpl;
	
	@Autowired
	private OriginalProcessInfoRepoSpringImpl originalProcessInfoRepoSpringImpl;

	public Constant() {
	}
	
	public Constant(OldProcessRepoSpringImpl oldProcessRepoSpringImpl) {
		this.oldProcessRepoSpringImpl = oldProcessRepoSpringImpl;
	}

	public OldProcess saveProcessOnDB(Object object) {

		JSONObject jsonObject = (JSONObject) object;

		OldProcess allProcessNeedMigrate = new OldProcess();
		OriginalProcessInfo originalProcessInfo = new OriginalProcessInfo();
		Optional<OldProcess> oldProcess = oldProcessRepoSpringImpl
				.findOldProcessByProcessInstanceId(jsonObject.getString("id"));
		String startedTime="";
		if (!oldProcess.isPresent()) {
			allProcessNeedMigrate.setName(jsonObject.getString("name"));
			allProcessNeedMigrate.setProcessInstanceId(jsonObject.getString("id"));
			allProcessNeedMigrate.setProcessDefinitionName(jsonObject.getString("processDefinitionName"));
			allProcessNeedMigrate.setBusinessKey(jsonObject.getString("businessKey"));
			allProcessNeedMigrate.setCreatedDate(getCurrentDate());
			
			startedTime=jsonObject.getString("started").replace("T", " ");
			
			originalProcessInfo.setName(jsonObject.getString("name"));
			originalProcessInfo.setProcessInstanceId(jsonObject.getString("id"));
			originalProcessInfo.setStartedTime(reverse(startedTime.substring(0,startedTime.indexOf("."))));
			originalProcessInfoRepoSpringImpl.saveAndFlush(originalProcessInfo);
			
			
			return	oldProcessRepoSpringImpl.save(allProcessNeedMigrate);
		}

		originalProcessInfoRepoSpringImpl.saveAndFlush(originalProcessInfo);
		return oldProcess.get();

	}
	
	private String reverse(String str) {
		String temp=str.substring(str.indexOf(" "));
		String[] resultArr= str.substring(0,str.indexOf(" ")).split("-");
		String result="";
		for (int i= resultArr.length-1;i>=0;i--) {
			if(i==0) {
				result+=resultArr[i];
				return result+temp;
			}
			result+=resultArr[i]+"-";
		}
		return result;
	}

	public MigrateProcess saveMigrateProcessOnDB(JSONObject jsonObject) {
		MigrateProcess migrateProcess = new MigrateProcess();

		migrateProcess.setName(jsonObject.getString("name"));
		migrateProcess.setProcessInstanceId(jsonObject.getString("id"));
		migrateProcess.setProcessDefinitionKey(jsonObject.getString("processDefinitionKey"));
		migrateProcess.setBusinessKey(jsonObject.getString("businessKey"));
		migrateProcess.setCreatedDate(getCurrentDate());

		return migrateProcessRepoSpringImpl.save(migrateProcess);

	}

	public String saveOldAndNewProcessOnDB(MigrateProcess migrateProcess, OldProcess allProcessNeedMigrate, int status,
			String errorMSG, int jobId) {

		ProcessMigrationInfo processMigrationInfo = new ProcessMigrationInfo();

		processMigrationInfo.setCloneProcessId(migrateProcess.getProcessInstanceId());
		processMigrationInfo.setJobId(jobId);
		processMigrationInfo.setOriginalProcessId(allProcessNeedMigrate.getProcessInstanceId());
		processMigrationInfo.setName(migrateProcess.getName());
		processMigrationInfo.setDefKey(migrateProcess.getProcessDefinitionKey());
		processMigrationInfo.setErrorMSG(errorMSG);
		processMigrationInfo.setCreatedDate(getCurrentDate());
		processMigrationInfo.setProjectNum(allProcessNeedMigrate.getProjectNum());

		String processId = processMigrationInfoRepoSpringImpl.save(processMigrationInfo).getCloneProcessId();
		return processId;

	}

	public void saveOldAndNewProcessOnDBV2(MigrateProcess migrateProcess, OldProcess allProcessNeedMigrate, int status,
			String errorMSG, int jobId, String processId) {

		ProcessMigrationInfo processMigrationInfo = null;
		Optional<ProcessMigrationInfo> processMigrationInfoOptional = processMigrationInfoRepoSpringImpl
				.findByCloneProcessId(processId);

		if (processMigrationInfoOptional.isPresent()) {
			processMigrationInfo = processMigrationInfoOptional.get();

			processMigrationInfo.setCloneProcessId(processId);
			processMigrationInfo.setStatus(status);
			processMigrationInfo.setErrorMSG(errorMSG);
			processMigrationInfo.setLastUpdateDate(getCurrentDate());
			processMigrationInfo.setProjectNum(allProcessNeedMigrate.getProjectNum());
			processMigrationInfoRepoSpringImpl.saveAndFlush(processMigrationInfo);

		}
	}

	public static JSONObject objectToJSONObject(Object object) {
		Object json = null;
		JSONObject jsonObject = null;
		try {
			json = new JSONTokener(object.toString()).nextValue();
		} catch (JSONException e) {
			e.printStackTrace();
		}
		if (json instanceof JSONObject) {
			jsonObject = (JSONObject) json;
		}
		return jsonObject;
	}

	public static String getCurrentDate() {

		DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");
		LocalDateTime now = LocalDateTime.now();
		return dtf.format(now);

	}

	public void setMigrateProcessRepoSpringImpl(MigrateProcessRepoSpringImpl migrateProcessRepoSpringImpl) {
		this.migrateProcessRepoSpringImpl = migrateProcessRepoSpringImpl;
	}

	public void setProcessMigrationInfoRepoSpringImpl(
			ProcessMigrationInfoRepoSpringImpl processMigrationInfoRepoSpringImpl) {
		this.processMigrationInfoRepoSpringImpl = processMigrationInfoRepoSpringImpl;
	}

	public MigrateProcessRepoSpringImpl getMigrateProcessRepoSpringImpl() {
		return migrateProcessRepoSpringImpl;
	}

	public ProcessMigrationInfoRepoSpringImpl getProcessMigrationInfoRepoSpringImpl() {
		return processMigrationInfoRepoSpringImpl;
	}

	public int getJmsCount() {
		return jmsCount;
	}

	public void setJmsCount(int jmsCount) {
		Constant.jmsCount = jmsCount;
	}

	public synchronized void decreseJmsCount() {
		jmsCount--;
	}
}
