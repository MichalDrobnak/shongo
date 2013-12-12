package cz.cesnet.shongo.client.web;

import cz.cesnet.shongo.ExpirationMap;
import cz.cesnet.shongo.api.UserInformation;
import cz.cesnet.shongo.controller.EntityPermission;
import cz.cesnet.shongo.controller.SystemPermission;
import cz.cesnet.shongo.controller.api.*;
import cz.cesnet.shongo.controller.api.request.EntityPermissionListRequest;
import cz.cesnet.shongo.controller.api.request.ListResponse;
import cz.cesnet.shongo.controller.api.request.ReservationRequestListRequest;
import cz.cesnet.shongo.controller.api.request.UserListRequest;
import cz.cesnet.shongo.controller.api.rpc.AuthorizationService;
import cz.cesnet.shongo.controller.api.rpc.ExecutableService;
import cz.cesnet.shongo.controller.api.rpc.ReservationService;
import org.joda.time.DateTime;
import org.joda.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

import javax.annotation.Resource;
import java.util.*;

/**
 * Cache of {@link UserInformation}s, {@link EntityPermission}s, {@link ReservationRequestSummary}s.
 *
 * @author Martin Srom <martin.srom@cesnet.cz>
 */
public class Cache
{
    private static Logger logger = LoggerFactory.getLogger(Cache.class);

    /**
     * Expiration of user information/permissions in minutes.
     */
    private static final long USER_EXPIRATION_MINUTES = 5;

    @Resource
    private AuthorizationService authorizationService;

    @Resource
    private ReservationService reservationService;

    @Resource
    private ExecutableService executableService;

    /**
     * {@link UserInformation}s by user-ids.
     */
    private ExpirationMap<SecurityToken, Map<SystemPermission, Boolean>> systemPermissionsByToken =
            new ExpirationMap<SecurityToken, Map<SystemPermission, Boolean>>();

    /**
     * {@link UserInformation}s by user-ids.
     */
    private ExpirationMap<String, UserInformation> userInformationByUserId =
            new ExpirationMap<String, UserInformation>();

    /**
     * {@link UserState}s by {@link SecurityToken}.
     */
    private ExpirationMap<SecurityToken, UserState> userStateByToken = new ExpirationMap<SecurityToken, UserState>();

    /**
     * {@link ReservationRequestSummary} by identifier.
     */
    private ExpirationMap<String, ReservationRequestSummary> reservationRequestById =
            new ExpirationMap<String, ReservationRequestSummary>();

    /**
     * {@link Reservation} by identifier.
     */
    private ExpirationMap<String, Reservation> reservationById =
            new ExpirationMap<String, Reservation>();

    /**
     * {@link Reservation} by identifier.
     */
    private ExpirationMap<String, Executable> executableById =
            new ExpirationMap<String, Executable>();

    /**
     * Cached information for single user.
     */
    private static class UserState
    {
        /**
         * Set of permissions which the user has for entity.
         */
        private ExpirationMap<String, Set<EntityPermission>> entityPermissionsByEntity =
                new ExpirationMap<String, Set<EntityPermission>>();

        /**
         * Constructor.
         */
        public UserState()
        {
            entityPermissionsByEntity.setExpiration(Duration.standardMinutes(USER_EXPIRATION_MINUTES));
        }
    }

    /**
     * Constructor.
     */
    public Cache()
    {
        // Set expiration durations
        systemPermissionsByToken.setExpiration(Duration.standardMinutes(5));
        userInformationByUserId.setExpiration(Duration.standardMinutes(USER_EXPIRATION_MINUTES));
        userStateByToken.setExpiration(Duration.standardHours(1));
        reservationRequestById.setExpiration(Duration.standardMinutes(5));
        reservationById.setExpiration(Duration.standardMinutes(5));
        executableById.setExpiration(Duration.standardSeconds(10));
    }

    /**
     * Method called each 5 minutes to clear expired items.
     */
    @Scheduled(fixedDelay = (USER_EXPIRATION_MINUTES * 60 * 1000))
    public synchronized void clearExpired()
    {
        logger.debug("Clearing expired user cache...");
        DateTime dateTimeNow = DateTime.now();
        systemPermissionsByToken.clearExpired(dateTimeNow);
        userInformationByUserId.clearExpired(dateTimeNow);
        userStateByToken.clearExpired(dateTimeNow);
        for (UserState userState : userStateByToken) {
            userState.entityPermissionsByEntity.clearExpired(dateTimeNow);
        }
        reservationRequestById.clearExpired(dateTimeNow);
        reservationById.clearExpired(dateTimeNow);
        executableById.clearExpired(dateTimeNow);
    }

    /**
     * @param executableId to be removed from the {@link #executableById}
     */
    public synchronized void clearExecutable(String executableId)
    {
        executableById.remove(executableId);
    }

    /**
     * @param securityToken to be removed from the {@link #systemPermissionsByToken}
     */
    public synchronized void clearSystemPermissions(SecurityToken securityToken)
    {
        systemPermissionsByToken.remove(securityToken);
    }

    /**
     * @param securityToken
     * @param systemPermission
     * @return true whether requesting user has given {@code systemPermission},
     *         false otherwise
     */
    public synchronized boolean hasSystemPermission(SecurityToken securityToken, SystemPermission systemPermission)
    {
        Map<SystemPermission, Boolean> systemPermissions = systemPermissionsByToken.get(securityToken);
        if (systemPermissions == null) {
            systemPermissions = new HashMap<SystemPermission, Boolean>();
            systemPermissionsByToken.put(securityToken, systemPermissions);
        }
        Boolean systemPermissionResult = systemPermissions.get(systemPermission);
        if (systemPermissionResult == null) {
            systemPermissionResult = authorizationService.hasSystemPermission(securityToken, systemPermission);
            systemPermissions.put(systemPermission, systemPermissionResult);
        }
        return systemPermissionResult;
    }

    /**
     * @param securityToken to be used for fetching the {@link UserInformation}
     * @param userId        user-id of the requested user
     * @return {@link UserInformation} for given {@code userId}
     */
    public synchronized UserInformation getUserInformation(SecurityToken securityToken, String userId)
    {
        UserInformation userInformation = userInformationByUserId.get(userId);
        if (userInformation == null) {
            ListResponse<UserInformation> response = authorizationService.listUsers(
                    new UserListRequest(securityToken, userId));
            if (response.getCount() == 0) {
                throw new RuntimeException("User with id '" + userId + "' hasn't been found.");
            }
            userInformation = response.getItem(0);
            userInformationByUserId.put(userId, userInformation);
        }
        return userInformation;
    }

    /**
     * @param securityToken
     * @return {@link UserState} for user with given {@code securityToken}
     */
    private synchronized UserState getUserState(SecurityToken securityToken)
    {
        UserState userState = userStateByToken.get(securityToken);
        if (userState == null) {
            userState = new UserState();
            userStateByToken.put(securityToken, userState);
        }
        return userState;
    }

    /**
     * @param securityToken of the requesting user
     * @param entityId      of the entity
     * @return set of {@link EntityPermission} for requesting user and given {@code entityId}
     */
    public synchronized Set<EntityPermission> getEntityPermissions(SecurityToken securityToken, String entityId)
    {
        UserState userState = getUserState(securityToken);
        Set<EntityPermission> entityPermissions = userState.entityPermissionsByEntity.get(entityId);
        if (entityPermissions == null) {
            Map<String, EntityPermissionSet> permissionsByEntity =
                    authorizationService.listEntityPermissions(new EntityPermissionListRequest(securityToken, entityId));
            entityPermissions = new HashSet<EntityPermission>();
            entityPermissions.addAll(permissionsByEntity.get(entityId).getEntityPermissions());
            userState.entityPermissionsByEntity.put(entityId, entityPermissions);
        }
        return entityPermissions;
    }

    /**
     * @param securityToken
     * @param reservationRequests
     * @return map of {@link EntityPermission}s by reservation request identifier
     */
    public Map<String, Set<EntityPermission>> getReservationRequestsPermissions(SecurityToken securityToken,
            Collection<ReservationRequestSummary> reservationRequests)
    {
        Map<String, Set<EntityPermission>> permissionsByReservationRequestId = new HashMap<String, Set<EntityPermission>>();
        Set<String> reservationRequestIds = new HashSet<String>();
        for (ReservationRequestSummary reservationRequest : reservationRequests) {
            String reservationRequestId = reservationRequest.getId();
            Set<EntityPermission> entityPermissions =
                    getEntityPermissionsWithoutFetching(securityToken, reservationRequestId);
            if (entityPermissions != null) {
                permissionsByReservationRequestId.put(reservationRequestId, entityPermissions);
            }
            else {
                reservationRequestIds.add(reservationRequestId);
            }
        }
        if (reservationRequestIds.size() > 0) {
            permissionsByReservationRequestId.putAll(fetchEntityPermissions(securityToken, reservationRequestIds));
        }
        return permissionsByReservationRequestId;
    }

    /**
     * @param securityToken of the requesting user
     * @param entityId      of the entity
     * @return set of {@link EntityPermission} for requesting user and given {@code entityId}
     *         or null if the {@link EntityPermission}s aren't cached
     */
    public synchronized Set<EntityPermission> getEntityPermissionsWithoutFetching(
            SecurityToken securityToken, String entityId)
    {
        UserState userState = getUserState(securityToken);
        return userState.entityPermissionsByEntity.get(entityId);
    }

    /**
     * Fetch {@link EntityPermission}s for given {@code entityIds}.
     *
     * @param securityToken
     * @param entityIds
     * @return fetched {@link EntityPermission}s by {@code entityIds}
     */
    public synchronized Map<String, Set<EntityPermission>> fetchEntityPermissions(
            SecurityToken securityToken, Set<String> entityIds)
    {
        Map<String, Set<EntityPermission>> result = new HashMap<String, Set<EntityPermission>>();
        if (entityIds.isEmpty()) {
            return result;
        }
        UserState userState = getUserState(securityToken);
        Map<String, EntityPermissionSet> permissionsByEntity =
                authorizationService.listEntityPermissions(new EntityPermissionListRequest(securityToken, entityIds));
        for (Map.Entry<String, EntityPermissionSet> entry : permissionsByEntity.entrySet()) {
            String entityId = entry.getKey();
            Set<EntityPermission> entityPermissions = userState.entityPermissionsByEntity.get(entityId);
            if (entityPermissions == null) {
                entityPermissions = new HashSet<EntityPermission>();
                userState.entityPermissionsByEntity.put(entityId, entityPermissions);
            }
            entityPermissions.clear();
            entityPermissions.addAll(entry.getValue().getEntityPermissions());
            result.put(entityId, entityPermissions);
        }
        return result;
    }

    /**
     * Load {@link ReservationRequestSummary}s for given {@code reservationRequestIds} to the {@link Cache}.
     *
     * @param securityToken
     * @param reservationRequestIds
     */
    public synchronized void fetchReservationRequests(SecurityToken securityToken, Set<String> reservationRequestIds)
    {
        Set<String> missingReservationRequestIds = null;
        for (String reservationRequestId : reservationRequestIds) {
            if (!reservationRequestById.contains(reservationRequestId)) {
                if (missingReservationRequestIds == null) {
                    missingReservationRequestIds = new HashSet<String>();
                }
                missingReservationRequestIds.add(reservationRequestId);
            }
        }
        if (missingReservationRequestIds != null) {
            ReservationRequestListRequest request = new ReservationRequestListRequest();
            request.setSecurityToken(securityToken);
            request.setReservationRequestIds(missingReservationRequestIds);
            ListResponse<ReservationRequestSummary> response = reservationService.listReservationRequests(request);
            for (ReservationRequestSummary reservationRequest : response) {
                reservationRequestById.put(reservationRequest.getId(), reservationRequest);
            }
        }
    }

    /**
     * @param securityToken
     * @param reservationRequestId
     * @return {@link ReservationRequestSummary} for given {@code reservationRequestId}
     */
    public synchronized ReservationRequestSummary getReservationRequestSummary(SecurityToken securityToken,
            String reservationRequestId)
    {
        ReservationRequestSummary reservationRequest = reservationRequestById.get(reservationRequestId);
        if (reservationRequest == null) {
            reservationRequest = getReservationRequestSummaryNotCached(securityToken, reservationRequestId);
        }
        return reservationRequest;
    }

    /**
     * @param securityToken
     * @param reservationRequestId
     * @return {@link ReservationRequestSummary} for given {@code reservationRequestId}
     */
    public synchronized ReservationRequestSummary getReservationRequestSummaryNotCached(SecurityToken securityToken,
            String reservationRequestId)
    {
        ReservationRequestListRequest request = new ReservationRequestListRequest();
        request.setSecurityToken(securityToken);
        request.addReservationRequestId(reservationRequestId);
        ListResponse<ReservationRequestSummary> response = reservationService.listReservationRequests(request);
        if (response.getItemCount() > 0) {
            ReservationRequestSummary reservationRequest = response.getItem(0);
            reservationRequestById.put(reservationRequest.getId(), reservationRequest);
            return reservationRequest;
        }
        return null;
    }

    /**
     * @param securityToken
     * @param reservationId
     * @return {@link Reservation} for given {@code reservationId}
     */
    public synchronized Reservation getReservation(SecurityToken securityToken, String reservationId)
    {
        Reservation reservation = reservationById.get(reservationId);
        if (reservation == null) {
            reservation = reservationService.getReservation(securityToken, reservationId);
            reservationById.put(reservationId, reservation);
        }
        return reservation;
    }

    /**
     * @param securityToken
     * @param executable
     * @return reservation request id for given {@code executable}
     */
    public synchronized String getReservationRequestIdByExecutable(SecurityToken securityToken, Executable executable)
    {
        Reservation reservation = getReservation(securityToken, executable.getReservationId());
        return reservation.getReservationRequestId();
    }

    /**
     * @param securityToken
     * @param executableId
     * @return reservation request id for given {@code executableId}
     */
    public synchronized String getReservationRequestIdByExecutableId(SecurityToken securityToken, String executableId)
    {
        Executable executable = getExecutable(securityToken, executableId);
        return getReservationRequestIdByExecutable(securityToken, executable);
    }

    /**
     * @param securityToken
     * @param executableId
     * @return {@link Executable} for given {@code executableId}
     */
    public synchronized Executable getExecutable(SecurityToken securityToken, String executableId)
    {
        Executable executable = executableById.get(executableId);
        if (executable == null) {
            executable = executableService.getExecutable(securityToken, executableId);
            executableById.put(executableId, executable);
        }
        return executable;
    }
}
