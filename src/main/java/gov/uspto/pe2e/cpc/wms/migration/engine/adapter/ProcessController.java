package gov.uspto.pe2e.cpc.wms.migration.engine.adapter;

import static gov.uspto.pe2e.cpc.wms.migration.engine.configration.ActiveMQConfig.PROCESS_QUEUE;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jms.core.JmsMessagingTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import gov.uspto.pe2e.cpc.wms.migration.engine.constant.Constant;
import gov.uspto.pe2e.cpc.wms.migration.engine.constant.MigrationResponse;
import gov.uspto.pe2e.cpc.wms.migration.engine.constant.Validation;
import gov.uspto.pe2e.cpc.wms.migration.engine.repository.mysql.ExceptionProcess;
import gov.uspto.pe2e.cpc.wms.migration.engine.repository.mysql.ExceptionProcessRepoSpringImpl;
import gov.uspto.pe2e.cpc.wms.migration.engine.repository.mysql.MigrationJob;
import gov.uspto.pe2e.cpc.wms.migration.engine.repository.mysql.MigrationJobRepoSpringImpl;
import gov.uspto.pe2e.cpc.wms.migration.engine.repository.mysql.ProcessMigrationInfo;
import gov.uspto.pe2e.cpc.wms.migration.engine.repository.mysql.ProcessMigrationInfoRepoSpringImpl;
import gov.uspto.pe2e.cpc.wms.migration.engine.service.MigrationServices;

@RestController
@RequestMapping("/migrationProcess")
public class ProcessController {

	@Autowired
	private JmsMessagingTemplate jmsMessagingTemplate;

	@Autowired
	MigrationServices migrationServices;

	@Autowired
	Validation validation;

	@Autowired
	MigrationJobRepoSpringImpl migrationJobRepoSpringImpl;

	@Autowired
	ExceptionProcessRepoSpringImpl exceptionProcessRepoSpringImpl;

	@Autowired
	ProcessMigrationInfoRepoSpringImpl processMigrationInfoRepoSpringImpl;

	private final Constant constant;

	public ProcessController(Constant constant) {
		this.constant = constant;
	}

	@GetMapping("/{jobId}")
	public ResponseEntity<MigrationResponse> migration(@PathVariable int jobId) {
		MigrationResponse migrationResponse = null;
		MigrationInputData inputDate = null;
		Map<String, String> exceptionProcesses = new HashMap<>();
		try {
			JSONObject resultJSONObject, jsonObject;
			Optional<MigrationJob> migrationJob = migrationJobRepoSpringImpl.findById(jobId);
			String analyzedData = "";
			if (migrationJob.isPresent()) {
				inputDate = new MigrationInputData();
				MigrationJob job = migrationJob.get();
				job.setStatus("migrationStarted");
				migrationJobRepoSpringImpl.save(job);
				analyzedData = job.getAnalysisObject();
				if(job.isSingleProcess()) {
				
				inputDate.setProcessUUID(job.getProcessName());
				inputDate.setSingleProcess(job.isSingleProcess());
				}
				else {
					inputDate.setFromDate(job.getFromDate().toString());
					inputDate.setToDate(job.getToDate().toString());
				}
			}

			resultJSONObject = migrationServices.getDataFromApi("enterprise/historic-process-instances/query", inputDate, "", "POST",inputDate.isSingleProcess()).get(0)
					.getJsonObject();
			
		
			if (!resultJSONObject.isEmpty()) {

				List<ExceptionProcess> exceptionProcess = exceptionProcessRepoSpringImpl
						.findByJobId(String.valueOf(jobId));
				exceptionProcesses = new HashMap<>();

				for (ExceptionProcess process : exceptionProcess) {
					exceptionProcesses.put(process.getProcessInstanceId(), process.getAnalysisObject());
				}

				JSONArray  dataArray = new JSONArray();
				if(inputDate.isSingleProcess()) {
					dataArray.put(resultJSONObject.getJSONArray("data")
							.getJSONObject(resultJSONObject.getJSONArray("data").length() - 1));
				}
				else {
					 dataArray = resultJSONObject.getJSONArray("data");
				}
				constant.setJmsCount(dataArray.length());
				for (Object object : dataArray) {

					constant.saveProcessOnDB(object);
					jsonObject = new JSONObject();
					jsonObject.put("jsonObject", Constant.objectToJSONObject(object));
					if (exceptionProcesses.containsKey(jsonObject.getJSONObject("jsonObject").optString("id"))) {
						jsonObject.put("analyzedData",
								exceptionProcesses.get(jsonObject.getJSONObject("jsonObject").optString("id")));
					} else {
						jsonObject.put("analyzedData", analyzedData);
					}
					jsonObject.put("jobId", jobId);
					jmsMessagingTemplate.convertAndSend(PROCESS_QUEUE, jsonObject.toString());

				}

			} else {
				migrationResponse = new MigrationResponse(-11, "NO RESULT FOUND >> resultJSONObject is empty");
			}

		} catch (Exception e) {
			e.printStackTrace();
		}

		return new ResponseEntity<MigrationResponse>(migrationResponse, HttpStatus.OK);
	}

	@GetMapping("Report/{jobId}")
	public String generateReport(@PathVariable int jobId) throws ClientProtocolException, IOException {
		List<Object[]> data = processMigrationInfoRepoSpringImpl.getReportData(jobId);
		List<ProcessMigrationInfo> allProcessData = processMigrationInfoRepoSpringImpl.findByJobId(jobId);
		int total = Integer.valueOf(data.get(0)[0].toString());
		int successfulProcessCount = Integer.valueOf(data.get(0)[1].toString());
		int failureProcessCount = Integer.valueOf(data.get(0)[2].toString());
		//==================================
		
		JSONArray allProcessDataAsJson = new JSONArray();
//		allProcessDataAsJson = migrationServices.checkValidateAndDisplay(allProcessData);
//		JSONObject processInfoAsJson =new JSONObject();
//		boolean cloneExist= false;
//		boolean originalExist=false;
//		boolean validate= false;
//		boolean display= false;
//		JSONObject reqBody =new JSONObject();
//		List<JSONObject> resultList= new ArrayList<>();
//		CloseableHttpClient client = HttpClients.createDefault();
//		for(ProcessMigrationInfo processMigrationInfo :allProcessData ) {
//			 cloneExist= migrationServices.checkProcessIfExist(processMigrationInfo.getCloneProcessId());
//			 originalExist= migrationServices.checkProcessIfExist(processMigrationInfo.getOriginalProcessId());
//			 List<String> ids= Arrays.asList(processMigrationInfo.getCloneProcessId(),processMigrationInfo.getOriginalProcessId());
//			if( cloneExist && originalExist){
//				
//				for(String id: ids) {
//				reqBody.put("processInstanceId", id);
//				reqBody.put("finished", false);
//				CloseableHttpResponse response = migrationServices.callApiCore(client, "enterprise/historic-tasks/query", reqBody, "POST");
//				String result = EntityUtils.toString(response.getEntity(), "UTF-8");
//				resultList.add(new JSONObject(result));
//				}
//				if(resultList.get(0).optString("taskDefinitionKey").equalsIgnoreCase(resultList.get(1).optString("taskDefinitionKey"))) {
//					validate=true;
//					display=true;
//				}
//				else {
//					display=true;
//				}
//				
//			}
//			else if(cloneExist) {
//				display=true;
//			}
//			processInfoAsJson = new JSONObject(processMigrationInfo);
//			processInfoAsJson.put("validate", validate);
//			processInfoAsJson.put("display", display);
//			allProcessDataAsJson.put(processInfoAsJson);
//		}
		JSONObject processInfoAsJson =new JSONObject();
		boolean cloneExist= false;
		boolean originalExist=false;
		boolean validate= false;
		boolean display= false;
		JSONObject reqBody =new JSONObject();
		List<JSONObject> resultList= new ArrayList<>();
		CloseableHttpClient client = HttpClients.createDefault();
		for(ProcessMigrationInfo processMigrationInfo :allProcessData ) {
			 cloneExist= migrationServices.checkProcessIfExist(processMigrationInfo.getCloneProcessId());
			 originalExist= migrationServices.checkProcessIfExist(processMigrationInfo.getOriginalProcessId());
			 List<String> ids= Arrays.asList(processMigrationInfo.getCloneProcessId(),processMigrationInfo.getOriginalProcessId());
			if( cloneExist && originalExist){
				display=true;
				for(String id: ids) {
				reqBody.put("processInstanceId", id);
				reqBody.put("finished", false);
				CloseableHttpResponse response = migrationServices.callApiCore(client, "enterprise/historic-tasks/query", reqBody, "POST");
				String result = EntityUtils.toString(response.getEntity(), "UTF-8");
				System.out.println("json array length: "+new JSONObject(result).optJSONArray("data").length());
				System.out.println("process ids: "+ id +"???????????????????????");
				if(new JSONObject(result).optJSONArray("data").isEmpty()) {
					break;
				}
				resultList.add(new JSONObject(result).optJSONArray("data").getJSONObject(0));
				}
				//taskDefinitionKey
				if(resultList.size()==2) {
				if(resultList.get(0).optString("taskDefinitionKey").equalsIgnoreCase(resultList.get(1).optString("taskDefinitionKey"))) {
					validate=true;
				}
				}
			}
			else if(cloneExist) {
				display=true;
			}
			if(display) {
			processInfoAsJson = new JSONObject(processMigrationInfo);
			processInfoAsJson.put("validate", validate);
//			processInfoAsJson.put("display", display);
			allProcessDataAsJson.put(processInfoAsJson);
			}
			else {
				total--;
				if(processMigrationInfo.getStatus()==200) {
					successfulProcessCount--;
				}
				else {
					failureProcessCount--;
				}
			}
			validate=false;
			display=false;
			resultList= new ArrayList<>();
		}
		JSONObject dataAsJSON = new JSONObject();
		dataAsJSON.put("JobId", jobId);
		dataAsJSON.put("totalProcess", total);
		dataAsJSON.put("successfulProcess", successfulProcessCount);
		dataAsJSON.put("failureProcess", failureProcessCount);
		dataAsJSON.put("processData", allProcessData);
		dataAsJSON.put("processData", allProcessDataAsJson);
		return dataAsJSON.toString();
	}
	
	//========================
	@PostMapping("/deleteProcessInst")
	public String deleteOriginalProcess(@RequestBody DeleteReqBody deleteReqBody)
					throws ClientProtocolException, IOException {
		JSONObject resultJSON=migrationServices.cancelAndDeleteOriginalProcessInst(deleteReqBody.name,
				deleteReqBody.processInstanceId, deleteReqBody.jobId);

		return resultJSON.toString();
	}//=============
}
