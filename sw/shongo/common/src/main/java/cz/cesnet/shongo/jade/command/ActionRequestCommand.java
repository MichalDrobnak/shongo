package cz.cesnet.shongo.jade.command;

import cz.cesnet.shongo.api.CommandException;
import cz.cesnet.shongo.jade.ActionRequesterBehaviour;
import cz.cesnet.shongo.jade.Agent;
import cz.cesnet.shongo.jade.ontology.ShongoOntology;
import jade.content.AgentAction;
import jade.content.Concept;
import jade.content.ContentElement;
import jade.content.lang.Codec;
import jade.content.onto.OntologyException;
import jade.content.onto.basic.Action;
import jade.core.AID;
import jade.domain.FIPANames;
import jade.lang.acl.ACLMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A command for a JADE agent to send a action (agent action, message, ...) to an agent via JADE middleware.
 * <p/>
 * The SL codec and Shongo ontology is used to encode the message.
 *
 * @author Martin Srom <martin.srom@cesnet.cz>
 * @author Ondrej Bouda <ondrej.bouda@cesnet.cz>
 */
public class ActionRequestCommand extends Command
{
    private static Logger logger = LoggerFactory.getLogger(ActionRequestCommand.class);

    /**
     * Message parameters
     */
    private Concept action;
    private AID performer;

    /**
     * Construct command that sends a action to another agent.
     */
    public ActionRequestCommand(String performerName, AgentAction action)
    {
        if (performerName.contains("@")) {
            performer = new AID(performerName, AID.ISGUID);
        }
        else {
            performer = new AID(performerName, AID.ISLOCALNAME);
        }

        this.action = action;
    }

    @Override
    public String getName()
    {
        return action.getClass().getSimpleName();
    }

    @Override
    public void process(Agent agent) throws CommandException
    {
        ACLMessage initMsg = new ACLMessage(ACLMessage.REQUEST);
        initMsg.addReceiver(performer);
        initMsg.setSender(agent.getAID());
        initMsg.setLanguage(FIPANames.ContentLanguage.FIPA_SL);
        initMsg.setOntology(ShongoOntology.getInstance().getName());

        ContentElement content = new Action(agent.getAID(), action);
        try {
            agent.getContentManager().fillContent(initMsg, content);
        }
        catch (Codec.CodecException e) {
            throw new CommandException("Error in composing the command message.", e);
        }
        catch (OntologyException e) {
            throw new CommandException("Error in composing the command message.", e);
        }

        logger.debug("{} initiating action request -> {}\n", agent.getAID().getName(), performer.getName());

        agent.addBehaviour(new ActionRequesterBehaviour(agent, initMsg, this));
        // FIXME: check that the behaviour is removed from the agent once it is done (or after some timeout)
    }
}
