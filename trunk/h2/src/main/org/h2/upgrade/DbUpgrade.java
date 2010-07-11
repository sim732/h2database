/*
 * Copyright 2004-2010 H2 Group. Multiple-Licensed under the H2 License,
 * Version 1.0, and under the Eclipse Public License, Version 1.0
 * (http://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.upgrade;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Collections;
import java.util.Hashtable;
import java.util.Map;
import java.util.Properties;
import org.h2.jdbc.JdbcConnection;
import org.h2.message.DbException;
import org.h2.store.fs.FileSystem;
import org.h2.store.fs.FileSystemDisk;
import org.h2.util.Utils;

/**
 * This class starts the conversion from older database versions to the current
 * version if the respective classes are found.
 */
public class DbUpgrade {

    private static boolean v1_1ClassesPresent;

    private static Map<String, DbUpgradeNonPageStoreToCurrent> runningConversions;

    static {
        // static initialize block
        v1_1ClassesPresent = Utils.isClassPresent("org.h2.upgrade.v1_1.Driver");
        runningConversions = Collections.synchronizedMap(new Hashtable<String, DbUpgradeNonPageStoreToCurrent>(1));
    }
    
    public static boolean areV1_1ClassesPresent() {
        return v1_1ClassesPresent;
    }
    
    /**
     * Connects to an old 1.1 database
     * 
     * @param url The connection string
     * @param info The connection properties
     * @return the connection via the upgrade classes
     * @throws SQLException
     */
    public static Connection connectWithOldVersion(String url, Properties info) throws SQLException {
        try {
            String oldStartUrlPrefix = (String) Utils.getStaticField("org.h2.upgrade.v1_1.engine.Constants.START_URL");
            url = url.replaceAll(org.h2.engine.Constants.START_URL, oldStartUrlPrefix);
            url = url.replaceAll(";IGNORE_UNKNOWN_SETTINGS=TRUE", "");
            url = url.replaceAll(";IGNORE_UNKNOWN_SETTINGS=FALSE", "");
            url = url.replaceAll(";PAGE_STORE=TRUE", "");
            url += ";IGNORE_UNKNOWN_SETTINGS=TRUE";
            Object ci = Utils.newInstance("org.h2.upgrade.v1_1.engine.ConnectionInfo", url, info);
            boolean isRemote = (Boolean) Utils.callMethod(ci, "isRemote");
            boolean isPersistent = (Boolean) Utils.callMethod(ci, "isPersistent");
            String dbName = (String) Utils.callMethod(ci, "getName");
            // remove stackable file systems
            int colon = dbName.indexOf(':');
            while (colon != -1) {
                String fileSystemPrefix = dbName.substring(0, colon+1);
                FileSystem fs = FileSystem.getInstance(fileSystemPrefix);
                if (fs == null || fs instanceof FileSystemDisk) {
                    break;
                }
                dbName = dbName.substring(colon+1);
                colon = dbName.indexOf(':');
            }
            File oldDataFile = new File(dbName + ".data.db");
            if (!isRemote && isPersistent && oldDataFile.exists()) {
                Utils.callStaticMethod("org.h2.upgrade.v1_1.Driver.load");
                Connection connection = DriverManager.getConnection(url, info);
                return connection;
            } else {
                return null;
            }
        } catch (Exception e) {
            throw DbException.toSQLException(e);
        }
    }

    /**
     * Starts the conversion if the respective classes are found. Is automatically
     * called on connect.
     *
     * @param url The connection string
     * @param info The connection properties
     * @throws SQLException
     */
    public static synchronized void upgrade(String url, Properties info) throws SQLException {
        if (v1_1ClassesPresent) {
            upgradeFromNonPageStore(url, info);
        }
    }

    private static void upgradeFromNonPageStore(String url, Properties info) throws SQLException {
        if (runningConversions.containsKey(url)) {
            // do not migrate, because we are currently migrating, and this is
            // the connection where "runscript from" will be executed
            return;
        }
        try {
            DbUpgradeNonPageStoreToCurrent instance = new DbUpgradeNonPageStoreToCurrent(url, info);
            runningConversions.put(url, instance);
            instance.upgrade();
        } finally {
            runningConversions.remove(url);
        }
    }

}
