package org.transitclock.domain.repository;

import org.hibernate.HibernateException;
import org.hibernate.Session;
import org.hibernate.Transaction;

import org.transitclock.domain.structs.VehicleToBlockConfig;

import java.util.Date;
import java.util.List;

public class VehicleToBlockConfigRepository extends BaseRepository<VehicleToBlockConfig> {
    /**
     * Reads List of VehicleConfig objects from database
     *
     * @param session
     * @return List of VehicleConfig objects
     * @throws HibernateException
     */
    public static List<VehicleToBlockConfig> getVehicleToBlockConfigs(Session session) throws HibernateException {
        return session
                .createQuery("FROM VehicleToBlockConfig ORDER BY assignmentDate DESC", VehicleToBlockConfig.class)
                .list();
    }

    /**
     * Reads List of VehicleConfig objects from database
     *
     * @param vehicleToBlockConfig, session
     * @throws HibernateException
     */
    public static void updateVehicleToBlockConfig(VehicleToBlockConfig vehicleToBlockConfig, Session session)
            throws HibernateException {
        session.merge(vehicleToBlockConfig);
    }

    public static void deleteVehicleToBlockConfig(long id, Session session) throws HibernateException {
        Transaction transaction = session.beginTransaction();
        try {
            session
                    .createMutationQuery("delete from VehicleToBlockConfig where id = :id")
                    .setParameter("id", id)
                    .executeUpdate();

            transaction.commit();
        } catch (Throwable t) {
            transaction.rollback();
            throw t;
        }
    }

    public static List<VehicleToBlockConfig> getVehicleToBlockConfigsByBlockId(Session session, String blockId) throws HibernateException {
        return session
                .createQuery("FROM VehicleToBlockConfig WHERE blockId = :blockId ORDER BY assignmentDate DESC", VehicleToBlockConfig.class)
                .setParameter("blockId", blockId)
                .list();
    }

    public static List<VehicleToBlockConfig> getVehicleToBlockConfigsByVehicleId(Session session, String vehicleId) throws HibernateException {
        return session.createQuery("FROM VehicleToBlockConfig WHERE vehicleId = :vehicleId ORDER BY assignmentDate DESC", VehicleToBlockConfig.class)
                .setParameter("vehicleId", vehicleId)
                .list();
    }

    public static VehicleToBlockConfig getVehicleToBlockConfigs(Session session, String vehicleId, Date date) throws HibernateException {
        return session.createQuery("FROM VehicleToBlockConfig WHERE vehicleId = :vehicleId AND validFrom >= :date AND validTo <= :date AND blockId IS NOT NULL ORDER BY assignmentDate DESC LIMIT 1", VehicleToBlockConfig.class)
            .setParameter("vehicleId", vehicleId)
            .setParameter("date", date)
            .getSingleResultOrNull();
    }
}
