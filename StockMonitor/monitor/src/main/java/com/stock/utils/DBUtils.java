package com.stock.utils;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.IOException;
import java.sql.*;
import java.util.Properties;

public class DBUtils {
    private static final Logger logger = LoggerFactory.getLogger(DBUtils.class);
    private static Properties dbProperties;
    private static final String DEFAULT_HOST = "localhost";
    private static final String DEFAULT_PORT = "3306";
    private static final String DEFAULT_USER = "root";
    private static final String DEFAULT_PASSWORD = "123456";
    private static final String DEFAULT_DB_NAME = "stock";
    
    private static volatile HikariDataSource dataSource;
    private static final Object lock = new Object();
    
    static {
        try {
            loadDBProperties();
            initDataSource();
        } catch (Exception e) {
            logger.error("Failed to initialize DBUtils", e);
            throw new RuntimeException("Failed to initialize DBUtils", e);
        }
    }
    
    private static void loadDBProperties() {
        dbProperties = new Properties();
        String configPath = System.getProperty("user.dir") + "/db.properties";
        System.out.println("尝试加载数据库配置文件: " + configPath);
        
        try (FileInputStream in = new FileInputStream(configPath)) {
            dbProperties.load(in);
            System.out.println("成功加载数据库配置文件");
            logger.info("Successfully loaded database properties from {}", configPath);
        } catch (IOException e) {
            System.out.println("未找到数据库配置文件，使用默认配置");
            logger.warn("db.properties not found at {}, using default configuration", configPath);
            setDefaultProperties();
        }
        
        validateAndSetDefaultIfNeeded();
    }
    
    private static void setDefaultProperties() {
        dbProperties.setProperty("db.host", DEFAULT_HOST);
        dbProperties.setProperty("db.port", DEFAULT_PORT);
        dbProperties.setProperty("db.user", DEFAULT_USER);
        dbProperties.setProperty("db.password", DEFAULT_PASSWORD);
        dbProperties.setProperty("db.name", DEFAULT_DB_NAME);
        System.out.println("已设置默认数据库配置");
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
        
        System.out.println("数据库配置信息:");
        System.out.println("Host: " + dbProperties.getProperty("db.host"));
        System.out.println("Port: " + dbProperties.getProperty("db.port"));
        System.out.println("Database: " + dbProperties.getProperty("db.name"));
        System.out.println("User: " + dbProperties.getProperty("db.user"));
    }
    
    private static void initDataSource() {
        if (dataSource == null) {
            synchronized (lock) {
                if (dataSource == null) {
                    try {
                        System.out.println("开始初始化数据库连接池...");
                        
                        HikariConfig config = new HikariConfig();
                        String host = dbProperties.getProperty("db.host");
                        String port = dbProperties.getProperty("db.port");
                        String dbName = dbProperties.getProperty("db.name");
                        String user = dbProperties.getProperty("db.user");
                        String password = dbProperties.getProperty("db.password");
                        
                        String url = String.format("jdbc:mysql://%s:%s/%s?useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&rewriteBatchedStatements=true",
                                host, port, dbName);
                        
                        config.setJdbcUrl(url);
                        config.setUsername(user);
                        config.setPassword(password);
                        
                        // 优化连接池配置
                        config.setPoolName("StockMonitorPool");
                        config.setMaximumPoolSize(100);        // 增加最大连接数
                        config.setMinimumIdle(20);            // 增加最小空闲连接数
                        config.setIdleTimeout(300000);        // 空闲连接超时时间5分钟
                        config.setConnectionTimeout(60000);    // 连接超时时间1分钟
                        config.setMaxLifetime(1800000);       // 连接最大生命周期30分钟
                        config.setValidationTimeout(3000);    // 缩短验证超时时间
                        config.setLeakDetectionThreshold(60000); // 连接泄漏检测
                        config.setAutoCommit(true);
                        
                        // 性能优化配置
                        config.addDataSourceProperty("cachePrepStmts", "true");
                        config.addDataSourceProperty("prepStmtCacheSize", "250");
                        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
                        config.addDataSourceProperty("useServerPrepStmts", "true");
                        config.addDataSourceProperty("useLocalSessionState", "true");
                        config.addDataSourceProperty("rewriteBatchedStatements", "true");
                        config.addDataSourceProperty("cacheResultSetMetadata", "true");
                        config.addDataSourceProperty("cacheServerConfiguration", "true");
                        config.addDataSourceProperty("elideSetAutoCommits", "true");
                        config.addDataSourceProperty("maintainTimeStats", "false");
                        config.addDataSourceProperty("useUnicode", "true");
                        config.addDataSourceProperty("characterEncoding", "utf8");
                        config.addDataSourceProperty("useSSL", "false");
                        
                        // 创建数据源
                        dataSource = new HikariDataSource(config);
                        System.out.println("数据库连接池初始化成功");
                        
                        // 测试连接
                        testConnection();
                    } catch (Exception e) {
                        System.err.println("初始化数据库连接池失败: " + e.getMessage());
                        logger.error("Failed to initialize connection pool", e);
                        throw new RuntimeException("Failed to initialize database connection pool", e);
                    }
                }
            }
        }
    }
    
    public static Connection getConnection() throws SQLException {
        int maxRetries = 3;
        int retryCount = 0;
        int retryDelay = 1000; // 1秒

        while (retryCount < maxRetries) {
            try {
                if (dataSource == null || dataSource.isClosed()) {
                    initDataSource();
                }
                Connection conn = dataSource.getConnection();
                if (conn.isValid(3)) {
                    return conn;
                }
            } catch (SQLException e) {
                retryCount++;
                if (retryCount == maxRetries) {
                    logger.error("Failed to get database connection after " + maxRetries + " attempts", e);
                    throw e;
                }
                logger.warn("Failed to get connection, retrying... (Attempt " + retryCount + " of " + maxRetries + ")");
                try {
                    Thread.sleep(retryDelay * retryCount);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new SQLException("Connection retry interrupted", ie);
                }
            }
        }
        throw new SQLException("Failed to get database connection after " + maxRetries + " attempts");
    }
    
    public static void close(Connection conn, Statement stmt, ResultSet rs) {
        if (rs != null) {
            try {
                rs.close();
            } catch (SQLException e) {
                logger.error("Failed to close ResultSet", e);
            }
        }
        if (stmt != null) {
            try {
                stmt.close();
            } catch (SQLException e) {
                logger.error("Failed to close Statement", e);
            }
        }
        if (conn != null) {
            try {
                conn.close(); // 这里实际上是将连接返回到连接池
            } catch (SQLException e) {
                logger.error("Failed to close Connection", e);
            }
        }
    }
    
    public static void shutdown() {
        if (dataSource != null && !dataSource.isClosed()) {
            try {
                dataSource.close();
                System.out.println("数据库连接池已关闭");
            } catch (Exception e) {
                logger.error("Error while shutting down connection pool", e);
            } finally {
                dataSource = null;
            }
        }
    }
    
    public static void testConnection() throws SQLException {
        try (Connection conn = getConnection()) {
            if (!conn.isValid(5)) {
                throw new SQLException("Database connection test failed");
            }
            
            // 测试数据库表是否存在
            DatabaseMetaData metaData = conn.getMetaData();
            ResultSet tables = metaData.getTables(null, null, "single_stock_data", null);
            if (!tables.next()) {
                System.err.println("警告: single_stock_data表不存在，尝试创建...");
                createTable(conn);
            }
        }
    }
    
    private static void createTable(Connection conn) throws SQLException {
        String createTableSQL = "CREATE TABLE IF NOT EXISTS single_stock_data (" +
            "id BIGINT AUTO_INCREMENT PRIMARY KEY," +
            "code VARCHAR(10) NOT NULL," +
            "name VARCHAR(50)," +
            "current_price DECIMAL(10,2)," +
            "change_amount DECIMAL(10,2)," +
            "change_percent DECIMAL(10,2)," +
            "zhuli_net_inflow DECIMAL(20,2)," +
            "zhuli_net_inflow_percent DECIMAL(10,2)," +
            "total_net_inflow DECIMAL(20,2)," +
            "total_net_inflow_percent DECIMAL(10,2)," +
            "chaodadan_net_inflow DECIMAL(20,2)," +
            "chaodadan_net_inflow_percent DECIMAL(10,2)," +
            "bigdan_net_inflow DECIMAL(20,2)," +
            "bigdan_net_inflow_percent DECIMAL(10,2)," +
            "zhongdan_net_inflow DECIMAL(20,2)," +
            "zhongdan_net_inflow_percent DECIMAL(10,2)," +
            "xiaodan_net_inflow DECIMAL(20,2)," +
            "xiaodan_net_inflow_percent DECIMAL(10,2)," +
            "total_volume DECIMAL(20,2)," +
            "create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
            "INDEX idx_code (code)," +
            "INDEX idx_create_time (create_time)" +
            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";
        
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(createTableSQL);
            System.out.println("成功创建single_stock_data表");
        }
    }
} 