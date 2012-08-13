package cz.cesnet.shongo.controller.allocation;

import cz.cesnet.shongo.AbstractManager;
import cz.cesnet.shongo.controller.ResourceDatabase;
import cz.cesnet.shongo.controller.request.CompartmentRequest;

import javax.persistence.EntityManager;
import javax.persistence.NoResultException;
import java.util.List;

/**
 * Manager for {@link AllocatedCompartment}.
 *
 * @author Martin Srom <martin.srom@cesnet.cz>
 */
public class AllocatedCompartmentManager extends AbstractManager
{
    /**
     * @param entityManager sets the {@link #entityManager}
     */
    public AllocatedCompartmentManager(EntityManager entityManager)
    {
        super(entityManager);
    }

    /**
     * @param allocatedCompartment to be created in the database
     */
    public void create(AllocatedCompartment allocatedCompartment)
    {
        super.create(allocatedCompartment);
    }

    /**
     * @param allocatedCompartment to be updated in the database
     */
    public void update(AllocatedCompartment allocatedCompartment)
    {
        super.update(allocatedCompartment);
    }

    /**
     * @param allocatedCompartment to be deleted in the database
     */
    public void delete(AllocatedCompartment allocatedCompartment, ResourceDatabase resourceDatabase)
    {
        // Remove all allocated virtual rooms from virtual rooms database
        List<AllocatedResource> allocatedResources = allocatedCompartment.getAllocatedResources();
        for (AllocatedResource allocatedResource : allocatedResources) {
            resourceDatabase.removeAllocatedResource(allocatedResource);
        }
        super.delete(allocatedCompartment);
    }

    /**
     * @param compartmentRequest
     * @return {@link AllocatedCompartment} for the given {@link CompartmentRequest} or null if doesn't exists
     */
    public AllocatedCompartment getByCompartmentRequest(CompartmentRequest compartmentRequest)
    {
        return getByCompartmentRequest(compartmentRequest.getId());
    }

    /**
     * @param compartmentRequestId
     * @return {@link AllocatedCompartment} for {@link CompartmentRequest} with given {@code compartmentRequestId}
     *         or null if doesn't exists
     */
    public AllocatedCompartment getByCompartmentRequest(Long compartmentRequestId)
    {
        try {
            AllocatedCompartment allocatedCompartment = entityManager.createQuery(
                    "SELECT alloc FROM AllocatedCompartment alloc WHERE alloc.compartmentRequest.id = :id",
                    AllocatedCompartment.class).setParameter("id", compartmentRequestId)
                    .getSingleResult();
            return allocatedCompartment;
        }
        catch (NoResultException exception) {
            return null;
        }
    }

    /**
     * @param compartmentRequestId
     * @return list of all allocated compartments for the compartment request
     */
    public List<AllocatedCompartment> listByCompartmentRequest(Long compartmentRequestId)
    {
        List<AllocatedCompartment> allocatedCompartments = entityManager.createQuery(
                "SELECT allocation FROM AllocatedCompartment allocation  WHERE allocation.compartmentRequest.id = :id",
                AllocatedCompartment.class)
                .setParameter("id", compartmentRequestId)
                .getResultList();
        return allocatedCompartments;
    }

    /**
     * @param allocatedCompartment allocated compartment to be marked for deletion
     */
    public void markedForDeletion(AllocatedCompartment allocatedCompartment)
    {
        allocatedCompartment.setCompartmentRequest(null);
        update(allocatedCompartment);
    }

    /**
     * Delete all allocated compartment which were marked by {@link #markedForDeletion(AllocatedCompartment)}.
     *
     * @param resourceDatabase
     */
    public void deleteAllMarked(ResourceDatabase resourceDatabase)
    {
        List<AllocatedCompartment> allocatedCompartments = entityManager.createQuery(
                "SELECT allocation FROM AllocatedCompartment allocation WHERE allocation.compartmentRequest IS NULL",
                AllocatedCompartment.class)
                .getResultList();
        for (AllocatedCompartment allocatedCompartment : allocatedCompartments) {
            delete(allocatedCompartment, resourceDatabase);
        }
    }
}
