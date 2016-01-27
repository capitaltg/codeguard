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

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import javax.annotation.Nonnull;

import com.atlassian.bitbucket.comment.Comment;
import com.atlassian.bitbucket.hook.repository.RepositoryHookService;
import com.atlassian.bitbucket.pull.PullRequest;
import com.atlassian.bitbucket.pull.PullRequestActivity;
import com.atlassian.bitbucket.pull.PullRequestCommentActivity;
import com.atlassian.bitbucket.pull.PullRequestService;
import com.atlassian.bitbucket.scm.pull.MergeRequest;
import com.atlassian.bitbucket.scm.pull.MergeRequestCheck;
import com.atlassian.bitbucket.setting.Settings;
import com.atlassian.bitbucket.util.Page;
import com.atlassian.bitbucket.util.PageRequest;
import com.atlassian.bitbucket.util.PageRequestImpl;
import com.google.common.base.Strings;

public class PreMergeVerifyCheck implements MergeRequestCheck {
	
	private final PullRequestService pullRequestService;
	private final RepositoryHookService hookService;	
	
	public PreMergeVerifyCheck(PullRequestService pullRequestService, RepositoryHookService hookService) {
		this.pullRequestService = pullRequestService;
		this.hookService = hookService;
	}
	
    @Override
	public void check(@Nonnull MergeRequest request) {
    
    	if(!needsToBeVerified(request)) {
    		return;
    	}
    	
    	PullRequest pullRequest = request.getPullRequest();
    	Optional<Comment> optional = findLatestVerifyComment(pullRequest);
    	
    	if(!optional.isPresent()){
    		request.veto("No succesful build", "Verification job needs to be run");
    	} else if(optional.get().getText().contains("failed")) {
    		request.veto("No succesfull", "Last verification job failed");
    	} else if(optional.get().getText().contains("Pull request was rescoped")) {
    		request.veto("No succesfull", "New verification job needs to complete after PR rescoping");
    	}
    	
    	return;
    	
    }
    
	private Optional<Comment> findLatestVerifyComment(PullRequest pullRequest) {

    	int repoId = pullRequest.getToRef().getRepository().getId();
    	long prid = pullRequest.getId();

    	PageRequest pageRequest = new PageRequestImpl(0,1000);
    	Page<PullRequestActivity> activities = pullRequestService.getActivities(repoId,prid,pageRequest);
    	
    	List<Comment> comments = (StreamSupport.stream(activities.getValues().spliterator(), false)
    			.filter(isPullRequestCommentActivity)
    			.map( a -> ((PullRequestCommentActivity)a).getComment())
    			.filter( c -> Strings.nullToEmpty(c.getText()).toLowerCase().contains("verification job"))
    			.sorted(compareByReverseDate)
    			.collect( Collectors.toList()));

    	Optional<Comment> optional = comments.stream().findFirst();
    	return optional;

    }
    
	private boolean needsToBeVerified(MergeRequest mergeRequest) {
    	
    	Settings settings = hookService.getSettings(mergeRequest.getPullRequest().getToRef().getRepository(), PreMergeVerifyHook.VERIFY_HOOK_KEY);
    	if(settings == null) {
    		return false;
    	}
    	
    	String branchesToVerify = settings.getString("regexToVerify");
    	String branch = mergeRequest.getPullRequest().getToRef().getDisplayId();
    	
    	if(Strings.isNullOrEmpty(branchesToVerify)) {
    		return false;
    	}
    	
    	boolean check = Pattern.matches(branchesToVerify, branch);
    	return check;
    }
    
	private static final Predicate<PullRequestActivity> isPullRequestCommentActivity = (a) -> a instanceof PullRequestCommentActivity;
	private static final Comparator<Comment> compareByReverseDate = (c1,c2) -> { return c2.getCreatedDate().compareTo(c1.getCreatedDate());} ;
    
}
