package cz.cesnet.shongo.controller.api;

import cz.cesnet.shongo.api.UserInformation;
import cz.cesnet.shongo.api.annotation.Transient;
import cz.cesnet.shongo.api.rpc.StructType;
import cz.cesnet.shongo.controller.Role;

/**
 * Represents a record in Shongo ACL which means that user with specified {@link #user} has role
 * with specified {@link #role} for resource with specified {@link #entityId}.
 *
 * @author Martin Srom <martin.srom@cesnet.cz>
 */
public class AclRecord implements StructType
{
    /**
     * Identifier of ACL record.
     */
    String id;

    /**
     * {@link cz.cesnet.shongo.api.UserInformation} of user of the {@link AclRecord}.
     */
    UserInformation user;

    /**
     * Identifier of entity of some {@link cz.cesnet.shongo.controller.EntityType} for which the user
     * gets granted the {@link #role}.
     */
    String entityId;

    /**
     * {@link cz.cesnet.shongo.controller.Role} which the user gets granted for the entity with {@link #entityId}.
     */
    Role role;

    /**
     * @return {@link #id}
     */
    public String getId()
    {
        return id;
    }

    /**
     * @param id sets the {@link #id}
     */
    public void setId(String id)
    {
        this.id = id;
    }

    /**
     * @return {@link #user}
     */
    public UserInformation getUser()
    {
        return user;
    }

    /**
     * @return {@link cz.cesnet.shongo.api.UserInformation#getUserId()}
     */
    @Transient
    public String getUserId()
    {
        return user.getUserId();
    }

    /**
     * @param user sets the {@link #user}
     */
    public void setUser(UserInformation user)
    {
        this.user = user;
    }

    /**
     * @return {@link #entityId}
     */
    public String getEntityId()
    {
        return entityId;
    }

    /**
     * @param entityId sets the {@link #entityId}
     */
    public void setEntityId(String entityId)
    {
        this.entityId = entityId;
    }

    /**
     * @return {@link #role}
     */
    public Role getRole()
    {
        return role;
    }

    /**
     * @param role {@link #role}
     */
    public void setRole(Role role)
    {
        this.role = role;
    }
}
