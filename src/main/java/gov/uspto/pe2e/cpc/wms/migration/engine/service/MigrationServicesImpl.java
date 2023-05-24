package gov.uspto.pe2e.cpc.wms.migration.engine.service;

import static gov.uspto.pe2e.cpc.wms.migration.engine.configration.ActiveMQConfig.PROCESS_QUEUE;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.commons.codec.binary.Base64;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.CloseableHttpResponse;
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
import org.json.XML;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Service;

import gov.uspto.pe2e.cpc.wms.migration.engine.constant.APIResponse;
import gov.uspto.pe2e.cpc.wms.migration.engine.constant.Constant;
import gov.uspto.pe2e.cpc.wms.migration.engine.repository.mysql.MigrateProcess;
import gov.uspto.pe2e.cpc.wms.migration.engine.repository.mysql.MigrateTask;
import gov.uspto.pe2e.cpc.wms.migration.engine.repository.mysql.OldProcess;
import gov.uspto.pe2e.cpc.wms.migration.engine.repository.mysql.ProcessMigrationInfo;
import gov.uspto.pe2e.cpc.wms.migration.engine.repository.mysql.Task;
import gov.uspto.pe2e.cpc.wms.migration.engine.repository.oracle.ActHiTaskinst;
import gov.uspto.pe2e.cpc.wms.migration.engine.repository.oracle.ActHiTaskinstRepository;

@Service
@Profile("dev")
public class MigrationServicesImpl implements MigrationServices {

	@Autowired
	private Environment env;

	@Autowired
	Constant constant;

	@Autowired
	MigrateProcess migrateProcess;

	@Autowired
	OldProcess oldProcess;

	@Autowired
	ProcessMigrationInfo migrationInfo;

	private static final Logger log = LoggerFactory.getLogger(MigrationServicesImpl.class);

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

	@JmsListener(destination = PROCESS_QUEUE)
	public void migrationProcces(String message) {

		try {

			JSONObject jsonObject = objectToJSONObject(message);

			JSONObject originalProInfo = callGetApi("enterprise/process-instances/", jsonObject.getString("id"));

			JSONObject createProcessResult = getDataFromApi("enterprise/process-instances", originalProInfo, "");
			migrateProcess = new MigrateProcess();
			migrateProcess = constant.saveMigrateProcessOnDB(createProcessResult);

			JSONObject originalProcessTaskObject = getDataFromApi("enterprise/tasks/query", jsonObject, "");
			JSONArray originalTasksArray = originalProcessTaskObject.getJSONArray("data");
			checkXMLFile(originalTasksArray, jsonObject.getString("businessKey"));

			Map<String, JSONObject> oldTaskWithVariables = new HashMap<>();

			for (Object oldTask : originalTasksArray) {
				JSONObject oldTaskInfo = objectToJSONObject(oldTask);
				oldTaskWithVariables.put(oldTaskInfo.getString("taskDefinitionKey"), oldTaskInfo);
			}

			// for (Object oldTask : originalTasksArray) {

//			JSONObject oldTaskInfo = objectToJSONObject(oldTask);

			// JSONArray jsonArray = oldTaskInfo.getJSONArray("variables");

//			oldTaskWithVariables = new HashMap<>();
//			oldTaskWithVariables.put(oldTaskInfo.getString("taskDefinitionKey"), oldTaskInfo);

			MigrateTask migrateTask = null;
			Task task = null;
			String tempTaskId = "";
			JSONObject js = new JSONObject();
			do {
				JSONObject migrateProcessActiveTaskObject = getDataFromApi("enterprise/historic-tasks/query",
						createProcessResult, "");
				migrateTask = new MigrateTask();
				if (migrateProcessActiveTaskObject.getInt("total") != 0) {

					migrateTask.setTaskDefKey(((JSONObject) migrateProcessActiveTaskObject.getJSONArray("data").get(0))
							.getString("taskDefinitionKey"));

					migrateTask.setTaskId(
							((JSONObject) migrateProcessActiveTaskObject.getJSONArray("data").get(0)).getString("id"));

					if (oldTaskWithVariables.containsKey(migrateTask.getTaskDefKey())) {
						tempTaskId = js.optString("id");
						js = oldTaskWithVariables.get(migrateTask.getTaskDefKey());
						tempTaskId = tempTaskId.isEmpty() ? js.optString("id") : tempTaskId;
						JSONArray jsonArray = js.getJSONArray("variables");

						for (Object arrayVar : jsonArray) {
							JSONObject arrayVarObj = objectToJSONObject(arrayVar);

							js.put(arrayVarObj.getString("name"), arrayVarObj.get("value"));

						}

						js.remove("variables");

						task = new Task();

						task.setTaskDefKey(js.getString("taskDefinitionKey"));
						task.setTaskId(js.optString("id"));

//				JSONObject migrateProcessActiveTaskObject = getDataFromApi("enterprise/historic-tasks/query",
//						createProcessResult, "");
//				MigrateTask migrateTask = new MigrateTask();
//
//				migrateTask.setTaskDefKey(((JSONObject) migrateProcessActiveTaskObject.getJSONArray("data").get(0))
//						.getString("taskDefinitionKey"));
//
//				migrateTask.setTaskId(
//						((JSONObject) migrateProcessActiveTaskObject.getJSONArray("data").get(0)).getString("id"));

						claimTask(migrateTask.getTaskId());

						if (js.getString("taskDefinitionKey").equals(migrateTask.getTaskDefKey())) {
							getDataFromApi("enterprise/task-forms/", js, migrateTask.getTaskId());

							boolean addOneSec = true;

							if (!tempTaskId.isEmpty()) {

								addOneSec = false;
							}

							changeCompletedTaskInfo(tempTaskId, migrateTask.getTaskId(), addOneSec);

						}

					}
				}
			} while ((migrateTask.getTaskDefKey() == null && migrateTask.getTaskDefKey().isEmpty())
					|| oldTaskWithVariables.containsKey(migrateTask.getTaskDefKey()));
			constant.saveOldAndNewProcessOnDB(migrateProcess, oldProcess, migrationInfo.getStatus(),
					migrationInfo.getErrorMSG(),0);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void checkXMLFile(JSONArray tasksArray, String processName) {

		JSONObject xmlJson = convertXMLToJSON();

		/*
		 * "migration": { "processes": { "process": [
		 */
		Object processObject = xmlJson.getJSONObject("migration").getJSONObject("processes").get("process");

		JSONObject processJsonObject = null;

		if (processObject instanceof JSONArray) {

			for (Object jsonObject : (JSONArray) processObject) {

				if (((JSONObject) jsonObject).getString("name").equalsIgnoreCase(processName)) {
					processJsonObject = (JSONObject) jsonObject;
				}

			}

		} else {
			if (((JSONObject) processObject).getString("name").equalsIgnoreCase(processName)) {
				processJsonObject = (JSONObject) processObject;
			}

		}

		if (processJsonObject != null) {

			if (processJsonObject.get("task") instanceof JSONArray) {

				JSONArray tasksJsonObjects = processJsonObject.getJSONArray("task");

				for (Object jsonObject : tasksJsonObjects) {

					boolean isExist = false;
					for (Object orgenalTaskObject : tasksArray) {

						if (((JSONObject) jsonObject).getString("id")
								.equals(((JSONObject) orgenalTaskObject).getString("taskDefinitionKey"))) {

							JSONArray orginalVariableObject = ((JSONObject) orgenalTaskObject)
									.getJSONArray("variables");
							if (((JSONObject) jsonObject).get("field") instanceof JSONArray) {

								JSONArray xmlVariableObject = ((JSONObject) jsonObject).getJSONArray("field");
								orginalVariableObject.put(xmlVariableObject);
								isExist = true;
								break;
							} else {
								orginalVariableObject.put(((JSONObject) jsonObject).get("field"));
								isExist = true;
								break;
							}
						}

					}
					if (!isExist) {

						JSONObject newTaskObject = new JSONObject();
						newTaskObject.put("taskDefinitionKey", ((JSONObject) jsonObject).getString("id"));
						newTaskObject.put("variables", ((JSONObject) jsonObject).get("field"));
						tasksArray.put(newTaskObject);
					}

				}
			} else {
				JSONObject tasksJsonObjects = processJsonObject.getJSONObject("task");

				boolean isExist = false;
				for (Object orgenalTaskObject : tasksArray) {

					if (((JSONObject) tasksJsonObjects).getString("id")
							.equals(((JSONObject) orgenalTaskObject).getString("taskDefinitionKey"))) {

						JSONArray orginalVariableObject = ((JSONObject) orgenalTaskObject).getJSONArray("variables");

						if (((JSONObject) tasksJsonObjects).get("field") instanceof JSONArray) {

							JSONArray xmlVariableObject = new JSONArray(((JSONObject) tasksJsonObjects).get("field"));
							orginalVariableObject.put(xmlVariableObject);
							isExist = true;
							break;
						} else {
							orginalVariableObject.put(((JSONObject) tasksJsonObjects).get("field"));
							isExist = true;
							break;
						}
					}

				}
				if (!isExist) {

					JSONObject newTaskObject = new JSONObject();
					newTaskObject.put("taskDefinitionKey", ((JSONObject) tasksJsonObjects).getString("id"));
					newTaskObject.put("variables", ((JSONObject) tasksJsonObjects).get("field"));
					tasksArray.put(newTaskObject);
				}

			}
		}
	}

	public JSONObject getDataFromApi(String endPointName, Object reqBody, String pathParam) {
		JSONObject reqBodyConverter = objectToJSONObject(reqBody);
		JSONObject jsonReqBody = createReqBody(endPointName, reqBodyConverter, pathParam,  false);
		JSONObject resultJSONObject = new JSONObject();
		resultJSONObject = callPostApi(endPointName, jsonReqBody, pathParam);
		return resultJSONObject;
	}

	@Override
	public JSONObject createReqBody(String endPointName, JSONObject bodyData, String pathParam,  boolean singleProcess) {
		JSONObject reqBody = null;
		if (endPointName.equals("enterprise/historic-process-instances/query")) {
			reqBody = new JSONObject();

//			reqBody.put("startedBefore", ((MigrationInputData) bodyData).getToDate());
//			reqBody.put("includeProcessVariables", true);
//			reqBody.put("processInstanceId", "1371482");
//			reqBody.put("processInstanceId", "1376927");
			reqBody.put("processInstanceId", "1053538");

		} else if (endPointName.equals("enterprise/process-instances")) {

			reqBody = new JSONObject();
			OldProcess process = this.jsonObjctToOldProcess(bodyData);

			reqBody.put("businessKey", process.getBusinessKey());
			reqBody.put("name", process.getName().isEmpty() ? process.getBusinessKey() : process.getName());
			reqBody.put("processDefinitionKey", process.getProcessDefinitionKey());
			reqBody.put("variables", bodyData.getJSONArray("variables"));

		} else if (endPointName.equals("enterprise/tasks/query")) {
			OldProcess process = this.jsonObjctToOldProcess(bodyData);

			reqBody = new JSONObject();
			reqBody.put("processInstanceId", process.getProcessInstanceId());
			reqBody.put("includeTaskLocalVariables", true);
			reqBody.put("state", "completed");
			reqBody.put("sort", "created-asc");
			reqBody.put("size", 100);
		} else if (endPointName.equals("enterprise/historic-tasks/query")) {
			MigrateProcess process = this.jsonObjctToMigrateProcess(bodyData);
			reqBody = new JSONObject();
			reqBody.put("processInstanceId", process.getProcessInstanceId());
			reqBody.put("finished", false);

		} else if (endPointName.equals("enterprise/task-forms/")) {
			reqBody = new JSONObject();
			reqBody.put("outcome", " ");
			reqBody.put("values", bodyData);
		}
		return reqBody;
	}

	public JSONObject callPostApi(String endPointName, JSONObject reqBody, String pathParam) {
		CloseableHttpClient client = HttpClients.createDefault();

		String url = pathParam.isEmpty() ? endPointName : endPointName + pathParam;

		HttpPost httpPost = new HttpPost(
				env.getProperty("spring.alfresco.rootURI") + env.getProperty("spring.alfresco.apsURL") + url);

		httpPost.addHeader("Authorization", "Basic " + getEncryptedBasicAuthorizationCreds());
		StringEntity entity;
		String result = null;
		JSONObject resultJSONObject = null;
		try {
			entity = new StringEntity(reqBody.toString(), "UTF-8");
			httpPost.setEntity(entity);
			httpPost.setHeader("Accept", "application/json");
			httpPost.setHeader("Content-type", "application/json");

			CloseableHttpResponse response = client.execute(httpPost);

			result = EntityUtils.toString(response.getEntity(), "UTF-8");
			int statusCode = response.getStatusLine().getStatusCode();

			if (statusCode == 200) {
				if (!result.isEmpty()) {
					resultJSONObject = new JSONObject(result);

				} else {
					resultJSONObject = new JSONObject();
				}
				this.migrationInfo.setStatus(statusCode);
			} else {
				if (endPointName.equals("enterprise/historic-process-instances/query")) {

				} else if (endPointName.equals("enterprise/process-instances")) {
					this.migrationInfo.setStatus(-200);
					throw new Exception();

				} else if (endPointName.equals("enterprise/tasks/query")) {
					this.migrationInfo.setStatus(-100);
					throw new Exception();

				} else if (endPointName.equals("enterprise/historic-tasks/query")) {
					this.migrationInfo.setStatus(-300);
					throw new Exception();

				} else if (endPointName.equals("enterprise/task-forms/" + pathParam)) {
					this.migrationInfo.setStatus(-500);
					throw new Exception();

				}

			}

		} catch (Exception e) {

		}

		return resultJSONObject;

	}

	public JSONObject callGetApi(String endPointName, String pathParam) {
		CloseableHttpClient client = HttpClients.createDefault();
		JSONObject responseJson = null;
		try {
			String uri = endPointName.equals("enterprise/task-forms/") ? endPointName + pathParam + "/variables"
					: endPointName + pathParam;

			HttpGet httpGet = new HttpGet(
					env.getProperty("spring.alfresco.rootURI") + env.getProperty("spring.alfresco.apsURL") + uri);

			httpGet.addHeader("Authorization", "Basic " + getEncryptedBasicAuthorizationCreds());

			httpGet.setHeader("Accept", "application/json");
			CloseableHttpResponse response = client.execute(httpGet);

			String result = EntityUtils.toString(response.getEntity(), "UTF-8");
			int statusCode = response.getStatusLine().getStatusCode();

			if (statusCode == 200) {
				if (response.getEntity() instanceof JSONArray) {
					responseJson = new JSONObject();
					responseJson.put("variables", result);
				} else {
					responseJson = new JSONObject(result);
				}

			}

		} catch (IOException ex) {

		} finally {
			try {
				client.close();
			} catch (IOException ex) {

			}
		}

		return responseJson;
	}

	private String getEncryptedBasicAuthorizationCreds() {
		String creds = "";
		creds = env.getProperty("spring.alfresco.userName") + ":" + env.getProperty("spring.alfresco.password");
		Base64 base64 = new Base64();
		creds = new String(base64.encode(creds.getBytes()));
		return creds;
	}

	private JSONObject convertXMLToJSON() {
		String path = env.getProperty("spring.config.XML.path");
		String line = "", str = "";
		try (BufferedReader br = new BufferedReader(new FileReader(path))) {
			while ((line = br.readLine()) != null) {
				str += line;
			}
		} catch (FileNotFoundException e) {
			this.migrationInfo.setStatus(-800);
			log.error("Error in convertXMLToJSON: " + e.getMessage());

		} catch (IOException e) {
			this.migrationInfo.setStatus(-800);
			log.error("Error in convertXMLToJSON: " + e.getMessage());
		}
		log.info(str);
		JSONObject jsondata = XML.toJSONObject(str);
		return jsondata;
	}

	private int claimTask(String taskId) {

		CloseableHttpClient client = HttpClients.createDefault();
		try {
			HttpPut httpPut = new HttpPut(env.getProperty("spring.alfresco.rootURI")
					+ env.getProperty("spring.alfresco.apsURL") + "enterprise/tasks/" + taskId + "/action/claim");

			httpPut.addHeader("Authorization", "Basic " + getEncryptedBasicAuthorizationCreds());

			httpPut.setHeader("Accept", "application/json");
			httpPut.setHeader("Content-type", "application/json");
			CloseableHttpResponse response = client.execute(httpPut);
			int statusCode = response.getStatusLine().getStatusCode();
			return statusCode;
		} catch (IOException ex) {
			this.migrationInfo.setStatus(-400);
			log.error("Error in claimTask (" + taskId + ") " + ex.getMessage());
		} finally {
			try {
				client.close();
			} catch (IOException ex) {
				this.migrationInfo.setStatus(-400);
			}
		}

		return 0;

	}

	@Autowired
	ActHiTaskinstRepository actHiTaskinst;

	private void changeCompletedTaskInfo(String oldTaskId, String newTaskId, boolean addOneSec) {
		Optional<ActHiTaskinst> oldTaskInfo = actHiTaskinst.findById(oldTaskId);
		Optional<ActHiTaskinst> newTaskInfo = actHiTaskinst.findById(newTaskId);

		if (oldTaskInfo.isPresent() && newTaskInfo.isPresent()) {
			if (addOneSec) {
				newTaskInfo.get().setStartTime(oldTaskInfo.get().getStartTime().plusSeconds(1));
				newTaskInfo.get().setClaimTime(oldTaskInfo.get().getClaimTime().plusSeconds(1));
				newTaskInfo.get().setEndTime(oldTaskInfo.get().getEndTime().plusSeconds(1));

			} else {
				newTaskInfo.get().setStartTime(oldTaskInfo.get().getStartTime());
				newTaskInfo.get().setClaimTime(oldTaskInfo.get().getClaimTime());
				newTaskInfo.get().setEndTime(oldTaskInfo.get().getEndTime());
			}
			newTaskInfo.get().setDueDate(oldTaskInfo.get().getDueDate());
			newTaskInfo.get().setDuration(oldTaskInfo.get().getDuration());
			newTaskInfo.get().setAssignee(oldTaskInfo.get().getAssignee());

			actHiTaskinst.saveAndFlush(newTaskInfo.get());

			log.info("Changed successfully");

		} else {
			this.migrationInfo.setStatus(-600);
			log.error("Error in changing Completed Task Info");
			log.error("Old Task Info: " + oldTaskInfo.toString());
			log.error("New Task Info: " + newTaskInfo.toString());

		}
	}

	private OldProcess jsonObjctToOldProcess(JSONObject object) {

		OldProcess process = new OldProcess();
		process.setBusinessKey(object.getString("businessKey"));
		process.setName(object.getString("name"));
		process.setProcessDefinitionKey(object.getString("processDefinitionKey"));
		process.setProcessInstanceId(object.getString("id"));
		return process;

	}

	private MigrateProcess jsonObjctToMigrateProcess(JSONObject object) {
		MigrateProcess process = new MigrateProcess();
		process.setBusinessKey(object.getString("businessKey"));
		process.setName(object.getString("name"));
		process.setProcessInstanceId(object.getString("id"));
		process.setProcessDefinitionKey(object.getString("processDefinitionKey"));
		return process;

	}
//
//	@Override
//	public List<APIResponse>  getDataFromApi1(String endPointName, Object reqBody, String pathParam, String methodeType, boolean singleProcess)
//			throws Exception {
//		// TODO Auto-generated method stub
//		return null;
//	}

	@Override
	public JSONObject cancelAndDeleteOriginalProcessInst(String name, String cloneProcessId, int jobId)
			throws ClientProtocolException, IOException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public boolean checkProcessIfExist(String id) throws ClientProtocolException, IOException {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public CloseableHttpResponse callApiCore(CloseableHttpClient client, String uri, JSONObject reqBody,
			String methodType) throws ClientProtocolException, IOException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public JSONArray checkValidateAndDisplay(List<ProcessMigrationInfo> allProcessData)
			throws ClientProtocolException, IOException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public List<APIResponse> getDataFromApi(String endPointName, Object reqBody, String pathParam, String methodeType,
			boolean singleProcess) throws Exception {
		// TODO Auto-generated method stub
		return null;
	}

}
