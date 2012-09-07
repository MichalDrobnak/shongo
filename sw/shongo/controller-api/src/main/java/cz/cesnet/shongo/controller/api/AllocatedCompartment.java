package cz.cesnet.shongo.controller.api;

import org.joda.time.Interval;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents an allocated {@link Compartment} from {@link ReservationRequest} for a single time slot.
 *
 * @author Martin Srom <martin.srom@cesnet.cz>
 */
public class AllocatedCompartment
{
    /**
     * Slot fot which the compartment is allocated.
     */
    private Interval slot;

    /**
     * Items which are allocated for a compartment.
     */
    private List<AllocatedItem> allocatedItems = new ArrayList<AllocatedItem>();

    /**
     * @return {@link #slot}
     */
    public Interval getSlot()
    {
        return slot;
    }

    /**
     * @param slot sets the {@link #slot}
     */
    public void setSlot(Interval slot)
    {
        this.slot = slot;
    }

    /**
     * @return {@link #allocatedItems}
     */
    public List<AllocatedItem> getAllocatedItems()
    {
        return allocatedItems;
    }

    /**
     * @param allocatedItems sets the {@link #allocatedItems}
     */
    public void setAllocatedItems(List<AllocatedItem> allocatedItems)
    {
        this.allocatedItems = allocatedItems;
    }

    /**
     * @param allocatedItem to be added to the {@link #allocatedItems}
     */
    public void addAllocatedItem(AllocatedItem allocatedItem)
    {
        allocatedItems.add(allocatedItem);
    }
}
