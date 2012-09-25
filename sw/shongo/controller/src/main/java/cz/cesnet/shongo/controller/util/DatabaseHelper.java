package cz.cesnet.shongo.controller.util;

import org.hibernate.Session;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.service.jdbc.connections.spi.ConnectionProvider;
import org.hsqldb.util.DatabaseManagerSwing;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.persistence.EntityManager;
import java.awt.event.ActionEvent;
import java.awt.event.WindowEvent;
import java.sql.Connection;

/**
 * Database helper.
 *
 * @author Martin Srom <martin.srom@cesnet.cz>
 */
public class DatabaseHelper
{
    private static Logger logger = LoggerFactory.getLogger(DatabaseHelper.class);

    /**
     * @param entityManager
     * @return {@link DatabaseManagerSwing}
     */
    public static DatabaseManagerSwing runDatabaseManager(EntityManager entityManager)
    {
        DatabaseManagerSwing databaseManager;
        try {
            databaseManager = new DatabaseManagerSwing(){
                @Override
                public void windowClosed(WindowEvent windowEvent)
                {
                    setVisible(false);
                    super.windowClosed(windowEvent);
                }
            };
            databaseManager.main();
        }
        catch (Exception exception) {
            logger.error("Cannot start database manager!", exception);
            return null;
        }

        try {
            Session session = (Session) entityManager.getDelegate();
            SessionFactoryImplementor sessionFactory = (SessionFactoryImplementor) session.getSessionFactory();
            ConnectionProvider connectionProvider = sessionFactory.getConnectionProvider();
            Connection connection = connectionProvider.getConnection();
            databaseManager.connect(connection);
        }
        catch (Exception exception) {
            logger.error("Cannot connect to current database!", exception);
        }
        return databaseManager;
    }

    /**
     * Run database managed and Wait for it to close.
     */
    public static void runDatabaseManagerAndWait(EntityManager entityManager)
    {
        DatabaseManagerSwing databaseManager = runDatabaseManager(entityManager);
        while (databaseManager.isVisible()) {
            try {
                Thread.sleep(500);
            }
            catch (InterruptedException exception) {
                logger.error("Failed to wait for database manager to close.", exception);
            }
        }
    }
}
