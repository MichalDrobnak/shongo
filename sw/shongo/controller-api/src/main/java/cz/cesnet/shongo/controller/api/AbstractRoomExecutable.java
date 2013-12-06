package cz.cesnet.shongo.controller.api;

import cz.cesnet.shongo.AliasType;
import cz.cesnet.shongo.Technology;
import cz.cesnet.shongo.api.Alias;
import cz.cesnet.shongo.api.DataMap;
import cz.cesnet.shongo.api.RoomSetting;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Represents an abstract class for a room in a device.
 *
 * @author Martin Srom <martin.srom@cesnet.cz>
 */
public abstract class AbstractRoomExecutable extends Executable
{
    /**
     * Set of technologies which the room supports.
     */
    private Set<Technology> technologies = new HashSet<Technology>();

    /**
     * License count.
     */
    private int licenseCount;

    /**
     * List of assigned {@link cz.cesnet.shongo.api.Alias}es to the {@link EndpointExecutable}.
     */
    private List<Alias> aliases = new ArrayList<Alias>();

    /**
     * List of {@link cz.cesnet.shongo.api.RoomSetting}s for the {@link cz.cesnet.shongo.controller.api.AbstractRoomExecutable}.
     */
    private List<RoomSetting> roomSettings = new ArrayList<RoomSetting>();

    /**
     * @see RoomExecutableParticipantConfiguration
     */
    private RoomExecutableParticipantConfiguration participantConfiguration;

    /**
     * @return {@link #technologies}
     */
    public Set<Technology> getTechnologies()
    {
        return technologies;
    }

    /**
     * @param technologies sets the {@link #technologies}
     */
    public void setTechnologies(Set<Technology> technologies)
    {
        this.technologies = technologies;
    }

    /**
     * Clear {@link #technologies}.
     */
    public void clearTechnologies()
    {
        technologies.clear();
    }

    /**
     * @param technology technology to be added to the set of technologies that the device support.
     */
    public void addTechnology(Technology technology)
    {
        technologies.add(technology);
    }

    /**
     * @return {@link #licenseCount}
     */
    public int getLicenseCount()
    {
        return licenseCount;
    }

    /**
     * @param licenseCount sets the {@link #licenseCount}
     */
    public void setLicenseCount(int licenseCount)
    {
        this.licenseCount = licenseCount;
    }

    /**
     * @return {@link #aliases}
     */
    public List<Alias> getAliases()
    {
        return aliases;
    }

    /**
     * @param aliasType
     * @return first {@link cz.cesnet.shongo.api.Alias} of given {@code aliasType}
     */
    public Alias getAliasByType(AliasType aliasType)
    {
        for (Alias alias : aliases) {
            if (alias.getType().equals(aliasType)) {
                return alias;
            }
        }
        return null;
    }

    /**
     * @param aliases sets the {@link #aliases}
     */
    public void setAliases(List<Alias> aliases)
    {
        this.aliases = aliases;
    }

    /**
     * Clear {@link #aliases}.
     */
    public void clearAliases()
    {
        aliases.clear();
    }

    /**
     * @param alias to be added to the {@link #aliases}
     */
    public void addAlias(Alias alias)
    {
        aliases.add(alias);
    }

    /**
     * @return {@link #roomSettings}
     */
    public List<RoomSetting> getRoomSettings()
    {
        return roomSettings;
    }

    /**
     * @param roomSettings sets the {@link #roomSettings}
     */
    public void setRoomSettings(List<RoomSetting> roomSettings)
    {
        this.roomSettings = roomSettings;
    }

    /**
     * @param roomSettingType
     * @return {@link RoomSetting} of given {@code roomSettingType} or null if doesn't exist
     */
    public <T extends RoomSetting> T getRoomSetting(Class<T> roomSettingType)
    {
        for (RoomSetting roomSetting : getRoomSettings()) {
            if (roomSettingType.isInstance(roomSetting)) {
                return roomSettingType.cast(roomSetting);
            }
        }
        return null;
    }

    /**
     * @param roomSetting to be added to the {@link #roomSettings}
     */
    public void addRoomSetting(RoomSetting roomSetting)
    {
        RoomSetting existingRoomSetting = getRoomSetting(roomSetting.getClass());
        if (existingRoomSetting != null) {
            existingRoomSetting.merge(roomSetting);
        }
        else {
            roomSettings.add(roomSetting);
        }
    }

    /**
     * @return {@link #participantConfiguration}
     */
    public RoomExecutableParticipantConfiguration getParticipantConfiguration()
    {
        return participantConfiguration;
    }

    /**
     * @param participantConfiguration sets the {@link #participantConfiguration}
     */
    public void setParticipantConfiguration(RoomExecutableParticipantConfiguration participantConfiguration)
    {
        this.participantConfiguration = participantConfiguration;
    }

    private static final String TECHNOLOGIES = "technologies";
    private static final String LICENSE_COUNT = "licenseCount";
    private static final String ALIASES = "aliases";
    private static final String ROOM_SETTINGS = "roomSettings";
    private static final String PARTICIPANT_CONFIGURATION = "participantConfiguration";

    @Override
    public DataMap toData()
    {
        DataMap dataMap = super.toData();
        dataMap.set(TECHNOLOGIES, technologies);
        dataMap.set(LICENSE_COUNT, licenseCount);
        dataMap.set(ALIASES, aliases);
        dataMap.set(ROOM_SETTINGS, roomSettings);
        dataMap.set(PARTICIPANT_CONFIGURATION, participantConfiguration);
        return dataMap;
    }

    @Override
    public void fromData(DataMap dataMap)
    {
        super.fromData(dataMap);
        technologies = dataMap.getSet(TECHNOLOGIES, Technology.class);
        licenseCount = dataMap.getInt(LICENSE_COUNT);
        aliases = dataMap.getList(ALIASES, Alias.class);
        roomSettings = dataMap.getList(ROOM_SETTINGS, RoomSetting.class);
        participantConfiguration = dataMap.getComplexType(
                PARTICIPANT_CONFIGURATION, RoomExecutableParticipantConfiguration.class);
    }
}
