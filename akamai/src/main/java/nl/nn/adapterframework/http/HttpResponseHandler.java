/*
   Copyright 2017 Integration Partners

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
*/
package nl.nn.adapterframework.http;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import nl.nn.adapterframework.util.Misc;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.entity.ContentType;
import org.apache.http.util.EntityUtils;

public class HttpResponseHandler {
	private HttpResponse httpResponse;
	private HttpEntity httpEntity;
	private InputStream content;
	private String contentAsString = null;
	private int statusCode = -1;

	public HttpResponseHandler(HttpResponse resp) throws IllegalStateException, IOException {
		httpResponse = resp;
		httpEntity = httpResponse.getEntity();
		content = httpEntity.getContent();
	}

	public StatusLine getStatus() {
		return httpResponse.getStatusLine();
	}

	public Header[] getAllHeaders() {
		return httpResponse.getAllHeaders();
	}

	public InputStream getContent() throws IOException {
		statusCode = getStatus().getStatusCode();
		return content;
	}

	public String getContentAsString() throws IOException {
		return getContentAsString(false);
	}

	public String getContentAsString(boolean returnNullonFault) throws IOException {
		if(statusCode < 0)
			contentAsString = Misc.streamToString(getContent(), "\n", getContentType(), false);
		else if(returnNullonFault && statusCode == 500)
			return "";

		return contentAsString;
	}

	public String getHeader(String header) {
		return httpResponse.getFirstHeader(header).getValue();
	}

	public HttpEntity getEntity() {
		return httpEntity;
	}

	public String getContentType() {
		Header contentTypeHeader = this.getFirstHeader(HttpHeaders.CONTENT_TYPE);
		ContentType contentType;
		if (contentTypeHeader != null) {
			contentType = ContentType.parse(contentTypeHeader.getValue());
		} else {
			contentType = ContentType.getOrDefault(httpEntity);
		}
		String charSet = Misc.DEFAULT_INPUT_STREAM_ENCODING;
		if(contentType != null && contentType.getCharset() != null)
			charSet = contentType.getCharset().displayName();
		return charSet;
	}

	public void close() throws IOException {
		EntityUtils.consume(httpEntity);
		content.close();
	}

	public Header getFirstHeader(String string) {
		return httpResponse.getFirstHeader(string);
	}

	public Map<String, List<String>> getHeaderFields() {
		Map<String, List<String>> headerMap = new HashMap<String, List<String>>();
		Header[] headers = httpResponse.getAllHeaders();
		for (int i = 0; i < headers.length; i++) {
			Header header = headers[i];
			String name = header.getName().toLowerCase();
			List<String> value;
			if(headerMap.containsKey(name)) {
				value = headerMap.get(name);
			}
			else {
				value = new ArrayList<String>();
			}
			value.add(header.getValue());
			headerMap.put(name, value);
		}
		return headerMap;
	}
}
