import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.sql.Timestamp;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathFactory;
import javax.xml.xpath.XPathConstants;

import org.apache.commons.httpclient.URIException;
import org.apache.commons.httpclient.util.URIUtil;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPatch;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.entity.StringEntity;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicHeader;
import org.apache.http.util.EntityUtils;
import org.apache.http.Header;
import org.apache.http.HttpException;
import org.apache.http.HttpResponse;
import org.apache.http.ParseException;
import org.apache.http.StatusLine;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonIOException;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

import HTTPClient.HTTPResponse;

import java.net.URI;

import lib.oracle.erp.shared.rest.Check;
import lib.oracle.erp.shared.rest.CheckType;
import lib.oracle.erp.shared.rest.Operation;
import lib.oracle.erp.shared.rest.Payload;
import lib.oracle.erp.shared.rest.RestResponse;
import lib.oracle.erp.shared.rest.Step;
import lib.oracle.erp.shared.rest.StepResponse;
import lib.oracle.erp.shared.rest.TestCase;
import lib.oracle.erp.shared.rest.Util;

import oracle.oats.scripting.modules.basic.api.*;
import oracle.oats.scripting.modules.basic.api.exceptions.AbstractScriptException;
import oracle.oats.scripting.modules.basic.api.exceptions.UserCausedFailureException;
import oracle.oats.scripting.modules.http.api.*;
import oracle.oats.scripting.modules.http.api.HTTPService.*;
import oracle.oats.scripting.modules.utilities.api.*;
import oracle.oats.scripting.modules.utilities.api.sql.*;
import oracle.oats.scripting.modules.utilities.api.xml.*;
import oracle.oats.scripting.modules.utilities.api.file.*;
import oracle.oats.scripting.modules.webService.api.*;
import oracle.oats.scripting.modules.webService.api.WSService.*;
import org.apache.http.entity.ContentType;
import javax.json.*;

@SuppressWarnings("unused")
public class script extends IteratingVUserScript {


	@ScriptService
	oracle.oats.scripting.modules.utilities.api.UtilitiesService utilities;
	@ScriptService
	oracle.oats.scripting.modules.http.api.HTTPService http;
	@ScriptService
	oracle.oats.scripting.modules.webService.api.WSService ws;




	HashMap<String, RequestResponse> responseMap = new HashMap<String, RequestResponse>();
	Header PRETTY_PRINT_HEADER = new BasicHeader("X-PrettyPrint", "1");
	String performanceInputFile = null;
	Long baseTime;
	String currentTimeStamp=null;


	class ResourceTime {

		public String url;
		public String operation;
		public long duration;
	}

	/**
	 * Rewrite later: Code doesnt looks good, But for now it works Purpose :
	 * Read xml tokens from the env_file
	 *
	 * @param EnvXML
	 * @param vNodeName
	 * @return
	 * @throws AbstractScriptException
	 * @throws Exception
	 */
	public String readXmlForTokens(File EnvXML, String vNodeName)
			throws AbstractScriptException {

		int ListofVariablesCount;
		String ActualValue = Util.EMPTY_STRING;
		String TempVar = Util.EMPTY_STRING;
		String UpdatedAtRunID = Util.EMPTY_STRING;

		DocumentBuilderFactory DocBuildFac = DocumentBuilderFactory
				.newInstance();
		DocBuildFac.setCoalescing(true);
		DocumentBuilder DocBuilder;
		Document Doc;
		try {
			DocBuilder = DocBuildFac.newDocumentBuilder();

			Doc = DocBuilder.parse(EnvXML);

			Element ElRoot = Doc.getDocumentElement();

			NodeList ChildNodes_Name = ElRoot.getElementsByTagName("Name");
			NodeList ChildNodes_Value = ElRoot.getElementsByTagName("Value");

			for (int i = 0; i < ChildNodes_Name.getLength(); i++) {
				NodeList subChildNodes_Name = ChildNodes_Name.item(i)
						.getChildNodes();
				NodeList subChildNodes_Value = ChildNodes_Value.item(i)
						.getChildNodes();
				for (int j = 0; j < subChildNodes_Name.getLength(); j++) {

					String nvalue = subChildNodes_Name.item(j).getTextContent();

					if (nvalue.equalsIgnoreCase(vNodeName)) {
						ActualValue = subChildNodes_Value.item(j)
								.getNodeValue();
					}

				}
			}
		} catch (Exception e1) {
			fail(Util.ERROR_PARSING_ENV_FILE + e1.getMessage());

		}

		if (ActualValue == Util.EMPTY_STRING) {
			info("Node:" + vNodeName + " or Node Value not available");

		}
		return ActualValue;
	}

	/**
	 * Function which accepts a variable and fetch its original value from test case
	 * @param variableName
	 * @param testCase
	 * @return the value of the variable
	 * @throws Exception
	 */

	public String convertLocalVariable(String variableName, TestCase testCase) throws Exception  {
		assert (variableName !=null);

		if(variableName.indexOf(".")==-1){
			if("TIMESTAMP".equals(variableName)){
				if(currentTimeStamp==null){
				Timestamp timestamp = new Timestamp(System.currentTimeMillis());
				String time=timestamp.toString();
				currentTimeStamp=time;
				return time;
				}else{
					return currentTimeStamp;
				}
			}
		  if("DTIMESTAMP".equals(variableName)){

					Timestamp timestamp = new Timestamp(System.currentTimeMillis());
					String time=timestamp.toString();
					currentTimeStamp=time;
					return time;

			}else{
				File environmentFile = new File(this.getSettings().get("env_file"));
				if (!environmentFile.exists())
					throw new IOException(Util.SET_ENV_FILE);
				return readXmlForTokens(environmentFile, variableName);
			}
		}else{
			String split[]=variableName.split("\\.");
			String stepName = split[0];
			Step resolvingStep =getStepForName(testCase,stepName);
			if(resolvingStep==null){
				testCase.setErrorMessage("Invalid step name");
				return null;
			}
			String pathToBeResolved = getPath(variableName);
			if(pathToBeResolved.equals(variableName)){
				testCase.setErrorMessage(Util.VARIABLE_CANNOT_BE_RESOLVED);
			}
			RestResponse stepResponse = resolvingStep.getRestResponse();
			if("payload".equals(split[1])||"PAYLOAD".equals(split[1])){
				if( resolvingStep.getPayload()!=null){
				String value= stepResponse.read(pathToBeResolved, resolvingStep.getPayload().getPayloadValue());
				if(value==null){
					testCase.setErrorMessage(Util.VARIABLE_CANNOT_BE_RESOLVED);
				}
				return value;
				}
				else{
					testCase.setErrorMessage("Payload is null for the step : "+stepName);
					return null;
				}
			}if("response".equals(split[1])||"RESPONSE".equals(split[1])){
				if(stepResponse!=null){
				String value=stepResponse.read(pathToBeResolved);
				return value;
				}
				else{
					testCase.setErrorMessage("Response for the "+stepName+" cannot be fetched");
					return null;
				}
			}
		}
		testCase.setErrorMessage(Util.INVALID_PATH);
		return null;

	}

	/**
	 * Method to fetch the path from the variable passed
	 * Helper function to replace variables in the testcase
	 * @param path is the full path
	 * @return the substring of path required to fetch the value
	 */
	private String getPath(String path){
		int dot1 = path.indexOf(".");
		int dot2 = path.indexOf(".", dot1 + 1);
		return path.substring(dot2+1,path.length());
	}
	/**
	 * Method which parse through the testcase and search for a step
	 * Step name is passed as the search key
	 * @param testCase
	 * @param stepName to be searched
	 * @return the Step if present
	 */
	private Step getStepForName(TestCase testCase, String stepName) {
		Step[] allSteps = testCase.getSteps();
		for (Step step : allSteps) {
		 if(stepName.equals(step.getStepName())) return step;
		}
		return null;
	}

	/**
	 * Wrapper function which reads the json file and executes the operations to
	 * return a boolean result output
	 *
	 * @param inputFile
	 * @return
	 * @throws AbstractScriptException
	 */

	public boolean executeTestCase(String inputFile) throws Exception {

		TestCase testcase = jsonfileToObject(inputFile);
		File f = new File(inputFile);
		String basePath=f.getAbsolutePath().substring(0,f.getAbsolutePath().lastIndexOf("\\"));
		testcase.setBasePath(basePath);
		boolean result=executeTestCase(testcase);
		if(!result) fail(testcase.getErrorMessage());
		return true;
	}
	private HashMap<String, String> mergeMap(HashMap<String, String> map1,HashMap<String, String> map2) {
		HashMap<String, String> finalMap=new HashMap<String, String>();
		if(map1!=null){
	    Iterator<Entry<String, String>> it = map1.entrySet().iterator();
	    while (it.hasNext()) {
	        Map.Entry pair = (Map.Entry)it.next();
	        finalMap.put((String)pair.getKey(), (String)pair.getValue());
	        //it.remove(); // avoids a ConcurrentModificationException
	    }
		}
		if(map2!=null){
		Iterator<Entry<String, String>> it1 = map2.entrySet().iterator();
	    while (it1.hasNext()) {
	        Map.Entry pair = (Map.Entry)it1.next();
	        finalMap.put((String)pair.getKey(), (String)pair.getValue());
	        //it1.remove();
	    }
		}

		return finalMap;
	}

	public StepResponse executeStep(HashMap<String,String> variableMap,String inputFile) throws Exception{
		StepResponse stepResponse= new StepResponse();
		Step step = stepFileToObject(inputFile);
		File f = new File(inputFile);
		String basePath=f.getAbsolutePath().substring(0,f.getAbsolutePath().lastIndexOf("\\"));
		step.setBasePath(basePath);
		step = this.prepareSingleStep(step, variableMap);
		RestResponse restResponse = new RestResponse();
		if("GET".equals(step.getOperation().toUpperCase())){
			restResponse = doGet(step.getUrl(), step.getUsername(),
					step.getPassword(), step.getCustomHeaders());
			step.setRestResponse(restResponse);
			stepResponse.setResponse(restResponse);
		}if("POST".equals(step.getOperation().toUpperCase())){
			restResponse=doPost(step.getUrl(),step.getUsername(),step.getPassword(),step.getPayload().getPayloadValue(),
					null,step.getCustomHeaders());
			step.setRestResponse(restResponse);
			stepResponse.setResponse(restResponse);
		}if("PATCH".equals(step.getOperation().toUpperCase())){
			restResponse=doPatch(step.getUrl(),step.getUsername(),step.getPassword(),step.getPayload().getPayloadValue(),
					null,step.getAcceptHeader(),step.getCustomHeaders());
			step.setRestResponse(restResponse);
			stepResponse.setResponse(restResponse);
		}if("DELETE".equals(step.getOperation().toUpperCase())){
			restResponse =doDelete(step.getUrl(),step.getUsername(),step.getPassword(),
			step.getAcceptHeader(),step.getCustomHeaders());
			step.setRestResponse(restResponse);
			stepResponse.setResponse(restResponse);
		}
		//CHECKS
		if(step.getCheck().getStatusCode()!=0){
			if((step.getCheck().getStatusCode())!=restResponse.getStatusCode())
			{
			 step.setErrorMessage("Status code doesnt match. Expected:"+step.getCheck().getStatusCode() +" Returned:"+restResponse.getStatusCode());

			}
		}
		String errorMessage=step.getCheck().getErrorMessage();
		if(errorMessage!=null){
			errorMessage=errorMessage.trim();
			if(!(errorMessage).equals(restResponse.getPayload()))
			{
			 step.setErrorMessage("Error messages doesnt match. Expected:"+step.getCheck().getErrorMessage() +" Returned:"+restResponse.getPayload());

			}
		}
		if(step.getCheck().getTimeConsumed()!=0&&((step.getCheck().getTimeConsumed())
				<restResponse.getTimeConsumed()))
		{
			step.setErrorMessage("The operation exceeds the time limit.Takes "+restResponse.getTimeConsumed()+
					" milliseconds");

		}

		CheckType[] allChecks=step.getCheck().getChecks();
		for(CheckType check:allChecks){
			String path;
			String matchType=check.getMatchType();
			String expectedValue;
			if("COMPARE".equals(check.getType().toUpperCase())){
				path=check.getTest().getPath();
				if(path==null)
				{
					step.setErrorMessage("Cannot fetch the value to compare.Path is missing.");

				}
				expectedValue=check.getTest().getExpectedValue();
				if(expectedValue==null){
					step.setErrorMessage("Expected value is missing");

				}
				String value=getVariableForStep(variableMap,step,path);
				expectedValue=getVariableForStep(variableMap,step,expectedValue);
				if (expectedValue==null|| value==null ){
					step.setErrorMessage("Missing value for comparison");

					}
				if(matchType!=null){
					matchType=matchType.toUpperCase();
				if("NOTEQUALS".equals(matchType)){
				if(value.equals(expectedValue)){
					step.setErrorMessage("Values are equal while comparing attributes." +
							"Expected : "+expectedValue+"Obtained : "+value);

				}
			}
				if("IGNORECASE".equals(matchType)){
					value=value.toUpperCase();
					expectedValue=expectedValue.toUpperCase();
					if(!value.equals(expectedValue)){
						step.setErrorMessage("Values mismatch while comparing attributes." +
								"Expected : "+expectedValue+"Obtained : "+value);

					}
				}
				if("CONTAINS".equals(matchType)){
					if(!value.contains(expectedValue)){
						step.setErrorMessage("Value "+value+" doesnot contain "+expectedValue);

					}
				}
				if("STARTSWITH".equals(matchType)){
					if(!value.startsWith(expectedValue)){
						step.setErrorMessage("Value "+value+" doesnot starts with "+expectedValue);

					}
				}
				if("EQUALS".equals(matchType)){
					if(!value.equals(expectedValue)){
						step.setErrorMessage("Values mismatch while comparing attributes." +
								"Expected : "+expectedValue+"Obtained : "+value);

					}
				}
				else{
					step.setErrorMessage("Invalid Match Type");

				}

			}

				else{
					if(!value.equals(expectedValue)){
						step.setErrorMessage("Values mismatch while comparing attributes." +
								"Expected : "+expectedValue+"Obtained : "+value);

					}
				}
			}
			if("COMPAREALL".equals(check.getType().toUpperCase())){

				path=check.getTest().getPath();
				expectedValue=check.getTest().getExpectedValue();
				RestResponse checkResponse=getResponseForPath(step,path);
				if(checkResponse==null)
				{
					step.setErrorMessage("Missing values for compare All check");

				}


				String responseFilePath = step.getBasePath() + File.separator + expectedValue;
				info(" Response file path " + responseFilePath);
				File responseFile = new File(responseFilePath);
				if (!responseFile.exists()) {
					info("The input file for step:" + step.getStepName()
							+ " is not found for comparison");
					throw new IOException("File not Found");
				}
				String inputJson=jsonFileToString(responseFilePath);
				JsonParser parser = new JsonParser();
				HashMap<String,String> mergedMap=mergeMap(check.getTest().getVariableMap(),variableMap);
				HashMap<String,String> clonedMap=(HashMap<String, String>) mergedMap.clone();
	   			inputJson=replaceVariables(clonedMap,inputJson);
	   			JsonElement jsonElement = parser.parse(new StringReader(inputJson));
	   			inputJson=prepareJson(jsonElement,step,inputJson,variableMap);
	   			boolean finalResult=checkResponse.compareJsonString(inputJson);
				if(finalResult==false)
				{
					step.setErrorMessage("Response mismatch`");

				}

			}
		}
		if(step.getErrorMessage()!=null){
			fail("Error : "+step.getErrorMessage());
		}
		stepResponse.setPayload(step.getPayload());
		stepResponse.setVariableMap(variableMap);
		return stepResponse;
		}



	private String prepareJson(JsonElement jsonToPrepare, Step step,
			String inputJson,HashMap<String,String> variableMap) throws AbstractScriptException, IOException {
		JsonParser parser = new JsonParser();
		String value;
		String finalValue;
		String formattedValue;
		Set<Entry<String, JsonElement>> ens1 = ((JsonObject) jsonToPrepare).entrySet();
	    JsonObject jsonobj = (JsonObject) jsonToPrepare;
	    if (ens1 != null) {
	         for (Entry<String, JsonElement> en : ens1) {
	             //For preparing keys
	        	 value=en.getKey().toString();
	        	 Pattern pattern = Pattern.compile(Util.REGEX_PATTERN);
	     		 Matcher matcher = pattern.matcher(value);

	     		    if (matcher.find()) {
                	finalValue=getVariableForStep(variableMap,step,value);
                	String variableName = matcher.group().substring(
    		                matcher.group().indexOf('{') + 1,
    		                matcher.group().indexOf('}'));
					inputJson.replace(value, finalValue);
                }
                //For preparing values
	                        if(en.getValue().isJsonObject() )
	                        {
	                        	inputJson=prepareJson(en.getValue(),step,inputJson,variableMap);
	                        }
	                        else if(en.getValue().isJsonArray()){
	                        	int i = 0;
	                        	JsonElement jsonElement2=en.getValue();
	                        	JsonArray jarr2 = jsonElement2.getAsJsonArray();
	                            // Iterate JSON Array to JSON Elements
	                            for (JsonElement je : jarr2) {
	                            	if(je.isJsonObject()||je.isJsonArray())
	                            	{
	                            		inputJson=prepareJson(je,step,inputJson,variableMap);
	                            	}
	                            	else
	                            	{
	                            		value=je.toString();
	                            		matcher = pattern.matcher(value);

	                	     		    if (matcher.find()) {
	        	                        	finalValue=getVariableForStep(variableMap,step,value);
	        	                        	String variableName = matcher.group().substring(
	        	            		                matcher.group().indexOf('{') + 1,
	        	            		                matcher.group().indexOf('}'));
	        	                        	inputJson.replace(value, finalValue);
	        	                        }
	        	                      }
	                            	 i++;
	                            }
	                        }
	                        else{
	                        value=jsonobj.get(en.getKey()).toString();
	                        matcher = pattern.matcher(value);

	    	     		    if (matcher.find()) {
	                        	finalValue=getVariableForStep(variableMap,step,value);
	                        	String variableName = matcher.group().substring(
	            		                matcher.group().indexOf('{') + 1,
	            		                matcher.group().indexOf('}'));
	                        	inputJson=inputJson.replace(value, finalValue);

	                        }
	                        }
	                    }
	                 }

		return inputJson;
	}

	private RestResponse getResponseForPath(Step step, String path) {
		Pattern pattern = Pattern.compile(Util.REGEX_PATTERN);
		Matcher matcher = pattern.matcher(path);
		RestResponse response=new RestResponse();

		    while (matcher.find()) {
		        String variableName = matcher.group().substring(
		                matcher.group().indexOf('{') + 1,
		                matcher.group().indexOf('}'));
		        String split[]=variableName.split("\\.");
		        String stepName=split[0];
		        String pathToBeResolved = getPath(variableName);
				if(!pathToBeResolved.equals(variableName)){
					step.setErrorMessage("Path cannot be resolved for Comparing Json");

				}
		        if("payload".equals(split[1])||"PAYLOAD".equals(split[1])){
		        	response.setPayload(step.getPayload().getPayloadValue());
		        	return response;
		        }
		        if("response".equals(split[1])||"RESPONSE".equals(split[1])){
		        	response=step.getRestResponse();
		        	return response;
		        }
		        else
		        {
		        	step.setErrorMessage("Variable Cannot be resolved for Comparing Json");


		        }


		       }

		    return response;

	}

	private Step prepareSingleStep(Step step, HashMap<String, String> variableMap) throws AbstractScriptException, IOException {
		// URL PREPARE
		assert (step.getUrl() != null);
		String url = step.getUrl();
		url=getVariableForStep(variableMap, step,url);
		if(url==null){
			step.setErrorMessage("Url cannot be resolved");

		}
		step.setUrl(url);
		if(!step.isStorePayload()==false){step.setStorePayload(true);}
		Payload payload = step.getPayload();
		if(payload!=null){
		if (!payload.getPayloadValue().matches("\\{(.*?)\\}")) {

//			String jwgPath = this.getScriptPackage().getScriptPath();
//			String basePath[] = jwgPath
//					.split("\\\\ERPRestSharedLibrary.jwg");

			String filePath =step.getBasePath() +  File.separator + payload.getPayloadValue();
			info(" Payload file path " + filePath);

			File payloadFile = new File(filePath);
			if (!payloadFile.exists()) {
				info("The input file for step:" + step.getStepName()
						+ " is not found");
				throw new IOException("File not Found");
			}
			payload.setPayloadValue(jsonFileToString(filePath));
			step.setPayload(payload);
		}
		HashMap<String,String> mergedMap=mergeMap(step.getPayload().getVariableMap(),variableMap);
		HashMap<String,String> duplicateMap=(HashMap<String, String>) mergedMap.clone();
		String finalPayload=replaceVariables(duplicateMap,step.getPayload().getPayloadValue());
		step.getPayload().setPayloadValue(finalPayload);
		JsonParser parser = new JsonParser();
		JsonElement jsonPayload = parser.parse(new StringReader(step.getPayload().getPayloadValue()));

		step=preparePayload(mergedMap,jsonPayload,step);

		}return step;

	}

	private Step preparePayload(HashMap<String,String> variableMap,JsonElement jsonPayload, Step step) throws AbstractScriptException, IOException {
		JsonParser parser = new JsonParser();
		String value;
		String finalValue;
		String formattedValue;
		Set<Entry<String, JsonElement>> ens1 = ((JsonObject) jsonPayload).entrySet();
	    JsonObject jsonobj = (JsonObject) jsonPayload;
	    if (ens1 != null) {
	         for (Entry<String, JsonElement> en : ens1) {
	             //For preparing keys
	        	 value=en.getKey().toString();
	        	 Pattern pattern = Pattern.compile(Util.REGEX_PATTERN);
	     		 Matcher matcher = pattern.matcher(value);

	     		    if (matcher.find()) {
                	finalValue=getVariableForStep(variableMap,step,value);
					step=replaceInPayload(value,finalValue,step);
                }
                //For preparing values
	                        if(en.getValue().isJsonObject() )
	                        {
	                        step=preparePayload(variableMap,en.getValue(),step);
	                        }
	                        else if(en.getValue().isJsonArray()){
	                        	int i = 0;
	                        	JsonElement jsonElement2=en.getValue();
	                        	JsonArray jarr2 = jsonElement2.getAsJsonArray();
	                            // Iterate JSON Array to JSON Elements
	                            for (JsonElement je : jarr2) {
	                            	if(je.isJsonObject()||je.isJsonArray())
	                            	{
	                            	step=preparePayload(variableMap,je,step);
	                            	}
	                            	else
	                            	{
	                            		value=je.toString();
	                            		matcher = pattern.matcher(value);

	                	     		    if (matcher.find()) {
	        	                        	finalValue=getVariableForStep(variableMap,step,value);
											step=replaceInPayload(value,finalValue,step);
	        	                        }
	        	                      }
	                            	 i++;
	                            }
	                        }
	                        else{
	                        value=jsonobj.get(en.getKey()).toString();
	                        matcher = pattern.matcher(value);

	    	     		    if (matcher.find()) {
	                        	finalValue=getVariableForStep(variableMap,step,value);
								step=replaceInPayload(value,finalValue,step);
	                        }
	                        }
	                    }
	                 }
		return step;
	}

	private String getVariableForStep(HashMap<String, String> variableMap,Step step,String variable) throws AbstractScriptException, IOException {
		Pattern pattern = Pattern.compile(Util.REGEX_PATTERN);
		Matcher matcher = pattern.matcher(variable);

		    while (matcher.find()) {
		        String variableName = matcher.group().substring(
		                matcher.group().indexOf('{') + 1,
		                matcher.group().indexOf('}'));
		       String value= getValueForVariable(variableName, step,variableMap);
		        if(value==null)
		        {
		        	step.setErrorMessage(Util.VARIABLE_CANNOT_BE_RESOLVED);

		        }
		        variable=variable.replace("${"+variableName+"}", value);

		       }

		    return variable;

	}

	private String getValueForVariable(String variableName, Step step, HashMap<String, String> variableMap) throws AbstractScriptException, IOException {
		assert (variableName !=null);

		if(variableName.indexOf(".")==-1){
			if("TIMESTAMP".equals(variableName)){
				if(currentTimeStamp==null){
				Timestamp timestamp = new Timestamp(System.currentTimeMillis());
				String time=timestamp.toString();
				currentTimeStamp=time;
				return time;
				}else{
					return currentTimeStamp;
				}
			}
		  if("DTIMESTAMP".equals(variableName)){

					Timestamp timestamp = new Timestamp(System.currentTimeMillis());
					String time=timestamp.toString();
					currentTimeStamp=time;
					return time;

			}else{
				//code to replace from variable map
				String returnValue=null;
				if(variableMap!=null)
					returnValue=variableMap.get(variableName);
				if(returnValue==null){
				//code to check in environment file
				File environmentFile = new File(this.getSettings().get("env_file"));
				if (!environmentFile.exists())
					throw new IOException(Util.SET_ENV_FILE);
				return readXmlForTokens(environmentFile, variableName);
			 }
				return returnValue;
			}
		}else{
			String split[]=variableName.split("\\.");
			Step resolvingStep =step;
			if(resolvingStep==null){
				step.setErrorMessage("Invalid step name");

			}
			String pathToBeResolved = getPath(variableName);
			if(pathToBeResolved.equals(variableName)){
				step.setErrorMessage(Util.VARIABLE_CANNOT_BE_RESOLVED);
			}
			RestResponse stepResponse = resolvingStep.getRestResponse();
			if("payload".equals(split[1])||"PAYLOAD".equals(split[1])){
				if( resolvingStep.getPayload()!=null){
				String value= stepResponse.read(pathToBeResolved, resolvingStep.getPayload().getPayloadValue());
				if(value==null){
					step.setErrorMessage(Util.VARIABLE_CANNOT_BE_RESOLVED);
				}
				return value;
				}
				else{
					step.setErrorMessage("Payload is null for the step : "+step.getStepName());

				}
			}if("response".equals(split[1])||"RESPONSE".equals(split[1])){
				if(stepResponse!=null){
				String value=stepResponse.read(pathToBeResolved);
				return value;
				}
				else{
					step.setErrorMessage("Response for the "+step.getStepName()+" cannot be fetched");

				}
			}
		}
		step.setErrorMessage(Util.INVALID_PATH);
		return null;


	}

	/**
	 * Method which executes the testcase
	 * @param testcase
	 * @return boolean value indicating the success of execution of entire test case
	 * @throws Exception
	 */

	private boolean executeTestCase(TestCase testcase) throws Exception {
		Step[] allSteps = testcase.getSteps();
		boolean finalResult=true;
		for (Step step : allSteps) {
			step = this.prepareStep(step, testcase);
			RestResponse restResponse = new RestResponse();
			if("GET".equals(step.getOperation().toUpperCase())){
				restResponse = doGet(step.getUrl(), step.getUsername(),
						step.getPassword(), step.getCustomHeaders());
				step.setRestResponse(restResponse);
			}if("POST".equals(step.getOperation().toUpperCase())){
				restResponse=doPost(step.getUrl(),step.getUsername(),step.getPassword(),step.getPayload().getPayloadValue(),
						null,step.getCustomHeaders());
				step.setRestResponse(restResponse);

			}if("PATCH".equals(step.getOperation().toUpperCase())){
				restResponse=doPatch(step.getUrl(),step.getUsername(),step.getPassword(),step.getPayload().getPayloadValue(),
						null,step.getAcceptHeader(),step.getCustomHeaders());
				step.setRestResponse(restResponse);

			}if("DELETE".equals(step.getOperation().toUpperCase())){
				restResponse =doDelete(step.getUrl(),step.getUsername(),step.getPassword(),
				step.getAcceptHeader(),step.getCustomHeaders());
				step.setRestResponse(restResponse);

			}
			//CHECKS
			if(step.getCheck().getStatusCode()!=0){
				if((step.getCheck().getStatusCode())!=restResponse.getStatusCode())
				{
				 testcase.setErrorMessage("Status code doesnt match. Expected:"+step.getCheck().getStatusCode() +" Returned:"+restResponse.getStatusCode());
				 return false;
				}
			}
			String errorMessage=step.getCheck().getErrorMessage();
			if(errorMessage!=null){
				errorMessage=errorMessage.trim();
				if(!(errorMessage).equals(restResponse.getPayload()))
				{
				 testcase.setErrorMessage("Error messages doesnt match. Expected:"+step.getCheck().getErrorMessage() +" Returned:"+restResponse.getPayload());
				 return false;
				}
			}
			if(step.getCheck().getTimeConsumed()!=0&&((step.getCheck().getTimeConsumed())
					<restResponse.getTimeConsumed()))
			{
				testcase.setErrorMessage("The operation exceeds the time limit.Takes "+restResponse.getTimeConsumed()+
						" milliseconds");
				return false;
			}

			CheckType[] allChecks=step.getCheck().getChecks();
			for(CheckType check:allChecks){
				String path;
				String matchType=check.getMatchType();
				String expectedValue;
				if("COMPARE".equals(check.getType().toUpperCase())){
					path=check.getTest().getPath();
					if(path==null)
					{
						testcase.setErrorMessage("Cannot fetch the value to compare.Path is missing.");
						return false;
					}
					expectedValue=check.getTest().getExpectedValue();
					if(expectedValue==null){
						testcase.setErrorMessage("Expected value is missing");
						return false;
					}
					String value=getVariable(testcase,path);
					expectedValue=getVariable(testcase,expectedValue);
					if (expectedValue==null|| value==null )
						return false;
					if(matchType!=null){
						matchType=matchType.toUpperCase();
					if("NOTEQUALS".equals(matchType)){
					if(value.equals(expectedValue)){
						testcase.setErrorMessage("Values are equal while comparing attributes." +
								"Expected : "+expectedValue+"Obtained : "+value);
						return false;
					}
				}
					if("IGNORECASE".equals(matchType)){
						value=value.toUpperCase();
						expectedValue=expectedValue.toUpperCase();
						if(!value.equals(expectedValue)){
							testcase.setErrorMessage("Values mismatch while comparing attributes." +
									"Expected : "+expectedValue+"Obtained : "+value);
							return false;
						}
					}
					if("CONTAINS".equals(matchType)){
						if(!value.contains(expectedValue)){
							testcase.setErrorMessage("Value "+value+" doesnot contain "+expectedValue);
							return false;
						}
					}
					if("STARTSWITH".equals(matchType)){
						if(!value.startsWith(expectedValue)){
							testcase.setErrorMessage("Value "+value+" doesnot starts with "+expectedValue);
							return false;
						}
					}
					if("EQUALS".equals(matchType)){
						if(!value.equals(expectedValue)){
							testcase.setErrorMessage("Values mismatch while comparing attributes." +
									"Expected : "+expectedValue+"Obtained : "+value);
							return false;
						}
					}
					else{
						testcase.setErrorMessage("Invalid Match Type");
						return false;
					}

				}

					else{
						if(!value.equals(expectedValue)){
							testcase.setErrorMessage("Values mismatch while comparing attributes." +
									"Expected : "+expectedValue+"Obtained : "+value);
							return false;
						}
					}
				}
				if("COMPAREALL".equals(check.getType().toUpperCase())){

					path=check.getTest().getPath();
					expectedValue=check.getTest().getExpectedValue();
					RestResponse checkResponse=getResponseForPath(testcase,path);
					if(checkResponse==null)
					{
						return false;
					}
//					String jwgPath = this.getScriptPackage().getScriptPath();
//					String basePath[] = jwgPath
//							.split("\\\\ERPRestSharedLibrary.jwg");

					String responseFilePath = testcase.getBasePath() + File.separator + expectedValue;
					info(" Response file path " + responseFilePath);
					File responseFile = new File(responseFilePath);
					if (!responseFile.exists()) {
						info("The input file for step:" + step.getStepName()
								+ " is not found for comparison");
						throw new IOException("File not Found");
					}
					String inputJson=jsonFileToString(responseFilePath);
					JsonParser parser = new JsonParser();
					inputJson=replaceVariables(check.getTest().getVariableMap(),inputJson);
		   			JsonElement jsonElement = parser.parse(new StringReader(inputJson));
		   			inputJson=prepareJson(jsonElement,testcase,inputJson);
		   			finalResult=checkResponse.compareJsonString(inputJson);
					if(finalResult==false)
					{
						testcase.setErrorMessage("Response mismatch`");
						return false;
					}

				}
			}
		}


		return finalResult;
	}
	/**
	 *
	 * Helper function to prepare the step before execution.
	 * Replaces the variables in test case
	 * @param step to prepare
	 * @param testCase
	 * @return step after replacements
	 * @throws Exception
	 */

	private Step prepareStep(Step step,TestCase testCase) throws Exception {
		// URL PREPARE
			assert (step.getUrl() != null);
			String url = step.getUrl();
			url=getVariable(testCase, url);
			if(url==null){
				testCase.setErrorMessage("Url cannot be resolved");
				return null;
			}
			step.setUrl(url);
			if(!step.isStorePayload()==false){step.setStorePayload(true);}
			Payload payload = step.getPayload();
			if(payload!=null){
			if (!payload.getPayloadValue().matches("\\{(.*?)\\}")) {

//				String jwgPath = this.getScriptPackage().getScriptPath();
//				String basePath[] = jwgPath
//						.split("\\\\ERPRestSharedLibrary.jwg");

				String filePath =testCase.getBasePath() +  File.separator + payload.getPayloadValue();
				info(" Payload file path " + filePath);

				File payloadFile = new File(filePath);
				if (!payloadFile.exists()) {
					info("The input file for step:" + step.getStepName()
							+ " is not found");
					throw new IOException("File not Found");
				}
				payload.setPayloadValue(jsonFileToString(filePath));
				step.setPayload(payload);
			}

			step=processPayload(testCase,step);
			}return step;

		}


	private Step processPayload(TestCase testCase,Step step) throws Exception
	{
		//call variable replacement function
		String payload=replaceVariables(step.getPayload().getVariableMap(),step.getPayload().getPayloadValue());
		step.getPayload().setPayloadValue(payload);
		JsonParser parser = new JsonParser();
		JsonElement jsonPayload = parser.parse(new StringReader(step.getPayload().getPayloadValue()));
		step=preparePayload(jsonPayload,testCase,step);
		return step;
	}
	@SuppressWarnings("rawtypes")
	private String replaceVariables(Map<String, String> mp,String payload) throws AbstractScriptException{
		String value;
		String key;

		Iterator<Entry<String, String>> it = mp.entrySet().iterator();
		    while (it.hasNext()) {
		        Map.Entry pair = it.next();
		        key=(String) pair.getKey();
		        value=(String) pair.getValue();
		        payload=payload.replace("${"+key+"}", value);
		        System.out.println("Replaced "+key + " with " + value);
		        //it.remove();
		     }
		    return payload;
		}



   /**
    * Helper method to fetch payload or response given the step name
    * @param testCase
    * @param path
    * @return
    * @throws Exception
    */

	private RestResponse getResponseForPath(TestCase testCase,String path) throws Exception{
		Pattern pattern = Pattern.compile(Util.REGEX_PATTERN);
		Matcher matcher = pattern.matcher(path);
		RestResponse response=new RestResponse();

		    while (matcher.find()) {
		        String variableName = matcher.group().substring(
		                matcher.group().indexOf('{') + 1,
		                matcher.group().indexOf('}'));
		        String split[]=variableName.split("\\.");
		        String stepName=split[0];
		        Step step=getStepForName(testCase,split[0]);
		        String pathToBeResolved = getPath(variableName);
				if(!pathToBeResolved.equals(variableName)){
					testCase.setErrorMessage("Path cannot be resolved for Comparing Json");
					return null;
				}
		        if("payload".equals(split[1])||"PAYLOAD".equals(split[1])){
		        	response.setPayload(step.getPayload().getPayloadValue());
		        	return response;
		        }
		        if("response".equals(split[1])||"RESPONSE".equals(split[1])){
		        	response=step.getRestResponse();
		        	return response;
		        }
		        else
		        {
		        	testCase.setErrorMessage("Variable Cannot be resolved for Comparing Json");
		        	return null;

		        }


		       }

		    return response;

	}

	/**
	 * Method which replaces variables in payload
	 * @param jsonPayload is the payload as a JsonElement
	 * @param testCase
	 * @param step
	 * @return
	 * @throws Exception
	 */
	private Step preparePayload(JsonElement jsonPayload,TestCase testCase,Step step) throws Exception
	{
		JsonParser parser = new JsonParser();
		String value;
		String finalValue;
		String formattedValue;
		Set<Entry<String, JsonElement>> ens1 = ((JsonObject) jsonPayload).entrySet();
	    JsonObject jsonobj = (JsonObject) jsonPayload;
	    if (ens1 != null) {
	         for (Entry<String, JsonElement> en : ens1) {
	             //For preparing keys
	        	 value=en.getKey().toString();
	        	 Pattern pattern = Pattern.compile(Util.REGEX_PATTERN);
	     		 Matcher matcher = pattern.matcher(value);

	     		    if (matcher.find()) {
                	finalValue=getVariable(testCase,value);
					step=replaceInPayload(value,finalValue,step);
                }
                //For preparing values
	                        if(en.getValue().isJsonObject() )
	                        {
	                        step=preparePayload(en.getValue(),testCase,step);
	                        }
	                        else if(en.getValue().isJsonArray()){
	                        	int i = 0;
	                        	JsonElement jsonElement2=en.getValue();
	                        	JsonArray jarr2 = jsonElement2.getAsJsonArray();
	                            // Iterate JSON Array to JSON Elements
	                            for (JsonElement je : jarr2) {
	                            	if(je.isJsonObject()||je.isJsonArray())
	                            	{
	                            	step=preparePayload(je,testCase,step);
	                            	}
	                            	else
	                            	{
	                            		value=je.toString();
	                            		matcher = pattern.matcher(value);

	                	     		    if (matcher.find()) {
	        	                        	finalValue=getVariable(testCase,value);
											step=replaceInPayload(value,finalValue,step);
	        	                        }
	        	                      }
	                            	 i++;
	                            }
	                        }
	                        else{
	                        value=jsonobj.get(en.getKey()).toString();
	                        matcher = pattern.matcher(value);

	    	     		    if (matcher.find()) {
	                        	finalValue=getVariable(testCase,value);
								step=replaceInPayload(value,finalValue,step);
	                        }
	                        }
	                    }
	                 }
		return step;
}

	/**
	 * Method which replaces variables in payload
	 * @param jsonPayload is the payload as a JsonElement
	 * @param testCase
	 * @param step
	 * @return
	 * @throws Exception
	 */
	private String prepareJson(JsonElement jsonToPrepare,TestCase testCase,String inputJson) throws Exception
	{
		JsonParser parser = new JsonParser();
		String value;
		String finalValue;
		String formattedValue;
		Set<Entry<String, JsonElement>> ens1 = ((JsonObject) jsonToPrepare).entrySet();
	    JsonObject jsonobj = (JsonObject) jsonToPrepare;
	    if (ens1 != null) {
	         for (Entry<String, JsonElement> en : ens1) {
	             //For preparing keys
	        	 value=en.getKey().toString();
	        	 Pattern pattern = Pattern.compile(Util.REGEX_PATTERN);
	     		 Matcher matcher = pattern.matcher(value);

	     		    if (matcher.find()) {
                	finalValue=getVariable(testCase,value);
                	String variableName = matcher.group().substring(
    		                matcher.group().indexOf('{') + 1,
    		                matcher.group().indexOf('}'));
					inputJson.replace(value, finalValue);
                }
                //For preparing values
	                        if(en.getValue().isJsonObject() )
	                        {
	                        	inputJson=prepareJson(en.getValue(),testCase,inputJson);
	                        }
	                        else if(en.getValue().isJsonArray()){
	                        	int i = 0;
	                        	JsonElement jsonElement2=en.getValue();
	                        	JsonArray jarr2 = jsonElement2.getAsJsonArray();
	                            // Iterate JSON Array to JSON Elements
	                            for (JsonElement je : jarr2) {
	                            	if(je.isJsonObject()||je.isJsonArray())
	                            	{
	                            		inputJson=prepareJson(je,testCase,inputJson);
	                            	}
	                            	else
	                            	{
	                            		value=je.toString();
	                            		matcher = pattern.matcher(value);

	                	     		    if (matcher.find()) {
	        	                        	finalValue=getVariable(testCase,value);
	        	                        	String variableName = matcher.group().substring(
	        	            		                matcher.group().indexOf('{') + 1,
	        	            		                matcher.group().indexOf('}'));
	        	                        	inputJson.replace(value, finalValue);
	        	                        }
	        	                      }
	                            	 i++;
	                            }
	                        }
	                        else{
	                        value=jsonobj.get(en.getKey()).toString();
	                        matcher = pattern.matcher(value);

	    	     		    if (matcher.find()) {
	                        	finalValue=getVariable(testCase,value);
	                        	String variableName = matcher.group().substring(
	            		                matcher.group().indexOf('{') + 1,
	            		                matcher.group().indexOf('}'));
	                        	inputJson=inputJson.replace(value, finalValue);

	                        }
	                        }
	                    }
	                 }

		return inputJson;
}
	/**
	 * Helper function to replace variables in payload
	 * @param value is the variable
	 * @param finalValue is the value of the variable
	 * @param step
	 * @return
	 * @throws AbstractScriptException
	 */
	private Step replaceInPayload(String value, String finalValue, Step step) throws AbstractScriptException {
		String payload=step.getPayload().getPayloadValue();
		String formattedValue= value.substring(
				value.indexOf('{') + 1,
				value.indexOf('}'));
		payload=payload.replace(value, finalValue);
		step.getPayload().setPayloadValue(payload);
		return step;
	}

	/**
	 * Function which accepts a String conatining a variable
	 * @param testCase
	 * @param variable for which the value to be found
	 * @return the value of the variable
	 * @throws Exception
	 */
	private String getVariable(TestCase testCase, String variable) throws Exception {
		Pattern pattern = Pattern.compile(Util.REGEX_PATTERN);
		Matcher matcher = pattern.matcher(variable);

		    while (matcher.find()) {
		        String variableName = matcher.group().substring(
		                matcher.group().indexOf('{') + 1,
		                matcher.group().indexOf('}'));
		        String value= convertLocalVariable(variableName, testCase);
		        if(value==null)
		        {
		        	testCase.setErrorMessage(Util.VARIABLE_CANNOT_BE_RESOLVED);
		        	return null;
		        }
		        variable=variable.replace("${"+variableName+"}", value);

		       }

		    return variable;
	}


	/*
	 * Function which reads the JSON Input and populates the testcase objects
	 *
	 * @param filePath is the path of file containing the JSON Input
	 *
	 * @return the TestCase object having details from input
	 */

	public TestCase jsonfileToObject(String filePath) throws Exception {

		Gson gson = new Gson();
		TestCase testcase = new TestCase();

		try {
			Reader reader = new FileReader(filePath);
			testcase = gson.fromJson(reader, TestCase.class);

		} catch (Exception e) {
			if (e instanceof JsonSyntaxException) {
				info("Unable to parse and convert the testcase input file. Please read the documentation for formatting");
				info("Exception details are " + e.getMessage());
			} else {
				info("Unable to read input file, Please check the path/permissions of the file");
				info("Exception details are " + e.getMessage());
			}
			throw e;
		}


		return testcase;
	}

	private Step stepFileToObject(String filePath) throws Exception {

		Gson gson = new Gson();
		Step step = new Step();

		try {
			Reader reader = new FileReader(filePath);
			step = gson.fromJson(reader, Step.class);

		} catch (Exception e) {
			if (e instanceof JsonSyntaxException) {
				info("Unable to parse and convert the testcase input file. Please read the documentation for formatting");
				info("Exception details are " + e.getMessage());
			} else {
				info("Unable to read input file, Please check the path/permissions of the file");
				info("Exception details are " + e.getMessage());
			}
			throw e;
		}


		return step;
	}





	  /**
	 *
	 * @param resource - Url excluding the server host/port if present
	 *
	 * @param operation - The HTTP operation
	 *
	 * @param duration - The time taken for the executed instruction
	 *
	 * @return boolean whether the operation id valid or not
	 *
	 * @throws Exception,ParserConfigurationException
	 */
	private boolean retrieveExemptions(String resource, String operation,
			long duration) throws AbstractScriptException {
		boolean result = false;
		ResourceTime resourceObject = new ResourceTime();
		try {
			Document doc = getDocumentFromFile(this.performanceInputFile);
			doc.getDocumentElement().normalize();

			XPath xPath = XPathFactory.newInstance().newXPath();

			NodeList nList = doc.getElementsByTagName("resource");
			resourceObject.duration = 0;
			for (int temp = 0; temp < nList.getLength(); temp++) {
				Node nNode = nList.item(temp);
				if (nNode.getNodeType() == Node.ELEMENT_NODE) {
					Element eElement = (Element) nNode;

					resourceObject.url = eElement.getElementsByTagName("url")
							.item(0).getTextContent();
					resourceObject.operation = eElement
							.getElementsByTagName("operation").item(0)
							.getTextContent();
					String toconv = eElement.getElementsByTagName("duration")
							.item(0).getTextContent();
					resourceObject.duration = Long.parseLong(toconv);
					if (resourceObject.url != null
							&& resourceObject.operation != null) {
						if (resourceObject.url.equals(resource)
								&& resourceObject.operation.equals(operation)
								&& (resourceObject.duration >= duration)) {
							result = true;
						}

					}

				}
			}
		} catch (Exception e) {
			e.printStackTrace();
			fail("Unable to parse Performance Exemptions File");
		}
		return result;
	}

	/**
	 * Method to read a file from a given path to a String
	 *
	 * @param filePath
	 * @return contens of File as a String
	 * @throws AbstractScriptException
	 */

	public String jsonFileToString(String filePath)
			throws AbstractScriptException {
		File jsonFile = new File(filePath);
		if (!jsonFile.exists())
			fail("File doesnot exists!");
		JsonParser parser = new JsonParser();
		Object obj = null;
		try {
			obj = parser.parse(new FileReader(jsonFile));
		} catch (JsonIOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			fail(Util.ERROR_PARSING_FILE + e.getMessage());
		} catch (JsonSyntaxException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			fail(Util.ERROR_PARSING_FILE + e.getMessage());
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			fail(Util.FILE_NOT_FOUND + e.getMessage());
		}
		if (obj == null)
			fail("Fail : Input File is null");
		JsonObject jsonobj = (JsonObject) obj;
		String inputJson = jsonobj.toString();
		return inputJson;

	}

	/**
	 * This is the performance benchmark time for all the REST API's . This
	 * value is across the family. Standard base time is 6 seconds
	 *
	 * This method reads the basetime from the performance File.
	 *
	 * @throws Exception
	 */
	private void setBaseTime() throws Exception {

		Document doc = getDocumentFromFile(this.performanceInputFile);
		if (doc == null) {
			info("Warning: Performance file not found , However setting the basetime to 6000 ms");
			this.baseTime = 6000l;
			return;
		}
		doc.getDocumentElement().normalize();
		XPath xPath = XPathFactory.newInstance().newXPath();

		String expression = "//basetime";
		NodeList nodeList = (NodeList) xPath.compile(expression).evaluate(doc,
				XPathConstants.NODESET);
		Node nNode = nodeList.item(0);
		if (nNode.getNodeType() == Node.ELEMENT_NODE) {
			Element eElement = (Element) nNode;
			this.baseTime = Long.valueOf(eElement.getTextContent().trim());

		}
		info("Successfully set the base time to " + this.baseTime);

	}

	/**
	 * getDocumentFromFile reads the filePath and returns the Document from the
	 * xml file.
	 *
	 * @return
	 * @throws ParserConfigurationException
	 * @throws SAXException
	 * @throws IOException
	 */
	private Document getDocumentFromFile(String filePath)
			throws ParserConfigurationException, SAXException, IOException {
		DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
		DocumentBuilder dBuilder;
		dBuilder = dbFactory.newDocumentBuilder();
		File inputFile = new File(filePath);
		Document doc = null;
		if (inputFile.exists())
			doc = dBuilder.parse(filePath);
		return doc;
	}

	/**
	 * getBaseURL gets the server and port information from the environmentFile.
	 * It looks for fin_fsm_url tag in the env file to read server port
	 * information Expected to fail if the token is not set correctly
	 *
	 * @throws AbstractScriptException
	 * @throws Exception
	 */
	private String getBaseURL() throws AbstractScriptException {

		info("Begin : ERPRestSharedLibaray:getBaseURL()");
		info("Using Environment File: " + this.getSettings().get("env_file"));
		File environmentFile = new File(this.getSettings().get("env_file"));
		if (!environmentFile.exists())
			fail(" Error:Environment File is missing. ");
		String baseURL = readXmlForTokens(environmentFile, "fin_fsm_url");
		if (baseURL == null)
			fail("Error: fin_fsm_url property not found in environment File");
		info("End : ERPRestSharedLibaray:getBaseURL()");
		return baseURL;

	}

	/**
	 * does the initialize and sets the performance files and basetimes.
	 */
	public void initialize() throws Exception {
		setPerformanceBaseFile();

	}

	/**
	 * This function sets the base performance file which contains any
	 * exemptions
	 *
	 * @throws Exception
	 */

	private void setPerformanceBaseFile() throws Exception {

		if (this.performanceInputFile == null) {
			String jwgPath = this.getScriptPackage().getScriptPath();
			String basePath[] = jwgPath.split(Util.BASE_FILE_PATH);
			this.performanceInputFile = basePath[0] + Util.PERFORMANCE_FILE;
			info(" Performance parameters are read from "
					+ this.performanceInputFile);
			setBaseTime();
		}

	}

	/**
	 * Utility method in case user wants to override the perf file. Make sure to
	 * have the path variable a generic one and not specific to the users view
	 * folder
	 *
	 * @param PerformanceFilePath
	 * @throws Exception
	 */
	public void setPerformanceBaseFile(String performanceFilePath)
			throws Exception {
		this.performanceInputFile = performanceFilePath;
		setBaseTime();
	}

	private HttpClient trustCertificates() throws AbstractScriptException {

		try {
			SSLContext sslContext = SSLContext.getInstance("TLSv1.2");
			sslContext.init(null, new TrustManager[] { new X509TrustManager() {
				public X509Certificate[] getAcceptedIssuers() {
					return null;
				}

				public void checkClientTrusted(X509Certificate[] certs,
						String authType) {
				}

				public void checkServerTrusted(X509Certificate[] certs,
						String authType) {
				}
			} }, new SecureRandom());

			SSLConnectionSocketFactory factory = new SSLConnectionSocketFactory(
					sslContext,
					SSLConnectionSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER);

			return HttpClients.custom().setSSLSocketFactory(factory).build();
		} catch (Exception E) {
			E.printStackTrace();
			fail("Exception encountered while getting the trust certificates");
		}
		return null;

	}

	private int getStatusCode(String input) throws AbstractScriptException {
		int statusCode = -1;
		if (input != null) {
			if (input.contains("200"))
				statusCode = 200;
			else if (input.contains("201"))
				statusCode = 201;
			else if (input.contains("202"))
				statusCode = 202;
			else if (input.contains("204"))
				statusCode = 204;
			else if (input.contains("400"))
				statusCode = 400;
			else if (input.contains("401"))
				statusCode = 401;
			else if (input.contains("403"))
				statusCode = 403;
			else if (input.contains("404"))
				statusCode = 404;
			else if (input.contains("415"))
				statusCode = 415;
			else if (input.contains("500"))
				statusCode = 500;
			else if (input.contains("503"))
				statusCode = 503;
			else
				fail("invalid Status code");
		}
		return statusCode;

	}

	private String convertResponse(HttpResponse response)
			throws AbstractScriptException {
		String result = Util.EMPTY_STRING;
		try {
			if (response == null || response.getEntity() == null)
				return result;
			BufferedReader rd = new BufferedReader(new InputStreamReader(
					response.getEntity().getContent()));
			StringBuilder sb = new StringBuilder();
			String line;
			while ((line = rd.readLine()) != null) {
				sb.append(line);
			}
			rd.close();
			result = sb.toString();
		} catch (IOException ioe) {
			ioe.printStackTrace();
			fail("Error while converting the response to string");
		}
		return result;
	}

	/**
	 *
	 * @param url
	 *            - Can be relative URL or absolute URL. If its a relative URL,
	 *            it should start with / and Server+PORT gets appended
	 * @param userName
	 * @param password
	 *            - If password is not provided , It checks in the users.xml for
	 *            the password
	 * @param customHeaders
	 *            - You can pass in customHeaders if you need. It will get
	 *            appended accordingly
	 * @return RestResponse Object
	 * @throws AbstractScriptException
	 * @throws AbstractScriptException
	 */
    public String getUrlPrefix(String url) throws AbstractScriptException
    {
    	if (url == null)
			fail(" Wrong Usage of ERPRestSharedLibrary , URL is a mandatory parameter");
    	String paths[]=url.split("/");
    	if(paths.length<2  || !"resources".equals(paths[1]))
    	{
    		fail("Seems like URL is not of the fusion standards. ");
    	}

    	if(paths.length>=4){
    	  String version=paths[2];
    	  if(paths[3].contains("?"))   //url has parameters
    		paths[3]=paths[3].substring(0,paths[3].indexOf("?"));
    	  return (paths[0]+"/resources/"+version+"/"+paths[3]);
    	}
    	else
    		return url;

    }
	public RestResponse doGet(String url, String userName, String password,
			HashMap<String, String> customHeaders)
			throws AbstractScriptException {

		info("Enter ERPRestSharedLibaray:doGet()");
		try {
			initialize();
		} catch (Exception E) {
			fail("Fail while loading performance File" + E.getMessage());
		}
		if (url == null)
			fail(" Wrong Usage of ERPRestSharedLibaray:doGet() , URL is a mandatory parameter");
		RestResponse returnResponse = new RestResponse();

		HttpGet httpGet;
		String finalUrl;
		finalUrl = getFinalURL(url);
		info("Final URL :" + finalUrl);
		URI finalUri = null;
		try {
			finalUrl = URIUtil.encodeQuery(finalUrl);
			finalUri = new URI(finalUrl);
		} catch (Exception U) {
			fail("Error while creating URI from the url:" + U.getMessage());
		}

		boolean httpsFlag = false;
		if (finalUrl.contains("https"))
			httpsFlag = true;
		String basicAuth = createAuthenticationHeader(userName, password);
		Header authHeader = new BasicHeader("Authorization", basicAuth); // authorization
																			// header
																			// added
		HttpClient httpClient;
		if (httpsFlag)
			httpClient = trustCertificates();
		else
			httpClient = HttpClientBuilder.create().build();
		try {
			httpGet = new HttpGet(finalUri);
			httpGet.addHeader(authHeader);
			httpGet.addHeader(PRETTY_PRINT_HEADER);
			if (customHeaders != null) { // push any custom headers if passed
				for (Map.Entry<String, String> head : customHeaders.entrySet()) {
					httpGet.addHeader(new BasicHeader(head.getKey(), head
							.getValue()));
				}
			}
			printRequest(httpGet);
			String operation = "GET";
			Date initialTime = new Date();
			HttpResponse response = httpClient.execute(httpGet);
			Date finalTime = new Date();
			String urlToValidate=url;
			if(urlToValidate.startsWith("http")||urlToValidate.startsWith("HTTP")){
				urlToValidate=urlToValidate.replace("//", "__");
				int indexOfSlash=urlToValidate.indexOf("/");
				urlToValidate=urlToValidate.substring(indexOfSlash+1, urlToValidate.length());
				}

			urlToValidate=getUrlPrefix(urlToValidate);

			long timeInMilliSec = validateTime(urlToValidate, operation, initialTime,
					finalTime);
			if (response == null)
				fail("Get Method invocation returned a null response");
			returnResponse = setReturnResponse(response, timeInMilliSec);
		} catch (IOException E) {
			E.printStackTrace();
			fail("Error while invoking GET: " + E.getMessage());
		}
		info("Exit ERPRestSharedLibaray:doGet()");
		return returnResponse;
	}

	/**
	 * @param url
	 * @param operation
	 * @param initialTime
	 * @param finalTime
	 * @return
	 * @throws AbstractScriptException
	 */
	private long validateTime(String url, String operation, Date initialTime,
			Date finalTime) throws AbstractScriptException {
		long timeInMilliSec = finalTime.getTime() - initialTime.getTime();
		long timeInSec = timeInMilliSec / 1000;
		if (timeInMilliSec <= baseTime) {
			info("Time taken by the URL is well under the performance basetime");
		} else {
			boolean result = retrieveExemptions(url, operation, timeInMilliSec);
			if (result != true)
				/*
				 * The operation fails if it is not registered as an exemption
				 * or it exceeds the registered time limit as well
				 */
				fail("The operation exceed the time limit. Takes " + timeInSec
						+ " seconds.");
		}
		return timeInMilliSec;
	}

	/**
	 *
	 * @param url
	 *            - Can be relative URL or absolute URL. If its a relative URL,
	 *            it should start with / and Server+PORT gets appended
	 * @param userName
	 * @param password
	 *            - If password is not provided , It checks in the users.xml for
	 *            the password
	 * @param inputJson
	 *            - Path of input JSON Payload
	 * @param customHeaders
	 *            - You can pass in customHeaders if you need. It will get
	 *            appended accordingly
	 * @return
	 * @throws AbstractScriptException
	 */

	public RestResponse doPost(String url, String userName, String password,
			String inputJson, String paramContentType,
			HashMap<String, String> customHeaders)
			throws AbstractScriptException {
		info("Enter ERPRestSharedLibaray:doPost()");
		try {
			initialize();
		} catch (Exception E) {
			fail("Fail while loading performance File" + E.getMessage());
		}
		if (url == null)
			fail(" Wrong Usage of ERPRestSharedLibaray:doPost() , URL is a mandatory parameters");
		RestResponse returnResponse = new RestResponse();

		HttpPost httpPost;
		String finalUrl;
		finalUrl = getFinalURL(url);
		info("Final URL :" + finalUrl);
		URI finalUri = null;
		try {
			finalUrl = URIUtil.encodeQuery(finalUrl);
			finalUri = new URI(finalUrl);
		} catch (Exception U) {
			fail("Error while creating URI from the url:" + U.getMessage());
		}

		boolean httpsFlag = false;
		if (finalUrl.contains("https"))
			httpsFlag = true;
		String basicAuth = createAuthenticationHeader(userName, password);

		Header authHeader = new BasicHeader("Authorization", basicAuth); // authorization
		String contentType=null;
		if(customHeaders!=null)contentType=customHeaders.get("Content-Type");																	// header
		if(contentType==null){																	// added
		 contentType = "application/vnd.oracle.adf.resourceitem+json";
		}
		if (paramContentType != null) {
			if (paramContentType.equalsIgnoreCase("xml")) {
				contentType = "application/vnd.oracle.adf.resource+xml";
			} else if (!paramContentType.equalsIgnoreCase("json")) {
				contentType = paramContentType;
			}

		}
		info("Content-Type ::" + contentType);
		Header contentTypeHeader = new BasicHeader("Content-Type", contentType);// Content-Type
																				// Header
																				// added
		HttpClient httpClient;
		JsonParser parser = new JsonParser();
		Object obj = null;
		try {
			obj = parser.parse(new StringReader(inputJson));
		} catch (JsonIOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			fail("Error while parsing input Json " + e.getMessage());
		} catch (JsonSyntaxException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			fail("Error while parsing input Json " + e.getMessage());
		}
		if (obj == null)
			fail("Fail : Input Json is null");
		JsonObject jsonobj = (JsonObject) obj;
		String requestBody = jsonobj.toString();

		StringEntity body = new StringEntity(requestBody,
				ContentType.APPLICATION_JSON);

		if (httpsFlag)
			httpClient = trustCertificates();
		else
			httpClient = HttpClientBuilder.create().build();
		try {
			httpPost = new HttpPost(finalUri);
			httpPost.addHeader(authHeader);
			httpPost.addHeader(contentTypeHeader);
			httpPost.addHeader(PRETTY_PRINT_HEADER);
			httpPost.setEntity(body);
			if (customHeaders != null) { // push any custom headers if passed
				for (Map.Entry<String, String> head : customHeaders.entrySet()) {
					if(!head.getKey().equals("Content-Type")){
					httpPost.addHeader(new BasicHeader(head.getKey(), head
							.getValue()));
					}
				}
			}
			String operation = "POST";
			printRequest(httpPost);
			Date initialTime = new Date();
			HttpResponse response = httpClient.execute(httpPost);
			Date finalTime = new Date();
			String urlToValidate=url;
			if(urlToValidate.startsWith("http")||urlToValidate.startsWith("HTTP")){
				urlToValidate=urlToValidate.replace("//", "__");
				int indexOfSlash=urlToValidate.indexOf("/");
				urlToValidate=urlToValidate.substring(indexOfSlash+1, urlToValidate.length());
				}

			urlToValidate=getUrlPrefix(urlToValidate);

			long timeInMilliSec = validateTime(urlToValidate, operation, initialTime,
					finalTime);

			if (response == null)
				fail("Post Method invocation returned a null response");
			returnResponse = setReturnResponse(response, timeInMilliSec);

		} catch (IOException E) {
			E.printStackTrace();
			fail("Error while invoking POST: " + E.getMessage());
		}
		info("Exit ERPRestSharedLibaray:doPost()");
		return returnResponse;
	}

	/**
	 * @param returnResponse
	 * @throws AbstractScriptException
	 */
	private void printResponse(RestResponse returnResponse)
			throws AbstractScriptException {
		if (returnResponse.getPayload().length() > 31000) {
			info(Util.LINE_BREAKER + "Header" + Util.LINE_BREAKER + "\n");
			info("Request Status: " + returnResponse.getStatusCode() + "/n");
			info("Request Status Message: " + returnResponse.getStatusReason()
					+ "/n");
			info(Util.LINE_BREAKER + "Body" + Util.LINE_BREAKER + "\n");
			int start = 0, mid = 31000;
			int CountORLength = returnResponse.getPayload().length();
			while (start < CountORLength) {
				String infoMsg = "Response Continued :: ";
				if (start == 0)
					infoMsg = "Response is :: ";
				info(infoMsg
						+ returnResponse.getPayload().substring(start, mid));
				start = mid;
				mid = mid + 31000;
				if (mid > CountORLength)
					mid = CountORLength;
			}
		} else
			info(returnResponse.toString());
	}

	/**
	 *
	 * @param url
	 * @param userName
	 * @param password
	 * @param inputJson
	 * @param acceptHeader
	 * @param customHeaders
	 * @return
	 * @throws AbstractScriptException
	 */

	public RestResponse doPatch(String url, String userName, String password,
			String inputJson, String paramContentType, String acceptHeader,
			HashMap<String, String> customHeaders)
			throws AbstractScriptException {
		info("Enter ERPRestSharedLibaray:doPatch()");
		try {
			initialize();
		} catch (Exception E) {
			fail("Fail while loading performance File" + E.getMessage());
		}
		if (url == null)
			fail(" Wrong Usage of ERPRestSharedLibaray:doPatch() , URL is a mandatory parameters");
		RestResponse returnResponse = new RestResponse();

		HttpPatch httpPatch;
		String finalUrl;
		finalUrl = getFinalURL(url);
		info("Final URL :" + finalUrl);
		URI finalUri = null;
		try {
			finalUrl = URIUtil.encodeQuery(finalUrl);
			finalUri = new URI(finalUrl);
		} catch (Exception U) {
			fail("Error while creating URI from the url:" + U.getMessage());
		}

		boolean httpsFlag = false;
		if (finalUrl.contains("https"))
			httpsFlag = true;
		String basicAuth = createAuthenticationHeader(userName, password);
		Header authHeader = new BasicHeader("Authorization", basicAuth); // authorization
																			// header
																			// added
		String contentType = "application/vnd.oracle.adf.resourceitem+json";

		if (paramContentType != null) {
			if (paramContentType.equalsIgnoreCase("xml")) {
				contentType = "application/vnd.oracle.adf.resource+xml";
			} else if (!paramContentType.equalsIgnoreCase("json")) {
				contentType = paramContentType;
			}

		}
		info("Content-Type ::" + contentType);
		Header contentTypeHeader = new BasicHeader("Content-Type", contentType);// Content-Type
																				// Header
																				// added

		HttpClient httpClient;
		StringEntity body = new StringEntity(inputJson,
				ContentType.APPLICATION_JSON);

		if (httpsFlag)
			httpClient = trustCertificates();
		else
			httpClient = HttpClientBuilder.create().build();
		try {
			httpPatch = new HttpPatch(finalUri);
			httpPatch.addHeader(authHeader);
			httpPatch.addHeader(contentTypeHeader);
			httpPatch.addHeader(PRETTY_PRINT_HEADER);
			httpPatch.setEntity(body);
			if (customHeaders != null) { // push any custom headers if passed
				for (Map.Entry<String, String> head : customHeaders.entrySet()) {
					httpPatch.addHeader(new BasicHeader(head.getKey(), head
							.getValue()));
				}
			}
			String operation = "PATCH";
			printRequest(httpPatch);
			Date initialTime = new Date();
			HttpResponse response = httpClient.execute(httpPatch);
			Date finalTime = new Date();
			String urlToValidate=url;
			if(urlToValidate.startsWith("http")||urlToValidate.startsWith("HTTP")){
				urlToValidate=urlToValidate.replace("//", "__");
				int indexOfSlash=urlToValidate.indexOf("/");
				urlToValidate=urlToValidate.substring(indexOfSlash+1, urlToValidate.length());
				}

			urlToValidate=getUrlPrefix(urlToValidate);

			long timeInMilliSec = validateTime(urlToValidate, operation, initialTime,
					finalTime);

			if (response == null)
				fail("Post Method invocation returned a null response");
			returnResponse = setReturnResponse(response, timeInMilliSec);

		} catch (IOException E) {
			E.printStackTrace();
			fail("Error while invoking PATCH: " + E.getMessage());
		}
		info("Exit ERPRestSharedLibaray:doPatch()");
		return returnResponse;
	}

	public RestResponse doDelete(String url, String userName, String password,
			String acceptHeader, HashMap<String, String> customHeaders)
			throws AbstractScriptException {
		info("Enter ERPRestSharedLibaray:doDelete()");
		try {
			initialize();
		} catch (Exception E) {
			fail("Fail while loading performance File" + E.getMessage());
		}
		if (url == null)
			fail(" Wrong Usage of ERPRestSharedLibaray:doDelete() , URL is a mandatory parameters");
		RestResponse returnResponse = new RestResponse();

		HttpDelete httpDelete;
		String finalUrl;
		finalUrl = getFinalURL(url);
		info("Final URL :" + finalUrl);
		URI finalUri = null;
		try {
			finalUrl = URIUtil.encodeQuery(finalUrl);
			finalUri = new URI(finalUrl);
		} catch (Exception U) {
			fail("Error while creating URI from the url:" + U.getMessage());
		}

		boolean httpsFlag = false;
		if (finalUrl.contains("https"))
			httpsFlag = true;
		String basicAuth = createAuthenticationHeader(userName, password);
		Header authHeader = new BasicHeader("Authorization", basicAuth); // authorization
																			// header
																			// added
		HttpClient httpClient;
		if (httpsFlag)
			httpClient = trustCertificates();
		else
			httpClient = HttpClientBuilder.create().build();
		try {
			httpDelete = new HttpDelete(finalUri);
			httpDelete.addHeader(authHeader);
			httpDelete.addHeader(PRETTY_PRINT_HEADER);
			if (customHeaders != null) { // push any custom headers if passed
				for (Map.Entry<String, String> head : customHeaders.entrySet()) {
					httpDelete.addHeader(new BasicHeader(head.getKey(), head
							.getValue()));
				}
			}
			printRequest(httpDelete);
			String operation = "DELETE";
			Date initialTime = new Date();
			HttpResponse response = httpClient.execute(httpDelete);
			Date finalTime = new Date();
			String urlToValidate=url;
			if(urlToValidate.startsWith("http")||urlToValidate.startsWith("HTTP")){
				urlToValidate=urlToValidate.replace("//", "__");
				int indexOfSlash=urlToValidate.indexOf("/");
				urlToValidate=urlToValidate.substring(indexOfSlash+1, urlToValidate.length());
				}

			urlToValidate=getUrlPrefix(urlToValidate);

			long timeInMilliSec = validateTime(urlToValidate, operation, initialTime,
					finalTime);

			if (response == null)
				fail("Get Method invocation returned a null response");
			returnResponse = setReturnResponse(response, timeInMilliSec);

		} catch (IOException E) {
			E.printStackTrace();
			fail("Error while invoking DELETE: " + E.getMessage());
		}
		info("Exit ERPRestSharedLibaray:doDelete()");
		return returnResponse;
	}

	/**
	 * @param returnResponse
	 * @param response
	 * @param timeInMilliSec
	 * @throws AbstractScriptException
	 */
	private RestResponse setReturnResponse(HttpResponse response,
			long timeInMilliSec) throws AbstractScriptException {
		RestResponse returnResponse = new RestResponse();
		returnResponse.setTimeConsumed(timeInMilliSec);
		returnResponse.setHttpResponse(response);
		StatusLine line = response.getStatusLine();
		returnResponse.setStatusCode(getStatusCode(line.toString()));
		returnResponse.setStatusReason(line.getReasonPhrase());
		returnResponse.setPayload(convertResponse(response));
		printResponse(returnResponse);
		return returnResponse;
	}

	private String createAuthenticationHeader(String userName, String password)
			throws AbstractScriptException {
		String userCredentials = null;
		if (password == null)
			fail("Authentication failed!Missing Password");

		else
			userCredentials = userName + ":" + password;
		;

		if (userCredentials == null)
			fail(" Unable to retrive usercredentials , Program exiting..");
		String basicAuth = "Basic "
				+ javax.xml.bind.DatatypeConverter
						.printBase64Binary(userCredentials.getBytes());
		info("Basic Authetication credentials :" + basicAuth);
		return basicAuth;
	}

	private String getFinalURL(String url) throws AbstractScriptException {
		String finalUrl;
		if (url != null && url.startsWith("http") || url.startsWith("HTTP"))
			finalUrl = url;
		else
			finalUrl = getBaseURL() + url;
		return finalUrl;
	}

	private void printRequest(HttpRequestBase method)
			throws AbstractScriptException {
		info("\n" + Util.LINE_BREAKER + "Request Details" + Util.LINE_BREAKER + "\n");
		info("URL: " + method.getURI().toString() + "\n");
		for (Header H : method.getAllHeaders()) {
			info("Header Name: " + H.getName() + "  ||  Header value: "
					+ H.getValue() + "\n");

		}
		if (method.getMethod() == "POST") {
			HttpPost myPost = (HttpPost) method;
			info("/n" + Util.LINE_BREAKER + " POST Input Payload" + Util.LINE_BREAKER
					+ "\n");
			try {
				info("\n" + EntityUtils.toString(myPost.getEntity()) + "\n");
			} catch (ParseException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		if (method.getMethod() == "PATCH") {
			HttpPatch patch = (HttpPatch) method;
			info("/n+" + Util.LINE_BREAKER + " Patch Input Payload" + Util.LINE_BREAKER
					+ "\n");
			try {
				info("\n" + EntityUtils.toString(patch.getEntity()) + "\n");
			} catch (ParseException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		info(Util.LINE_BREAKER + "End Request" + Util.LINE_BREAKER + "\n");
	}

	public HashMap<String, String> constructHashMapfromJson(String inputJson)
			throws AbstractScriptException {

		File jsonFile = new File(inputJson);
		JsonObject object = null;
		JsonParser parser = new JsonParser();
		if (!jsonFile.exists())
			fail("Fail : Input file provided to construct hashmap doesnt exists");
		else
			try {
				object = (JsonObject) parser.parse(new FileReader(jsonFile));
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				fail("Error parsing input Json");
			}
		HashMap<String, String> map = new HashMap<String, String>();

		for (Map.Entry<String, JsonElement> E : object.entrySet()) {
			map.put(E.getKey(), E.getValue().getAsString());
		}
		return map;
	}

	public String preparePayload(String inputFile,
			HashMap<String, String> replacementMap)
			throws AbstractScriptException, IOException {

		File input = new File(inputFile);
		if (!input.exists())
			fail("Fail : Input file provided to prepare payload doesnt exists");
		JsonObject object = null;
		JsonParser parser = new JsonParser();
		try {
			object = (JsonObject) parser.parse(new FileReader(input));
		} catch (Exception E) {
			E.printStackTrace();
			fail("Error while parsing JSON File");
		}
		String replaceString = object.toString();
		if (replacementMap != null) {
			for (Map.Entry<String, String> entry : replacementMap.entrySet()) {
				info("Replacing JSON for " + entry.getKey() + " With value "
						+ entry.getValue());
				replaceString = replaceString.replace(entry.getKey(),
						entry.getValue());
			}
		}

		return replaceString;

	}

	/**
	 * Add code to be executed each iteration for this virtual user.
	 */
	public void run() throws Exception {

	}

	/**
	 * Add any cleanup code or perform any operations after all iterations are
	 * performed.
	 */
	public void finish() throws Exception {

	}
}
