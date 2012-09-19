package cz.cesnet.shongo.controller.reservation;

import cz.cesnet.shongo.AbstractManager;
import cz.cesnet.shongo.controller.Cache;
import cz.cesnet.shongo.controller.request.AbstractReservationRequest;
import cz.cesnet.shongo.controller.request.ReservationRequest;
import org.joda.time.Interval;

import javax.persistence.EntityManager;
import javax.persistence.NoResultException;
import java.util.List;

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
     * @param Reservation to be deleted in the database
     */
    public void delete(Reservation Reservation, Cache cache)
    {
        // Remove all allocated virtual rooms from virtual rooms database
        List<Reservation> childReservations = Reservation.getChildReservations();
        for (Reservation childReservation : childReservations) {
            cache.removeReservation(childReservation);
        }
        super.delete(Reservation);
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
                    "SELECT reservation FROM Reservation reservation WHERE reservation.id IN("
                            + " SELECT reservationRequest.reservation.id FROM ReservationRequest reservationRequest"
                            + " WHERE reservationRequest.id = :id)", Reservation.class)
                    .setParameter("id", reservationRequestId)
                    .getSingleResult();
            return reservation;
        }
        catch (NoResultException exception) {
            return null;
        }
    }

    /**
     * @param reservationRequest from which the {@link Reservation}s should be
     *                           returned.
     * @return list of {@link Reservation}s from given {@code reservationRequest}
     */
    public List<Reservation> listByReservationRequest(AbstractReservationRequest reservationRequest)
    {
        return listByReservationRequest(reservationRequest.getId());
    }

    /**
     * @param reservationRequestId for {@link AbstractReservationRequest} from which the {@link Reservation}s should be
     *                             returned.
     * @return list of {@link Reservation}s from {@link AbstractReservationRequest} with
     *         given {@code reservationRequestId}
     */
    public List<Reservation> listByReservationRequest(Long reservationRequestId)
    {
        List<Reservation> reservations = entityManager.createQuery(
                "SELECT reservation FROM Reservation reservation"
                        + " WHERE reservation IN ("
                        + "   SELECT reservationRequest.reservation FROM ReservationRequestSet reservationRequestSet"
                        + "   LEFT JOIN reservationRequestSet.reservationRequests reservationRequest"
                        + "   WHERE reservationRequestSet.id = :id"
                        + " ) OR reservation IN ("
                        + "   SELECT reservationRequest.reservation FROM ReservationRequest reservationRequest"
                        + "   WHERE reservationRequest.id = :id"
                        + " )",
                Reservation.class)
                .setParameter("id", reservationRequestId)
                .getResultList();
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
                "SELECT reservation FROM " + reservationType.getSimpleName() + " reservation "
                        + "WHERE reservation.slotStart BETWEEN :start AND :end",
                reservationType)
                .setParameter("start", interval.getStart())
                .setParameter("end", interval.getEnd())
                .getResultList();
        return reservations;
    }

    /**
     * Delete {@link Reservation}s which aren't allocated for any {@link ReservationRequest}.
     *
     * @param cache from which the {@link Reservation}s are also deleted
     */
    public void deleteAllNotReferencedByReservationRequest(Cache cache)
    {
        List<Reservation> reservations = entityManager.createQuery(
                "SELECT reservation FROM Reservation reservation"
                        + " WHERE reservation.parentReservation IS NULL AND reservation NOT IN("
                        + " SELECT reservationRequest.reservation FROM ReservationRequest reservationRequest)",
                Reservation.class)
                .getResultList();
        for (Reservation reservation : reservations) {
            delete(reservation, cache);
        }
    }
}
