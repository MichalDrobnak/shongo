package cz.cesnet.shongo.controller.scheduler;

import cz.cesnet.shongo.AliasType;
import cz.cesnet.shongo.Technology;
import cz.cesnet.shongo.controller.Cache;
import cz.cesnet.shongo.controller.CallInitiation;
import cz.cesnet.shongo.controller.Domain;
import cz.cesnet.shongo.controller.executor.Compartment;
import cz.cesnet.shongo.controller.executor.Endpoint;
import cz.cesnet.shongo.controller.executor.EndpointProvider;
import cz.cesnet.shongo.controller.request.ExistingEndpointSpecification;
import cz.cesnet.shongo.controller.request.ExternalEndpointSetSpecification;
import cz.cesnet.shongo.controller.request.Specification;
import cz.cesnet.shongo.controller.reservation.Reservation;
import cz.cesnet.shongo.controller.resource.*;
import cz.cesnet.shongo.TodoImplementException;
import cz.cesnet.shongo.report.Report;
import org.joda.time.Interval;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Tests for {@link CompartmentReservationTask}.
 *
 * @author Martin Srom <martin.srom@cesnet.cz>
 */
public class CompartmentReservationTaskTest
{
    @Before
    public void before() throws Exception
    {
        Domain.setLocalDomain(new Domain("test"));
    }

    @After
    public void after() throws Exception
    {
        Domain.setLocalDomain(null);
    }

    @Test
    public void testFailures() throws Exception
    {
        Cache cache = Cache.createTestingCache();
        ReservationTask.Context context = new ReservationTask.Context(cache, Interval.parse("2012/2013"));

        CompartmentReservationTask compartmentReservationTask;

        compartmentReservationTask = new CompartmentReservationTask(context);
        compartmentReservationTask.addChildReservation(
                new SimpleEndpointSpecification(new Technology[]{Technology.H323}));
        try {
            compartmentReservationTask.perform();
            Assert.fail("Exception about not enough requested ports should be thrown.");
        }
        catch (SchedulerException exception) {
        }

        compartmentReservationTask = new CompartmentReservationTask(context);
        compartmentReservationTask.addChildReservation(
                new SimpleEndpointSpecification(new Technology[]{Technology.H323}));
        compartmentReservationTask.addChildReservation(
                new SimpleEndpointSpecification(new Technology[]{Technology.SIP}));
        try {
            compartmentReservationTask.perform();
            Assert.fail("Exception about no available virtual room should be thrown.");
        }
        catch (SchedulerException exception) {
        }

        compartmentReservationTask = new CompartmentReservationTask(context);
        compartmentReservationTask.addChildReservation(
                new SimpleEndpointSpecification(true, new Technology[]{Technology.H323}));
        compartmentReservationTask.addChildReservation(
                new SimpleEndpointSpecification(true, new Technology[]{Technology.H323}));
        try {
            compartmentReservationTask.perform();
            Assert.fail("Exception about no alias available should be thrown.");
        }
        catch (SchedulerException exception) {
        }

        DeviceResource deviceResource = new DeviceResource();
        deviceResource.setAllocatable(true);
        deviceResource.addTechnology(Technology.H323);
        deviceResource.addTechnology(Technology.SIP);
        deviceResource.addCapability(new RoomProviderCapability(100));
        cache.addResource(deviceResource);

        compartmentReservationTask = new CompartmentReservationTask(context, CallInitiation.VIRTUAL_ROOM);
        compartmentReservationTask.addChildSpecification(
                new ExternalEndpointSetSpecification(Technology.H323, 3));
        try {
            compartmentReservationTask.perform();
            Assert.fail("Exception about cannot create.");
        }
        catch (SchedulerException exception) {
        }
    }

    @Test
    public void testNoRoom() throws Exception
    {
        ReservationTask.Context context = new ReservationTask.Context(new Cache(), Interval.parse("2012/2013"));
        CompartmentReservationTask compartmentReservationTask = new CompartmentReservationTask(context);
        compartmentReservationTask.addChildReservation(new SimpleEndpointSpecification(
                new Alias(AliasType.H323_E164, "950000001"), true, new Technology[]{Technology.H323}));
        compartmentReservationTask.addChildReservation(
                new SimpleEndpointSpecification(true, new Technology[]{Technology.H323}));
        Reservation reservation = compartmentReservationTask.perform();
        Assert.assertNotNull(reservation);
        Assert.assertEquals(2, reservation.getChildReservations().size());
        Assert.assertEquals(1, ((Compartment) reservation.getExecutable()).getConnections().size());
    }

    @Test
    public void testSingleRoom() throws Exception
    {
        Cache cache = Cache.createTestingCache();

        DeviceResource deviceResource = new DeviceResource();
        deviceResource.setAllocatable(true);
        deviceResource.addTechnology(Technology.H323);
        deviceResource.addTechnology(Technology.SIP);
        deviceResource.addCapability(new RoomProviderCapability(100));
        deviceResource.addCapability(new AliasProviderCapability("950000001", AliasType.H323_E164, true));
        deviceResource.addCapability(new AliasProviderCapability("950000001@cesnet.cz", AliasType.SIP_URI, true));
        cache.addResource(deviceResource);

        ReservationTask.Context context;
        CompartmentReservationTask compartmentReservationTask;
        Reservation reservation;

        context = new ReservationTask.Context(cache, Interval.parse("2012/2013"));
        compartmentReservationTask = new CompartmentReservationTask(context);
        compartmentReservationTask.addChildReservation(
                new SimpleEndpointSpecification(false, new Technology[]{Technology.H323}));
        compartmentReservationTask.addChildReservation(
                new SimpleEndpointSpecification(true, new Technology[]{Technology.H323}));
        reservation = compartmentReservationTask.perform();
        Assert.assertNotNull(reservation);
        Assert.assertEquals(4, reservation.getChildReservations().size());
        Assert.assertEquals(2, ((Compartment) reservation.getExecutable()).getConnections().size());

        context = new ReservationTask.Context(cache, Interval.parse("2012/2013"));
        compartmentReservationTask = new CompartmentReservationTask(context);
        compartmentReservationTask.addChildReservation(
                new SimpleEndpointSpecification(true, new Technology[]{Technology.H323}));
        compartmentReservationTask.addChildReservation(
                new SimpleEndpointSpecification(true, new Technology[]{Technology.SIP}));
        reservation = compartmentReservationTask.perform();
        Assert.assertNotNull(reservation);
        Assert.assertEquals(5, reservation.getChildReservations().size());
        Assert.assertEquals(2, ((Compartment) reservation.getExecutable()).getConnections().size());
    }

    @Test
    public void testSingleRoomFromMultipleEndpoints() throws Exception
    {
        try {
            Cache cache = Cache.createTestingCache();

            DeviceResource mcu = new DeviceResource();
            mcu.setAllocatable(true);
            mcu.addTechnology(Technology.H323);
            mcu.addCapability(new RoomProviderCapability(100));
            mcu.addCapability(new AliasProviderCapability("95{digit:1}", AliasType.H323_E164));
            cache.addResource(mcu);

            DeviceResource terminal = new DeviceResource();
            terminal.setAllocatable(true);
            terminal.setTechnology(Technology.H323);
            terminal.addCapability(new StandaloneTerminalCapability());
            cache.addResource(terminal);

            ReservationTask.Context context = new ReservationTask.Context(cache, Interval.parse("2012/2013"));
            CompartmentReservationTask compartmentReservationTask = new CompartmentReservationTask(context);
            compartmentReservationTask.addChildSpecification(new ExternalEndpointSetSpecification(Technology.H323, 50));
            compartmentReservationTask.addChildSpecification(new ExistingEndpointSpecification(terminal));
            Reservation reservation = compartmentReservationTask.perform();
            Assert.assertEquals(3, reservation.getChildReservations().size());
            Assert.assertEquals(2, ((Compartment) reservation.getExecutable()).getEndpoints().size());
            Assert.assertEquals(1, ((Compartment) reservation.getExecutable()).getRoomEndpoints().size());
        }
        catch (Exception exception) {
            exception.printStackTrace();
            throw exception;
        }
    }

    @Test
    public void testAliasAllocation() throws Exception
    {
        Cache cache = Cache.createTestingCache();

        DeviceResource deviceResource = new DeviceResource();
        deviceResource.setAllocatable(true);
        deviceResource.addTechnology(Technology.H323);
        deviceResource.addTechnology(Technology.SIP);
        deviceResource.addCapability(new RoomProviderCapability(100));
        cache.addResource(deviceResource);

        Resource resource = new Resource();
        resource.setAllocatable(true);
        resource.addCapability(new AliasProviderCapability("95{digit:1}", AliasType.H323_E164));
        resource.addCapability(new AliasProviderCapability("001@cesnet.cz", AliasType.SIP_URI));
        cache.addResource(resource);

        ReservationTask.Context context;
        CompartmentReservationTask compartmentReservationTask;
        Reservation reservation;

        context = new ReservationTask.Context(cache, Interval.parse("2012/2013"));
        compartmentReservationTask = new CompartmentReservationTask(context, CallInitiation.TERMINAL);
        compartmentReservationTask.addChildSpecification(
                new SimpleEndpointSpecification(new Technology[]{Technology.H323}));
        compartmentReservationTask.addChildSpecification(
                new SimpleEndpointSpecification(new Technology[]{Technology.H323}));
        reservation = compartmentReservationTask.perform();
        Assert.assertNotNull(reservation);
        Assert.assertEquals(4, reservation.getChildReservations().size());
        Assert.assertEquals(2, ((Compartment) reservation.getExecutable()).getConnections().size());

        context = new ReservationTask.Context(cache, Interval.parse("2012/2013"));
        compartmentReservationTask = new CompartmentReservationTask(context, CallInitiation.VIRTUAL_ROOM);
        compartmentReservationTask.addChildSpecification(
                new SimpleEndpointSpecification(new Technology[]{Technology.H323}));
        compartmentReservationTask.addChildSpecification(
                new SimpleEndpointSpecification(new Technology[]{Technology.H323}));
        reservation = compartmentReservationTask.perform();
        Assert.assertNotNull(reservation);
        Assert.assertEquals(5, reservation.getChildReservations().size());
        Assert.assertEquals(2, ((Compartment) reservation.getExecutable()).getConnections().size());

        try {
            context = new ReservationTask.Context(cache, Interval.parse("2012/2013"));
            compartmentReservationTask = new CompartmentReservationTask(context, CallInitiation.VIRTUAL_ROOM);
            compartmentReservationTask.addChildSpecification(
                    new SimpleEndpointSpecification(new Technology[]{Technology.SIP}));
            compartmentReservationTask.addChildSpecification(
                    new SimpleEndpointSpecification(new Technology[]{Technology.SIP}));
            compartmentReservationTask.perform();
            Assert.fail("Only one SIP alias should be possible to allocate.");
        }
        catch (SchedulerException exception) {
        }
    }

    @Test
    public void testDependentResource() throws Exception
    {
        Cache cache = Cache.createTestingCache();

        Resource room = new Resource();
        room.setAllocatable(true);
        cache.addResource(room);

        DeviceResource terminal1 = new DeviceResource();
        terminal1.setParentResource(room);
        terminal1.setAllocatable(true);
        terminal1.addTechnology(Technology.H323);
        StandaloneTerminalCapability terminalCapability = new StandaloneTerminalCapability();
        terminalCapability.addAlias(new Alias(AliasType.H323_E164, "950000001"));
        terminal1.addCapability(terminalCapability);
        cache.addResource(terminal1);

        DeviceResource terminal2 = new DeviceResource();
        terminal2.setParentResource(room);
        terminal2.setAllocatable(true);
        terminal2.addTechnology(Technology.H323);
        terminal2.addCapability(new StandaloneTerminalCapability());
        cache.addResource(terminal2);

        ReservationTask.Context context;
        CompartmentReservationTask compartmentReservationTask;
        Reservation reservation;

        context = new ReservationTask.Context(cache, Interval.parse("2012/2013"));
        compartmentReservationTask = new CompartmentReservationTask(context);
        compartmentReservationTask.addChildSpecification(new ExistingEndpointSpecification(terminal1));
        reservation = compartmentReservationTask.perform();
        Assert.assertNotNull(reservation);
        Assert.assertEquals(2, reservation.getNestedReservations().size());
        Assert.assertEquals(0, ((Compartment) reservation.getExecutable()).getConnections().size());

        context = new ReservationTask.Context(cache, Interval.parse("2012/2013"));
        compartmentReservationTask = new CompartmentReservationTask(context);
        compartmentReservationTask.addChildSpecification(new ExistingEndpointSpecification(terminal1));
        compartmentReservationTask.addChildSpecification(new ExistingEndpointSpecification(terminal2));
        reservation = compartmentReservationTask.perform();
        Assert.assertNotNull(reservation);
        Assert.assertEquals(3, reservation.getNestedReservations().size());
        Assert.assertEquals(1, ((Compartment) reservation.getExecutable()).getConnections().size());
    }

    @Test
    public void testEndpointUniqueness() throws Exception
    {
        Cache cache = Cache.createTestingCache();

        DeviceResource endpoint = new DeviceResource();
        endpoint.setAllocatable(true);
        endpoint.addTechnology(Technology.H323);
        endpoint.addCapability(new StandaloneTerminalCapability());
        cache.addResource(endpoint);

        try {
            ReservationTask.Context context = new ReservationTask.Context(cache, Interval.parse("2012/2013"));
            CompartmentReservationTask compartmentReservationTask = new CompartmentReservationTask(context);
            compartmentReservationTask.addChildSpecification(new ExistingEndpointSpecification(endpoint));
            compartmentReservationTask.addChildSpecification(new ExistingEndpointSpecification(endpoint));
            Reservation reservation = compartmentReservationTask.perform();
            Assert.fail("Exception that resource is requested multiple times should be thrown");
        }
        catch (SchedulerException exception) {
            Assert.assertEquals(SchedulerReportSet.ResourceMultipleRequestedReport.class,
                    exception.getReport().getChildReports().get(0).getClass());
        }
    }

    private static class SimpleEndpointSpecification extends Specification implements ReservationTaskProvider
    {
        private Alias alias = null;
        private boolean standalone = false;
        private Set<Technology> technologies = new HashSet<Technology>();

        public SimpleEndpointSpecification(Alias alias, boolean standalone, Technology[] technologies)
        {
            this.alias = alias;
            this.standalone = standalone;
            for (Technology technology : technologies) {
                this.technologies.add(technology);
            }
        }

        public SimpleEndpointSpecification(boolean standalone, Technology[] technologies)
        {
            this.standalone = standalone;
            for (Technology technology : technologies) {
                this.technologies.add(technology);
            }
        }

        public SimpleEndpointSpecification(Technology[] technologies)
        {
            this(false, technologies);
        }


        @Override
        public ReservationTask createReservationTask(ReservationTask.Context context)
        {
            return new ReservationTask(context)
            {
                class SimpleEndpointReservation extends Reservation implements EndpointProvider
                {
                    private Endpoint endpoint = new Endpoint()
                    {
                        @Override
                        public int getCount()
                        {
                            return 1;
                        }

                        @Override
                        public Set<Technology> getTechnologies()
                        {
                            return technologies;
                        }

                        @Override
                        public boolean isStandalone()
                        {
                            return standalone;
                        }

                        @Override
                        public void addAssignedAlias(Alias alias) throws SchedulerException
                        {
                        }

                        @Override
                        public List<Alias> getAliases()
                        {
                            List<Alias> aliases = new ArrayList<Alias>();
                            if (alias != null) {
                                aliases.add(alias);
                            }
                            return aliases;
                        }

                        @Override
                        public String getReportDescription(Report.MessageType messageType)
                        {
                            return "simple endpoint";
                        }
                    };

                    @Override
                    public Endpoint getEndpoint()
                    {
                        return endpoint;
                    }

                    @Override
                    protected cz.cesnet.shongo.controller.api.Reservation createApi()
                    {
                        throw new TodoImplementException();
                    }
                }

                @Override
                protected Reservation createReservation() throws SchedulerException
                {
                    return new SimpleEndpointReservation();
                }
            };
        }

        @Override
        protected cz.cesnet.shongo.controller.api.Specification createApi()
        {
            throw new RuntimeException("Not implemented.");
        }
    }
}
