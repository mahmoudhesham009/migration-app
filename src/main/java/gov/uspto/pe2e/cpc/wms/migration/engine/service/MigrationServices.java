package gov.uspto.pe2e.cpc.wms.migration.engine.service;

import java.io.IOException;
import java.util.List;

import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.impl.client.CloseableHttpClient;
import org.json.JSONArray;
import org.json.JSONObject;

import gov.uspto.pe2e.cpc.wms.migration.engine.constant.APIResponse;
import gov.uspto.pe2e.cpc.wms.migration.engine.repository.mysql.ProcessMigrationInfo;

public interface MigrationServices {

	public JSONObject createReqBody(String endPointName, JSONObject bodyData, String pathParam, boolean singleProcess);

	List<APIResponse>  getDataFromApi(String endPointName, Object reqBody, String pathParam, String methodeType, boolean singleProcess)
			throws Exception;
	public JSONObject  cancelAndDeleteOriginalProcessInst(String name,String cloneProcessId,int jobId) throws ClientProtocolException, IOException;
	
	public boolean checkProcessIfExist(String id) throws ClientProtocolException, IOException;
	public CloseableHttpResponse callApiCore(CloseableHttpClient client,String uri, JSONObject reqBody, String methodType) throws ClientProtocolException, IOException;
	public JSONArray checkValidateAndDisplay(List<ProcessMigrationInfo> allProcessData) throws ClientProtocolException, IOException;

}
