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
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
        private BorderPane dashboardLayoutRoot;
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
            try {
                masterPanel.getStylesheets().add(Objects.requireNonNull(getClass().getResource("/assets/style.css")).toExternalForm());
            } catch (Exception e) {
                System.err.println("Erro ao carregar CSS: " + e.getMessage());
            }

            Scene scene = new Scene(masterPanel, 1200, 800);
            primaryStage.setTitle("Motor Pro | Management System");
            primaryStage.setScene(scene);
            primaryStage.setMaximized(true);

            try {
                InputStream is = getClass().getResourceAsStream("/assets/perfil.png");
                if (is != null) primaryStage.getIcons().add(new Image(is));
            } catch (Exception ignored) {}

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
                        case "DASHBOARD":
                            target = createDashboardStructure();
                            dashboardLayoutRoot = (BorderPane) target;
                            break;
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
            VBox card = new VBox(25);
            card.setAlignment(Pos.CENTER);
            card.getStyleClass().add("root");
            Label logo = new Label("MOTOR PRO");
            logo.getStyleClass().add("label-logo");

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
            container.getStyleClass().add("root");
            VBox card = new VBox(20);
            card.setAlignment(Pos.CENTER);
            card.setMaxSize(450, 400);

            Label title = new Label("LOGIN");
            title.getStyleClass().add("login-title");
            TextField txtTel = createStyledTextField("TELEFONE DA OFICINA", "text-field-styled");
            txtTel.setMaxWidth(450);
            txtTel.setPrefHeight(70);

            Button btnEntrar = createBigButton("ACESSAR SISTEMA", "big-button-primary");
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
                            showAlert(Alert.AlertType.ERROR, "Erro", "Oficina não encontrada com este telefone.");
                            btnEntrar.setText("ACESSAR SISTEMA");
                            btnEntrar.setDisable(false);
                        }
                    }));
            });

            Button btnBack = new Button("Voltar ao início");
            btnBack.getStyleClass().add("link-button");
            btnBack.setOnAction(e -> showScreen("LAUNCH"));

            card.getChildren().addAll(title, txtTel, btnEntrar, btnBack);
            container.getChildren().add(card);
            return container;
        }

        private Parent createConfigScreen(boolean isNew) {
            BorderPane main = new BorderPane();
            main.getStyleClass().add("dashboard-content");
            main.setPadding(new Insets(50, 80, 50, 80));

            HBox header = new HBox(20);
            header.setAlignment(Pos.CENTER_LEFT);
            Label lblT = new Label(isNew ? "NOVA OFICINA" : "MEUS DADOS");
            lblT.getStyleClass().add("content-title");
            
            Button btnSave = new Button("SALVAR ALTERAÇÕES");
            btnSave.getStyleClass().addAll("big-button", "big-button-primary");
            btnSave.setPrefWidth(240);
            btnSave.setPrefHeight(65);
            btnSave.setFont(Font.font("Sora", 13)); // Ajuste de fonte para o botão

            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            header.getChildren().addAll(lblT, spacer, btnSave);
            main.setTop(header);

            GridPane grid = new GridPane();
            grid.setHgap(30);
            grid.setVgap(20); // Espaçamento vertical entre os campos
            grid.setPadding(new Insets(40, 0, 0, 0)); // Espaçamento do topo do grid

            // Coluna 1: Dados da oficina
            VBox c1 = createFormCard("INFORMAÇÕES BÁSICAS");
            TextField txtNome = createStyledTextField("NOME DA OFICINA", "text-field-form");
            TextField txtEndereco = createStyledTextField("ENDEREÇO COMPLETO", "text-field-form");
            TextField txtTelefone = createStyledTextField("TELEFONE (ACESSO)", "text-field-form");
            TextField txtLat = createStyledTextField("LATITUDE", "text-field-form");
            TextField txtLon = createStyledTextField("LONGITUDE", "text-field-form");

            c1.getChildren().addAll(txtNome, txtEndereco, txtTelefone, txtLat, txtLon);
            GridPane.setConstraints(c1, 0, 0);

            // Coluna 2: Serviços
            VBox c2 = createFormCard("SERVIÇOS PRESTADOS");
            String[] std = {"Freios", "Suspensão", "Motor", "Óleo", "Revisão", "Elétrica", "Alinhamento", "Pneus"};
            List<CheckBox> cbs = new java.util.ArrayList<>();
            VBox cg = new VBox(10); // Espaçamento entre os checkboxes
            cg.setPadding(new Insets(10));
            cg.setStyle("-fx-background-color: white;"); // Fundo branco para os checkboxes
            for (String s : std) {
                CheckBox b = new CheckBox(s);
                b.getStyleClass().add("check-box");
                cbs.add(b);
                cg.getChildren().add(b);
            }
            ScrollPane sp = new ScrollPane(cg);
            sp.setFitToWidth(true);
            sp.setStyle("-fx-background-color: transparent; -fx-border-color: transparent;"); // Remove borda e fundo do scrollpane
            c2.getChildren().add(sp);
            GridPane.setConstraints(c2, 1, 0);

            grid.getChildren().addAll(c1, c2);
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

                client.sendAsync(HttpRequest.newBuilder().uri(URI.create(BASE_URL + "oficinas")).build(),
                                HttpResponse.BodyHandlers.ofString())
                    .thenApply(HttpResponse::body)
                    .thenAccept(json -> Platform.runLater(() -> {
                        List<Map<String, Object>> items = gson.fromJson(json, new TypeToken<List<Map<String, Object>>>(){}.getType());
                        boolean exists = false;
                        if (items != null && isNew) {
                            for (Map<String, Object> item : items) {
                                if (telInput.equals(String.valueOf(item.get("telefone")))) {
                                    exists = true;
                                    break;
                                }
                            }
                        }

                        if (exists) {
                            showAlert(Alert.AlertType.WARNING, "Aviso", "Telefone já cadastrado.");
                            btnSave.setText("SALVAR ALTERAÇÕES");
                            btnSave.setDisable(false);
                        } else {
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
                                    showAlert(Alert.AlertType.INFORMATION, "Sucesso", "Dados atualizados!");
                                    showScreen("DASHBOARD");
                                } else {
                                    showAlert(Alert.AlertType.ERROR, "Erro", "Erro ao salvar.");
                                    btnSave.setText("SALVAR ALTERAÇÕES");
                                    btnSave.setDisable(false);
                                }
                            }));
                        }
                    }));
            });

            autoDetectLocation(txtEndereco, txtLat, txtLon);
            return main;
        }

        private BorderPane createDashboardStructure() {
            BorderPane dash = new BorderPane();
            dash.getStyleClass().add("root");
            VBox sidebar = new VBox();
            sidebar.setPrefWidth(320);
            sidebar.getStyleClass().add("sidebar");
            sidebarButtons = new VBox(10);
            sidebarButtons.setAlignment(Pos.TOP_LEFT);
            sidebarButtons.setPadding(new Insets(30, 0, 0, 0));

            // Botões do menu
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

            Button btnLogout = createSidebarBtn("🚪", "SAIR");
            btnLogout.setOnAction(e -> {
                workshopId = "";
                PREFS.put("workshopId", "");
                screenCache.clear();
                showScreen("LAUNCH");
            });
            sidebarButtons.getChildren().add(btnLogout);
            sidebarButtons.setPadding(new Insets(0, 0, 20, 0));

            sidebar.getChildren().add(sidebarButtons);
            dash.setLeft(sidebar);

            dashboardContent = new StackPane();
            dashboardContent.getStyleClass().add("dashboard-content");
            dash.setCenter(dashboardContent);

            // Auto-refresh a cada 5 segundos
            Timeline fiveSecondsWonder = new Timeline(new KeyFrame(Duration.seconds(5), event -> {
                if (currentActiveTab.equals("CONFIG_OFICINA")) return;
                RefreshParams p = tabRefreshMap.get(currentActiveTab);
                if (p != null) refreshData(p.endpoint, p.tableView, p.jsonKeys);
            }));
            fiveSecondsWonder.setCycleCount(Timeline.INDEFINITE);
            fiveSecondsWonder.play();

            // Simula o clique no primeiro botão para carregar o conteúdo inicial
            if (!sidebarButtons.getChildren().isEmpty()) {
                Button firstButton = (Button) sidebarButtons.getChildren().get(0);
                firstButton.fire();
            }

            return dash;
        }

        private void showDashboardContent(Parent content, String tabName) {
            dashboardContent.getChildren().clear();
            dashboardContent.getChildren().add(content);
            StackPane.setAlignment(content, Pos.TOP_LEFT);
        }

        private void addMenuButton(String icon, String text, String endpoint, String[] cols, String[] keys) {
            Button btn = createSidebarBtn(icon, text);
            TableView<Map<String, Object>> tableView = createTableView(text, cols);
            Parent viewPanel = createViewPanel(text, tableView);
            
            tabRefreshMap.put(text, new RefreshParams(endpoint, tableView, keys));
            
            btn.setOnAction(e -> {
                currentActiveTab = text;
                showDashboardContent(viewPanel, text);
                highlightButton(btn);
                refreshData(endpoint, tableView, keys);
            });
            sidebarButtons.getChildren().add(btn);
        }

        private Button createBigButton(String text, String styleClass) {
            Button btn = new Button(text);
            btn.getStyleClass().addAll("big-button", styleClass);
            return btn;
        }

        private Button createSidebarBtn(String icon, String text) {
            Button btn = new Button(icon + "   " + text);
            btn.getStyleClass().add("sidebar-button");
            btn.setMaxWidth(Double.MAX_VALUE);
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
            field.setPrefHeight(70); // Altura padrão para campos de formulário
            return field;
        }

        private VBox createFormCard(String title) {
            VBox p = new VBox(15); // Espaçamento vertical entre os elementos do card
            p.getStyleClass().add("form-card");
            Label l = new Label(title);
            l.getStyleClass().add("form-card-title");
            p.getChildren().add(l);
            return p;
        }

        private TableView<Map<String, Object>> createTableView(String title, String[] cols) {
            TableView<Map<String, Object>> table = new TableView<>();
            table.getStyleClass().add("table-view");
            table.setPrefHeight(Double.MAX_VALUE); // Ocupa todo o espaço vertical disponível
            table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

            for (String colName : cols) {
                TableColumn<Map<String, Object>, String> column = new TableColumn<>(colName);
                column.setCellValueFactory(cellData -> {
                    String key = cellData.getTableColumn().getText().toLowerCase().replace(" ", ""); // Converte "Data/Hora" para "datahora"
                    if (key.equals("oficina")) key = "oficinaNome"; // Ajuste para o campo real da API
                    return new SimpleStringProperty(String.valueOf(cellData.getValue().getOrDefault(key, "-")));
                });
                column.setStyle("-fx-alignment: CENTER;");
                table.getColumns().add(column);
            }
            return table;
        }

        private Parent createViewPanel(String title, TableView<Map<String, Object>> table) {
            VBox p = new VBox(50); // Espaçamento entre título e tabela
            p.getStyleClass().add("dashboard-content");
            p.setPadding(new Insets(60, 80, 60, 80));

            String cleanTitle = title.replaceAll("[^\\p{L}\\p{Nd}\\s]", "").trim();
            Label t = new Label(cleanTitle);
            t.getStyleClass().add("content-title");

            ScrollPane sp = new ScrollPane(table);
            sp.setFitToWidth(true);
            sp.setFitToHeight(true);
            sp.getStyleClass().add("scroll-pane");

            p.getChildren().addAll(t, sp);
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
                    .thenApply(res -> {
                        if (res.statusCode() == 201 || res.statusCode() == 200) {
                            try {
                                Map<String, Object> r = gson.fromJson(res.body(),
                                        new TypeToken<Map<String, Object>>(){}.getType());
                                if (r.containsKey("id")) {
                                    workshopId = r.get("id").toString();
                                    PREFS.put("workshopId", workshopId);
                                }
                            } catch (Exception ignored) {}
                            return true;
                        }
                        return false;
                    })
                    .exceptionally(ex -> {
                        Platform.runLater(() -> showAlert(Alert.AlertType.ERROR, "Erro de Conexão", "Não foi possível conectar à API."));
                        return false;
                    });
        }

        private void refreshData(String endpoint, TableView<Map<String, Object>> tableView, String[] jsonKeys) {
            if (workshopId.isEmpty() && endpoint.contains("oficina/")) {
                tableView.getItems().clear();
                return;
            }

            client.sendAsync(
                            HttpRequest.newBuilder().uri(URI.create(BASE_URL + endpoint)).build(),
                            HttpResponse.BodyHandlers.ofString())
                    .thenApply(HttpResponse::body)
                    .thenAccept(json -> Platform.runLater(() -> {
                        tableView.getItems().clear();
                        try {
                            List<Map<String, Object>> items = gson.fromJson(json,
                                    new TypeToken<List<Map<String, Object>>>(){}.getType());
                            if (items != null) {
                                for (Map<String, Object> item : items) {
                                    Map<String, Object> rowData = new HashMap<>();
                                    for (String key : jsonKeys) {
                                        rowData.put(key, item.getOrDefault(key, "-"));
                                    }
                                    tableView.getItems().add(rowData);
                                }
                            }
                        } catch (Exception ignored) {}
                    }))
                    .exceptionally(ex -> {
                        Platform.runLater(() -> showAlert(Alert.AlertType.ERROR, "Erro de Dados", "Não foi possível carregar os dados."));
                        return null;
                    });
        }

        private void autoDetectLocation(TextField txtLoc, TextField txtLat, TextField txtLon) {
            client.sendAsync(
                            HttpRequest.newBuilder().uri(URI.create("http://ip-api.com/json/")).build(),
                            HttpResponse.BodyHandlers.ofString())
                    .thenApply(HttpResponse::body)
                    .thenAccept(json -> Platform.runLater(() -> {
                        try {
                            Map<String, Object> data = gson.fromJson(json,
                                    new TypeToken<Map<String, Object>>(){}.getType());
                            if (data != null && "success".equals(data.get("status"))) {
                                txtLat.setText(String.valueOf(data.get("lat")));
                                txtLon.setText(String.valueOf(data.get("lon")));
                                txtLoc.setText(data.get("city") + ", " + data.get("regionName"));
                            }
                        } catch (Exception ignored) {}
                    }))
                    .exceptionally(ex -> {
                        Platform.runLater(() -> showAlert(Alert.AlertType.ERROR, "Erro de Localização", "Não foi possível detectar a localização."));
                        return null;
                    });
        }

        private void loadFonts() {
            try {
                Font.loadFont(Objects.requireNonNull(getClass().getResourceAsStream("/assets/Ardela.ttf")), 10);
                Font.loadFont(Objects.requireNonNull(getClass().getResourceAsStream("/assets/Sora-Regular.ttf")), 10);
            } catch (Exception e) {
                System.err.println("Erro ao carregar fontes: " + e.getMessage());
            }
        }
    }
}