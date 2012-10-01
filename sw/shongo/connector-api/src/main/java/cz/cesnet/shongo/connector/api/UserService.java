package cz.cesnet.shongo.connector.api;

import cz.cesnet.shongo.api.Alias;
import cz.cesnet.shongo.api.CommandException;
import cz.cesnet.shongo.api.CommandUnsupportedException;

import java.util.Collection;
import java.util.Map;

/**
 * @author Ondrej Bouda <ondrej.bouda@cesnet.cz>
 */
public interface UserService
{
    /**
     * Dials a user and adds him/her to the room.
     *
     * @param roomId        identifier of room to which to add the user
     * @param roomUserId    identifier to assign to the user within the given room
     * @param alias         alias under which the user is callable
     */
    void dial(String roomId, String roomUserId, Alias alias) throws CommandException, CommandUnsupportedException;

    /**
     * Lists all users present in a virtual room.
     *
     * @param roomId room identifier
     * @return array of room users
     */
    Collection<RoomUser> listRoomUsers(String roomId) throws CommandException, CommandUnsupportedException;

    /**
     * Gets user information and settings in a room.
     *
     * @param roomId     room identifier
     * @param roomUserId identifier of the user within the given room
     * @return description of the user
     */
    RoomUser getRoomUser(String roomId, String roomUserId) throws CommandException, CommandUnsupportedException;

    /**
     * Modifies user settings in the room.
     * <p/>
     * Suitable for setting microphone/playback level, muting/unmuting, user layout, ...
     *
     * @param roomId     room identifier
     * @param roomUserId identifier of the user within the given room
     * @param attributes map of attributes to change
     */
    void modifyRoomUser(String roomId, String roomUserId, Map attributes) throws CommandException, CommandUnsupportedException;

    /**
     * Disconnects a user from a room.
     *
     * @param roomId     room identifier
     * @param roomUserId identifier of the user within the given room
     */
    void disconnectRoomUser(String roomId, String roomUserId) throws CommandException, CommandUnsupportedException;

    /**
     * Enables a given room user as a content provider in the room. This is typically enabled by default.
     *
     * @param roomId     room identifier
     * @param roomUserId identifier of the user within the given room
     */
    void enableContentProvider(String roomId, String roomUserId) throws CommandException, CommandUnsupportedException;

    /**
     * Disables a given room user as a content provider in the room.
     * <p/>
     * Typically, all users are allowed to fight for being the content provider. Using this method, a user is not
     * allowed to do this.
     *
     * @param roomId     room identifier
     * @param roomUserId identifier of the user within the given room
     */
    void disableContentProvider(String roomId, String roomUserId) throws CommandException, CommandUnsupportedException;
}
