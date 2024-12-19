package com.stock.utils;

import java.io.FileInputStream;
import java.io.IOException;
import java.sql.*;
import java.util.Properties;

public class DBUtils {
    private static Properties dbProperties;
    private static final String DEFAULT_HOST = "localhost";
    private static final String DEFAULT_PORT = "3306";
    private static final String DEFAULT_USER = "root";
    private static final String DEFAULT_PASSWORD = "root";
    private static final String DEFAULT_DB_NAME = "stock_db";
    
    static {
        loadDBProperties();
    }
    
    private static void loadDBProperties() {
        dbProperties = new Properties();
        String configPath = System.getProperty("user.dir") + "/db.properties";
        try (FileInputStream in = new FileInputStream(configPath)) {
            dbProperties.load(in);
        } catch (IOException e) {
            System.out.println("Warning: db.properties not found, using default configuration");
            setDefaultProperties();
        }
        
        // 验证必要的配置项是否存在，如果不存在则使用默认值
        validateAndSetDefaultIfNeeded();
    }
    
    private static void setDefaultProperties() {
        dbProperties.setProperty("db.host", DEFAULT_HOST);
        dbProperties.setProperty("db.port", DEFAULT_PORT);
        dbProperties.setProperty("db.user", DEFAULT_USER);
        dbProperties.setProperty("db.password", DEFAULT_PASSWORD);
        dbProperties.setProperty("db.name", DEFAULT_DB_NAME);
    }
    
    private static void validateAndSetDefaultIfNeeded() {
        if (dbProperties.getProperty("db.host") == null || dbProperties.getProperty("db.host").trim().isEmpty()) {
            dbProperties.setProperty("db.host", DEFAULT_HOST);
        }
        if (dbProperties.getProperty("db.port") == null || dbProperties.getProperty("db.port").trim().isEmpty()) {
            dbProperties.setProperty("db.port", DEFAULT_PORT);
        }
        if (dbProperties.getProperty("db.user") == null || dbProperties.getProperty("db.user").trim().isEmpty()) {
            dbProperties.setProperty("db.user", DEFAULT_USER);
        }
        if (dbProperties.getProperty("db.password") == null || dbProperties.getProperty("db.password").trim().isEmpty()) {
            dbProperties.setProperty("db.password", DEFAULT_PASSWORD);
        }
        if (dbProperties.getProperty("db.name") == null || dbProperties.getProperty("db.name").trim().isEmpty()) {
            dbProperties.setProperty("db.name", DEFAULT_DB_NAME);
        }
    }
    
    public static Connection getConnection() throws SQLException {
        // 每次获取连接时重新加载配置，以确保使用最新的配置
        loadDBProperties();
        
        String host = dbProperties.getProperty("db.host");
        String port = dbProperties.getProperty("db.port");
        String dbName = dbProperties.getProperty("db.name");
        String user = dbProperties.getProperty("db.user");
        String password = dbProperties.getProperty("db.password");
        
        String url = String.format("jdbc:mysql://%s:%s/%s?useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true",
                host, port, dbName);
        
//        System.out.println("Connecting to database: " + url);
        return DriverManager.getConnection(url, user, password);
    }
    
    public static void testConnection() throws SQLException {
        try (Connection conn = getConnection()) {
            if (!conn.isValid(5)) {
                throw new SQLException("数据库连接测试失败");
            }
            System.out.println("Database connection test successful");
        }
    }
    
    public static void close(Connection conn, Statement stmt, ResultSet rs) {
        try {
            if (rs != null) {
                rs.close();
            }
            if (stmt != null) {
                stmt.close();
            }
            if (conn != null) {
                conn.close();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
    
    public static Properties getDbProperties() {
        return dbProperties;
    }
} 