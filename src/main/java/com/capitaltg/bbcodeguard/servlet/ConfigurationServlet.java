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
package com.capitaltg.bbcodeguard.servlet;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.atlassian.sal.api.pluginsettings.PluginSettings;
import com.atlassian.sal.api.pluginsettings.PluginSettingsFactory;
import com.atlassian.sal.api.user.UserManager;
import com.atlassian.soy.renderer.SoyException;
import com.atlassian.soy.renderer.SoyTemplateRenderer;
import com.capitaltg.bbcodeguard.ConfigurationException;
import com.capitaltg.bbcodeguard.jenkins.JenkinsManager;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;

/**
 * Updates the global plugin configuration
 * 
 * @author tslazar
 *
 */
public class ConfigurationServlet extends HttpServlet {

	public static final String CODEGUARD_SETTINGS_KEY = "com.capitaltg.bbcodeguard";

	private final Logger logger = LoggerFactory.getLogger(this.getClass());
	
	private final UserManager userManager;
	private final SoyTemplateRenderer soyTemplateRenderer;
	private final PluginSettings pluginSettings;
	private final JenkinsManager jenkinsManager;

	public ConfigurationServlet(UserManager userManager, SoyTemplateRenderer soyTemplateRenderer, 
			PluginSettingsFactory pluginSettingsFactory, JenkinsManager jenkinsManager) {
		this.userManager = userManager;
		this.soyTemplateRenderer = soyTemplateRenderer;
		this.jenkinsManager = jenkinsManager;
		this.pluginSettings = pluginSettingsFactory.createGlobalSettings();
	}

	@Override
	protected void doGet(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {
		
		if (!isAdmin(request)) {
			return;
		}
		
		Map<String, String> settings = (Map<String, String>) pluginSettings.get(CODEGUARD_SETTINGS_KEY);
		if (settings == null) {
			settings = new HashMap<>();
		}
		logger.debug("Current settings are: {}",settings);

		if(request.getParameter("clear")!=null) {
			pluginSettings.put(CODEGUARD_SETTINGS_KEY, null);
			settings = Maps.newHashMap();
		}
		
        response.setContentType("text/html;charset=UTF-8");
        try {
            soyTemplateRenderer.render(
            		response.getWriter(), 
            		"com.capitaltg.bbcodeguard:codeguardian-publish-config",
                    "com.atlassian.stash.repository.hook.ref.config",
                    ImmutableMap.of("config", settings));
            
        } catch (SoyException e) {
        	logger.error("Failed to render configuration request", e);
            Throwable cause = e.getCause();
            if (cause instanceof IOException) {
                throw (IOException) cause;
            }
            throw new ServletException(e);
        }

	}

	@Override
	public void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {

		if (!isAdmin(request)) {
			return;
		}

		Map<String, Object> errors = new HashMap<>(1);
		Map submittedSettings = request.getParameterMap();
		Map<String,String> storedSettings = new HashMap<>();
		
		String jenkinsBaseUrl = ((String[])submittedSettings.get("jenkinsBaseUrl"))[0];
		String username = ((String[])submittedSettings.get("username"))[0];
		String password = ((String[])submittedSettings.get("password"))[0];
		String bbusername = ((String[])submittedSettings.get("bbusername"))[0];
		String bbpassword = ((String[])submittedSettings.get("bbpassword"))[0];
		
		logger.info("Set jenkinsBaseUrl: {}", jenkinsBaseUrl);
		logger.info("Set username: {}", username);
		logger.info("Set password: {}", password);
		logger.info("Set bbusername: {}", bbusername);
		logger.info("Set bbpassword: {}", bbpassword);
		
		try {
			jenkinsManager.checkAdminCredentials(jenkinsBaseUrl, username, password);
		} catch(ConfigurationException e) {
			logger.error("Failed to save settings", e);
			if(e.getMessage().contains("username")) {
				errors.put("username", Arrays.asList(e.getMessage()));
			} else {
				errors.put("jenkinsBaseUrl", Arrays.asList(e.getMessage()));
			}
		}

		String credentialsId = jenkinsManager.findCredentials(jenkinsBaseUrl, username, password, username);
		if(credentialsId==null){
			errors.put("jenkinsbbuser", Arrays.asList("Jenkins server does not have user credentials defined for "+username));
		}

		storedSettings.put("jenkinsBaseUrl", jenkinsBaseUrl);
		storedSettings.put("username", username);
		storedSettings.put("password", password);
		storedSettings.put("bbusername", bbusername);
		storedSettings.put("bbpassword", bbpassword);
		storedSettings.put("credentialsId", credentialsId);
		
		Map<String, Object> map = Maps.newHashMap();
		map.put("config", storedSettings); 
		map.put("errors", errors);

		if(errors.isEmpty()) {
			map.put("success", "Successfully saved settings");
			pluginSettings.put(CODEGUARD_SETTINGS_KEY, storedSettings);
			logger.info("Successfully updated settings");
			String redirect = request.getRequestURI().replace("codeguard/config", "upm");
			response.sendRedirect(redirect);
			jenkinsManager.reload();
			return;
		} 

		// don't save, don't declare success
		logger.error("Failed to update settings: {}",errors.values().stream().findFirst().get());
		
        response.setContentType("text/html;charset=UTF-8");
        try {
            soyTemplateRenderer.render(
            		response.getWriter(), 
            		"com.capitaltg.bbcodeguard:codeguardian-publish-config",
                    "com.atlassian.stash.repository.hook.ref.config",
                    map);
            
        } catch (SoyException e) {
        	logger.error("Failed to render configuration request", e);
            Throwable cause = e.getCause();
            if (cause instanceof IOException) {
                throw (IOException) cause;
            }
            throw new ServletException(e);
        }

    }

	private boolean isAdmin(HttpServletRequest request){
		String username = userManager.getRemoteUsername(request);
		logger.info("Logged in user is: {}. System Admin: {}", username, userManager.isSystemAdmin(username));
		return userManager.isSystemAdmin(username);
	}

}