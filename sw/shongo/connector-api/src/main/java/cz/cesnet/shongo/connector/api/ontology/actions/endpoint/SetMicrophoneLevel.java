package cz.cesnet.shongo.connector.api.ontology.actions.endpoint;

import cz.cesnet.shongo.api.CommandException;
import cz.cesnet.shongo.api.CommandUnsupportedException;
import cz.cesnet.shongo.connector.api.CommonService;
import cz.cesnet.shongo.connector.api.ontology.ConnectorAgentAction;

/**
 * Command to set microphone(s) level.
 *
 * @author Ondrej Bouda <ondrej.bouda@cesnet.cz>
 */
public class SetMicrophoneLevel extends ConnectorAgentAction
{
    private int level;

    public int getLevel()
    {
        return level;
    }

    public void setLevel(int level)
    {
        this.level = level;
    }


    public SetMicrophoneLevel()
    {
    }

    public SetMicrophoneLevel(int level)
    {
        this.level = level;
    }

    @Override
    public Object exec(CommonService connector) throws CommandException, CommandUnsupportedException
    {
        logger.debug("Setting microphone level {}", level);
        getEndpoint(connector).setMicrophoneLevel(level);
        return null;
    }

    public String toString()
    {
        return String.format(SetMicrophoneLevel.class.getSimpleName() + " (level: %d)", level);
    }
}
