import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import com.google.gson.Gson;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class Main extends Application {

    private static final String BASE_URL = "https://api-java-production-5e77.up.railway.app/";
    private static final HttpClient HTTP  = HttpClient.newBuilder()
            .connectTimeout(java.time.Duration.ofSeconds(15)).build();
    private static final Gson GSON = new Gson();

    private String workshopId;
    private StackPane root;

    // Variáveis para armazenar a localização detectada automaticamente via IP
    private double detectedLat = 0.0;
    private double detectedLon = 0.0;

    @Override
    public void start(Stage primaryStage) {
        root = new StackPane();
        Scene scene = new Scene(root, 1440, 900);
        try {
            scene.getStylesheets().add(getClass().getResource("/assets/style.css").toExternalForm());
        } catch (Exception ignored) {}

        primaryStage.setTitle("Motor Pro - Gerenciamento");
        primaryStage.setMaximized(true);
        primaryStage.setScene(scene);
        primaryStage.show();

        // Inicia detecção de localização por IP assim que a aplicação abre
        autoDetectLocation();
        
        showRegistrationScreen();
    }

    private void showRegistrationScreen() {
        VBox formWrapper = new VBox(20);
        formWrapper.setAlignment(Pos.CENTER);
        formWrapper.setMaxWidth(620);
        formWrapper.setPadding(new Insets(60, 40, 60, 40));

        Label title = new Label("CADASTRO DA OFICINA");
        title.getStyleClass().add("content-title");

        VBox card = new VBox(12);
        card.getStyleClass().add("form-card");

        // Campos do formulário (sem Latitude/Longitude conforme solicitado)
        TextField txtNome           = styledField("Nome da oficina");
        TextField txtServicos       = styledField("Serviços (ex: Freios, Óleo, Revisão)");
        TextField txtEndereco       = styledField("Endereço Completo");
        TextField txtTelefone       = styledField("Telefone de Contato");
        TextField txtEmail          = styledField("E-mail Comercial");
        TextField txtHorario        = styledField("Horário de Funcionamento (ex: Seg-Sex 08h-18h)");
        TextField txtWebsite        = styledField("Website (opcional)");
        TextField txtPagamento      = styledField("Formas de Pagamento");
        TextField txtEspecialidades = styledField("Especialidades (ex: Nacionais, Importados)");

        Label msgErro = new Label();
        msgErro.setStyle("-fx-text-fill: #f87171; -fx-font-size: 13px;");

        Button btnCadastrar = new Button("CADASTRAR OFICINA");
        btnCadastrar.getStyleClass().add("big-button-primary");
        btnCadastrar.setMaxWidth(Double.MAX_VALUE);
        btnCadastrar.setPrefHeight(52);

        card.getChildren().addAll(
                fieldLabel("NOME DA OFICINA"), txtNome,
                fieldLabel("SERVIÇOS OFERECIDOS"), txtServicos,
                fieldLabel("ENDEREÇO COMPLETO"), txtEndereco,
                fieldLabel("TELEFONE"), txtTelefone,
                fieldLabel("E-MAIL"), txtEmail,
                fieldLabel("HORÁRIO DE FUNCIONAMENTO"), txtHorario,
                fieldLabel("FORMAS DE PAGAMENTO"), txtPagamento,
                msgErro, btnCadastrar
        );

        btnCadastrar.setOnAction(e -> {
            if (txtNome.getText().isBlank() || txtEndereco.getText().isBlank() || txtEmail.getText().isBlank()) {
                msgErro.setText("⚠ Preencha os campos obrigatórios (Nome, Endereço e E-mail).");
                return;
            }
            
            btnCadastrar.setDisable(true);
            btnCadastrar.setText("CADASTRANDO...");

            // Monta o JSON conforme o modelo Oficina.java
            Map<String, Object> data = new HashMap<>();
            data.put("nome", txtNome.getText().trim());
            data.put("servicos", txtServicos.getText().trim());
            data.put("endereco", txtEndereco.getText().trim());
            data.put("latitude", detectedLat); 
            data.put("longitude", detectedLon);
            data.put("telefone", txtTelefone.getText().trim());
            data.put("email", txtEmail.getText().trim());
            data.put("horarioFuncionamento", txtHorario.getText().trim());
            data.put("website", txtWebsite.getText().trim());
            data.put("formasPagamento", txtPagamento.getText().trim());
            data.put("especialidades", txtEspecialidades.getText().trim());

            sendToAPI("POST", "oficinas", data).thenAccept(ok -> Platform.runLater(() -> {
                if (ok) {
                    Alert alert = new Alert(Alert.AlertType.INFORMATION, "Oficina cadastrada com sucesso!\nID: " + workshopId);
                    alert.showAndWait();
                    // Futuro: Redirecionar para o Dashboard
                } else {
                    btnCadastrar.setDisable(false);
                    btnCadastrar.setText("CADASTRAR OFICINA");
                    msgErro.setText("⚠ Erro ao cadastrar na API. Verifique a conexão ou os dados.");
                }
            }));
        });

        formWrapper.getChildren().addAll(title, card);
        ScrollPane scroll = new ScrollPane(formWrapper);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background-color: transparent; -fx-background: #0B0B0B;");
        root.getChildren().setAll(scroll);
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

    private CompletableFuture<Boolean> sendToAPI(String method, String path, Object data) {
        String json = GSON.toJson(data);
        HttpRequest.Builder rb = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + path))
                .header("Content-Type", "application/json");

        if (method.equals("POST")) rb.POST(HttpRequest.BodyPublishers.ofString(json));

        return HTTP.sendAsync(rb.build(), HttpResponse.BodyHandlers.ofString())
                .thenApply(res -> {
                    if (res.statusCode() >= 200 && res.statusCode() < 300) {
                        try {
                            Map<String, Object> body = GSON.fromJson(res.body(), Map.class);
                            if (body.containsKey("id")) {
                                workshopId = String.valueOf(body.get("id"));
                                if (workshopId.endsWith(".0")) workshopId = workshopId.substring(0, workshopId.length()-2);
                            }
                        } catch (Exception ignored) {}
                        return true;
                    }
                    return false;
                }).exceptionally(ex -> false);
    }

    private void autoDetectLocation() {
        HTTP.sendAsync(HttpRequest.newBuilder().uri(URI.create("http://ip-api.com/json/")).build(),
                HttpResponse.BodyHandlers.ofString())
                .thenAccept(res -> {
                    try {
                        Map<String, Object> d = GSON.fromJson(res.body(), Map.class);
                        if (d != null && "success".equals(d.get("status"))) {
                            detectedLat = (double) d.get("lat");
                            detectedLon = (double) d.get("lon");
                        }
                    } catch (Exception ignored) {}
                }).exceptionally(ex -> null);
    }

    public static void main(String[] args) { launch(args); }
}
