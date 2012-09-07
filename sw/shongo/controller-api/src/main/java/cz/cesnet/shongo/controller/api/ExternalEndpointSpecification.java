package cz.cesnet.shongo.controller.api;

import cz.cesnet.shongo.Technology;
import cz.cesnet.shongo.api.annotation.Required;

/**
 * Special type of requested resource for external endpoints.
 *
 * @author Martin Srom <martin.srom@cesnet.cz>
 */
public class ExternalEndpointSpecification extends ResourceSpecification
{
    /**
     * Technology of the resource.
     */
    public static final String TECHNOLOGY = "technology";

    /**
     * Number of same resources.
     */
    public static final String COUNT = "count";

    /**
     * Constructor.
     */
    public ExternalEndpointSpecification()
    {
    }

    /**
     * Constructor.
     *
     * @param technology sets the {@link #TECHNOLOGY}
     * @param count      sets the {@link #COUNT}
     */
    public ExternalEndpointSpecification(Technology technology, int count)
    {
        setTechnology(technology);
        setCount(count);
    }

    /**
     * @return {@link #TECHNOLOGY}
     */
    @Required
    public Technology getTechnology()
    {
        return getPropertyStorage().getValue(TECHNOLOGY);
    }

    /**
     * @param technology sets the {@link #TECHNOLOGY}
     */
    public void setTechnology(Technology technology)
    {
        getPropertyStorage().setValue(TECHNOLOGY, technology);
    }

    /**
     * @return {@link #COUNT}
     */
    public Integer getCount()
    {
        return getPropertyStorage().getValue(COUNT);
    }

    /**
     * @param count sets the {@link #COUNT}
     */
    public void setCount(Integer count)
    {
        getPropertyStorage().setValue(COUNT, count);
    }
}
