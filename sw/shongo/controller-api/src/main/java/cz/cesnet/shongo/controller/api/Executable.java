package cz.cesnet.shongo.controller.api;

import cz.cesnet.shongo.api.DataMap;
import cz.cesnet.shongo.api.IdentifiedComplexType;
import org.joda.time.Interval;

/**
 * Represents an allocated object which can be executed.
 *
 * @author Martin Srom <martin.srom@cesnet.cz>
 */
public class Executable extends IdentifiedComplexType
{
    /**
     * Reservation for which the {@link Executable} is allocated.
     */
    private String reservationId;

    /**
     * Slot of the {@link cz.cesnet.shongo.controller.api.Executable}.
     */
    private Interval slot;

    /**
     * Current state of the {@link cz.cesnet.shongo.controller.api.Executable}.
     */
    private ExecutableState state;

    /**
     * {@link ExecutableStateReport} for the {@link #state}.
     */
    private ExecutableStateReport stateReport;

    /**
     * {@link Executable} is migrated to this {@link Executable}.
     */
    private Executable migratedExecutable;

    /**
     * @return {@link #reservationId}
     */
    public String getReservationId()
    {
        return reservationId;
    }

    /**
     * @param reservationId sets the {@link #reservationId}
     */
    public void setReservationId(String reservationId)
    {
        this.reservationId = reservationId;
    }

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
     * @return {@link #state}
     */
    public ExecutableState getState()
    {
        return state;
    }

    /**
     * @param state sets the {@link #state}
     */
    public void setState(ExecutableState state)
    {
        this.state = state;
    }

    /**
     * @return {@link #stateReport}
     */
    public ExecutableStateReport getStateReport()
    {
        return stateReport;
    }

    /**
     * @param stateReport sets the {@link #stateReport}
     */
    public void setStateReport(ExecutableStateReport stateReport)
    {
        this.stateReport = stateReport;
    }

    /**
     * @return {@link #migratedExecutable}
     */
    public Executable getMigratedExecutable()
    {
        return migratedExecutable;
    }

    /**
     * @param migratedExecutable sets the {@link #migratedExecutable}
     */
    public void setMigratedExecutable(Executable migratedExecutable)
    {
        this.migratedExecutable = migratedExecutable;
    }

    private static final String RESERVATION_ID = "reservationId";
    private static final String SLOT = "slot";
    private static final String STATE = "state";
    private static final String STATE_REPORT = "stateReport";
    private static final String MIGRATED_EXECUTABLE = "migratedExecutable";

    @Override
    public DataMap toData()
    {
        DataMap dataMap = super.toData();
        dataMap.set(RESERVATION_ID, reservationId);
        dataMap.set(SLOT, slot);
        dataMap.set(STATE, state);
        dataMap.set(STATE_REPORT, stateReport);
        dataMap.set(MIGRATED_EXECUTABLE, migratedExecutable);
        return dataMap;
    }

    @Override
    public void fromData(DataMap dataMap)
    {
        super.fromData(dataMap);
        reservationId = dataMap.getString(RESERVATION_ID);
        slot = dataMap.getInterval(SLOT);
        state = dataMap.getEnum(STATE, ExecutableState.class);
        stateReport = dataMap.getComplexType(STATE_REPORT, ExecutableStateReport.class);
        migratedExecutable = dataMap.getComplexType(MIGRATED_EXECUTABLE, Executable.class);
    }
}
