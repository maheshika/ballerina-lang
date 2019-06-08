/*
*  Copyright (c) 2017, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
*
*  WSO2 Inc. licenses this file to you under the Apache License,
*  Version 2.0 (the "License"); you may not use this file except
*  in compliance with the License.
*  You may obtain a copy of the License at
*
*    http://www.apache.org/licenses/LICENSE-2.0
*
*  Unless required by applicable law or agreed to in writing,
*  software distributed under the License is distributed on an
*  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
*  KIND, either express or implied.  See the License for the
*  specific language governing permissions and limitations
*  under the License.
*/
package org.ballerinalang.net.http;

import org.ballerinalang.connector.api.Annotation;
import org.ballerinalang.connector.api.BallerinaConnectorException;
import org.ballerinalang.connector.api.Resource;
import org.ballerinalang.connector.api.Service;
import org.ballerinalang.connector.api.Struct;
import org.ballerinalang.net.uri.DispatcherUtil;
import org.ballerinalang.net.uri.URITemplate;
import org.ballerinalang.net.uri.URITemplateException;
import org.ballerinalang.net.uri.parser.Literal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wso2.transport.http.netty.message.HttpCarbonMessage;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.ballerinalang.net.http.BHttpUtil.checkConfigAnnotationAvailability;
import static org.ballerinalang.net.http.BHttpUtil.sanitizeBasePath;
import static org.ballerinalang.net.http.HttpConstants.ANN_NAME_INTERRUPTIBLE;
import static org.ballerinalang.net.http.HttpConstants.AUTO;
import static org.ballerinalang.net.http.HttpConstants.DEFAULT_BASE_PATH;
import static org.ballerinalang.net.http.HttpConstants.DEFAULT_HOST;
import static org.ballerinalang.net.http.HttpConstants.DOLLAR;
import static org.ballerinalang.net.http.HttpConstants.HTTP_PACKAGE_PATH;
import static org.ballerinalang.net.http.HttpConstants.PACKAGE_BALLERINA_BUILTIN;

/**
 * {@code HttpService} This is the http wrapper for the {@code Service} implementation.
 *
 * @since 0.94
 */
public class BHttpService implements Cloneable {

    private static final Logger log = LoggerFactory.getLogger(HttpService.class);

    protected static final String BASE_PATH_FIELD = "basePath";
    private static final String COMPRESSION_FIELD = "compression";
    private static final String CORS_FIELD = "cors";
    private static final String VERSIONING_FIELD = "versioning";
    private static final String HOST_FIELD = "host";
    protected static final String WEBSOCKET_UPGRADE_FIELD = "webSocketUpgrade";

    private Service balService;
    private List<BHttpResource> resources;
    private List<BHttpResource> upgradeToWebSocketResources;
    private List<String> allAllowedMethods;
    private String basePath;
    private CorsHeaders corsHeaders;
    private URITemplate<BHttpResource, HttpCarbonMessage> uriTemplate;
    private boolean keepAlive = true; //default behavior
    private String compression = AUTO; //default behavior
    private String hostName;
    private boolean interruptible;

    protected BHttpService(Service service) {
        this.balService = service;
    }

    public Object clone() throws CloneNotSupportedException {
        return super.clone();
    }

    public boolean isKeepAlive() {
        return keepAlive;
    }

    public void setKeepAlive(boolean keepAlive) {
        this.keepAlive = keepAlive;
    }

    public void setCompression(String compression) {
        this.compression = compression;
    }

    public String getName() {
        return balService.getName();
    }

    public String getPackage() {
        return balService.getPackage();
    }

    public Service getBalService() {
        return balService;
    }

    public List<BHttpResource> getResources() {
        return resources;
    }

    public void setResources(List<BHttpResource> resources) {
        this.resources = resources;
    }

    public List<String> getAllAllowedMethods() {
        return allAllowedMethods;
    }

    public void setAllAllowedMethods(List<String> allAllowMethods) {
        this.allAllowedMethods = allAllowMethods;
    }

    public void setHostName(String hostName) {
        this.hostName = hostName;
    }

    public String getHostName() {
        return hostName;
    }

    public boolean isInterruptible() {
        return interruptible;
    }

    public void setInterruptible(boolean interruptible) {
        this.interruptible = interruptible;
    }

    public String getBasePath() {
        return basePath;
    }

    public void setBasePath(String basePath) {
        if (basePath == null || basePath.trim().isEmpty()) {
            this.basePath = DEFAULT_BASE_PATH.concat(this.getName().startsWith(DOLLAR) ? "" : this.getName());
        } else {
            String sanitizedPath = sanitizeBasePath(basePath);
            this.basePath = urlDecode(sanitizedPath);
        }
    }

    public CorsHeaders getCorsHeaders() {
        return corsHeaders;
    }

    public void setCorsHeaders(CorsHeaders corsHeaders) {
        this.corsHeaders = corsHeaders;
        if (this.corsHeaders == null || !this.corsHeaders.isAvailable()) {
            return;
        }
        if (this.corsHeaders.getAllowOrigins() == null) {
            this.corsHeaders.setAllowOrigins(Stream.of("*").collect(Collectors.toList()));
        }
        if (this.corsHeaders.getAllowMethods() == null) {
            this.corsHeaders.setAllowMethods(Stream.of("*").collect(Collectors.toList()));
        }
    }

    public List<BHttpResource> getUpgradeToWebSocketResources() {
        return upgradeToWebSocketResources;
    }

    public void setUpgradeToWebSocketResources(List<BHttpResource> upgradeToWebSocketResources) {
        this.upgradeToWebSocketResources = upgradeToWebSocketResources;
    }

    public URITemplate<BHttpResource, HttpCarbonMessage> getUriTemplate() throws URITemplateException {
        if (uriTemplate == null) {
            uriTemplate = new URITemplate<>(new Literal<>(new BHttpResourceDataElement(), "/"));
        }
        return uriTemplate;
    }

    public static List<BHttpService> buildHttpService(Service service) {
        List<BHttpService> serviceList = new ArrayList<>();
        List<String> basePathList = new ArrayList<>();
        BHttpService httpService = new BHttpService(service);
        Annotation serviceConfigAnnotation = getHttpServiceConfigAnnotation(service);
        httpService.setInterruptible(hasInterruptibleAnnotation(service));

        if (checkConfigAnnotationAvailability(serviceConfigAnnotation)) {
            Struct serviceConfig = serviceConfigAnnotation.getValue();

            httpService.setCompression(serviceConfig.getRefField(COMPRESSION_FIELD).getStringValue());
            httpService.setCorsHeaders(CorsHeaders.buildCorsHeaders(serviceConfig.getStructField(CORS_FIELD)));
            httpService.setHostName(serviceConfig.getStringField(HOST_FIELD).trim());

            String basePath = serviceConfig.getStringField(BASE_PATH_FIELD);
            if (basePath.contains(HttpConstants.VERSION)) {
                prepareBasePathList(serviceConfig.getStructField(VERSIONING_FIELD),
                                    serviceConfig.getStringField(BASE_PATH_FIELD), basePathList,
                                    httpService.getBalService().getPackageVersion());
            } else {
                basePathList.add(basePath);
            }
        } else {
            log.debug("serviceConfig not specified in the Service instance, using default base path");
            //service name cannot start with / hence concat
            String basePath = httpService.getName().startsWith(DOLLAR) ? DEFAULT_BASE_PATH :
                    DEFAULT_BASE_PATH.concat(httpService.getName());
            basePathList.add(basePath);
            httpService.setHostName(DEFAULT_HOST);
        }

        List<BHttpResource> httpResources = new ArrayList<>();
        List<BHttpResource> upgradeToWebSocketResources = new ArrayList<>();
        for (Resource resource : httpService.getBalService().getResources()) {
            Annotation resourceConfigAnnotation =
                    BHttpUtil.getResourceConfigAnnotation(resource, HttpConstants.HTTP_PACKAGE_PATH);
            if (checkConfigAnnotationAvailability(resourceConfigAnnotation)
                    && resourceConfigAnnotation.getValue().getStructField(WEBSOCKET_UPGRADE_FIELD) != null) {
                BHttpResource upgradeResource = BHttpResource.buildHttpResource(resource, httpService);
                upgradeToWebSocketResources.add(upgradeResource);
            } else {
                BHttpResource httpResource = BHttpResource.buildHttpResource(resource, httpService);
                try {
                    httpService.getUriTemplate().parse(httpResource.getPath(), httpResource,
                                                       new BHttpResourceElementFactory());
                } catch (URITemplateException | UnsupportedEncodingException e) {
                    throw new BallerinaConnectorException(e.getMessage());
                }
                httpResources.add(httpResource);
            }
        }
        httpService.setResources(httpResources);
        httpService.setUpgradeToWebSocketResources(upgradeToWebSocketResources);
        httpService.setAllAllowedMethods(DispatcherUtil.getAllResourceMethods(httpService));

        if (basePathList.size() == 1) {
            httpService.setBasePath(basePathList.get(0));
            serviceList.add(httpService);
            return serviceList;
        }

        for (String basePath : basePathList) {
            BHttpService tempHttpService;
            try {
                tempHttpService = (BHttpService) httpService.clone();
            } catch (CloneNotSupportedException e) {
                throw new BallerinaConnectorException("Service registration failed");
            }
            tempHttpService.setBasePath(basePath);
            serviceList.add(tempHttpService);
        }
        return serviceList;
    }

    private static void prepareBasePathList(Struct versioningConfig, String basePath, List<String> basePathList,
                                            String packageVersion) {
        String patternAnnotValue = HttpConstants.DEFAULT_VERSION;
        Boolean allowNoVersionAnnotValue = false;
        Boolean matchMajorVersionAnnotValue = false;
        if (versioningConfig != null) {
            patternAnnotValue = versioningConfig.getStringField(HttpConstants.ANN_CONFIG_ATTR_PATTERN);
            allowNoVersionAnnotValue = versioningConfig.getBooleanField(HttpConstants.ANN_CONFIG_ATTR_ALLOW_NO_VERSION);
            matchMajorVersionAnnotValue = versioningConfig.getBooleanField(
                    HttpConstants.ANN_CONFIG_ATTR_MATCH_MAJOR_VERSION);
        }
        patternAnnotValue = patternAnnotValue.toLowerCase();
        basePathList.add(replaceServiceVersion(basePath, packageVersion, patternAnnotValue));

        if (allowNoVersionAnnotValue) {
            basePathList.add(basePath.replace(HttpConstants.VERSION, "").replace("//", "/"));
        }
        if (matchMajorVersionAnnotValue) {
            String patternWithMajor = patternAnnotValue.replace(HttpConstants.MINOR_VERSION, "");
            patternWithMajor = patternWithMajor.endsWith(".") ?
                    patternWithMajor.substring(0, patternWithMajor.length() - 1) : patternWithMajor;
            basePathList.add(replaceServiceVersion(basePath, packageVersion, patternWithMajor));
        }
    }

    private static String replaceServiceVersion(String basePath, String version, String pattern) {
        pattern = pattern.toLowerCase();
        String[] versionElements = version.split("\\.");
        String majorVersion = versionElements[0];
        String minorVersion = versionElements.length > 1 ? versionElements[1] : "";

        if (pattern.contains(HttpConstants.MAJOR_VERSION) || pattern.contains(HttpConstants.MINOR_VERSION)) {
            String patternReplaced = pattern.replace(HttpConstants.MAJOR_VERSION, majorVersion);
            String result = patternReplaced.replace(HttpConstants.MINOR_VERSION, minorVersion);

            return basePath.replace(HttpConstants.VERSION, result);
        }
        throw new BallerinaConnectorException("Invalid versioning pattern: expect \"" + HttpConstants.MAJOR_VERSION +
                                              "," + HttpConstants.MINOR_VERSION + "\" elements");
    }

    private static Annotation getHttpServiceConfigAnnotation(Service service) {
        return getServiceConfigAnnotation(service, HTTP_PACKAGE_PATH, HttpConstants.ANN_NAME_HTTP_SERVICE_CONFIG);
    }

    protected static Annotation getServiceConfigAnnotation(Service service, String packagePath, String annotationName) {
        List<Annotation> annotationList = service.getAnnotationList(packagePath, annotationName);

        if (annotationList == null || annotationList.isEmpty()) {
            return null;
        }
        return annotationList.get(0);
    }

    private static boolean hasInterruptibleAnnotation(Service service) {
        List<Annotation> annotationList = service.getAnnotationList(PACKAGE_BALLERINA_BUILTIN, ANN_NAME_INTERRUPTIBLE);
        return annotationList != null && !annotationList.isEmpty();
    }

    private String urlDecode(String basePath) {
        try {
            basePath = URLDecoder.decode(basePath, StandardCharsets.UTF_8.name());
        } catch (UnsupportedEncodingException e) {
            throw new BallerinaConnectorException(e.getMessage());
        }
        return basePath;
    }
}