import com.google.gson.*;
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
import java.util.*;
import java.util.function.Consumer;
import java.util.prefs.Preferences;

public class Main {
    public static void main(String[] args) {
        Application.launch(App.class, args);
    }

    public static class App extends Application {
        private static final String BASE_URL = "https://api-java-production-5e77.up.railway.app/api/";
        private static final Gson GSON = new Gson();
        private static final HttpClient HTTP = HttpClient.newBuilder()
                .connectTimeout(java.time.Duration.ofSeconds(15)).build();
        private static final Preferences PREFS = Preferences.userNodeForPackage(Main.class);

        private String workshopId = PREFS.get("workshopId", "");
        private StackPane root;
        private StackPane dashContent;
        private VBox sidebarBox;
        private String activeTab = "";

        private final Map<String, String> tabEndpoints = new HashMap<>();
        private final Map<String, TableView<Map<String, Object>>> tabTables = new HashMap<>();

        @Override
        public void start(Stage stage) {
            loadFonts();
            root = new StackPane();
            Scene scene = new Scene(root, 1280, 800);
            
            // Tenta aplicar o CSS com log de depuração
            applyCSS(scene);

            stage.setTitle("Motor Pro | Workshop Management");
            loadIcon(stage);
            stage.setScene(scene);
            stage.setMaximized(true);
            stage.show();

            navigate(workshopId.isEmpty() ? "LAUNCH" : "DASHBOARD");
        }

        private void applyCSS(Scene scene) {
            try {
                // No padrão Maven, o caminho começa da raiz de 'resources'
                URL cssUrl = getClass().getResource("/assets/style.css");
                if (cssUrl != null) {
                    scene.getStylesheets().clear();
                    scene.getStylesheets().add(cssUrl.toExternalForm());
                    System.out.println("✅ CSS carregado de: " + cssUrl.toExternalForm());
                } else {
                    System.err.println("❌ ERRO: Arquivo '/assets/style.css' não encontrado em src/main/resources.");
                }
            } catch (Exception e) {
                System.err.println("❌ Falha crítica ao carregar CSS: " + e.getMessage());
            }
        }

        private void navigate(String screen) {
            Platform.runLater(() -> {
                root.getChildren().clear();
                Parent view = switch (screen) {
                    case "LOGIN"     -> buildLogin();
                    case "DASHBOARD" -> buildDashboard();
                    default          -> buildLaunch();
                };
                root.getChildren().add(view);
            });
        }

        private Parent buildLaunch() {
            VBox card = new VBox(30);
            card.setAlignment(Pos.CENTER);
            card.getStyleClass().add("root");
            Label logo = new Label("MOTOR PRO");
            logo.getStyleClass().add("label-logo");
            Button btnAcessar = bigBtn("ACESSAR MINHA OFICINA", "big-button-primary");
            Button btnCadastrar = bigBtn("CADASTRAR NOVA OFICINA", "big-button-secondary");
            btnAcessar.setOnAction(e -> navigate("LOGIN"));
            btnCadastrar.setOnAction(e -> alert("Cadastro indisponível no momento."));
            card.getChildren().addAll(logo, btnAcessar, btnCadastrar);
            return card;
        }

        private Parent buildLogin() {
            VBox outer = new VBox();
            outer.setAlignment(Pos.CENTER);
            outer.getStyleClass().add("root");
            VBox card = new VBox(25);
            card.setAlignment(Pos.CENTER);
            card.setMaxSize(450, 420);
            card.getStyleClass().add("form-card");
            Label title = new Label("Login");
            title.getStyleClass().add("login-title");
            TextField tel = field("Telefone da Oficina");
            Button btnEntrar = bigBtn("ENTRAR", "big-button-primary");
            Button btnVoltar = linkBtn("Voltar");
            btnEntrar.setOnAction(e -> doLogin(tel.getText().trim(), btnEntrar));
            btnVoltar.setOnAction(e -> navigate("LAUNCH"));
            card.getChildren().addAll(title, tel, btnEntrar, btnVoltar);
            outer.getChildren().add(card);
            return outer;
        }

        private void doLogin(String tel, Button btn) {
            if (tel.isEmpty()) return;
            btn.setText("BUSCANDO..."); btn.setDisable(true);
            httpGet("oficinas", body -> {
                try {
                    JsonElement el = JsonParser.parseString(body);
                    if (el.isJsonArray()) {
                        List<Map<String, Object>> list = GSON.fromJson(el, new TypeToken<List<Map<String, Object>>>(){}.getType());
                        String found = null;
                        for (Map<String, Object> item : list) {
                            if (tel.equals(String.valueOf(item.get("telefone")))) found = String.valueOf(item.get("id"));
                        }
                        if (found != null) {
                            workshopId = found;
                            PREFS.put("workshopId", found);
                            navigate("DASHBOARD");
                        } else {
                            alert("Oficina não encontrada.");
                            Platform.runLater(() -> { btn.setText("ENTRAR"); btn.setDisable(false); });
                        }
                    }
                } catch (Exception e) {
                    Platform.runLater(() -> { btn.setText("ENTRAR"); btn.setDisable(false); });
                }
            }, () -> Platform.runLater(() -> { btn.setText("ENTRAR"); btn.setDisable(false); }));
        }

        private Parent buildDashboard() {
            tabEndpoints.clear(); tabTables.clear();
            BorderPane dash = new BorderPane();
            dash.getStyleClass().add("root");
            dash.setLeft(buildSidebar());
            dashContent = new StackPane();
            dashContent.getStyleClass().add("dashboard-content");
            dash.setCenter(dashContent);
            startRefreshTimer();
            Platform.runLater(this::fireFirstTab);
            return dash;
        }

        private VBox buildSidebar() {
            VBox sidebar = new VBox();
            sidebar.setPrefWidth(320);
            sidebar.getStyleClass().add("sidebar");
            sidebarBox = new VBox(12);
            sidebarBox.setPadding(new Insets(30, 15, 0, 15));

            registerTab("📅", "AGENDAMENTOS", "appointments/oficina/" + workshopId,
                    new String[]{"Cliente", "Serviço", "Data/Hora", "Status"},
                    new String[]{"userName", "servico", "dataHora", "status"});
            registerTab("👥", "CLIENTES", "users", new String[]{"Nome", "Email"}, new String[]{"nome", "email"});
            registerTab("🔍", "OFICINAS", "oficinas", new String[]{"Nome", "Endereço", "Telefone"}, new String[]{"nome", "endereco", "telefone"});

            Region spacer = new Region();
            VBox.setVgrow(spacer, Priority.ALWAYS);
            Button sair = menuBtn("🚪", "SAIR");
            sair.setOnAction(e -> { workshopId = ""; PREFS.put("workshopId", ""); navigate("LAUNCH"); });
            sidebarBox.getChildren().addAll(spacer, sair);
            sidebar.getChildren().add(sidebarBox);
            return sidebar;
        }

        private void registerTab(String icon, String name, String endpoint, String[] cols, String[] keys) {
            TableView<Map<String, Object>> t = new TableView<>();
            t.getStyleClass().add("table-view");
            t.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
            for (int i = 0; i < cols.length; i++) {
                final String key = keys[i];
                TableColumn<Map<String, Object>, String> col = new TableColumn<>(cols[i]);
                col.setCellValueFactory(d -> new SimpleStringProperty(Objects.toString(d.getValue().get(key), "-")));
                t.getColumns().add(col);
            }
            if (name.equals("AGENDAMENTOS")) addAppointmentContext(t);
            if (name.equals("CLIENTES")) addClientClick(t);

            tabEndpoints.put(name, endpoint);
            tabTables.put(name, t);

            Button btn = menuBtn(icon, name);
            btn.setOnAction(e -> {
                activeTab = name;
                dashContent.getChildren().setAll(contentPanel(name, t));
                highlightBtn(btn);
                loadData(name);
            });
            sidebarBox.getChildren().add(btn);
        }

        private void addAppointmentContext(TableView<Map<String, Object>> t) {
            t.setRowFactory(tv -> {
                TableRow<Map<String, Object>> row = new TableRow<>();
                MenuItem c1 = new MenuItem("Confirmar");
                MenuItem c2 = new MenuItem("Concluir");
                MenuItem c3 = new MenuItem("Cancelar");
                c1.setOnAction(e -> updateStatus(row.getItem(), "CONFIRMADO"));
                c2.setOnAction(e -> updateStatus(row.getItem(), "CONCLUIDO"));
                c3.setOnAction(e -> updateStatus(row.getItem(), "CANCELADO"));
                ContextMenu cm = new ContextMenu(c1, c2, c3);
                row.contextMenuProperty().bind(javafx.beans.binding.Bindings.when(row.emptyProperty()).then((ContextMenu) null).otherwise(cm));
                return row;
            });
        }

        private void updateStatus(Map<String, Object> item, String s) {
            if (item == null) return;
            httpPatch("appointments/" + item.get("id") + "/status", GSON.toJson(Map.of("status", s)), () -> loadData("AGENDAMENTOS"));
        }

        private void addClientClick(TableView<Map<String, Object>> t) {
            t.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2 && t.getSelectionModel().getSelectedItem() != null) {
                    Map<String, Object> u = t.getSelectionModel().getSelectedItem();
                    httpGet("vehicles/" + u.get("id"), body -> {
                        List<Map<String, Object>> list = GSON.fromJson(body, new TypeToken<List<Map<String, Object>>>(){}.getType());
                        StringBuilder msg = new StringBuilder();
                        if (list.isEmpty()) msg.append("Nenhum veículo cadastrado.");
                        else for (Map<String, Object> v : list) msg.append("• ").append(v.get("marca")).append(" - ").append(v.get("placa")).append("\n");
                        alert(msg.toString());
                    }, null);
                }
            });
        }

        private void startRefreshTimer() {
            Timeline t = new Timeline(new KeyFrame(Duration.seconds(10), e -> loadData(activeTab)));
            t.setCycleCount(Timeline.INDEFINITE);
            t.play();
        }

        private void loadData(String tab) {
            String endpoint = tabEndpoints.get(tab);
            TableView<Map<String, Object>> table = tabTables.get(tab);
            if (endpoint == null || table == null) return;
            httpGet(endpoint, body -> {
                try {
                    JsonElement el = JsonParser.parseString(body);
                    if (el.isJsonArray()) {
                        List<Map<String, Object>> items = GSON.fromJson(el, new TypeToken<List<Map<String, Object>>>(){}.getType());
                        Platform.runLater(() -> table.getItems().setAll(items));
                    }
                } catch (Exception ignored) {}
            }, null);
        }

        private void fireFirstTab() {
            for (javafx.scene.Node n : sidebarBox.getChildren()) {
                if (n instanceof Button b && !b.getText().contains("SAIR")) { b.fire(); return; }
            }
        }

        private void httpGet(String e, Consumer<String> ok, Runnable fail) {
            HTTP.sendAsync(HttpRequest.newBuilder().uri(URI.create(BASE_URL + e)).build(), HttpResponse.BodyHandlers.ofString())
                .thenAccept(r -> { if (r.statusCode() == 200) ok.accept(r.body()); else if (fail != null) fail.run(); });
        }

        private void httpPatch(String e, String j, Runnable ok) {
            HTTP.sendAsync(HttpRequest.newBuilder().uri(URI.create(BASE_URL + e)).method("PATCH", HttpRequest.BodyPublishers.ofString(j)).header("Content-Type", "application/json").build(), HttpResponse.BodyHandlers.ofString())
                .thenAccept(r -> { if (r.statusCode() == 200 && ok != null) ok.run(); });
        }

        private Parent contentPanel(String title, TableView<Map<String, Object>> table) {
            VBox box = new VBox(20); box.getStyleClass().add("dashboard-content");
            Label lbl = new Label(title); lbl.getStyleClass().add("content-title");
            VBox.setVgrow(table, Priority.ALWAYS);
            box.getChildren().addAll(lbl, table);
            return box;
        }

        private Button bigBtn(String t, String s) { Button b = new Button(t); b.getStyleClass().addAll("big-button", s); return b; }
        private Button menuBtn(String i, String l) { Button b = new Button(i + "   " + l); b.getStyleClass().add("sidebar-button"); b.setMaxWidth(Double.MAX_VALUE); b.setAlignment(Pos.CENTER_LEFT); return b; }
        private Button linkBtn(String t) { Button b = new Button(t); b.getStyleClass().add("link-button"); return b; }
        private TextField field(String p) { TextField f = new TextField(); f.setPromptText(p); f.getStyleClass().add("text-field-styled"); f.setPrefHeight(60); return f; }
        private void highlightBtn(Button s) { sidebarBox.getChildren().forEach(n -> { if (n instanceof Button b) b.getStyleClass().remove("sidebar-button-selected"); }); s.getStyleClass().add("sidebar-button-selected"); }
        private void alert(String m) { Platform.runLater(() -> { Alert a = new Alert(Alert.AlertType.INFORMATION); a.setHeaderText(null); a.setContentText(m); a.showAndWait(); }); }
        
        private void loadIcon(Stage s) { 
            try { 
                InputStream i = getClass().getResourceAsStream("/assets/perfil.png"); 
                if (i != null) s.getIcons().add(new Image(i)); 
            } catch (Exception ignored) {} 
        }
        
        private void loadFonts() { 
            try { 
                Font.loadFont(getClass().getResourceAsStream("/assets/Ardela.ttf"), 12); 
                Font.loadFont(getClass().getResourceAsStream("/assets/Sora-Regular.ttf"), 12); 
            } catch (Exception ignored) {} 
        }
    }
}