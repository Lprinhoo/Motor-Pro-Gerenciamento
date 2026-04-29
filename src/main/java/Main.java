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
import java.util.prefs.Preferences;

public class Main extends Application {

    private static final String BASE_URL = "https://api-java-production-5e77.up.railway.app/";
    private static final HttpClient HTTP  = HttpClient.newBuilder()
            .connectTimeout(java.time.Duration.ofSeconds(15)).build();
    private static final Gson GSON = new Gson();

    private static final String PREF_WORKSHOP_ID   = "workshopId";
    private static final String PREF_WORKSHOP_NOME = "workshopNome";
    private static final Preferences PREFS = Preferences.userNodeForPackage(Main.class);

    private String workshopId;
    private String workshopNome;
    private String currentTab      = "VEÍCULOS";
    private final Map<String, TableView<Map<String, Object>>> tabTables    = new HashMap<>();
    private final Map<String, String>   tabEndpoints = new HashMap<>();
    private final Map<String, String[]> tabKeys      = new HashMap<>();

    private ScheduledExecutorService scheduler;
    private StackPane root;
    private BorderPane dashPane;

    @Override
    public void start(Stage stage) {
        root = new StackPane();
        Scene scene = new Scene(root, 1440, 900);
        try {
            scene.getStylesheets().add(getClass().getResource("/assets/style.css").toExternalForm());
        } catch (Exception ignored) {}

        stage.setTitle("Motor Pro | Management System");
        stage.setMaximized(true);
        try {
            stage.getIcons().add(new Image(getClass().getResourceAsStream("/assets/perfil.png")));
        } catch (Exception ignored) {}

        stage.setScene(scene);
        stage.show();

        workshopId   = PREFS.get(PREF_WORKSHOP_ID,   null);
        workshopNome = PREFS.get(PREF_WORKSHOP_NOME,  "Minha Oficina");

        if (workshopId != null && !workshopId.isBlank()) {
            showDashboard();
        } else {
            showLaunchScreen();
        }
    }

    private void showLaunchScreen() {
        VBox card = new VBox(32);
        card.setAlignment(Pos.CENTER);
        Label logo = new Label("MOTOR PRO");
        logo.getStyleClass().add("content-title");
        Label sub = new Label("Bem-vindo! Cadastre sua oficina para começar.");
        sub.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 16px;");
        Button btnCadastrar = new Button("CADASTRAR MINHA OFICINA");
        btnCadastrar.getStyleClass().add("big-button-primary");
        btnCadastrar.setPrefSize(360, 64);
        btnCadastrar.setOnAction(e -> showFirstAccessRegistration());
        card.getChildren().addAll(logo, sub, btnCadastrar);
        StackPane launch = new StackPane(card);
        launch.getStyleClass().add("master-panel");
        root.getChildren().setAll(launch);
    }

    private void showFirstAccessRegistration() {
        VBox formWrapper = new VBox(20);
        formWrapper.setAlignment(Pos.CENTER);
        formWrapper.setMaxWidth(620);
        formWrapper.setPadding(new Insets(60, 40, 60, 40));

        Label title = new Label("CADASTRO DA OFICINA");
        title.getStyleClass().add("content-title");
        Label subtitle = new Label("Preencha os dados abaixo. Use um nome e e-mail únicos.");
        subtitle.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 14px;");

        VBox card = new VBox(12);
        card.getStyleClass().add("form-card");

        TextField txtNome  = styledField("Nome da oficina");
        TextField txtLocal = styledField("Endereço");
        TextField txtTel   = styledField("Telefone");
        TextField txtEmail = styledField("E-mail");

        TextField txtLat = new TextField("0.0");
        TextField txtLon = new TextField("0.0");
        txtLat.setVisible(false); txtLat.setManaged(false);
        txtLon.setVisible(false); txtLon.setManaged(false);

        Label lblServicos = new Label("SERVIÇOS OFERECIDOS");
        lblServicos.setStyle("-fx-text-fill: #16BC4E; -fx-font-weight: 700; -fx-font-size: 12px;");

        String[] services = {"Freios", "Suspensão", "Motor", "Óleo", "Revisão", "Elétrica"};
        List<CheckBox> checkBoxes = new ArrayList<>();
        HBox cbRow1 = new HBox(16);
        HBox cbRow2 = new HBox(16);
        for (int i = 0; i < services.length; i++) {
            CheckBox cb = new CheckBox(services[i]);
            cb.getStyleClass().add("check-box");
            checkBoxes.add(cb);
            (i < 3 ? cbRow1 : cbRow2).getChildren().add(cb);
        }

        Label msgErro = new Label();
        msgErro.setStyle("-fx-text-fill: #f87171; -fx-font-size: 13px;");

        Button btnSalvar = new Button("CADASTRAR E ENTRAR");
        btnSalvar.getStyleClass().add("big-button-primary");
        btnSalvar.setMaxWidth(Double.MAX_VALUE);
        btnSalvar.setPrefHeight(52);

        card.getChildren().addAll(
                fieldLabel("NOME DA OFICINA"), txtNome,
                fieldLabel("ENDEREÇO"),        txtLocal,
                fieldLabel("TELEFONE"),        txtTel,
                fieldLabel("E-MAIL"),          txtEmail,
                lblServicos, cbRow1, cbRow2,
                msgErro, btnSalvar
        );

        btnSalvar.setOnAction(e -> {
            if (txtNome.getText().isBlank() || txtLocal.getText().isBlank()) {
                msgErro.setText("⚠ Preencha nome e endereço.");
                return;
            }
            msgErro.setText("");
            btnSalvar.setText("ENVIANDO..."); btnSalvar.setDisable(true);

            List<String> selected = new ArrayList<>();
            for (CheckBox cb : checkBoxes) if (cb.isSelected()) selected.add(cb.getText());

            Map<String, Object> data = new HashMap<>();
            data.put("nome",     txtNome.getText().trim());
            data.put("endereco", txtLocal.getText().trim());
            data.put("telefone", txtTel.getText().trim());
            data.put("email",    txtEmail.getText().trim());
            data.put("latitude", txtLat.getText());   // Envia como String
            data.put("longitude", txtLon.getText());  // Envia como String
            data.put("servicos", selected);           // Envia como Lista (para evitar 400)

            sendToAPI("POST", "api/oficinas", data).thenAccept(ok -> Platform.runLater(() -> {
                if (ok) {
                    PREFS.put(PREF_WORKSHOP_ID, workshopId);
                    PREFS.put(PREF_WORKSHOP_NOME, txtNome.getText());
                    workshopNome = txtNome.getText();
                    showDashboard();
                } else {
                    btnSalvar.setText("CADASTRAR E ENTRAR");
                    btnSalvar.setDisable(false);
                    msgErro.setText("⚠ Erro 500. Tente um nome/email diferente.");
                }
            }));
        });

        formWrapper.getChildren().addAll(title, subtitle, card);
        ScrollPane scroll = new ScrollPane(formWrapper);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background-color: transparent; -fx-background: #0f172a;");
        root.getChildren().setAll(new StackPane(scroll));
        autoDetectLocation(txtLocal, txtLat, txtLon);
    }

    private void showDashboard() {
        dashPane = new BorderPane();
        dashPane.getStyleClass().add("master-panel");
        dashPane.setLeft(buildSidebar());
        dashPane.setCenter(buildWelcomePane());
        root.getChildren().setAll(dashPane);
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r); t.setDaemon(true); return t;
        });
        scheduler.scheduleAtFixedRate(() -> Platform.runLater(this::autoRefresh), 5, 5, TimeUnit.SECONDS);
    }

    private VBox buildSidebar() {
        VBox sidebar = new VBox(8);
        sidebar.getStyleClass().add("sidebar");
        sidebar.setPrefWidth(280);
        Label brand = new Label("MOTOR PRO");
        brand.setStyle("-fx-font-size:22px; -fx-font-weight:700; -fx-text-fill: #16BC4E;");
        brand.setPadding(new Insets(0, 0, 16, 0));

        Button btnOficinaInfo   = sidebarBtn("🏪", "MINHA OFICINA");
        Button btnClientes      = sidebarBtn("👥", "CLIENTES");
        Button btnAgendamentos  = sidebarBtn("📅", "AGENDAMENTOS");
        Button btnEditar        = sidebarBtn("⚙️", "EDITAR OFICINA");

        btnOficinaInfo.setOnAction(e -> {
            currentTab = "MINHA OFICINA"; loadAndShowOficinaInfo();
            clearSelected(sidebar); btnOficinaInfo.getStyleClass().add("sidebar-button-selected");
        });
        registerTab("CLIENTES", "api/users", new String[]{"Nome", "Email"}, new String[]{"nome", "email"}, btnClientes);
        registerTab("AGENDAMENTOS", "api/appointments/oficina/" + workshopId, new String[]{"Cliente", "Serviço"}, new String[]{"userId", "servico"}, btnAgendamentos);
        btnEditar.setOnAction(e -> {
            currentTab = "EDITAR OFICINA"; dashPane.setCenter(buildEditOficinaPane());
            clearSelected(sidebar); btnEditar.getStyleClass().add("sidebar-button-selected");
        });

        sidebar.getChildren().addAll(brand, btnOficinaInfo, btnClientes, btnAgendamentos, new Separator(), btnEditar);
        Platform.runLater(btnOficinaInfo::fire);
        return sidebar;
    }

    private Button sidebarBtn(String icon, String label) {
        Button btn = new Button(icon + "  " + label);
        btn.getStyleClass().add("sidebar-button");
        btn.setMaxWidth(Double.MAX_VALUE);
        return btn;
    }

    private void registerTab(String name, String endpoint, String[] cols, String[] keys, Button btn) {
        tabEndpoints.put(name, endpoint); tabKeys.put(name, keys);
        TableView<Map<String, Object>> table = buildTable(cols, keys);
        tabTables.put(name, table);
        btn.setOnAction(e -> {
            currentTab = name; dashPane.setCenter(new VBox(new Label(name), table)); refreshTab(name);
            clearSelected(btn.getParent()); btn.getStyleClass().add("sidebar-button-selected");
        });
    }

    private void clearSelected(javafx.scene.Parent parent) {
        parent.getChildrenUnmodifiable().forEach(n -> { if (n instanceof Button b) b.getStyleClass().remove("sidebar-button-selected"); });
    }

    private void loadAndShowOficinaInfo() {
        if (workshopId == null) return;
        HTTP.sendAsync(HttpRequest.newBuilder().uri(URI.create(BASE_URL + "api/oficinas/" + workshopId)).build(), HttpResponse.BodyHandlers.ofString())
                .thenAccept(res -> Platform.runLater(() -> {
                    if (res.statusCode() == 200) {
                        Map<String, Object> info = GSON.fromJson(res.body(), new com.google.gson.reflect.TypeToken<Map<String, Object>>(){}.getType());
                        dashPane.setCenter(buildOficinaInfoPane(info));
                    }
                }));
    }

    private ScrollPane buildOficinaInfoPane(Map<String, Object> info) {
        VBox card = new VBox(15); card.setPadding(new Insets(20));
        card.getChildren().addAll(new Label("NOME: " + strOf(info, "nome")), new Label("ENDEREÇO: " + strOf(info, "endereco")),
                new Label("TEL: " + strOf(info, "telefone")), new Label("EMAIL: " + strOf(info, "email")), new Label("SERVIÇOS: " + strOf(info, "servicos")));
        return new ScrollPane(card);
    }

    private String strOf(Map<String, Object> map, String key) {
        Object v = map.get(key);
        if (v == null) return "—";
        if (v instanceof List<?> l) return String.join(", ", l.stream().map(Object::toString).toList());
        return v.toString();
    }

    private ScrollPane buildEditOficinaPane() {
        VBox content = new VBox(20); content.setPadding(new Insets(20));
        TextField txtNome = new TextField(workshopNome);
        Button btnSave = new Button("SALVAR");
        btnSave.setOnAction(e -> {
            Map<String, Object> data = new HashMap<>();
            data.put("nome", txtNome.getText());
            sendToAPI("PUT", "api/oficinas/" + workshopId, data).thenAccept(ok -> Platform.runLater(() -> {
                if (ok) new Alert(Alert.AlertType.INFORMATION, "Sucesso!").show();
            }));
        });
        content.getChildren().addAll(new Label("Nome:"), txtNome, btnSave);
        return new ScrollPane(content);
    }

    private TableView<Map<String, Object>> buildTable(String[] cols, String[] keys) {
        TableView<Map<String, Object>> table = new TableView<>();
        for (int i = 0; i < cols.length; i++) {
            final String key = keys[i];
            TableColumn<Map<String, Object>, Object> col = new TableColumn<>(cols[i]);
            col.setCellValueFactory(d -> new SimpleObjectProperty<>(d.getValue().get(key)));
            table.getColumns().add(col);
        }
        return table;
    }

    private StackPane buildWelcomePane() { return new StackPane(new Label("Selecione uma opção")); }

    private TextField styledField(String prompt) {
        TextField tf = new TextField(); tf.setPromptText(prompt); tf.getStyleClass().add("text-field-styled"); return tf;
    }

    private Label fieldLabel(String text) { Label l = new Label(text); l.setStyle("-fx-text-fill: #9A9AAA; -fx-font-size: 10px;"); return l; }

    private void autoRefresh() { if (tabEndpoints.containsKey(currentTab)) refreshTab(currentTab); }

    private void refreshTab(String name) {
        String ep = tabEndpoints.get(name);
        if (ep == null) return;
        HTTP.sendAsync(HttpRequest.newBuilder().uri(URI.create(BASE_URL + ep)).build(), HttpResponse.BodyHandlers.ofString())
                .thenAccept(res -> {
                    List<Map<String, Object>> rows = GSON.fromJson(res.body(), new com.google.gson.reflect.TypeToken<List<Map<String, Object>>>(){}.getType());
                    if (rows != null) Platform.runLater(() -> tabTables.get(name).setItems(FXCollections.observableArrayList(rows)));
                });
    }

    private java.util.concurrent.CompletableFuture<Boolean> sendToAPI(String method, String endpoint, Object data) {
        String json = GSON.toJson(data);
        System.out.println("DEBUG: " + method + " " + endpoint + " | Payload: " + json);
        HttpRequest.Builder rb = HttpRequest.newBuilder().uri(URI.create(BASE_URL + endpoint)).header("Content-Type", "application/json");
        if (method.equals("POST")) rb.POST(HttpRequest.BodyPublishers.ofString(json));
        else if (method.equals("PUT")) rb.PUT(HttpRequest.BodyPublishers.ofString(json));

        return HTTP.sendAsync(rb.build(), HttpResponse.BodyHandlers.ofString()).thenApply(res -> {
            System.out.println("DEBUG: Status " + res.statusCode() + " | Body: " + res.body());
            if (res.statusCode() >= 200 && res.statusCode() < 300) {
                try {
                    Map<String, Object> r = GSON.fromJson(res.body(), new com.google.gson.reflect.TypeToken<Map<String, Object>>(){}.getType());
                    if (r != null && r.get("id") != null) {
                        workshopId = String.valueOf(r.get("id")); // Extração de ID mais segura
                        if (workshopId.endsWith(".0")) workshopId = workshopId.substring(0, workshopId.length()-2);
                    }
                } catch (Exception ignored) {}
                return true;
            }
            return false;
        }).exceptionally(ex -> { ex.printStackTrace(); return false; });
    }

    private void autoDetectLocation(TextField loc, TextField lat, TextField lon) {
        HTTP.sendAsync(HttpRequest.newBuilder().uri(URI.create("http://ip-api.com/json/")).build(), HttpResponse.BodyHandlers.ofString())
                .thenAccept(res -> {
                    Map<String, Object> d = GSON.fromJson(res.body(), new com.google.gson.reflect.TypeToken<Map<String, Object>>(){}.getType());
                    if (d != null && "success".equals(d.get("status"))) Platform.runLater(() -> {
                        lat.setText(String.valueOf(d.get("lat"))); lon.setText(String.valueOf(d.get("lon")));
                        if (loc.getText().isBlank()) loc.setText(d.get("city") + ", " + d.get("regionName"));
                    });
                });
    }

    @Override
    public void stop() { if (scheduler != null) scheduler.shutdownNow(); }
    public static void main(String[] args) { launch(args); }
}
