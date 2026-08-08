package org.transitclock.domain.repository;

import java.util.List;

import org.hibernate.HibernateException;
import org.hibernate.Session;
import org.hibernate.Transaction;

import org.transitclock.domain.structs.ExportTable;

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

    public static List<ExportTable> getExportTable(Session session, int type) throws HibernateException {
        var query = session.createQuery("FROM ExportTable WHERE exportType = :type ORDER BY exportDate DESC", ExportTable.class)
                .setParameter("type", type);
        return query.list();
    }

    public static List<ExportTable> getExport(Session session, long id) throws HibernateException {
        return session.createQuery("FROM ExportTable WHERE id = :id", ExportTable.class)
                .setParameter("id", id)
                .list();
    }

    public static void updateStatus(Session session, String fileName, String info) throws HibernateException {
        Transaction transaction = session.beginTransaction();
        try {
            session.createMutationQuery("UPDATE  ExportTable e SET e.exportStatus = 3, e.file = :info  WHERE e.fileName = :name")
                    .setParameter("name", fileName)
                    .setParameter("info", info.getBytes())
                    .executeUpdate();
            transaction.commit();
        } catch (Throwable t) {
            transaction.rollback();
            throw t;
        }
    }

    public static void deleteExportTableRecord(int id, Session session) throws HibernateException {
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

    public static void deleteExportTableRecord(String name, Session session) throws HibernateException {
        Transaction transaction = session.beginTransaction();
        try {
            var q = session
                    .createMutationQuery("delete from ExportTable e where e.fileName = :name")
                    .setParameter("name", name);
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
