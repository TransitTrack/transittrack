package org.transitclock.domain.repository;

import org.hibernate.HibernateException;
import org.hibernate.Session;

import org.transitclock.domain.structs.VehicleConfig;

import java.util.List;

public class VehicleConfigRepository extends BaseRepository<VehicleConfig> {
    /**
     * Reads List of VehicleConfig objects from database
     *
     * @param session
     * @return List of VehicleConfig objects
     * @throws HibernateException
     */
    public static List<VehicleConfig> getVehicleConfigs(Session session) throws HibernateException {
        return session
                .createQuery("FROM VehicleConfig", VehicleConfig.class)
                .list();
    }

    /**
     * Reads List of VehicleConfig objects from database
     *
     * @param vehicleConfig, session
     * @throws HibernateException
     */
    public static void updateVehicleConfig(VehicleConfig vehicleConfig, Session session) throws HibernateException {
        // Transaction tx = session.beginTransaction();
        session.merge(vehicleConfig);
        // tx.commit();
    }
}
