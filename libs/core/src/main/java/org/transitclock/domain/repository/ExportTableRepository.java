package org.transitclock.domain.repository;

import org.hibernate.HibernateException;
import org.hibernate.Session;
import org.hibernate.Transaction;

import org.transitclock.domain.structs.ExportTable;

import java.util.List;

public class ExportTableRepository extends BaseRepository<ExportTable> {
    /**
     * Reads List of VehicleConfig objects from database
     *
     * @param session
     * @return List of VehicleConfig objects
     * @throws HibernateException
     */
    public static List<ExportTable> getExportTable(Session session) throws HibernateException {
        // String hql = "FROM ExportTable";
        var query = session.createQuery("FROM ExportTable ORDER BY exportDate DESC", ExportTable.class);
        return query.list();
    }

    public static void deleteExportTableRecord(long id, Session session) throws HibernateException {
        Transaction transaction = session.beginTransaction();
        try {
            var q = session
                    .createMutationQuery("delete from ExportTable where id = :id")
                    .setParameter("id", id);
            q.executeUpdate();

            transaction.commit();
        } catch (Throwable t) {
            transaction.rollback();
            throw t;
        }
    }

    public static List<ExportTable> getExportFile(Session session, long id) throws HibernateException {
        return session.createQuery("FROM ExportTable WHERE id = :id", ExportTable.class)
                .setParameter("id", id)
                .list();
    }
}
