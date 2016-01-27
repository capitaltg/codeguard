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

import java.util.regex.Pattern;

import com.atlassian.bitbucket.event.pull.PullRequestEvent;
import com.atlassian.bitbucket.event.pull.PullRequestOpenedEvent;
import com.atlassian.bitbucket.event.pull.PullRequestRescopedEvent;
import com.atlassian.bitbucket.hook.repository.RepositoryHookService;
import com.atlassian.bitbucket.pull.PullRequest;
import com.atlassian.bitbucket.pull.PullRequestService;
import com.atlassian.bitbucket.repository.Repository;
import com.atlassian.bitbucket.setting.Settings;
import com.atlassian.event.api.EventListener;
import com.capitaltg.bbcodeguard.jenkins.JenkinsManager;
import com.google.common.base.Strings;

/**
 * Listens for events that should trigger a verify job
 * or invalidate the results of a previously run job
 * 
 * @author tslazar
 *
 */
public class PullRequestListener {

	private final PullRequestService pullRequestService;
	private final RepositoryHookService hookService;
	private final JenkinsManager jenkinsManager;
	
	public PullRequestListener(PullRequestService pullRequestService, RepositoryHookService hookService, 
			JenkinsManager jenkinsManager) {
		this.jenkinsManager = jenkinsManager;
		this.pullRequestService = pullRequestService;
		this.hookService = hookService;
	}

	@EventListener
	public void onPullRequestOpened(PullRequestOpenedEvent event) {
    	if(shouldRunVerifyJob(event.getPullRequest())) {
			startVerifyJob(event);
    	}
	}

	@EventListener
	public void onPullRequestRescoped(PullRequestRescopedEvent event) {
    	if(shouldRunVerifyJob(event.getPullRequest())) {
			addPRComment(event, "Pull request was rescoped. Verification job will be rerun.");
			startVerifyJob(event);
    	}
	}

	private void startVerifyJob(PullRequestEvent event) {
		Repository repository = event.getPullRequest().getToRef().getRepository();
		jenkinsManager.startVerifyJob(repository,
				event.getPullRequest().getId(), event.getPullRequest().getToRef().getDisplayId(),
				event.getPullRequest().getFromRef().getDisplayId());

	}

	private void addPRComment(PullRequestEvent event, String text){
		pullRequestService.addComment(event.getPullRequest().getToRef().getRepository().getId(), event.getPullRequest().getId(), text);
	}

	private boolean shouldRunVerifyJob(PullRequest pullRequest) {
    	
    	Settings settings = hookService.getSettings(pullRequest.getToRef().getRepository(), PreMergeVerifyHook.VERIFY_HOOK_KEY);
    	if(settings == null) {
    		return false;
    	}
    	
    	String branchesToVerify = settings.getString("regexToVerify");
    	String branch = pullRequest.getToRef().getDisplayId();
    	
    	if(Strings.isNullOrEmpty(branchesToVerify)) {
    		return false;
    	}
    	
    	return Pattern.matches(branchesToVerify, branch);
    	
    }
    
}
