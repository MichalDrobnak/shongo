package cz.cesnet.shongo.controller.domains;

import cz.cesnet.shongo.CommonReportSet;
import cz.cesnet.shongo.Technology;
import cz.cesnet.shongo.TodoImplementException;
import cz.cesnet.shongo.api.Converter;
import cz.cesnet.shongo.api.UserInformation;
import cz.cesnet.shongo.controller.*;
import cz.cesnet.shongo.controller.api.Domain;
import cz.cesnet.shongo.controller.api.domains.InterDomainAction;
import cz.cesnet.shongo.controller.api.domains.response.*;
import cz.cesnet.shongo.controller.api.domains.response.Reservation;
import cz.cesnet.shongo.controller.api.request.DomainCapabilityListRequest;
import cz.cesnet.shongo.controller.booking.ObjectIdentifier;
import cz.cesnet.shongo.controller.booking.resource.ForeignResources;
import cz.cesnet.shongo.controller.scheduler.SchedulerContext;
import cz.cesnet.shongo.ssl.SSLCommunication;
import org.apache.commons.collections4.MultiMap;
import org.apache.commons.collections4.map.MultiValueMap;
import org.apache.ws.commons.util.Base64;

import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.map.ObjectReader;
import org.codehaus.jackson.map.SerializationConfig;
import org.joda.time.Interval;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.util.*;
import java.util.concurrent.*;

/**
 * Foreign domains connector for Inter Domain Agent
 *
 * @author Ondrej Pavelka <pavelka@cesnet.cz>
 */
public class DomainsConnector
{
    private final Integer CORE_POOL_SIZE = 10;

    private ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(CORE_POOL_SIZE);

    private final Logger logger = LoggerFactory.getLogger(InterDomainAgent.class);

    private final ConcurrentMap<String, String> clientAccessTokens = new ConcurrentHashMap<>();

    protected final ObjectMapper mapper = new ObjectMapper();

    private final ControllerConfiguration configuration;

    private final DomainService domainService;

    private final DomainAdminNotifier notifier;

    private final int COMMAND_TIMEOUT;

    private final int THREAD_TIMEOUT = 500;

    public DomainsConnector(ControllerConfiguration configuration, DomainService domainService, DomainAdminNotifier notifier)
    {
        this.domainService = domainService;
        this.configuration = configuration;
        COMMAND_TIMEOUT = configuration.getInterDomainCommandTimeout();
        this.notifier = notifier;
        mapper.configure(SerializationConfig.Feature.WRITE_DATES_AS_TIMESTAMPS, false);
    }

    protected ScheduledThreadPoolExecutor getExecutor()
    {
        return executor;
    }

    protected DomainService getDomainService()
    {
        return this.domainService;
    }

    public ControllerConfiguration getConfiguration()
    {
        return configuration;
    }

    protected <T> Map<String, T> performTypedRequests(final InterDomainAction.HttpMethod method, final String action,
                                                      final MultiMap<String, String> parameters, final Collection<Domain> domains,
                                                      Class<T> objectClass)
    {
        final Map<String, T> resultMap = new HashMap<>();
        ObjectReader reader = mapper.reader(objectClass);
        performRequests(method, action, parameters, domains, reader, resultMap, objectClass);
        return resultMap;
    }

    protected <T> Map<String, List<T>> performTypedListRequests(final InterDomainAction.HttpMethod method, final String action,
                                                                final MultiMap<String, String> parameters, final Collection<Domain> domains,
                                                                Class<T> objectClass)
    {
        final Map<String, List<T>> resultMap = new HashMap<>();
        ObjectReader reader = mapper.reader(mapper.getTypeFactory().constructCollectionType(List.class, objectClass));
        performRequests(method, action, parameters, domains, reader, resultMap, List.class);
        return resultMap;
    }

    protected <T> Map<String, T> performTypedRequests(final InterDomainAction.HttpMethod method, final String action,
                                                      final Map<Domain, MultiMap<String, String>> parametersByDomain, Class<T> objectClass)
    {
        final Map<String, T> resultMap = new HashMap<>();
        ObjectReader reader = mapper.reader(objectClass);
        performRequests(method, action, parametersByDomain, reader, resultMap, objectClass);
        return resultMap;
    }

    /**
     * Returns map of given domains with positive result, or does not add it at all to the map.
     *
     * @param method      of the request
     * @param action      to preform
     * @param domains     for which the request will be performed and will be returned in map
     * @param reader      to parse JSON
     * @param result      collection to store the result
     * @param returnClass {@link Class<T>} of the object to return
     * @return result object as instance of given {@code clazz}
     */
    protected synchronized <T> void performRequests(final InterDomainAction.HttpMethod method, final String action,
                                                    final MultiMap<String, String> parameters, final Collection<Domain> domains,
                                                    final ObjectReader reader, final Map<String, ?> result,
                                                    final Class<T> returnClass)
    {
        final ConcurrentMap<String, Future<T>> futureTasks = new ConcurrentHashMap<>();

        for (final Domain domain : domains) {
            Callable<T> task = new DomainTask<T>(method, action, parameters, domain, reader, returnClass, result, null);
            futureTasks.put(domain.getName(), executor.submit(task));
        }

        while (!futureTasks.isEmpty()) {
            try {
                Iterator<Map.Entry<String, Future<T>>> i = futureTasks.entrySet().iterator();
                while (i.hasNext()) {
                    Map.Entry<String, Future<T>> entry = i.next();
                    if (entry.getValue().isDone()) {
                        futureTasks.remove(entry.getKey());
                    }
                }
                Thread.sleep(THREAD_TIMEOUT);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                continue;
            }
        }
    }

    /**
     * Returns map of given domains with positive result, or does not add it at all to the map.
     *
     * @param method      of the request
     * @param action      to preform
     * @param parametersByDomain    that will be send to given domain
     * @param reader      to parse JSON
     * @param result      collection to store the result
     * @param returnClass {@link Class<T>} of the object to return
     * @return result object as instance of given {@code clazz}
     */
    protected synchronized <T> void performRequests(final InterDomainAction.HttpMethod method, final String action,
                                                    final Map<Domain, MultiMap<String, String>> parametersByDomain,
                                                    final ObjectReader reader, final Map<String, ?> result,
                                                    final Class<T> returnClass)
    {
        final ConcurrentMap<String, Future<T>> futureTasks = new ConcurrentHashMap<>();

        for (final Domain domain : parametersByDomain.keySet()) {
            Callable<T> task = new DomainTask<T>(method, action, parametersByDomain.get(domain), domain, reader, returnClass, result, null);
            futureTasks.put(domain.getName(), executor.submit(task));
        }

        while (!futureTasks.isEmpty()) {
            try {
                Iterator<Map.Entry<String, Future<T>>> i = futureTasks.entrySet().iterator();
                while (i.hasNext()) {
                    Map.Entry<String, Future<T>> entry = i.next();
                    if (entry.getValue().isDone()) {
                        futureTasks.remove(entry.getKey());
                    }
                }
                Thread.sleep(THREAD_TIMEOUT);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                continue;
            }
        }
    }

//    protected <T> T performRequest(final InterDomainAction.HttpMethod method, final String action, final Map<String, String> parameters, final Domain domain, Class<T> objectClass)
//    {
//        return performRequest(method, action, parameters, domain, mapper.reader(objectClass), objectClass);
//    }
//
//    protected <T> List<T> performRequest(final InterDomainAction.HttpMethod method, final String action, final Map<String, String> parameters, final Domain domain, Class<T> objectClass)
//    {
//        ObjectReader reader = mapper.reader(mapper.getTypeFactory().constructCollectionType(List.class, objectClass));
//        return performRequest(method, action, parameters, domain, reader, List.class);
//    }

    /**
     * Perform request on one foreign domain, returns {@link JSONObject} or throws {@link ForeignDomainConnectException}.
     *
     * @param method {@link cz.cesnet.shongo.controller.api.domains.InterDomainAction.HttpMethod}
     * @param action to perform, uses static variables from {@link InterDomainAction}
     * @param domain for which perform the request
     * @param clazz  {@link Class<T>} of the object to return
     * @return result object as instance of given {@code clazz}
     */
    private <T> T performRequest(final InterDomainAction.HttpMethod method, final String action, final MultiMap<String, String> parameters,
                                 final Domain domain, final ObjectReader reader, Class<T> clazz)
    throws ForeignDomainConnectException
    {
        if (action == null || domain == null || reader == null) {
            throw new IllegalArgumentException("Action, domain and reader cannot be null.");
        }
        URL actionUrl = buildRequestUrl(domain, action, parameters);
        logger.debug(String.format("Calling action %s on domain %s", actionUrl, domain.getName()));
        HttpsURLConnection connection = buildConnection(domain, actionUrl);
        // If basic auth is required
        //TODO !configuration.hasInterDomainPKI() &&
        if (configuration.hasInterDomainBasicAuth()) {
            String accessToken = this.clientAccessTokens.get(domain.getName());
            if (accessToken == null) {
                accessToken = login(domain);
            }
            String basicAuth = "Basic " + encodeCredentials(accessToken);
            connection.setRequestProperty("Authorization", basicAuth);
        }

        boolean success = true;
        try {
            connection.setRequestMethod(method.getValue());
            switch (method) {
                case GET:
                    connection.setDoInput(true);
                    connection.setRequestProperty("Accept", "application/json");
                    processError(connection, domain);
//                  ====================DEBUG=====================
//                    BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
//                    StringBuilder stringBuilder = new StringBuilder();
//                    String responseLine;
//                    while ((responseLine = bufferedReader.readLine()) != null) {
//                        stringBuilder.append(responseLine);
//                    }
//                    return reader.readValue(stringBuilder.toString());
//                  ====================DEBUG=====================
                    return reader.readValue(connection.getInputStream());
                case POST:
//                    connection.setDoOutput(true);
//                    connection.setRequestProperty("Content-Type", "application/json");
//                    if (jsonObject != null) {
//                        connection.getOutputStream().write(jsonObject.toString().getBytes());
//                    }
//                    processError(connection, domain);
//                    break;
                case PUT:
                case DELETE:
                    throw new TodoImplementException();
                default:
                    throw new ForeignDomainConnectException(domain, actionUrl.toString(), "Unsupported http method");
            }
        } catch (Exception e) {
            String message = "Failed to perform request (" + actionUrl + ") to domain " + domain.getName();
            logger.error(message, e);
            success = false;
            throw new ForeignDomainConnectException(domain, actionUrl.toString(), e);
        } finally {
            if (success) {
                logger.debug("Action: " + actionUrl + " was successful.");
            }
            connection.disconnect();
        }
    }

    protected URL buildRequestUrl(final Domain domain, String action, MultiMap<String, String> parameters)
    {
        action = action.trim();
        while (action.startsWith("/")) {
            action = action.substring(1, action.length());
        }
        StringBuilder parametersBuilder = new StringBuilder();
        if (parameters != null && !parameters.isEmpty()) {
            parametersBuilder.append("?");
            boolean first = true;
            for (Map.Entry<String, Object> parameter : parameters.entrySet()) {
                for (Object value : (List<?>) parameter.getValue()) {
                    if (first) {
                        first = false;
                    } else {
                        parametersBuilder.append("&");
                    }
                    parametersBuilder.append(parameter.getKey() + "=" + value);
                }
            }
        }
        String actionUrl = domain.getDomainAddress().getFullUrl() + "/" + action + parametersBuilder.toString();
        try {
            return new URL(actionUrl);
        } catch (MalformedURLException e) {
            String message = "Malformed URL " + actionUrl + ".";
            logger.error(message);
            throw new ForeignDomainConnectException(domain, actionUrl, e);
        }
    }

    protected HttpsURLConnection buildConnection(Domain domain, URL url)
    {
        HttpsURLConnection connection;
        try {
            connection = (HttpsURLConnection) url.openConnection();
            connection.setConnectTimeout(COMMAND_TIMEOUT);
            connection.setReadTimeout(COMMAND_TIMEOUT);
            // For secure connection
            if ("HTTPS".equals(url.getProtocol().toUpperCase())) {
                TrustManagerFactory trustManagerFactory = null;
                String certificatePath = domain.getCertificatePath();
                if (certificatePath != null) {
                    KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
                    trustStore.load(null);
                    trustStore.setCertificateEntry(certificatePath.substring(0, certificatePath.lastIndexOf('.')),
                            SSLCommunication.readPEMCert(certificatePath));
                    trustManagerFactory = TrustManagerFactory.getInstance("SunX509");
                    trustManagerFactory.init(trustStore);
                }

                KeyManagerFactory keyManagerFactory = InterDomainAgent.getInstance().getAuthentication().getKeyManagerFactory();
                SSLContext sslContext = SSLContext.getInstance("TLS");
                sslContext.init(keyManagerFactory.getKeyManagers(), trustManagerFactory == null ? null : trustManagerFactory.getTrustManagers(), null);

                connection.setSSLSocketFactory(sslContext.getSocketFactory());
            }
            return connection;
        } catch (IOException e) {
            String message = "Failed to initialize connection for action: " + url;
            logger.error(message, e);
            throw new ForeignDomainConnectException(domain, url.toString(), e);
        } catch (GeneralSecurityException e) {
            String message = "Failed to load client certificate.";
            logger.error(message, e);
            throw new ForeignDomainConnectException(domain, url.toString(), e);
        }
    }

    protected void processError(HttpsURLConnection connection, final Domain domain)
    {
        String actionUrl = connection.getURL().toString();
        try {
            int errorCode = connection.getResponseCode();
            switch (errorCode) {
                case 400:
                    throw new ForeignDomainConnectException(domain, actionUrl, "400 Bad Request " + connection.getResponseMessage());
                case 401:
                    throw new ForeignDomainConnectException(domain, actionUrl, "401 Unauthorized " + connection.getResponseMessage());
                case 403:
                    throw new ForeignDomainConnectException(domain, actionUrl, "401 Forbidden " + connection.getResponseMessage());
                case 404:
                    throw new ForeignDomainConnectException(domain, actionUrl, "404 Not Found " + connection.getResponseMessage());
                case 500:
                    throw new ForeignDomainConnectException(domain, actionUrl, "500 Internal Server Error " + connection.getResponseMessage());
                default:
                    if (errorCode > 400) {
                        throw new ForeignDomainConnectException(domain, actionUrl, errorCode + " " + connection.getResponseMessage());
                    }
            }
        } catch (IOException e) {
            String message = "Failed to get connection respose code for " + actionUrl;
            logger.error(message);
            throw new ForeignDomainConnectException(domain, actionUrl, e);
        }
    }

    public static String encodeCredentials(String credentials)
    {
        return new String(new Base64().encode(credentials.getBytes()).replaceAll("\n", ""));
    }

    /**
     * Section of Inter Domain Connector actions
     */

    /**
     * Login to foreign {@code domain} with basic authentication
     *
     * @return access token
     */
    public String login(Domain domain)
    {
        URL loginUrl = buildRequestUrl(domain, InterDomainAction.DOMAIN_LOGIN, null);
        HttpsURLConnection connection = buildConnection(domain, loginUrl);
        DomainLogin domainLogin = null;
        try {
            String passwordHash = configuration.getInterDomainBasicAuthPasswordHash();

            String userCredentials = LocalDomain.getLocalDomainShortName() + ":" + passwordHash;
            String basicAuth = "Basic " + encodeCredentials(userCredentials);
            connection.setRequestProperty("Authorization", basicAuth);
            connection.setRequestMethod(InterDomainAction.HttpMethod.GET.getValue());
            connection.setUseCaches(false);
            connection.setDoInput(true);
            connection.setDoOutput(true);

            processError(connection, domain);
            ObjectReader reader = mapper.reader(DomainLogin.class);
            InputStream inputStream = connection.getInputStream();
            domainLogin = reader.readValue(inputStream);
        } catch (IOException e) {
            logger.error("Failed to perform login to domain.", e);
            throw new ForeignDomainConnectException(domain, loginUrl.toString(), e);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }

        String accessToken = domainLogin.getAccessToken();
        this.clientAccessTokens.put(domain.getName(), accessToken);
        return accessToken;

    }

    /**
     * @return all domains, even the ones that are not allocatable
     */
    public List<Domain> listForeignDomains()
    {
        return this.domainService.listForeignDomains();
    }

    /**
     * @return all foreign domains used for allocation
     */
    public List<Domain> listAllocatableForeignDomains()
    {
        return this.domainService.listDomains(true, true);
    }

    /**
     * @return all foreign domains
     */
    public List<Domain> listForeignDomains(Boolean onlyAllocatable)
    {
        if (onlyAllocatable != null && onlyAllocatable) {
            return listAllocatableForeignDomains();
        }
        else {
            return listForeignDomains();
        }
    }

    /**
     * Test if domain by given name exists and is allocatable.
     * @param domainName
     * @return
     */
    public boolean isDomainAllocatable(String domainName)
    {
        try {
            Domain domain = this.domainService.findDomainByName(domainName);
            return domain.isAllocatable();
        }
        catch (CommonReportSet.ObjectNotExistsException ex) {
            return false;
        }
    }

    /**
     * Test if domain by given name exists.
     * @param domainName
     * @return
     */
    public boolean domainExists(String domainName)
    {
        try {
            Domain domain = this.domainService.findDomainByName(domainName);
            return domain != null;
        }
        catch (CommonReportSet.ObjectNotExistsException ex) {
            return false;
        }
    }

    /**
     * Returns unmodifiable list of statuses of all foreign domains
     *
     * @return
     */
    public List<Domain> getForeignDomainsStatuses()
    {
        List<Domain> foreignDomains = listForeignDomains();
        Map<String, DomainStatus> response = performTypedRequests(InterDomainAction.HttpMethod.GET, InterDomainAction.DOMAIN_STATUS, null, foreignDomains, DomainStatus.class);
        for (Domain domain : foreignDomains) {
            DomainStatus status = response.get(domain.getName());
            domain.setStatus(status == null ? Domain.Status.NOT_AVAILABLE : status.toStatus());
        }

        return foreignDomains;
    }

    public Map<String, List<DomainCapability>> listForeignCapabilities(DomainCapabilityListRequest request)
    {
        MultiMap<String, String> parameters = new MultiValueMap<>();
        parameters.put("type", request.getCapabilityType().toString());
        if (request.getInterval() != null) {
            parameters.put("interval", Converter.convertIntervalToStringUTC(request.getInterval()));
        }
        if (request.getLicenseCount() != null) {
            parameters.put("licenseCount", request.getLicenseCount());
        }
        if (DomainCapabilityListRequest.Type.VIRTUAL_ROOM.equals(request.getCapabilityType())) {
            if (request.getTechnologyVariants() != null) {
                if (request.getTechnologyVariants().isEmpty()) {
                    throw new IllegalArgumentException("Some technology must be set.");
                }
                if (request.getTechnologyVariants().size() > 1) {
                    throw new TodoImplementException();
                }
                for (Technology technology : request.getTechnologyVariants().get(0)) {
                    parameters.put("technologies", technology.toString());
                }
            }
        }
        List<Domain> domains;
        if (request.getDomain() == null) {
            domains = listForeignDomains(request.getOnlyAllocatable());
        }
        else {
            domains = new ArrayList<>();
            domains.add(request.getDomain());
        }
        // Resource IDs are not filtered by inter domain protocol
        Map<String, List<DomainCapability>> domainResources = performTypedListRequests(InterDomainAction.HttpMethod.GET,
                InterDomainAction.DOMAIN_CAPABILITY_LIST, parameters, domains, DomainCapability.class);
        return domainResources;
    }

    public Reservation allocateResource(SchedulerContext schedulerContext, Interval slot, ForeignResources foreignResources,
                                        String previousReservationRequestId)
    {
        Domain domain = foreignResources.getDomain().toApi();
        ObjectReader reader = mapper.reader(Reservation.class);
        MultiMap<String, String> parameters = new MultiValueMap<>();
        parameters.put("slot", Converter.convertIntervalToStringUTC(slot));
        parameters.put("type", DomainCapabilityListRequest.Type.RESOURCE.toString());
        parameters.put("resourceId", ObjectIdentifier.formatId(foreignResources));
        parameters.put("userId", schedulerContext.getUserId());
        parameters.put("description", schedulerContext.getDescription());
        if (previousReservationRequestId != null) {
            parameters.put("reservationRequestId", previousReservationRequestId);
        }

        Reservation reservation = performRequest(InterDomainAction.HttpMethod.GET, InterDomainAction.DOMAIN_ALLOCATE_RESOURCE, parameters, domain, reader, Reservation.class);
        if (reservation.getSlot() == null) {
            reservation.setSlot(slot);
        }
        return reservation;
    }

    public List<Reservation> allocateRoom(Set<Domain> domains, int participantCount, List<Set<Technology>> technologyVariants, Interval slot, SchedulerContext schedulerContext)
    {
        MultiMap<String, String> parameters = new MultiValueMap<>();
        parameters.put("slot", Converter.convertIntervalToStringUTC(slot));
        parameters.put("participantCount", participantCount);
        parameters.put("userId", schedulerContext.getUserId());
        parameters.put("description", schedulerContext.getDescription());
//        parameters.put("roomPin", );
//        parameters.put("roomAccessMode", );
//        parameters.put("roomRecorded", );
        if (technologyVariants == null  || technologyVariants.isEmpty()) {
            throw new IllegalArgumentException("Some technology must be set.");
        }
        if (technologyVariants.size() > 1) {
            throw new TodoImplementException();
        }
        for (Technology technology : technologyVariants.get(0)) {
            parameters.put("technologies", technology.toString());
        }
        //TODO: participants
        //TODO: room name

        Map<String, Reservation> reservations = performTypedRequests(InterDomainAction.HttpMethod.GET, InterDomainAction.DOMAIN_ALLOCATE_ROOM, parameters, domains, Reservation.class);
        return new ArrayList<>(reservations.values());
    }

    public Reservation getReservationByRequest(Domain domain, String foreignReservationRequestId)
    {
        ObjectReader reader = mapper.reader(Reservation.class);
        MultiMap<String, String> parameters = new MultiValueMap<>();
        parameters.put("reservationRequestId", foreignReservationRequestId);

        Reservation reservation = performRequest(InterDomainAction.HttpMethod.GET, InterDomainAction.DOMAIN_RESERVATION_DATA, parameters, domain, reader, Reservation.class);

        return reservation;
    }

    public List<Reservation> getReservationsByRequests(Set<String> foreignReservationRequestIds)
    {
        Map<Domain, MultiMap<String, String>> parametersByDomain = new HashMap<>();
        for (String reservationRequestId : foreignReservationRequestIds) {
            MultiMap<String, String> parameters = new MultiValueMap<>();
            parameters.put("reservationRequestId", reservationRequestId);

            String domainName = ObjectIdentifier.parseForeignDomain(reservationRequestId);
            Domain domain = getDomainService().findDomainByName(domainName);
            parametersByDomain.put(domain, parameters);
        }

        Map<String, Reservation> response = performTypedRequests(InterDomainAction.HttpMethod.GET, InterDomainAction.DOMAIN_RESERVATION_DATA, parametersByDomain, Reservation.class);
        List<Reservation> reservations = new ArrayList<>();
        for (String domainName : response.keySet()) {
//            Domain domain = getDomainService().findDomainByName(domainName);
            Reservation reservation = response.get(domainName);

//            Long domainId = ObjectIdentifier.parse(domain.getId()).getPersistenceId();
//            reservation.setUserId(UserInformation.formatForeignUserId(reservation.getUserId(), domainId));

            reservations.add(reservation);
        }

        return reservations;
    }

    public boolean deallocateReservation(Domain domain, String foreignReservationRequestId)
    {
        ObjectReader reader = mapper.reader(AbstractResponse.class);
        MultiMap<String, String> parameters = new MultiValueMap<>();
        parameters.put("reservationRequestId", foreignReservationRequestId);

        AbstractResponse response = performRequest(InterDomainAction.HttpMethod.GET, InterDomainAction.DOMAIN_RESERVATION_REQUEST_DELETE, parameters, domain, reader, AbstractResponse.class);

        return AbstractResponse.Status.OK.equals(response.getStatus());
    }

//    public List<Reservation> listReservations(Domain domain)
//    {
//        return listReservations(domain, null, null);
//    }

    public List<Reservation> listReservations(Domain domain, String resourceId, Interval slot)
    {
        ObjectReader reader = mapper.reader(mapper.getTypeFactory().constructCollectionType(List.class, Reservation.class));
        MultiMap<String, String> parameters = new MultiValueMap<>();
        if (resourceId != null) {
            parameters.put("resourceId", resourceId);
        }
        if (slot != null) {
            parameters.put("slot", Converter.convertIntervalToStringUTC(slot));
        }

        List<Reservation> response = performRequest(InterDomainAction.HttpMethod.GET, InterDomainAction.DOMAIN_RESOURCE_RESERVATION_LIST, parameters, domain, reader, List.class);
        List<Reservation> reservations = new ArrayList<>();
        for (Reservation reservation : response) {
            if (resourceId == null || resourceId.equals(reservation.getForeignResourceId())) {
                Long domainId = ObjectIdentifier.parse(domain.getId()).getPersistenceId();
                reservation.setUserId(UserInformation.formatForeignUserId(reservation.getUserId(), domainId));
                reservations.add(reservation);
            }
        }
        return reservations;
    }

    /**
     * Represents action to be called on domain. Returns result after successful call. If {@code result} or
     * {@code unavailableDomains} are set, result or failed action will be written to them (synchronized on the {@code result}).
     * @param <T> class of the result
     */
    protected class DomainTask<T> implements Callable<T>, Runnable
    {
        /**
         * Http method of the action
         */
        private InterDomainAction.HttpMethod method;

        /**
         * URL path of the action to call
         */
        private String action;

        /**
         * GET parameters of the action
         */
        private MultiMap<String, String> parameters;

        /**
         * Domain for which will the action be called
         */
        private Domain domain;

        /**
         * Reader to parse the JSON result
         */
        private ObjectReader reader;

        /**
         * Class of the result to be returned
         */
        private Class<T> returnClass;

        /**
         * Result map to be filled
         */
        private Map<String, ?> result;

        /**
         * Set of domains for which the action fails
         */
        private Set<String> unavailableDomains;

        public DomainTask(final InterDomainAction.HttpMethod method, final String action,
                          final MultiMap<String, String> parameters, final Domain domain,
                          final ObjectReader reader, final Class<T> returnClass,
                          final Map<String, ?> result, final Set<String> unavailableDomains)
        {
            this.method = method;
            this.action = action;
            this.parameters = parameters;
            this.domain = domain;
            this.reader = reader;
            this.returnClass = returnClass;
            this.result = result;
            this.unavailableDomains = unavailableDomains;
        }

        /**
         * Callable will be terminated (throws {@link IllegalStateException}) only if domains does not exist.
         *
         * @return
         */
        @Override
        synchronized public T call()
        {
            if (!Thread.currentThread().getName().contains("domainTask")) {
                Thread.currentThread().setName(Thread.currentThread().getName() + "-domainTask-" + domain.getName());
            }
            boolean failed = true;
            try {
                if (InterDomainAgent.getInstance().getDomainService().getDomain(domain.getId()) == null) {
                    terminateDomainTask();
                }
                T response = performRequest(method, action, parameters, domain, reader, returnClass);
                if (result != null && response != null) {
                    synchronized (result) {
                        ((Map<String, T>) result).put(domain.getName(), response);
                        if (unavailableDomains != null) {
                            unavailableDomains.remove(domain.getName());
                        }
                    }
                    failed = false;
                }
                return response;
            } catch (IllegalStateException e) {
                logger.debug("Scheduled command has ended (should be ok), action: " + action + " on domain: " + domain.getName());
                // Thread will be terminated (in {@link ScheduledThreadPoolExecutor}).
                throw e;
            } catch (Exception e) {
                try {
                    notifier.logAndNotifyDomainAdmins("Failed to perform request to domain " + domain.getName(), e);
                } catch (Exception notifyEx) {
                    logger.error("Notification has failed.", notifyEx);
                }
                return null;
            } finally {
                // If {@code unavailableDomains} is set and request failed add it and also to {@link result}
                // with empty {@code ArrayList} if possible.
                // NOTICE: {@code unavailableDomains} is used only by CachedDomainsConnector.
                if (unavailableDomains != null && failed) {
                    synchronized (result) {
                        if (!result.containsKey(domain.getName())) {
                            try {
                                result.put(domain.getName(), null);
                            }
                            catch (ClassCastException ex) {
                                // Ignore
                            }
                        }
                        unavailableDomains.add(domain.getName());
                    }
                }
            }
        }

        /**
         * Runnable will be terminated (throws {@link IllegalStateException}) if domain does not exist or is no allocatable.
         * Allows only request for allocatable domains.
         */
        @Override
        synchronized public void run()
        {

            if (!Thread.currentThread().getName().contains("domainTask")) {
                Thread.currentThread().setName(Thread.currentThread().getName() + "-domainTask-" + domain.getName());
            }
            Domain internalDomain = InterDomainAgent.getInstance().getDomainService().getDomain(domain.getId());
            if (internalDomain == null || !internalDomain.isAllocatable()) {
                terminateDomainTask();
            }
            call();
        }

        private void terminateDomainTask() throws IllegalStateException
        {
            synchronized (result) {
                if (result != null) {
                    result.remove(domain.getName());
                }
                if (unavailableDomains != null) {
                    unavailableDomains.remove(domain.getName());
                }
            }
            logger.info("Domain '" + domain.getName() + "' does not exist or is not allocatable. Domain task terminated.");
            throw new IllegalStateException("Domain '" + domain.getName() + "' does not exist or is not allocatable.");
        }
    }
}
