package cz.cesnet.shongo.controller.authorization;


import cz.cesnet.shongo.CommonReportSet;
import cz.cesnet.shongo.api.UserInformation;
import cz.cesnet.shongo.controller.ControllerConfiguration;
import cz.cesnet.shongo.controller.ControllerReportSet;
import cz.cesnet.shongo.controller.api.Group;
import cz.cesnet.shongo.controller.api.SecurityToken;
import cz.cesnet.shongo.report.ReportRuntimeException;
import cz.cesnet.shongo.ssl.ConfiguredSSLContext;
import org.apache.commons.lang.StringUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.StatusLine;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.*;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.StringEntity;
import org.apache.http.util.EntityUtils;
import org.apache.ws.commons.util.Base64;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.persistence.EntityManagerFactory;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.*;

/**
 * Provides methods for performing authentication and authorization.
 *
 * @author Martin Srom <martin.srom@cesnet.cz>
 */
public class ServerAuthorization extends Authorization
{
    private static Logger logger = LoggerFactory.getLogger(ServerAuthorization.class);

    /**
     * Authentication service path in auth-server.
     */
    private static final String AUTHENTICATION_SERVICE_PATH = "/authn/oic";

    /**
     * User web service path in auth-server.
     */
    private static final String USER_SERVICE_PATH = "/perun/users";

    /**
     * User principal web service path in auth-server.
     */
    private static final String PRINCIPAL_SERVICE_PATH = "/perun/principal";

    /**
     * Groups web service path in auth-server.
     */
    private static final String GROUP_SERVICE_PATH = "/perun/groups";

    /**
     * Access token which won't be verified and can be used for testing purposes.
     */
    private String rootAccessToken;

    /**
     * URL to authorization server.
     */
    private String authorizationServer;

    /**
     * Authorization header for requests.
     */
    private String requestAuthorizationHeader;

    /**
     * {@link HttpClient} for performing auth-server requests.
     */
    private HttpClient httpClient;

    /**
     * @see ObjectMapper
     */
    private ObjectMapper jsonMapper = new ObjectMapper();

    /**
     * Constructor.
     *
     * @param configuration        to load authorization configuration from
     * @param entityManagerFactory
     */
    private ServerAuthorization(ControllerConfiguration configuration, EntityManagerFactory entityManagerFactory)
    {
        super(configuration, entityManagerFactory);

        // Debug HTTP requests
        //System.setProperty("org.apache.commons.logging.Log", "org.apache.commons.logging.impl.SimpleLog");
        //System.setProperty("org.apache.commons.logging.simplelog.showdatetime", "true");
        //System.setProperty("org.apache.commons.logging.simplelog.log.httpclient.wire", "debug");
        //System.setProperty("org.apache.commons.logging.simplelog.log.org.apache.commons.httpclient", "debug");

        // Authorization server
        authorizationServer = configuration.getString(ControllerConfiguration.SECURITY_SERVER);
        if (authorizationServer == null) {
            throw new IllegalStateException("Authorization server is not set in the configuration.");
        }
        logger.info("Using authorization server '{}'.", authorizationServer);

        // Authorization header

        String clientId = configuration.getString(ControllerConfiguration.SECURITY_CLIENT_ID);
        String clientSecret = configuration.getString(ControllerConfiguration.SECURITY_CLIENT_SECRET);
        String clientAuthorization = clientId + ":" + clientSecret;
        byte[] bytes = clientAuthorization.getBytes();
        requestAuthorizationHeader = "Basic " + Base64.encode(bytes, 0, bytes.length, 0, "");

        // Root access token
        rootAccessToken = configuration.getString(ControllerConfiguration.SECURITY_ROOT_ACCESS_TOKEN);
        administratorAccessTokens.add(rootAccessToken);

        // Create http client
        httpClient = ConfiguredSSLContext.getInstance().createHttpClient();

        initialize();
    }

    /**
     * @param rootAccessToken sets the {@link #rootAccessToken}
     */
    public void setRootAccessToken(String rootAccessToken)
    {
        this.rootAccessToken = rootAccessToken;
    }

    /**
     * @return url to authentication service in auth-server
     */
    private String getAuthenticationUrl()
    {
        return authorizationServer + AUTHENTICATION_SERVICE_PATH;
    }

    @Override
    protected UserInformation onValidate(SecurityToken securityToken)
    {
        // Always allow testing access token
        if (rootAccessToken != null && securityToken.getAccessToken().equals(rootAccessToken)) {
            logger.trace("Access token '{}' is valid for testing.", securityToken.getAccessToken());
            return ROOT_USER_DATA.getUserInformation();
        }
        return super.onValidate(securityToken);
    }

    @Override
    protected UserData onGetUserDataByAccessToken(String accessToken)
            throws ControllerReportSet.UserNotExistsException
    {
        // Testing security token represents root user
        if (rootAccessToken != null && accessToken.equals(rootAccessToken)) {
            return ROOT_USER_DATA;
        }

        Exception errorException = null;
        String errorReason = null;
        try {
            URIBuilder uriBuilder = new URIBuilder(getAuthenticationUrl() + "/userinfo");
            uriBuilder.setParameter("schema", "openid");
            HttpGet httpGet = new HttpGet(uriBuilder.build());
            httpGet.setHeader("Authorization", "Bearer " + accessToken);
            HttpResponse response = httpClient.execute(httpGet);
            if (response.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
                JsonNode jsonNode = readJson(response.getEntity());
                if (jsonNode == null) {
                    throw new ControllerReportSet.UserNotExistsException(accessToken);
                }
                return createUserDataFromWebService(jsonNode);
            }
            else {
                JsonNode jsonNode = readJson(response.getEntity());
                if (jsonNode != null) {
                    errorReason = String.format("%s, %s",
                            jsonNode.get("error").getTextValue(), jsonNode.get("error_description").getTextValue());
                }
                else {
                    errorReason = "unknown";
                }
            }
        }
        catch (ControllerReportSet.UserNotExistsException exception) {
            throw exception;
        }
        catch (Exception exception) {
            errorException = exception;
        }
        // Handle error
        String errorMessage = String.format("Retrieving user information by access token '%s' failed.", accessToken);
        if (errorReason != null) {
            errorMessage += " " + errorReason;
        }
        throw new RuntimeException(errorMessage, errorException);
    }

    @Override
    protected UserData onGetUserDataByUserId(final String userId)
            throws ControllerReportSet.UserNotExistsException
    {
        return performGetRequest(authorizationServer + USER_SERVICE_PATH + "/" + userId,
                "Retrieving user information by user-id '" + userId + "' failed",
                new RequestHandler<UserData>()
                {
                    @Override
                    public UserData success(JsonNode data)
                    {
                        if (data == null) {
                            throw new ControllerReportSet.UserNotExistsException(userId);
                        }
                        return createUserDataFromWebService(data);
                    }

                    @Override
                    public void error(StatusLine statusLine, String detail)
                    {
                        int statusCode = statusLine.getStatusCode();
                        if (statusCode == HttpStatus.SC_NOT_FOUND || detail.contains("UserNotExistsException")) {
                            throw new ControllerReportSet.UserNotExistsException(userId);
                        }
                    }
                });
    }

    @Override
    protected String onGetUserIdByPrincipalName(final String principalName)
            throws ControllerReportSet.UserNotExistsException
    {
        return performGetRequest(authorizationServer + PRINCIPAL_SERVICE_PATH + "/" + principalName,
                "Retrieving user-id by principal name '" + principalName + "' failed",
                new RequestHandler<String>()
                {
                    @Override
                    public String success(JsonNode data)
                    {
                        if (data == null) {
                            throw new ControllerReportSet.UserNotExistsException(principalName);
                        }
                        if (!data.has("id")) {
                            throw new IllegalStateException("Principal service must return identifier.");
                        }
                        JsonNode dataId = data.get("id");
                        if (dataId.isNull()) {
                            throw new IllegalStateException("Principal service must return not null identifier.");
                        }
                        return dataId.asText();
                    }

                    @Override
                    public void error(StatusLine statusLine, String detail)
                    {
                        int statusCode = statusLine.getStatusCode();
                        if (statusCode == HttpStatus.SC_NOT_FOUND) {
                            throw new ControllerReportSet.UserNotExistsException(principalName);
                        }
                    }
                });
    }

    @Override
    protected Collection<UserData> onListUserData(Set<String> filterUserIds, String search)
    {
        String listUsersUrlQuery = "";
        if (filterUserIds != null && filterUserIds.size() > 0) {
            try {
                String userIds = StringUtils.join(filterUserIds, ",");
                listUsersUrlQuery += (listUsersUrlQuery.isEmpty() ? "?" : "&");
                listUsersUrlQuery += "filter_user_id=" + URLEncoder.encode(userIds, "UTF-8");
            }
            catch (UnsupportedEncodingException exception) {
                throw new CommonReportSet.UnknownErrorException(exception, "Url encoding failed");
            }
        }
        if (search != null) {
            try {
                listUsersUrlQuery += (listUsersUrlQuery.isEmpty() ? "?" : "&");
                listUsersUrlQuery += "search=" + URLEncoder.encode(search, "UTF-8");
            }
            catch (UnsupportedEncodingException exception) {
                throw new CommonReportSet.UnknownErrorException(exception, "Url encoding failed");
            }
        }
        String listUsersUrl = authorizationServer + USER_SERVICE_PATH + listUsersUrlQuery;
        return performGetRequest(listUsersUrl, "Retrieving user information failed",
                new RequestHandler<Collection<UserData>>()
                {
                    @Override
                    public Collection<UserData> success(JsonNode data)
                    {
                        List<UserData> userDataList = new LinkedList<UserData>();
                        if (data != null) {
                            for (JsonNode childJsonNode : data.get("_embedded").get("users")) {
                                UserData userData = createUserDataFromWebService(childJsonNode);
                                userDataList.add(userData);
                            }
                        }
                        return userDataList;
                    }

                    @Override
                    public void error(StatusLine statusLine, String detail)
                    {
                        if (detail.contains("UserNotExistsException")) {
                            String userId;
                            int start = detail.lastIndexOf("id=");
                            int end = -1;
                            if (start != -1) {
                                start += 3;
                                end = detail.indexOf(" ", start);
                            }
                            if (start != -1 && end != -1) {
                                userId = detail.substring(start, end);
                            }
                            else {
                                userId = "<not-parsed>";
                                logger.warn("User-id cannot be parsed from '{}'.", detail);
                            }
                            throw new ControllerReportSet.UserNotExistsException(userId);

                        }
                    }
                });
    }

    @Override
    protected Group onGetGroup(final String groupId) throws ControllerReportSet.GroupNotExistsException
    {
        return performGetRequest(authorizationServer + GROUP_SERVICE_PATH + "/" + groupId,
                "Retrieving group " + groupId + " failed",
                new RequestHandler<Group>()
                {
                    @Override
                    public Group success(JsonNode data)
                    {
                        Group group = new Group();
                        group.setId(data.get("id").asText());
                        if (data.has("parent_group_id")) {
                            JsonNode parentId = data.get("parent_group_id");
                            if (!parentId.isNull()) {
                                group.setParentId(parentId.asText());
                            }
                        }
                        group.setName(data.get("name").asText());
                        if (data.has("description")) {
                            JsonNode description = data.get("description");
                            if (!description.isNull()) {
                                group.setDescription(description.asText());
                            }
                        }
                        return group;
                    }

                    @Override
                    public void error(StatusLine statusLine, String detail)
                    {
                        int statusCode = statusLine.getStatusCode();
                        if (statusCode == HttpStatus.SC_NOT_FOUND || detail.contains("GroupNotExistsException")) {
                            throw new ControllerReportSet.GroupNotExistsException(groupId);
                        }
                    }
                });
    }

    @Override
    public List<Group> onListGroups(Set<String> filterGroupIds)
    {
        String listGroupsUrlQuery = "";
        if (filterGroupIds != null && filterGroupIds.size() > 0) {
            try {
                String userIds = StringUtils.join(filterGroupIds, ",");
                listGroupsUrlQuery += (listGroupsUrlQuery.isEmpty() ? "?" : "&");
                listGroupsUrlQuery += "filter_group_id=" + URLEncoder.encode(userIds, "UTF-8");
            }
            catch (UnsupportedEncodingException exception) {
                throw new CommonReportSet.UnknownErrorException(exception, "Url encoding failed");
            }
        }
        String listGroupsUrl = authorizationServer + GROUP_SERVICE_PATH + listGroupsUrlQuery;
        return performGetRequest(listGroupsUrl, "Retrieving groups failed", new RequestHandler<List<Group>>()
        {
            @Override
            public List<Group> success(JsonNode data)
            {
                List<Group> groups = new LinkedList<Group>();
                if (data != null) {
                    Iterator<JsonNode> groupIterator = data.get("_embedded").get("groups").getElements();
                    while (groupIterator.hasNext()) {
                        JsonNode groupNode = groupIterator.next();
                        Group group = new Group();
                        group.setId(groupNode.get("id").asText());
                        if (groupNode.has("parent_group_id")) {
                            JsonNode parentId = groupNode.get("parent_group_id");
                            if (!parentId.isNull()) {
                                group.setParentId(parentId.asText());
                            }
                        }
                        group.setName(groupNode.get("name").asText());
                        if (groupNode.has("description")) {
                            JsonNode description = groupNode.get("description");
                            if (!description.isNull()) {
                                group.setDescription(description.asText());
                            }
                        }
                        groups.add(group);
                    }
                }
                return groups;
            }

            @Override
            public void error(StatusLine statusLine, String detail)
            {
                if (detail.contains("GroupNotExistsException")) {
                    String userId;
                    int start = detail.lastIndexOf("id=");
                    int end = -1;
                    if (start != -1) {
                        start += 3;
                        end = detail.indexOf(" ", start);
                    }
                    if (start != -1 && end != -1) {
                        userId = detail.substring(start, end);
                    }
                    else {
                        userId = "<not-parsed>";
                        logger.warn("Group-id cannot be parsed from '{}'.", detail);
                    }
                    throw new ControllerReportSet.GroupNotExistsException(userId);
                }
            }
        });
    }

    @Override
    public Set<String> onListGroupUserIds(final String groupId)
    {
        return performGetRequest(authorizationServer + GROUP_SERVICE_PATH + "/" + groupId + "/users",
                "Retrieving user-ids in group " + groupId + " failed",
                new RequestHandler<Set<String>>()
                {
                    @Override
                    public Set<String> success(JsonNode data)
                    {
                        Set<String> userIds = new HashSet<String>();
                        if (data != null) {
                            Iterator<JsonNode> userIterator = data.get("_embedded").get("users").getElements();
                            while (userIterator.hasNext()) {
                                JsonNode userNode = userIterator.next();
                                if (!userNode.has("id")) {
                                    throw new IllegalStateException("User must have identifier.");
                                }
                                userIds.add(userNode.get("id").asText());
                            }
                        }
                        return userIds;
                    }
                });
    }

    @Override
    protected Set<String> onListUserGroupIds(String userId)
    {
        return performGetRequest(authorizationServer + USER_SERVICE_PATH  + "/" + userId + "/groups",
                "Retrieving group-ids for user " + userId + " failed",
                new RequestHandler<Set<String>>()
                {
                    @Override
                    public Set<String> success(JsonNode data)
                    {
                        Set<String> groupIds = new HashSet<String>();
                        if (data != null) {
                            Iterator<JsonNode> userIterator = data.get("_embedded").get("groups").getElements();
                            while (userIterator.hasNext()) {
                                JsonNode groupNode = userIterator.next();
                                if (!groupNode.has("id")) {
                                    throw new IllegalStateException("Group must have identifier.");
                                }
                                groupIds.add(groupNode.get("id").asText());
                            }
                        }
                        return groupIds;
                    }
                });
    }

    @Override
    public String onCreateGroup(final Group group)
    {
        ObjectNode content = jsonMapper.createObjectNode();
        content.put("name", group.getName());
        content.put("description", group.getDescription());
        if (group.getParentId() != null) {
            content.put("parent_group_id", group.getName());
        }
        return performPostRequest(authorizationServer + GROUP_SERVICE_PATH, content, "Creating group failed",
                new RequestHandler<String>()
                {
                    @Override
                    public String success(JsonNode data)
                    {
                        return data.get("id").asText();
                    }

                    @Override
                    public void error(StatusLine statusLine, String detail)
                    {
                        if (detail.contains("GroupExistsException")) {
                            throw new ControllerReportSet.GroupAlreadyExistsException(group.getName());
                        }
                    }
                });
    }

    @Override
    public void onDeleteGroup(final String groupId)
    {
        performDeleteRequest(authorizationServer + GROUP_SERVICE_PATH + "/" + groupId,
                "Deleting group " + groupId + " failed",
                new RequestHandler<Object>()
                {
                    @Override
                    public void error(StatusLine statusLine, String detail)
                    {
                        if (detail.contains("GroupNotExistsException")) {
                            throw new ControllerReportSet.GroupNotExistsException(groupId);
                        }
                    }
                });
    }

    @Override
    public void onAddGroupUser(final String groupId, final String userId)
    {
        performPutRequest(authorizationServer + GROUP_SERVICE_PATH + "/" + groupId + "/users/" + userId,
                "Adding user " + userId + " to group " + groupId + " failed",
                new RequestHandler<Object>()
                {
                    @Override
                    public void error(StatusLine statusLine, String detail)
                    {
                        int statusCode = statusLine.getStatusCode();
                        if (statusCode == HttpStatus.SC_NOT_FOUND) {
                            if (detail.contains("User")) {
                                throw new ControllerReportSet.UserNotExistsException(userId);
                            }
                        }
                        if (detail.contains("AlreadyMemberException")) {
                            throw new ControllerReportSet.UserAlreadyInGroupException(groupId, userId);
                        }
                        else if (detail.contains("GroupNotExistsException")) {
                            throw new ControllerReportSet.GroupNotExistsException(groupId);
                        }
                    }
                });
    }

    @Override
    public void onRemoveGroupUser(final String groupId, final String userId)
    {
        performDeleteRequest(authorizationServer + GROUP_SERVICE_PATH + "/" + groupId + "/users/" + userId,
                "Removing user " + userId + " from group " + groupId + " failed",
                new RequestHandler<Object>()
                {
                    @Override
                    public void error(StatusLine statusLine, String detail)
                    {
                        int statusCode = statusLine.getStatusCode();
                        if (statusCode == HttpStatus.SC_NOT_FOUND) {
                            if (detail.contains("User")) {
                                throw new ControllerReportSet.UserNotExistsException(userId);
                            }
                        }
                        if (detail.contains("NotGroupMemberException")) {
                            throw new ControllerReportSet.UserNotInGroupException(groupId, userId);
                        }
                        else if (detail.contains("GroupNotExistsException")) {
                            throw new ControllerReportSet.GroupNotExistsException(groupId);
                        }
                    }
                });
    }

    /**
     * @see #performRequest
     */
    private <T> T performGetRequest(String url, String description, RequestHandler<T> requestHandler)
    {
        HttpGet httpGet = new HttpGet(url);
        return performRequest(httpGet, description, requestHandler);
    }

    /**
     * @see #performRequest
     */
    private <T> T performPostRequest(String url, JsonNode content, String description, RequestHandler<T> requestHandler)
    {
        StringEntity entity;
        try {
            String json = content.toString();
            entity = new StringEntity(json);
        }
        catch (UnsupportedEncodingException exception) {
            throw new CommonReportSet.UnknownErrorException(exception, "Entity encoding failed");
        }
        HttpPost httpPost = new HttpPost(url);
        httpPost.setEntity(entity);
        httpPost.setHeader("Content-Type", "application/json");
        return performRequest(httpPost, description, requestHandler);
    }

    /**
     * @see #performRequest
     */
    private void performPutRequest(String url, String description, RequestHandler requestHandler)
    {
        HttpPut httpPut = new HttpPut(url);
        performRequest(httpPut, description, requestHandler);
    }

    /**
     * @see #performRequest
     */
    private void performDeleteRequest(String url, String description, RequestHandler requestHandler)
    {
        HttpDelete httpDelete = new HttpDelete(url);
        performRequest(httpDelete, description, requestHandler);
    }

    /**
     * Perform given {@code httpRequest}.
     *
     * @param httpRequest    to be performed
     * @param description    for error reporting
     * @param requestHandler to handle response or error
     * @return result from given {@code requestHandler}
     */
    private <T> T performRequest(HttpRequestBase httpRequest, String description, RequestHandler<T> requestHandler)
    {
        try {
            httpRequest.addHeader("Authorization", requestAuthorizationHeader);
            httpRequest.setHeader("Accept", "application/hal+json");
            HttpResponse response = httpClient.execute(httpRequest);
            StatusLine statusLine = response.getStatusLine();
            int statusCode = statusLine.getStatusCode();
            if (statusCode == HttpStatus.SC_NO_CONTENT) {
                return null;
            }
            else if (statusCode >= HttpStatus.SC_OK && statusCode <= HttpStatus.SC_ACCEPTED) {
                JsonNode data = readJson(response.getEntity());
                return requestHandler.success(data);
            }
            else {
                String content = readContent(response.getEntity());
                String detail = "";
                if (statusCode != HttpStatus.SC_BAD_REQUEST) {
                    try {
                        JsonNode jsonNode = jsonMapper.readTree(content);
                        if (jsonNode.has("detail")) {
                            JsonNode detailNode = jsonNode.get("description");
                            if (!detailNode.isNull()) {
                                detail = detailNode.asText();
                            }
                        }
                    }
                    catch (Exception exception) {
                        logger.warn("Cannot parse json: {}", content);
                        detail = content;
                    }
                }
                requestHandler.error(statusLine, detail);
                String error = description + ": " + statusLine.toString();
                if (detail != null) {
                    error += ": " + detail;
                }
                throw new CommonReportSet.UnknownErrorException(error);
            }
        }
        catch (ReportRuntimeException exception) {
            throw exception;
        }
        catch (Exception exception) {
            throw new CommonReportSet.UnknownErrorException(exception, description + ".");
        }
    }

    /**
     * @param httpEntity to be read
     * @return {@link JsonNode} from given {@code httpEntity}
     */
    private JsonNode readJson(HttpEntity httpEntity)
    {
        if (httpEntity.getContentLength() == 0) {
            return null;
        }
        try {
            InputStream inputStream = httpEntity.getContent();
            try {
                int ava = inputStream.available();
                return jsonMapper.readTree(inputStream);
            }
            finally {
                inputStream.close();
            }
        }
        catch (EOFException exception) {
            throw new RuntimeException("JSON is empty.", exception);
        }
        catch (IOException exception) {
            throw new RuntimeException("Reading JSON failed.", exception);
        }
    }

    /**
     * Read all content from given {@code httpEntity}.
     *
     * @param httpEntity to be read
     */
    private String readContent(HttpEntity httpEntity)
    {
        if (httpEntity != null) {
            try {
                return EntityUtils.toString(httpEntity);
            }
            catch (IOException exception) {
                throw new RuntimeException("Reading content failed.", exception);
            }
        }
        return null;
    }

    /**
     * @param httpResponse to be handled
     * @throws RuntimeException is always thrown
     */
    private <T> T handleAuthorizationRequestError(HttpResponse httpResponse)
    {
        JsonNode jsonNode = readJson(httpResponse.getEntity());
        return handleAuthorizationRequestError(jsonNode);
    }

    /**
     * @param jsonNode to be handled
     * @throws RuntimeException is always thrown
     */
    private <T> T handleAuthorizationRequestError(JsonNode jsonNode)
    {
        String title = "unknown";
        String detail = "none";
        if (jsonNode != null) {
            title = jsonNode.get("title").getTextValue();
            detail = jsonNode.get("detail").getTextValue();
        }
        throw new RuntimeException(String.format("Authorization request failed: %s, %s", title, detail));
    }

    /**
     * @param exception to be handled
     * @throws RuntimeException is always thrown
     */
    private <T> T handleAuthorizationRequestError(Exception exception)
    {
        throw new RuntimeException(String.format("Authorization request failed. %s", exception.getMessage()));
    }

    /**
     * @param data from authorization server
     * @return {@link UserData}
     */
    private static UserData createUserDataFromWebService(JsonNode data)
    {
        UserData userData = new UserData();

        // Required fields
        if (!data.has("id")) {
            throw new IllegalArgumentException("User information must contain identifier.");
        }
        if (!data.has("first_name") || !data.has("last_name")) {
            throw new IllegalArgumentException("User information must contain given and family name.");
        }

        // Common user data
        UserInformation userInformation = userData.getUserInformation();
        userInformation.setUserId(data.get("id").asText());
        userInformation.setFirstName(data.get("first_name").getTextValue());
        userInformation.setLastName(data.get("last_name").getTextValue());
        if (data.has("organization")) {
            JsonNode organization = data.get("organization");
            if (!organization.isNull()) {
                userInformation.setOrganization(organization.getTextValue());
            }
        }
        if (data.has("mail")) {
            JsonNode email = data.get("mail");
            if (!email.isNull()) {
                userInformation.setEmail(email.getTextValue());
            }
        }
        if (data.has("principal_names")) {
            Iterator<JsonNode> principalNameIterator = data.get("principal_names").getElements();
            while (principalNameIterator.hasNext()) {
                JsonNode principalName = principalNameIterator.next();
                userInformation.addPrincipalName(principalName.getTextValue());
            }
        }

        // Additional user data
        if (data.has("language")) {
            JsonNode language = data.get("language");
            if (!language.isNull()) {
                Locale locale = new Locale(language.getTextValue());
                userData.setLocale(locale);
            }
        }

        if (data.has("authentication_info")) {
            JsonNode authenticationInfo = data.get("authentication_info");
            if (authenticationInfo.has("provider") && authenticationInfo.has("loa")) {
                userData.setUserAuthorizationData(new UserAuthorizationData(
                        authenticationInfo.get("provider").getTextValue(),
                        authenticationInfo.get("loa").getIntValue()));
            }
        }

        return userData;
    }

    /**
     * @return new instance of {@link ServerAuthorization}
     * @throws IllegalStateException when other {@link Authorization} already exists
     */
    public static ServerAuthorization createInstance(ControllerConfiguration configuration,
            EntityManagerFactory entityManagerFactory) throws IllegalStateException
    {
        ServerAuthorization serverAuthorization = new ServerAuthorization(configuration, entityManagerFactory);
        Authorization.setInstance(serverAuthorization);
        return serverAuthorization;
    }

    /**
     * Http request handler for {@link #performRequest}
     */
    private static abstract class RequestHandler<T>
    {
        /**
         * Handle HTTP json response.
         *
         * @param data
         * @return parsed json response
         */
        public T success(JsonNode data)
        {
            return null;
        }

        /**
         * Handle HTTP error.
         *
         * @param statusLine
         * @param detail
         */
        public void error(StatusLine statusLine, String detail)
        {
        }
    }
}
