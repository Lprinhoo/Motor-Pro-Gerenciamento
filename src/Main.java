import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.prefs.Preferences;

public class Main {
    public static void main(String[] args) {
        Application.launch(App.class, args);
    }

    public static class App extends Application {
        private static final String BASE_URL = "https://api-java-production-5e77.up.railway.app/api/";
        private static final HttpClient client = HttpClient.newBuilder()
                .connectTimeout(java.time.Duration.ofSeconds(15)).build();
        private static final Gson gson = new Gson();

        private StackPane masterPanel;
        private StackPane dashboardContent;
        private VBox sidebarButtons;

        private static final Preferences PREFS = Preferences.userNodeForPackage(Main.class);
        private String workshopId = PREFS.get("workshopId", "");

        private String currentActiveTab = "AGENDAMENTOS";
        private final Map<String, Parent> screenCache = new HashMap<>();
        private final Map<String, RefreshParams> tabRefreshMap = new HashMap<>();

        private static class RefreshParams {
            String endpoint; TableView<Map<String, Object>> tableView; String[] jsonKeys;
            RefreshParams(String e, TableView<Map<String, Object>> tv, String[] k) {
                this.endpoint = e; this.tableView = tv; this.jsonKeys = k;
            }
        }

        @Override
        public void start(Stage primaryStage) {
            loadFonts();
            masterPanel = new StackPane();
            masterPanel.getStyleClass().add("master-panel");

            // Carregamento do CSS via Resource
            try {
                URL cssUrl = getClass().getResource("/assets/style.css");
                if (cssUrl != null) {
                    masterPanel.getStylesheets().add(cssUrl.toExternalForm());
                }
            } catch (Exception e) {
                System.err.println("Erro ao carregar CSS: " + e.getMessage());
            }

            Scene scene = new Scene(masterPanel, 1200, 800);
            primaryStage.setTitle("Motor Pro | Management System");

            try {
                InputStream is = getClass().getResourceAsStream("/assets/perfil.png");
                if (is != null) primaryStage.getIcons().add(new Image(is));
            } catch (Exception ignored) {}

            primaryStage.setScene(scene);
            primaryStage.setMaximized(true);
            primaryStage.show();

            showScreen("LAUNCH");
        }

        private void showScreen(String screenName) {
            Platform.runLater(() -> {
                masterPanel.getChildren().forEach(node -> node.setVisible(false));
                Parent target = screenCache.get(screenName);

                if (target == null) {
                    switch (screenName) {
                        case "LAUNCH": target = createLaunchScreen(); break;
                        case "LOGIN": target = createLoginScreen(); break;
                        case "REGISTER": target = createConfigScreen(true); break;
                        case "DASHBOARD": target = createDashboardStructure(); break;
                    }
                    if (target != null) {
                        screenCache.put(screenName, target);
                        masterPanel.getChildren().add(target);
                    }
                }
                if (target != null) {
                    target.setVisible(true);
                    target.toFront();
                }
            });
        }

        private Parent createLaunchScreen() {
            VBox card = new VBox(30);
            card.setAlignment(Pos.CENTER);
            card.getStyleClass().add("dashboard-content");

            Label logo = new Label("MOTOR PRO");
            logo.getStyleClass().add("content-title");

            Button btnLogin = createBigButton("ENTRAR NA MINHA OFICINA", "big-button-primary");
            btnLogin.setOnAction(e -> {
                if (!workshopId.isEmpty()) showScreen("DASHBOARD");
                else showScreen("LOGIN");
            });

            Button btnRegister = createBigButton("CADASTRAR NOVA OFICINA", "big-button-secondary");
            btnRegister.setOnAction(e -> {
                workshopId = "";
                PREFS.put("workshopId", "");
                screenCache.remove("REGISTER");
                showScreen("REGISTER");
            });

            card.getChildren().addAll(logo, btnLogin, btnRegister);
            return card;
        }

        private Parent createLoginScreen() {
            VBox container = new VBox();
            container.setAlignment(Pos.CENTER);
            container.getStyleClass().add("dashboard-content");

            VBox card = new VBox(25);
            card.setAlignment(Pos.CENTER);
            card.setMaxSize(450, 400);
            card.getStyleClass().add("form-card");

            Label title = new Label("Acesse sua Oficina");
            title.getStyleClass().add("form-card-title");

            TextField txtTel = createStyledTextField("Digite o Telefone da Oficina", "text-field-styled");
            txtTel.setMaxWidth(350);

            Button btnEntrar = createBigButton("ACESSAR SISTEMA", "big-button-primary");
            btnEntrar.setPrefWidth(350);
            btnEntrar.setOnAction(e -> {
                String tel = txtTel.getText().trim();
                if (tel.isEmpty()) {
                    showAlert(Alert.AlertType.WARNING, "Aviso", "O telefone é obrigatório.");
                    return;
                }
                btnEntrar.setText("BUSCANDO...");
                btnEntrar.setDisable(true);

                client.sendAsync(HttpRequest.newBuilder().uri(URI.create(BASE_URL + "oficinas")).build(),
                                HttpResponse.BodyHandlers.ofString())
                        .thenApply(HttpResponse::body)
                        .thenAccept(json -> Platform.runLater(() -> {
                            try {
                                List<Map<String, Object>> items = gson.fromJson(json, new TypeToken<List<Map<String, Object>>>(){}.getType());
                                String foundId = "";
                                if (items != null) {
                                    for (Map<String, Object> item : items) {
                                        if (tel.equals(String.valueOf(item.get("telefone")))) {
                                            foundId = String.valueOf(item.get("id"));
                                            break;
                                        }
                                    }
                                }
                                if (!foundId.isEmpty()) {
                                    workshopId = foundId;
                                    PREFS.put("workshopId", workshopId);
                                    showScreen("DASHBOARD");
                                } else {
                                    showAlert(Alert.AlertType.ERROR, "Erro", "Oficina não encontrada.");
                                    btnEntrar.setText("ACESSAR SISTEMA");
                                    btnEntrar.setDisable(false);
                                }
                            } catch (Exception ex) {
                                btnEntrar.setDisable(false);
                                btnEntrar.setText("ACESSAR SISTEMA");
                            }
                        }));
            });

            Button btnBack = new Button("Voltar ao início");
            btnBack.getStyleClass().add("sidebar-button");
            btnBack.setOnAction(e -> showScreen("LAUNCH"));

            card.getChildren().addAll(title, txtTel, btnEntrar, btnBack);
            container.getChildren().add(card);
            return container;
        }

        private Parent createConfigScreen(boolean isNew) {
            BorderPane main = new BorderPane();
            main.getStyleClass().add("dashboard-content");
            main.setPadding(new Insets(50));

            HBox header = new HBox(20);
            header.setAlignment(Pos.CENTER_LEFT);
            Label lblT = new Label(isNew ? "NOVA OFICINA" : "MEUS DADOS");
            lblT.getStyleClass().add("content-title");

            Button btnSave = new Button("SALVAR ALTERAÇÕES");
            btnSave.getStyleClass().add("big-button-primary");
            btnSave.setPrefSize(240, 50);

            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            header.getChildren().addAll(lblT, spacer, btnSave);
            main.setTop(header);

            GridPane grid = new GridPane();
            grid.setHgap(30);
            grid.setVgap(20);
            grid.setPadding(new Insets(30, 0, 0, 0));

            VBox c1 = createFormCard("INFORMAÇÕES BÁSICAS");
            TextField txtNome = createStyledTextField("NOME DA OFICINA", "text-field-styled");
            TextField txtEndereco = createStyledTextField("ENDEREÇO COMPLETO", "text-field-styled");
            TextField txtTelefone = createStyledTextField("TELEFONE (ACESSO)", "text-field-styled");
            TextField txtLat = createStyledTextField("LATITUDE", "text-field-styled");
            TextField txtLon = createStyledTextField("LONGITUDE", "text-field-styled");

            c1.getChildren().addAll(txtNome, txtEndereco, txtTelefone, txtLat, txtLon);
            grid.add(c1, 0, 0);

            VBox c2 = createFormCard("SERVIÇOS PRESTADOS");
            String[] std = {"Freios", "Suspensão", "Motor", "Óleo", "Revisão", "Elétrica", "Alinhamento", "Pneus"};
            List<CheckBox> cbs = new java.util.ArrayList<>();
            VBox cg = new VBox(10);
            cg.setPadding(new Insets(10));
            cg.setStyle("-fx-background-color: white;");
            for (String s : std) {
                CheckBox b = new CheckBox(s);
                cbs.add(b);
                cg.getChildren().add(b);
            }
            ScrollPane sp = new ScrollPane(cg);
            sp.setFitToWidth(true);
            sp.setPrefHeight(300);
            sp.getStyleClass().add("table-view");
            c2.getChildren().add(sp);
            grid.add(c2, 1, 0);

            main.setCenter(grid);

            btnSave.setOnAction(e -> {
                String nomeInput = txtNome.getText().trim();
                String telInput = txtTelefone.getText().trim();
                if (nomeInput.isEmpty() || telInput.isEmpty()) {
                    showAlert(Alert.AlertType.WARNING, "Aviso", "Preencha os campos obrigatórios.");
                    return;
                }

                btnSave.setText("PROCESSANDO...");
                btnSave.setDisable(true);

                StringJoiner sj = new StringJoiner(",");
                for (CheckBox b : cbs) if (b.isSelected()) sj.add(b.getText());

                Map<String, Object> data = new HashMap<>();
                data.put("nome", nomeInput);
                data.put("endereco", txtEndereco.getText().trim());
                data.put("telefone", telInput);
                data.put("servicos", sj.toString());
                try {
                    data.put("latitude", Double.parseDouble(txtLat.getText().trim()));
                    data.put("longitude", Double.parseDouble(txtLon.getText().trim()));
                } catch (Exception ex) {
                    data.put("latitude", 0.0); data.put("longitude", 0.0);
                }

                String method = isNew ? "POST" : "PUT";
                String endpoint = isNew ? "oficinas" : "oficinas/" + workshopId;

                sendToAPI(method, endpoint, data).thenAccept(ok -> Platform.runLater(() -> {
                    if (ok) {
                        showAlert(Alert.AlertType.INFORMATION, "Sucesso", "Dados salvos!");
                        showScreen("DASHBOARD");
                    } else {
                        showAlert(Alert.AlertType.ERROR, "Erro", "Erro ao salvar.");
                        btnSave.setText("SALVAR ALTERAÇÕES");
                        btnSave.setDisable(false);
                    }
                }));
            });

            autoDetectLocation(txtEndereco, txtLat, txtLon);
            return main;
        }

        private BorderPane createDashboardStructure() {
            BorderPane dash = new BorderPane();

            VBox sidebar = new VBox();
            sidebar.setPrefWidth(280);
            sidebar.getStyleClass().add("sidebar");

            sidebarButtons = new VBox(10);
            sidebarButtons.setAlignment(Pos.TOP_LEFT);

            addMenuButton("📅", "AGENDAMENTOS", "appointments/oficina/" + workshopId,
                    new String[]{"Serviço", "Data/Hora", "Status"},
                    new String[]{"servico", "dataHora", "status"});

            addMenuButton("🔍", "OFICINAS PARCEIRAS", "oficinas",
                    new String[]{"Nome", "Endereço", "Telefone"},
                    new String[]{"nome", "endereco", "telefone"});

            Button btnConfig = createSidebarBtn("⚙️", "CONFIGURAÇÕES");
            btnConfig.setOnAction(e -> {
                currentActiveTab = "CONFIG_OFICINA";
                showDashboardContent(createConfigScreen(false), "CONFIG_OFICINA");
                highlightButton(btnConfig);
            });
            sidebarButtons.getChildren().add(btnConfig);

            Region spacer = new Region();
            VBox.setVgrow(spacer, Priority.ALWAYS);
            sidebarButtons.getChildren().add(spacer);

            Button btnLogout = createSidebarBtn("🚪", "SAIR DO SISTEMA");
            btnLogout.setOnAction(e -> {
                workshopId = "";
                PREFS.put("workshopId", "");
                screenCache.clear();
                showScreen("LAUNCH");
            });
            sidebarButtons.getChildren().add(btnLogout);

            sidebar.getChildren().add(sidebarButtons);
            dash.setLeft(sidebar);

            dashboardContent = new StackPane();
            dashboardContent.getStyleClass().add("dashboard-content");
            dash.setCenter(dashboardContent);

            Timeline autoRefresh = new Timeline(new KeyFrame(Duration.seconds(10), event -> {
                if (currentActiveTab.equals("CONFIG_OFICINA")) return;
                RefreshParams p = tabRefreshMap.get(currentActiveTab);
                if (p != null) refreshData(p.endpoint, p.tableView, p.jsonKeys);
            }));
            autoRefresh.setCycleCount(Timeline.INDEFINITE);
            autoRefresh.play();

            if (!sidebarButtons.getChildren().isEmpty()) {
                ((Button) sidebarButtons.getChildren().get(0)).fire();
            }

            return dash;
        }

        private void showDashboardContent(Parent content, String tabName) {
            dashboardContent.getChildren().setAll(content);
        }

        private void addMenuButton(String icon, String title, String endpoint, String[] cols, String[] keys) {
            Button btn = createSidebarBtn(icon, title);
            TableView<Map<String, Object>> tableView = createTableView(cols, keys);
            Parent viewPanel = createViewPanel(title, tableView);

            tabRefreshMap.put(title, new RefreshParams(endpoint, tableView, keys));

            btn.setOnAction(e -> {
                currentActiveTab = title;
                showDashboardContent(viewPanel, title);
                highlightButton(btn);
                refreshData(endpoint, tableView, keys);
            });
            sidebarButtons.getChildren().add(btn);
        }

        private Button createBigButton(String text, String styleClass) {
            Button btn = new Button(text);
            btn.getStyleClass().add(styleClass);
            btn.setPrefHeight(60);
            btn.setMaxWidth(400);
            return btn;
        }

        private Button createSidebarBtn(String icon, String text) {
            Button btn = new Button(icon + "   " + text);
            btn.getStyleClass().add("sidebar-button");
            btn.setMaxWidth(Double.MAX_VALUE);
            btn.setAlignment(Pos.CENTER_LEFT);
            btn.setPadding(new Insets(12, 20, 12, 20));
            return btn;
        }

        private void highlightButton(Button selectedButton) {
            sidebarButtons.getChildren().forEach(node -> {
                if (node instanceof Button) node.getStyleClass().remove("sidebar-button-selected");
            });
            selectedButton.getStyleClass().add("sidebar-button-selected");
        }

        private TextField createStyledTextField(String prompt, String styleClass) {
            TextField field = new TextField();
            field.setPromptText(prompt);
            field.getStyleClass().add(styleClass);
            return field;
        }

        private VBox createFormCard(String title) {
            VBox p = new VBox(20);
            p.getStyleClass().add("form-card");
            Label l = new Label(title);
            l.setStyle("-fx-font-weight: bold; -fx-text-fill: #1e293b; -fx-font-size: 16px;");
            p.getChildren().add(l);
            return p;
        }

        private TableView<Map<String, Object>> createTableView(String[] cols, String[] keys) {
            TableView<Map<String, Object>> table = new TableView<>();
            table.getStyleClass().add("table-view");
            table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

            for (int i = 0; i < cols.length; i++) {
                final String key = keys[i];
                TableColumn<Map<String, Object>, String> column = new TableColumn<>(cols[i]);
                column.setCellValueFactory(cellData -> {
                    Object val = cellData.getValue().get(key);
                    return new SimpleStringProperty(val != null ? String.valueOf(val) : "-");
                });
                table.getColumns().add(column);
            }
            return table;
        }

        private Parent createViewPanel(String title, TableView<Map<String, Object>> table) {
            VBox p = new VBox(25);
            p.getStyleClass().add("dashboard-content");

            Label t = new Label(title);
            t.getStyleClass().add("content-title");

            VBox.setVgrow(table, Priority.ALWAYS);
            p.getChildren().addAll(t, table);
            return p;
        }

        private void showAlert(Alert.AlertType type, String title, String message) {
            Platform.runLater(() -> {
                Alert alert = new Alert(type);
                alert.setTitle(title);
                alert.setHeaderText(null);
                alert.setContentText(message);
                alert.showAndWait();
            });
        }

        private java.util.concurrent.CompletableFuture<Boolean> sendToAPI(
                String method, String endpoint, Object data) {
            String json = gson.toJson(data);
            HttpRequest.Builder rb = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + endpoint))
                    .header("Content-Type", "application/json");

            if (method.equals("POST")) rb.POST(HttpRequest.BodyPublishers.ofString(json));
            else rb.PUT(HttpRequest.BodyPublishers.ofString(json));

            return client.sendAsync(rb.build(), HttpResponse.BodyHandlers.ofString())
                    .thenApply(res -> res.statusCode() == 201 || res.statusCode() == 200)
                    .exceptionally(ex -> false);
        }

        private void refreshData(String endpoint, TableView<Map<String, Object>> tableView, String[] jsonKeys) {
            if (workshopId.isEmpty() && endpoint.contains("oficina/")) return;

            client.sendAsync(HttpRequest.newBuilder().uri(URI.create(BASE_URL + endpoint)).build(),
                            HttpResponse.BodyHandlers.ofString())
                    .thenApply(res -> res.statusCode() == 200 ? res.body() : null)
                    .thenAccept(json -> {
                        if (json == null) return;
                        Platform.runLater(() -> {
                            try {
                                List<Map<String, Object>> items = gson.fromJson(json, new TypeToken<List<Map<String, Object>>>(){}.getType());
                                if (items != null) tableView.getItems().setAll(items);
                            } catch (Exception ignored) {}
                        });
                    });
        }

        private void autoDetectLocation(TextField txtLoc, TextField txtLat, TextField txtLon) {
            client.sendAsync(HttpRequest.newBuilder().uri(URI.create("http://ip-api.com/json/")).build(),
                            HttpResponse.BodyHandlers.ofString())
                    .thenApply(HttpResponse::body)
                    .thenAccept(json -> Platform.runLater(() -> {
                        try {
                            Map<String, Object> data = gson.fromJson(json, new TypeToken<Map<String, Object>>(){}.getType());
                            if (data != null && "success".equals(data.get("status"))) {
                                if (txtLat.getText().isEmpty()) txtLat.setText(String.valueOf(data.get("lat")));
                                if (txtLon.getText().isEmpty()) txtLon.setText(String.valueOf(data.get("lon")));
                                if (txtLoc.getText().isEmpty()) txtLoc.setText(data.get("city") + ", " + data.get("regionName"));
                            }
                        } catch (Exception ignored) {}
                    }));
        }

        private void loadFonts() {
            try {
                InputStream s1 = getClass().getResourceAsStream("/assets/Ardela.ttf");
                if (s1 != null) Font.loadFont(s1, 12);
                InputStream s2 = getClass().getResourceAsStream("/assets/Sora-Regular.ttf");
                if (s2 != null) Font.loadFont(s2, 12);
            } catch (Exception e) {
                System.err.println("Aviso: Falha ao carregar fontes personalizadas.");
            }
        }
    }
}