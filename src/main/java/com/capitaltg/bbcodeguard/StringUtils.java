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
package com.capitaltg.bbcodeguard;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.util.Map;

import org.apache.commons.io.IOUtils;

public class StringUtils {

	public static String readResourceAndUpdateText(String resourceName, Map<String,String> values) throws IOException {
		InputStream inputStream = StringUtils.class.getClassLoader().getResourceAsStream(resourceName);
		StringWriter writer = new StringWriter();
		IOUtils.copy(inputStream, writer);
		inputStream.close();
		return StringUtils.replaceAll(writer.toString(), values);
	}
	
	public static String replaceAll(String original, Map<String,String> replacements) {
		if(original == null || replacements == null || replacements.isEmpty()){
			return original;
		}
		StringHolder response = new StringHolder(original);
		replacements.forEach( (n,v) -> response.replaceAll("@"+n+"@", v) );
		return response.toString();
	}

	private static class StringHolder {
		private String string;
		public StringHolder(String string) {
			this.string = string;
		}
		public void replaceAll(String regex, String replacement) {
			string = string.replaceAll(regex, replacement);
		}
		@Override
		public String toString() {
			return string;
		}
	}

}