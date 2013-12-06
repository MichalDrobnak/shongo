    package cz.cesnet.shongo.controller.booking.room;

import cz.cesnet.shongo.Technology;
import cz.cesnet.shongo.api.Room;
import cz.cesnet.shongo.controller.booking.EntityIdentifier;
import cz.cesnet.shongo.controller.booking.resource.DeviceResource;
import cz.cesnet.shongo.controller.executor.Executor;
import cz.cesnet.shongo.controller.Reporter;
import cz.cesnet.shongo.controller.api.UsedRoomExecutable;
import cz.cesnet.shongo.controller.booking.room.settting.RoomSetting;
import cz.cesnet.shongo.controller.booking.executable.Executable;
import cz.cesnet.shongo.controller.booking.executable.ExecutableManager;
import cz.cesnet.shongo.controller.booking.executable.ManagedEndpoint;
import cz.cesnet.shongo.controller.executor.*;
import cz.cesnet.shongo.controller.booking.resource.Address;
import cz.cesnet.shongo.controller.booking.alias.Alias;
import cz.cesnet.shongo.TodoImplementException;
import cz.cesnet.shongo.controller.booking.resource.Resource;
import cz.cesnet.shongo.controller.scheduler.SchedulerException;
import cz.cesnet.shongo.report.Report;

import javax.persistence.*;
import java.util.*;

    /**
 * Represents a re-used {@link RoomEndpoint} for different
 * {@link RoomConfiguration}.
 *
 * @author Martin Srom <martin.srom@cesnet.cz>
 */
@Entity
public class UsedRoomEndpoint extends RoomEndpoint implements ManagedEndpoint, Reporter.ResourceContext
{
    /**
     * {@link RoomEndpoint} which is re-used.
     */
    private RoomEndpoint reusedRoomEndpoint;

    /**
     * Specifies whether {@link #onStop} is active.
     */
    private boolean isStopping;

    /**
     * Constructor.
     */
    public UsedRoomEndpoint()
    {
    }

    /**
     * @return {@link #reusedRoomEndpoint}
     */
    @OneToOne
    @Access(AccessType.FIELD)
    public RoomEndpoint getReusedRoomEndpoint()
    {
        return reusedRoomEndpoint;
    }

    /**
     * @param roomEndpoint sets the {@link #reusedRoomEndpoint}
     */
    public void setReusedRoomEndpoint(RoomEndpoint roomEndpoint)
    {
        this.reusedRoomEndpoint = roomEndpoint;
    }

    /**
     * @return merged {@link RoomConfiguration} of {@link #roomConfiguration} and {@link #reusedRoomEndpoint#roomConfiguration}
     */
    @Transient
    private RoomConfiguration getMergedRoomConfiguration()
    {
        RoomConfiguration roomConfiguration = getRoomConfiguration();
        RoomConfiguration roomEndpointConfiguration = reusedRoomEndpoint.getRoomConfiguration();
        RoomConfiguration mergedRoomConfiguration = new RoomConfiguration();
        mergedRoomConfiguration.setLicenseCount(
                roomConfiguration.getLicenseCount() + roomEndpointConfiguration.getLicenseCount());
        mergedRoomConfiguration.setTechnologies(roomConfiguration.getTechnologies());
        for (RoomSetting roomSetting : roomEndpointConfiguration.getRoomSettings()) {
            mergedRoomConfiguration.addRoomSetting(roomSetting);
        }
        for (RoomSetting roomSetting : roomConfiguration.getRoomSettings()) {
            mergedRoomConfiguration.addRoomSetting(roomSetting);
        }
        return mergedRoomConfiguration;
    }

    @Override
    @Transient
    public Collection<Executable> getExecutionDependencies()
    {
        List<Executable> dependencies = new ArrayList<Executable>();
        dependencies.add(reusedRoomEndpoint);
        return dependencies;
    }

    @Override
    @Transient
    public Resource getResource()
    {
        if (reusedRoomEndpoint instanceof ResourceRoomEndpoint) {
            ResourceRoomEndpoint resourceRoomEndpoint = (ResourceRoomEndpoint) reusedRoomEndpoint;
            return resourceRoomEndpoint.getResource();
        }
        else {
            throw new TodoImplementException(reusedRoomEndpoint.getClass());
        }
    }

    @Override
    protected cz.cesnet.shongo.controller.api.Executable createApi()
    {
        return new UsedRoomExecutable();
    }

    @Override
    public void toApi(cz.cesnet.shongo.controller.api.Executable executableApi, Report.UserType userType)
    {
        super.toApi(executableApi, userType);

        UsedRoomExecutable usedRoomExecutableEndpointApi =
                (UsedRoomExecutable) executableApi;

        usedRoomExecutableEndpointApi.setReusedRoomExecutableId(EntityIdentifier.formatId(reusedRoomEndpoint));

        RoomConfiguration roomConfiguration = getMergedRoomConfiguration();
        usedRoomExecutableEndpointApi.setLicenseCount(roomConfiguration.getLicenseCount());
        for (Technology technology : roomConfiguration.getTechnologies()) {
            usedRoomExecutableEndpointApi.addTechnology(technology);
        }
        for (Alias alias : getAliases()) {
            usedRoomExecutableEndpointApi.addAlias(alias.toApi());
        }
        for (RoomSetting roomSetting : roomConfiguration.getRoomSettings()) {
            usedRoomExecutableEndpointApi.addRoomSetting(roomSetting.toApi());
        }
    }

    @Transient
    @Override
    public int getEndpointServiceCount()
    {
        return super.getEndpointServiceCount() + reusedRoomEndpoint.getEndpointServiceCount();
    }

    @Transient
    @Override
    public DeviceResource getDeviceResource()
    {
        return reusedRoomEndpoint.getDeviceResource();
    }

    @Override
    @Transient
    public String getRoomId()
    {
        return reusedRoomEndpoint.getRoomId();
    }

    @Override
    @Transient
    public boolean isStandalone()
    {
        return reusedRoomEndpoint.isStandalone();
    }

    @Override
    @Transient
    public List<Alias> getAliases()
    {
        List<Alias> aliases = new ArrayList<Alias>();
        aliases.addAll(reusedRoomEndpoint.getAliases());
        aliases.addAll(super.getAssignedAliases());
        return aliases;
    }

    @Override
    public void addAssignedAlias(Alias assignedAlias) throws SchedulerException
    {
        super.addAssignedAlias(assignedAlias);
    }

    @Override
    @Transient
    public Address getAddress()
    {
        return reusedRoomEndpoint.getAddress();
    }

    @Override
    @Transient
    public String getReportDescription()
    {
        return reusedRoomEndpoint.getReportDescription();
    }

    @Override
    @Transient
    public String getConnectorAgentName()
    {
        if (reusedRoomEndpoint instanceof ManagedEndpoint) {
            ManagedEndpoint managedEndpoint = (ManagedEndpoint) reusedRoomEndpoint;
            return managedEndpoint.getConnectorAgentName();
        }
        return null;
    }

    @Override
    public void fillRoomApi(Room roomApi, ExecutableManager executableManager)
    {
        super.fillRoomApi(roomApi, executableManager);

        // Use reused room configuration
        reusedRoomEndpoint.fillRoomApi(roomApi, executableManager);

        // Modify the room configuration (only when we aren't stopping the reused room)
        if (!isStopping) {
            RoomConfiguration roomConfiguration = getMergedRoomConfiguration();
            roomApi.setDescription(getRoomDescriptionApi());
            roomApi.setLicenseCount(roomConfiguration.getLicenseCount() + getEndpointServiceCount());
            for (RoomSetting roomSetting : roomConfiguration.getRoomSettings()) {
                roomApi.addRoomSetting(roomSetting.toApi());
            }
            for (Alias alias : getAssignedAliases()) {
                roomApi.addAlias(alias.toApi());
            }
        }
    }

    @Override
    public void modifyRoom(Room roomApi, Executor executor)
            throws ExecutionReportSet.RoomNotStartedException, ExecutionReportSet.CommandFailedException
    {
        reusedRoomEndpoint.modifyRoom(roomApi, executor);
    }

    @Override
    protected Executable.State onStart(Executor executor, ExecutableManager executableManager)
    {
        try {
            modifyRoom(getRoomApi(executableManager), executor);
            return Executable.State.STARTED;
        }
        catch (ExecutionReportSet.RoomNotStartedException exception) {
            executableManager.createExecutionReport(this, exception.getReport());
        }
        catch (ExecutionReportSet.CommandFailedException exception) {
            executableManager.createExecutionReport(this, exception.getReport());
        }
        return Executable.State.STARTING_FAILED;
    }

    @Override
    protected Executable.State onUpdate(Executor executor, ExecutableManager executableManager)
    {
        try {
            modifyRoom(getRoomApi(executableManager), executor);
            return Executable.State.STARTED;
        }
        catch (ExecutionReportSet.RoomNotStartedException exception) {
            executableManager.createExecutionReport(this, exception.getReport());
        }
        catch (ExecutionReportSet.CommandFailedException exception) {
            executableManager.createExecutionReport(this, exception.getReport());
        }
        return null;
    }

    @Override
    protected Executable.State onStop(Executor executor, ExecutableManager executableManager)
    {
        isStopping = true;
        try {
            modifyRoom(getRoomApi(executableManager), executor);
            return Executable.State.STOPPED;
        }
        catch (ExecutionReportSet.RoomNotStartedException exception) {
            executableManager.createExecutionReport(this, exception.getReport());
        }
        catch (ExecutionReportSet.CommandFailedException exception) {
            executableManager.createExecutionReport(this, exception.getReport());
        }
        finally {
            isStopping = false;
        }
        return Executable.State.STOPPING_FAILED;
    }
}
