package cz.cesnet.shongo.api;

/**
 * Represents a {@link RoomSetting} for a {@link cz.cesnet.shongo.Technology#ADOBE_CONNECT}.
 *
 * @author Martin Srom <martin.srom@cesnet.cz>
 */
public class AdobeConnectRoomSetting extends RoomSetting
{
    /**
     * The PIN that must be entered to get to the room.
     */
    private String pin;

    /**
     * Room access mode
     */
    private AdobeConnectAccessMode accessMode;

    /**
     * @return {@link #pin}
     */
    public String getPin()
    {
        return pin;
    }

    /**
     * @param pin sets the {@link #pin}
     */
    public void setPin(String pin)
    {
        this.pin = pin;
    }

    public AdobeConnectAccessMode getAccessMode()
    {
        return accessMode;
    }

    public void setAccessMode(AdobeConnectAccessMode accessMode)
    {
        this.accessMode = accessMode;
    }

    public static final String PIN = "pin";
    public static final String ACCESS_MODE = "accessMode";

    @Override
    public DataMap toData()
    {
        DataMap dataMap = super.toData();
        dataMap.set(PIN, pin);
        dataMap.set(ACCESS_MODE,accessMode);
        return dataMap;
    }

    @Override
    public void fromData(DataMap dataMap)
    {
        super.fromData(dataMap);
        pin = dataMap.getString(PIN);
        accessMode = dataMap.getEnum(ACCESS_MODE,AdobeConnectAccessMode.class);
    }

    @Override
    public void merge(RoomSetting roomSetting)
    {
        if (!(roomSetting instanceof AdobeConnectRoomSetting)) {
            throw new IllegalArgumentException(AdobeConnectRoomSetting.class.getSimpleName() +
                    " is not compatible with " + roomSetting.getClass().getSimpleName());
        }
        AdobeConnectRoomSetting adobeConnectRoomSetting = (AdobeConnectRoomSetting) roomSetting;
        if (adobeConnectRoomSetting.getPin() != null) {
            setPin(adobeConnectRoomSetting.getPin());
        }
        if (adobeConnectRoomSetting.getAccessMode() != null) {
            setAccessMode(adobeConnectRoomSetting.getAccessMode());
        }
    }
}
