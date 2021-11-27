package cz.cesnet.shongo.controller.rest.api;

import cz.cesnet.shongo.Technology;
import cz.cesnet.shongo.Temporal;
import cz.cesnet.shongo.api.UserInformation;
import cz.cesnet.shongo.controller.api.ReservationRequestSummary;
import cz.cesnet.shongo.controller.api.SecurityToken;
import cz.cesnet.shongo.controller.api.request.ListResponse;
import cz.cesnet.shongo.controller.api.request.ReservationRequestListRequest;
import cz.cesnet.shongo.controller.api.rpc.ReservationService;
import cz.cesnet.shongo.controller.rest.models.ReservationRequestState;
import cz.cesnet.shongo.controller.rest.models.SpecificationType;
import cz.cesnet.shongo.controller.rest.models.TechnologyModel;
import org.joda.time.Interval;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.joda.time.DateTime;

import java.util.*;

/**
 * @author Michal Drobňák
 */
@RestController
@RequestMapping("/api/v1/reservation_request")
public class ReservationRequestController {
    // TODO: Add author to the response
    // TODO: Find out about allocation-state and permanent-room-id

    private final ReservationService reservationService;

    public ReservationRequestController(@Autowired ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    @GetMapping("/list")
    Map<String, Object> listRequests(
            @RequestHeader("Authorization") String accessToken,
            @RequestParam(value = "start", required = false) Integer start,
            @RequestParam(value = "count", required = false) Integer count,
            @RequestParam(value = "sort", required = false,
                    defaultValue = "DATETIME") ReservationRequestListRequest.Sort sort,
            @RequestParam(value = "sort_desc", required = false, defaultValue = "true") boolean sortDescending,
            @RequestParam(value = "specification_type", required = false) Set<SpecificationType> specificationTypes,
            @RequestParam(value = "specification_technology", required = false) TechnologyModel specificationTechnology,
            @RequestParam(value = "interval_from", required = false) DateTime intervalFrom,
            @RequestParam(value = "interval_to", required = false) DateTime intervalTo,
            @RequestParam(value = "user_id", required = false) String userId,
            @RequestParam(value = "participant_user_id", required = false) String participantUserId,
            @RequestParam(value = "search", required = false) String search) {
        ReservationRequestListRequest request = new ReservationRequestListRequest();
        SecurityToken securityToken = new SecurityToken(accessToken.split("Bearer")[1].trim());

        request.setSecurityToken(securityToken);
        request.setStart(start);
        request.setCount(count);
        request.setSort(sort);
        request.setSortDescending(sortDescending);

        if (specificationTypes != null && specificationTypes.size() > 0) {
            if (specificationTypes.contains(SpecificationType.ADHOC_ROOM)) {
                request.addSpecificationType(ReservationRequestSummary.SpecificationType.ROOM);
            }
            if (specificationTypes.contains(SpecificationType.PERMANENT_ROOM)) {
                request.addSpecificationType(ReservationRequestSummary.SpecificationType.PERMANENT_ROOM);
            }
            if (specificationTypes.contains(SpecificationType.PERMANENT_ROOM_CAPACITY)) {
                request.addSpecificationType(ReservationRequestSummary.SpecificationType.USED_ROOM);
            }
            if (specificationTypes.contains(SpecificationType.MEETING_ROOM)) {
                request.addSpecificationType(ReservationRequestSummary.SpecificationType.RESOURCE);
            }
        }
        if (specificationTechnology != null) {
            request.setSpecificationTechnologies(specificationTechnology.getTechnologies());
        }
        if (intervalFrom != null || intervalTo != null) {
            if (intervalFrom == null) {
                intervalFrom = Temporal.DATETIME_INFINITY_START;
            }
            if (intervalTo == null) {
                intervalTo = Temporal.DATETIME_INFINITY_END;
            }
            if (!intervalFrom.isAfter(intervalTo)) {
                request.setInterval(new Interval(intervalFrom, intervalTo));
            }
        }
        if (userId != null) {
            if (UserInformation.isLocal(userId)) {
                request.setUserId(userId);
            }
        }
        if (participantUserId != null) {
            request.setParticipantUserId(participantUserId);
        }
        if (search != null) {
            request.setSearch(search);
        }

        ListResponse<ReservationRequestSummary> response = reservationService.listReservationRequests(request);

        List<Map<String, Object>> items = new LinkedList<>();
        for (ReservationRequestSummary reservationRequest : response.getItems()) {
            Map<String, Object> item = new HashMap<>();

            String reservationRequestId = reservationRequest.getId();
            Set<Technology> technologies = reservationRequest.getSpecificationTechnologies();
            TechnologyModel technology = TechnologyModel.find(technologies);
            Interval earliestSlot = reservationRequest.getEarliestSlot();
            ReservationRequestState state = ReservationRequestState.fromApi(reservationRequest);

            item.put("id", reservationRequestId);
            item.put("creationTime", reservationRequest.getDateTime().toString());
            item.put("description", reservationRequest.getDescription());
            item.put("participantCount", reservationRequest.getRoomParticipantCount());
            item.put("name", reservationRequest.getRoomName());
            item.put("technology", technology);
            item.put("slotStart", earliestSlot.getStart().toString());
            item.put("slotEnd", earliestSlot.getEnd().toString());
            item.put("state", state);

            items.add(item);
        }

        Map<String, Object> data = new HashMap<>();
        data.put("count", response.getCount());
        data.put("items", items);
        return data;
    }
}
