package cz.cesnet.shongo.controller.api;

import cz.cesnet.shongo.Technology;
import cz.cesnet.shongo.api.Alias;
import cz.cesnet.shongo.api.RoomSetting;
import cz.cesnet.shongo.api.annotation.Required;

import java.util.List;
import java.util.Set;

/**
 * {@link Specification} virtual room.
 *
 * @author Martin Srom <martin.srom@cesnet.cz>
 */
public class RoomSpecification extends Specification
{
    /**
     * Preferred {@link Resource} shongo-id with {@link AliasProviderCapability}.
     */
    public static final String RESOURCE_ID = "resourceId";

    /**
     * Set of technologies which the virtual rooms must support.
     */
    public static final String TECHNOLOGIES = "technologies";

    /**
     * Number of ports which must be allocated for the virtual room.
     */
    public static final String PARTICIPANT_COUNT = "participantCount";

    /**
     * Specifies whether {@link Alias} should be acquired for each {@link Technology} from {@link #TECHNOLOGIES}.
     */
    public static final String WITH_ALIAS = "withAlias";

    /**
     * {@link cz.cesnet.shongo.api.RoomSetting}s for the virtual room.
     */
    public static final String ROOM_SETTINGS = "roomSettings";

    /**
     * Constructor.
     */
    public RoomSpecification()
    {
    }

    /**
     * Constructor.
     *
     * @param participantCount sets the {@link #PARTICIPANT_COUNT}
     * @param technologies to be added to the {@link #TECHNOLOGIES}
     */
    public RoomSpecification(int participantCount, Technology[] technologies)
    {
        setParticipantCount(participantCount);
        for (Technology technology : technologies) {
            addTechnology(technology);
        }
        setWithAlias(true);
    }

    /**
     * @return {@link #RESOURCE_ID}
     */
    public String getResourceId()
    {
        return getPropertyStorage().getValue(RESOURCE_ID);
    }

    /**
     * @param resourceId sets the {@link #RESOURCE_ID}
     */
    public void setResourceId(String resourceId)
    {
        getPropertyStorage().setValue(RESOURCE_ID, resourceId);
    }

    /**
     * @return {@link #TECHNOLOGIES}
     */
    @Required
    public Set<Technology> getTechnologies()
    {
        return getPropertyStorage().getCollection(TECHNOLOGIES, Set.class);
    }

    /**
     * @param technologies sets the {@link #TECHNOLOGIES}
     */
    public void setTechnologies(Set<Technology> technologies)
    {
        getPropertyStorage().setCollection(TECHNOLOGIES, technologies);
    }

    /**
     * @param technology technology to be added to the {@link #TECHNOLOGIES}
     */
    public void addTechnology(Technology technology)
    {
        getPropertyStorage().addCollectionItem(TECHNOLOGIES, technology, Set.class);
    }

    /**
     * @param technology technology to be removed from the {@link #TECHNOLOGIES}
     */
    public void removeTechnology(Technology technology)
    {
        getPropertyStorage().removeCollectionItem(TECHNOLOGIES, technology);
    }

    /**
     * @return {@link #RESOURCE_ID}
     */
    @Required
    public Integer getParticipantCount()
    {
        return getPropertyStorage().getValue(PARTICIPANT_COUNT);
    }

    /**
     * @param participantCount sets the {@link #PARTICIPANT_COUNT}
     */
    public void setParticipantCount(Integer participantCount)
    {
        getPropertyStorage().setValue(PARTICIPANT_COUNT, participantCount);
    }

    /**
     * @return {@link #WITH_ALIAS}
     */
    public Boolean getWithAlias()
    {
        return getPropertyStorage().getValue(WITH_ALIAS);
    }

    /**
     * @param withAlias sets the {@link #WITH_ALIAS}
     */
    public void setWithAlias(Boolean withAlias)
    {
        getPropertyStorage().setValue(WITH_ALIAS, withAlias);
    }

    /**
     * @return {@link #ROOM_SETTINGS}
     */
    public List<RoomSetting> getRoomSettings()
    {
        return getPropertyStorage().getCollection(ROOM_SETTINGS, List.class);
    }

    /**
     * @param roomSettings sets the {@link #ROOM_SETTINGS}
     */
    public void setRoomSettings(List<RoomSetting> roomSettings)
    {
        getPropertyStorage().setCollection(ROOM_SETTINGS, roomSettings);
    }

    /**
     * @param roomSetting to be added to the {@link #ROOM_SETTINGS}
     */
    public void addRoomSetting(RoomSetting roomSetting)
    {
        getPropertyStorage().addCollectionItem(ROOM_SETTINGS, roomSetting, List.class);
    }

    /**
     * @param roomSetting to be removed from the {@link #ROOM_SETTINGS}
     */
    public void removeRoomSetting(RoomSetting roomSetting)
    {
        getPropertyStorage().removeCollectionItem(ROOM_SETTINGS, roomSetting);
    }
}
