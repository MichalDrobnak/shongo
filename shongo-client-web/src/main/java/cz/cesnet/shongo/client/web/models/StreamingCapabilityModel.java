package cz.cesnet.shongo.client.web.models;

import cz.cesnet.shongo.controller.api.StreamingCapability;

/**
 * @author Marek Perichta <mperichta@cesnet.cz>
 */
public class StreamingCapabilityModel {

    public StreamingCapabilityModel() {
    }

    public StreamingCapability toApi() {
        return new StreamingCapability();
    }
}
