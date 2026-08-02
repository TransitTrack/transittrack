package org.transitclock.repository;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Session;
import org.transitclock.Core;
import org.transitclock.domain.structs.ExportTable;
import org.transitclock.repository.contract.RepositoryInterface;

import java.util.Date;
import java.util.List;

@Slf4j
public class ExportTableRepository implements RepositoryInterface<ExportTable> {

    @Override
    public ExportTable findById(Session session, long id) {
        return session.get(ExportTable.class, id);
    }

    @Override
    public List<ExportTable> findByType(Session session, int type) {
        return session.createQuery(
                        "from ExportTable e where e.exportType = :type order by e.exportDate desc",
                        ExportTable.class
                ).setParameter("type", type)
                .list();
    }

    @Override
    public ExportTable findByName(Session session, String fileName) {
        return session.createQuery(
                "from ExportTable e where e.fileName = :name order by e.exportDate desc",
                ExportTable.class
        ).setParameter("name", fileName)
                .list().get(0);
    }

    @Override
    public List<ExportTable> findByTypeNativeQuery(Session session, int type) {

        List<Object[]> rows = session.createNativeQuery("select id, data_date, export_date, export_status, export_type, file_name from export_table where export_type = :type order by export_date desc")
                .setParameter("type", type)
                .list();

        return rows.stream()
                .map(r -> new ExportTable(
                        ((Number) r[0]).intValue(),
                        (Date) r[1],
                        (Date) r[2],
                        ((Number) r[4]).intValue(),
                        ((Number) r[3]).intValue(),
                        (String) r[5]
                ))
                .toList();
    }

    @Override
    public ExportTable save(ExportTable entity) {
        //Use queue to write object to database
        var isSaved = Core.getInstance().getDbLogger().add(entity);
        if (isSaved) return entity;
        else return null;
    }

    @Override
    public void update(Session session, String fileName, byte[] info) {
        session.createMutationQuery(
                        "update ExportTable e " +
                                "set e.exportStatus = 3, e.file = :info " +
                                "where e.fileName = :name"
                )
                .setParameter("name", fileName)
                .setParameter("info", info)
                .executeUpdate();
    }

    @Override
    public boolean deleteById(Session session, long id) {
        var result = session.createMutationQuery(
                        "delete from ExportTable e where e.id = :id"
                )
                .setParameter("id", id)
                .executeUpdate();
        return result > 0;
    }

    @Override
    public void deleteByName(Session session, String fileName) {
        var result = session.createMutationQuery(
                        "delete from ExportTable e where e.fileName = :name"
                )
                .setParameter("name", fileName)
                .executeUpdate();
    }
}

