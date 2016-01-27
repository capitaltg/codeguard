/**
 * Copyright 2016 Capital Technology Group, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.capitaltg.bbcodeguard.jenkins;

import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.URLConnection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.net.ssl.HttpsURLConnection;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.atlassian.bitbucket.repository.Repository;
import com.atlassian.bitbucket.repository.RepositoryCloneLinksRequest;
import com.atlassian.bitbucket.repository.RepositoryService;
import com.atlassian.sal.api.pluginsettings.PluginSettings;
import com.atlassian.sal.api.pluginsettings.PluginSettingsFactory;
import com.capitaltg.bbcodeguard.ConfigurationException;
import com.capitaltg.bbcodeguard.StringUtils;
import com.capitaltg.bbcodeguard.servlet.ConfigurationServlet;
import com.google.common.collect.Maps;

/**
 * Manages all interactions with Jenkins
 * 
 * @author tslazar
 *
 */
public class JenkinsManager {

	private Logger logger = LoggerFactory.getLogger(JenkinsManager.class);
	
	private final PluginSettings pluginSettings;
	private final RepositoryService repositoryService;
	
	private String baseURL = null;
	private String username = null;
	private String password = null;
	private String credentialsId = null;
	
	public JenkinsManager(PluginSettingsFactory pluginSettingsFactory,
			RepositoryService repositoryService) {
		this.repositoryService = repositoryService;
		this.pluginSettings = pluginSettingsFactory.createGlobalSettings();
		reload();
	}
	
	public void reload(){
		@SuppressWarnings("unchecked")
		Map<String, String> settings = (Map<String, String>) pluginSettings.get(ConfigurationServlet.CODEGUARD_SETTINGS_KEY);
		if(settings == null) {
			settings = new HashMap<>();
		}
		baseURL = settings.get("jenkinsBaseUrl");
		username = settings.get("username");
		password = settings.get("password");
		credentialsId = settings.get("credentialsId");
		logger.info("Reloaded configuration settings: {}",settings);
	}
	
	@SuppressWarnings("boxing")
	public void startPublishJob(Repository repository, String branch) {

		String repositoryURL = getRepositoryURL(repository);
		String project = repository.getProject().getKey();
		String slug = repository.getSlug();

    	logger.info("Starting publish job {} - {} for branch '{}'",project,slug, branch);
		try {
			createOrOverwritePublishJob(repositoryURL, project, slug);
        	String jobName = project+"_"+slug+"_publish";
        	String jenkinsurl = String.format("%s/job/%s/buildWithParameters?branch=%s",
        			baseURL,
        			jobName,
        			branch);

			HttpURLConnection connection = post(jenkinsurl);
			//TODO handle failures
			logger.info("Started publish job for {} {} {} with response: {} - {}", project, slug, branch, connection.getResponseCode(), connection.getResponseMessage());
			
		} catch(IOException e) {
			logger.error("TODO Failed to start job", e);
		}
		
	}
	
	@SuppressWarnings("boxing")
	public void startVerifyJob(Repository repository, 
			long pr, String branchTo, String branchFrom) {
		
		String project = repository.getProject().getKey();
		String slug = repository.getSlug();
		
		logger.info("Verify job {} - {} for merging branch {} into {} (PR {})",project, slug, branchFrom, branchTo, pr);
    			
		try {
			createOrOverwriteVerifyJob(repository);
        	String jobName = project+"_"+slug+"_verify";
        	String jenkinsurl = String.format("%s/job/%s/buildWithParameters?fromBranch=%s&toBranch=%s&pr=%s",
        			baseURL,
        			jobName,
        			branchFrom,
        			branchTo,
        			pr);
        	
			HttpURLConnection connection = post(jenkinsurl);
			//TODO handle failure
			logger.info("Response: {} - {}", connection.getResponseCode(), connection.getResponseMessage());
			
		} catch(IOException e) {
			logger.error("TODO Failed to start job", e);
		}
		
	}
	
	private static String createEmptyJobXML() throws IOException {
		String string = StringUtils.readResourceAndUpdateText("templates/publish.xml", Collections.emptyMap());
		return string;
	}

	private String createPublishJobXML(String repositoryURL, String project, String slug) throws IOException {
		Map<String, String> map = Maps.newHashMap();
			map.put("credentialsId", credentialsId); 
			map.put("repositoryURL", repositoryURL);
		logger.info("Created xml configuration for {} {} publish job",project, slug);
		return StringUtils.readResourceAndUpdateText("templates/publish.xml", map);
	}

	private String createVerifyJobXML(Repository repository) throws IOException {

		String repositoryURL = getRepositoryURL(repository);
		String bitbucketBaseURL = getBitbucketBaseURL(repository);
		String project = repository.getProject().getKey();
		String slug = repository.getSlug();

		Map<String, String> map = Maps.newHashMap();
			map.put("jenkinsurl", baseURL);
			map.put("project",project);
			map.put("repo",slug);
			map.put("credentialsId", credentialsId);
			map.put("bitbucketBaseURL", bitbucketBaseURL);
			map.put("repositoryURL", repositoryURL);
			map.put("username",username);
			map.put("password",password);
		logger.info("Created xml configuration for {} {} verify job",project, slug);
		return StringUtils.readResourceAndUpdateText("templates/verify.xml", map);
	}

	@SuppressWarnings("boxing")
	private void deleteJob(String baseurl, String jobName) throws IOException {
		String jenkinsurl = baseurl+"/job/"+jobName+"/doDelete";
		HttpURLConnection connection = post(jenkinsurl, "deleteme!!!");
		logger.info("Deleted job {} with response {}: {}", jobName, connection.getResponseCode(),connection.getResponseMessage());
	}
	
	@SuppressWarnings("boxing")
	private void createOrOverwritePublishJob(String repositoryURL, String project, String slug) throws IOException {

		String xml = createPublishJobXML(repositoryURL, project, slug);
		String jobName = project+"_"+slug+"_publish";
		String jenkinsurl = baseURL+"/createItem?name="+jobName;

		if(checkJobExists(jobName)) {
			jenkinsurl = baseURL+"/job/"+jobName+"/config.xml";
			logger.info("Will overwrite publish job for {} {}",project, slug);
		} else {
			logger.info("Creating new jenkins job for {} {}",project, slug);
		}
		
		HttpURLConnection connection = post(jenkinsurl, xml);
		logger.info("Created job for {} {} with response {}: {}",project, slug, connection.getResponseCode(),connection.getResponseMessage());
		
	}

	private HttpURLConnection post(String urlstring) throws IOException{
		return post(urlstring, null);
	}
	
	private HttpURLConnection post(String urlstring, String string) throws IOException{
		return post(urlstring, string, username, password);
	}
	
	private static HttpURLConnection post(String urlstring, String string, String username, String password) throws IOException{

		URL url = new URL(urlstring);
		HttpsURLConnection connection = (HttpsURLConnection)url.openConnection();
		
		addAuthenticationCredentials(connection,username,password);
		connection.setRequestMethod("POST");
		
		if(string!=null) {
			connection.setRequestProperty("Content-Type","application/xml");
			connection.setDoOutput(true);
			DataOutputStream stream = new DataOutputStream(connection.getOutputStream());
			stream.writeBytes(string);
			stream.close();
		}
		
		return connection;

	}
	
	private HttpURLConnection get(String urlstring) throws IOException{
		URL url = new URL(urlstring);
		HttpsURLConnection connection = (HttpsURLConnection)url.openConnection();
		addAuthenticationCredentials(connection);
		connection.setRequestMethod("GET");
		addAuthenticationCredentials(connection);
		return connection;
	}
	
	@SuppressWarnings("boxing")
	private void createOrOverwriteVerifyJob(Repository repository) throws IOException {

		String xml = createVerifyJobXML(repository);

		String project = repository.getProject().getKey();
		String slug = repository.getSlug();
		String jobName = project+"_"+slug+"_verify";
		String jenkinsurl = baseURL+"/createItem?name="+jobName;

		if(checkJobExists(jobName)) {
			jenkinsurl = baseURL+"/job/"+jobName+"/config.xml";
			logger.info("Will overwrite verify job for {} {}",project, slug);
		} else {
			logger.info("Creating new jenkins job for {} {}",project, slug);
		}
		
		HttpURLConnection connection = post(jenkinsurl, xml);
		logger.info("Created job for {} {} with response {}: {}",project, slug, connection.getResponseCode(),connection.getResponseMessage());
		
	}
	
	@SuppressWarnings("boxing")
	private boolean checkJobExists(String jobName) throws IOException {
		String jenkinsurl = baseURL+"/job/"+jobName+"/config.xml";
		HttpURLConnection connection = get(jenkinsurl);
		if(connection.getResponseCode()==200) {
			return true;
		}
		return false;
	}

	@SuppressWarnings("boxing")
	public void checkAdminCredentials(String baseurl, String username, String password) throws IOException, ConfigurationException{

		try {
			String xml = createEmptyJobXML();
			String jobName = "admin_job_check";
			
			HttpURLConnection connection = post(baseurl+"/createItem?name="+jobName, xml, username, password);
			
			int responseCode = connection.getResponseCode();
			String responseMessage = connection.getResponseMessage();
			if(responseMessage.matches("Invalid password/token for(.)*")) {
				throw new ConfigurationException("Invalid username or password for jenkins");
			} else if(responseCode == 301) {
				throw new ConfigurationException("Invalid jenkins URL");
			} else if(responseCode == 403) {
				throw new ConfigurationException("User does not have permission to create jobs on Jenkins server");
			} else if(String.valueOf(responseCode).matches("40\\d")) {
				throw new ConfigurationException("Invalid jenkins URL");
			}
			logger.info("Admin credentials for {}/**** @{} work.  Returned response {} - {}", username,baseurl,responseCode,responseMessage);
			deleteJob(baseurl, jobName);
			
		} catch(SocketTimeoutException e) {
			logger.error("Failed to connect to jenkins to check admin connection", e);
			throw new ConfigurationException("Unable to connect to jenkins URL (connect timed out)");
		} catch(ConnectException e) {
			logger.error("Failed to connect to jenkins to check admin connection", e);
			throw new ConfigurationException("Unable to connect to jenkins URL (connection refused)");
		} catch(MalformedURLException e) {
			logger.error("Failed to connect to jenkins to check admin connection", e);
			throw new ConfigurationException("Invalid jenkins URL");
		}
		
	}
	
	private void addAuthenticationCredentials(URLConnection connection) {
		addAuthenticationCredentials(connection, username, password);
	}

	private static void addAuthenticationCredentials(URLConnection connection, String username, String password) {
		String userpass = username + ":" + password;
		String basicAuth = "Basic " + javax.xml.bind.DatatypeConverter.printBase64Binary(userpass.getBytes());
		connection.setRequestProperty ("Authorization", basicAuth);
	}
	
	public String findCredentials(String baseurl, String username, String password, String user) throws IOException {
		logger.info("Finding credentials for {} on {}",user, baseurl);
		String userpass = username + ":" + password;
		String basicAuth = "Basic " + javax.xml.bind.DatatypeConverter.printBase64Binary(userpass.getBytes());

		Document document = Jsoup.connect(baseurl + "/credential-store/domain/_/").header("Authorization", basicAuth).get();

		Elements links = document.select("a[tooltip][href~=credential/(.)*]:contains("+user+")");
		Optional<Element> element = links.stream().findFirst();
		if(element.isPresent()) {
			String id = element.get().attr("href").split("/")[1];
			return id;
		}
		return null;
		
	}

	public String getBaseURL() {
		return baseURL;
	}

	private String getRepositoryURL(Repository repository) {
		RepositoryCloneLinksRequest request = new RepositoryCloneLinksRequest.Builder()
				.repository(repository)
				.protocol("ssh")
				.user(null)
				.build();
		return repositoryService.getCloneLinks(request).iterator().next().getHref();
	}

	private static final Pattern BITBUCKET_REPO_PATTERN = Pattern .compile("(.*)/scm/(.*)/(.*)\\.git");
	private String getBitbucketBaseURL(Repository repository) {
		RepositoryCloneLinksRequest request = new RepositoryCloneLinksRequest.Builder()
				.repository(repository)
				.protocol("http")
				.user(null)
				.build();
		
		String url = repositoryService.getCloneLinks(request).iterator().next().getHref();
		Matcher matcher = BITBUCKET_REPO_PATTERN.matcher(url);
		if(matcher.matches()) {
			return matcher.group(1);
		} 
		return null;
	}
	
}