package gov.uspto.pe2e.cpc.wms.migration.engine.service;

import static gov.uspto.pe2e.cpc.wms.migration.engine.configration.ActiveMQConfig.PROCESS_QUEUE;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.Queue;

import org.apache.commons.codec.binary.Base64;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Service;

import gov.uspto.pe2e.cpc.wms.migration.engine.adapter.MigrationInputData;
import gov.uspto.pe2e.cpc.wms.migration.engine.constant.APIResponse;
import gov.uspto.pe2e.cpc.wms.migration.engine.constant.Constant;
import gov.uspto.pe2e.cpc.wms.migration.engine.repository.mysql.MigrateProcess;
import gov.uspto.pe2e.cpc.wms.migration.engine.repository.mysql.MigrateTask;
import gov.uspto.pe2e.cpc.wms.migration.engine.repository.mysql.MigrationJob;
import gov.uspto.pe2e.cpc.wms.migration.engine.repository.mysql.MigrationJobRepoSpringImpl;
import gov.uspto.pe2e.cpc.wms.migration.engine.repository.mysql.OldProcess;
import gov.uspto.pe2e.cpc.wms.migration.engine.repository.mysql.ProcessMigrationInfo;
import gov.uspto.pe2e.cpc.wms.migration.engine.repository.mysql.ProcessMigrationInfoRepoSpringImpl;
import gov.uspto.pe2e.cpc.wms.migration.engine.repository.mysql.Task;
import gov.uspto.pe2e.cpc.wms.migration.engine.repository.oracle.ActHiTaskinst;
import gov.uspto.pe2e.cpc.wms.migration.engine.repository.oracle.ActHiTaskinstRepository;
import lombok.extern.slf4j.Slf4j;

@Service
@Profile("FixBug")
@Slf4j
public class MigrationServicesImplTest implements MigrationServices {

	@Autowired
	private Environment env;

	@Autowired
	Constant constant;

	@Autowired
	MigrationJobRepoSpringImpl migrationJobRepoSpringImpl;

	@Autowired
	ProcessMigrationInfoRepoSpringImpl processMigrationInfoRepoSpringImpl;

	private static final Logger LOGGER = LoggerFactory.getLogger(MigrationServicesImplTest.class);

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

	private List<APIResponse> getActiveTask(JSONObject createProcessResult,boolean singleProcess) throws Exception {
		List<APIResponse> apiResponses = getDataFromApi("enterprise/historic-tasks/query", createProcessResult, "",
				"POST", singleProcess);
		return apiResponses;

	}

	private List<APIResponse> climeAndCompleteTask(Task task, JSONObject oldTaskInfo, MigrateTask migrateTask,
			String tempTaskId, boolean addOneSec, int counter, boolean singleProcess) throws Exception {
		ProcessMigrationInfo migrationInfo = new ProcessMigrationInfo();
		List<APIResponse> apiResponses = new ArrayList<>();
		APIResponse apiResponse = new APIResponse();
		if (!tempTaskId.isEmpty()) {
			LOGGER.info("Start Clime Task For Task Id {0} " + migrateTask.getTaskId());
			apiResponses = getDataFromApi("enterprise/tasks/", null, migrateTask.getTaskId(), "PUT", false);
			LOGGER.info("Finish Clime Task For Task Id {0} " + migrateTask.getTaskId());
			LOGGER.info("Start Complete Task For Task Id {0} " + migrateTask.getTaskId());
			apiResponses = getDataFromApi("enterprise/task-forms/", oldTaskInfo, migrateTask.getTaskId(), "POST",
					singleProcess);

			apiResponses = changeCompletedTaskInfo(tempTaskId, migrateTask.getTaskId(), addOneSec, counter);
		} else {
			migrationInfo.setStatus(200);
			apiResponse.setMigrationInfo(migrationInfo);
			apiResponses.add(apiResponse);
		}

		return apiResponses;

	}

	private boolean isEqual(Task oldTask, MigrateTask newTask) {

		return oldTask.getTaskDefKey().equalsIgnoreCase(newTask.getTaskDefKey());

	}

	private Object saveOnDB(JSONObject jsonObject) {
		MigrateProcess migrateProcess = new MigrateProcess();
		migrateProcess = constant.saveMigrateProcessOnDB(jsonObject);

		return migrateProcess;

	}

	private Object setTaskData(Task task, MigrateTask migrateTask, JSONObject jsonObject) {

		if (task == null) {
			migrateTask = new MigrateTask();
			migrateTask.setTaskDefKey(
					((JSONObject) jsonObject.getJSONArray("data").get(0)).getString("taskDefinitionKey"));
			migrateTask.setTaskId(((JSONObject) jsonObject.getJSONArray("data").get(0)).getString("id"));
			return migrateTask;

		} else {
			task = new Task();
			task.setTaskDefKey(jsonObject.getString("taskDefinitionKey"));
			task.setTaskId(jsonObject.optString("id"));
			return task;
		}

	}

	@JmsListener(destination = PROCESS_QUEUE)
	public void migrationProcces(String message) throws JSONException, Exception {
		MigrateProcess migrateProcess = new MigrateProcess();
		OldProcess oldProcess = new OldProcess();
		ProcessMigrationInfo migrationInfo = new ProcessMigrationInfo();
		List<APIResponse> apiResponses = new ArrayList<>();
		String processId = "";
		JSONObject js = new JSONObject(message);
		String analyzedData = js.getString("analyzedData");
		int jobId = js.getInt("jobId");
		Map<String, JSONObject> newTasksMap = new HashMap<>();
		String lastActiveTaskId = "";
		MigrationJob migrationJob =  new MigrationJob();
		migrationJob = migrationJobRepoSpringImpl.findById(jobId).get();
//		LOGGER.info("Start Migration With the Below Param \n" + "js {} " + js + "\n" + "analyzed Data {}" + analyzedData
//				+ "\n" + "job Id {0}" + jobId);

		 try {
		JSONObject jsonObject = js.getJSONObject("jsonObject");
		LOGGER.info("Start Get All info for old Process for process Instance Id : {0}" + jsonObject.getString("id"));
		String oldProcessId= jsonObject.getString("id");
		apiResponses = getDataFromApi("enterprise/process-instances/", null, jsonObject.getString("id"), "GET", migrationJob.isSingleProcess());
		JSONObject originalProInfo = apiResponses.get(0).getJsonObject();

		oldProcess = jsonObjctToOldProcess(originalProInfo);
		apiResponses = getDataFromApi("enterprise/process-instances", originalProInfo, "", "POST", migrationJob.isSingleProcess());
		JSONObject createProcessResult = apiResponses.get(0).getJsonObject();

		migrateProcess = (MigrateProcess) saveOnDB(createProcessResult);
		processId = constant.saveOldAndNewProcessOnDB(migrateProcess, oldProcess, 0, "", jobId);
		apiResponses = getDataFromApi("enterprise/tasks/query", jsonObject, "", "POST", migrationJob.isSingleProcess());
		JSONObject originalProcessTaskObject = apiResponses.get(0).getJsonObject();
		JSONArray originalTasksArray = originalProcessTaskObject.getJSONArray("data");

//			log.info("-------------------------------------------------------------------------" + originalTasksArray);

		getDataFromAnalyzedData(originalTasksArray, analyzedData,
				jsonObject.optString("processDefinitionDeploymentId"));

		// *********
		JSONArray oldTasksArray = new JSONArray();
		// delete all new tasks from array
		// put all new tasks in a map newTasksMap
		for (Object task : originalTasksArray) {
			JSONObject taskJO = objectToJSONObject(task);

			if (taskJO.optString("id").isEmpty()) {
				newTasksMap.put(taskJO.optString("taskDefinitionKey"), taskJO);
			} else {
				oldTasksArray.put(taskJO);
			}
		}
		System.out.println(
				"************************************originalTasksArray.toString(): " + originalTasksArray.length());
		System.out.println("***********************************newTasksMap: " + newTasksMap.size());
		System.out.println("************************************oldTasksArray: " + oldTasksArray.length());

//			log.info("-------------------------------------------------------------------------" + originalTasksArray);
//			Queue<String> queue = new PriorityQueue<>();
//			newTasksMap.get("BRT01").optString("id");
		String tempTaskId = "";
		boolean addOneSec = false;
		JSONObject tempOldTaskInfo = new JSONObject();
		JSONObject lastActiveTask = new JSONObject();
		Task task = new Task();
		MigrateTask migrateTask = new MigrateTask();
		JSONObject newTaskJO = new JSONObject();
		int counter = 1;

		// for in all old tasks in original process 
		for (int i = 0; i < oldTasksArray.length(); i++) {

			Object oldTask = oldTasksArray.get(i);

			System.out.println(
					"############I: " + i + "#################################C: " + counter + "###################");
			JSONObject oldTaskInfo = objectToJSONObject(oldTask); 
			// get active task for cloned process in each iteration 
			apiResponses = getActiveTask(createProcessResult,migrationJob.isSingleProcess());
			JSONObject migrateProcessActiveTaskObject = apiResponses.get(0).getJsonObject();

			migrateTask = (MigrateTask) setTaskData(null, migrateTask, migrateProcessActiveTaskObject);

			task = (Task) setTaskData(task, null, oldTaskInfo);

//				JSONArray jsonArray = oldTaskInfo.getJSONArray("variables");
//				for (Object arrayVar : jsonArray) {
//					JSONObject arrayVarObj = objectToJSONObject(arrayVar);
//					oldTaskInfo.put(arrayVarObj.getString("name"), arrayVarObj.get("value"));
//
//				}
//				oldTaskInfo.remove("variables");
			System.out.println("********check isEqual*********");
			System.out.println("task: " + task.getTaskDefKey());
			System.out.println("migrated  task: " + migrateTask.getTaskDefKey());

			if (isEqual(task, migrateTask)) {
				System.out.println("oldTaskInfo.optString(\"endDate\").isEmpty(): " + oldTaskInfo.optString("endDate"));
				if (oldTaskInfo.optString("endDate").isEmpty()) {
					// check if task is still active
					System.out.println("****************End mig.****************");
					break;
				}
				System.out.println("*********isEqual********");
				JSONArray jsonArray = oldTaskInfo.getJSONArray("variables");
				for (Object arrayVar : jsonArray) {
					JSONObject arrayVarObj = objectToJSONObject(arrayVar);
					oldTaskInfo.put(arrayVarObj.getString("name"), arrayVarObj.get("value"));

				}
				oldTaskInfo.remove("variables");

//					tempTaskId = oldTaskInfo.optString("id");
//					tempTaskId = tempTaskId.isEmpty() ? tempOldTaskInfo.optString("id") : tempTaskId;
//
//					tempOldTaskInfo = oldTaskInfo;
//
//					if (!tempTaskId.isEmpty()) {
//
//						addOneSec = false;
//					}
				lastActiveTaskId = oldTaskInfo.optString("id");

//					apiResponses = climeAndCompleteTask(task, oldTaskInfo, migrateTask, tempTaskId, addOneSec);
				apiResponses = climeAndCompleteTask(task, oldTaskInfo, migrateTask, lastActiveTaskId, addOneSec,
						counter,migrationJob.isSingleProcess());
				counter = 1;
//					lastActiveTaskId = tempTaskId;
				lastActiveTask = oldTaskInfo;

			} else {
				System.out.println("********* not Equal********");
				// check if taskdef key in map exists
				if (newTasksMap.containsKey(migrateTask.getTaskDefKey())) {
					System.out.println("********* in map********");
					JSONArray jsonArray = newTasksMap.get(migrateTask.getTaskDefKey()).getJSONArray("variables");
					for (Object arrayVar : jsonArray) {
						JSONObject arrayVarObj = objectToJSONObject(arrayVar);
						newTaskJO.put(arrayVarObj.getString("name"), arrayVarObj.get("value"));

					}
					if (newTaskJO.has("variables")) {
						newTaskJO.remove("variables");
					}
					newTaskJO.put("id", oldTaskInfo.optString("id"));
					
					// repeat the same task for next iteration 
					i--;
					System.out.println("i= " + i);

					lastActiveTask = objectToJSONObject(oldTasksArray.get(i));
					lastActiveTaskId = lastActiveTask.optString("id");
					System.out.println("lastActiveTaskId: " + lastActiveTaskId);
					apiResponses = climeAndCompleteTask(task, newTaskJO, migrateTask, lastActiveTaskId, true, counter,migrationJob.isSingleProcess());

//						apiResponses = getActiveTask(createProcessResult);
//						migrateProcessActiveTaskObject = apiResponses.get(0).getJsonObject();
//						migrateTask = (MigrateTask) setTaskData(null, migrateTask, migrateProcessActiveTaskObject);
//						
					addOneSec = true;
					counter++;
					// else
					// migrated

				}
				/*
				 * if (queue.isEmpty()) { queue.add(oldTaskInfo.toString());// 1 tempOldTaskInfo
				 * = oldTaskInfo;// 1 continue; } else { if
				 * (Constant.objectToJSONObject(queue.peek()).getString("taskDefinitionKey")
				 * .equalsIgnoreCase(migrateTask.getTaskDefKey())) {
				 * 
				 * tempOldTaskInfo = oldTaskInfo; tempTaskId = tempTaskId.isEmpty() ?
				 * tempOldTaskInfo.optString("id") :
				 * Constant.objectToJSONObject(queue.peek()).getString("id"); apiResponses =
				 * climeAndCompleteTask(task, Constant.objectToJSONObject(queue.poll()),
				 * migrateTask, tempTaskId, true); queue.add(oldTaskInfo.toString()); } else {
				 * queue.add(oldTaskInfo.toString()); } }
				 */
			}
		}
		/*
		 * Map<String, JSONObject> map = new HashMap<>(); while (!queue.isEmpty()) {
		 * if(map.containsKey(Constant.objectToJSONObject(queue.peek()).getString(
		 * "taskDefinitionKey")))
		 * log.info("****************************************************************");
		 * 
		 * map.put(Constant.objectToJSONObject(queue.peek()).getString(
		 * "taskDefinitionKey"), Constant.objectToJSONObject(queue.poll())); }
		 * 
		 * apiResponses = getActiveTask(createProcessResult); JSONObject
		 * migrateProcessActiveTaskObject = apiResponses.get(0).getJsonObject(); // if
		 * (!Constant.objectToJSONObject(queue.peek()).has("id")) { // tempTaskId =
		 * tempOldTaskInfo.optString("id").isEmpty() ? tempTaskId // :
		 * tempOldTaskInfo.optString("id"); // } else { // tempTaskId =
		 * Constant.objectToJSONObject(queue.peek()).getString("id"); // } migrateTask =
		 * (MigrateTask) setTaskData(null, migrateTask, migrateProcessActiveTaskObject);
		 * 
		 * while (map.containsKey(migrateTask.getTaskDefKey())) {
		 * 
		 * // tempOldTaskInfo = map.get(migrateTask.getTaskDefKey()); // if
		 * (Constant.objectToJSONObject(queue.peek()).getString("taskDefinitionKey") //
		 * .equalsIgnoreCase(migrateTask.getTaskDefKey())) { apiResponses =
		 * climeAndCompleteTask(task, map.get(migrateTask.getTaskDefKey()), migrateTask,
		 * tempTaskId, true);
		 * 
		 * apiResponses = getActiveTask(createProcessResult);
		 * migrateProcessActiveTaskObject = apiResponses.get(0).getJsonObject();
		 * migrateTask = (MigrateTask) setTaskData(null, migrateTask,
		 * migrateProcessActiveTaskObject); map.remove(migrateTask.getTaskDefKey());
		 * 
		 * }
		 */

		// }
//				else {
//					
//				}

		// }

		System.out.println("newTasksMap tasks def: " + newTasksMap.keySet());

//			if(lastActiveTask.optString("endDate").isEmpty()) {
//				constant.saveOldAndNewProcessOnDBV2(migrateProcess, oldProcess,
//						apiResponses.get(0).getMigrationInfo().getStatus(),
//						apiResponses.get(0).getMigrationInfo().getErrorMSG(), jobId, processId);
//			}
		JSONObject reqBody = new JSONObject();
		reqBody.put("id", oldProcess.getProcessInstanceId());
		
		apiResponses = getActiveTask(reqBody,migrationJob.isSingleProcess());
		lastActiveTask = apiResponses.get(0).getJsonObject();
		System.out.println("********************************************************************");
		System.out.println("lastActiveTask: "
				+ ((JSONObject) lastActiveTask.getJSONArray("data").get(0)).getString("taskDefinitionKey"));
		while (true) {
			
			System.out.println("*********in while *************");
			System.out.println("migrateTask.getTaskDefKey(): " + migrateTask.getTaskDefKey());

			apiResponses = getActiveTask(createProcessResult,migrationJob.isSingleProcess());
			JSONObject migrateProcessActiveTaskObject = apiResponses.get(0).getJsonObject();
			migrateTask = (MigrateTask) setTaskData(null, migrateTask, migrateProcessActiveTaskObject);

			if ((((JSONObject) lastActiveTask.getJSONArray("data").get(0)).getString("taskDefinitionKey"))
					.equals(migrateTask.getTaskDefKey())) {
				System.out.println("break 1");
				break;
			}

			if (!newTasksMap.containsKey(migrateTask.getTaskDefKey())) {
				System.out.println("break 2");
				break;
			}
			counter++;
			JSONArray jsonArray = newTasksMap.get(migrateTask.getTaskDefKey()).getJSONArray("variables");
			for (Object arrayVar : jsonArray) {
				JSONObject arrayVarObj = objectToJSONObject(arrayVar);
				newTaskJO.put(arrayVarObj.getString("name"), arrayVarObj.get("value"));

			}
			if (newTaskJO.has("variables")) {
				newTaskJO.remove("variables");
			}
			newTaskJO.put("id", lastActiveTaskId);
			apiResponses = climeAndCompleteTask(task, newTaskJO, migrateTask, newTaskJO.optString("id"), true, counter,migrationJob.isSingleProcess());
			lastActiveTaskId = migrateTask.getTaskId();

		}

		 } catch (Exception e) {

			LOGGER.error("Error in Migration Process:"+e.getMessage());
			if (apiResponses.get(0).getMigrationInfo().getStatus() == 200) {
				migrationInfo.setErrorMSG("Internel Server Error");
				migrationInfo.setStatus(-500);
				apiResponses.get(0).setMigrationInfo(migrationInfo);
			}

		 }
		if (processId == null || processId.isEmpty()) {
			migrateProcess.setProcessInstanceId("0");
			migrateProcess.setName(oldProcess.getName());
			migrateProcess.setBusinessKey(oldProcess.getBusinessKey());
			migrateProcess.setProcessDefinitionKey(oldProcess.getProcessDefinitionKey());
			constant.saveOldAndNewProcessOnDB(migrateProcess, oldProcess, migrationInfo.getStatus(),
					"ERROR in Create Process ", jobId);
		} else {
			constant.saveOldAndNewProcessOnDBV2(migrateProcess, oldProcess,
					apiResponses.get(0).getMigrationInfo().getStatus(),
					apiResponses.get(0).getMigrationInfo().getErrorMSG(), jobId, processId);
		}
		constant.decreseJmsCount();

		if (constant.getJmsCount() == 0) {
			Optional<MigrationJob> migrationJob2 = migrationJobRepoSpringImpl.findById(jobId);
			if (migrationJob2.isPresent()) {
				MigrationJob job = migrationJob2.get();
				job.setStatus("migrationFinished");
				migrationJobRepoSpringImpl.saveAndFlush(job);
				LOGGER.info("Finished Migration Process For Job Id {0} " + jobId);
			}
		}
	}

	private void getDataFromAnalyzedData(JSONArray tasksArray, String analyzedDataStr, String deploymentId) {
//		LOGGER.info("Start Get Analysis Data for Deployment Id : {}" + deploymentId + "and Analyzed Data is {}"
//				+ analyzedDataStr);
		JSONArray analyzedData = new JSONArray(analyzedDataStr), variablesJA = new JSONArray(), tempJA;
		JSONObject deploymentJOData = new JSONObject(), tempJO, varJO, tempJO2;
		boolean isExist;
		for (Object object : analyzedData) {
			tempJO = (JSONObject) object;
			if (tempJO.has("DeploymentId") && tempJO.getString("DeploymentId").equals(deploymentId)) {
				tempJA = tempJO.getJSONArray("data");
				if (!tempJA.isEmpty())
					deploymentJOData = tempJA.getJSONObject(0);

			}
		}

		if (!deploymentJOData.isEmpty()) {

			for (String taskId : deploymentJOData.keySet()) {

				isExist = false;
				for (Object orgenalTaskObject : tasksArray) {

					if (taskId.equals(((JSONObject) orgenalTaskObject).getString("taskDefinitionKey"))) {

						tempJO = deploymentJOData.getJSONObject(taskId);
						if (tempJO.has("formFieldList")) {
							variablesJA = tempJO.getJSONArray("formFieldList");

							JSONArray orginalVariableObject = ((JSONObject) orgenalTaskObject)
									.getJSONArray("variables");

							if (!variablesJA.isEmpty())
								for (Object var : variablesJA) {
									varJO = (JSONObject) var;
									tempJO2 = new JSONObject();
									tempJO2.put("name", varJO.getString("id"));
									tempJO2.put("value", varJO.getString("value"));
									orginalVariableObject.put(tempJO2);
								}

						}
						isExist = true;
					}

				}

				if (!isExist) {
					JSONArray newTaskArray = new JSONArray();

					tempJO = deploymentJOData.getJSONObject(taskId);

					if (tempJO.has("formFieldList")) {
						variablesJA = tempJO.getJSONArray("formFieldList");

						if (!variablesJA.isEmpty())
							for (Object var : variablesJA) {
								varJO = (JSONObject) var;
								tempJO2 = new JSONObject();
								tempJO2.put("name", varJO.getString("id"));
								tempJO2.put("value", varJO.getString("value"));
								newTaskArray.put(tempJO2);
							}
					}
					if (!newTaskArray.isEmpty()) {
						tempJO2 = new JSONObject();
						tempJO2.put("taskDefinitionKey", taskId);
						tempJO2.put("variables", newTaskArray);
						tasksArray.put(tempJO2);
					}

				}

			}
		}
//		LOGGER.info(
//				"Finish Get Analysis Data for Deployment Id : {0}" + deploymentId + "The result is {1}" + tasksArray);
	}

	@Override
	public List<APIResponse> getDataFromApi(String endPointName, Object reqBody, String pathParam, String methodeType,
			boolean singleProcess) throws Exception {

		JSONObject reqBodyConverter = new JSONObject();
		JSONObject jsonReqBody = new JSONObject();

		if (methodeType.equals("POST")) {

			if (reqBody instanceof MigrationInputData) {
				reqBodyConverter = MigrationInputDataToJSON((MigrationInputData) reqBody);
			} else {
				reqBodyConverter = objectToJSONObject(reqBody);
				reqBodyConverter.put("processBusinessKey", reqBody);		
//				reqBodyConverter.put("processUUID", ((MigrationInputData) reqBody).getProcessUUID());
			}

			jsonReqBody = createReqBody(endPointName, reqBodyConverter, pathParam, singleProcess);

		}
		List<APIResponse> resultAsMap = callApi(endPointName, jsonReqBody, pathParam, methodeType);
		return resultAsMap;
	}

	private JSONObject MigrationInputDataToJSON(MigrationInputData reqBody) {
		JSONObject inputData = new JSONObject();

		inputData.put("fromDate", reqBody.getFromDate());
		inputData.put("toDate", reqBody.getToDate());
		inputData.put("processUUID", reqBody.getProcessUUID());
		inputData.put("singleProcess", reqBody.isSingleProcess());
		return inputData;
	}

	@Override
	public JSONObject createReqBody(String endPointName, JSONObject bodyData, String pathParam, boolean singleProcess) {
		JSONObject reqBody = null;
		if (endPointName.equals("enterprise/historic-process-instances/query")) {
			reqBody = new JSONObject();
			if (!singleProcess) {
			reqBody.put("startedAfter", bodyData.getString("fromDate"));
			reqBody.put("startedBefore", bodyData.getString("toDate"));
			} else {
				// put process business key here
				reqBody.put("processBusinessKey", bodyData.optString("processUUID"));
			}

			reqBody.put("includeProcessVariables", true);
			LOGGER.info("Get All Info for Processes Need Migration. \n  Request Body : {0} " + reqBody
					+ " For enterprise/historic-process-instances/query  API");

		} else if (endPointName.equals("enterprise/process-instances")) {

			reqBody = new JSONObject();
			OldProcess process = this.jsonObjctToOldProcess(bodyData);

			reqBody.put("businessKey", process.getBusinessKey());
			reqBody.put("name", process.getName().isEmpty() ? process.getBusinessKey() : process.getName());
			reqBody.put("processDefinitionKey", process.getProcessDefinitionKey());
			reqBody.put("variables", bodyData.getJSONArray("variables"));
			LOGGER.info("Start a Process Instance. \n Request Body {0} " + reqBody
					+ "for enterprise/process-instances API");

		} else if (endPointName.equals("enterprise/tasks/query")) {
			OldProcess process = this.jsonObjctToOldProcess(bodyData);

			reqBody = new JSONObject();
			reqBody.put("processInstanceId", process.getProcessInstanceId());
			reqBody.put("includeTaskLocalVariables", true);
			reqBody.put("state", "completed");
			reqBody.put("sort", "created-asc");
			reqBody.put("size", 100);
			LOGGER.info("Get List Tasks. \n Request Body {0} " + reqBody + "for enterprise/tasks/query API");
		} else if (endPointName.equals("enterprise/historic-tasks/query")) {
			MigrateProcess process = this.jsonObjctToMigrateProcess(bodyData);
			reqBody = new JSONObject();
			reqBody.put("processInstanceId", process.getProcessInstanceId());
			reqBody.put("finished", false);
			LOGGER.info("Get Active Tasks For New Process. \n Request Body {0} " + reqBody
					+ "for enterprise/historic-tasks/query API");

		} else if (endPointName.equals("enterprise/task-forms/")) {
			reqBody = new JSONObject();
			reqBody.put("outcome", " ");
			reqBody.put("values", bodyData);
			// LOGGER.info("Complete Task Form. \n Request Body {0} " + reqBody + "for
			// enterprise/task-forms/ API");
		}

		return reqBody;
	}

	private List<APIResponse> callApi(String endPointName, JSONObject reqBody, String pathParam, String methodType)
			throws Exception {
		ProcessMigrationInfo migrationInfo = new ProcessMigrationInfo();
		CloseableHttpClient client = HttpClients.createDefault();

		String uri = pathParam.isEmpty() ? endPointName
				: endPointName.equals("enterprise/task-forms/") ? endPointName + pathParam
						: endPointName.equals("enterprise/tasks/") ? endPointName + pathParam + "/action/claim"
								: endPointName + pathParam;
		String alfrescoURL = env.getProperty("spring.alfresco.rootURI") + env.getProperty("spring.alfresco.apsURL");

		// LOGGER.info("Start Call API {0} " + alfrescoURL + "\n" + "Method Type {1}" +
		// methodType);

		int statusCode = 0;

		// StringEntity entity;
		String result = null;
		JSONObject resultJSONObject = null;
		CloseableHttpResponse response = null;
		try {
			response = callApiCore(client, uri, reqBody, methodType);
			statusCode = response.getStatusLine().getStatusCode();
			result = EntityUtils.toString(response.getEntity(), "UTF-8");

			/*
			 * if (methodType.equalsIgnoreCase("POST")) { HttpPost httpPost = new
			 * HttpPost(alfrescoURL + uri);
			 * 
			 * httpPost.addHeader("Authorization", "Basic " +
			 * getEncryptedBasicAuthorizationCreds()); entity = new
			 * StringEntity(reqBody.toString(), "UTF-8"); httpPost.setEntity(entity);
			 * httpPost.setHeader("Accept", "application/json");
			 * httpPost.setHeader("Content-type", "application/json");
			 * 
			 * response = client.execute(httpPost);
			 * 
			 * result = EntityUtils.toString(response.getEntity(), "UTF-8");
			 * 
			 * statusCode = response.getStatusLine().getStatusCode(); } else if
			 * (methodType.equalsIgnoreCase("GET")) { HttpGet httpGet = new
			 * HttpGet(alfrescoURL + uri);
			 * 
			 * httpGet.addHeader("Authorization", "Basic " +
			 * getEncryptedBasicAuthorizationCreds());
			 * 
			 * httpGet.setHeader("Accept", "application/json");
			 * 
			 * response = client.execute(httpGet);
			 * 
			 * result = EntityUtils.toString(response.getEntity(), "UTF-8");
			 * 
			 * statusCode = response.getStatusLine().getStatusCode(); } else if
			 * (methodType.equalsIgnoreCase("DELETE")) { // HttpDelete httpDelete = new
			 * HttpDelete(alfrescoURL + uri); // httpDelete.addHeader("Authorization",
			 * "Basic " + getEncryptedBasicAuthorizationCreds()); // response =
			 * client.execute(httpDelete); // statusCode =
			 * response.getStatusLine().getStatusCode(); } else { client =
			 * HttpClients.createDefault(); HttpPut httpPut = new HttpPut(alfrescoURL +
			 * uri);
			 * 
			 * httpPut.addHeader("Authorization", "Basic " +
			 * getEncryptedBasicAuthorizationCreds());
			 * 
			 * httpPut.setHeader("Accept", "application/json");
			 * httpPut.setHeader("Content-type", "application/json"); response =
			 * client.execute(httpPut); result = EntityUtils.toString(response.getEntity(),
			 * "UTF-8"); statusCode = response.getStatusLine().getStatusCode();
			 * 
			 * }
			 */
			if (statusCode == 200) {
				migrationInfo.setStatus(statusCode);
				if (!result.isEmpty()) {
					if (response.getEntity() instanceof JSONArray) {
						resultJSONObject = new JSONObject();
						resultJSONObject.put("variables", result);
					} else {
						resultJSONObject = new JSONObject(result);
					}

				} else {
					resultJSONObject = new JSONObject();
				}
			} else {
				migrationInfo.setStatus(statusCode);
				migrationInfo.setErrorMSG("ERROR ON  " + endPointName + " API" + " " + result);
				LOGGER.error("Error When Call API {0} " + alfrescoURL + endPointName + "\n" + "With Status Code is: {} "
						+ statusCode + "And Result is {1}" + result);
				throw new Exception();

			}

			client.close();
		} catch (IOException ex) {
			migrationInfo.setStatus(-500);
			migrationInfo.setErrorMSG(ex.getMessage());
			LOGGER.error("Error When Call API {0} " + alfrescoURL + endPointName + "\n" + "With Status Code is: {1} "
					+ statusCode + "And Result is {2}" + result);
		}
//		LOGGER.info("Finish Call API {0} " + alfrescoURL + endPointName + "\n" + "With Status Code is: {1} "
//				+ statusCode + "And Result is {2}" + resultJSONObject);

		List<APIResponse> resultAsMap = new ArrayList<>();
		APIResponse apiResponse = new APIResponse();
		apiResponse.setJsonObject(resultJSONObject);
		apiResponse.setMigrationInfo(migrationInfo);
		resultAsMap.add(apiResponse);
		return resultAsMap;
	}

	private String getEncryptedBasicAuthorizationCreds() {
		String creds = "";
		creds = env.getProperty("spring.alfresco.userName") + ":" + env.getProperty("spring.alfresco.password");
		Base64 base64 = new Base64();
		creds = new String(base64.encode(creds.getBytes()));
		return creds;
	}

	@Autowired
	ActHiTaskinstRepository actHiTaskinst;

	private List<APIResponse> changeCompletedTaskInfo(String oldTaskId, String newTaskId, boolean addOneSec,
			int counter) {
		LOGGER.info("Start Change Completed Task Info  For Task Id {0} " + newTaskId);
		ProcessMigrationInfo migrationInfo = new ProcessMigrationInfo();
		Optional<ActHiTaskinst> oldTaskInfo = actHiTaskinst.findById(oldTaskId);
		Optional<ActHiTaskinst> newTaskInfo = actHiTaskinst.findById(newTaskId);

		if (oldTaskInfo.isPresent() && newTaskInfo.isPresent()) {
			if (addOneSec) {
				newTaskInfo.get().setStartTime(oldTaskInfo.get().getStartTime().plusSeconds(counter));
				newTaskInfo.get().setClaimTime(oldTaskInfo.get().getClaimTime().plusSeconds(counter));
				newTaskInfo.get().setEndTime(oldTaskInfo.get().getEndTime().plusSeconds(counter));

			} else {
				newTaskInfo.get().setStartTime(oldTaskInfo.get().getStartTime());
				newTaskInfo.get().setClaimTime(oldTaskInfo.get().getClaimTime());
				newTaskInfo.get().setEndTime(oldTaskInfo.get().getEndTime());
			}
			newTaskInfo.get().setDueDate(oldTaskInfo.get().getDueDate());
			newTaskInfo.get().setDuration(oldTaskInfo.get().getDuration());
			newTaskInfo.get().setAssignee(oldTaskInfo.get().getAssignee());

			actHiTaskinst.saveAndFlush(newTaskInfo.get());
			migrationInfo.setStatus(200);
			LOGGER.info("Changed successfully");

		} else {
			migrationInfo.setStatus(-600);
			migrationInfo.setErrorMSG("Error in changing Completed Task Info");

		}
		LOGGER.info("Finished Change Completed Task Info  For Task Id {0} " + newTaskId);
		APIResponse apiResponse = new APIResponse();
		apiResponse.setMigrationInfo(migrationInfo);
		List<APIResponse> apiResponses = new ArrayList<>();
		apiResponses.add(apiResponse);
		return apiResponses;
	}

	private OldProcess jsonObjctToOldProcess(JSONObject object) {

		OldProcess process = new OldProcess();
		process.setBusinessKey(object.optString("businessKey"));
		process.setName(object.optString("name"));
		process.setProcessDefinitionKey(object.optString("processDefinitionKey"));
		process.setProcessInstanceId(object.optString("id"));

		JSONArray variables = object.getJSONArray("variables");
		for (Object var : variables) {
			JSONObject varJsonObj = Constant.objectToJSONObject(var);
			String name = varJsonObj.optString("name");
			if (name.equals("PROPOSAL_ALIAS")) {
				process.setProjectNum(varJsonObj.optString("value"));
			}
		}

		return process;

	}

	private MigrateProcess jsonObjctToMigrateProcess(JSONObject object) {
		MigrateProcess process = new MigrateProcess();
		process.setBusinessKey(object.optString("businessKey"));
		process.setName(object.optString("name"));
		process.setProcessInstanceId(object.optString("id"));
		process.setProcessDefinitionKey(object.optString("processDefinitionKey"));
		return process;

	}

	public JSONObject cancelAndDeleteOriginalProcessInst(String name, String cloneProcessId, int jobId)
			throws ClientProtocolException, IOException {
		String alfrescoURL = env.getProperty("spring.alfresco.rootURI") + env.getProperty("spring.alfresco.apsURL");
		// initialization
		int statusCode = 0;
		String message = "";
		JSONObject resultJSON = new JSONObject();
		// find original process
		Optional<String> processInstId = processMigrationInfoRepoSpringImpl
				.findOriginalProcessInstanceIdByCloneIdAndJobId(cloneProcessId, jobId); // check QUERY
		if (!processInstId.isPresent()) {
			resultJSON.put("statusCode", -404);
			resultJSON.put("message", "The process instance does not exist");
			return resultJSON;
		}
		// call api
		String uri = "enterprise/process-instances/";
		String pathParam = processInstId.get();
		CloseableHttpClient client = HttpClients.createDefault();

		try {
			CloseableHttpResponse response = callApiCore(client, uri + pathParam, new JSONObject(), "DELETE");
			statusCode = response.getStatusLine().getStatusCode();
			// if statusCode == 200 call func again
			if (statusCode == 200) {
				resultJSON = cancelAndDeleteOriginalProcessInst(name, cloneProcessId, jobId);
				message = "Original process instance of name '" + name + "' has been deleted successfully";
				/*
				 * if process instance deleted successfully(entered 200 block) and then not
				 * found(returned from 404 block) in the last call
				 */
				if (resultJSON.optInt("statusCode") == 404) {
					// recreate JSON result to be successful
					resultJSON.remove("statusCode");
					resultJSON.remove("message");
					resultJSON.put("statusCode", 200);
					resultJSON.put("message",
							"Original process instance of name '" + name + "' has been deleted successfully");

				}
				return resultJSON;
			}
			// else return status code : 403 (no permission) / 404 (not found)
			else if (statusCode == 403) {
				message = "User does not have permission to cancel or remove the process instance";
			} else if (statusCode == 404) {
				message = "The process instance does not exist";
			} else {
				message = "An Error Occurred. Please try again";
			}
			// Catching Server Errors >> Code:-500
		} catch (IOException ex) {
			statusCode = -500;
			message = "An Error Occurred. Please try again";
			LOGGER.error("Error When Call API {0} " + alfrescoURL + uri + "\n" + "With Status Code is: {1} "
					+ statusCode + "And message is {2}" + ex.getMessage());

		} finally {
			client.close();
			resultJSON.put("statusCode", statusCode);
			resultJSON.put("message", message);
			return resultJSON;

		}

	}

	public boolean checkProcessIfExist(String id) throws ClientProtocolException, IOException {
		CloseableHttpClient client = HttpClients.createDefault();
		CloseableHttpResponse response = callApiCore(client, "enterprise/process-instances/" + id, new JSONObject(),
				"GET");
		int statusCode = response.getStatusLine().getStatusCode();
		if (statusCode == 200) {
			return true;
		}
		return false;
	}
	// ======================================

	public CloseableHttpResponse callApiCore(CloseableHttpClient client, String uri, JSONObject reqBody,
			String methodType) throws ClientProtocolException, IOException {
		String alfrescoURL = env.getProperty("spring.alfresco.rootURI") + env.getProperty("spring.alfresco.apsURL");

		LOGGER.info("Start Call API {0} " + alfrescoURL + "\n" + "Method Type {1}" + methodType);
		StringEntity entity;
		CloseableHttpResponse response = null;

		if (methodType.equalsIgnoreCase("POST")) {
			HttpPost httpPost = new HttpPost(alfrescoURL + uri);

			httpPost.addHeader("Authorization", "Basic " + getEncryptedBasicAuthorizationCreds());
			entity = new StringEntity(reqBody.toString(), "UTF-8");
			httpPost.setEntity(entity);
			httpPost.setHeader("Accept", "application/json");
			httpPost.setHeader("Content-type", "application/json");

			response = client.execute(httpPost);

		} else if (methodType.equalsIgnoreCase("GET")) {
			HttpGet httpGet = new HttpGet(alfrescoURL + uri);

			httpGet.addHeader("Authorization", "Basic " + getEncryptedBasicAuthorizationCreds());

			httpGet.setHeader("Accept", "application/json");

			response = client.execute(httpGet);

		} else if (methodType.equalsIgnoreCase("DELETE")) {
			HttpDelete httpDelete = new HttpDelete(alfrescoURL + uri);
			httpDelete.addHeader("Authorization", "Basic " + getEncryptedBasicAuthorizationCreds());
//			httpDelete.setHeader("Accept", "application/json");
			response = client.execute(httpDelete);

		} else {
			client = HttpClients.createDefault();
			HttpPut httpPut = new HttpPut(alfrescoURL + uri);

			httpPut.addHeader("Authorization", "Basic " + getEncryptedBasicAuthorizationCreds());

			httpPut.setHeader("Accept", "application/json");
			httpPut.setHeader("Content-type", "application/json");
			response = client.execute(httpPut);

		}

		return response;

	}

	public JSONArray checkValidateAndDisplay(List<ProcessMigrationInfo> allProcessData)
			throws ClientProtocolException, IOException {
		/*
		 * loop over allProcessData check if processes is exist Not exist: set display =
		 * false and validate=false for each row get active task for original and cloned
		 * process Exist:check equality by taskDefKey(ex: PIT05) Equal: set
		 * validate=true and display = true each Object in list should be converted to
		 * JSONObject convert whole list to JSONArray
		 */
		JSONArray allProcessDataAsJson = new JSONArray();
		JSONObject processInfoAsJson = new JSONObject();
		boolean cloneExist = false;
		boolean originalExist = false;
		boolean validate = false;
		boolean display = false;
		JSONObject reqBody = new JSONObject();
		List<JSONObject> resultList = new ArrayList<>();
		CloseableHttpClient client = HttpClients.createDefault();
		for (ProcessMigrationInfo processMigrationInfo : allProcessData) {
			cloneExist = checkProcessIfExist(processMigrationInfo.getCloneProcessId());
			originalExist = checkProcessIfExist(processMigrationInfo.getOriginalProcessId());
			List<String> ids = Arrays.asList(processMigrationInfo.getCloneProcessId(),
					processMigrationInfo.getOriginalProcessId());
			if (cloneExist && originalExist) {
				display = true;
				for (String id : ids) {
					reqBody.put("processInstanceId", id);
					reqBody.put("finished", false);
					CloseableHttpResponse response = callApiCore(client, "enterprise/historic-tasks/query", reqBody,
							"POST");
					String result = EntityUtils.toString(response.getEntity(), "UTF-8");
					System.out.println("json array length: " + new JSONObject(result).optJSONArray("data").length());
					System.out.println("process ids: " + id + "???????????????????????");
					if (new JSONObject(result).optJSONArray("data").isEmpty()) {
						break;
					}
					resultList.add(new JSONObject(result).optJSONArray("data").getJSONObject(0));
				}
				// taskDefinitionKey
				if (resultList.size() == 2) {
					if (resultList.get(0).optString("taskDefinitionKey")
							.equalsIgnoreCase(resultList.get(1).optString("taskDefinitionKey"))) {
						validate = true;
					}
				}
			} else if (cloneExist) {
				display = true;
			}
			if (display) {
				processInfoAsJson = new JSONObject(processMigrationInfo);
				processInfoAsJson.put("validate", validate);
//			processInfoAsJson.put("display", display);
				allProcessDataAsJson.put(processInfoAsJson);
			}
			validate = false;
			display = false;
			resultList = new ArrayList<>();
		}
		return allProcessDataAsJson;
	}
}
