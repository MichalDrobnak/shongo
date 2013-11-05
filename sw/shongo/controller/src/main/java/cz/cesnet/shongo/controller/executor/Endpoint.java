package cz.cesnet.shongo.controller.executor;

import cz.cesnet.shongo.Technology;
import cz.cesnet.shongo.controller.CallInitiation;
import cz.cesnet.shongo.controller.Scheduler;
import cz.cesnet.shongo.controller.api.EndpointExecutable;
import cz.cesnet.shongo.controller.common.AbstractPerson;
import cz.cesnet.shongo.controller.resource.Address;
import cz.cesnet.shongo.controller.resource.Alias;
import cz.cesnet.shongo.controller.scheduler.SchedulerException;
import cz.cesnet.shongo.report.Report;

import javax.persistence.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Represents an entity (or multiple entities) which can participate in a {@link Compartment}.
 *
 * @author Martin Srom <martin.srom@cesnet.cz>
 */
@Entity
public abstract class Endpoint extends Executable
{
    /**
     * List of {@link cz.cesnet.shongo.controller.common.AbstractPerson}s which use the {@link Endpoint} in the {@link Compartment}.
     */
    private List<AbstractPerson> persons = new ArrayList<AbstractPerson>();

    /**
     * {@link Alias}es that are additionally assigned to the {@link Endpoint}.
     */
    private List<Alias> assignedAliases = new ArrayList<Alias>();

    /**
     * {@link EndpointService}s for this {@link Endpoint}.
     */
    private List<EndpointService> services = new ArrayList<EndpointService>();

    /**
     * @return {@link #persons}
     */
    @OneToMany(cascade = CascadeType.ALL)
    @Access(AccessType.FIELD)
    public List<AbstractPerson> getPersons()
    {
        return persons;
    }

    /**
     * @param person to be added to the {@link #persons}
     */
    public void addPerson(AbstractPerson person)
    {
        persons.add(person);
    }

    /**
     * @return {@link #assignedAliases}
     */
    @OneToMany(cascade = CascadeType.ALL)
    @Access(AccessType.FIELD)
    public List<Alias> getAssignedAliases()
    {
        return assignedAliases;
    }

    /**
     * @param assignedAlias alias to be added to the {@link #assignedAliases}
     */
    public void addAssignedAlias(Alias assignedAlias) throws SchedulerException
    {
        assignedAliases.add(assignedAlias);
    }

    /**
     * Remove all {@link #assignedAliases}
     */
    public void clearAssignedAliases()
    {
        assignedAliases.clear();
    }

    /**
     * @return {@link #services}
     */
    @OneToMany(cascade = CascadeType.ALL)
    @Access(AccessType.FIELD)
    public List<EndpointService> getServices()
    {
        return services;
    }

    /**
     * @param service to be added to the {@link #services}
     */
    public void addService(EndpointService service)
    {
        services.add(service);
    }

    /**
     * @return number of the endpoints which the {@link Endpoint} represents.
     */
    @Transient
    public int getCount()
    {
        return 1;
    }

    /**
     * @return set of technologies which are supported by the {@link Endpoint}
     */

    @Transient
    public abstract Set<Technology> getTechnologies();

    /**
     * @return {@link #assignedAliases}
     */
    @Transient
    public List<Alias> getAliases()
    {
        return assignedAliases;
    }

    /**
     * @return true if device can participate in 2-point video conference without virtual room,
     *         false otherwise
     */
    @Transient
    public boolean isStandalone()
    {
        return false;
    }

    /**
     * @return IP address or URL of the {@link Endpoint}
     */
    @Transient
    public Address getAddress()
    {
        return null;
    }

    /**
     * @return description of the {@link Endpoint}
     */
    @Transient
    public String getDescription()
    {
        return "endpoint " + getReportDescription();
    }

    /**
     * Defines who should initiate the call to this endpoint ({@code null} means that the {@link Scheduler}
     * can decide it).
     */
    @Transient
    public CallInitiation getCallInitiation()
    {
        return null;
    }

    @Override
    protected cz.cesnet.shongo.controller.api.Executable createApi()
    {
        return new EndpointExecutable();
    }

    @Override
    public void toApi(cz.cesnet.shongo.controller.api.Executable executableApi, Report.UserType userType)
    {
        super.toApi(executableApi, userType);

        if (executableApi instanceof EndpointExecutable) {
            EndpointExecutable endpointApi =
                    (EndpointExecutable) executableApi;
            endpointApi.setDescription(getDescription());
            for (Alias alias : getAssignedAliases()) {
                endpointApi.addAlias(alias.toApi());
            }
        }
    }
}
