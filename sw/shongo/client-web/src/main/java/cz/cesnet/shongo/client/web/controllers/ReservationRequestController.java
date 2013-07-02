package cz.cesnet.shongo.client.web.controllers;

import cz.cesnet.shongo.AliasType;
import cz.cesnet.shongo.Technology;
import cz.cesnet.shongo.api.UserInformation;
import cz.cesnet.shongo.client.web.Cache;
import cz.cesnet.shongo.client.web.models.ReservationRequestModel;
import cz.cesnet.shongo.client.web.models.UnsupportedApiException;
import cz.cesnet.shongo.controller.Permission;
import cz.cesnet.shongo.controller.api.*;
import cz.cesnet.shongo.controller.api.request.ListResponse;
import cz.cesnet.shongo.controller.api.request.ReservationRequestListRequest;
import cz.cesnet.shongo.controller.api.rpc.ReservationService;
import org.joda.time.Interval;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.*;

/**
 * Controller for managing reservation requests.
 *
 * @author Martin Srom <martin.srom@cesnet.cz>
 */
@Controller
@RequestMapping("/reservation-request")
public class ReservationRequestController
{
    private static final String MESSAGE_ADHOC_ROOM = "views.reservationRequest.specification.ADHOC_ROOM";
    private static final String MESSAGE_PERMANENT_ROOM = "views.reservationRequest.specification.PERMANENT_ROOM";
    private static final String MESSAGE_ROOMS_ROOM_ADHOC = "views.reservationRequestList.rooms.room.adhoc";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormat.forStyle("M-");
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormat.forStyle("MS");

    @Resource
    private ReservationService reservationService;

    @Resource
    private Cache cache;

    @Resource
    private MessageSource messageSource;

    @RequestMapping(value = {"", "/list"}, method = RequestMethod.GET)
    public String getList()
    {
        return "reservationRequestList";
    }

    @RequestMapping(value = "/delete/{id:.+}", method = RequestMethod.GET)
    public String getDelete(
            SecurityToken securityToken,
            @PathVariable(value = "id") String reservationRequestId, Model model)
    {
        // Get reservation request
        AbstractReservationRequest reservationRequest =
                reservationService.getReservationRequest(securityToken, reservationRequestId);

        // List reservation requests which got provided the reservation request to be deleted
        ReservationRequestListRequest reservationRequestListRequest = new ReservationRequestListRequest();
        reservationRequestListRequest.setSecurityToken(securityToken);
        reservationRequestListRequest.setProvidedReservationRequestId(reservationRequestId);
        ListResponse<ReservationRequestSummary> reservationRequests =
                reservationService.listReservationRequests(reservationRequestListRequest);

        model.addAttribute("dependencies", reservationRequests.getItems());
        model.addAttribute("reservationRequest", reservationRequest);
        return "reservationRequestDelete";
    }

    @RequestMapping(value = "/delete/confirmed", method = RequestMethod.POST)
    public String getDeleteConfirmed(
            SecurityToken securityToken,
            @RequestParam(value = "id") String reservationRequestId)
    {
        reservationService.deleteReservationRequest(securityToken, reservationRequestId);
        return "redirect:/reservation-request";
    }

    @RequestMapping(value = "/data", method = RequestMethod.GET)
    @ResponseBody
    public Map getDataList(
            Locale locale,
            SecurityToken securityToken,
            @RequestParam(value = "start", required = false) Integer start,
            @RequestParam(value = "count", required = false) Integer count,
            @RequestParam(value = "type", required = false) ReservationRequestModel.SpecificationType specificationType)
    {
        // List reservation requests
        ReservationRequestListRequest request = new ReservationRequestListRequest();
        request.setSecurityToken(securityToken);
        request.setStart(start);
        request.setCount(count);
        request.setSort(ReservationRequestListRequest.Sort.DATETIME);
        request.setSortDescending(true);
        if (specificationType != null) {
            switch (specificationType) {
                case PERMANENT_ROOM:
                    request.addSpecificationClass(AliasSpecification.class);
                    request.addSpecificationClass(AliasSetSpecification.class);
                    break;
                case ADHOC_ROOM:
                    request.addSpecificationClass(RoomSpecification.class);
                    break;
            }
        }
        ListResponse<ReservationRequestSummary> response = reservationService.listReservationRequests(request);

        // Get permissions for reservation requests
        Map<String, Set<Permission>> permissionsByReservationRequestId = new HashMap<String, Set<Permission>>();
        Set<String> reservationRequestIds = new HashSet<String>();
        for (ReservationRequestSummary responseItem : response.getItems()) {
            String reservationRequestId = responseItem.getId();
            Set<Permission> permissions = cache.getPermissionsWithoutFetching(securityToken, reservationRequestId);
            if (permissions != null) {
                permissionsByReservationRequestId.put(reservationRequestId, permissions);
            }
            else {
                reservationRequestIds.add(reservationRequestId);
            }
        }
        if (reservationRequestIds.size() > 0) {
            permissionsByReservationRequestId.putAll(cache.fetchPermissions(securityToken, reservationRequestIds));
        }

        Set<String> providedReservationRequestIds = new HashSet<String>();
        for (ReservationRequestSummary reservationRequest : response.getItems()) {
            String providedReservationRequestId = reservationRequest.getProvidedReservationRequestId();
            if (providedReservationRequestId != null) {
                providedReservationRequestIds.add(providedReservationRequestId);
            }
        }
        cache.loadReservationRequests(securityToken, providedReservationRequestIds);

        // Build response
        DateTimeFormatter dateFormatter = DATE_FORMATTER.withLocale(locale);
        DateTimeFormatter dateTimeFormatter = DATE_TIME_FORMATTER.withLocale(locale);
        List<Map<String, Object>> items = new LinkedList<Map<String, Object>>();
        for (ReservationRequestSummary reservationRequest : response.getItems()) {
            String reservationRequestId = reservationRequest.getId();

            Map<String, Object> item = new HashMap<String, Object>();
            item.put("id", reservationRequestId);
            item.put("description", reservationRequest.getDescription());
            item.put("purpose", reservationRequest.getPurpose());
            item.put("dateTime", dateFormatter.print(reservationRequest.getDateTime()));
            items.add(item);

            AllocationState allocationState = reservationRequest.getAllocationState();

            if (allocationState != null) {
                String allocationStateMessage = messageSource.getMessage(
                        "views.reservationRequest.allocationState." + allocationState, null, locale);
                item.put("allocationState", allocationState);
                item.put("allocationStateMessage", allocationStateMessage);
            }

            Set<Permission> permissions = permissionsByReservationRequestId.get(reservationRequestId);
            item.put("writable", permissions.contains(Permission.WRITE));

            UserInformation user = cache.getUserInformation(securityToken, reservationRequest.getUserId());
            item.put("user", user.getFullName());

            Interval earliestSlot = reservationRequest.getEarliestSlot();
            if (earliestSlot != null) {
                item.put("earliestSlotStart", dateTimeFormatter.print(earliestSlot.getStart()));
                item.put("earliestSlotEnd", dateTimeFormatter.print(earliestSlot.getEnd()));
            }

            Set<Technology> technologies = reservationRequest.getTechnologies();
            ReservationRequestModel.Technology technology = ReservationRequestModel.Technology.find(technologies);
            ReservationRequestSummary.Specification specification = reservationRequest.getSpecification();
            if (specification instanceof ReservationRequestSummary.RoomSpecification) {
                ReservationRequestSummary.RoomSpecification roomSpecification =
                        (ReservationRequestSummary.RoomSpecification) specification;
                item.put("type", messageSource.getMessage(MESSAGE_ADHOC_ROOM, null, locale));
                String providedReservationRequestId = reservationRequest.getProvidedReservationRequestId();
                if (providedReservationRequestId != null) {
                    ReservationRequestSummary providedReservationRequest =
                            cache.getReservationRequest(securityToken, providedReservationRequestId);
                    ReservationRequestSummary.AliasSpecification aliasSpecification =
                            (ReservationRequestSummary.AliasSpecification) providedReservationRequest.getSpecification();
                    if (aliasSpecification != null && AliasType.ROOM_NAME.equals(aliasSpecification.getAliasType())) {
                        item.put("room", aliasSpecification.getValue());
                        item.put("roomReservationRequestId", providedReservationRequestId);
                    }
                    else {
                        throw new UnsupportedApiException(aliasSpecification);
                    }
                }
                else {
                    StringBuilder roomBuilder = new StringBuilder();
                    roomBuilder.append(messageSource.getMessage(MESSAGE_ROOMS_ROOM_ADHOC, null, locale));
                    if (technology != null) {
                        roomBuilder.append(" (");
                        roomBuilder.append(technology.getTitle());
                        roomBuilder.append(")");
                    }
                    item.put("room", roomBuilder.toString());
                }
                item.put("participantCount", roomSpecification.getParticipantCount());
            }
            else if (specification instanceof ReservationRequestSummary.AliasSpecification) {
                ReservationRequestSummary.AliasSpecification aliasType = (ReservationRequestSummary.AliasSpecification) specification;
                item.put("type", messageSource.getMessage(MESSAGE_PERMANENT_ROOM, null, locale));
                if (technology != null) {
                    item.put("technology", technology.getTitle());
                }
                if (aliasType.getAliasType().equals(AliasType.ROOM_NAME)) {
                    item.put("roomName", aliasType.getValue());
                }
            }
        }
        Map<String, Object> data = new HashMap<String, Object>();
        data.put("start", response.getStart());
        data.put("count", response.getCount());
        data.put("items", items);
        return data;
    }
}
