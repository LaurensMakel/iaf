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
import java.io.UnsupportedEncodingException;
import java.net.SocketTimeoutException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;

import javax.net.ssl.SSLContext;
import javax.servlet.http.HttpServletResponse;
import javax.xml.transform.TransformerConfigurationException;

import nl.nn.adapterframework.configuration.ConfigurationException;
import nl.nn.adapterframework.core.HasPhysicalDestination;
import nl.nn.adapterframework.core.IPipeLineSession;
import nl.nn.adapterframework.core.ParameterException;
import nl.nn.adapterframework.core.SenderException;
import nl.nn.adapterframework.core.TimeOutException;
import nl.nn.adapterframework.core.TimeoutGuardSenderWithParametersBase;
import nl.nn.adapterframework.parameters.Parameter;
import nl.nn.adapterframework.parameters.ParameterResolutionContext;
import nl.nn.adapterframework.parameters.ParameterValue;
import nl.nn.adapterframework.parameters.ParameterValueList;
import nl.nn.adapterframework.util.ClassUtils;
import nl.nn.adapterframework.util.CredentialFactory;
import nl.nn.adapterframework.util.Misc;
import nl.nn.adapterframework.util.TransformerPool;
import nl.nn.adapterframework.util.XmlUtils;

import org.apache.commons.lang.StringUtils;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.Credentials;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.AuthCache;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.config.AuthSchemes;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.config.RequestConfig.Builder;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.client.BasicAuthCache;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.DefaultRedirectStrategy;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.htmlcleaner.CleanerProperties;
import org.htmlcleaner.HtmlCleaner;
import org.htmlcleaner.SimpleXmlSerializer;
import org.htmlcleaner.TagNode;

/**
 * Sender for the HTTP protocol using GET, POST, PUT or DELETE using httpclient 4+
 * 
 * <p><b>Configuration:</b>
 * <table border="1">
 * <tr><th>attributes</th><th>description</th><th>default</th></tr>
 * <tr><td>classname</td><td>nl.nn.adapterframework.http.HttpSender</td><td>&nbsp;</td></tr>
 * <tr><td>{@link #setName(String) name}</td><td>name of the sender</td><td>&nbsp;</td></tr>
 * <tr><td>{@link #setUrl(String) url}</td><td>URL or base of URL to be used </td><td>&nbsp;</td></tr>
 * <tr><td>{@link #setUrlParam(String) urlParam}</td><td>parameter that is used to obtain url; overrides url-attribute.</td><td>url</td></tr>
 * <tr><td>{@link #setMethodType(String) methodType}</td><td>type of method to be executed, either 'GET', 'POST', 'PUT', 'DELETE', 'HEAD' or 'REPORT'</td><td>GET</td></tr>
 * <tr><td>{@link #setContentType(String) contentType}</td><td>content-type of the request, only for POST and PUT methods</td><td>text/html; charset=UTF-8</td></tr>
 * <tr><td>{@link #setTimeout(int) timeout}</td><td>timeout in ms of obtaining a connection/result. 0 means no timeout</td><td>10000</td></tr>
 * <tr><td>{@link #setMaxConnections(int) maxConnections}</td><td>the maximum number of concurrent connections</td><td>10</td></tr>
 * <tr><td>{@link #setMaxExecuteRetries(int) maxExecuteRetries}</td><td>the maximum number of times it the execution is retried</td><td>1</td></tr>
 * <tr><td>{@link #setAuthAlias(String) authAlias}</td><td>alias used to obtain credentials for authentication to host</td><td>&nbsp;</td></tr>
 * <tr><td>{@link #setUserName(String) userName}</td><td>username used in authentication to host</td><td>&nbsp;</td></tr>
 * <tr><td>{@link #setPassword(String) password}</td><td>&nbsp;</td><td>&nbsp;</td></tr>
 * <tr><td>{@link #setProxyHost(String) proxyHost}</td><td>&nbsp;</td><td>&nbsp;</td></tr>
 * <tr><td>{@link #setProxyPort(int) proxyPort}</td><td>&nbsp;</td><td>80</td></tr>
 * <tr><td>{@link #setProxyAuthAlias(String) proxyAuthAlias}</td><td>alias used to obtain credentials for authentication to proxy</td><td>&nbsp;</td></tr>
 * <tr><td>{@link #setProxyUserName(String) proxyUserName}</td><td>&nbsp;</td><td>&nbsp;</td></tr>
 * <tr><td>{@link #setProxyPassword(String) proxyPassword}</td><td>&nbsp;</td><td>&nbsp;</td></tr>
 * <tr><td>{@link #setProxyRealm(String) proxyRealm}</td><td>&nbsp;</td><td>&nbsp;</td></tr>
 * <tr><td>{@link #setCertificate(String) certificate}</td><td>resource URL to certificate to be used for authentication</td><td>&nbsp;</td></tr>
 * <tr><td>{@link #setCertificateAuthAlias(String) certificateAuthAlias}</td><td>alias used to obtain certificate password</td><td>&nbsp;</td></tr>
 * <tr><td>{@link #setCertificatePassword(String) certificatePassword}</td><td>&nbsp;</td><td>&nbsp;</td></tr>
 * <tr><td>{@link #setKeystoreType(String) keystoreType}</td><td>&nbsp;</td><td>pkcs12</td></tr>
 * <tr><td>{@link #setKeyManagerAlgorithm(String) keyManagerAlgorithm}</td><td>&nbsp;</td><td></td></tr>
 * <tr><td>{@link #setTruststore(String) truststore}</td><td>resource URL to truststore to be used for authentication</td><td>&nbsp;</td></tr>
 * <tr><td>{@link #setTruststoreAuthAlias(String) truststoreAuthAlias}</td><td>alias used to obtain truststore password</td><td>&nbsp;</td></tr>
 * <tr><td>{@link #setTruststorePassword(String) truststorePassword}</td><td>&nbsp;</td><td>&nbsp;</td></tr>
 * <tr><td>{@link #setTruststoreType(String) truststoreType}</td><td>&nbsp;</td><td>jks</td></tr>
 * <tr><td>{@link #setTrustManagerAlgorithm(String) trustManagerAlgorithm}</td><td>&nbsp;</td><td></td></tr>
 * <tr><td>{@link #setAllowSelfSignedCertificates(boolean) allowSelfSignedCertificates}</td><td>when true, self signed certificates are accepted</td><td>false</td></tr>
 * <tr><td>{@link #setFollowRedirects(boolean) followRedirects}</td><td>when true, a redirect request will be honoured, e.g. to switch to https</td><td>true</td></tr>
 * <tr><td>{@link #setVerifyHostname(boolean) verifyHostname}</td><td>when true, the hostname in the certificate will be checked against the actual hostname</td><td>true</td></tr>
 * <tr><td>{@link #setJdk13Compatibility(boolean) jdk13Compatibility}</td><td>enables the use of certificates on JDK 1.3.x. The SUN reference implementation JSSE 1.0.3 is included for convenience</td><td>false</td></tr>
 * <tr><td>{@link #setStaleChecking(boolean) staleChecking}</td><td>controls whether connections checked to be stale, i.e. appear open, but are not.</td><td>true</td></tr>
 * <tr><td>{@link #setEncodeMessages(boolean) encodeMessages}</td><td>specifies whether messages will encoded, e.g. spaces will be replaced by '+' etc.</td><td>false</td></tr>
 * <tr><td>{@link #setParamsInUrl(boolean) paramsInUrl}</td><td>when false and <code>methodeType=POST</code>, request parameters are put in the request body instead of in the url</td><td>true</td></tr>
 * <tr><td>{@link #setInputMessageParam(String) inputMessageParam}</td><td>(only used when <code>methodeType=POST</code> and <code>paramsInUrl=false</code>) name of the request parameter which is used to put the input message in</td><td>&nbsp;</td></tr>
 * <tr><td>{@link #setHeadersParams(String) headersParams}</td><td>Comma separated list of parameter names which should be set as http headers</td><td>&nbsp;</td></tr>
 * <tr><td>{@link #setIgnoreRedirects(boolean) ignoreRedirects}</td><td>when true, besides http status code 200 (OK) also the code 301 (MOVED_PERMANENTLY), 302 (MOVED_TEMPORARILY) and 307 (TEMPORARY_REDIRECT) are considered successful</td><td>false</td></tr>
 * <tr><td>{@link #setIgnoreCertificateExpiredException(boolean) ignoreCertificateExpiredException}</td><td>when true, the CertificateExpiredException is ignored</td><td>false</td></tr>
 * <tr><td>{@link #setXhtml(boolean) xhtml}</td><td>when true, the html response is transformed to xhtml</td><td>false</td></tr>
 * <tr><td>{@link #setStyleSheetName(String) styleSheetName}</td><td>>(only used when <code>xhtml=true</code>) stylesheet to apply to the html response</td><td>&nbsp;</td></tr>
 * <tr><td>{@link #setMultipart(boolean) multipart}</td><td>when true and <code>methodeType=POST</code> and <code>paramsInUrl=false</code>, request parameters are put in a multipart/form-data entity instead of in the request body</td><td>false</td></tr>
 * <tr><td>{@link #setMultipartResponse(boolean) multipartResponse}</td><td>when true the response body is expected to be in mime multipart which is the case when a soap message with attachments is received (see also <a href="https://docs.oracle.com/javaee/7/api/javax/xml/soap/SOAPMessage.html">https://docs.oracle.com/javaee/7/api/javax/xml/soap/SOAPMessage.html</a>). The first part will be returned as result of this sender. Other parts are returned as streams in sessionKeys with names multipart1, multipart2, etc. The http connection is held open until the last stream is read.</td><td>false</td></tr>
 * <tr><td>{@link #setStreamResultToServlet(boolean) streamResultToServlet}</td><td>if set, the result is streamed to the HttpServletResponse object of the RestServiceDispatcher (instead of passed as a String)</td><td>false</td></tr>
 * <tr><td>{@link #setBase64(boolean) base64}</td><td>when true, the result is base64 encoded</td><td>false</td></tr>
 * <tr><td>{@link #setProtocol(String) protocol}</td><td>Secure socket protocol (such as "SSL" and "TLS") to use when a SSLContext object is generated. If empty the protocol "SSL" is used</td><td>&nbsp;</td></tr>
 * <tr><td>{@link #setStreamResultToFileNameSessionKey(String) streamResultToFileNameSessionKey}</td><td>if set, the result is streamed to a file (instead of passed as a String)</td><td>&nbsp;</td></tr>
 * <tr><td>{@link #setStoreResultAsStreamInSessionKey(String) storeResultAsStreamInSessionKey}</td><td>if set, a pointer to an input stream of the result is put in the specified sessionKey (as the sender interface only allows a sender to return a string a sessionKey is used instead to return the stream)</td><td>&nbsp;</td></tr>
 * <tr><td>{@link #setResultStatusCodeSessionKey(String) resultStatusCodeSessionKey}</td><td>if set, the status code of the HTTP response is put in specified in the sessionKey and the (error or okay) response message is returned</td><td>&nbsp;</td></tr>
 * <tr><td>{@link #setMultipartXmlSessionKey(String) multipartXmlSessionKey}</td><td>if set and <code>methodeType=POST</code> and <code>paramsInUrl=false</code>, a multipart/form-data entity is created instead of a request body. For each part element in the session key a part in the multipart entity is created</td><td>&nbsp;</td></tr>
 * </table>
 * </p>
 * <p><b>Parameters:</b></p>
 * <p>Any parameters present are appended to the request as request-parameters except the headersParams list which are added as http headers</p>
 * 
 * <p><b>Expected message format:</b></p>
 * <p>GET methods expect a message looking like this</p>
 * <pre>
 *   param_name=param_value&another_param_name=another_param_value
 * </pre>
 * <p>POST AND PUT methods expect a message similar as GET, or looking like this</p>
 * <pre>
 *   param_name=param_value
 *   another_param_name=another_param_value
 * </pre>
 *
 * <p>
 * Note 1:
 * Some certificates require the &lt;java_home&gt;/jre/lib/security/xxx_policy.jar files to be upgraded to unlimited strength. Typically, in such a case, an error message like 
 * <code>Error in loading the keystore: Private key decryption error: (java.lang.SecurityException: Unsupported keysize or algorithm parameters</code> is observed.
 * For IBM JDKs these files can be downloaded from http://www.ibm.com/developerworks/java/jdk/security/50/ (scroll down to 'IBM SDK Policy files')
 * </p>
 * Replace in the directory java\jre\lib\security the following files:
 * <ul>
 * <li>local_policy.jar</li>
 * <li>US_export_policy.jar</li>
 * </ul>
 * <p>
 * Note 2:
 * To debug ssl-related problems, set the following system property:
 * <ul>
 * <li>IBM / WebSphere: <code>-Djavax.net.debug=true</code></li>
 * <li>SUN: <code>-Djavax.net.debug=all</code></li>
 * </ul>
 * </p>
 * <p>
 * Note 3:
 * In case <code>javax.net.ssl.SSLHandshakeException: unknown certificate</code>-exceptions are thrown, 
 * probably the certificate of the other party is not trusted. Try to use one of the certificates in the path as your truststore by doing the following:
 * <ul>
 *   <li>open the URL you are trying to reach in InternetExplorer</li>
 *   <li>click on the yellow padlock on the right in the bottom-bar. This opens the certificate information window</li>
 *   <li>click on tab 'Certificeringspad'</li>
 *   <li>double click on root certificate in the tree displayed. This opens the certificate information window for the root certificate</li>
 *   <li>click on tab 'Details'</li>
 *   <li>click on 'Kopieren naar bestand'</li>
 *   <li>click 'next', choose 'DER Encoded Binary X.509 (.CER)'</li>
 *   <li>click 'next', choose a filename</li>
 *   <li>click 'next' and 'finish'</li>
 * 	 <li>Start IBM key management tool ikeyman.bat, located in Program Files/IBM/WebSphere Studio/Application Developer/v5.1.2/runtimes/base_v51/bin (or similar)</li>
 *   <li>create a new key-database (Sleuteldatabase -> Nieuw...), or open the default key.jks (default password="changeit")</li>
 *   <li>add the generated certificate (Toevoegen...)</li>
 *   <li>store the key-database in JKS format</li>
 *   <li>if you didn't use the standard keydatabase, then reference the file in the truststore-attribute in Configuration.xml (include the file as a resource)</li>
 *   <li>use jks for the truststoreType-attribute</li>
 *   <li>restart your application</li>
 *   <li>instead of IBM ikeyman you can use the standard java tool <code>keytool</code> as follows: 
 *      <code>keytool -import -alias <i>yourAlias</i> -file <i>pathToSavedCertificate</i></code></li>
 * </ul>
 * <p>
 * Note 4:
 * In case <code>cannot create or initialize SocketFactory: (IOException) Unable to verify MAC</code>-exceptions are thrown,
 * please check password or authAlias configuration of the corresponding certificate. 
 * </p>
 * 
 * @author	Niels Meijer
 * @since	7.0
 */
//TODO: Fix javadoc!

public abstract class HttpSenderBase extends TimeoutGuardSenderWithParametersBase implements HasPhysicalDestination {
	private ClassLoader classLoader = Thread.currentThread().getContextClassLoader();

	private String url;
	private String urlParam = "url";
	private String methodType = "GET";
	private String contentType = "text/html; charset="+getCharSet();

	/** CONNECTION POOL **/
	private int timeout = 10000;
	private int maxConnections = 10;
	private int maxExecuteRetries = 1;
	private PoolingHttpClientConnectionManager connectionManager;
	protected HttpClientBuilder httpClientBuilder = HttpClientBuilder.create();
	protected HttpClientContext httpClientContext = HttpClientContext.create();
	protected HttpHost httpTarget;

	/** SECURITY */
	private String authAlias;
	private String userName;
	private String password;
	private String authDomain;

	/** PROXY **/
	private String proxyHost;
	private int    proxyPort=80;
	private String proxyAuthAlias;
	private String proxyUserName;
	private String proxyPassword;
	private String proxyRealm=null;

	/** SSL **/
	private String certificate;
	private String certificateAuthAlias;
	private String certificatePassword;
	private String keystoreType="pkcs12";
	private String keyManagerAlgorithm=null;
	private String truststore=null;
	private String truststoreAuthAlias;
	private String truststorePassword=null;
	private String truststoreType="jks";
	private String trustManagerAlgorithm=null;
	private boolean allowSelfSignedCertificates = false;
	private boolean verifyHostname=true;
	private boolean jdk13Compatibility=false;
	private boolean ignoreCertificateExpiredException=false;

	private String inputMessageParam=null;
	private String headersParams=null;
	private boolean followRedirects=true;
	private boolean staleChecking=true;
	private int staleTimeout = 5000;
	private boolean encodeMessages=false;
	private boolean paramsInUrl=true;
	private boolean ignoreRedirects=false;
	private boolean xhtml=false;
	private String styleSheetName=null;
	private boolean multipart=false;
	private boolean multipartResponse=false;
	private boolean streamResultToServlet=false;
	private String streamResultToFileNameSessionKey=null;
	private boolean base64=false;
	private String protocol=null;
	private String storeResultAsStreamInSessionKey;
	private String storeResultAsByteArrayInSessionKey;
	private String resultStatusCodeSessionKey;
	private String multipartXmlSessionKey;

	private TransformerPool transformerPool=null;

	protected Parameter urlParameter;

	protected URIBuilder staticUri;
	private CredentialFactory credentials;

	private String charSet = Misc.DEFAULT_INPUT_STREAM_ENCODING;

	private Set<Parameter> parametersToSkip=new HashSet<Parameter>();

	protected void addParameterToSkip(Parameter param) {
		if (param!=null) {
			parametersToSkip.add(param);
		}
	}

	protected URIBuilder getURI(String url) throws URISyntaxException {
		URIBuilder uri = new URIBuilder(url);

		if (uri.getPath()==null) {
			uri.setPath("/");
		}

		log.info(getLogPrefix()+"created uri: scheme=["+uri.getScheme()+"] host=["+uri.getHost()+"] path=["+uri.getPath()+"]");
		return uri;
	}

	protected int getPort(URIBuilder uri) {
		int port = uri.getPort();
		if (port<1) {
			try {
				log.debug(getLogPrefix()+"looking up protocol for scheme ["+uri.getScheme()+"]");
				URL url = uri.build().toURL();
				port = url.getDefaultPort();
			} catch (Exception e) {
				log.debug(getLogPrefix()+"protocol for scheme ["+uri.getScheme()+"] not found, setting port to 80",e);
				port=80; 
			}
		}
		return port;
	}

	public void configure() throws ConfigurationException {
		super.configure();

		if (!getMethodType().equals("POST")) {
			if (!isParamsInUrl()) {
				throw new ConfigurationException(getLogPrefix()+"paramsInUrl can only be set to false for methodType POST");
			}
			if (StringUtils.isNotEmpty(getInputMessageParam())) {
				throw new ConfigurationException(getLogPrefix()+"inputMessageParam can only be set for methodType POST");
			}
		}

		Builder requestConfig = RequestConfig.custom();
		requestConfig.setConnectTimeout(getTimeout());
		requestConfig.setConnectionRequestTimeout(getTimeout());
		requestConfig.setSocketTimeout(getTimeout());

		if (paramList!=null) {
			paramList.configure();
			if (StringUtils.isNotEmpty(getUrlParam())) {
				urlParameter = paramList.findParameter(getUrlParam());
				addParameterToSkip(urlParameter);
			}
		}
		if (getMaxConnections() <= 0) {
			throw new ConfigurationException(getLogPrefix()+"maxConnections is set to ["+getMaxConnections()+"], which is not enough for adequate operation");
		}
		try {
			if (urlParameter == null) {
				if (StringUtils.isEmpty(getUrl())) {
					throw new ConfigurationException(getLogPrefix()+"url must be specified, either as attribute, or as parameter");
				}
				staticUri = getURI(getUrl());
			}

			URL certificateUrl = null;
			URL truststoreUrl = null;
	
			if (!StringUtils.isEmpty(getCertificate())) {
				certificateUrl = ClassUtils.getResourceURL(classLoader, getCertificate());
				if (certificateUrl == null) {
					throw new ConfigurationException(getLogPrefix()+"cannot find URL for certificate resource ["+getCertificate()+"]");
				}
				log.info(getLogPrefix()+"resolved certificate-URL to ["+certificateUrl.toString()+"]");
			}
			if (!StringUtils.isEmpty(getTruststore())) {
				truststoreUrl = ClassUtils.getResourceURL(classLoader, getTruststore());
				if (truststoreUrl == null) {
					throw new ConfigurationException(getLogPrefix()+"cannot find URL for truststore resource ["+getTruststore()+"]");
				}
				log.info(getLogPrefix()+"resolved truststore-URL to ["+truststoreUrl.toString()+"]");
			}

			if (certificateUrl != null || truststoreUrl != null || allowSelfSignedCertificates) {
				AuthSSLProtocolSocketFactoryBase socketfactory;
				try {
					CredentialFactory certificateCf = new CredentialFactory(getCertificateAuthAlias(), null, getCertificatePassword());
					CredentialFactory truststoreCf  = new CredentialFactory(getTruststoreAuthAlias(),  null, getTruststorePassword());

					if (isJdk13Compatibility()) {
						socketfactory = new AuthSSLProtocolSocketFactoryForJsse10x(
							certificateUrl, certificateCf.getPassword(), getKeystoreType(), getKeyManagerAlgorithm(),
							truststoreUrl,  truststoreCf.getPassword(),  getTruststoreType(), getTrustManagerAlgorithm(),
							isAllowSelfSignedCertificates(), isVerifyHostname(), isIgnoreCertificateExpiredException());
					} else {
						socketfactory = new AuthSSLProtocolSocketFactory(
							certificateUrl, certificateCf.getPassword(), getKeystoreType(), getKeyManagerAlgorithm(),
							truststoreUrl,  truststoreCf.getPassword(),  getTruststoreType(), getTrustManagerAlgorithm(),
							isAllowSelfSignedCertificates(), isVerifyHostname(), isIgnoreCertificateExpiredException());
					}

					if (StringUtils.isNotEmpty(getProtocol())) {
						socketfactory.setProtocol(getProtocol());
					}

					socketfactory.initSSLContext();

					SSLContext sslContext = (SSLContext) socketfactory.sslContext;
					SSLConnectionSocketFactory socketFactory = new SSLConnectionSocketFactory(sslContext);
					httpClientBuilder.setSSLSocketFactory(socketFactory);

				} catch (Throwable t) {
					throw new ConfigurationException(getLogPrefix()+"cannot create or initialize SocketFactory",t);
				}
			}

			credentials = new CredentialFactory(getAuthAlias(), getUserName(), getPassword());
			CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
			if (!StringUtils.isEmpty(credentials.getUsername())) {
				String uname;
				if (StringUtils.isNotEmpty(getAuthDomain())) {
					uname = getAuthDomain() + "\\" + credentials.getUsername();
				} else {
					uname = credentials.getUsername();
				}
				credentialsProvider.setCredentials(
					new AuthScope(AuthScope.ANY_HOST, AuthScope.ANY_PORT), 
					new UsernamePasswordCredentials(uname, credentials.getPassword())
				);
			}
			if (StringUtils.isNotEmpty(getProxyHost())) {
				HttpHost proxy = new HttpHost(getProxyHost(), getProxyPort());
				AuthScope scope = new AuthScope(proxy, getProxyRealm(), AuthScope.ANY_SCHEME);

				CredentialFactory pcf = new CredentialFactory(getProxyAuthAlias(), getProxyUserName(), getProxyPassword());

				if (StringUtils.isNotEmpty(pcf.getUsername())) {
					Credentials credentials = new UsernamePasswordCredentials(pcf.getUsername(), pcf.getPassword());
					credentialsProvider.setCredentials(scope, credentials);
				}
				log.trace("setting credentialProvider [" + credentialsProvider.toString() + "]");

				requestConfig.setProxy(proxy);
				requestConfig.setProxyPreferredAuthSchemes(Arrays.asList(AuthSchemes.BASIC));

				AuthCache authCache = httpClientContext.getAuthCache();
				if(authCache == null)
					authCache = new BasicAuthCache();

				authCache.put(proxy, new BasicScheme());
				httpClientContext.setAuthCache(authCache);
				httpClientBuilder.setProxy(proxy);
			}
			httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider);

		} catch (URISyntaxException e) {
			throw new ConfigurationException(getLogPrefix()+"cannot interpret uri ["+getUrl()+"]");
		}

		if (StringUtils.isNotEmpty(getStyleSheetName())) {
			try {
				URL stylesheetURL = ClassUtils.getResourceURL(classLoader, getStyleSheetName());
				if (stylesheetURL == null) {
					throw new ConfigurationException(getLogPrefix() + "cannot find stylesheet ["+getStyleSheetName()+"]");
				}
				transformerPool = TransformerPool.getInstance(stylesheetURL);
			} catch (IOException e) {
				throw new ConfigurationException(getLogPrefix() + "cannot retrieve ["+ getStyleSheetName() + "]", e);
			} catch (TransformerConfigurationException te) {
				throw new ConfigurationException(getLogPrefix() + "got error creating transformer from file [" + getStyleSheetName() + "]", te);
			}
		}

		httpClientBuilder.setDefaultRequestConfig(requestConfig.build());
	}

	public void open() throws SenderException {
		connectionManager = new PoolingHttpClientConnectionManager();
		connectionManager.setMaxTotal(getMaxConnections());

		log.debug(getLogPrefix()+"set up connectionManager, inactivity checking ["+connectionManager.getValidateAfterInactivity()+"]");
		boolean staleChecking = (connectionManager.getValidateAfterInactivity() >= 0);
		if (staleChecking != isStaleChecking()) {
			log.info(getLogPrefix()+"set up connectionManager, setting stale checking ["+isStaleChecking()+"]");
			connectionManager.setValidateAfterInactivity(getStaleTimeout());
		}

		httpClientBuilder.useSystemProperties();
		httpClientBuilder.disableAuthCaching();
		httpClientBuilder.setConnectionManager(connectionManager);

		if (transformerPool!=null) {
			try {
				transformerPool.open();
			} catch (Exception e) {
				throw new SenderException(getLogPrefix()+"cannot start TransformerPool", e);
			}
		}
	}

	public void close() {
		connectionManager.shutdown();
		connectionManager = null;

		if (transformerPool!=null) {
			transformerPool.close();
		}
	}

	public boolean isSynchronous() {
		return true;
	}

	protected boolean appendParameters(boolean parametersAppended, StringBuffer path, ParameterValueList parameters, Map<String, String> headersParamsMap) throws SenderException {
		if (parameters != null) {
			if (log.isDebugEnabled()) log.debug(getLogPrefix()+"appending ["+parameters.size()+"] parameters");
		}
		for(int i=0; i < parameters.size(); i++) {
			if (parametersToSkip.contains(paramList.get(i))) {
				if (log.isDebugEnabled()) log.debug(getLogPrefix()+"skipping ["+paramList.get(i)+"]");
				continue;
			}
			ParameterValue pv = parameters.getParameterValue(i);
			if (headersParamsMap.keySet().contains(pv.getDefinition().getName())) {
				headersParamsMap.put(pv.getDefinition().getName(), pv.asStringValue(""));
			} else {
				try {
					if (parametersAppended) {
						path.append("&");
					} else {
						path.append("?");
						parametersAppended = true;
					}

					String parameterToAppend = pv.getDefinition().getName() +"="+ URLEncoder.encode(pv.asStringValue(""), getCharSet());
					if (log.isDebugEnabled()) log.debug(getLogPrefix()+"appending parameter ["+parameterToAppend+"]");
					path.append(parameterToAppend);
				} catch (UnsupportedEncodingException e) {
					throw new SenderException(getLogPrefix()+"["+getCharSet()+"] encoding error. Failed to add parameter ["+pv.getDefinition().getName()+"]");
				}
			}
		}
		return parametersAppended;
	}

	public abstract HttpRequestBase getMethod(URIBuilder uri, String message, ParameterValueList parameters, Map<String, String> headersParamsMap) throws SenderException;

	public abstract String extractResult(HttpResponseHandler responseHandler, ParameterResolutionContext prc, HttpServletResponse response) throws SenderException, IOException;

	public String sendMessageWithTimeoutGuarded(String correlationID, String message, ParameterResolutionContext prc) throws SenderException, TimeOutException {
		ParameterValueList pvl = null;
		try {
			if (prc !=null && paramList !=null) {
				pvl=prc.getValues(paramList);
			}
		} catch (ParameterException e) {
			throw new SenderException(getLogPrefix()+"Sender ["+getName()+"] caught exception evaluating parameters",e);
		}

		URIBuilder uri;
		HttpRequestBase httpRequestBase;
		try {
			if (urlParameter != null) {
				String url = (String) pvl.getParameterValue(getUrlParam()).getValue();
				uri = getURI(url);
			} else {
				uri = staticUri;
			}

			httpTarget = new HttpHost(uri.getHost(), getPort(uri), uri.getScheme());

			Map<String, String> headersParamsMap = new HashMap<String, String>();
			if (headersParams != null) {
				StringTokenizer st = new StringTokenizer(headersParams, ",");
				while (st.hasMoreElements()) {
					headersParamsMap.put(st.nextToken(), null);
				}
			}

			httpRequestBase = getMethod(uri, message, pvl, headersParamsMap);
			if(httpRequestBase == null)
				throw new SenderException("invalid or unknown httpRequestBase");

			if (!"POST".equals(getMethodType()) && !"PUT".equals(getMethodType()) && !"REPORT".equals(getMethodType())) {
				httpClientBuilder.setRedirectStrategy(new DefaultRedirectStrategy() {
					@Override
					protected boolean isRedirectable(String method) {
						return true;
					}
				});
			}

			if (credentials != null && !StringUtils.isEmpty(credentials.getUsername())) {
				AuthCache authCache = httpClientContext.getAuthCache();
				if(authCache == null)
					authCache = new BasicAuthCache();

				authCache.put(httpTarget, new BasicScheme());
				httpClientContext.setAuthCache(authCache);
			}

			log.info(getLogPrefix()+"configured httpclient for host ["+uri.getHost()+"]");

		} catch (URISyntaxException e) {
			throw new SenderException(e);
		}

		CloseableHttpClient httpClient = httpClientBuilder.build();

		String result = null;
		int statusCode = -1;
		int count=getMaxExecuteRetries();
		String msg = null;
		while (count-- >= 0 && statusCode == -1) {
			try {
				log.debug(getLogPrefix()+"executing method [" + httpRequestBase.getRequestLine() + "]");
				HttpResponse httpResponse = httpClient.execute(httpTarget, httpRequestBase, httpClientContext);
				log.debug(getLogPrefix()+"executed method");

				HttpResponseHandler responseHandler = new HttpResponseHandler(httpResponse);
				StatusLine statusline = httpResponse.getStatusLine();
				statusCode = statusline.getStatusCode();

				if (statusCode != HttpServletResponse.SC_OK) {
					log.warn(getLogPrefix()+"status ["+statusline.toString()+"]");
				} else {
					log.debug(getLogPrefix()+"status ["+statusCode+"]");
				}

				HttpServletResponse response = (HttpServletResponse) prc.getSession().get(IPipeLineSession.HTTP_RESPONSE_KEY);

				result = extractResult(responseHandler, prc, response);

				log.debug(getLogPrefix()+"retrieved result ["+result+"]");
			} catch (ClientProtocolException e) {
				Throwable throwable = e.getCause();
				String cause = null;
				if (throwable != null) {
					cause = throwable.toString();
				}
				msg = e.getMessage();
				log.warn(getLogPrefix()+"httpException with message [" + msg + "] and cause [" + cause + "], executeRetries left [" + count + "]");
			} catch (IOException e) {
				httpRequestBase.abort();
				if (e instanceof SocketTimeoutException) {
					throw new TimeOutException(e);
				}
				throw new SenderException(e);
			} finally {
				//TODO: Make sure stream is closed properly
				// In case of storeResultAsStreamInSessionKey release connection
				// is done by ReleaseConnectionAfterReadInputStream.
				if (StringUtils.isEmpty(getStoreResultAsStreamInSessionKey())) {
					httpRequestBase.releaseConnection();
				}
			}
		}

		if (statusCode == -1){
			if (StringUtils.contains(msg.toUpperCase(), "TIMEOUTEXCEPTION")) {
				//java.net.SocketTimeoutException: Read timed out
				throw new TimeOutException("Failed to recover from timeout exception");
			}
			throw new SenderException("Failed to recover from exception");
		}

		if (isXhtml() && StringUtils.isNotEmpty(result)) {
			result = XmlUtils.skipDocTypeDeclaration(result.trim());
			if (result.startsWith("<html>") || result.startsWith("<html ")) {
				CleanerProperties props = new CleanerProperties();
				HtmlCleaner cleaner = new HtmlCleaner(props);
				TagNode tagNode = cleaner.clean(result);
				result = new SimpleXmlSerializer(props).getXmlAsString(tagNode);

				if (transformerPool != null) {
					log.debug(getLogPrefix() + " transforming result [" + result + "]");
					ParameterResolutionContext prc_xslt = new ParameterResolutionContext(result, null, true, true);
					try {
						result = transformerPool.transform(prc_xslt.getInputSource(), null);
					} catch (Exception e) {
						throw new SenderException("Exception on transforming input", e);
					}
				}
			}
		}

		return result;
	}

	public String sendMessage(String correlationID, String message) throws SenderException, TimeOutException {
		return sendMessage(correlationID, message, null);
	}


	public String getPhysicalDestinationName() {
		if (urlParameter!=null) {
			return "dynamic url";
		}
		return getUrl();
	}


	public String getUrl() {
		return url;
	}
	public void setUrl(String string) {
		url = string;
	}

	public String getUrlParam() {
		return urlParam;
	}
	public void setUrlParam(String urlParam) {
		this.urlParam = urlParam;
	}

	public String getMethodType() {
		return methodType.toUpperCase();
	}
	public void setMethodType(String string) {
		methodType = string;
	}

	public void setContentType(String string) {
		contentType = string;
	}
	public String getContentType() {
		return contentType;
	}

	public void setCharSet(String string) {
		charSet = string;
	}
	public String getCharSet() {
		return charSet;
	}

	public int getTimeout() {
		return timeout;
	}
	public void setTimeout(int i) {
		timeout = i;
	}

	public int retrieveTymeout() {
		// add 1 second to timeout to be sure HttpClient timeout is not
		// overruled
		return (getTimeout() / 1000) + 1;
	}

	public int getMaxConnections() {
		return maxConnections;
	}
	public void setMaxConnections(int i) {
		maxConnections = i;
	}

	public int getMaxExecuteRetries() {
		return maxExecuteRetries;
	}
	public void setMaxExecuteRetries(int i) {
		maxExecuteRetries = i;
	}


	public String getAuthAlias() {
		return authAlias;
	}
	public void setAuthAlias(String string) {
		authAlias = string;
	}

	public String getUserName() {
		return userName;
	}
	public void setUserName(String string) {
		userName = string;
	}

	public String getPassword() {
		return password;
	}
	public void setPassword(String string) {
		password = string;
	}

	public String getAuthDomain() {
		return authDomain;
	}
	public void setAuthDomain(String string) {
		authDomain = string;
	}

	public String getProxyHost() {
		return proxyHost;
	}
	public void setProxyHost(String string) {
		proxyHost = string;
	}

	public int getProxyPort() {
		return proxyPort;
	}
	public void setProxyPort(int i) {
		proxyPort = i;
	}

	public String getProxyAuthAlias() {
		return proxyAuthAlias;
	}
	public void setProxyAuthAlias(String string) {
		proxyAuthAlias = string;
	}

	public String getProxyUserName() {
		return proxyUserName;
	}
	public void setProxyUserName(String string) {
		proxyUserName = string;
	}

	public String getProxyPassword() {
		return proxyPassword;
	}
	public void setProxyPassword(String string) {
		proxyPassword = string;
	}

	public String getProxyRealm() {
		if(StringUtils.isEmpty(proxyRealm))
			return null;
		return proxyRealm;
	}
	public void setProxyRealm(String string) {
		proxyRealm = string;
	}


	
	public String getCertificate() {
		return certificate;
	}
	public void setCertificate(String string) {
		certificate = string;
	}

	public String getCertificateAuthAlias() {
		return certificateAuthAlias;
	}
	public void setTruststoreAuthAlias(String string) {
		truststoreAuthAlias = string;
	}

	public String getCertificatePassword() {
		return certificatePassword;
	}
	public void setCertificatePassword(String string) {
		certificatePassword = string;
	}

	public String getKeystoreType() {
		return keystoreType;
	}
	public void setKeystoreType(String string) {
		keystoreType = string;
	}

	public void setKeyManagerAlgorithm(String keyManagerAlgorithm) {
		this.keyManagerAlgorithm = keyManagerAlgorithm;
	}
	public String getKeyManagerAlgorithm() {
		return keyManagerAlgorithm;
	}

	
	public String getTruststore() {
		return truststore;
	}
	public void setTruststore(String string) {
		truststore = string;
	}

	public String getTruststoreAuthAlias() {
		return truststoreAuthAlias;
	}
	public void setCertificateAuthAlias(String string) {
		certificateAuthAlias = string;
	}

	public String getTruststorePassword() {
		return truststorePassword;
	}
	public void setTruststorePassword(String string) {
		truststorePassword = string;
	}

	public String getTruststoreType() {
		return truststoreType;
	}
	public void setTruststoreType(String string) {
		truststoreType = string;
	}

	public void setTrustManagerAlgorithm(String trustManagerAlgorithm) {
		this.trustManagerAlgorithm = trustManagerAlgorithm;
	}
	public String getTrustManagerAlgorithm() {
		return trustManagerAlgorithm;
	}


	public boolean isVerifyHostname() {
		return verifyHostname;
	}
	public void setVerifyHostname(boolean b) {
		verifyHostname = b;
	}

	public boolean isJdk13Compatibility() {
		return jdk13Compatibility;
	}
	public void setJdk13Compatibility(boolean b) {
		jdk13Compatibility = b;
	}

	public boolean isEncodeMessages() {
		return encodeMessages;
	}
	public void setEncodeMessages(boolean b) {
		encodeMessages = b;
	}

	public boolean isStaleChecking() {
		return staleChecking;
	}
	public void setStaleChecking(boolean b) {
		staleChecking = b;
	}
	
	public int getStaleTimeout() {
		return staleTimeout;
	}
	
	public void setStaleTimeout(int timeout) {
		staleTimeout = timeout;
	}

	public boolean isFollowRedirects() {
		return followRedirects;
	}
	public void setFollowRedirects(boolean b) {
		followRedirects = b;
	}

	public void setAllowSelfSignedCertificates(boolean allowSelfSignedCertificates) {
		this.allowSelfSignedCertificates = allowSelfSignedCertificates;
	}

	public boolean isAllowSelfSignedCertificates() {
		return allowSelfSignedCertificates;
	}

	public void setInputMessageParam(String inputMessageParam) {
		this.inputMessageParam = inputMessageParam;
	}
	public String getInputMessageParam() {
		return inputMessageParam;
	}

	public void setHeadersParams(String headersParams) {
		this.headersParams = headersParams;
	}
	public String getHeadersParams() {
		return headersParams;
	}

	public void setIgnoreRedirects(boolean b) {
		ignoreRedirects = b;
	}
	public boolean isIgnoreRedirects() {
		return ignoreRedirects;
	}

	public void setIgnoreCertificateExpiredException(boolean b) {
		ignoreCertificateExpiredException = b;
	}
	public boolean isIgnoreCertificateExpiredException() {
		return ignoreCertificateExpiredException;
	}

	public void setParamsInUrl(boolean b) {
		paramsInUrl = b;
	}
	public boolean isParamsInUrl() {
		return paramsInUrl;
	}

	public void setXhtml(boolean b) {
		xhtml = b;
	}
	public boolean isXhtml() {
		return xhtml;
	}

	public void setStyleSheetName(String stylesheetName){
		this.styleSheetName=stylesheetName;
	}
	public String getStyleSheetName() {
		return styleSheetName;
	}

	public void setMultipart(boolean b) {
		multipart = b;
	}
	public boolean isMultipart() {
		return multipart;
	}

	public void setMultipartResponse(boolean b) {
		multipartResponse = b;
	}
	public boolean isMultipartResponse() {
		return multipartResponse;
	}

	public void setStreamResultToServlet(boolean b) {
		streamResultToServlet = b;
	}
	public boolean isStreamResultToServlet() {
		return streamResultToServlet;
	}

	public void setBase64(boolean b) {
		base64 = b;
	}
	public boolean isBase64() {
		return base64;
	}

	public String getProtocol() {
		return protocol;
	}

	public void setProtocol(String string) {
		protocol = string;
	}

	public String getStreamResultToFileNameSessionKey() {
		return streamResultToFileNameSessionKey;
	}
	public void setStreamResultToFileNameSessionKey(String string) {
		streamResultToFileNameSessionKey = string;
	}

	public String getStoreResultAsStreamInSessionKey() {
		return storeResultAsStreamInSessionKey;
	}
	public void setStoreResultAsStreamInSessionKey(String storeResultAsStreamInSessionKey) {
		this.storeResultAsStreamInSessionKey = storeResultAsStreamInSessionKey;
	}

	public String getStoreResultAsByteArrayInSessionKey() {
		return storeResultAsByteArrayInSessionKey;
	}
	public void setStoreResultAsByteArrayInSessionKey(String storeResultAsByteArrayInSessionKey) {
		this.storeResultAsByteArrayInSessionKey = storeResultAsByteArrayInSessionKey;
	}

	public String getResultStatusCodeSessionKey() {
		return resultStatusCodeSessionKey;
	}
	public void setResultStatusCodeSessionKey(String resultStatusCodeSessionKey) {
		this.resultStatusCodeSessionKey = resultStatusCodeSessionKey;
	}

	public String getMultipartXmlSessionKey() {
		return multipartXmlSessionKey;
	}
	public void setMultipartXmlSessionKey(String multipartXmlSessionKey) {
		this.multipartXmlSessionKey = multipartXmlSessionKey;
	}
}