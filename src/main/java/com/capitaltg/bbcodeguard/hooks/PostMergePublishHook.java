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
package com.capitaltg.bbcodeguard.hooks;

import java.util.Collection;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.atlassian.bitbucket.hook.repository.AsyncPostReceiveRepositoryHook;
import com.atlassian.bitbucket.hook.repository.RepositoryHookContext;
import com.atlassian.bitbucket.repository.RefChange;
import com.capitaltg.bbcodeguard.jenkins.JenkinsManager;
import com.google.common.base.Strings;

public class PostMergePublishHook implements AsyncPostReceiveRepositoryHook {

	private Logger logger = LoggerFactory.getLogger(this.getClass());

	private final JenkinsManager jenkinsManager;
	
	public PostMergePublishHook(JenkinsManager jenkinsManager) {
		this.jenkinsManager = jenkinsManager;
	}

	@Override
	public void postReceive(RepositoryHookContext context, Collection<RefChange> changes) {

		try {
			
			String regexToPublish = Strings.nullToEmpty(context.getSettings().getString("regexToPublish"));
	    	Set<String> branchesToPublish = changes
	    			.stream()
	    			.map( c -> c.getRef().getDisplayId())
	    			.filter( c -> Pattern.matches(regexToPublish, c))
	    			.collect(Collectors.toSet());
	    	branchesToPublish.stream().forEach( branch -> jenkinsManager.startPublishJob(context.getRepository(), branch) );
	    	
		} catch(Exception e) {
			logger.error("TODO Failed to invoke publish after pull request merge",e);
		}
    	
	}
	
}