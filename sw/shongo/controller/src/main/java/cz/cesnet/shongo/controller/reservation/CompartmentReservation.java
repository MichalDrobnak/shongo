package cz.cesnet.shongo.controller.reservation;

import cz.cesnet.shongo.controller.Domain;
import cz.cesnet.shongo.controller.compartment.Compartment;

import javax.persistence.CascadeType;
import javax.persistence.Entity;
import javax.persistence.OneToOne;

/**
 * Represents a {@link Reservation} for a {@link Compartment}.
 *
 * @author Martin Srom <martin.srom@cesnet.cz>
 */
@Entity
public class CompartmentReservation extends Reservation
{
    /**
     * {@link Compartment} which is allocated by the {@link CompartmentReservation}.
     */
    private Compartment compartment;

    /**
     * @return {@link #compartment}
     */
    @OneToOne(cascade = CascadeType.ALL)
    public Compartment getCompartment()
    {
        return compartment;
    }

    /**
     * @param compartment sets the {@link #compartment}
     */
    public void setCompartment(Compartment compartment)
    {
        this.compartment = compartment;
    }
}
