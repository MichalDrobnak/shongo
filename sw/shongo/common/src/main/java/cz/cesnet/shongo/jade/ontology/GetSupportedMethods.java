package cz.cesnet.shongo.jade.ontology;

import cz.cesnet.shongo.api.CommandException;
import cz.cesnet.shongo.api.CommandUnsupportedException;
import cz.cesnet.shongo.connector.api.CommonService;

/**
 * Command to get methods supported by the connector.
 *
 * @author Ondrej Bouda <ondrej.bouda@cesnet.cz>
 */
public class GetSupportedMethods extends ConnectorAgentAction
{
    @Override
    public Object exec(CommonService connector) throws CommandException, CommandUnsupportedException
    {
        return connector.getSupportedMethods();
    }

    public String toString()
    {
        return "GetSupportedMethods agent action";
    }
}
