package cz.cesnet.shongo.controller.rest.api;

import cz.cesnet.shongo.controller.api.ReservationRequestSummary;
import cz.cesnet.shongo.controller.api.SecurityToken;
import cz.cesnet.shongo.controller.api.request.ListResponse;
import cz.cesnet.shongo.controller.api.request.ReservationRequestListRequest;
import cz.cesnet.shongo.controller.api.rpc.ReservationService;
import cz.cesnet.shongo.controller.rest.models.TechnologyModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.joda.time.DateTime;
import java.util.Set;

/**
 * @author Michal Drobňák
 */
@RestController
@RequestMapping("/api/v1/reservation_request")
public class ReservationRequestController {

    private final ReservationService reservationService;

    public ReservationRequestController(@Autowired ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    @GetMapping("/list")
    String listRequests(
            @RequestHeader("Authorization") String accessToken,
            @RequestParam(value = "start", required = false) Integer start,
            @RequestParam(value = "count", required = false) Integer count,
            @RequestParam(value = "sort", required = false,
                    defaultValue = "DATETIME") ReservationRequestListRequest.Sort sort,
            @RequestParam(value = "sort_desc", required = false, defaultValue = "true") boolean sortDescending,
            @RequestParam(value = "specification_type", required = false) Set<ReservationRequestSummary.SpecificationType> specificationTypes,
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

        ListResponse<ReservationRequestSummary> response = reservationService.listReservationRequests(request);
        return response.toString();
    }
}
