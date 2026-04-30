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
    private String workshopId      = "550e8400-e29b-41d4-a716-446655440000";
    private String currentTab      = "VEÍCULOS";
    private String jwtToken        = null; // Bearer token obtido no login
    private final Map<String, TableView<Map<String, Object>>> tabTables    = new HashMap<>();
    private final Map<String, String>                         tabEndpoints = new HashMap<>();
    private final Map<String, String[]>                       tabKeys      = new HashMap<>();

    private ScheduledExecutorService scheduler;

    // Layout raiz
    private StackPane  root;
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
    // TELA DE LOGIN
    // =========================================================================
    private void showLaunchScreen() {
        VBox card = new VBox(20);
        card.setAlignment(Pos.CENTER);
        card.setMaxWidth(420);

        Label logo = new Label("MOTOR PRO");
        logo.getStyleClass().add("content-title");
        VBox.setMargin(logo, new Insets(0, 0, 16, 0));

        // ── campos ──────────────────────────────────────────────────────────
        TextField    txtUser = styledField("Usuário");
        PasswordField txtPass = new PasswordField();
        txtPass.setPromptText("Senha");
        txtPass.getStyleClass().add("text-field-styled");

        Label lblError = new Label();
        lblError.setStyle("-fx-text-fill: #FF4C4C; -fx-font-size:12px;");
        lblError.setVisible(false);

        // ── botão login ──────────────────────────────────────────────────────
        Button btnLogin = new Button("ENTRAR");
        btnLogin.getStyleClass().add("big-button-primary");
        btnLogin.setMaxWidth(Double.MAX_VALUE);
        btnLogin.setPrefHeight(52);

        // ── botão entrar sem login (modo offline / sem autenticação) ─────────
        Button btnSkip = new Button("Entrar sem login  →");
        btnSkip.setStyle("-fx-background-color: transparent; -fx-text-fill: #9A9AAA; " +
                "-fx-font-size:12px; -fx-cursor: hand;");
        btnSkip.setOnAction(e -> showDashboard());

        Runnable doLogin = () -> {
            String user = txtUser.getText().trim();
            String pass = txtPass.getText();
            if (user.isBlank() || pass.isBlank()) {
                lblError.setText("Preencha usuário e senha.");
                lblError.setVisible(true);
                return;
            }
            btnLogin.setText("ENTRANDO..."); btnLogin.setDisable(true);
            lblError.setVisible(false);

            postLogin(user, pass).thenAccept(token ->
                    Platform.runLater(() -> {
                        btnLogin.setText("ENTRAR"); btnLogin.setDisable(false);
                        if (token != null) {
                            jwtToken = token;
                            showDashboard();
                        } else {
                            lblError.setText("Usuário ou senha incorretos.");
                            lblError.setVisible(true);
                        }
                    })
            );
        };

        btnLogin.setOnAction(e -> doLogin.run());
        // Enter nos campos também dispara o login
        txtUser.setOnAction(e -> doLogin.run());
        txtPass.setOnAction(e -> doLogin.run());

        card.getChildren().addAll(logo,
                fieldLabel("USUÁRIO"), txtUser,
                fieldLabel("SENHA"),   txtPass,
                lblError, btnLogin, btnSkip);

        StackPane launch = new StackPane(card);
        launch.getStyleClass().add("master-panel");
        root.getChildren().setAll(launch);
    }

    /**
     * POST /oficina-users/login (ou /auth/login)
     * Retorna o JWT token ou null se falhar.
     * Tenta os dois endpoints mais comuns de Spring Security.
     */
    private java.util.concurrent.CompletableFuture<String> postLogin(String username, String password) {
        // Tenta /oficina-users/login primeiro; se não existir tenta /auth/login
        return tryLoginEndpoint("oficina-users/login", username, password)
                .thenCompose(token -> {
                    if (token != null) return java.util.concurrent.CompletableFuture.completedFuture(token);
                    return tryLoginEndpoint("auth/login", username, password);
                });
    }

    private java.util.concurrent.CompletableFuture<String> tryLoginEndpoint(
            String endpoint, String username, String password) {

        Map<String, String> body = new LinkedHashMap<>();
        body.put("username", username);
        body.put("password", password);
        String json = GSON.toJson(body);

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + endpoint))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        return HTTP.sendAsync(req, HttpResponse.BodyHandlers.ofString())
                .thenApply(res -> {
                    System.out.println("[login:" + endpoint + "] Status: "
                            + res.statusCode() + " | " + res.body());
                    if (res.statusCode() == 200 || res.statusCode() == 201) {
                        try {
                            Map<String, Object> r = GSON.fromJson(res.body(),
                                    new com.google.gson.reflect.TypeToken<
                                            Map<String, Object>>(){}.getType());
                            // Aceita chaves comuns: token, accessToken, jwt, access_token
                            for (String key : new String[]{"token","accessToken","jwt","access_token"}) {
                                if (r.containsKey(key)) return (String) r.get(key);
                            }
                        } catch (Exception ignored) {}
                    }
                    return null;
                }).exceptionally(ex -> null);
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

        Button btnClientes     = sidebarBtn("👥", "CLIENTES");
        Button btnAgendamentos = sidebarBtn("📅", "AGENDAMENTOS");
        Button btnOficina      = sidebarBtn("➕", "ADICIONAR OFICINA");

        registerTab("CLIENTES",     "api/users",     new String[]{"Nome", "Email", "ID"},
                new String[]{"nome", "email", "id"}, btnClientes);
        registerTab("AGENDAMENTOS", "agendamentos", new String[]{"Cliente", "Serviço", "Horário"},
                new String[]{"userId", "servico", "horario"}, btnAgendamentos);

        // Abre o assistente de 3 etapas de cadastro de oficina
        btnOficina.setOnAction(e -> {
            currentTab = "ADICIONAR OFICINA";
            clearSelected(sidebar);
            btnOficina.getStyleClass().add("sidebar-button-selected");
            dashPane.setCenter(buildRegistrationWizard());
        });

        sidebar.getChildren().addAll(brand, btnClientes, btnAgendamentos,
                new Separator(), btnOficina);

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

    // -------------------------------------------------------------------------
    // Painel de boas-vindas
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
    // WIZARD DE CADASTRO EM 3 ETAPAS
    // =========================================================================
    /**
     * Etapa 1 — Processar pagamento (POST /oficinas/initiate-registration)
     * Simula o pagamento do plano e recebe o registrationToken.
     * Após sucesso, avança para a Etapa 2.
     */
    private ScrollPane buildRegistrationWizard() {
        VBox content = new VBox(30);
        content.getStyleClass().add("dashboard-content");

        // ── Cabeçalho ────────────────────────────────────────────────────────
        Label title = new Label("CADASTRAR NOVA OFICINA");
        title.getStyleClass().add("content-title");

        Label stepLabel = new Label("ETAPA 1 DE 3  —  Pagamento do Plano");
        stepLabel.setStyle("-fx-text-fill: #16BC4E; -fx-font-size:13px; -fx-font-weight:700;");

        Label description = new Label(
                "Selecione um plano e confirme o pagamento para gerar o token de registro da oficina.\n" +
                        "O token será usado na próxima etapa para completar o cadastro.");
        description.setStyle("-fx-text-fill: #9A9AAA; -fx-font-size:13px;");
        description.setWrapText(true);

        // ── Seleção de plano ─────────────────────────────────────────────────
        VBox planCard = formCard("PLANO DE ASSINATURA");
        ToggleGroup planGroup = new ToggleGroup();
        String[][] plans = {
                {"Básico",    "R$ 99/mês  — até 2 usuários, relatórios básicos"},
                {"Profissional", "R$ 199/mês — até 10 usuários, relatórios avançados"},
                {"Empresarial",  "R$ 399/mês — usuários ilimitados, suporte prioritário"}
        };
        for (String[] plan : plans) {
            RadioButton rb = new RadioButton(plan[0] + "   |   " + plan[1]);
            rb.setToggleGroup(planGroup);
            rb.setStyle("-fx-text-fill: #E0E0E0; -fx-font-size:13px;");
            VBox.setMargin(rb, new Insets(4, 0, 4, 0));
            planCard.getChildren().add(rb);
        }
        // Seleciona o primeiro plano por padrão
        planGroup.getToggles().get(0).setSelected(true);

        // ── Dados do cartão (simulação) ──────────────────────────────────────
        VBox payCard = formCard("DADOS DE PAGAMENTO");
        TextField txtCardName   = styledField("Nome no cartão");
        TextField txtCardNumber = styledField("Número do cartão (ex: 4111 1111 1111 1111)");
        HBox expiryRow = new HBox(12);
        TextField txtExpiry = styledField("Validade (MM/AA)");
        TextField txtCvv    = styledField("CVV");
        txtExpiry.setPrefWidth(160);
        txtCvv.setPrefWidth(100);
        expiryRow.getChildren().addAll(txtExpiry, txtCvv);

        payCard.getChildren().addAll(
                fieldLabel("NOME NO CARTÃO"),   txtCardName,
                fieldLabel("NÚMERO DO CARTÃO"), txtCardNumber,
                fieldLabel("VALIDADE / CVV"),   expiryRow
        );

        // ── Card de resultado (token) ─────────────────────────────────────────
        VBox resultCard = formCard("TOKEN DE REGISTRO GERADO");
        resultCard.setVisible(false);
        resultCard.setManaged(false);
        resultCard.setStyle("-fx-border-color: #16BC4E; -fx-border-width: 1; -fx-border-radius: 6;");

        Label tokenLabel = new Label("—");
        tokenLabel.setStyle(
                "-fx-text-fill: #16BC4E; -fx-font-family: monospace; " +
                        "-fx-font-size: 15px; -fx-font-weight:700;");
        tokenLabel.setWrapText(true);

        Label tokenMsg = new Label(
                "✅  Pagamento confirmado! Guarde este token — ele é necessário para completar o cadastro da oficina.");
        tokenMsg.setStyle("-fx-text-fill: #9A9AAA; -fx-font-size:12px;");
        tokenMsg.setWrapText(true);

        resultCard.getChildren().addAll(fieldLabel("TOKEN"), tokenLabel, tokenMsg);

        // ── Botões ────────────────────────────────────────────────────────────
        Button btnPay = new Button("CONFIRMAR PAGAMENTO");
        btnPay.getStyleClass().add("big-button-primary");
        btnPay.setPrefSize(300, 52);

        Button btnNext = new Button("PRÓXIMA ETAPA →");
        btnNext.getStyleClass().add("big-button-primary");
        btnNext.setPrefSize(220, 52);
        btnNext.setVisible(false);
        btnNext.setManaged(false);

        final String[] generatedToken = {null};

        btnPay.setOnAction(e -> {
            btnPay.setText("PROCESSANDO..."); btnPay.setDisable(true);

            postInitiateRegistration().thenAccept(token -> // Chamada corrigida
                    Platform.runLater(() -> {
                        btnPay.setText("CONFIRMAR PAGAMENTO"); btnPay.setDisable(false);
                        if (token != null) {
                            generatedToken[0] = token;
                            tokenLabel.setText(token);
                            resultCard.setVisible(true);
                            resultCard.setManaged(true);
                            btnNext.setVisible(true);
                            btnNext.setManaged(true);
                            btnPay.setVisible(false);
                            btnPay.setManaged(false);
                        } else {
                            showAlert(Alert.AlertType.ERROR,
                                    "Falha ao processar pagamento. Verifique os dados e a conexão com a API.");
                        }
                    })
            );
        });

        btnNext.setOnAction(e ->
                dashPane.setCenter(buildStep2Pane(generatedToken[0]))
        );

        content.getChildren().addAll(
                title, stepLabel, description, planCard, payCard, btnPay, resultCard, btnNext);

        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background-color: transparent; -fx-background: #0B0B0B;");
        return scroll;
    }

    /**
     * Etapa 2 — Completar registro (POST /oficinas/complete-registration)
     * Formulário completo da oficina usando o token gerado na Etapa 1.
     */
    private ScrollPane buildStep2Pane(String registrationToken) {
        VBox content = new VBox(30);
        content.getStyleClass().add("dashboard-content");

        // ── Cabeçalho ────────────────────────────────────────────────────────
        Label title = new Label("CADASTRAR NOVA OFICINA");
        title.getStyleClass().add("content-title");

        Label stepLabel = new Label("ETAPA 2 DE 3  —  Dados da Oficina");
        stepLabel.setStyle("-fx-text-fill: #16BC4E; -fx-font-size:13px; -fx-font-weight:700;");

        // ── Exibir token em uso ───────────────────────────────────────────────
        VBox tokenInfo = formCard("TOKEN ATIVO");
        Label tokenDisplay = new Label(registrationToken != null ? registrationToken : "—");
        tokenDisplay.setStyle("-fx-text-fill: #16BC4E; -fx-font-family: monospace; -fx-font-size:12px;");
        tokenDisplay.setWrapText(true);
        tokenInfo.getChildren().addAll(fieldLabel("REGISTRATION TOKEN"), tokenDisplay);

        // ── Grid de formulário ────────────────────────────────────────────────
        HBox grid = new HBox(24);
        HBox.setHgrow(grid, Priority.ALWAYS);

        // Coluna 1 — Identificação
        VBox card1 = formCard("IDENTIFICAÇÃO");
        TextField txtNome     = styledField("Nome da Oficina");
        TextField txtEmail    = styledField("E-mail");
        TextField txtTel      = styledField("Telefone");
        TextField txtWebsite  = styledField("Website (opcional)");
        card1.getChildren().addAll(
                fieldLabel("NOME"), txtNome,
                fieldLabel("E-MAIL"), txtEmail,
                fieldLabel("TELEFONE"), txtTel,
                fieldLabel("WEBSITE"), txtWebsite
        );

        // Coluna 2 — Localização
        VBox card2 = formCard("LOCALIZAÇÃO");
        TextField txtEndereco = styledField("Endereço completo");
        TextField txtLat      = styledField("Latitude");
        TextField txtLon      = styledField("Longitude");
        card2.getChildren().addAll(
                fieldLabel("ENDEREÇO"), txtEndereco,
                fieldLabel("LATITUDE"), txtLat,
                fieldLabel("LONGITUDE"), txtLon
        );

        // Coluna 3 — Operação
        VBox card3 = formCard("OPERAÇÃO");
        TextField txtHorario       = styledField("Ex: Seg-Sex 08:00-18:00");
        TextField txtFormasPag     = styledField("Ex: Cartão, Dinheiro, Pix");
        TextField txtEspecialidades = styledField("Ex: Mecânica Geral, Elétrica");
        card3.getChildren().addAll(
                fieldLabel("HORÁRIO DE FUNCIONAMENTO"), txtHorario,
                fieldLabel("FORMAS DE PAGAMENTO"), txtFormasPag,
                fieldLabel("ESPECIALIDADES"), txtEspecialidades
        );

        // Coluna 4 — Serviços
        VBox card4 = formCard("SERVIÇOS");
        String[] services = {"Freios", "Suspensão", "Motor", "Óleo", "Revisão", "Elétrica"};
        List<CheckBox> checkBoxes = new ArrayList<>();
        for (String s : services) {
            CheckBox cb = new CheckBox(s);
            cb.getStyleClass().add("check-box");
            checkBoxes.add(cb);
            card4.getChildren().add(cb);
        }

        grid.getChildren().addAll(card1, card2, card3, card4);
        for (javafx.scene.Node n : grid.getChildren())
            HBox.setHgrow(n, Priority.ALWAYS);

        // ── Botão salvar ─────────────────────────────────────────────────────
        Button btnSave = new Button("CONCLUIR CADASTRO DA OFICINA");
        btnSave.getStyleClass().add("big-button-primary");
        btnSave.setPrefSize(380, 52);

        // Referência mutável para capturar o oficinaId retornado
        final long[] resolvedOficinaId = {-1L};

        btnSave.setOnAction(e -> {
            if (txtNome.getText().isBlank() || txtEmail.getText().isBlank()
                    || txtTel.getText().isBlank() || txtEndereco.getText().isBlank()) {
                showAlert(Alert.AlertType.WARNING, "Preencha ao menos: Nome, E-mail, Telefone e Endereço.");
                return;
            }

            btnSave.setText("ENVIANDO..."); btnSave.setDisable(true);

            List<String> servs = new ArrayList<>();
            for (CheckBox cb : checkBoxes) if (cb.isSelected()) servs.add(cb.getText());

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("registrationToken", registrationToken);
            data.put("nome",              txtNome.getText());
            data.put("servicos",          String.join(", ", servs));
            data.put("endereco",          txtEndereco.getText());
            try {
                data.put("latitude",  Double.parseDouble(txtLat.getText()));
                data.put("longitude", Double.parseDouble(txtLon.getText()));
            } catch (Exception ex) {
                data.put("latitude",  0.0);
                data.put("longitude", 0.0);
            }
            data.put("telefone",          txtTel.getText());
            data.put("email",             txtEmail.getText());
            data.put("horarioFuncionamento", txtHorario.getText());
            data.put("website",           txtWebsite.getText());
            data.put("formasPagamento",   txtFormasPag.getText());
            data.put("especialidades",    txtEspecialidades.getText());

            postCompleteRegistration(data).thenAccept(oficinaId ->
                    Platform.runLater(() -> {
                        btnSave.setText("CONCLUIR CADASTRO DA OFICINA");
                        btnSave.setDisable(false);
                        if (oficinaId != null) {
                            resolvedOficinaId[0] = oficinaId;
                            dashPane.setCenter(buildStep3Pane(oficinaId));
                        } else {
                            showAlert(Alert.AlertType.ERROR, "Falha ao concluir cadastro. Token inválido ou oficina já registrada.");
                        }
                    })
            );
        });

        autoDetectLocation(txtEndereco, txtLat, txtLon);

        content.getChildren().addAll(title, stepLabel, tokenInfo, grid, btnSave);

        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background-color: transparent; -fx-background: #0B0B0B;");
        return scroll;
    }

    /**
     * Etapa 3 — Registrar usuário da oficina (POST /oficina-users/register)
     * Cria o login do dono da oficina com o ID da oficina ativa.
     */
    private ScrollPane buildStep3Pane(long oficinaId) {
        VBox content = new VBox(30);
        content.getStyleClass().add("dashboard-content");

        // ── Cabeçalho ────────────────────────────────────────────────────────
        Label title = new Label("CADASTRAR NOVA OFICINA");
        title.getStyleClass().add("content-title");

        Label stepLabel = new Label("ETAPA 3 DE 3  —  Criar Usuário da Oficina");
        stepLabel.setStyle("-fx-text-fill: #16BC4E; -fx-font-size:13px; -fx-font-weight:700;");

        Label successBanner = new Label("✅  Oficina registrada com sucesso! ID: " + oficinaId);
        successBanner.setStyle(
                "-fx-text-fill: #16BC4E; -fx-font-size:14px; -fx-font-weight:700; " +
                        "-fx-background-color: #0d2b1a; -fx-padding: 12 20 12 20; -fx-background-radius: 8;");

        Label description = new Label(
                "Agora crie o usuário de acesso para este dono de oficina.\n" +
                        "O ID da oficina (" + oficinaId + ") será vinculado automaticamente.");
        description.setStyle("-fx-text-fill: #9A9AAA; -fx-font-size:13px;");
        description.setWrapText(true);

        // ── Formulário ────────────────────────────────────────────────────────
        VBox card = formCard("CREDENCIAIS DO USUÁRIO");
        card.setMaxWidth(480);

        TextField    txtUsername = styledField("Nome de usuário (login)");
        PasswordField txtPassword = new PasswordField();
        txtPassword.setPromptText("Senha segura");
        txtPassword.getStyleClass().add("text-field-styled");

        Label idInfo = new Label("ID DA OFICINA VINCULADA: " + oficinaId);
        idInfo.setStyle("-fx-text-fill: #16BC4E; -fx-font-size:12px; -fx-font-family: monospace;");

        card.getChildren().addAll(
                fieldLabel("USUÁRIO"), txtUsername,
                fieldLabel("SENHA"),   txtPassword,
                fieldLabel(""),        idInfo
        );

        // ── Botão registrar ──────────────────────────────────────────────────
        Button btnRegister = new Button("REGISTRAR USUÁRIO");
        btnRegister.getStyleClass().add("big-button-primary");
        btnRegister.setPrefSize(260, 52);

        btnRegister.setOnAction(e -> {
            if (txtUsername.getText().isBlank() || txtPassword.getText().isBlank()) {
                showAlert(Alert.AlertType.WARNING, "Preencha usuário e senha.");
                return;
            }

            btnRegister.setText("REGISTRANDO..."); btnRegister.setDisable(true);

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("username",  txtUsername.getText());
            data.put("password",  txtPassword.getText());
            data.put("oficinaId", String.valueOf(oficinaId));

            postRegisterOficinaUser(data).thenAccept(success ->
                    Platform.runLater(() -> {
                        btnRegister.setText("REGISTRAR USUÁRIO");
                        btnRegister.setDisable(false);
                        if (success) {
                            showAlert(Alert.AlertType.INFORMATION,
                                    "Usuário '" + txtUsername.getText() + "' registrado com sucesso!\n" +
                                            "Cadastro completo da oficina (ID: " + oficinaId + ") concluído.");
                            // Volta para o dashboard principal
                            dashPane.setCenter(buildWelcomePane());
                        } else {
                            showAlert(Alert.AlertType.ERROR,
                                    "Erro ao registrar usuário. Username já existe ou oficina não encontrada.");
                        }
                    })
            );
        });

        content.getChildren().addAll(title, stepLabel, successBanner, description, card, btnRegister);

        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background-color: transparent; -fx-background: #0B0B0B;");
        return scroll;
    }

    // =========================================================================
    // Chamadas de API específicas do fluxo de cadastro
    // =========================================================================

    /**
     * POST /oficinas/initiate-registration
     * Inicia o registro da oficina e retorna o registrationToken, ou null em caso de falha.
     * O endpoint não requer corpo na versão atual; em produção enviaria dados do cartão/plano.
     */
    private java.util.concurrent.CompletableFuture<String> postInitiateRegistration() { // Renomeado
        HttpRequest.Builder rb = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "oficinas/initiate-registration")) // URI corrigida
                .header("Content-Type", "application/json");
        if (jwtToken != null) rb.header("Authorization", "Bearer " + jwtToken);
        HttpRequest request = rb.POST(HttpRequest.BodyPublishers.noBody()).build();

        return HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(res -> {
                    System.out.println("[initiate-registration] Status: " + res.statusCode() + " | " + res.body());
                    // Aceita 200 ou 201 como sucesso
                    if (res.statusCode() == 200 || res.statusCode() == 201) {
                        try {
                            Map<String, Object> body = GSON.fromJson(res.body(),
                                    new com.google.gson.reflect.TypeToken<Map<String, Object>>(){}.getType());
                            return (String) body.get("registrationToken");
                        } catch (Exception ex) {
                            System.err.println("[initiate-registration] Parse error: " + ex.getMessage());
                            return null;
                        }
                    }
                    return null;
                }).exceptionally(ex -> {
                    System.err.println("[initiate-registration] Exception: " + ex.getMessage());
                    return null;
                });
    }

    /**
     * POST /oficinas/complete-registration
     * Retorna o ID (Long) da oficina ativada, ou null em caso de falha.
     */
    private java.util.concurrent.CompletableFuture<Long> postCompleteRegistration(Map<String, Object> data) {
        String json = GSON.toJson(data);
        HttpRequest.Builder rb = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "oficinas/complete-registration"))
                .header("Content-Type", "application/json");
        if (jwtToken != null) rb.header("Authorization", "Bearer " + jwtToken);
        HttpRequest request = rb.POST(HttpRequest.BodyPublishers.ofString(json)).build();

        return HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(res -> {
                    System.out.println("[complete-registration] Status: " + res.statusCode() + " | " + res.body());
                    if (res.statusCode() == 200) {
                        try {
                            // Resposta: {"message": "Registro da oficina concluído com sucesso! ID da Oficina: 123"}
                            Map<String, Object> body = GSON.fromJson(res.body(),
                                    new com.google.gson.reflect.TypeToken<Map<String, Object>>(){}.getType());
                            String message = (String) body.get("message");
                            // Extrai o ID numérico da mensagem
                            String[] parts = message.split(":");
                            if (parts.length > 1) {
                                String idStr = parts[parts.length - 1].trim();
                                return Long.parseLong(idStr);
                            }
                        } catch (Exception ex) {
                            System.err.println("[complete-registration] Parse error: " + ex.getMessage());
                        }
                    }
                    return null;
                }).exceptionally(ex -> {
                    System.err.println("[complete-registration] Exception: " + ex.getMessage());
                    return null;
                });
    }

    /**
     * POST /oficina-users/register
     * Retorna true em caso de sucesso (200), false caso contrário.
     */
    private java.util.concurrent.CompletableFuture<Boolean> postRegisterOficinaUser(Map<String, Object> data) {
        String json = GSON.toJson(data);
        HttpRequest.Builder rb = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "oficina-users/register"))
                .header("Content-Type", "application/json");
        if (jwtToken != null) rb.header("Authorization", "Bearer " + jwtToken);
        HttpRequest request = rb.POST(HttpRequest.BodyPublishers.ofString(json)).build();

        return HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(res -> {
                    System.out.println("[oficina-users/register] Status: " + res.statusCode() + " | " + res.body());
                    return res.statusCode() == 200;
                }).exceptionally(ex -> {
                    System.err.println("[oficina-users/register] Exception: " + ex.getMessage());
                    return false;
                });
    }

    // =========================================================================
    // CONFIG / CADASTRO LEGADO (mantido para referência futura)
    // =========================================================================
    private ScrollPane buildConfigPane(boolean isNew) {
        VBox content = new VBox(30);
        content.getStyleClass().add("dashboard-content");

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

        HBox grid = new HBox(24);
        HBox.setHgrow(grid, Priority.ALWAYS);

        VBox card1 = formCard("DADOS DA EMPRESA");
        TextField txtNome  = styledField("NOME");
        TextField txtLocal = styledField("ENDEREÇO");
        TextField txtTel   = styledField("TELEFONE");
        TextField txtLat   = styledField("LATITUDE");
        TextField txtLon   = styledField("LONGITUDE");
        card1.getChildren().addAll(
                fieldLabel("NOME"), txtNome,
                fieldLabel("ENDEREÇO"), txtLocal,
                fieldLabel("TELEFONE"), txtTel,
                fieldLabel("LATITUDE"), txtLat,
                fieldLabel("LONGITUDE"), txtLon);

        VBox card3 = formCard("SERVIÇOS");
        String[] services = {"Freios", "Suspensão", "Motor", "Óleo", "Revisão", "Elétrica"};
        List<CheckBox> checkBoxes = new ArrayList<>();
        for (String s : services) {
            CheckBox cb = new CheckBox(s);
            cb.getStyleClass().add("check-box");
            checkBoxes.add(cb);
            card3.getChildren().add(cb);
        }

        grid.getChildren().addAll(card1, card3);
        for (javafx.scene.Node n : grid.getChildren())
            HBox.setHgrow(n, Priority.ALWAYS);

        content.getChildren().add(grid);

        btnSave.setOnAction(e -> {
            btnSave.setText("ENVIANDO..."); btnSave.setDisable(true);

            List<String> servs = new ArrayList<>();
            for (CheckBox cb : checkBoxes) if (cb.isSelected()) servs.add(cb.getText());

            Map<String, Object> data = new HashMap<>();
            data.put("nome",     txtNome.getText());
            data.put("endereco", txtLocal.getText());
            data.put("telefone", txtTel.getText());
            try {
                data.put("latitude",  Double.parseDouble(txtLat.getText()));
                data.put("longitude", Double.parseDouble(txtLon.getText()));
            } catch (Exception ex) { data.put("latitude", 0.0); data.put("longitude", 0.0); }
            data.put("servicos", String.join(", ", servs));

            String method   = isNew ? "POST" : "PUT";
            String endpoint = isNew ? "api/oficinas" : "api/oficinas/" + workshopId;

            sendToAPI(method, endpoint, data).thenAccept(ok ->
                    Platform.runLater(() -> {
                        btnSave.setText("SALVAR NO BANCO"); btnSave.setDisable(false);
                        showAlert(ok ? Alert.AlertType.INFORMATION : Alert.AlertType.ERROR,
                                ok ? "Sucesso! Oficina salva no banco."
                                        : "Erro 500: Verifique mecânicos/serviços.");
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

    private void showAlert(Alert.AlertType type, String message) {
        Alert alert = new Alert(type);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
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

        if ("AGENDAMENTOS".equals(name)) {
            endpoint = "api/appointments/oficina/" + workshopId;
            if (table.getRowFactory() == null) addStatusContextMenu(table);
        }

        HttpRequest.Builder getBuilder = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + endpoint))
                .header("Content-Type", "application/json");
        if (jwtToken != null) getBuilder.header("Authorization", "Bearer " + jwtToken);

        HTTP.sendAsync(getBuilder.build(), HttpResponse.BodyHandlers.ofString())
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
    // API HTTP genérica
    // =========================================================================
    private java.util.concurrent.CompletableFuture<Boolean> sendToAPI(
            String method, String endpoint, Object data) {

        String json = GSON.toJson(data);
        HttpRequest.Builder rb = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + endpoint))
                .header("Content-Type", "application/json");
        if (jwtToken != null) rb.header("Authorization", "Bearer " + jwtToken);
        if ("POST".equals(method))  rb.POST(HttpRequest.BodyPublishers.ofString(json));
        else                        rb.PUT(HttpRequest.BodyPublishers.ofString(json));
        if ("PATCH".equals(method)) rb.method("PATCH", HttpRequest.BodyPublishers.ofString(json));

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
