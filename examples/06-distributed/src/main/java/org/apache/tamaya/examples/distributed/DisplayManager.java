/*
 * Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package org.apache.tamaya.examples.distributed;

import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.json.Json;
import io.vertx.core.spi.cluster.ClusterManager;
import io.vertx.spi.cluster.hazelcast.HazelcastClusterManager;
import javafx.application.Application;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import org.apache.tamaya.spisupport.propertysource.EnvironmentPropertySource;
import org.apache.tamaya.spisupport.propertysource.SystemPropertySource;
import org.apache.tamaya.functions.ConfigurationFunctions;
import org.apache.tamaya.hazelcast.HazelcastPropertySource;
import org.apache.tamaya.inject.ConfigurationInjector;
import org.apache.tamaya.mutableconfig.MutableConfiguration;

import java.util.logging.Logger;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import org.apache.tamaya.Configuration;
import org.apache.tamaya.spi.ConfigurationBuilder;

/**
 * Created by atsticks on 12.11.16.
 */
public class DisplayManager extends Application{

    private static final Logger LOG = Logger.getLogger(DisplayManager.class.getSimpleName());

    public static final String DISPLAY_SHOW_TOPIC = "Display::show";
    public static final String DISPLAY_REGISTER_TOPIC = "Display::register";
    public static final String CONTENT_FIELD = "content";

    private Scene scene;

    private Group root = new Group();

    private TextField configFilterField = new TextField("");

    private TextArea configField = new TextArea();

    private TextArea monitorField = new TextArea("Nothing to monitor yet.");

    private StringBuffer monitorBuffer = new StringBuffer();

    private Vertx vertx;

    private static HazelcastPropertySource hazelCastPropertySource;

    public DisplayManager(){
        LOG.info("\n-----------------------------------\n" +
                "Starting DisplayDisplayManager...\n" +
                "-----------------------------------");
        LOG.info("--- Starting Vertx cluster...");
        // Reusing the hazelcast instance already in place for vertx...
        ClusterManager mgr = new HazelcastClusterManager(
                hazelCastPropertySource.getHazelcastInstance());
        VertxOptions vertxOptions = new VertxOptions().setClusterManager(mgr);
        Vertx.clusteredVertx(vertxOptions, h -> {
            vertx = h.result();
        });
        LOG.info("--- Waiting for Vertx cluster...");
        while(vertx==null){
            try {
                Thread.sleep(100L);
            } catch (InterruptedException e) {
                LOG.severe("Vertx cluster did not respond in time.");
                Thread.currentThread().interrupt();
            }
        }
        monitorField.getStyleClass().add("monitor");
        configField.getStyleClass().add("config");
    }

    @Override
    public void start(Stage stage) throws Exception {
        LOG.info("--- Configuring application...");
        ConfigurationInjector.getInstance()
                .configure(this);
        LOG.info("--- Starting stage...");
        initStage(stage);
        registerListeners();
        LOG.info("--- Showing stage...");
        stage.show();
        LOG.info("\n----------------------\n" +
                 "DisplayManager started\n" +
                 "----------------------");
    }

    private void registerListeners() {
        // registering update hook
        vertx.eventBus().consumer(DISPLAY_SHOW_TOPIC, h -> {
            DisplayContent content = Json.decodeValue((String)h.body(), DisplayContent.class);
            logToMonitor("NEW CONTENT: " + content.toString());
            logToMonitor("Updating config for content: " + content + "...");
            MutableConfiguration config = MutableConfiguration.create();
            String id = content.getDisplayId();
            config.put("displays."+id+".title", content.getTitle());
            config.put("displays."+id+".timestamp", String.valueOf(content.getTimestamp()));
            config.put("displays."+id+".content.content", content.getContent().get(CONTENT_FIELD));
            config.store();
            logToMonitor("UPDATED.");
        });
        vertx.eventBus().consumer(DISPLAY_REGISTER_TOPIC, h -> {
            DisplayRegistration registration = Json.decodeValue((String)h.body(), DisplayRegistration.class);
            if(registration.isUpdate()){
                logToMonitor("UDT DISPLAY: " + registration.getId());
            }else{
                logToMonitor("NEW DISPLAY: " + registration.toString());
            }
            MutableConfiguration config = MutableConfiguration.create();
            String id = registration.getId();
            config.put("displays."+id+".displayName", registration.getDisplayName());
            config.put("_displays."+id+".displayName.ttl", "10000");
            if(registration.getHost()!=null) {
                config.put("displays." + id + ".host", registration.getHost());
                config.put("_displays." + id + ".host.ttl", "10000");
            }
            config.put("displays."+id+".displayModel", registration.getDisplayModel());
            config.put("_displays."+id+".displayModel.ttl", "10000");
            config.store();
            logToMonitor("UPDATED.");
        });
    }

    private void initStage(Stage stage) {
        stage.setTitle("Display Manager");
        scene = new Scene(root, Color.RED);
        scene.getStylesheets().add("/stylesheet.css");

        BorderPane layout = new BorderPane();
        layout.getStyleClass().add("main-layout");
//        layout.setPrefSize(600, 400);
//        layout.setTop(createWinTitle());

        Node configPanel = createConfigNode();
        Node monitorPanel = createMonitorNode();

        TabPane tabPane = new TabPane();
        tabPane.getStylesheets().add("main-tabs");
        Tab tab0 = new Tab("Monitor", monitorPanel);
        tab0.setClosable(false);
        Tab tab1 = new Tab("Configuration", configPanel);
        tab1.setClosable(false);
        Tab tab2 = new Tab("Content Manager", new ContentManagerPanel(vertx));
        tab2.setClosable(false);
        tabPane.getTabs().add(0, tab0);
        tabPane.getTabs().add(1, tab1);
        tabPane.getTabs().add(2, tab2);
        layout.setCenter(tabPane);
        layout.setBottom(createStatusPane());
        scene.setRoot(layout);
        stage.setScene(scene);
    }

    private Node createStatusPane() {
        return new Label();
    }

    private Node createMonitorNode() {
        VBox vbox = new VBox();
        ScrollPane monitorPane = new ScrollPane(monitorField);
        monitorPane.setFitToHeight(true);
        monitorPane.setFitToWidth(true);
        monitorField.setPrefSize(2000,2000);
        vbox.getChildren().addAll(monitorPane);
        return vbox;
    }

    private Node createConfigNode() {
        VBox vbox = new VBox();
        ScrollPane contentPane = new ScrollPane(configField);
        contentPane.setFitToHeight(true);
        contentPane.setFitToWidth(true);
        configField.setPrefSize(2000,2000);
        vbox.getChildren().addAll(contentPane, createButtonPane());
        return vbox;
    }

    private Node createWinTitle() {
        Label winTitle = new Label();
        winTitle.setMinHeight(30.0);
        winTitle.setMinWidth(200.0);
        winTitle.setId("wintitle");
        winTitle.setText("Tamaya Config Demo - DisplayManager");
        return winTitle;
    }

    private Pane createButtonPane() {
        HBox buttonLayout = new HBox();
        buttonLayout.getStyleClass().add("button-pane");
        Button refreshConfig = new Button("Refresh Config");
        refreshConfig.setId("refreshConfig-button");
        refreshConfig.onActionProperty().set(h -> {
                showConfig();
            });
        configFilterField.onActionProperty().set(h -> {
            showConfig();
        });
        configFilterField.setId("configFilter-field");
        buttonLayout.getChildren().addAll(refreshConfig, configFilterField);
        return buttonLayout;
    }

    private void showConfig() {
        String filter = configFilterField.getText();
        String configAsText;
        if(filter!=null && !filter.trim().isEmpty()){
            configAsText = Configuration.current()
                    .map(ConfigurationFunctions.section(filter))
                    .adapt(ConfigurationFunctions.textInfo());
        }else{
            configAsText = Configuration.current()
                    .adapt(ConfigurationFunctions.textInfo());
        }
        configField.setText(configAsText);
    }

    public void logToMonitor(String message){
        if(!message.endsWith("\n")){
            monitorBuffer.append(message).append('\n');
        }else{
            monitorBuffer.append(message);
        }
        synchronized (monitorField) {
            monitorField.setText(monitorBuffer.toString());
        }
    }

    public static void main(String[] args) {
        // Programmatically setup our configuration
        hazelCastPropertySource = new HazelcastPropertySource();
        Configuration cfg = Configuration.current();
        ConfigurationBuilder builder = Configuration.createConfigurationBuilder()
                .setConfiguration(cfg);
        Configuration built = builder.addPropertySources(
                        new EnvironmentPropertySource(),
                        new SystemPropertySource(),
                        hazelCastPropertySource
                        )
                .addDefaultPropertyConverters()
                .build();
         Configuration.setCurrent(built);
        // Launch the app
        Application.launch(DisplayManager.class);
    }



}
