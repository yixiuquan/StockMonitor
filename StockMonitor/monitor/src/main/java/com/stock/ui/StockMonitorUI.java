package com.stock.ui;

import cn.hutool.core.date.StopWatch;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import com.stock.utils.DBUtils;
import com.stock.vo.StockSpeed;
import lombok.extern.slf4j.Slf4j;

import java.sql.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Properties;
import java.io.*;
import java.util.List;

@Slf4j
public class StockMonitorUI extends Application {
    
    private TableView<StockData> realTimeTable;
    private TableView<StockData> historyTable;
    private Properties dbProperties;
    private TextField searchField;
    private TextField pageSizeField;
    private TabPane tabPane;
    private ObservableList<StockData> realTimeData = FXCollections.observableArrayList();
    
    @Override
    public void start(Stage primaryStage) {
        tabPane = new TabPane();
        
        // 实时数据标签页
        Tab realTimeTab = new Tab("实时数据");
        realTimeTab.setClosable(false);
        realTimeTab.setContent(createRealTimeContent());
        
        // 历史数据标签页
        Tab historyTab = new Tab("历史数据");
        historyTab.setClosable(false);
        historyTab.setContent(createHistoryContent());
        
        // 数据库配置标签页
        Tab dbConfigTab = new Tab("数据库配置");
        dbConfigTab.setClosable(false);
        dbConfigTab.setContent(createDBConfigContent());
        
        // 突破五日线标签页
        Tab breakMA5Tab = new Tab("突破五日线");
        breakMA5Tab.setClosable(false);
        breakMA5Tab.setContent(createBreakMA5Content());
        
        tabPane.getTabs().addAll(realTimeTab, historyTab, dbConfigTab, breakMA5Tab);
        
        // 使用BorderPane作为根容器
        BorderPane root = new BorderPane();
        root.setCenter(tabPane);
        
        // 设置TabPane填满整个窗口
        tabPane.prefWidthProperty().bind(root.widthProperty());
        tabPane.prefHeightProperty().bind(root.heightProperty());
        
        Scene scene = new Scene(root, 1200, 800);
        
        // 加载CSS样式
        String cssPath = getClass().getResource("/styles/table.css").toExternalForm();
        scene.getStylesheets().add(cssPath);
        
        // 设置窗口样式
        primaryStage.setTitle("监控系统");
        primaryStage.setScene(scene);
        
        // 设置最小窗口大小
        primaryStage.setMinWidth(1000);
        primaryStage.setMinHeight(600);
        
        // 设置窗口最大化
        primaryStage.setMaximized(true);
        
        primaryStage.show();
        
        // 加载数据库配置
        loadDBProperties();
        
        // 设置UI实例到主类
        com.stock.StockMonitorMain2.setUI(this);
    }
    
    private TableCell<StockData, Number> createMoneyCell() {
        return new TableCell<StockData, Number>() {
            @Override
            protected void updateItem(Number item, boolean empty) {
                super.updateItem(item, empty);
                // 清除所有样式类
                getStyleClass().removeAll("number", "positive", "negative");
                
                if (empty || item == null) {
                    setText(null);
                } else {
                    double value = item.doubleValue();
                    String formattedValue;
                    if (Math.abs(value) >= 100000000) { // 1亿
                        formattedValue = String.format("%.2f亿", value / 100000000);
                    } else {
                        formattedValue = String.format("%.2f万", value / 10000);
                    }
                    setText(formattedValue);
                    getStyleClass().add("number");
                    if (value > 0) {
                        getStyleClass().add("positive");
                    } else if (value < 0) {
                        getStyleClass().add("negative");
                    }
                }
            }
        };
    }
    
    private VBox createRealTimeContent() {
        VBox vbox = new VBox(5);
        vbox.setPadding(new Insets(10));
        vbox.setFillWidth(true);
        
        // 实时数据表格
        realTimeTable = new TableView<>();
        realTimeTable.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        
        // 设置表格填充属性
        VBox.setVgrow(realTimeTable, Priority.ALWAYS);
        realTimeTable.setMaxHeight(Double.MAX_VALUE);
        realTimeTable.setMaxWidth(Double.MAX_VALUE);
        realTimeTable.setMinHeight(Region.USE_COMPUTED_SIZE);
        realTimeTable.setMinWidth(Region.USE_COMPUTED_SIZE);
        realTimeTable.setPrefHeight(Region.USE_COMPUTED_SIZE);
        realTimeTable.setPrefWidth(Region.USE_COMPUTED_SIZE);
        
        // 设置表格行高
        realTimeTable.setFixedCellSize(35);
        
        // 设置表格的占位符
        Label placeholder = new Label("暂无数据");
        placeholder.setStyle("-fx-font-size: 14px; -fx-text-fill: #666666;");
        realTimeTable.setPlaceholder(placeholder);
        
        // 设置表格选择模式
        realTimeTable.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        realTimeTable.setEditable(false);
        
        // 创建列并绑定数据
        TableColumn<StockData, String> codeCol = new TableColumn<>("代码");
        codeCol.setCellValueFactory(cellData -> cellData.getValue().codeProperty());
        codeCol.setPrefWidth(100);
        
        TableColumn<StockData, String> nameCol = new TableColumn<>("名称");
        nameCol.setCellValueFactory(cellData -> cellData.getValue().nameProperty());
        nameCol.setPrefWidth(120);
        
        TableColumn<StockData, Number> priceCol = new TableColumn<>("价格");
        priceCol.setCellValueFactory(cellData -> cellData.getValue().currentPriceProperty());
        priceCol.setCellFactory(column -> new TableCell<StockData, Number>() {
            @Override
            protected void updateItem(Number item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    getStyleClass().removeAll("number");
                } else {
                    setText(String.format("%.2f", item.doubleValue()));
                    getStyleClass().add("number");
                }
            }
        });
        priceCol.setPrefWidth(100);
        
        TableColumn<StockData, Number> inflowCol = new TableColumn<>("净流入");
        inflowCol.setCellValueFactory(cellData -> cellData.getValue().netInflowProperty());
        inflowCol.setCellFactory(column -> createMoneyCell());
        inflowCol.setPrefWidth(120);
        
        TableColumn<StockData, Number> changeCol = new TableColumn<>("涨跌幅");
        changeCol.setCellValueFactory(cellData -> cellData.getValue().changePercentProperty());
        changeCol.setCellFactory(column -> new TableCell<StockData, Number>() {
            @Override
            protected void updateItem(Number item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    getStyleClass().removeAll("number");
                } else {
                    setText(String.format("%6.2f%%", item.doubleValue()));
                    getStyleClass().add("number");
                }
            }
        });
        changeCol.setMinWidth(100);
        
        TableColumn<StockData, Number> mainForceCol = new TableColumn<>("主力占比");
        mainForceCol.setCellValueFactory(cellData -> cellData.getValue().mainForcePercentProperty());
        mainForceCol.setCellFactory(column -> new TableCell<StockData, Number>() {
            @Override
            protected void updateItem(Number item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    getStyleClass().removeAll("number");
                } else {
                    setText(String.format("%6.2f%%", item.doubleValue()));
                    getStyleClass().add("number");
                }
            }
        });
        mainForceCol.setMinWidth(100);
        
        TableColumn<StockData, Number> momentumCol = new TableColumn<>("动量分");
        momentumCol.setCellValueFactory(cellData -> cellData.getValue().momentumScoreProperty());
        momentumCol.setCellFactory(column -> new TableCell<StockData, Number>() {
            @Override
            protected void updateItem(Number item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    getStyleClass().removeAll("number");
                } else {
                    setText(String.format("%5.1f", item.doubleValue()));
                    getStyleClass().add("number");
                }
            }
        });
        momentumCol.setMinWidth(80);
        
        TableColumn<StockData, Number> upScoreCol = new TableColumn<>("上涨");
        upScoreCol.setCellValueFactory(cellData -> cellData.getValue().upTrendScoreProperty());
        upScoreCol.setCellFactory(column -> new TableCell<StockData, Number>() {
            @Override
            protected void updateItem(Number item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    getStyleClass().removeAll("number");
                } else {
                    setText(String.format("%4.1f", item.doubleValue()));
                    getStyleClass().add("number");
                }
            }
        });
        upScoreCol.setMinWidth(80);
        
        TableColumn<StockData, Number> downScoreCol = new TableColumn<>("下跌");
        downScoreCol.setCellValueFactory(cellData -> cellData.getValue().downTrendScoreProperty());
        downScoreCol.setCellFactory(column -> new TableCell<StockData, Number>() {
            @Override
            protected void updateItem(Number item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    getStyleClass().removeAll("number");
                } else {
                    setText(String.format("%4.1f", item.doubleValue()));
                    getStyleClass().add("number");
                }
            }
        });
        downScoreCol.setMinWidth(80);
        
        TableColumn<StockData, Number> volumeCol = new TableColumn<>("成交量");
        volumeCol.setCellValueFactory(cellData -> cellData.getValue().volumeProperty());
        volumeCol.setCellFactory(column -> createMoneyCell());
        volumeCol.setPrefWidth(120);
        
        TableColumn<StockData, Number> lurkingCol = new TableColumn<>("潜伏差值");
        lurkingCol.setCellValueFactory(cellData -> cellData.getValue().lurkingValueProperty());
        lurkingCol.setCellFactory(column -> createMoneyCell());
        lurkingCol.setPrefWidth(120);
        
        realTimeTable.getColumns().addAll(
            codeCol, nameCol, priceCol, inflowCol, changeCol,
            mainForceCol, momentumCol, upScoreCol, downScoreCol,
            volumeCol, lurkingCol
        );
        
        vbox.getChildren().add(realTimeTable);
        return vbox;
    }
    
    private VBox createHistoryContent() {
        VBox vbox = new VBox(5);
        vbox.setPadding(new Insets(10));
        vbox.setFillWidth(true);
        
        // 搜索区域
        HBox searchBox = new HBox(10);
        searchBox.setPadding(new Insets(0, 0, 10, 0));
        searchBox.setAlignment(Pos.CENTER_LEFT);
        searchBox.getStyleClass().add("search-box");
        
        searchField = new TextField();
        searchField.setPromptText("输入股票代码或名称");
        searchField.setPrefWidth(200);
        searchField.getStyleClass().add("search-field");
        
        Button searchBtn = new Button("搜索");
        searchBtn.getStyleClass().add("search-button");
        searchBtn.setOnAction(e -> searchHistoryData(1)); // 从第1页开始搜索
        
        // 分页控制区域
        HBox pageBox = new HBox(10);
        pageBox.setAlignment(Pos.CENTER);

        Button pageSizeBtn = new Button("页数:");
        pageSizeField = new TextField();
        pageSizeField.setMaxWidth(80);
        pageSizeField.setText(50+"");
        pageSizeField.getStyleClass().add("search-field");
        Button prevBtn = new Button("上一页");
        Label pageLabel = new Label("第1页");
        Button nextBtn = new Button("下一页");
        
        prevBtn.setOnAction(e -> {
            int currentPage = Integer.parseInt(pageLabel.getText().replaceAll("第|页", ""));
            if (currentPage > 1) {
                searchHistoryData(currentPage - 1);
                pageLabel.setText("第" + (currentPage - 1) + "页");
            }
        });
        
        nextBtn.setOnAction(e -> {
            int currentPage = Integer.parseInt(pageLabel.getText().replaceAll("第|页", ""));
            searchHistoryData(currentPage + 1);
            pageLabel.setText("第" + (currentPage + 1) + "页");
        });
        
        pageBox.getChildren().addAll(pageSizeBtn, pageSizeField, prevBtn, pageLabel, nextBtn);
        
        searchBox.getChildren().addAll( searchField, searchBtn, pageBox);
        
        // 历史数据表格
        historyTable = new TableView<>();
        historyTable.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        
        // 设置表格填充属性
        VBox.setVgrow(historyTable, Priority.ALWAYS);
        historyTable.setMaxHeight(Double.MAX_VALUE);
        historyTable.setMaxWidth(Double.MAX_VALUE);
        historyTable.setMinHeight(Region.USE_COMPUTED_SIZE);
        historyTable.setMinWidth(Region.USE_COMPUTED_SIZE);
        historyTable.setPrefHeight(Region.USE_COMPUTED_SIZE);
        historyTable.setPrefWidth(Region.USE_COMPUTED_SIZE);
        
        // 设置表格行高
        historyTable.setFixedCellSize(35);
        
        // 设置表格的占位符
        Label placeholder = new Label("暂无数据");
        placeholder.setStyle("-fx-font-size: 14px; -fx-text-fill: #666666;");
        historyTable.setPlaceholder(placeholder);
        
        // 设置表格选择模式
        historyTable.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        historyTable.setEditable(false);
        
        // 创建列并绑定数据
        TableColumn<StockData, String> dateCol = new TableColumn<>("日期");
        dateCol.setCellValueFactory(cellData -> cellData.getValue().createTimeProperty());
        dateCol.setPrefWidth(180);
        
        TableColumn<StockData, String> codeCol = new TableColumn<>("代码");
        codeCol.setCellValueFactory(cellData -> cellData.getValue().codeProperty());
        codeCol.setPrefWidth(100);
        
        TableColumn<StockData, String> nameCol = new TableColumn<>("名称");
        nameCol.setCellValueFactory(cellData -> cellData.getValue().nameProperty());
        nameCol.setPrefWidth(120);
        
        TableColumn<StockData, Number> priceCol = new TableColumn<>("价格");
        priceCol.setCellValueFactory(cellData -> cellData.getValue().currentPriceProperty());
        priceCol.setCellFactory(column -> new TableCell<StockData, Number>() {
            @Override
            protected void updateItem(Number item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    getStyleClass().removeAll("number");
                } else {
                    setText(String.format("%8.2f", item.doubleValue()));
                    getStyleClass().add("number");
                }
            }
        });
        priceCol.setPrefWidth(100);
        
        TableColumn<StockData, Number> inflowCol = new TableColumn<>("净流入");
        inflowCol.setCellValueFactory(cellData -> cellData.getValue().netInflowProperty());
        inflowCol.setCellFactory(column -> createMoneyCell());
        inflowCol.setPrefWidth(120);
        
        TableColumn<StockData, Number> inflowDiffCol = new TableColumn<>("较上条净流入");
        inflowDiffCol.setCellValueFactory(cellData -> cellData.getValue().inflowDiffProperty());
        inflowDiffCol.setCellFactory(column -> createMoneyCell());
        inflowDiffCol.setPrefWidth(120);
        
        TableColumn<StockData, Number> mainForceInflowCol = new TableColumn<>("主力净流入");
        mainForceInflowCol.setCellValueFactory(cellData -> cellData.getValue().mainForceInflowProperty());
        mainForceInflowCol.setCellFactory(column -> createMoneyCell());
        mainForceInflowCol.setPrefWidth(120);
        
        TableColumn<StockData, Number> superLargeInflowCol = new TableColumn<>("超大单净流入");
        superLargeInflowCol.setCellValueFactory(cellData -> cellData.getValue().superLargeInflowProperty());
        superLargeInflowCol.setCellFactory(column -> createMoneyCell());
        superLargeInflowCol.setPrefWidth(120);
        
        TableColumn<StockData, Number> otherInflowCol = new TableColumn<>("其他净流入");
        otherInflowCol.setCellValueFactory(cellData -> cellData.getValue().otherInflowProperty());
        otherInflowCol.setCellFactory(column -> createMoneyCell());
        otherInflowCol.setPrefWidth(120);
        
        TableColumn<StockData, Number> changeCol = new TableColumn<>("涨跌幅");
        changeCol.setCellValueFactory(cellData -> cellData.getValue().changePercentProperty());
        changeCol.setCellFactory(column -> new TableCell<StockData, Number>() {
            @Override
            protected void updateItem(Number item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    getStyleClass().removeAll("number", "positive", "negative");
                } else {
                    setText(String.format("%6.2f%%", item.doubleValue()));
                    getStyleClass().add("number");
                    if (item.doubleValue() > 0) {
                        getStyleClass().add("positive");
                    } else if (item.doubleValue() < 0) {
                        getStyleClass().add("negative");
                    }
                }
            }
        });
        changeCol.setPrefWidth(100);
        
        historyTable.getColumns().addAll(
            dateCol, codeCol, nameCol, priceCol, inflowCol, inflowDiffCol,
            mainForceInflowCol, superLargeInflowCol, otherInflowCol, changeCol
        );
        
        vbox.getChildren().addAll(searchBox, historyTable);
        return vbox;
    }
    
    private void searchHistoryData(int page) {

        String searchText = searchField.getText().trim();
        if (searchText.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "请输入搜索内容");
            return;
        }

        int PAGE_SIZE = 50;
        String pageSizeText = pageSizeField.getText().trim();
        if (!pageSizeText.isEmpty()) {
            PAGE_SIZE = Integer.parseInt(pageSizeText);
        }
        int offset = (page - 1) * PAGE_SIZE;
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        StopWatch sw = new StopWatch("获取数据任务");
        try {
            conn = DBUtils.getConnection();
            String sql = "SELECT a.*, " +
                        "(a.total_net_inflow - IFNULL(b.total_net_inflow, 0)) as inflow_diff, " +
                        "(a.bigdan_net_inflow + a.zhongdan_net_inflow + a.xiaodan_net_inflow) as other_inflow " +
                        "FROM single_stock_data a " +
                        "LEFT JOIN single_stock_data b ON a.code = b.code " +
                        "AND b.create_time = (SELECT MAX(create_time) FROM single_stock_data c " +
                        "WHERE c.code = a.code AND c.create_time < a.create_time) " +
                        "WHERE a.code LIKE ? OR a.name LIKE ? " +
                        "ORDER BY a.create_time DESC " +
                        "LIMIT ? OFFSET ?";
            ps = conn.prepareStatement(sql);
            ps.setString(1, "%" + searchText + "%");
            ps.setString(2, "%" + searchText + "%");
            ps.setInt(3, PAGE_SIZE);
            ps.setInt(4, offset);
            sw.start("执行查询");
            rs = ps.executeQuery();
            sw.stop();
            sw.start("循环解析数据");
            ObservableList<StockData> data = FXCollections.observableArrayList();
            while (rs.next()) {
                StockData stockData = new StockData();
                stockData.setCode(rs.getString("code"));
                stockData.setName(rs.getString("name"));
                stockData.setCurrentPrice(rs.getDouble("current_price"));
                stockData.setNetInflow(rs.getDouble("total_net_inflow"));
                stockData.setChangePercent(rs.getDouble("change_percent"));
                stockData.setMainForcePercent(rs.getDouble("zhuli_net_inflow_percent"));
                stockData.setVolume(rs.getDouble("total_volume"));
                stockData.setCreateTime(rs.getString("create_time"));
                
                // 设置增字段的值
                stockData.setInflowDiff(rs.getDouble("inflow_diff"));
                stockData.setMainForceInflow(rs.getDouble("zhuli_net_inflow"));
                stockData.setSuperLargeInflow(rs.getDouble("chaodadan_net_inflow"));
                stockData.setOtherInflow(rs.getDouble("other_inflow"));
                
                data.add(stockData);
            }
            historyTable.setItems(data);
        } catch (SQLException e) {
            showAlert(Alert.AlertType.ERROR, "查询失败: " + e.getMessage());
        } finally {
            DBUtils.close(conn, ps, rs);
        }
        sw.stop();
        sw.setKeepTaskList(true); //是否构建TaskInfo信息
        Arrays.stream(sw.getTaskInfo()).forEach(stopWatch -> log.info(stopWatch.getTaskName() + "耗时:" + stopWatch.getTimeMillis() + "ms"));
        log.info("详细打印：" + sw.prettyPrint());
        log.info("所有任务总耗时：" + sw.getTotalTimeMillis() + "ms");
    }
    
    private VBox createDBConfigContent() {
        VBox vbox = new VBox(15);
        vbox.setPadding(new Insets(20));
        vbox.setFillWidth(true);
        vbox.setAlignment(Pos.TOP_LEFT);
        vbox.setStyle("-fx-background-color: white;");
        
        // 标题
        Label titleLabel = new Label("数据库配置");
        titleLabel.setStyle("-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: #333333;");
        
        // 创建网格布局来放置输入框和标签
        GridPane gridPane = new GridPane();
        gridPane.setHgap(15);
        gridPane.setVgap(15);
        gridPane.setPadding(new Insets(25, 0, 25, 0));
        gridPane.setStyle("-fx-background-color: white;");
        
        // 样式
        String labelStyle = "-fx-font-size: 14px; -fx-text-fill: #666666;";
        String fieldStyle = "-fx-font-size: 14px; -fx-pref-width: 300px; -fx-padding: 8 12; " +
                          "-fx-background-color: white; -fx-border-color: #dddddd; " +
                          "-fx-border-radius: 4; -fx-background-radius: 4;";
        
        // 数据库主机
        Label hostLabel = new Label("数据库主机：");
        hostLabel.setStyle(labelStyle);
        TextField hostField = new TextField();
        hostField.setStyle(fieldStyle);
        hostField.setPromptText("localhost");
        gridPane.add(hostLabel, 0, 0);
        gridPane.add(hostField, 1, 0);
        
        // 端口
        Label portLabel = new Label("端口：");
        portLabel.setStyle(labelStyle);
        TextField portField = new TextField();
        portField.setStyle(fieldStyle);
        portField.setPromptText("3306");
        gridPane.add(portLabel, 0, 1);
        gridPane.add(portField, 1, 1);
        
        // 用户名
        Label userLabel = new Label("用户名：");
        userLabel.setStyle(labelStyle);
        TextField userField = new TextField();
        userField.setStyle(fieldStyle);
        userField.setPromptText("root");
        gridPane.add(userLabel, 0, 2);
        gridPane.add(userField, 1, 2);
        
        // 密码
        Label passLabel = new Label("密码：");
        passLabel.setStyle(labelStyle);
        PasswordField passField = new PasswordField();
        passField.setStyle(fieldStyle);
        gridPane.add(passLabel, 0, 3);
        gridPane.add(passField, 1, 3);
        
        // 数据库名
        Label dbNameLabel = new Label("数据库名：");
        dbNameLabel.setStyle(labelStyle);
        TextField dbNameField = new TextField();
        dbNameField.setStyle(fieldStyle);
        dbNameField.setPromptText("stock_db");
        gridPane.add(dbNameLabel, 0, 4);
        gridPane.add(dbNameField, 1, 4);
        
        // 按钮样式
        String buttonStyle = "-fx-font-size: 14px; -fx-min-width: 120px; -fx-min-height: 35px; " +
                           "-fx-background-radius: 4; -fx-cursor: hand;";
        String primaryButtonStyle = buttonStyle + 
                                  "-fx-background-color: #4a90e2; -fx-text-fill: white;";
        String secondaryButtonStyle = buttonStyle + 
                                    "-fx-background-color: white; -fx-text-fill: #4a90e2; " +
                                    "-fx-border-color: #4a90e2; -fx-border-radius: 4;";
        
        // 按钮器
        HBox buttonBox = new HBox(15);
        buttonBox.setAlignment(Pos.CENTER_LEFT);
        buttonBox.setPadding(new Insets(10, 0, 0, 0));
        
        // 保存配置按钮
        Button saveBtn = new Button("保存配置");
        saveBtn.setStyle(primaryButtonStyle);
        saveBtn.setOnAction(e -> saveDBConfig(
            hostField.getText(),
            portField.getText(),
            userField.getText(),
            passField.getText(),
            dbNameField.getText()
        ));
        
        // 测试连接按钮
        Button testBtn = new Button("测试连接");
        testBtn.setStyle(secondaryButtonStyle);
        testBtn.setOnAction(e -> testConnection());
        
        // 初始化数据库按钮
        Button initBtn = new Button("初始化数据库");
        initBtn.setStyle(secondaryButtonStyle);
        initBtn.setOnAction(e -> initializeDatabase());
        
        buttonBox.getChildren().addAll(saveBtn, testBtn, initBtn);
        
        vbox.getChildren().addAll(titleLabel, gridPane, buttonBox);
        
        // 加载现有配置
        loadDBProperties();
        if (dbProperties != null) {
            hostField.setText(dbProperties.getProperty("db.host", ""));
            portField.setText(dbProperties.getProperty("db.port", ""));
            userField.setText(dbProperties.getProperty("db.user", ""));
            passField.setText(dbProperties.getProperty("db.password", ""));
            dbNameField.setText(dbProperties.getProperty("db.name", ""));
        }
        
        return vbox;
    }
    
    private void saveDBConfig(String host, String port, String user, String password, String dbName) {
        Properties props = new Properties();
        props.setProperty("db.host", host);
        props.setProperty("db.port", port);
        props.setProperty("db.user", user);
        props.setProperty("db.password", password);
        props.setProperty("db.name", dbName);
        
        try (FileOutputStream out = new FileOutputStream("db.properties")) {
            props.store(out, "Database Configuration");
            showAlert(Alert.AlertType.INFORMATION, "配置保存成功");
        } catch (IOException e) {
            showAlert(Alert.AlertType.ERROR, "配置保存失败: " + e.getMessage());
        }
    }
    
    private void loadDBProperties() {
        dbProperties = new Properties();
        try (FileInputStream in = new FileInputStream("db.properties")) {
            dbProperties.load(in);
        } catch (IOException e) {
            // 如果文件不存在，使用默认值
            dbProperties.setProperty("db.host", "localhost");
            dbProperties.setProperty("db.port", "3306");
            dbProperties.setProperty("db.user", "root");
            dbProperties.setProperty("db.password", "");
            dbProperties.setProperty("db.name", "stock_db");
        }
    }
    
    private void testConnection() {
        try {
            DBUtils.testConnection();
            showAlert(Alert.AlertType.INFORMATION, "数据库连接成功！");
        } catch (SQLException e) {
            showAlert(Alert.AlertType.ERROR, "数据库连接失败: " + e.getMessage());
        }
    }
    
    private void initializeDatabase() {
        try {
            Connection conn = DBUtils.getConnection();
            Statement stmt = conn.createStatement();
            
            // 创建数据表的SQL
            String createTableSQL = "CREATE TABLE IF NOT EXISTS single_stock_data (" +
                "id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '自增主键'," +
                "code VARCHAR(10) NOT NULL COMMENT '股票代码'," +
                "name VARCHAR(50) NOT NULL COMMENT '股票名称'," +
                "current_price DECIMAL(10,2) NOT NULL COMMENT '当前价格'," +
                "zhuli_net_inflow DECIMAL(20,2) COMMENT '今日主力净流入'," +
                "zhuli_net_inflow_percent DECIMAL(10,4) COMMENT '今日主力净流百分比'," +
                "total_net_inflow DECIMAL(20,2) COMMENT '总净流入'," +
                "total_net_inflow_percent DECIMAL(10,4) COMMENT '总净流入百分比'," +
                "chaodadan_net_inflow DECIMAL(20,2) COMMENT '超大单净流入'," +
                "chaodadan_net_inflow_percent DECIMAL(10,4) COMMENT '超大单净流入百分比'," +
                "bigdan_net_inflow DECIMAL(20,2) COMMENT '大单净流入'," +
                "bigdan_net_inflow_percent DECIMAL(10,4) COMMENT '大单净流入百分比'," +
                "zhongdan_net_inflow DECIMAL(20,2) COMMENT '中单净流入'," +
                "zhongdan_net_inflow_percent DECIMAL(10,4) COMMENT '中单净流入百分比'," +
                "xiaodan_net_inflow DECIMAL(20,2) COMMENT '小单净流入'," +
                "xiaodan_net_inflow_percent DECIMAL(10,4) COMMENT '小单净流入百分比'," +
                "change_amount DECIMAL(10,2) COMMENT '涨跌额'," +
                "change_percent DECIMAL(10,4) COMMENT '涨跌幅'," +
                "total_volume DECIMAL(20,2) COMMENT '总成交量'," +
                "create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'," +
                "update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'," +
                "INDEX idx_code (code)," +
                "INDEX idx_create_time (create_time)," +
                "INDEX idx_code_create_time (code, create_time)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='股票实时数据表'";
            
            stmt.execute(createTableSQL);
            showAlert(Alert.AlertType.INFORMATION, "数据库初始化成功！");
            
        } catch (SQLException e) {
            showAlert(Alert.AlertType.ERROR, "数据库初始化失败: " + e.getMessage());
        }
    }
    
    private void showAlert(Alert.AlertType type, String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(type);
            alert.setTitle("提示");
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }
    
    private VBox createBreakMA5Content() {
        VBox vbox = new VBox(5);
        vbox.setPadding(new Insets(10));
        vbox.setFillWidth(true);
        
        // 创建刷新按钮
        Button refreshBtn = new Button("刷新数据");
        refreshBtn.setStyle("-fx-font-size: 14px; -fx-min-width: 100px; -fx-min-height: 35px; " +
                          "-fx-background-color: #4a90e2; -fx-text-fill: white; " +
                          "-fx-background-radius: 4; -fx-cursor: hand;");
        refreshBtn.setOnAction(e -> refreshBreakMA5Data());
        
        // 创建表格
        TableView<StockData> breakMA5Table = new TableView<>();
        breakMA5Table.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        
        // 设置表格填充属性
        VBox.setVgrow(breakMA5Table, Priority.ALWAYS);
        breakMA5Table.setMaxHeight(Double.MAX_VALUE);
        breakMA5Table.setMaxWidth(Double.MAX_VALUE);
        breakMA5Table.setMinHeight(Region.USE_COMPUTED_SIZE);
        breakMA5Table.setMinWidth(Region.USE_COMPUTED_SIZE);
        breakMA5Table.setPrefHeight(Region.USE_COMPUTED_SIZE);
        breakMA5Table.setPrefWidth(Region.USE_COMPUTED_SIZE);
        
        // 设置表格行高
        breakMA5Table.setFixedCellSize(35);
        
        // 设置表格的占位符
        Label placeholder = new Label("暂无数据");
        placeholder.setStyle("-fx-font-size: 14px; -fx-text-fill: #666666;");
        breakMA5Table.setPlaceholder(placeholder);
        
        // 创建列
        TableColumn<StockData, String> codeCol = new TableColumn<>("代码");
        codeCol.setCellValueFactory(cellData -> cellData.getValue().codeProperty());
        codeCol.setPrefWidth(100);
        
        TableColumn<StockData, String> nameCol = new TableColumn<>("名称");
        nameCol.setCellValueFactory(cellData -> cellData.getValue().nameProperty());
        nameCol.setPrefWidth(120);
        
        TableColumn<StockData, Number> priceCol = new TableColumn<>("当前价格");
        priceCol.setCellValueFactory(cellData -> cellData.getValue().currentPriceProperty());
        priceCol.setCellFactory(column -> new TableCell<StockData, Number>() {
            @Override
            protected void updateItem(Number item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    getStyleClass().removeAll("number");
                } else {
                    setText(String.format("%8.2f", item.doubleValue()));
                    getStyleClass().add("number");
                }
            }
        });
        priceCol.setPrefWidth(100);

        TableColumn<StockData, Number> changeCol = new TableColumn<>("涨跌幅");
        changeCol.setCellValueFactory(cellData -> cellData.getValue().changePercentProperty());
        changeCol.setCellFactory(column -> new TableCell<StockData, Number>() {
            @Override
            protected void updateItem(Number item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    getStyleClass().removeAll("number", "positive", "negative");
                } else {
                    setText(String.format("%6.2f%%", item.doubleValue()));
                    getStyleClass().add("number");
                    if (item.doubleValue() > 0) {
                        getStyleClass().add("positive");
                    } else if (item.doubleValue() < 0) {
                        getStyleClass().add("negative");
                    }
                }
            }
        });
        changeCol.setPrefWidth(100);

        TableColumn<StockData, Number> netInflowCol = new TableColumn<>("净流入");
        netInflowCol.setCellValueFactory(cellData -> cellData.getValue().netInflowProperty());
        netInflowCol.setCellFactory(column -> new TableCell<StockData, Number>() {
            @Override
            protected void updateItem(Number item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    getStyleClass().removeAll("number", "positive", "negative");
                } else {
                    setText(String.format("%10.2f", item.doubleValue()));
                    getStyleClass().add("number");
                    if (item.doubleValue() > 0) {
                        getStyleClass().add("positive");
                    } else if (item.doubleValue() < 0) {
                        getStyleClass().add("negative");
                    }
                }
            }
        });
        netInflowCol.setPrefWidth(120);

        TableColumn<StockData, Number> mainForceCol = new TableColumn<>("主力净流入");
        mainForceCol.setCellValueFactory(cellData -> cellData.getValue().mainForceInflowProperty());
        mainForceCol.setCellFactory(column -> new TableCell<StockData, Number>() {
            @Override
            protected void updateItem(Number item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    getStyleClass().removeAll("number", "positive", "negative");
                } else {
                    setText(String.format("%10.2f", item.doubleValue()));
                    getStyleClass().add("number");
                    if (item.doubleValue() > 0) {
                        getStyleClass().add("positive");
                    } else if (item.doubleValue() < 0) {
                        getStyleClass().add("negative");
                    }
                }
            }
        });
        mainForceCol.setPrefWidth(120);

        TableColumn<StockData, Number> superLargeCol = new TableColumn<>("超大单净流入");
        superLargeCol.setCellValueFactory(cellData -> cellData.getValue().superLargeInflowProperty());
        superLargeCol.setCellFactory(column -> new TableCell<StockData, Number>() {
            @Override
            protected void updateItem(Number item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    getStyleClass().removeAll("number", "positive", "negative");
                } else {
                    setText(String.format("%10.2f", item.doubleValue()));
                    getStyleClass().add("number");
                    if (item.doubleValue() > 0) {
                        getStyleClass().add("positive");
                    } else if (item.doubleValue() < 0) {
                        getStyleClass().add("negative");
                    }
                }
            }
        });
        superLargeCol.setPrefWidth(120);

        TableColumn<StockData, Number> otherCol = new TableColumn<>("其他净流入");
        otherCol.setCellValueFactory(cellData -> cellData.getValue().otherInflowProperty());
        otherCol.setCellFactory(column -> new TableCell<StockData, Number>() {
            @Override
            protected void updateItem(Number item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    getStyleClass().removeAll("number", "positive", "negative");
                } else {
                    setText(String.format("%10.2f", item.doubleValue()));
                    getStyleClass().add("number");
                    if (item.doubleValue() > 0) {
                        getStyleClass().add("positive");
                    } else if (item.doubleValue() < 0) {
                        getStyleClass().add("negative");
                    }
                }
            }
        });
        otherCol.setPrefWidth(120);
        
        breakMA5Table.getColumns().addAll(
            codeCol, nameCol, priceCol, changeCol, netInflowCol,
            mainForceCol, superLargeCol, otherCol
        );
        
        vbox.getChildren().addAll(refreshBtn, breakMA5Table);
        
        return vbox;
    }
    
    private void refreshBreakMA5Data() {
        String sql = "";
        Connection conn = null;
        Statement stmt = null;
        ResultSet rs = null;
        try {
            conn = DBUtils.getConnection();
            
            // 修改SQL查询，使用正确的关联方式
            sql = 
                "SELECT t.code, t.name, t.current_price, t.change_percent, t.total_net_inflow, t.zhuli_net_inflow, t.chaodadan_net_inflow, t.other_inflow  " +
                  "FROM (SELECT s1.code, s1.name, s1.current_price, s1.change_percent, s1.total_net_inflow, s1.zhuli_net_inflow, s1.chaodadan_net_inflow, " +
                  "        (s1.bigdan_net_inflow + s1.zhongdan_net_inflow + s1.xiaodan_net_inflow) AS other_inflow, " +
                  "        (SELECT AVG(current_price) FROM single_stock_data WHERE code = s1.code AND create_time <= s1.create_time ORDER BY create_time DESC LIMIT 5 ) AS ma5  " +
                  "    FROM single_stock_data s1 " +
                  "   INNER JOIN (SELECT code, MAX(create_time) AS max_time FROM single_stock_data GROUP BY code) latest ON s1.code = latest.code  " +
                  "        AND s1.create_time = latest.max_time  " +
                  "    ) t  " +
                  "WHERE " +
                  "    t.current_price > t.ma5  " +
                  "    AND EXISTS ( " +
                  "    SELECT 1  " +
                  "    FROM single_stock_data s3  " +
                  "    WHERE " +
                  "        s3.code = t.code  " +
                  "        AND s3.create_time < ( " +
                  "            SELECT MAX(create_time)  " +
                  "            FROM single_stock_data  " +
                  "            WHERE code = t.code " +
                  "        )  " +
                  "        AND s3.current_price < ( " +
                  "            SELECT AVG(current_price)  " +
                  "            FROM single_stock_data  " +
                  "            WHERE  " +
                  "                code = s3.code  " +
                  "                AND create_time <= s3.create_time  " +
                  "            ORDER BY create_time DESC  " +
                  "            LIMIT 5 " +
                  "        )  " +
                  "    ORDER BY s3.create_time DESC  " +
                  "    LIMIT 1  " +
                  "    )  " +
                  "ORDER BY t.code";
            
            stmt = conn.createStatement();
            rs = stmt.executeQuery(sql);
            
            ObservableList<StockData> data = FXCollections.observableArrayList();
            while (rs.next()) {
                StockData stockData = new StockData();
                stockData.setCode(rs.getString("code"));
                stockData.setName(rs.getString("name"));
                stockData.setCurrentPrice(rs.getDouble("current_price"));
                stockData.setChangePercent(rs.getDouble("change_percent"));
                stockData.setNetInflow(rs.getDouble("total_net_inflow"));
                stockData.setMainForceInflow(rs.getDouble("zhuli_net_inflow"));
                stockData.setSuperLargeInflow(rs.getDouble("chaodadan_net_inflow"));
                stockData.setOtherInflow(rs.getDouble("other_inflow"));
                data.add(stockData);
            }
            
            // 获取表格引用并更新数据
            TableView<StockData> breakMA5Table = (TableView<StockData>) ((VBox) tabPane.getTabs().get(3).getContent()).getChildren().get(1);
            breakMA5Table.setItems(data);
            
        } catch (SQLException e) {
            showAlert(Alert.AlertType.ERROR, "查询失败: " + e.getMessage());
            log.error("查询失败：{}", sql);
        } catch (Exception e) {
            showAlert(Alert.AlertType.ERROR, "系统错误: " + e.getMessage());
            log.error("系统错误：", e);
        } finally {
            DBUtils.close(conn, stmt, rs);
        }
    }
    
    public void updateRealTimeData(List<StockSpeed> speedResults) {
        // 在后台线程中准备数据
        List<StockData> newData = new ArrayList<>();
        for (StockSpeed result : speedResults) {
            StockData stockData = new StockData();
            stockData.setCode(result.getCode());
            stockData.setName(result.getName());
            stockData.setCurrentPrice(result.getCurrentPrice());
            stockData.setNetInflow(result.getZhuliNetInflow());
            stockData.setChangePercent(result.getChangePercent());
            stockData.setMainForcePercent(result.getZhuliNetInflowPercent());
            stockData.setMomentumScore(result.getMomentumScore());
            stockData.setUpTrendScore(result.getRankUpTrendScore());
            stockData.setDownTrendScore(result.getRankDownTrendScore());
            stockData.setVolume(result.getVolume());
            newData.add(stockData);
        }
        
        // 只在UI线程中执行最终的更新
        Platform.runLater(() -> {
            realTimeData.clear();
            realTimeData.addAll(newData);
            if (realTimeTable.getItems() != realTimeData) {
                realTimeTable.setItems(realTimeData);
            }
        });
    }
    
    public static void main(String[] args) {
        launch(args);
    }
} 