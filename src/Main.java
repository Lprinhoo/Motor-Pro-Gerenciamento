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
    // URL ATUALIZADA PARA O RAILWAY
    private static final String BASE_URL = "https://SUA-URL-DO-RAILWAY.up.railway.app/api/";
    private static final HttpClient client = HttpClient.newBuilder().connectTimeout(java.time.Duration.ofSeconds(10)).build();
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

    private String currentAccessKey = "DEMO-001"; 
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
        
        masterPanel.add(createLoginScreen(), "LOGIN");
        add(masterPanel);
        masterLayout.show(masterPanel, "LOGIN");
        setVisible(true);
    }

    private void setupWindow() {
        setTitle("Motor Pro | Admin Panel");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setExtendedState(JFrame.MAXIMIZED_BOTH);
        try {
            InputStream is = getClass().getResourceAsStream("/assets/perfil.png");
            if (is != null) setIconImage(ImageIO.read(is));
        } catch (Exception ignored) {}
    }

    private JPanel createLoginScreen() {
        JPanel container = new JPanel(new GridBagLayout());
        container.setBackground(SOLID_BLACK);
        JPanel card = new JPanel(); card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS)); card.setBackground(SOLID_BLACK);
        
        // --- AVATAR MAIOR E CENTRALIZADO ---
        JLabel avatar = new JLabel();
        try {
            InputStream is = getClass().getResourceAsStream("/assets/perfil.png");
            if (is != null) avatar.setIcon(new ImageIcon(ImageIO.read(is).getScaledInstance(200, 200, Image.SCALE_SMOOTH))); // Aumentado
        } catch (Exception ignored) {}
        avatar.setAlignmentX(0.5f);
        avatar.setBorder(new CompoundBorder(new LineBorder(PRIMARY_GREEN, 4, true), new EmptyBorder(10, 10, 10, 10))); // Borda mais grossa

        JLabel title = new JLabel("MOTOR PRO");
        title.setForeground(PURE_WHITE); title.setFont(ardelaFont.deriveFont(64f)); // Fonte maior
        title.setAlignmentX(0.5f);
        title.setBorder(new EmptyBorder(50, 0, 10, 0)); // Espaçamento

        JLabel subTitle = new JLabel("ADMIN PANEL");
        subTitle.setForeground(PRIMARY_GREEN); subTitle.setFont(soraFont.deriveFont(Font.BOLD, 18f)); // Subtítulo
        subTitle.setAlignmentX(0.5f);
        subTitle.setBorder(new EmptyBorder(0, 0, 80, 0));

        JButton btn = new JButton("ENTRAR NO DASHBOARD");
        btn.setBackground(PRIMARY_GREEN); btn.setForeground(SOLID_BLACK); btn.setFont(soraFont.deriveFont(Font.BOLD, 20f)); // Fonte maior no botão
        btn.setMaximumSize(new Dimension(500, 80)); // Botão maior
        btn.setAlignmentX(0.5f); btn.setBorder(null); btn.setCursor(new Cursor(Cursor.HAND_CURSOR));

        btn.addActionListener(e -> showDashboard());

        card.add(avatar); card.add(title); card.add(subTitle); card.add(Box.createRigidArea(new Dimension(0, 60))); card.add(btn);
        container.add(card);
        return container;
    }

    private void showDashboard() {
        masterPanel.add(createDashboardStructure(), "DASHBOARD");
        masterLayout.show(masterPanel, "DASHBOARD");
    }

    private JPanel createDashboardStructure() {
        JPanel dash = new JPanel(new BorderLayout());
        dashboardLayout = new CardLayout(); dashboardContent = new JPanel(dashboardLayout);
        
        JPanel sidebar = new JPanel(new BorderLayout()); sidebar.setPreferredSize(new Dimension(320, 0)); sidebar.setBackground(SOLID_BLACK);
        sidebarButtons = new JPanel(); sidebarButtons.setLayout(new BoxLayout(sidebarButtons, BoxLayout.Y_AXIS)); sidebarButtons.setBackground(SOLID_BLACK);
        
        // ABAS DE DADOS REAIS
        addMenuButton("🚗", "VEÍCULOS", "veiculos", new String[]{"Placa", "Modelo", "Ano"}, new String[]{"placa", "modelo", "ano"});
        addMenuButton("📅", "AGENDAMENTOS", "agendamentos", new String[]{"Cliente", "Serviço", "Horário"}, new String[]{"userId", "servico", "horario"});
        addMenuButton("🛠️", "OFICINAS", "oficinas", new String[]{"ID", "Nome", "Localidade"}, new String[]{"id", "nome", "localidade"});

        lblStatus = new JLabel(); updateStatus(true);
        JPanel foot = new JPanel(new BorderLayout()); foot.setBackground(SOLID_BLACK); foot.setBorder(new EmptyBorder(20, 45, 40, 45));
        foot.add(lblStatus); sidebar.add(foot, BorderLayout.SOUTH);
        
        sidebar.add(sidebarButtons, BorderLayout.CENTER); dash.add(sidebar, BorderLayout.WEST); dash.add(dashboardContent, BorderLayout.CENTER);
        
        if (sidebarButtons.getComponentCount() > 0) ((JButton)sidebarButtons.getComponent(0)).doClick();
        
        autoRefreshTimer = new javax.swing.Timer(5000, e -> { 
            RefreshParams p = tabRefreshMap.get(currentActiveTab); 
            if (p != null) refreshData(p.endpoint, p.model, p.jsonKeys); 
        });
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
        btn.setBackground(Color.decode("#1A1A1A"));
        btn.setBorder(new CompoundBorder(BorderFactory.createMatteBorder(0, 8, 0, 0, PRIMARY_GREEN), new EmptyBorder(0, 37, 0, 0)));
    }

    private JPanel createViewPanel(String title, DefaultTableModel model) {
        JPanel p = new JPanel(new BorderLayout(0, 40)); p.setBackground(ICE_WHITE); p.setBorder(new EmptyBorder(60, 90, 60, 90));
        String cleanTitle = title.replaceAll("[^\\p{L}\\p{Nd}\\s]", "").trim();
        JLabel t = new JLabel(cleanTitle); t.setFont(ardelaFont.deriveFont(52f)); t.setForeground(SOLID_BLACK);
        JTable table = new JTable(model); table.setRowHeight(75); table.setFont(soraFont.deriveFont(16f));
        table.setBackground(PURE_WHITE); table.setGridColor(ICE_WHITE);
        JTableHeader h = table.getTableHeader(); h.setBackground(PURE_WHITE); h.setFont(soraFont.deriveFont(Font.BOLD, 18f)); h.setPreferredSize(new Dimension(0, 80)); h.setBorder(BorderFactory.createMatteBorder(0, 0, 4, 0, PRIMARY_GREEN));
        DefaultTableCellRenderer cr = new DefaultTableCellRenderer(); cr.setHorizontalAlignment(JLabel.CENTER);
        for(int i=0; i<table.getColumnCount(); i++) table.getColumnModel().getColumn(i).setCellRenderer(cr);
        p.add(t, BorderLayout.NORTH); p.add(new JScrollPane(table), BorderLayout.CENTER);
        return p;
    }

    private void refreshData(String endpoint, DefaultTableModel model, String[] jsonKeys) {
        client.sendAsync(HttpRequest.newBuilder().uri(URI.create(BASE_URL + endpoint)).build(), HttpResponse.BodyHandlers.ofString())
            .thenApply(res -> { updateStatus(res.statusCode() == 200); return res.body(); })
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
            })).exceptionally(ex -> { SwingUtilities.invokeLater(() -> updateStatus(false)); return null; });
    }

    private void updateStatus(boolean online) {
        String c = online ? "#16BC4E" : "#555555";
        if (lblStatus != null) lblStatus.setText("<html><span style='color: " + c + "; font-family: Sora; font-size: 11px; font-weight: bold;'>&bull; System Online</span></html>");
    }

    private void loadFonts() {
        try {
            InputStream isA = getClass().getResourceAsStream("/assets/Ardela.ttf");
            ardelaFont = Font.createFont(Font.TRUETYPE_FONT, isA);
            InputStream isS = getClass().getResourceAsStream("/assets/Sora-Regular.ttf");
            soraFont = Font.createFont(Font.TRUETYPE_FONT, isS);
            GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(ardelaFont);
            GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(soraFont);
        } catch (Exception e) {
            ardelaFont = new Font("SansSerif", Font.BOLD, 36);
            soraFont = new Font("SansSerif", Font.PLAIN, 16);
        }
    }

    public static void main(String[] args) {
        try { UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName()); } catch (Exception ignored) {}
        SwingUtilities.invokeLater(Main::new);
    }
}
