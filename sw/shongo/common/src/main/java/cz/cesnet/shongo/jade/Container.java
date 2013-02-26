package cz.cesnet.shongo.jade;

import cz.cesnet.shongo.fault.jade.CommandAgentNotStarted;
import cz.cesnet.shongo.jade.command.Command;
import cz.cesnet.shongo.util.Logging;
import cz.cesnet.shongo.util.ThreadHelper;
import jade.content.ContentElement;
import jade.content.lang.sl.SLCodec;
import jade.content.onto.Ontology;
import jade.content.onto.basic.Action;
import jade.content.onto.basic.Result;
import jade.core.ContainerID;
import jade.core.Profile;
import jade.core.ProfileImpl;
import jade.core.Runtime;
import jade.domain.FIPANames;
import jade.domain.JADEAgentManagement.JADEManagementOntology;
import jade.domain.JADEAgentManagement.QueryPlatformLocationsAction;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.wrapper.AgentContainer;
import jade.wrapper.AgentController;
import jade.wrapper.StaleProxyException;
import jade.wrapper.State;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Represents a container in JADE middle-ware.
 *
 * @author Martin Srom <martin.srom@cesnet.cz>
 */
public class Container
{
    private static Logger logger = LoggerFactory.getLogger(Container.class);

    /**
     * Configuration for container in JADE middle-ware.
     */
    private Profile profile;

    /**
     * Controller for container in JADE middle-ware.
     */
    private AgentContainer containerController;

    /**
     * Container agents.
     */
    private Map<String, Object> agents = new HashMap<String, Object>();

    /**
     * Arguments agents.
     */
    private Map<String, Object[]> agentsArguments = new HashMap<String, Object[]>();

    /**
     * Container agents controllers.
     */
    private Map<String, AgentController> agentControllers = new HashMap<String, AgentController>();

    /**
     * Construct JADE container.
     *
     * @param profile
     */
    private Container(Profile profile)
    {
        this.profile = profile;
    }

    /**
     * Create a container in JADE middle-ware.
     *
     * @param profile
     * @return jade container
     */
    private static Container createContainer(Profile profile)
    {
        // Setup container profile
        profile.setParameter(Profile.FILE_DIR, "data/jade/");
        // Create directory if not exits
        new java.io.File("data/jade").mkdir();

        return new Container(profile);
    }

    /**
     * Create a main container in JADE middle-ware. Each main container must run on a local host and port,
     * must have assigned a platform id.
     *
     * @param localHost
     * @param localPort
     * @param platformId
     * @return jade container
     */
    public static Container createMainContainer(String localHost, int localPort, String platformId)
    {
        jade.core.Profile profile = new jade.core.ProfileImpl();
        profile.setParameter(Profile.MAIN, "true");
        profile.setParameter(Profile.LOCAL_HOST, localHost);
        profile.setParameter(Profile.LOCAL_PORT, Integer.toString(localPort));
        profile.setParameter(Profile.PLATFORM_ID, platformId);
        return createContainer(profile);
    }

    /**
     * Create an agent (slave) container in JADE middle-ware. Each agent container must run on a local host
     * and port, must connect to main container on a remote host and port.
     *
     * @param mainHost
     * @param mainPort
     * @param localHost
     * @param localPort
     * @return jade container
     */
    public static Container createContainer(String mainHost, int mainPort, String localHost, int localPort)
    {
        jade.core.Profile profile = new jade.core.ProfileImpl();
        profile.setParameter(Profile.MAIN, "false");
        profile.setParameter(Profile.MAIN_HOST, mainHost);
        profile.setParameter(Profile.MAIN_PORT, Integer.toString(mainPort));
        profile.setParameter(Profile.LOCAL_HOST, localHost);
        profile.setParameter(Profile.LOCAL_PORT, Integer.toString(localPort));
        return createContainer(profile);
    }

    /**
     * Start JADE container.
     *
     * @return true if start succeeded,
     *         false otherwise
     */
    public boolean start()
    {
        if (isStarted()) {
            return true;
        }

        // Setup JADE runtime
        Runtime runtime = Runtime.instance();

        // Disable System.out, because JADE prints an unwanted messages
        Logging.disableSystemOut();
        Logging.disableSystemErr();

        if (containerController != null) {
            try {
                containerController.kill();
            }
            catch (Exception exception) {
            }
            containerController = null;
        }

        // Clone profile
        Properties properties = (Properties) ((ProfileImpl) this.profile).getProperties().clone();
        Profile profile = new ProfileImpl();
        for (java.util.Map.Entry<Object, Object> entry : properties.entrySet()) {
            String name = (String) entry.getKey();
            String value = (String) entry.getValue();
            if (name.equals(Profile.MTPS) || name.equals(Profile.SERVICES)) {
                continue;
            }
            profile.setParameter(name, value);
        }
        this.profile = profile;
        this.profile.setParameter(Profile.SERVICES, "jade.core.faultRecovery.FaultRecoveryService;");

        // Create main or agent container base on Profile.MAIN parameter
        boolean result = true;
        if (profile.isMain()) {
            containerController = runtime.createMainContainer(profile);
            if (containerController == null) {
                logger.error("Failed to start the JADE main container.");
                result = false;
            }
        }
        else {
            containerController = runtime.createAgentContainer(profile);
            if (containerController == null) {
                String url = profile.getParameter(Profile.MAIN_HOST, "");
                url += ":" + profile.getParameter(Profile.MAIN_PORT, "");
                logger.error("Failed to start the JADE container. Is the main container {} running?", url);
                result = false;
            }
        }

        // Enable System.out back
        Logging.enableSystemOut();
        Logging.enableSystemErr();

        if (result == false) {
            return false;
        }

        // Start agents
        for (String agentName : agents.keySet()) {
            if (startAgent(agentName) == false) {
                try {
                    containerController.kill();
                }
                catch (Exception exception) {
                }
                containerController = null;
                return false;
            }
        }

        return true;
    }

    /**
     * Stop JADE container.
     */
    public void stop()
    {
        if (isStarted()) {
            // Stop agents
            for (String agentName : agents.keySet()) {
                stopAgent(agentName);
            }

            // Stop platform
            try {
                if (profile.isMain()) {
                    containerController.getPlatformController().kill();
                }
                else {
                    containerController.kill();
                }
                containerController = null;
            }
            catch (Exception exception) {
                logger.error("Failed to kill container.", exception);
            }
        }
    }

    /**
     * Start agent in container.
     *
     * @param agentName
     * @return true if agent start succeeded,
     *         false otherwise
     */
    private boolean startAgent(String agentName)
    {
        // Check if agent controller is started and if so skip the startup
        AgentController agentController = agentControllers.get(agentName);
        if (agentController != null) {
            try {
                // NOTE: tests the agent connection state (if disconnected, an exception is thrown)
                agentController.getState().toString();
                return true;
            }
            catch (StaleProxyException exception) {
                try {
                    agentController.kill();
                }
                catch (StaleProxyException e1) {
                }
                // Remove agent and it will be restarted
                agentControllers.remove(agentName);
            }
            agentController = null;
        }

        // Start agent
        Object agent = agents.get(agentName);
        Object[] arguments = agentsArguments.get(agentName);
        if (agent instanceof Class) {
            Class agentClass = (Class) agent;
            try {
                agentController = containerController
                        .createNewAgent(agentName, agentClass.getCanonicalName(), arguments);
            }
            catch (StaleProxyException exception) {
                logger.error("Failed to create agent.", exception);
                return false;
            }
            try {
                agentController.start();
            }
            catch (Exception exception) {
                logger.error("Failed to start agent.", exception);
                return false;
            }
        }
        else if (agent instanceof Agent) {
            try {
                Agent agentInstance = (Agent) agent;
                if (agentInstance.getState() == Agent.AP_DELETED) {
                    logger.error("Can't start agent that was deleted [{} of type {}]!", agentName,
                            agentInstance.getClass().getName());
                    return false;
                }
                agentInstance.setArguments(arguments);
                agentController = containerController.acceptNewAgent(agentName, agentInstance);
            }
            catch (StaleProxyException exception) {
                logger.error("Failed to accept or start agent.", exception);
                return false;
            }
            try {
                agentController.start();
            }
            catch (Exception exception) {
                logger.error("Failed to start agent.", exception);
                return false;
            }
        }
        else {
            throw new RuntimeException("Unknown agent type " + agent.getClass().getCanonicalName() + "!");
        }

        agentControllers.put(agentName, agentController);

        return true;
    }

    /**
     * Stop agent in container.
     *
     * @param agentName
     */
    private void stopAgent(String agentName)
    {
        if (isAgentStarted(agentName) == false) {
            return;
        }
        AgentController agentController = agentControllers.get(agentName);
        if (agentController != null) {
            try {
                agentController.kill();
                agentControllers.remove(agentName);
            }
            catch (StaleProxyException exception) {
                logger.error("Failed to kill agent.", exception);
            }
        }
    }

    /**
     * Is container started?
     *
     * @return true if the container is started,
     *         false otherwise
     */
    public boolean isStarted()
    {
        if (containerController == null || !containerController.isJoined()) {
            return false;
        }
        return true;
    }

    /**
     * @return collection of agent names
     */
    public Collection<String> getAgentNames()
    {
        return agents.keySet();
    }

    /**
     * Add agent to container by it's class. The agent will be started when the container
     * will start and stopped when the container stops.
     *
     * @param agentName
     * @param agentClass
     */
    public void addAgent(String agentName, Class agentClass, Object[] arguments)
    {
        agents.put(agentName, agentClass);
        if (arguments != null) {
            agentsArguments.put(agentName, arguments);
        }

        if (isStarted()) {
            startAgent(agentName);
        }
    }

    /**
     * Add agent instance to container. The agent will be started when the container
     * will start and stopped when the container stops.
     *
     * @param agentName
     * @param agent
     */
    public void addAgent(String agentName, Agent agent, Object[] arguments)
    {
        agents.put(agentName, agent);
        if (arguments != null) {
            agentsArguments.put(agentName, arguments);
        }

        if (isStarted()) {
            startAgent(agentName);
        }
    }

    /**
     * Remove agent.
     *
     * @param agentName
     */
    public void removeAgent(String agentName)
    {
        stopAgent(agentName);
        agents.remove(agentName);
        agentsArguments.remove(agentName);
    }

    /**
     * Checks whether container contains agent with given name.
     *
     * @param agentName
     * @return true if container contains agent,
     *         false otherwise
     */
    public boolean isAgentStarted(String agentName)
    {
        if (!agents.containsKey(agentName)) {
            return false;
        }
        Object agent = agents.get(agentName);
        if (agent instanceof Agent) {
            Agent agentInstance = (Agent) agent;
            if (!agentInstance.isStarted()) {
                return false;
            }
        }
        AgentController agentController = agentControllers.get(agentName);
        try {
            State state = agentController.getState();
        }
        catch (Exception exception) {
            return false;
        }
        return true;
    }

    /**
     * Perform command on local agent and do not wait for the result (not blocking).
     * <p/>
     * Passes the command to the agent by means of the O2A channel (see JADE documentation).
     *
     * @param command command to be performed by an agent
     */
    public Command performCommand(String agentName, Command command)
    {
        if (!isStarted()) {
            command.setFailed(new CommandAgentNotStarted(agentName));
            return command;
        }
        AgentController agentController = agentControllers.get(agentName);
        if (agentController == null) {
            command.setFailed(new CommandAgentNotStarted(agentName));
            return command;
        }
        try {
            // NOTE: must be ASYNC, otherwise, the connector thread be deadlock, waiting for itself
            agentController.putO2AObject(command, AgentController.ASYNC);
        }
        catch (StaleProxyException exception) {
            logger.error("Failed to put command object to agent queue.", exception);
        }
        return command;
    }

    /**
     * Print status information.
     */
    public void printStatus()
    {
        try {
            System.out.println();

            // Print controller status
            String containerName = "Unnamed";
            String containerType = (profile.isMain() ? "Main" : "Slave");
            String containerStatus = "Not-Started";
            String containerAddress = profile.getParameter(Profile.LOCAL_HOST, "") + ":"
                    + profile.getParameter(Profile.LOCAL_PORT, "");
            if (containerController == null) {
                profile.getParameter(Profile.CONTAINER_NAME, "");
            }
            else {
                containerName = containerController.getContainerName();
                if (containerController.isJoined()) {
                    containerStatus = "Started";
                }
            }
            System.out.printf("Container: [%s]\n", containerName);
            System.out.printf("Type:      [%s]\n", containerType);
            System.out.printf("Status:    [%s]\n", containerStatus);
            System.out.printf("Address:   [%s]\n", containerAddress);

            // Show main container address
            if (profile.isMain() == false) {
                System.out.printf("Main:      [%s:%s]\n", profile.getParameter(Profile.MAIN_HOST, ""),
                        profile.getParameter(Profile.MAIN_PORT, ""));
            }
            // Show all slave containers in platform
            else {
                List<ContainerID> containerList = listContainers();
                if (containerList.size() > 1) {
                    System.out.println();
                    System.out.printf("List of slave containers:\n");

                    int index = 0;
                    for (ContainerID container : containerList) {
                        if (container.getMain()) {
                            continue;
                        }
                        System.out.printf("%2d) [%s] at [%s]\n", index + 1, container.getName(),
                                container.getAddress());
                        index++;
                    }
                }
            }

            // Print agents status
            if (agentControllers.size() > 0) {
                System.out.println();
                System.out.printf("List of agents:\n");

                int index = 0;
                for (Map.Entry<String, AgentController> entry : agentControllers.entrySet()) {
                    AgentController agentController = entry.getValue();
                    String agentName = entry.getKey();
                    String agentStatus = "Not-Started";
                    try {
                        agentName = agentController.getName();
                        agentStatus = agentController.getState().getName();
                    }
                    catch (StaleProxyException exception) {
                    }
                    System.out.printf("%2d) Name:   [%s]\n", index + 1, agentName);
                    System.out.printf("    Status: [%s]\n", agentStatus);
                    index++;
                }
            }

            System.out.println();
        }
        catch (Exception exception) {
            logger.error("Failed to get container status.", exception);
        }
    }

    /**
     * Show JADE Management GUI.
     */
    public void addManagementGui()
    {
        addAgent("rma", jade.tools.rma.rma.class, null);
    }

    /**
     * Has JADE management GUI?
     *
     * @return boolean
     */

    public boolean hasManagementGui()
    {
        return isAgentStarted("rma");
    }

    /**
     * Hide JADE Management GUI.
     */
    public void removeManagementGui()
    {
        removeAgent("rma");
    }

    /**
     * List containers in platform.
     *
     * @return containers
     */
    private List<ContainerID> listContainers()
    {
        try {
            Ontology ontology = JADEManagementOntology.getInstance();

            // Create agent that will perform listing
            jade.core.Agent agent = new jade.core.Agent();
            agent.getContentManager().registerLanguage(new SLCodec());
            agent.getContentManager().registerOntology(ontology);
            AgentController agentController = containerController.acceptNewAgent("listContainers", agent);
            agentController.start();

            // Send Request to AMS
            QueryPlatformLocationsAction query = new QueryPlatformLocationsAction();
            Action action = new Action(agent.getAID(), query);
            ACLMessage message = new ACLMessage(ACLMessage.REQUEST);
            message.addReceiver(agent.getAMS());
            message.setLanguage(FIPANames.ContentLanguage.FIPA_SL);
            message.setOntology(ontology.getName());
            agent.getContentManager().fillContent(message, action);
            agent.send(message);

            // Receive response
            ACLMessage receivedMessage = agent.blockingReceive(MessageTemplate.MatchSender(agent.getAMS()));
            ContentElement content = agent.getContentManager().extractContent(receivedMessage);

            // Build list of containers
            List<ContainerID> containers = new ArrayList<ContainerID>();
            Result result = (Result) content;
            jade.util.leap.List listOfPlatforms = (jade.util.leap.List) result.getValue();
            Iterator iter = listOfPlatforms.iterator();
            while (iter.hasNext()) {
                ContainerID next = (ContainerID) iter.next();
                containers.add(next);
            }

            // Kill agent
            agentController.kill();

            return containers;
        }
        catch (Exception exception) {
            logger.error("Failed to list containers", exception);
        }
        return new ArrayList<ContainerID>();
    }

    /**
     * Kill JADE threads
     */
    public static void killAllJadeThreads()
    {
        logger.debug("Killing all JADE threads...");
        ThreadHelper.killThreadGroup("JADE");
    }
}
