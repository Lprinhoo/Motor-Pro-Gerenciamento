import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;
import java.util.List;

public class Main extends JFrame {
    private static final String BASE_URL = "https://teste-production-a485.up.railway.app/";
    private static final String ADMIN_PASSWORD = "motopro2024";
    private static final HttpClient client = HttpClient.newBuilder().connectTimeout(java.time.Duration.ofSeconds(15)).build();
    private static final Gson gson = new Gson();

    private static final Color PRIMARY_GREEN = Color.decode("#16BC4E");
    private static final Color SOLID_BLACK = Color.decode("#0B0B0B");
    private static final Color ICE_WHITE = Color.decode("#F5F6FC");
    private static final Color PURE_WHITE = Color.decode("#FFFFFF");
    private static final Color TEXT_GRAY = Color.decode("#888888");
    private static final Color SIDEBAR_SELECTED = Color.decode("#1A1A1A");

    private Font ardelaFont, soraFont;
    private CardLayout masterLayout;
    private JPanel masterPanel;
    private JPanel dashboardContent;
    private CardLayout dashboardLayout;
    private JPanel sidebarButtons;
    private JLabel lblStatus;

    private String workshopId = ""; 
    private String currentActiveTab = "VEÍCULOS";
    private Map<String, RefreshParams> tabRefreshMap = new HashMap<>();
    private javax.swing.Timer autoRefreshTimer;

    private static class RefreshParams {
        String endpoint; DefaultTableModel model; String[] jsonKeys;
        RefreshParams(String e, DefaultTableModel m, String[] k) { this.endpoint = e; this.model = m; this.jsonKeys = k; }
    }

    public Main() {
        loadFonts();
        setupWindow();
        masterLayout = new CardLayout();
        masterPanel = new JPanel(masterLayout);
        masterPanel.add(createLaunchScreen(), "LOGIN");
        add(masterPanel);
        masterLayout.show(masterPanel, "LOGIN");
        setVisible(true);
    }

    private void setupWindow() {
        setTitle("Motor Pro | Management System");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setExtendedState(JFrame.MAXIMIZED_BOTH);
        try {
            InputStream is = getClass().getResourceAsStream("/assets/perfil.png");
            if (is != null) setIconImage(ImageIO.read(is));
        } catch (Exception ignored) {}
    }

    private JPanel createLaunchScreen() {
        JPanel container = new JPanel(new GridBagLayout());
        container.setBackground(SOLID_BLACK);
        JPanel card = new JPanel(); card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS)); card.setBackground(SOLID_BLACK);
        JLabel logo = new JLabel("MOTOR PRO"); logo.setForeground(PURE_WHITE); logo.setFont(ardelaFont.deriveFont(64f)); logo.setAlignmentX(0.5f);
        JButton btn = new JButton("ENTRAR NO DASHBOARD");
        btn.setBackground(PRIMARY_GREEN); btn.setForeground(SOLID_BLACK); btn.setFont(soraFont.deriveFont(Font.BOLD, 20f));
        btn.setMaximumSize(new Dimension(500, 80)); btn.setAlignmentX(0.5f); btn.setBorder(null); btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btn.addActionListener(e -> showDashboard());
        card.add(logo); card.add(Box.createRigidArea(new Dimension(0, 60))); card.add(btn);
        container.add(card);
        return container;
    }

    private JPanel createConfigScreen(boolean isNew) {
        JPanel main = new JPanel(new BorderLayout(0, 40));
        main.setBackground(ICE_WHITE);
        main.setBorder(new EmptyBorder(40, 60, 40, 60));

        JPanel header = new JPanel(new BorderLayout()); header.setBackground(ICE_WHITE);
        JLabel lblT = new JLabel(isNew ? "NOVO CADASTRO" : "CONFIGURAÇÃO"); lblT.setFont(ardelaFont.deriveFont(52f)); lblT.setForeground(SOLID_BLACK);
        JButton btnSave = new JButton("SALVAR DADOS"); btnSave.setBackground(PRIMARY_GREEN); btnSave.setForeground(SOLID_BLACK); btnSave.setFont(soraFont.deriveFont(Font.BOLD, 12f)); btnSave.setPreferredSize(new Dimension(200, 60));
        header.add(lblT, BorderLayout.WEST); header.add(btnSave, BorderLayout.EAST);
        main.add(header, BorderLayout.NORTH);

        JPanel grid = new JPanel(new GridLayout(1, 3, 25, 0)); grid.setBackground(ICE_WHITE);

        JPanel c1 = createFormCard("DADOS DA EMPRESA");
        JTextField txtID = createStyledField("ID DA OFICINA (Vazio para novo)", PURE_WHITE, SOLID_BLACK);
        txtID.setText(workshopId);
        JTextField txtN = createStyledField("NOME DA OFICINA", PURE_WHITE, SOLID_BLACK);
        JTextField txtL = createStyledField("LOCALIDADE / ENDEREÇO", PURE_WHITE, SOLID_BLACK);
        JTextField txtLat = createStyledField("LATITUDE", PURE_WHITE, SOLID_BLACK);
        JTextField txtLon = createStyledField("LONGITUDE", PURE_WHITE, SOLID_BLACK);
        c1.add(txtID); c1.add(Box.createRigidArea(new Dimension(0, 10)));
        c1.add(txtN); c1.add(Box.createRigidArea(new Dimension(0, 10)));
        c1.add(txtL); c1.add(Box.createRigidArea(new Dimension(0, 10)));
        c1.add(txtLat); c1.add(Box.createRigidArea(new Dimension(0, 10)));
        c1.add(txtLon);

        DefaultListModel<String> mM = new DefaultListModel<>(); JPanel col2 = createListPanel("EQUIPE (MECÂNICOS)", mM);

        JPanel c3 = createFormCard("SERVIÇOS");
        String[] std = {"Freios", "Suspensão", "Motor", "Óleo", "Revisão"};
        List<JCheckBox> cbs = new ArrayList<>();
        JPanel cg = new JPanel(new GridLayout(0, 1, 0, 5)); cg.setBackground(PURE_WHITE);
        for(String s : std) { JCheckBox b = new JCheckBox(s); b.setBackground(PURE_WHITE); b.setFont(soraFont.deriveFont(12f)); cbs.add(b); cg.add(b); }
        c3.add(new JScrollPane(cg));

        grid.add(c1); grid.add(col2); grid.add(c3);
        main.add(grid, BorderLayout.CENTER);

        btnSave.addActionListener(e -> {
            List<String> servs = new ArrayList<>(); for(JCheckBox b : cbs) if(b.isSelected()) servs.add(b.getText());
            Map<String, Object> data = new HashMap<>();
            data.put("nome", txtN.getText()); 
            data.put("localidade", txtL.getText());
            data.put("endereco", txtL.getText());
            try { data.put("latitude", Double.parseDouble(txtLat.getText())); data.put("longitude", Double.parseDouble(txtLon.getText())); } catch (Exception ex) { data.put("latitude", 0.0); data.put("longitude", 0.0); }
            data.put("mecanicos", listFromModel(mM)); data.put("servicos", servs);
            data.put("horarios", Arrays.asList("08:00", "12:00", "18:00"));

            String tID = txtID.getText().trim();
            String method = tID.isEmpty() ? "POST" : "PUT";
            String endpoint = tID.isEmpty() ? "api/oficinas" : "api/oficinas/" + tID;

            sendToAPI(method, endpoint, data).thenAccept(ok -> { if(ok) SwingUtilities.invokeLater(() -> showDashboard()); });
        });

        autoDetectLocation(txtL, txtLat, txtLon);
        return main;
    }

    private void showDashboard() {
        String pass = JOptionPane.showInputDialog(this, "Senha Admin:", "Login", JOptionPane.QUESTION_MESSAGE);
        if(ADMIN_PASSWORD.equals(pass)) {
            masterPanel.add(createDashboardStructure(), "DASHBOARD");
            masterLayout.show(masterPanel, "DASHBOARD");
        } else if(pass != null) {
            JOptionPane.showMessageDialog(this, "Acesso Negado.");
        }
    }

    private JPanel createDashboardStructure() {
        JPanel dash = new JPanel(new BorderLayout());
        dashboardLayout = new CardLayout(); dashboardContent = new JPanel(dashboardLayout);
        JPanel sidebar = new JPanel(new BorderLayout()); sidebar.setPreferredSize(new Dimension(320, 0)); sidebar.setBackground(SOLID_BLACK);
        sidebarButtons = new JPanel(); sidebarButtons.setLayout(new BoxLayout(sidebarButtons, BoxLayout.Y_AXIS)); sidebarButtons.setBackground(SOLID_BLACK);
        
        addMenuButton("🚗", "VEÍCULOS", "api/veiculos", new String[]{"Placa", "Modelo", "Ano"}, new String[]{"placa", "modelo", "ano"});
        addMenuButton("📅", "AGENDAMENTOS", "api/agendamentos", new String[]{"Cliente", "Serviço", "Horário"}, new String[]{"userId", "servico", "horario"});
        
        JButton btnEdit = createSidebarBtn("✏️", "EDITAR OFICINA");
        dashboardContent.add(createConfigScreen(false), "EDIT_CONFIG");
        btnEdit.addActionListener(e -> { currentActiveTab = "EDIT_CONFIG"; dashboardLayout.show(dashboardContent, "EDIT_CONFIG"); highlightButton(btnEdit); });
        sidebarButtons.add(btnEdit);

        JButton btnAdd = createSidebarBtn("➕", "ADICIONAR OFICINA");
        dashboardContent.add(createConfigScreen(true), "ADD_OFICINA");
        btnAdd.addActionListener(e -> { currentActiveTab = "ADD_OFICINA"; dashboardLayout.show(dashboardContent, "ADD_OFICINA"); highlightButton(btnAdd); });
        sidebarButtons.add(btnAdd);

        sidebar.add(sidebarButtons, BorderLayout.CENTER); dash.add(sidebar, BorderLayout.WEST); dash.add(dashboardContent, BorderLayout.CENTER);
        if (sidebarButtons.getComponentCount() > 0) ((JButton)sidebarButtons.getComponent(0)).doClick();
        
        autoRefreshTimer = new javax.swing.Timer(5000, e -> { if(!currentActiveTab.endsWith("CONFIG")) { RefreshParams p = tabRefreshMap.get(currentActiveTab); if (p != null) refreshData(p.endpoint, p.model, p.jsonKeys); } });
        autoRefreshTimer.start();
        return dash;
    }

    private void addMenuButton(String icon, String text, String endpoint, String[] cols, String[] keys) {
        JButton btn = createSidebarBtn(icon, text);
        DefaultTableModel model = new DefaultTableModel(cols, 0) { @Override public boolean isCellEditable(int r, int c) { return false; } };
        dashboardContent.add(createViewPanel(text, model), text);
        tabRefreshMap.put(text, new RefreshParams(endpoint, model, keys));
        btn.addActionListener(e -> { currentActiveTab = text; dashboardLayout.show(dashboardContent, text); highlightButton(btn); refreshData(endpoint, model, keys); });
        sidebarButtons.add(btn);
    }

    private JButton createSidebarBtn(String icon, String text) {
        JButton btn = new JButton("<html><body><span style='font-family: Segoe UI Emoji; color: #FFFFFF;'>"+icon+"</span>&nbsp;&nbsp;<span style='font-family: Sora; color: #FFFFFF;'>"+text+"</span></body></html>");
        btn.setMaximumSize(new Dimension(320, 75)); btn.setBackground(SOLID_BLACK); btn.setBorder(new EmptyBorder(0, 45, 0, 0));
        btn.setFocusPainted(false); btn.setBorderPainted(false); btn.setHorizontalAlignment(SwingConstants.LEFT); btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        return btn;
    }

    private void highlightButton(JButton btn) {
        for (Component c : sidebarButtons.getComponents()) if (c instanceof JButton) { c.setBackground(SOLID_BLACK); ((JButton)c).setBorder(new EmptyBorder(0, 45, 0, 0)); }
        btn.setBackground(SIDEBAR_SELECTED);
        btn.setBorder(new CompoundBorder(BorderFactory.createMatteBorder(0, 8, 0, 0, PRIMARY_GREEN), new EmptyBorder(0, 37, 0, 0)));
    }

    private JPanel createViewPanel(String title, DefaultTableModel model) {
        JPanel p = new JPanel(new BorderLayout(0, 40)); p.setBackground(ICE_WHITE); p.setBorder(new EmptyBorder(60, 90, 60, 90));
        JLabel t = new JLabel(title.replaceAll("[^\\p{L}\\p{Nd}\\s]", "").trim()); t.setFont(ardelaFont.deriveFont(52f));
        JTable table = new JTable(model); table.setRowHeight(75); table.setFont(soraFont.deriveFont(16f));
        table.setBackground(PURE_WHITE); table.setGridColor(ICE_WHITE);
        JTableHeader h = table.getTableHeader(); h.setBackground(PURE_WHITE); h.setFont(soraFont.deriveFont(Font.BOLD, 18f)); h.setPreferredSize(new Dimension(0, 80)); h.setBorder(BorderFactory.createMatteBorder(0, 0, 4, 0, PRIMARY_GREEN));
        DefaultTableCellRenderer cr = new DefaultTableCellRenderer(); cr.setHorizontalAlignment(JLabel.CENTER);
        for(int i=0; i<table.getColumnCount(); i++) table.getColumnModel().getColumn(i).setCellRenderer(cr);
        p.add(t, BorderLayout.NORTH); p.add(new JScrollPane(table), BorderLayout.CENTER);
        return p;
    }

    private java.util.concurrent.CompletableFuture<Boolean> sendToAPI(String method, String endpoint, Object data) {
        String json = gson.toJson(data);
        HttpRequest.Builder rb = HttpRequest.newBuilder().uri(URI.create(BASE_URL + endpoint)).header("Content-Type", "application/json");
        if(method.equals("POST")) rb.POST(HttpRequest.BodyPublishers.ofString(json));
        else rb.PUT(HttpRequest.BodyPublishers.ofString(json));
        return client.sendAsync(rb.build(), HttpResponse.BodyHandlers.ofString()).thenApply(res -> res.statusCode() >= 200 && res.statusCode() < 300).exceptionally(ex -> false);
    }

    private void refreshData(String endpoint, DefaultTableModel model, String[] jsonKeys) {
        client.sendAsync(HttpRequest.newBuilder().uri(URI.create(BASE_URL + endpoint)).build(), HttpResponse.BodyHandlers.ofString())
            .thenApply(res -> res.body())
            .thenAccept(json -> SwingUtilities.invokeLater(() -> {
                model.setRowCount(0);
                try {
                    List<Map<String, Object>> data = gson.fromJson(json, new TypeToken<List<Map<String, Object>>>(){}.getType());
                    if (data != null) for (Map<String, Object> item : data) {
                        Object[] row = new Object[jsonKeys.length];
                        for (int i = 0; i < jsonKeys.length; i++) row[i] = (item.get(jsonKeys[i]) != null) ? item.get(jsonKeys[i]).toString() : "-";
                        model.addRow(row);
                    }
                } catch (Exception ignored) {}
            })).exceptionally(ex -> null);
    }

    private void autoDetectLocation(JTextField txtLoc, JTextField txtLat, JTextField txtLon) {
        client.sendAsync(HttpRequest.newBuilder().uri(URI.create("http://ip-api.com/json/")).build(), HttpResponse.BodyHandlers.ofString())
            .thenApply(HttpResponse::body)
            .thenAccept(json -> SwingUtilities.invokeLater(() -> {
                Map<String, Object> data = gson.fromJson(json, new TypeToken<Map<String, Object>>(){}.getType());
                if(data != null && "success".equals(data.get("status"))) {
                    txtLat.setText(String.valueOf(data.get("lat"))); txtLon.setText(String.valueOf(data.get("lon")));
                    txtLoc.setText(data.get("city") + ", " + data.get("regionName"));
                }
            }));
    }

    private void loadFonts() {
        try {
            InputStream isA = getClass().getResourceAsStream("/assets/Ardela.ttf");
            ardelaFont = Font.createFont(Font.TRUETYPE_FONT, isA);
            InputStream isS = getClass().getResourceAsStream("/assets/Sora-Regular.ttf");
            soraFont = Font.createFont(Font.TRUETYPE_FONT, isS);
        } catch (Exception e) {
            ardelaFont = new Font("SansSerif", Font.BOLD, 36);
            soraFont = new Font("SansSerif", Font.PLAIN, 16);
        }
    }

    private JTextField createStyledField(String placeholder, Color bg, Color fg) {
        JTextField f = new JTextField(); f.setMaximumSize(new Dimension(Integer.MAX_VALUE, 60));
        f.setFont(soraFont.deriveFont(14f)); f.setBackground(bg); f.setForeground(fg); f.setCaretColor(fg);
        f.setBorder(BorderFactory.createTitledBorder(new LineBorder(PRIMARY_GREEN, 1), placeholder, 0, 0, soraFont.deriveFont(9f), PRIMARY_GREEN));
        return f;
    }

    private JPanel createFormCard(String title) {
        JPanel p = new JPanel(); p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS)); p.setBackground(PURE_WHITE);
        p.setBorder(new CompoundBorder(new LineBorder(Color.decode("#DDD"), 1), new EmptyBorder(25, 25, 25, 25)));
        JLabel l = new JLabel(title); l.setFont(soraFont.deriveFont(Font.BOLD, 14f)); l.setBorder(new EmptyBorder(0, 0, 20, 0));
        p.add(l); return p;
    }

    private JPanel createListPanel(String title, DefaultListModel<String> model) {
        JPanel p = new JPanel(new BorderLayout(5, 5)); p.setBackground(PURE_WHITE);
        p.setBorder(new CompoundBorder(new LineBorder(Color.decode("#DDD"), 1), new EmptyBorder(10, 10, 10, 10)));
        JTextField in = new JTextField("Adicionar..."); in.addActionListener(e -> { if(!in.getText().isEmpty()){ model.addElement(in.getText()); in.setText(""); } });
        p.add(new JLabel(title), BorderLayout.NORTH); p.add(in, BorderLayout.CENTER); p.add(new JScrollPane(new JList<>(model)), BorderLayout.SOUTH);
        p.getComponent(2).setPreferredSize(new Dimension(0, 150));
        return p;
    }

    private List<String> listFromModel(DefaultListModel<String> m) {
        List<String> l = new ArrayList<>(); for(int i=0; i<m.size(); i++) l.add(m.get(i)); return l;
    }

    public static void main(String[] args) {
        try { UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName()); } catch (Exception ignored) {}
        SwingUtilities.invokeLater(Main::new);
    }
}
