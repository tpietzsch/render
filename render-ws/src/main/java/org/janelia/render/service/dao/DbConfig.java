package org.janelia.render.service.dao;

import com.mongodb.ReadPreference;
import com.mongodb.ServerAddress;
import com.mongodb.connection.ConnectionPoolSettings;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Database connection configuration properties.
 *
 * @author Eric Trautman
 */
public class DbConfig {

    private final String connectionString;
    private final List<ServerAddress> serverAddressList;
    private final String userName;
    private final String authenticationDatabase;
    private final String password;
    private Integer maxConnectionsPerHost;
    private Integer maxConnectionIdleTime;
    private final ReadPreference readPreference;

    /**
     * Configure everything from a single connection string like:
     *
     * <pre>
     *     mongodb://temApp:password@render-mongodb2.int.janelia.org,render-mongodb3.int.janelia.org/?
     *               authSource=admin&maxPoolSize=100&maxIdleTimeMS=600000
     * </pre>
     *
     * The Connection String URI Format is documented at
     * <a href="https://docs.mongodb.com/manual/reference/connection-string">
     *   https://docs.mongodb.com/manual/reference/connection-string
     * </a>.
     *
     * @param  connectionString  connection string.
     */
    public DbConfig(final String connectionString) {
        this.connectionString = connectionString;
        this.serverAddressList = null;
        this.userName = null;
        this.authenticationDatabase = null;
        this.password = null;
        this.maxConnectionsPerHost = null;
        this.maxConnectionIdleTime = null;
        this.readPreference = null;
    }

    /**
     * Configure using the specified parameters.
     */
    public DbConfig(final List<ServerAddress> serverAddressList,
                    final String userName,
                    final String authenticationDatabase,
                    final String password,
                    final ReadPreference readPreference) {
        this.connectionString = null;
        this.serverAddressList = new ArrayList<>(serverAddressList);
        this.userName = userName;
        this.authenticationDatabase = authenticationDatabase;
        this.password = password;
        this.maxConnectionsPerHost = ConnectionPoolSettings.builder().build().getMaxSize(); // 100
        this.maxConnectionIdleTime = 600000; // 10 minutes
        this.readPreference = readPreference;
    }

    public boolean hasConnectionString() {
        return connectionString != null;
    }

    public String getConnectionString() {
        return connectionString;
    }

    public List<ServerAddress> getServerAddressList() {
        return serverAddressList;
    }

    public boolean hasCredentials() {
        return ((userName != null) && (authenticationDatabase != null) && (password != null));
    }

    public String getUserName() {
        return userName;
    }

    public String getAuthenticationDatabase() {
        return authenticationDatabase;
    }

    public char[] getPassword() {
        return password.toCharArray();
    }

    public int getMaxConnectionsPerHost() {
        return maxConnectionsPerHost;
    }

    public int getMaxConnectionIdleTime() {
        return maxConnectionIdleTime;
    }

    public ReadPreference getReadPreference() {
        return readPreference;
    }

    public static DbConfig fromFile(final File file)
            throws IllegalArgumentException {

        DbConfig dbConfig;
        final Properties properties = new Properties();

        final String path = file.getAbsolutePath();

        FileInputStream in = null;
        try {
            in = new FileInputStream(file);
            properties.load(in);

            final String connectionString = properties.getProperty("connectionString");

            if (connectionString == null) {

                LOG.info("fromFile: using explicit parameters specified in {}", file.getAbsolutePath());

                final String commaSeparatedServers = getRequiredProperty("servers", properties, path);
                final List<ServerAddress> serverAddressList = new ArrayList<>();
                int endHost;
                int startPort;
                int port;
                for (final String server : commaSeparatedServers.split(",")) {
                    endHost = server.indexOf(':');
                    startPort = endHost + 1;
                    if ((endHost > 0) && (server.length() > startPort)) {
                        try {
                            port = Integer.parseInt(server.substring(startPort));
                        } catch (final NumberFormatException e) {
                            throw new IllegalArgumentException("invalid port value for server address '" + server +
                                                               "' specified in " + path, e);
                        }
                        serverAddressList.add(new ServerAddress(server.substring(0, endHost), port));
                    } else {
                        serverAddressList.add(new ServerAddress(server));
                    }
                }

                final String userName = properties.getProperty("userName");
                String userNameSource = null;
                String password = null;
                if (userName == null) {
                    LOG.info("fromFile: skipping load of database credentials because no userName is defined in {}", path);
                } else {
                    userNameSource = getRequiredProperty("authenticationDatabase", properties, path);
                    password = getRequiredProperty("password", properties, path);
                }

                final String readPreferenceName = properties.getProperty("readPreference");
                ReadPreference readPreference = ReadPreference.primary();
                if (readPreferenceName != null) {
                    readPreference = ReadPreference.valueOf(readPreferenceName);
                }

                dbConfig = new DbConfig(serverAddressList, userName, userNameSource, password, readPreference);

                final String maxConnectionsPerHostStr = properties.getProperty("maxConnectionsPerHost");
                if (maxConnectionsPerHostStr != null) {
                    try {
                        dbConfig.maxConnectionsPerHost = Integer.parseInt(maxConnectionsPerHostStr);
                    } catch (final NumberFormatException e) {
                        throw new IllegalArgumentException("invalid maxConnectionsPerHost value (" +
                                                           maxConnectionsPerHostStr + ") specified in " + path, e);
                    }
                }

                final String maxConnectionIdleTimeStr = properties.getProperty("maxConnectionIdleTime");
                if (maxConnectionIdleTimeStr != null) {
                    try {
                        dbConfig.maxConnectionIdleTime = Integer.parseInt(maxConnectionIdleTimeStr);
                    } catch (final NumberFormatException e) {
                        throw new IllegalArgumentException("invalid maxConnectionIdleTime value (" +
                                                           maxConnectionIdleTimeStr + ") specified in " + path, e);
                    }
                }

            } else {

                LOG.info("fromFile: using connectionString specified in {}", file.getAbsolutePath());

                dbConfig = new DbConfig(connectionString);

            }

        } catch (final IllegalArgumentException e) {
            throw e;
        } catch (final Exception e) {
            throw new IllegalArgumentException("failed to load properties from " + path, e);
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (final IOException e) {
                    LOG.warn("failed to close " + path + ", ignoring error");
                }
            }
        }

        return dbConfig;
    }

    private static String getRequiredProperty(final String propertyName,
                                              final Properties properties,
                                              final String path)
            throws IllegalArgumentException {

        final String value = properties.getProperty(propertyName);
        if (value == null) {
            throw new IllegalArgumentException(propertyName + " value is missing from " + path);
        }
        return value;
    }

    private static final Logger LOG = LoggerFactory.getLogger(DbConfig.class);

}
