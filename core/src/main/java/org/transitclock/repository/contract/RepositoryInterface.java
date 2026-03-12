package org.transitclock.repository.contract;

import org.hibernate.Session;

import java.util.List;

public interface RepositoryInterface<T> {

    T save(T entity);

    T findById(Session session, long id);

    T findByName(Session session, String name);

    List<T> findByType(Session session, int type);

    List<T> findByTypeNativeQuery(Session session, int type);

    void update(Session session, String fileName, byte[] info);

    void deleteByName(Session session, String name);

    boolean deleteById(Session session, long id);
}
