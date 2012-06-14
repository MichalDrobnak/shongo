package cz.cesnet.shongo.common;

import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.MappedSuperclass;
import javax.persistence.Transient;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Represents an object that can be persisted to a database.
 *
 * @author Martin Srom <martin.srom@cesnet.cz>
 */
@MappedSuperclass
public abstract class PersistentObject extends PrintableObject
{
    /**
     * Persistent object must have an unique identifier.
     */
    private Long id;

    /**
     * @return {@link #id}
     */
    @Id
    @GeneratedValue
    public Long getId()
    {
        return id;
    }

    /**
     * @param id sets the {@link #id}
     */
    private void setId(Long id)
    {
        this.id = id;
    }

    /**
     * @return true if object has already been persisted, otherwise false
     */
    @Transient
    public boolean isPersisted()
    {
        return id != null;
    }

    /**
     * Checks whether object has already been persisted.
     *
     * @throws IllegalStateException
     */
    public void checkPersisted() throws IllegalStateException
    {
        if (isPersisted() == false) {
            throw new IllegalArgumentException(this.getClass().getSimpleName() + " hasn't been persisted yet!");
        }
    }

    /**
     * Checks whether object has not been persisted yet.
     *
     * @throws IllegalStateException
     */
    public void checkNotPersisted() throws IllegalStateException
    {
        if (isPersisted()) {
            throw new IllegalArgumentException(this.getClass().getSimpleName() + " has already been persisted!");
        }
    }

    @Override
    protected void fillDescriptionMap(Map<String, String> map)
    {
        super.fillDescriptionMap(map);

        if (getId() != null) {
            map.put("id", getId().toString());
        }
    }
}
