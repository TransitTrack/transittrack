package org.transitclock.repository;

import lombok.extern.slf4j.Slf4j;
import org.hibernate.Session;
import org.transitclock.Core;
import org.transitclock.domain.structs.ExportTable;
import org.transitclock.service.contract.RepositoryInterface;

import java.util.List;

@Slf4j
public class ExportTableRepository implements RepositoryInterface<ExportTable> {


    @Override
    public List<ExportTable> findAll(Session session) {
        return session.createQuery(
                "from ExportTable e order by e.exportDate desc",
                ExportTable.class
        ).list();
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
    public ExportTable findById(Session session, long id) {
        return session.get(ExportTable.class, id);
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

