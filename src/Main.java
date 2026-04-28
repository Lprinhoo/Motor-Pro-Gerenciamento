import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.MapValueFactory;
import javafx.scene.image.Image;
import javafx.scene.layout.*;
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
    private static final String BASE_URL = "https://teste-production-a485.up.railway.app/api/";
    private static final HttpClient HTTP  = HttpClient.newBuilder()
            .connectTimeout(java.time.Duration.ofSeconds(15)).build();
    private static final Gson GSON = new Gson();

    // -------------------------------------------------------------------------
    // Estado
    // -------------------------------------------------------------------------
    private String workshopId      = "";
    private String currentTab      = "VEÍCULOS";
    private final Map<String, TableView<Map<String, Object>>> tabTables = new HashMap<>();
    private final Map<String, String>   tabEndpoints = new HashMap<>();
    private final Map<String, String[]> tabKeys      = new HashMap<>();

    private ScheduledExecutorService scheduler;

    // Layout raiz
    private StackPane root;
    private BorderPane dashPane;

    // -------------------------------------------------------------------------
    // Inicialização JavaFX
    // -------------------------------------------------------------------------
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

        showLaunchScreen();
    }

    // =========================================================================
    // LAUNCH SCREEN
    // =========================================================================
    private void showLaunchScreen() {
        VBox card = new VBox(40);
        card.setAlignment(Pos.CENTER);

        Label logo = new Label("MOTOR PRO");
        logo.getStyleClass().add("content-title");   // Ardela, Ice White via CSS

        Button btnEnter = new Button("ENTRAR NO DASHBOARD");
        btnEnter.getStyleClass().add("big-button-primary");
        btnEnter.setPrefSize(420, 72);
        btnEnter.setOnAction(e -> showDashboard());

        card.getChildren().addAll(logo, btnEnter);

        // fundo Midnight via classe master-panel
        StackPane launch = new StackPane(card);
        launch.getStyleClass().add("master-panel");

        root.getChildren().setAll(launch);
    }

    // =========================================================================
    // DASHBOARD
    // =========================================================================
    private void showDashboard() {
        dashPane = new BorderPane();
        dashPane.getStyleClass().add("master-panel");

        dashPane.setLeft(buildSidebar());
        dashPane.setCenter(buildWelcomePane());  // painel inicial

        root.getChildren().setAll(dashPane);

        // Auto-refresh a cada 5 s
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "auto-refresh");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleAtFixedRate(() -> Platform.runLater(this::autoRefresh), 5, 5, TimeUnit.SECONDS);
    }

    // -------------------------------------------------------------------------
    // Sidebar
    // -------------------------------------------------------------------------
    private VBox buildSidebar() {
        VBox sidebar = new VBox(8);
        sidebar.getStyleClass().add("sidebar");
        sidebar.setPrefWidth(280);

        Label brand = new Label("MOTOR PRO");
        brand.setStyle("-fx-font-size:22px; -fx-font-weight:700; -fx-text-fill: #16BC4E;");
        brand.setPadding(new Insets(0, 0, 24, 0));

        Button btnVeiculos      = sidebarBtn("🚗", "VEÍCULOS");
        Button btnAgendamentos  = sidebarBtn("📅", "AGENDAMENTOS");
        Button btnOficina       = sidebarBtn("➕", "ADICIONAR OFICINA");

        registerTab("VEÍCULOS",     "veiculos",     new String[]{"Placa", "Modelo", "Ano"},
                new String[]{"placa", "modelo", "ano"}, btnVeiculos);
        registerTab("AGENDAMENTOS", "agendamentos", new String[]{"Cliente", "Serviço", "Horário"},
                new String[]{"userId", "servico", "horario"}, btnAgendamentos);

        btnOficina.setOnAction(e -> {
            currentTab = "ADICIONAR OFICINA";
            clearSelected(sidebar);
            btnOficina.getStyleClass().add("sidebar-button-selected");
            dashPane.setCenter(buildConfigPane(true));
        });

        sidebar.getChildren().addAll(brand, btnVeiculos, btnAgendamentos,
                new Separator(), btnOficina);

        // Seleciona Veículos por padrão
        Platform.runLater(() -> btnVeiculos.fire());

        return sidebar;
    }

    private Button sidebarBtn(String icon, String label) {
        Button btn = new Button(icon + "  " + label);
        btn.getStyleClass().add("sidebar-button");
        btn.setMaxWidth(Double.MAX_VALUE);
        return btn;
    }

    /** Registra uma aba de tabela e o evento do botão. */
    private void registerTab(String name, String endpoint, String[] cols,
                             String[] keys, Button btn) {
        tabEndpoints.put(name, endpoint);
        tabKeys.put(name, keys);

        TableView<Map<String, Object>> table = buildTable(cols, keys);
        tabTables.put(name, table);

        BorderPane viewPane = new BorderPane();
        viewPane.getStyleClass().add("dashboard-content");

        Label title = new Label(name);
        title.getStyleClass().add("content-title");
        BorderPane.setMargin(title, new Insets(0, 0, 24, 0));

        viewPane.setTop(title);
        viewPane.setCenter(table);

        btn.setOnAction(e -> {
            currentTab = name;
            clearSelected(btn.getParent());
            btn.getStyleClass().add("sidebar-button-selected");
            dashPane.setCenter(viewPane);
            refreshTab(name);
        });
    }

    private void clearSelected(javafx.scene.Parent parent) {
        parent.getChildrenUnmodifiable().forEach(n -> {
            if (n instanceof Button b)
                b.getStyleClass().remove("sidebar-button-selected");
        });
    }

    // -------------------------------------------------------------------------
    // Tabela genérica
    // -------------------------------------------------------------------------
    @SuppressWarnings("unchecked")
    private TableView<Map<String, Object>> buildTable(String[] cols, String[] keys) {
        TableView<Map<String, Object>> table = new TableView<>();
        table.getStyleClass().add("table-view");
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        for (int i = 0; i < cols.length; i++) {
            TableColumn<Map<String, Object>, Object> col = new TableColumn<>(cols[i]);
            col.setCellValueFactory(new MapValueFactory<>(keys[i]));
            col.setStyle("-fx-alignment: CENTER;");
            table.getColumns().add(col);
        }
        return table;
    }

    // -------------------------------------------------------------------------
    // Painel de boas-vindas (centro inicial antes de clicar)
    // -------------------------------------------------------------------------
    private StackPane buildWelcomePane() {
        StackPane pane = new StackPane();
        pane.getStyleClass().add("master-panel");
        Label lbl = new Label("Selecione uma opção no menu.");
        lbl.setStyle("-fx-text-fill: #9A9AAA; -fx-font-size:18px;");
        pane.getChildren().add(lbl);
        return pane;
    }

    // =========================================================================
    // CONFIG / CADASTRO
    // =========================================================================
    private ScrollPane buildConfigPane(boolean isNew) {
        VBox content = new VBox(30);
        content.getStyleClass().add("dashboard-content");

        // — Header —
        HBox header = new HBox();
        header.setAlignment(Pos.CENTER_LEFT);

        Label title = new Label(isNew ? "NOVO CADASTRO" : "CONFIGURAÇÃO");
        title.getStyleClass().add("content-title");
        HBox.setHgrow(title, Priority.ALWAYS);

        Button btnSave = new Button("SALVAR NO BANCO");
        btnSave.getStyleClass().add("big-button-primary");
        btnSave.setPrefSize(200, 52);

        header.getChildren().addAll(title, btnSave);
        content.getChildren().add(header);

        // — Grid de 3 colunas —
        HBox grid = new HBox(24);
        HBox.setHgrow(grid, Priority.ALWAYS);

        // Coluna 1 — Dados da empresa
        VBox card1 = formCard("DADOS DA EMPRESA");
        TextField txtNome  = styledField("NOME");
        TextField txtLocal = styledField("LOCALIDADE");
        TextField txtLat   = styledField("LATITUDE");
        TextField txtLon   = styledField("LONGITUDE");
        PasswordField txtSenha = new PasswordField();
        txtSenha.setPromptText("SENHA");
        txtSenha.getStyleClass().add("text-field-styled");
        card1.getChildren().addAll(
                fieldLabel("NOME"), txtNome,
                fieldLabel("LOCALIDADE"), txtLocal,
                fieldLabel("LATITUDE"), txtLat,
                fieldLabel("LONGITUDE"), txtLon,
                fieldLabel("SENHA"), txtSenha);

        // Coluna 2 — Mecânicos
        VBox card2 = formCard("MECÂNICOS");
        ObservableList<String> mecList = FXCollections.observableArrayList();
        ListView<String> mecView = new ListView<>(mecList);
        mecView.getStyleClass().add("table-view");
        mecView.setPrefHeight(220);

        TextField txtMec = styledField("Adicionar mecânico...");
        Button btnAddMec = new Button("+ ADD");
        btnAddMec.getStyleClass().add("big-button-secondary");
        btnAddMec.setOnAction(e -> {
            String v = txtMec.getText().trim();
            if (!v.isEmpty()) { mecList.add(v); txtMec.clear(); }
        });
        HBox mecRow = new HBox(8, txtMec, btnAddMec);
        HBox.setHgrow(txtMec, Priority.ALWAYS);
        card2.getChildren().addAll(mecRow, mecView);

        // Coluna 3 — Serviços
        VBox card3 = formCard("SERVIÇOS");
        String[] services = {"Freios", "Suspensão", "Motor", "Óleo", "Revisão", "Elétrica"};
        List<CheckBox> checkBoxes = new ArrayList<>();
        for (String s : services) {
            CheckBox cb = new CheckBox(s);
            cb.getStyleClass().add("check-box");
            checkBoxes.add(cb);
            card3.getChildren().add(cb);
        }

        grid.getChildren().addAll(card1, card2, card3);
        for (javafx.scene.Node n : grid.getChildren())
            HBox.setHgrow(n, Priority.ALWAYS);

        content.getChildren().add(grid);

        // — Ação salvar —
        btnSave.setOnAction(e -> {
            btnSave.setText("ENVIANDO..."); btnSave.setDisable(true);

            List<String> servs = new ArrayList<>();
            for (CheckBox cb : checkBoxes) if (cb.isSelected()) servs.add(cb.getText());

            Map<String, Object> data = new HashMap<>();
            data.put("nome",      txtNome.getText());
            data.put("localidade", txtLocal.getText());
            data.put("senha",     txtSenha.getText());
            try {
                data.put("latitude",  Double.parseDouble(txtLat.getText()));
                data.put("longitude", Double.parseDouble(txtLon.getText()));
            } catch (Exception ex) { data.put("latitude", 0.0); data.put("longitude", 0.0); }
            data.put("mecanicos", new ArrayList<>(mecList));
            data.put("servicos",  servs);
            data.put("horarios",  Arrays.asList("08:00", "12:00", "18:00"));

            String method   = isNew ? "POST" : "PUT";
            String endpoint = isNew ? "oficinas" : "oficinas/" + workshopId;

            sendToAPI(method, endpoint, data).thenAccept(ok ->
                    Platform.runLater(() -> {
                        btnSave.setText("SALVAR NO BANCO"); btnSave.setDisable(false);
                        Alert alert = new Alert(ok ? Alert.AlertType.INFORMATION : Alert.AlertType.ERROR);
                        alert.setHeaderText(null);
                        alert.setContentText(ok
                                ? "Sucesso! Oficina salva no banco."
                                : "Erro 500: Verifique mecânicos/serviços.");
                        alert.showAndWait();
                    })
            );
        });

        autoDetectLocation(txtLocal, txtLat, txtLon);

        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background-color: transparent; -fx-background: #0B0B0B;");
        return scroll;
    }

    // -------------------------------------------------------------------------
    // Helpers de formulário
    // -------------------------------------------------------------------------
    private VBox formCard(String title) {
        VBox card = new VBox(10);
        card.getStyleClass().add("form-card");
        Label lbl = new Label(title);
        lbl.setStyle("-fx-text-fill: #16BC4E; -fx-font-weight: 700; -fx-font-size: 13px;");
        card.getChildren().add(lbl);
        return card;
    }

    private TextField styledField(String prompt) {
        TextField tf = new TextField();
        tf.setPromptText(prompt);
        tf.getStyleClass().add("text-field-styled");
        return tf;
    }

    private Label fieldLabel(String text) {
        Label l = new Label(text);
        l.setStyle("-fx-text-fill: #9A9AAA; -fx-font-size: 10px; -fx-font-weight: 600;");
        VBox.setMargin(l, new Insets(6, 0, 0, 0));
        return l;
    }

    // =========================================================================
    // Refresh de dados
    // =========================================================================
    private void autoRefresh() {
        if (tabEndpoints.containsKey(currentTab)) refreshTab(currentTab);
    }

    private void refreshTab(String name) {
        String endpoint = tabEndpoints.get(name);
        String[] keys   = tabKeys.get(name);
        TableView<Map<String, Object>> table = tabTables.get(name);
        if (endpoint == null || table == null) return;

        HTTP.sendAsync(
                        HttpRequest.newBuilder().uri(URI.create(BASE_URL + endpoint)).build(),
                        HttpResponse.BodyHandlers.ofString())
                .thenAccept(res -> {
                    List<Map<String, Object>> rows = GSON.fromJson(
                            res.body(), new com.google.gson.reflect.TypeToken<
                                    List<Map<String, Object>>>(){}.getType());
                    if (rows == null) return;
                    Platform.runLater(() -> {
                        ObservableList<Map<String, Object>> data = FXCollections.observableArrayList();
                        for (Map<String, Object> row : rows) {
                            Map<String, Object> normalized = new HashMap<>();
                            for (String key : keys)
                                normalized.put(key, row.getOrDefault(key, "-"));
                            data.add(normalized);
                        }
                        table.setItems(data);
                    });
                }).exceptionally(ex -> null);
    }

    // =========================================================================
    // API HTTP
    // =========================================================================
    private java.util.concurrent.CompletableFuture<Boolean> sendToAPI(
            String method, String endpoint, Object data) {

        String json = GSON.toJson(data);
        HttpRequest.Builder rb = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + endpoint))
                .header("Content-Type", "application/json");
        if ("POST".equals(method)) rb.POST(HttpRequest.BodyPublishers.ofString(json));
        else                       rb.PUT(HttpRequest.BodyPublishers.ofString(json));

        return HTTP.sendAsync(rb.build(), HttpResponse.BodyHandlers.ofString())
                .thenApply(res -> {
                    System.out.println("Status: " + res.statusCode() + " | " + res.body());
                    if (res.statusCode() == 201) {
                        try {
                            Map<String, Object> r = GSON.fromJson(res.body(),
                                    new com.google.gson.reflect.TypeToken<
                                            Map<String, Object>>(){}.getType());
                            workshopId = r.get("id").toString();
                        } catch (Exception ignored) {}
                    }
                    return res.statusCode() >= 200 && res.statusCode() < 300;
                }).exceptionally(ex -> false);
    }

    // =========================================================================
    // Geolocalização
    // =========================================================================
    private void autoDetectLocation(TextField txtLoc, TextField txtLat, TextField txtLon) {
        HTTP.sendAsync(
                        HttpRequest.newBuilder().uri(URI.create("http://ip-api.com/json/")).build(),
                        HttpResponse.BodyHandlers.ofString())
                .thenAccept(res -> {
                    Map<String, Object> d = GSON.fromJson(res.body(),
                            new com.google.gson.reflect.TypeToken<
                                    Map<String, Object>>(){}.getType());
                    if (d != null && "success".equals(d.get("status"))) {
                        Platform.runLater(() -> {
                            txtLat.setText(String.valueOf(d.get("lat")));
                            txtLon.setText(String.valueOf(d.get("lon")));
                            txtLoc.setText(d.get("city") + ", " + d.get("regionName"));
                        });
                    }
                }).exceptionally(ex -> null);
    }

    // =========================================================================
    // Encerramento
    // =========================================================================
    @Override
    public void stop() {
        if (scheduler != null) scheduler.shutdownNow();
    }

    // =========================================================================
    // main
    // =========================================================================
    public static void main(String[] args) {
        launch(args);
    }
}