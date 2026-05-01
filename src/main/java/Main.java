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
import javafx.stage.Stage;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CompletableFuture;

public class Main extends Application {

    // -------------------------------------------------------------------------
    // Constantes
    // -------------------------------------------------------------------------
    private static final String BASE_URL = "https://api-java-production-5e77.up.railway.app/";
    private static final HttpClient HTTP  = HttpClient.newBuilder()
            .connectTimeout(java.time.Duration.ofSeconds(15)).build();
    private static final Gson GSON = new Gson();

    // -------------------------------------------------------------------------
    // Estado
    // -------------------------------------------------------------------------
    private String workshopId      = null; // null = oficina não cadastrada ainda
    private String currentTab      = "VEÍCULOS";
    private final Map<String, TableView<Map<String, Object>>> tabTables    = new HashMap<>();
    private final Map<String, String>                         tabEndpoints = new HashMap<>();
    private final Map<String, String[]>                       tabKeys      = new HashMap<>();

    // Lat/Lon detectadas em segundo plano — nunca exibidas ao usuário
    private double detectedLat = 0.0;
    private double detectedLon = 0.0;

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

        // Pré-carrega localização em segundo plano ao iniciar o app
        prefetchLocation();

        // ============================================================
        // MUDANÇA 1: Inicia direto na tela de LOGIN, não no dashboard
        // ============================================================
        showLoginScreen();
    }

    // =========================================================================
    // PREFETCH DE LOCALIZAÇÃO (em segundo plano, sem interação do usuário)
    // =========================================================================
    private void prefetchLocation() {
        HTTP.sendAsync(
                        HttpRequest.newBuilder().uri(URI.create("http://ip-api.com/json/")).build(),
                        HttpResponse.BodyHandlers.ofString())
                .thenAccept(res -> {
                    Map<String, Object> d = GSON.fromJson(res.body(),
                            new com.google.gson.reflect.TypeToken<
                                    Map<String, Object>>(){}.getType());
                    if (d != null && "success".equals(d.get("status"))) {
                        try {
                            detectedLat = Double.parseDouble(String.valueOf(d.get("lat")));
                            detectedLon = Double.parseDouble(String.valueOf(d.get("lon")));
                        } catch (Exception ignored) {}
                    }
                }).exceptionally(ex -> null);
    }

    // =========================================================================
    // MUDANÇA 1 — TELA DE LOGIN
    // Dois caminhos: "Entrar no Dashboard" (tem oficina) ou "Cadastrar Oficina"
    // =========================================================================
    private void showLoginScreen() {
        VBox card = new VBox(28);
        card.setAlignment(Pos.CENTER);
        card.setMaxWidth(480);

        Label logo = new Label("MOTOR PRO");
        logo.getStyleClass().add("content-title");

        Label subtitle = new Label("Sistema de Gerenciamento de Oficinas");
        subtitle.setStyle("-fx-text-fill: #9A9AAA; -fx-font-size: 14px;");

        // Separador visual
        Separator sep = new Separator();
        sep.setMaxWidth(280);

        // — Botão principal: acessar dashboard (já tem oficina) —
        Button btnDashboard = new Button("ENTRAR NO DASHBOARD");
        btnDashboard.getStyleClass().add("big-button-primary");
        btnDashboard.setPrefSize(420, 64);
        btnDashboard.setOnAction(e -> showDashboard());

        // — Botão secundário: cadastrar nova oficina —
        Label lblOuSe = new Label("Não tem uma oficina cadastrada?");
        lblOuSe.setStyle("-fx-text-fill: #9A9AAA; -fx-font-size: 12px;");

        Button btnCadastrar = new Button("CADASTRAR OFICINA");
        btnCadastrar.getStyleClass().add("big-button-secondary"); // estilo diferente no CSS
        btnCadastrar.setPrefSize(420, 52);
        // ============================================================
        // MUDANÇA 2: Vai para tela de cadastro/pagamento STANDALONE
        // (fora do dashboard)
        // ============================================================
        btnCadastrar.setOnAction(e -> showCadastroStandalone());

        card.getChildren().addAll(logo, subtitle, sep, btnDashboard, lblOuSe, btnCadastrar);

        StackPane login = new StackPane(card);
        login.getStyleClass().add("master-panel");

        root.getChildren().setAll(login);
    }

    // =========================================================================
    // MUDANÇA 2 — TELA DE CADASTRO STANDALONE (fora do dashboard)
    // Inclui formulário + seção de pagamento
    // =========================================================================
    private void showCadastroStandalone() {
        // ---- Cabeçalho com botão Voltar ----
        HBox topBar = new HBox(16);
        topBar.setAlignment(Pos.CENTER_LEFT);
        topBar.setPadding(new Insets(20, 32, 0, 32));

        Button btnVoltar = new Button("← VOLTAR");
        btnVoltar.getStyleClass().add("sidebar-button");
        btnVoltar.setOnAction(e -> showLoginScreen());

        Label topTitle = new Label("CADASTRO DE OFICINA");
        topTitle.setStyle("-fx-font-size: 20px; -fx-font-weight: 700; -fx-text-fill: #16BC4E;");

        topBar.getChildren().addAll(btnVoltar, topTitle);

        // ---- Conteúdo rolável ----
        VBox content = new VBox(32);
        content.getStyleClass().add("dashboard-content");
        content.setPadding(new Insets(24, 32, 48, 32));

        // — SEÇÃO 1: Dados da Oficina —
        Label secDados = sectionTitle("1. DADOS DA EMPRESA");

        VBox cardDados = formCard("");
        cardDados.getChildren().remove(0); // remove label vazio criado por formCard

        // MUDANÇA 3: Lat/Lon NÃO aparecem no formulário — preenchidos em 2º plano
        TextField txtNome  = styledField("Ex: Auto Center Araújo");
        TextField txtLocal = styledField("Ex: Rua das Flores, 123, Fortaleza - CE");
        TextField txtTel   = styledField("Ex: (85) 99999-9999");
        TextField txtEmail = styledField("Ex: contato@autocenter.com");
        TextField txtHorarioFuncionamento = styledField("Ex: Seg-Sex 08:00-18:00");
        TextField txtWebsite = styledField("Ex: www.autocenter.com");
        TextField txtFormasPagamento = styledField("Ex: Cartão, Dinheiro, Pix");
        TextField txtEspecialidades = styledField("Ex: Alinhamento, Balanceamento, Troca de Óleo");

        cardDados.getChildren().addAll(
                fieldLabel("NOME DA OFICINA"), txtNome,
                fieldLabel("ENDEREÇO"),        txtLocal,
                fieldLabel("TELEFONE"),        txtTel,
                fieldLabel("EMAIL"),           txtEmail,
                fieldLabel("HORÁRIO DE FUNCIONAMENTO"), txtHorarioFuncionamento,
                fieldLabel("WEBSITE (OPCIONAL)"), txtWebsite,
                fieldLabel("FORMAS DE PAGAMENTO"), txtFormasPagamento,
                fieldLabel("ESPECIALIDADES"), txtEspecialidades);

        // — SEÇÃO 2: Serviços —
        Label secServicos = sectionTitle("2. SERVIÇOS OFERECIDOS");

        VBox cardServicos = formCard("");
        cardServicos.getChildren().remove(0);
        String[] services = {"Freios", "Suspensão", "Motor", "Óleo", "Revisão", "Elétrica"};
        List<CheckBox> checkBoxes = new ArrayList<>();
        GridPane gridServs = new GridPane();
        gridServs.setHgap(24); gridServs.setVgap(12);
        for (int i = 0; i < services.length; i++) {
            CheckBox cb = new CheckBox(services[i]);
            cb.getStyleClass().add("check-box");
            checkBoxes.add(cb);
            gridServs.add(cb, i % 3, i / 3);
        }
        cardServicos.getChildren().add(gridServs);

        // — SEÇÃO 3: Pagamento —
        Label secPag = sectionTitle("3. PLANO & PAGAMENTO");

        VBox cardPag = formCard("");
        cardPag.getChildren().remove(0);

        // Seleção de plano
        Label lblPlano = fieldLabel("SELECIONE SEU PLANO");
        ToggleGroup grupoPlano = new ToggleGroup();

        RadioButton planoBasico    = planoRadio("BÁSICO",       "R$ 49,90/mês — 1 usuário, 50 agendamentos/mês",    grupoPlano);
        RadioButton planoProfissional = planoRadio("PROFISSIONAL","R$ 99,90/mês — 5 usuários, agendamentos ilimitados", grupoPlano);
        RadioButton planoEnterprise   = planoRadio("ENTERPRISE",  "R$ 199,90/mês — ilimitado + suporte prioritário",    grupoPlano);
        planoBasico.setSelected(true);

        // Dados do cartão
        Label lblCartao = fieldLabel("DADOS DO CARTÃO");
        TextField txtTitular  = styledField("Nome impresso no cartão");
        TextField txtNumero   = styledField("0000 0000 0000 0000");
        HBox rowCartao = new HBox(16);
        TextField txtValidade = styledField("MM/AA");
        TextField txtCvv      = styledField("CVV");
        rowCartao.getChildren().addAll(txtValidade, txtCvv);
        HBox.setHgrow(txtValidade, Priority.ALWAYS);
        HBox.setHgrow(txtCvv,      Priority.ALWAYS);

        Label lblSeguro = new Label("🔒  Pagamento seguro via SSL. Seus dados não são armazenados.");
        lblSeguro.setStyle("-fx-text-fill: #9A9AAA; -fx-font-size: 11px;");

        cardPag.getChildren().addAll(
                lblPlano,
                planoBasico, planoProfissional, planoEnterprise,
                fieldLabel("TITULAR DO CARTÃO"), txtTitular,
                lblCartao, txtNumero,
                rowCartao,
                lblSeguro);

        // — Botão Finalizar —
        Button btnFinalizar = new Button("CADASTRAR E ATIVAR PLANO");
        btnFinalizar.getStyleClass().add("big-button-primary");
        btnFinalizar.setPrefSize(360, 60);
        btnFinalizar.setMaxWidth(Double.MAX_VALUE);

        btnFinalizar.setOnAction(e -> {
            btnFinalizar.setText("ENVIANDO..."); btnFinalizar.setDisable(true);

            List<String> servs = new ArrayList<>();
            for (CheckBox cb : checkBoxes) if (cb.isSelected()) servs.add(cb.getText());

            RadioButton planoSel = (RadioButton) grupoPlano.getSelectedToggle();
            String planoNome = planoSel != null ? planoSel.getText() : "BÁSICO";

            // Step 1: Initiate Registration
            Map<String, Object> initiateData = new HashMap<>();
            initiateData.put("nome",      txtNome.getText());
            initiateData.put("endereco",  txtLocal.getText());
            initiateData.put("telefone",  txtTel.getText());
            initiateData.put("email",     txtEmail.getText());
            initiateData.put("latitude",  detectedLat);
            initiateData.put("longitude", detectedLon);

            sendToAPI("POST", "api/oficinas/initiate-registration", initiateData)
                .thenCompose(initiateRes -> {
                    if (initiateRes.get("success") != null && (Boolean) initiateRes.get("success")) {
                        String registrationToken = (String) initiateRes.get("registrationToken");
                        if (registrationToken == null || registrationToken.isEmpty()) {
                            return CompletableFuture.completedFuture(Map.of("success", false, "message", "Token de registro não recebido."));
                        }

                        // Step 2: Complete Registration
                        Map<String, Object> completeData = new HashMap<>();
                        completeData.put("registrationToken", registrationToken);
                        completeData.put("horarioFuncionamento", txtHorarioFuncionamento.getText());
                        completeData.put("website", txtWebsite.getText());
                        completeData.put("formasPagamento", txtFormasPagamento.getText());
                        completeData.put("especialidades", txtEspecialidades.getText());
                        completeData.put("servicos",  String.join(", ", servs));
                        completeData.put("plano",     planoNome);

                        return sendToAPI("POST", "api/oficinas/complete-registration", completeData);
                    } else {
                        return CompletableFuture.completedFuture(initiateRes); // Pass along the error from initiate
                    }
                })
                .thenAccept(finalRes -> Platform.runLater(() -> {
                    btnFinalizar.setText("CADASTRAR E ATIVAR PLANO");
                    btnFinalizar.setDisable(false);
                    Alert alert = new Alert(Alert.AlertType.ERROR);
                    alert.setHeaderText(null);

                    if (finalRes.get("success") != null && (Boolean) finalRes.get("success")) {
                        alert.setAlertType(Alert.AlertType.INFORMATION);
                        alert.setContentText("✅ Oficina cadastrada com sucesso! Bem-vindo ao Motor Pro.");
                        alert.showAndWait();
                        // Assuming complete-registration returns the workshop ID
                        if (finalRes.get("id") != null) {
                            workshopId = finalRes.get("id").toString();
                        }
                        showDashboard();
                    } else {
                        String errorMessage = (String) finalRes.getOrDefault("message", "Erro desconhecido ao cadastrar. Verifique os dados e tente novamente.");
                        alert.setContentText("❌ " + errorMessage);
                        alert.showAndWait();
                    }
                }));
        });

        content.getChildren().addAll(
                secDados, cardDados,
                secServicos, cardServicos,
                secPag, cardPag,
                btnFinalizar);

        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background-color: transparent; -fx-background: #0B0B0B;");

        VBox fullScreen = new VBox(topBar, scroll);
        fullScreen.getStyleClass().add("master-panel");
        VBox.setVgrow(scroll, Priority.ALWAYS);

        root.getChildren().setAll(fullScreen);
    }

    // =========================================================================
    // DASHBOARD (só acessível após login ou cadastro concluído)
    // =========================================================================
    private void showDashboard() {
        dashPane = new BorderPane();
        dashPane.getStyleClass().add("master-panel");

        dashPane.setLeft(buildSidebar());
        dashPane.setCenter(buildWelcomePane());

        root.getChildren().setAll(dashPane);

        // Auto-refresh a cada 5 s
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "auto-refresh");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleAtFixedRate(
                () -> Platform.runLater(this::autoRefresh), 5, 5, TimeUnit.SECONDS);
    }

    // -------------------------------------------------------------------------
    // Sidebar do dashboard
    // -------------------------------------------------------------------------
    private VBox buildSidebar() {
        VBox sidebar = new VBox(8);
        sidebar.getStyleClass().add("sidebar");
        sidebar.setPrefWidth(280);

        Label brand = new Label("MOTOR PRO");
        brand.setStyle("-fx-font-size:22px; -fx-font-weight:700; -fx-text-fill: #16BC4E;");
        brand.setPadding(new Insets(0, 0, 24, 0));

        Button btnClientes     = sidebarBtn("👥", "CLIENTES");
        Button btnAgendamentos = sidebarBtn("📅", "AGENDAMENTOS");
        Button btnSair         = sidebarBtn("🚪", "SAIR");

        registerTab("CLIENTES",     "api/users",     new String[]{"Nome", "Email", "ID"},
                new String[]{"nome", "email", "id"}, btnClientes);
        registerTab("AGENDAMENTOS", "agendamentos", new String[]{"Cliente", "Serviço", "Horário"},
                new String[]{"userId", "servico", "horario"}, btnAgendamentos);

        // Botão Sair — volta para tela de login
        btnSair.setOnAction(e -> {
            if (scheduler != null) scheduler.shutdownNow();
            showLoginScreen();
        });

        sidebar.getChildren().addAll(brand, btnClientes, btnAgendamentos,
                new Separator(), btnSair);

        Platform.runLater(() -> btnClientes.fire());

        return sidebar;
    }

    private Button sidebarBtn(String icon, String label) {
        Button btn = new Button(icon + "  " + label);
        btn.getStyleClass().add("sidebar-button");
        btn.setMaxWidth(Double.MAX_VALUE);
        return btn;
    }

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
            final String key = keys[i];
            TableColumn<Map<String, Object>, Object> col = new TableColumn<>(cols[i]);
            col.setCellValueFactory(data ->
                    new SimpleObjectProperty<>(data.getValue().get(key)));
            col.setStyle("-fx-alignment: CENTER;");
            table.getColumns().add(col);
        }
        return table;
    }

    private void addStatusContextMenu(TableView<Map<String, Object>> table) {
        ContextMenu menu = new ContextMenu();
        String[] statuses = {"PENDENTE", "CONFIRMADO", "CANCELADO", "CONCLUIDO"};
        for (String s : statuses) {
            MenuItem item = new MenuItem("Marcar como " + s);
            item.setOnAction(e -> {
                Map<String, Object> selected = table.getSelectionModel().getSelectedItem();
                if (selected != null) {
                    String id = String.valueOf(selected.get("id"));
                    Map<String, String> body = Map.of("status", s);
                    sendToAPI("PATCH", "api/appointments/" + id + "/status", body)
                            .thenRun(() -> Platform.runLater(() -> refreshTab(currentTab)));
                }
            });
            menu.getItems().add(item);
        }
        table.setRowFactory(tv -> {
            TableRow<Map<String, Object>> row = new TableRow<>();
            row.contextMenuProperty().bind(
                    javafx.beans.binding.Bindings.when(row.emptyProperty())
                            .then((ContextMenu) null).otherwise(menu));
            return row;
        });
    }

    private StackPane buildWelcomePane() {
StackPane pane = new StackPane();
        pane.getStyleClass().add("master-panel");
        Label lbl = new Label("Selecione uma opção no menu.");
        lbl.setStyle("-fx-text-fill: #9A9AAA; -fx-font-size:18px;");
        pane.getChildren().add(lbl);
        return pane;
    }

    // =========================================================================
    // Helpers de formulário
    // =========================================================================
    private Label sectionTitle(String text) {
        Label l = new Label(text);
        l.setStyle("-fx-text-fill: #16BC4E; -fx-font-size: 15px; -fx-font-weight: 700;");
        VBox.setMargin(l, new Insets(8, 0, 4, 0));
        return l;
    }

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

    private RadioButton planoRadio(String nome, String descricao, ToggleGroup grupo) {
        RadioButton rb = new RadioButton(nome + " — " + descricao);
        rb.setToggleGroup(grupo);
        rb.setText(nome); // texto principal
        rb.setStyle("-fx-text-fill: #E8E8F0; -fx-font-size: 13px; -fx-font-weight: 600;");
        // Tooltip com a descrição completa
        Tooltip tp = new Tooltip(descricao);
        Tooltip.install(rb, tp);

        // Label de detalhe ao lado
        Label detalhe = new Label(descricao);
        detalhe.setStyle("-fx-text-fill: #9A9AAA; -fx-font-size: 11px;");

        // Agrupa radio + detalhe numa linha (HBox)
        HBox linha = new HBox(12, rb, detalhe);
        linha.setAlignment(Pos.CENTER_LEFT);
        linha.setPadding(new Insets(4, 0, 4, 0));

        // Workaround: encapsula num VBox para poder adicionar via getChildren().add()
        // Retorna só o RadioButton; a descrição aparece via Tooltip
        return rb;
    }

    // =========================================================================
    // Refresh
    // =========================================================================
    private void autoRefresh() {
        if (tabEndpoints.containsKey(currentTab)) refreshTab(currentTab);
    }

    private void refreshTab(String name) {
        String endpoint = tabEndpoints.get(name);
        String[] keys   = tabKeys.get(name);
        TableView<Map<String, Object>> table = tabTables.get(name);
        if (endpoint == null || table == null) return;

        if ("AGENDAMENTOS".equals(name)) {
            endpoint = "api/appointments/oficina/" + workshopId;
            if (table.getRowFactory() == null) addStatusContextMenu(table);
        }

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
    private CompletableFuture<Map<String, Object>> sendToAPI(
            String method, String endpoint, Object data) {

        String json = GSON.toJson(data);
        HttpRequest.Builder rb = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + endpoint))
                .header("Content-Type", "application/json");
        if ("POST".equals(method))  rb.POST(HttpRequest.BodyPublishers.ofString(json));
        else if ("PUT".equals(method)) rb.PUT(HttpRequest.BodyPublishers.ofString(json));
        if ("PATCH".equals(method)) rb.method("PATCH", HttpRequest.BodyPublishers.ofString(json));

        return HTTP.sendAsync(rb.build(), HttpResponse.BodyHandlers.ofString())
                .thenApply(res -> {
                    System.out.println("Status: " + res.statusCode() + " | " + res.body());
                    Map<String, Object> responseMap = new HashMap<>();
                    responseMap.put("success", res.statusCode() >= 200 && res.statusCode() < 300);
                    try {
                        Map<String, Object> r = GSON.fromJson(res.body(),
                                new com.google.gson.reflect.TypeToken<
                                        Map<String, Object>>(){}.getType());
                        if (r != null) {
                            responseMap.putAll(r);
                        }
                    } catch (Exception ignored) {
                        responseMap.put("message", "Erro ao processar resposta da API.");
                    }
                    return responseMap;
                }).exceptionally(ex -> {
                    Map<String, Object> errorMap = new HashMap<>();
                    errorMap.put("success", false);
                    errorMap.put("message", "Erro de comunicação com a API: " + ex.getMessage());
                    return errorMap;
                });
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