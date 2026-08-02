package org.transitclock.core.dataCache;

import lombok.extern.slf4j.Slf4j;
import org.hibernate.Session;
import org.transitclock.domain.hibernate.HibernateUtils;
import org.transitclock.domain.structs.ExportTable;
import org.transitclock.repository.ExportTableRepository;
import org.transitclock.repository.contract.RepositoryInterface;
import org.transitclock.utils.Time;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class ExportTableCache {
    private final static ExportTableCache instance = new ExportTableCache();
    private final Map<Integer, List<ExportTable>> exportsMap = new ConcurrentHashMap<>();
    private final RepositoryInterface<ExportTable> exportRepo = new ExportTableRepository();
    private List<ExportTable> allExportTables = List.of();
    private volatile long lastCacheReadTimeFor1 = 0;
    private volatile long lastCacheReadTimeFor2 = 0;
    private volatile long lastCacheReadTimeFor3 = 0;

    private ExportTableCache() {
    }

    public static ExportTableCache getInstance() {
        return instance;
    }

    public List<ExportTable> getByType(int type) {
        if (type == 0) return allExportTables;
        updateCacheIfNeeded(type);
        return exportsMap.getOrDefault(type, List.of());
    }

    private void updateCacheIfNeeded(int type) {
        long now = System.currentTimeMillis();
        if (exportsMap.get(type) != null && now - getLastCacheReadTime(type) < Time.HOUR_IN_MSECS) {
            return;
        }
        try (var session = HibernateUtils.getSession()) {
            session.setDefaultReadOnly(true);
            reload(type, session);

            setLastCacheReadTime(type, now);
        } catch (Exception ex) {
            logger.error("{} - happened while fetching the exports to cache.", ex.getMessage());
            throw new RuntimeException(ex);
        }
    }

    private void reload(int type, Session session) {

        if (type > 1) exportsMap.put(type, new ArrayList<>(exportRepo.findByType(session, type)));
        else if (type == 1) exportsMap.put(type, new ArrayList<>(exportRepo.findByTypeNativeQuery(session, type)));

        allExportTables = List.copyOf(exportsMap.values().stream()
                .flatMap(List::stream)
                .toList());
    }

    public void addExportTable(ExportTable exportTable) {
        var type = exportTable.getExportType();
        List<ExportTable> modifableList;

        if (this.exportsMap.get(type) != null) this.exportsMap.get(type).add(exportTable);
        else {
            modifableList = new ArrayList<>();
            modifableList.add(exportTable);
            this.exportsMap.put(type, new ArrayList<>(modifableList));
        }
        updateExportsCache(type, false);
        logger.debug("Added export to cache.");
    }

    public void removeExportTable(ExportTable exportTable) {
        this.exportsMap.get(exportTable.getExportType())
                .removeIf(ex -> ex.getId() == exportTable.getId());
        logger.debug("Removed export '{}' from cache.", exportTable);
    }

    public void updateExportsCache(int type, boolean disableDelay) {

        long delay = type > 1 ? Time.MIN_IN_MSECS : (20 * Time.MIN_IN_MSECS);
        long timestamp = System.currentTimeMillis();

        new Thread(new Runnable() {
            @Override
            public void run() {
                Thread.currentThread().setName("exports-cache-" + Thread.currentThread().getId());

                try (var session = HibernateUtils.getSession()) {
                    if (!disableDelay) {
                        logger.info("Ran new thread: {}, for cache updating with delay: {} mins",
                                Thread.currentThread().getName(),
                                delay / Time.MIN_IN_MSECS);
                        Thread.sleep(delay);
                    }
                    session.setDefaultReadOnly(true);
                    reload(type, session);
                    setLastCacheReadTime(type, System.currentTimeMillis());
                    logger.info("The cache is just reloaded for type={} and took {} secs",
                            type, (System.currentTimeMillis() - timestamp) / 1000);

                } catch (Exception ex) {
                    logger.error("{} - happened while fetching the exports to cache.", ex.getMessage());
                    throw new RuntimeException(ex);
                }
            }
        }).start();
    }

    private long getLastCacheReadTime(int type) {
        if (type == 1) return lastCacheReadTimeFor1;
        else if (type == 2) return lastCacheReadTimeFor2;
        else if (type == 3) return lastCacheReadTimeFor3;
        else throw new RuntimeException("Unknown export type " + type);
    }

    private void setLastCacheReadTime(int type, long time) {
        if (type == 1) {
            lastCacheReadTimeFor1 = time;
        } else if (type == 2) {
            lastCacheReadTimeFor2 = time;
        } else if (type == 3) {
            lastCacheReadTimeFor3 = time;
        } else throw new RuntimeException("Unknown export type " + type);
    }
}
