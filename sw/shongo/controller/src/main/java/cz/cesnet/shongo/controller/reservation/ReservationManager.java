package cz.cesnet.shongo.controller.reservation;

import cz.cesnet.shongo.AbstractManager;
import cz.cesnet.shongo.Technology;
import cz.cesnet.shongo.controller.Cache;
import cz.cesnet.shongo.controller.executor.Compartment;
import cz.cesnet.shongo.controller.executor.Executable;
import cz.cesnet.shongo.controller.executor.ExecutableManager;
import cz.cesnet.shongo.controller.request.ReservationRequest;
import cz.cesnet.shongo.controller.resource.Alias;
import cz.cesnet.shongo.controller.util.DatabaseFilter;
import cz.cesnet.shongo.fault.EntityNotFoundException;
import cz.cesnet.shongo.fault.TodoImplementException;
import org.joda.time.DateTime;
import org.joda.time.DateTimeFieldType;
import org.joda.time.Interval;

import javax.persistence.EntityManager;
import javax.persistence.NoResultException;
import javax.persistence.TypedQuery;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * Manager for {@link Reservation}.
 *
 * @author Martin Srom <martin.srom@cesnet.cz>
 */
public class ReservationManager extends AbstractManager
{
    /**
     * @param entityManager sets the {@link #entityManager}
     */
    public ReservationManager(EntityManager entityManager)
    {
        super(entityManager);
    }

    /**
     * @param reservation to be created in the database
     */
    public void create(Reservation reservation)
    {
        super.create(reservation);
    }

    /**
     * @param reservation to be updated in the database
     */
    public void update(Reservation reservation)
    {
        super.update(reservation);
    }

    /**
     * @param reservation to be deleted in the database
     */
    public void delete(Reservation reservation, Cache cache)
    {
        Executable executable = reservation.getExecutable();
        if (executable != null) {
            ExecutableManager executableManager = new ExecutableManager(entityManager);
            if (executable.getState().equals(Compartment.State.STARTED)) {
                if (executable.getSlotEnd().isAfter(DateTime.now())) {
                    DateTime newSlotEnd = DateTime.now().withField(DateTimeFieldType.millisOfSecond(), 0);
                    if (newSlotEnd.isBefore(executable.getSlotStart())) {
                        newSlotEnd = executable.getSlotStart();
                    }
                    executable.setSlotEnd(newSlotEnd);
                    executableManager.update(executable);
                }
            }
        }
        // Remove reservation from cache
        cache.removeReservation(reservation);
        // Remove also all child reservations
        List<Reservation> childReservations = reservation.getChildReservations();
        for (Reservation childReservation : childReservations) {
            cache.removeReservation(childReservation);
        }
        super.delete(reservation);
    }

    /**
     * @param reservationId of the {@link Reservation}
     * @return {@link Reservation} with given id
     * @throws EntityNotFoundException when the {@link Reservation} doesn't exist
     */
    public Reservation get(Long reservationId) throws EntityNotFoundException
    {
        try {
            Reservation reservation = entityManager.createQuery(
                    "SELECT reservation FROM Reservation reservation"
                            + " WHERE reservation.id = :id",
                    Reservation.class).setParameter("id", reservationId)
                    .getSingleResult();
            return reservation;
        }
        catch (NoResultException exception) {
            throw new EntityNotFoundException(Reservation.class, reservationId);
        }
    }

    /**
     * @param reservationRequest for which the {@link Reservation} should be returned
     * @return {@link Reservation} for the given {@link ReservationRequest} or null if doesn't exists
     */
    public Reservation getByReservationRequest(ReservationRequest reservationRequest)
    {
        return getByReservationRequest(reservationRequest.getId());
    }

    /**
     * @param reservationRequestId of the {@link ReservationRequest} for which the {@link Reservation} should be returned
     * @return {@link Reservation} for the given {@link ReservationRequest} or null if doesn't exists
     */
    public Reservation getByReservationRequest(Long reservationRequestId)
    {
        try {
            Reservation reservation = entityManager.createQuery(
                    "SELECT reservation FROM Reservation reservation WHERE reservation.reservationRequest.id = :id",
                    Reservation.class)
                    .setParameter("id", reservationRequestId)
                    .getSingleResult();
            return reservation;
        }
        catch (NoResultException exception) {
            return null;
        }
    }

    /**
     * @param userId
     * @param reservationClasses
     * @param technologies
     * @return list of {@link Reservation}s
     */
    public List<Reservation> list(String userId, Long reservationRequestId,
            Set<Class<? extends Reservation>> reservationClasses, Set<Technology> technologies)
    {
        DatabaseFilter filter = new DatabaseFilter("reservation");
        filter.addUserId(userId);
        if (reservationClasses != null && reservationClasses.size() > 0) {
            // List only reservations of given classes
            filter.addFilter("TYPE(reservation) IN(:classes)");
            filter.addFilterParameter("classes", reservationClasses);
        }
        if (reservationRequestId != null) {
            // List only reservations which are allocated for request with given id
            filter.addFilter("reservation IN ("
                    + "   SELECT reservation FROM ReservationRequestSet reservationRequestSet"
                    + "   LEFT JOIN reservationRequestSet.reservationRequests reservationRequest"
                    + "   LEFT JOIN reservationRequest.reservations reservation"
                    + "   WHERE reservationRequestSet.id = :reservationRequestId"
                    + " ) OR reservation IN ("
                    + "   SELECT reservation FROM AbstractReservationRequest reservationRequest"
                    + "   LEFT JOIN reservationRequest.reservations reservation"
                    + "   WHERE reservationRequest.id = :reservationRequestId"
                    + " )");
            filter.addFilterParameter("reservationRequestId", reservationRequestId);
        }

        TypedQuery<Reservation> query = entityManager.createQuery(
                "SELECT reservation FROM Reservation reservation"
                        + " WHERE reservation.parentReservation IS NULL AND " + filter.toQueryWhere(),
                Reservation.class);
        filter.fillQueryParameters(query);
        List<Reservation> reservations = query.getResultList();

        if (technologies != null && technologies.size() > 0) {
            Iterator<Reservation> iterator = reservations.iterator();
            while (iterator.hasNext()) {
                Reservation reservation = iterator.next();
                if (reservation instanceof AliasReservation) {
                    AliasReservation aliasReservation = (AliasReservation) reservation;
                    boolean technologyFound = false;
                    for (Alias alias : aliasReservation.getAliases()) {
                        if (technologies.contains(alias.getTechnology())) {
                            technologyFound = true;
                            break;
                        }
                    }
                    if (!technologyFound) {
                        iterator.remove();
                    }
                }
                else {
                    throw new TodoImplementException();
                }
            }
        }
        return reservations;
    }

    /**
     * @param interval        in which the requested {@link Reservation}s should start
     * @param reservationType type of requested {@link Reservation}s
     * @return list of {@link Reservation}s starting in given {@code interval}
     */
    public <R extends Reservation> List<R> listByInterval(Interval interval, Class<R> reservationType)
    {
        List<R> reservations = entityManager.createQuery(
                "SELECT reservation FROM " + reservationType.getSimpleName() + " reservation"
                        + " WHERE reservation.slotStart BETWEEN :start AND :end",
                reservationType)
                .setParameter("start", interval.getStart())
                .setParameter("end", interval.getEnd())
                .getResultList();
        return reservations;
    }

    /**
     * Get list of reused {@link Reservation}s. Reused {@link Reservation} is a {@link Reservation} which is referenced
     * by at least one {@link ExistingReservation} in the {@link ExistingReservation#reservation} attribute.
     *
     * @return list of reused {@link Reservation}.
     */
    public List<Reservation> getReusedReservations()
    {
        List<Reservation> reservations = entityManager.createQuery(
                "SELECT DISTINCT reservation.reservation FROM ExistingReservation reservation", Reservation.class)
                .getResultList();
        return reservations;
    }

    /**
     * Get list of {@link ExistingReservation} which reuse the given {@code reusedReservation}.
     *
     * @param reusedReservation which must be referenced in the {@link ExistingReservation#reservation}
     * @return list of {@link ExistingReservation} which reuse the given {@code reusedReservation}
     */
    public List<ExistingReservation> getExistingReservations(Reservation reusedReservation)
    {
        List<ExistingReservation> reservations = entityManager.createQuery(
                "SELECT reservation FROM ExistingReservation reservation"
                        + " WHERE reservation.reservation = :reusedReservation",
                ExistingReservation.class)
                .setParameter("reusedReservation", reusedReservation)
                .getResultList();
        return reservations;
    }

    /**
     * Delete {@link Reservation}s which aren't allocated for any {@link ReservationRequest}.
     *
     * @return list of deleted {@link Reservation}
     */
    public List<Reservation> getReservationsForDeletion()
    {
        List<Reservation> reservations = entityManager.createQuery(
                "SELECT reservation FROM Reservation reservation"
                + " LEFT JOIN reservation.reservationRequest reservationRequest"
                        + " WHERE reservation.createdBy = :createdBy"
                        + " AND reservation.parentReservation IS NULL"
                        + " AND (reservationRequest IS NULL OR reservationRequest.state != :state)",
                Reservation.class)
                .setParameter("createdBy", Reservation.CreatedBy.CONTROLLER)
                .setParameter("state", ReservationRequest.State.ALLOCATED)
                .getResultList();
        return reservations;
    }

    /**
     * @param reservation to be checked if it is provided to any {@link ReservationRequest} or {@link Reservation}
     * @return true if given {@code reservation} is provided to any other {@link ReservationRequest},
     *         false otherwise
     */
    public boolean isProvided(Reservation reservation)
    {
        // Checks whether reservation isn't referenced in existing reservations
        List reservations = entityManager.createQuery(
                "SELECT reservation.id FROM ExistingReservation reservation"
                        + " WHERE reservation.reservation = :reservation")
                .setParameter("reservation", reservation)
                .getResultList();
        if (reservations.size() > 0) {
            return false;
        }
        // Checks whether reservation isn't referenced in existing reservation requests
        List reservationRequests = entityManager.createQuery(
                "SELECT reservationRequest.id FROM AbstractReservationRequest reservationRequest"
                        + " WHERE :reservation MEMBER OF reservationRequest.providedReservations")
                .setParameter("reservation", reservation)
                .getResultList();
        if (reservationRequests.size() > 0) {
            return false;
        }
        return true;
    }
}
