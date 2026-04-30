import com.google.gson.Gson;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class Main extends Application {

    // -------------------------------------------------------------------------
    // Constantes
    // -------------------------------------------------------------------------
    private static final String BASE_URL = "https://api-java-production-5e77.up.railway.app";
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(java.time.Duration.ofSeconds(15)).build();
    private static final Gson GSON = new Gson();

    // -------------------------------------------------------------------------
    // Estado de sessão
    // -------------------------------------------------------------------------
    private String jwtToken    = null;  // Bearer token após login
    private String workshopId  = null;  // ID da oficina autenticada
    private String currentTab  = "AGENDAMENTOS";

    private final Map<String, TableView<Map<String, Object>>> tabTables    = new HashMap<>();
    private final Map<String, String>                         tabEndpoints = new HashMap<>();
    private final Map<String, String[]>                       tabKeys      = new HashMap<>();

    private ScheduledExecutorService scheduler;
    private StackPane root;
    private BorderPane dashPane;

    // =========================================================================
    // START
    // =========================================================================
    @Override
    public void start(Stage stage) {
        root = new StackPane();
        Scene scene = new Scene(root, 1440, 900);
        scene.getStylesheets().add(
                getClass().getResource("/assets/style.css").toExternalForm());

        stage.setTitle("Motor Pro | Management System");
        stage.setMaximized(true);
        try {
            stage.getIcons().add(
                    new Image(getClass().getResourceAsStream("/assets/perfil.png")));
        } catch (Exception ignored) {}

        stage.setScene(scene);
        stage.show();

        showLoginScreen();
    }

    // =========================================================================
    // TELA DE LOGIN / REGISTRO
    // =========================================================================
    private void showLoginScreen() {
        VBox card = new VBox(20);
        card.setAlignment(Pos.CENTER);
        card.setMaxWidth(420);
        card.setPadding(new Insets(48));
        card.setStyle(
                "-fx-background-color: #111318;" +
                        "-fx-background-radius: 16;" +
                        "-fx-border-color: #2A2A3A;" +
                        "-fx-border-radius: 16;" +
                        "-fx-border-width: 1;"
        );

        Label logo = new Label("MOTOR PRO");
        logo.setStyle("-fx-font-size:28px; -fx-font-weight:700; -fx-text-fill:#16BC4E;");

        Label subtitle = new Label("Sistema de Gerenciamento de Oficina");
        subtitle.setStyle("-fx-text-fill:#9A9AAA; -fx-font-size:13px;");

        // ---- Campos ----
        TextField txtUser = new TextField();
        txtUser.setPromptText("Email / Usuário");
        txtUser.getStyleClass().add("text-field-styled");
        txtUser.setMaxWidth(Double.MAX_VALUE);

        PasswordField txtPass = new PasswordField();
        txtPass.setPromptText("Senha");
        txtPass.getStyleClass().add("text-field-styled");
        txtPass.setMaxWidth(Double.MAX_VALUE);

        // Campo extra só para registro
        TextField txtOficinaId = new TextField();
        txtOficinaId.setPromptText("ID da Oficina (somente no cadastro)");
        txtOficinaId.getStyleClass().add("text-field-styled");
        txtOficinaId.setMaxWidth(Double.MAX_VALUE);
        txtOficinaId.setVisible(false);
        txtOficinaId.setManaged(false);

        Label lblStatus = new Label();
        lblStatus.setStyle("-fx-text-fill:#FF4C4C; -fx-font-size:12px;");
        lblStatus.setWrapText(true);

        // ---- Botões ----
        Button btnLogin = new Button("ENTRAR");
        btnLogin.getStyleClass().add("big-button-primary");
        btnLogin.setMaxWidth(Double.MAX_VALUE);

        Button btnToggle = new Button("Não tem conta? Cadastre-se");
        btnToggle.setStyle(
                "-fx-background-color:transparent;" +
                        "-fx-text-fill:#16BC4E;" +
                        "-fx-cursor:hand;" +
                        "-fx-font-size:12px;"
        );

        final boolean[] isRegisterMode = {false};

        btnToggle.setOnAction(e -> {
            isRegisterMode[0] = !isRegisterMode[0];
            if (isRegisterMode[0]) {
                btnLogin.setText("CADASTRAR");
                btnToggle.setText("Já tem conta? Entrar");
                txtOficinaId.setVisible(true);
                txtOficinaId.setManaged(true);
            } else {
                btnLogin.setText("ENTRAR");
                btnToggle.setText("Não tem conta? Cadastre-se");
                txtOficinaId.setVisible(false);
                txtOficinaId.setManaged(false);
            }
            lblStatus.setText("");
        });

        btnLogin.setOnAction(e -> {
            String user = txtUser.getText().trim();
            String pass = txtPass.getText().trim();
            if (user.isEmpty() || pass.isEmpty()) {
                lblStatus.setText("Preencha usuário e senha.");
                return;
            }
            btnLogin.setDisable(true);
            btnLogin.setText("AGUARDE...");
            lblStatus.setText("");

            if (isRegisterMode[0]) {
                // ---------- REGISTRO ----------
                String ofId = txtOficinaId.getText().trim();
                if (ofId.isEmpty()) {
                    lblStatus.setText("Informe o ID da Oficina para cadastro.");
                    btnLogin.setDisable(false);
                    btnLogin.setText("CADASTRAR");
                    return;
                }
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("username", user);
                body.put("password", pass);
                try { body.put("oficinaId", Long.parseLong(ofId)); }
                catch (NumberFormatException ex) { body.put("oficinaId", ofId); }

                doPost("/oficina-users/register", body, null)
                        .thenAccept(res -> Platform.runLater(() -> {
                            btnLogin.setDisable(false);
                            btnLogin.setText("CADASTRAR");
                            if (res != null && res.statusCode() >= 200 && res.statusCode() < 300) {
                                lblStatus.setStyle("-fx-text-fill:#16BC4E; -fx-font-size:12px;");
                                lblStatus.setText("Cadastro realizado! Faça login.");
                                isRegisterMode[0] = false;
                                btnLogin.setText("ENTRAR");
                                btnToggle.setText("Não tem conta? Cadastre-se");
                                txtOficinaId.setVisible(false);
                                txtOficinaId.setManaged(false);
                            } else {
                                lblStatus.setStyle("-fx-text-fill:#FF4C4C; -fx-font-size:12px;");
                                lblStatus.setText("Erro no cadastro. Verifique os dados.");
                            }
                        })).exceptionally(ex -> { Platform.runLater(() -> {
                            btnLogin.setDisable(false); btnLogin.setText("CADASTRAR");
                            lblStatus.setText("Erro de conexão.");
                        }); return null; });

            } else {
                // ---------- LOGIN ----------
                Map<String, String> body = new LinkedHashMap<>();
                body.put("username", user);
                body.put("password", pass);

                doPost("/oficina-users/login", body, null)
                        .thenAccept(res -> Platform.runLater(() -> {
                            btnLogin.setDisable(false);
                            btnLogin.setText("ENTRAR");
                            if (res != null && res.statusCode() == 200) {
                                try {
                                    Map<?, ?> json = GSON.fromJson(res.body(), Map.class);
                                    jwtToken = (String) json.get("token");
                                    // Tenta extrair o oficina ID da resposta
                                    if (json.containsKey("oficinaId")) {
                                        workshopId = String.valueOf(json.get("oficinaId"));
                                    } else if (json.containsKey("id")) {
                                        workshopId = String.valueOf(json.get("id"));
                                    }
                                    if (jwtToken != null && !jwtToken.isEmpty()) {
                                        showDashboard();
                                    } else {
                                        lblStatus.setText("Token não recebido. Tente novamente.");
                                    }
                                } catch (Exception ex) {
                                    lblStatus.setText("Erro ao processar resposta do servidor.");
                                }
                            } else {
                                lblStatus.setStyle("-fx-text-fill:#FF4C4C; -fx-font-size:12px;");
                                lblStatus.setText("Usuário ou senha incorretos.");
                            }
                        })).exceptionally(ex -> { Platform.runLater(() -> {
                            btnLogin.setDisable(false); btnLogin.setText("ENTRAR");
                            lblStatus.setText("Erro de conexão com o servidor.");
                        }); return null; });
            }
        });

        // Permite login com Enter
        txtPass.setOnAction(e -> btnLogin.fire());

        card.getChildren().addAll(
                logo, subtitle,
                new Separator(),
                txtUser, txtPass, txtOficinaId,
                btnLogin, btnToggle, lblStatus
        );

        StackPane bg = new StackPane(card);
        bg.getStyleClass().add("master-panel");
        root.getChildren().setAll(bg);
    }

    // =========================================================================
    // DASHBOARD
    // =========================================================================
    private void showDashboard() {
        dashPane = new BorderPane();
        dashPane.getStyleClass().add("master-panel");
        dashPane.setLeft(buildSidebar());
        dashPane.setCenter(buildWelcomePane());
        root.getChildren().setAll(dashPane);

        // Auto-refresh a cada 10 s
        if (scheduler != null) scheduler.shutdownNow();
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "auto-refresh");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleAtFixedRate(
                () -> Platform.runLater(this::autoRefresh), 10, 10, TimeUnit.SECONDS);
    }

    // -------------------------------------------------------------------------
    // Sidebar
    // -------------------------------------------------------------------------
    private VBox buildSidebar() {
        VBox sidebar = new VBox(8);
        sidebar.getStyleClass().add("sidebar");
        sidebar.setPrefWidth(280);

        Label brand = new Label("MOTOR PRO");
        brand.setStyle("-fx-font-size:22px; -fx-font-weight:700; -fx-text-fill:#16BC4E;");
        brand.setPadding(new Insets(0, 0, 24, 0));

        Button btnAgendamentos = sidebarBtn("📅", "AGENDAMENTOS");
        Button btnClientes     = sidebarBtn("👥", "CLIENTES");
        Button btnOficina      = sidebarBtn("🏭", "MINHA OFICINA");
        Button btnLogout       = sidebarBtn("🚪", "SAIR");

        // Registra abas
        registerAgendamentosTab(btnAgendamentos, sidebar);
        registerClientesTab(btnClientes, sidebar);

        btnOficina.setOnAction(e -> {
            currentTab = "OFICINA";
            clearSelected(sidebar);
            btnOficina.getStyleClass().add("sidebar-button-selected");
            dashPane.setCenter(buildOficinaPane());
        });

        btnLogout.setStyle(
                "-fx-background-color:transparent;" +
                        "-fx-text-fill:#FF4C4C;" +
                        "-fx-cursor:hand;" +
                        "-fx-alignment:CENTER_LEFT;" +
                        "-fx-font-size:13px;" +
                        "-fx-padding:10 16 10 16;"
        );
        btnLogout.setMaxWidth(Double.MAX_VALUE);
        btnLogout.setOnAction(e -> {
            if (scheduler != null) scheduler.shutdownNow();
            jwtToken   = null;
            workshopId = null;
            showLoginScreen();
        });

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);

        sidebar.getChildren().addAll(
                brand, btnAgendamentos, btnClientes, btnOficina,
                spacer, new Separator(), btnLogout
        );

        // Seleciona Agendamentos ao abrir
        Platform.runLater(btnAgendamentos::fire);
        return sidebar;
    }

    private Button sidebarBtn(String icon, String label) {
        Button btn = new Button(icon + "  " + label);
        btn.getStyleClass().add("sidebar-button");
        btn.setMaxWidth(Double.MAX_VALUE);
        return btn;
    }

    private void clearSelected(javafx.scene.Parent parent) {
        parent.getChildrenUnmodifiable().forEach(n -> {
            if (n instanceof Button b)
                b.getStyleClass().remove("sidebar-button-selected");
        });
    }

    // =========================================================================
    // ABA AGENDAMENTOS
    // =========================================================================
    private void registerAgendamentosTab(Button btn, VBox sidebar) {
        String name = "AGENDAMENTOS";

        // Colunas visíveis
        String[] cols = {"ID", "Cliente ID", "Veículo", "Serviço", "Data/Hora", "Status"};
        String[] keys = {"id", "clienteId", "veiculo", "servico", "horario", "status"};

        TableView<Map<String, Object>> table = new TableView<>();
        table.getStyleClass().add("table-view");
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.setPlaceholder(new Label("Nenhum agendamento encontrado."));

        // Cria colunas normais
        for (int i = 0; i < cols.length; i++) {
            final String key = keys[i];
            final String col = cols[i];

            if ("Status".equals(col)) {
                // Coluna de status com badge colorido
                TableColumn<Map<String, Object>, String> statusCol = new TableColumn<>("Status");
                statusCol.setCellValueFactory(data ->
                        new javafx.beans.property.SimpleStringProperty(
                                String.valueOf(data.getValue().getOrDefault("status", "-"))
                        )
                );
                statusCol.setCellFactory(tc -> new TableCell<>() {
                    @Override
                    protected void updateItem(String status, boolean empty) {
                        super.updateItem(status, empty);
                        if (empty || status == null) { setGraphic(null); setText(null); return; }
                        Label badge = new Label(status);
                        badge.setPadding(new Insets(4, 12, 4, 12));
                        badge.setStyle(
                                "-fx-background-radius:20;" +
                                        "-fx-font-size:11px;" +
                                        "-fx-font-weight:700;" +
                                        statusColor(status)
                        );
                        setGraphic(badge);
                        setText(null);
                        setAlignment(Pos.CENTER);
                    }
                });
                table.getColumns().add(statusCol);
            } else {
                TableColumn<Map<String, Object>, Object> c = new TableColumn<>(col);
                c.setCellValueFactory(data ->
                        new SimpleObjectProperty<>(data.getValue().getOrDefault(key, "-"))
                );
                c.setStyle("-fx-alignment:CENTER;");
                table.getColumns().add(c);
            }
        }

        tabTables.put(name, table);
        tabEndpoints.put(name, "/agendamentos");
        tabKeys.put(name, keys);

        // ---- Painel de ações ----
        HBox actions = new HBox(12);
        actions.setAlignment(Pos.CENTER_LEFT);
        actions.setPadding(new Insets(0, 0, 16, 0));

        Button btnConfirmar = actionBtn("✅ Confirmar",  "#16BC4E");
        Button btnConcluir  = actionBtn("🏁 Concluir",   "#3B82F6");
        Button btnCancelar  = actionBtn("❌ Cancelar",   "#EF4444");
        Button btnDetalhes  = actionBtn("🔍 Detalhes",   "#9A9AAA");
        Button btnExcluir   = actionBtn("🗑 Excluir",    "#FF4C4C");
        Button btnRefresh   = actionBtn("🔄 Atualizar",  "#6366F1");

        btnConfirmar.setOnAction(e -> mudarStatus(table, "CONFIRMADO"));
        btnConcluir.setOnAction(e  -> mudarStatus(table, "CONCLUIDO"));
        btnCancelar.setOnAction(e  -> mudarStatus(table, "CANCELADO"));
        btnDetalhes.setOnAction(e  -> verDetalhes(table));
        btnExcluir.setOnAction(e   -> excluirAgendamento(table));
        btnRefresh.setOnAction(e   -> refreshTab(name));

        actions.getChildren().addAll(
                btnConfirmar, btnConcluir, btnCancelar,
                btnDetalhes, btnExcluir, btnRefresh
        );

        // ---- Layout da aba ----
        BorderPane viewPane = new BorderPane();
        viewPane.getStyleClass().add("dashboard-content");

        Label title = new Label("AGENDAMENTOS");
        title.getStyleClass().add("content-title");

        Label hint = new Label("Selecione um agendamento para usar as ações abaixo.");
        hint.setStyle("-fx-text-fill:#9A9AAA; -fx-font-size:12px;");

        VBox top = new VBox(8, title, hint, actions);
        top.setPadding(new Insets(0, 0, 8, 0));
        viewPane.setTop(top);
        viewPane.setCenter(table);

        btn.setOnAction(e -> {
            currentTab = name;
            clearSelected(sidebar);
            btn.getStyleClass().add("sidebar-button-selected");
            dashPane.setCenter(viewPane);
            refreshTab(name);
        });
    }

    /** Cor do badge de status */
    private String statusColor(String status) {
        return switch (status) {
            case "CONFIRMADO" -> "-fx-background-color:#16BC4E33; -fx-text-fill:#16BC4E;";
            case "PENDENTE"   -> "-fx-background-color:#F59E0B33; -fx-text-fill:#F59E0B;";
            case "CANCELADO"  -> "-fx-background-color:#EF444433; -fx-text-fill:#EF4444;";
            case "CONCLUIDO"  -> "-fx-background-color:#3B82F633; -fx-text-fill:#3B82F6;";
            default           -> "-fx-background-color:#9A9AAA33; -fx-text-fill:#9A9AAA;";
        };
    }

    private Button actionBtn(String label, String color) {
        Button btn = new Button(label);
        btn.setStyle(
                "-fx-background-color:" + color + "22;" +
                        "-fx-text-fill:" + color + ";" +
                        "-fx-border-color:" + color + "55;" +
                        "-fx-border-radius:8;" +
                        "-fx-background-radius:8;" +
                        "-fx-padding:8 16 8 16;" +
                        "-fx-cursor:hand;" +
                        "-fx-font-size:12px;" +
                        "-fx-font-weight:700;"
        );
        return btn;
    }

    /** Muda o status de um agendamento selecionado */
    private void mudarStatus(TableView<Map<String, Object>> table, String novoStatus) {
        Map<String, Object> selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showAlert(Alert.AlertType.WARNING, "Selecione um agendamento primeiro.");
            return;
        }
        String id = String.valueOf(selected.get("id"));
        Map<String, String> body = Map.of("status", novoStatus);

        doPut("/agendamentos/" + id, body)
                .thenAccept(res -> Platform.runLater(() -> {
                    if (res != null && res.statusCode() >= 200 && res.statusCode() < 300) {
                        refreshTab("AGENDAMENTOS");
                    } else {
                        showAlert(Alert.AlertType.ERROR, "Erro ao atualizar status. Cód: " +
                                (res != null ? res.statusCode() : "?"));
                    }
                }));
    }

    /** Abre modal com detalhes do agendamento */
    private void verDetalhes(TableView<Map<String, Object>> table) {
        Map<String, Object> selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showAlert(Alert.AlertType.WARNING, "Selecione um agendamento primeiro.");
            return;
        }
        String id = String.valueOf(selected.get("id"));

        doGet("/agendamentos/" + id).thenAccept(res -> Platform.runLater(() -> {
            if (res == null || res.statusCode() != 200) {
                showAlert(Alert.AlertType.ERROR, "Não foi possível carregar os detalhes.");
                return;
            }
            try {
                Map<?, ?> data = GSON.fromJson(res.body(), Map.class);
                Stage modal = new Stage();
                modal.initModality(Modality.APPLICATION_MODAL);
                modal.setTitle("Detalhes do Agendamento #" + id);

                VBox content = new VBox(14);
                content.setPadding(new Insets(32));
                content.setStyle("-fx-background-color:#111318;");
                content.setPrefWidth(480);

                Label title = new Label("Detalhes do Agendamento");
                title.setStyle("-fx-font-size:18px; -fx-font-weight:700; -fx-text-fill:#FFFFFF;");

                // Status badge
                Object statusVal = data.get("status");
                String status = String.valueOf(statusVal != null ? statusVal : "-");
                Label statusBadge = new Label(status);
                statusBadge.setPadding(new Insets(4, 16, 4, 16));
                statusBadge.setStyle(
                        "-fx-background-radius:20; -fx-font-weight:700; -fx-font-size:12px;" +
                                statusColor(status)
                );

                VBox fields = new VBox(10);
                String[][] fieldDefs = {
                        {"ID",           "id"},
                        {"Cliente ID",   "clienteId"},
                        {"Veículo",      "veiculo"},
                        {"Serviço",      "servico"},
                        {"Data/Hora",    "horario"},
                        {"Observações",  "observacoes"},
                };
                for (String[] fd : fieldDefs) {
                    Object val = data.get(fd[1]);
                    if (val == null) continue;
                    HBox row = new HBox(8);
                    Label lKey = new Label(fd[0] + ":");
                    lKey.setStyle("-fx-text-fill:#9A9AAA; -fx-font-size:12px; -fx-min-width:100;");
                    Label lVal = new Label(String.valueOf(val));
                    lVal.setStyle("-fx-text-fill:#FFFFFF; -fx-font-size:13px;");
                    lVal.setWrapText(true);
                    row.getChildren().addAll(lKey, lVal);
                    fields.getChildren().add(row);
                }

                Button btnFechar = new Button("FECHAR");
                btnFechar.getStyleClass().add("big-button-primary");
                btnFechar.setOnAction(ev -> modal.close());

                content.getChildren().addAll(title, statusBadge, new Separator(), fields, btnFechar);

                Scene modalScene = new Scene(content);
                try {
                    modalScene.getStylesheets().add(
                            getClass().getResource("/assets/style.css").toExternalForm());
                } catch (Exception ignored) {}
                modal.setScene(modalScene);
                modal.showAndWait();
            } catch (Exception ex) {
                showAlert(Alert.AlertType.ERROR, "Erro ao processar resposta: " + ex.getMessage());
            }
        }));
    }

    /** Exclui agendamento com confirmação */
    private void excluirAgendamento(TableView<Map<String, Object>> table) {
        Map<String, Object> selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showAlert(Alert.AlertType.WARNING, "Selecione um agendamento primeiro.");
            return;
        }
        String id = String.valueOf(selected.get("id"));

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Confirmar Exclusão");
        confirm.setHeaderText(null);
        confirm.setContentText("Deseja realmente excluir o agendamento #" + id + "?");
        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isEmpty() || result.get() != ButtonType.OK) return;

        doDelete("/agendamentos/" + id).thenAccept(res -> Platform.runLater(() -> {
            if (res != null && res.statusCode() >= 200 && res.statusCode() < 300) {
                refreshTab("AGENDAMENTOS");
            } else {
                showAlert(Alert.AlertType.ERROR, "Erro ao excluir agendamento.");
            }
        }));
    }

    // =========================================================================
    // ABA CLIENTES
    // =========================================================================
    private void registerClientesTab(Button btn, VBox sidebar) {
        String name = "CLIENTES";
        String[] cols = {"ID", "Nome", "Email", "Telefone"};
        String[] keys = {"id", "nome", "email", "telefone"};

        TableView<Map<String, Object>> table = buildGenericTable(cols, keys);
        table.setPlaceholder(new Label("Nenhum cliente encontrado."));

        tabTables.put(name, table);
        tabKeys.put(name, keys);
        // Endpoint dinâmico (precisa de workshopId)
        tabEndpoints.put(name, "/clientes");

        BorderPane viewPane = new BorderPane();
        viewPane.getStyleClass().add("dashboard-content");

        HBox header = new HBox(12);
        header.setAlignment(Pos.CENTER_LEFT);

        Label title = new Label("CLIENTES");
        title.getStyleClass().add("content-title");
        HBox.setHgrow(title, Priority.ALWAYS);

        Button btnRefresh = actionBtn("🔄 Atualizar", "#6366F1");
        btnRefresh.setOnAction(e -> refreshTab(name));

        Button btnVerVeiculos = actionBtn("🚗 Ver Veículos", "#9A9AAA");
        btnVerVeiculos.setOnAction(e -> verVeiculosCliente(table));

        header.getChildren().addAll(title, btnVerVeiculos, btnRefresh);

        VBox top = new VBox(8, header);
        top.setPadding(new Insets(0, 0, 16, 0));
        viewPane.setTop(top);
        viewPane.setCenter(table);

        btn.setOnAction(e -> {
            currentTab = name;
            clearSelected(sidebar);
            btn.getStyleClass().add("sidebar-button-selected");
            dashPane.setCenter(viewPane);
            refreshTab(name);
        });
    }

    private void verVeiculosCliente(TableView<Map<String, Object>> table) {
        Map<String, Object> selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showAlert(Alert.AlertType.WARNING, "Selecione um cliente primeiro.");
            return;
        }
        String clienteId = String.valueOf(selected.get("id"));
        String nomeCliente = String.valueOf(selected.getOrDefault("nome", clienteId));

        doGet("/veiculos/cliente/" + clienteId).thenAccept(res -> Platform.runLater(() -> {
            if (res == null || res.statusCode() != 200) {
                showAlert(Alert.AlertType.ERROR, "Não foi possível carregar os veículos.");
                return;
            }
            try {
                List<?> lista = GSON.fromJson(res.body(), List.class);

                Stage modal = new Stage();
                modal.initModality(Modality.APPLICATION_MODAL);
                modal.setTitle("Veículos de " + nomeCliente);

                VBox content = new VBox(16);
                content.setPadding(new Insets(32));
                content.setStyle("-fx-background-color:#111318;");
                content.setPrefWidth(560);

                Label title = new Label("Veículos — " + nomeCliente);
                title.setStyle("-fx-font-size:18px; -fx-font-weight:700; -fx-text-fill:#FFFFFF;");

                TableView<Map<String, Object>> vTable = buildGenericTable(
                        new String[]{"ID", "Marca", "Modelo", "Placa", "Ano"},
                        new String[]{"id", "marca", "modelo", "placa", "ano"}
                );
                vTable.setPrefHeight(300);

                ObservableList<Map<String, Object>> veiculos = FXCollections.observableArrayList();
                for (Object item : lista) {
                    if (item instanceof Map<?, ?> m) {
                        Map<String, Object> row = new HashMap<>();
                        for (String k : new String[]{"id","marca","modelo","placa","ano"}) {
                            Object val = m.get(k);
                            row.put(k, (val != null) ? val : "-");
                        }
                        veiculos.add(row);
                    }
                }
                vTable.setItems(veiculos);

                Button btnFechar = new Button("FECHAR");
                btnFechar.getStyleClass().add("big-button-primary");
                btnFechar.setOnAction(ev -> modal.close());

                content.getChildren().addAll(title, new Separator(), vTable, btnFechar);

                Scene modalScene = new Scene(content);
                try {
                    modalScene.getStylesheets().add(
                            getClass().getResource("/assets/style.css").toExternalForm());
                } catch (Exception ignored) {}
                modal.setScene(modalScene);
                modal.showAndWait();
            } catch (Exception ex) {
                showAlert(Alert.AlertType.ERROR, "Erro ao processar veículos: " + ex.getMessage());
            }
        }));
    }

    // =========================================================================
    // PAINEL MINHA OFICINA
    // =========================================================================
    private ScrollPane buildOficinaPane() {
        VBox content = new VBox(24);
        content.getStyleClass().add("dashboard-content");

        Label title = new Label("MINHA OFICINA");
        title.getStyleClass().add("content-title");

        // Campos editáveis
        TextField txtTelefone  = styledField("Telefone");
        TextField txtHorarios  = styledField("Horários (ex: Seg-Sex 08h-18h)");
        TextField txtEndereco  = styledField("Endereço");

        Label lblInfo = new Label("Carregando dados da oficina...");
        lblInfo.setStyle("-fx-text-fill:#9A9AAA; -fx-font-size:12px;");

        Button btnSalvar = new Button("SALVAR ALTERAÇÕES");
        btnSalvar.getStyleClass().add("big-button-primary");
        btnSalvar.setPrefSize(240, 48);

        // Carrega dados atuais
        doGet("/oficinas").thenAccept(res -> Platform.runLater(() -> {
            if (res == null || res.statusCode() != 200) {
                lblInfo.setText("Não foi possível carregar os dados da oficina.");
                return;
            }
            try {
                List<?> lista = GSON.fromJson(res.body(), List.class);
                if (lista != null && !lista.isEmpty() && lista.get(0) instanceof Map<?, ?> m) {
                    Object vTel = m.get("telefone");
                    txtTelefone.setText(String.valueOf(vTel != null ? vTel : ""));
                    Object vHor = m.get("horarios");
                    txtHorarios.setText(String.valueOf(vHor != null ? vHor : ""));
                    Object vEnd = m.get("endereco");
                    txtEndereco.setText(String.valueOf(vEnd != null ? vEnd : ""));
                    if (workshopId == null && m.containsKey("id"))
                        workshopId = String.valueOf(m.get("id"));
                    lblInfo.setText("Dados carregados com sucesso.");
                }
            } catch (Exception ex) {
                lblInfo.setText("Erro ao processar dados.");
            }
        }));

        btnSalvar.setOnAction(e -> {
            if (workshopId == null) {
                showAlert(Alert.AlertType.WARNING, "ID da oficina não identificado.");
                return;
            }
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("telefone", txtTelefone.getText());
            body.put("horarios", txtHorarios.getText());
            body.put("endereco", txtEndereco.getText());

            btnSalvar.setText("SALVANDO...");
            btnSalvar.setDisable(true);

            doPut("/oficinas/" + workshopId, body).thenAccept(res -> Platform.runLater(() -> {
                btnSalvar.setDisable(false);
                btnSalvar.setText("SALVAR ALTERAÇÕES");
                if (res != null && res.statusCode() >= 200 && res.statusCode() < 300) {
                    lblInfo.setStyle("-fx-text-fill:#16BC4E; -fx-font-size:12px;");
                    lblInfo.setText("✅ Dados atualizados com sucesso!");
                } else {
                    lblInfo.setStyle("-fx-text-fill:#FF4C4C; -fx-font-size:12px;");
                    lblInfo.setText("Erro ao salvar. Cód: " + (res != null ? res.statusCode() : "?"));
                }
            }));
        });

        VBox formCard = new VBox(12);
        formCard.setStyle(
                "-fx-background-color:#111318;" +
                        "-fx-background-radius:12;" +
                        "-fx-border-color:#2A2A3A;" +
                        "-fx-border-radius:12;" +
                        "-fx-padding:24;"
        );
        formCard.setMaxWidth(480);
        formCard.getChildren().addAll(
                fieldLabel("TELEFONE"),    txtTelefone,
                fieldLabel("HORÁRIOS"),    txtHorarios,
                fieldLabel("ENDEREÇO"),    txtEndereco,
                new Separator(),
                btnSalvar, lblInfo
        );

        content.getChildren().addAll(title, formCard);

        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background-color:transparent; -fx-background:#0B0B0B;");
        return scroll;
    }

    // =========================================================================
    // TABELA GENÉRICA
    // =========================================================================
    private TableView<Map<String, Object>> buildGenericTable(String[] cols, String[] keys) {
        TableView<Map<String, Object>> table = new TableView<>();
        table.getStyleClass().add("table-view");
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        for (int i = 0; i < cols.length; i++) {
            final String key = keys[i];
            TableColumn<Map<String, Object>, Object> col = new TableColumn<>(cols[i]);
            col.setCellValueFactory(data ->
                    new SimpleObjectProperty<>(data.getValue().getOrDefault(key, "-"))
            );
            col.setStyle("-fx-alignment:CENTER;");
            table.getColumns().add(col);
        }
        return table;
    }

    // =========================================================================
    // REFRESH
    // =========================================================================
    private void autoRefresh() {
        if (tabEndpoints.containsKey(currentTab)) refreshTab(currentTab);
    }

    @SuppressWarnings("unchecked")
    private void refreshTab(String name) {
        String[] keys = tabKeys.get(name);
        TableView<Map<String, Object>> table = tabTables.get(name);
        if (keys == null || table == null) return;

        // Endpoint correto por aba
        String endpoint;
        if ("AGENDAMENTOS".equals(name)) {
            endpoint = "/agendamentos";
        } else if ("CLIENTES".equals(name) && workshopId != null) {
            endpoint = "/oficinas/" + workshopId + "/clientes";
        } else {
            endpoint = tabEndpoints.get(name);
        }

        doGet(endpoint).thenAccept(res -> Platform.runLater(() -> {
            if (res == null || res.statusCode() != 200) return;
            try {
                List<Map<String, Object>> rows = GSON.fromJson(
                        res.body(),
                        new com.google.gson.reflect.TypeToken<List<Map<String, Object>>>(){}.getType()
                );
                if (rows == null) return;
                ObservableList<Map<String, Object>> data = FXCollections.observableArrayList();
                for (Map<String, Object> row : rows) {
                    Map<String, Object> normalized = new HashMap<>();
                    for (String key : keys)
                        normalized.put(key, row.getOrDefault(key, "-"));
                    // Preserva o id original para operações
                    if (row.containsKey("id"))
                        normalized.put("id", row.get("id"));
                    data.add(normalized);
                }
                table.setItems(data);
            } catch (Exception ignored) {}
        }));
    }

    // =========================================================================
    // HTTP — Métodos centralizados com JWT
    // =========================================================================

    private java.util.concurrent.CompletableFuture<HttpResponse<String>> doGet(String path) {
        HttpRequest.Builder rb = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + path))
                .GET();
        if (jwtToken != null)
            rb.header("Authorization", "Bearer " + jwtToken);
        return HTTP.sendAsync(rb.build(), HttpResponse.BodyHandlers.ofString())
                .exceptionally(ex -> null);
    }

    private java.util.concurrent.CompletableFuture<HttpResponse<String>> doPost(
            String path, Object body, String tokenOverride) {
        String json = GSON.toJson(body);
        HttpRequest.Builder rb = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json));
        String tok = tokenOverride != null ? tokenOverride : jwtToken;
        if (tok != null)
            rb.header("Authorization", "Bearer " + tok);
        return HTTP.sendAsync(rb.build(), HttpResponse.BodyHandlers.ofString())
                .exceptionally(ex -> null);
    }

    private java.util.concurrent.CompletableFuture<HttpResponse<String>> doPut(
            String path, Object body) {
        String json = GSON.toJson(body);
        HttpRequest.Builder rb = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + path))
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(json));
        if (jwtToken != null)
            rb.header("Authorization", "Bearer " + jwtToken);
        return HTTP.sendAsync(rb.build(), HttpResponse.BodyHandlers.ofString())
                .exceptionally(ex -> null);
    }

    private java.util.concurrent.CompletableFuture<HttpResponse<String>> doDelete(String path) {
        HttpRequest.Builder rb = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + path))
                .DELETE();
        if (jwtToken != null)
            rb.header("Authorization", "Bearer " + jwtToken);
        return HTTP.sendAsync(rb.build(), HttpResponse.BodyHandlers.ofString())
                .exceptionally(ex -> null);
    }

    // =========================================================================
    // UTILITÁRIOS
    // =========================================================================
    private StackPane buildWelcomePane() {
        StackPane pane = new StackPane();
        pane.getStyleClass().add("master-panel");
        Label lbl = new Label("Selecione uma opção no menu.");
        lbl.setStyle("-fx-text-fill:#9A9AAA; -fx-font-size:18px;");
        pane.getChildren().add(lbl);
        return pane;
    }

    private TextField styledField(String prompt) {
        TextField tf = new TextField();
        tf.setPromptText(prompt);
        tf.getStyleClass().add("text-field-styled");
        return tf;
    }

    private Label fieldLabel(String text) {
        Label l = new Label(text);
        l.setStyle("-fx-text-fill:#9A9AAA; -fx-font-size:10px; -fx-font-weight:600;");
        VBox.setMargin(l, new Insets(6, 0, 0, 0));
        return l;
    }

    private void showAlert(Alert.AlertType type, String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(type);
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }

    // =========================================================================
    // ENCERRAMENTO
    // =========================================================================
    @Override
    public void stop() {
        if (scheduler != null) scheduler.shutdownNow();
    }

    // =========================================================================
    // MAIN
    // =========================================================================
    public static void main(String[] args) {
        launch(args);
    }
}