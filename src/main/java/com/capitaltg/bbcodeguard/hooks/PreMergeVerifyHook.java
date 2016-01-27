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

import com.atlassian.bitbucket.hook.repository.AsyncPostReceiveRepositoryHook;
import com.atlassian.bitbucket.hook.repository.RepositoryHookContext;
import com.atlassian.bitbucket.repository.RefChange;

/**
 * This class does nothing at all.  This class exists so that the verify 
 * hook can be configured and enabled/disabled per repository
 * 
 * @author tslazar
 *
 */
public class PreMergeVerifyHook implements AsyncPostReceiveRepositoryHook {

	public static final String VERIFY_HOOK_KEY = "com.capitaltg.bbcodeguard:jenkinsVerifyHook";

	@Override
	public void postReceive(RepositoryHookContext context, Collection<RefChange> refChanges) {}
	
}
