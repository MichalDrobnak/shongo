package cz.cesnet.shongo.connector.api;

import cz.cesnet.shongo.api.Alias;
import cz.cesnet.shongo.api.CommandException;
import cz.cesnet.shongo.api.CommandUnsupportedException;
import cz.cesnet.shongo.api.RoomUser;

import java.util.Collection;
import java.util.Map;

/**
 * @author Ondrej Bouda <ondrej.bouda@cesnet.cz>
 */
public interface UserService
{
    /**
     * Lists all users present in a virtual room.
     *
     * @param roomId room identifier
     * @return array of room users
     */
    Collection<RoomUser> listParticipants(String roomId) throws CommandException, CommandUnsupportedException;

    /**
     * Gets user information and settings in a room.
     *
     * @param roomId     room identifier
     * @param roomUserId identifier of the user within the given room
     * @return description of the user
     */
    RoomUser getParticipant(String roomId, String roomUserId) throws CommandException, CommandUnsupportedException;

    /**
     * Dials a user by an alias and adds him/her to the room.
     *
     * @param roomId identifier of room to which to add the user
     * @param alias  alias under which the user is callable
     * @return identifier assigned to the user within the given room (generated by the connector)
     */
    String dialParticipant(String roomId, Alias alias) throws CommandException, CommandUnsupportedException;

    /**
     * Modifies user settings in the room.
     * <p/>
     * Suitable for setting microphone/playback level, muting/unmuting, user layout, ...
     *
     * @param roomId     room identifier
     * @param roomUserId identifier of the user within the given room
     * @param attributes map of attributes to change
     */
    void modifyParticipant(String roomId, String roomUserId, Map<String, Object> attributes)
            throws CommandException, CommandUnsupportedException;

    /**
     * Disconnects a user from a room.
     *
     * @param roomId     room identifier
     * @param roomUserId identifier of the user within the given room
     */
    void disconnectParticipant(String roomId, String roomUserId) throws CommandException, CommandUnsupportedException;
}
