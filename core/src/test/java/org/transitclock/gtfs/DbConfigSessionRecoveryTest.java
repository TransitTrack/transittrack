package org.transitclock.gtfs;

import org.hibernate.Session;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.transitclock.domain.hibernate.HibernateUtils;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.*;

/**
 * Guards the session-recovery behaviour of {@link DbConfig}. When the global session dies (db
 * reboot/failover) it must be recovered by opening a fresh session from the existing
 * SessionFactory. It must NOT tear down the whole SessionFactory, because that aborts in-flight
 * statements of every other thread sharing the connection pool (the "canceling statement due to
 * user request" / "this statement has been closed" cascade).
 */
public class DbConfigSessionRecoveryTest {

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = DbConfig.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    /** Recovering the session must not close the SessionFactory shared by other threads. */
    @Test
    void createNewGlobalSession_doesNotCloseSessionFactory() {
        DbConfig dbConfig = mock(DbConfig.class);
        doCallRealMethod().when(dbConfig).createNewGlobalSession();
        doCallRealMethod().when(dbConfig).getGlobalSession();

        Session freshSession = mock(Session.class);
        when(freshSession.isOpen()).thenReturn(true);

        try (MockedStatic<HibernateUtils> hibernate = Mockito.mockStatic(HibernateUtils.class)) {
            hibernate.when(() -> HibernateUtils.getSession(nullable(String.class))).thenReturn(freshSession);

            dbConfig.createNewGlobalSession();

            // A brand new session was opened from the existing factory...
            assertSame(freshSession, dbConfig.getGlobalSession());
            hibernate.verify(() -> HibernateUtils.getSession(nullable(String.class)), times(1));
            // ...and the factory (and thus other threads' connections) was left intact.
            hibernate.verify(HibernateUtils::clearSessionFactory, never());
        }
    }

    /** A stale/closed global session must be transparently replaced on the next access. */
    @Test
    void getGlobalSession_replacesClosedSession() throws Exception {
        DbConfig dbConfig = mock(DbConfig.class);
        doCallRealMethod().when(dbConfig).getGlobalSession();

        Session staleSession = mock(Session.class);
        when(staleSession.isOpen()).thenReturn(false);
        setField(dbConfig, "globalSession", staleSession);

        Session freshSession = mock(Session.class);
        when(freshSession.isOpen()).thenReturn(true);

        try (MockedStatic<HibernateUtils> hibernate = Mockito.mockStatic(HibernateUtils.class)) {
            hibernate.when(() -> HibernateUtils.getSession(nullable(String.class))).thenReturn(freshSession);

            assertSame(freshSession, dbConfig.getGlobalSession());
            hibernate.verify(HibernateUtils::clearSessionFactory, never());
        }
    }
}