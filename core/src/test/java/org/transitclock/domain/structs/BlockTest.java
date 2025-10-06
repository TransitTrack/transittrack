package org.transitclock.domain.structs;

import org.hibernate.Hibernate;
import org.hibernate.JDBCException;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.hibernate.collection.spi.PersistentList;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.transitclock.Core;
import org.transitclock.gtfs.DbConfig;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

public class BlockTest {

    private Block block;
    private PersistentList<?> mockTrips;
    private SharedSessionContractImplementor mockSessionImpl;
    private Session mockSession;
    private Transaction mockTx;

    // for Core.getInstance().getDbConfig()
    private Core mockCore;
    private DbConfig mockDbConfig;

    @BeforeEach
    void setUp() throws Exception {
        block = new Block();

        mockTrips = mock(PersistentList.class);
        mockSessionImpl = mock(SharedSessionContractImplementor.class);
        mockSession = mock(Session.class);
        mockTx = mock(Transaction.class);

        when(mockTx.isActive()).thenReturn(true);
        when(mockSessionImpl.getTransaction()).thenReturn(mockTx);

        when(mockTrips.getSession()).thenReturn(mockSessionImpl);

        Field tripsField = Block.class.getDeclaredField("trips");
        tripsField.setAccessible(true);
        tripsField.set(block, mockTrips);

        mockCore = mock(Core.class);
        mockDbConfig = mock(DbConfig.class);
        when(mockCore.getDbConfig()).thenReturn(mockDbConfig);
        when(mockDbConfig.getGlobalSession()).thenReturn(mockSession);
    }

    /**
     * Block.getTrips()
     */
    @Test
    void getTrips_whenJdbcException_thenRollbackTransaction() {
        JDBCException mockedJdbc = mock(JDBCException.class);
        when(mockedJdbc.getCause()).thenReturn(new RuntimeException("cancelled by user"));

        when(mockTrips.get(0)).thenThrow(mockedJdbc);

        try (MockedStatic<Hibernate> mockedHibernate = Mockito.mockStatic(Hibernate.class);
             MockedStatic<Core> mockedCoreStatic = Mockito.mockStatic(Core.class)) {

            mockedHibernate.when(() -> Hibernate.isInitialized(mockTrips)).thenReturn(false);

            mockedCoreStatic.when(Core::getInstance).thenReturn(mockCore);

            assertThrows(JDBCException.class, () -> block.getTrips());

            verify(mockTx, times(1)).rollback();
        }
    }
}

