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
import java.awt.*;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;
import java.util.List;

public class Main extends JFrame {
    private static final String BASE_URL = "https://api-java-production-5e77.up.railway.app/";
    private static final HttpClient client = HttpClient.newBuilder().connectTimeout(java.time.Duration.ofSeconds(15)).build();
    private static final Gson gson = new Gson();

    // Paleta Oficial
    private static final Color PRIMARY_GREEN = Color.decode("#16BC4E");
    private static final Color SOLID_BLACK = Color.decode("#0B0B0B");
    private static final Color ICE_WHITE = Color.decode("#F5F6FC");
    private static final Color PURE_WHITE = Color.decode("#FFFFFF");
    private static final Color TEXT_GRAY = Color.decode("#888888");
    private static final Color SIDEBAR_SELECTED = Color.decode("#1A1A1A");

    private Font ardelaFont, soraFont;
    private CardLayout masterLayout;
    private JPanel masterPanel, dashboardContent, sidebarButtons;
    private CardLayout dashboardLayout;
    private String currentActiveTab = "OFICINAS";

    public Main() {
        loadFonts();
        setupWindow();
        masterLayout = new CardLayout();
        masterPanel = new JPanel(masterLayout);
        
        masterPanel.add(createLaunchScreen(), "LAUNCH");
        add(masterPanel);
        masterLayout.show(masterPanel, "LAUNCH");
        setVisible(true);
    }

    private void setupWindow() {
        setTitle("Motor Pro | Admin Dashboard");
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
        
        JLabel logo = new JLabel("MOTOR PRO");
        logo.setForeground(PURE_WHITE); logo.setFont(ardelaFont.deriveFont(64f)); logo.setAlignmentX(0.5f);
        
        JLabel sub = new JLabel("ENTERPRISE MANAGEMENT");
        sub.setForeground(PRIMARY_GREEN); sub.setFont(soraFont.deriveFont(Font.BOLD, 16f)); sub.setAlignmentX(0.5f);
        sub.setBorder(new EmptyBorder(0, 0, 80, 0));

        JButton btn = new JButton("ACESSAR DASHBOARD");
        btn.setBackground(PRIMARY_GREEN); btn.setForeground(SOLID_BLACK); btn.setFont(soraFont.deriveFont(Font.BOLD, 20f));
        btn.setMaximumSize(new Dimension(500, 80)); btn.setAlignmentX(0.5f); btn.setCursor(new Cursor(Cursor.HAND_CURSOR));

        btn.addActionListener(e -> showDashboard());
        card.add(logo); card.add(sub); card.add(Box.createRigidArea(new Dimension(0, 40))); card.add(btn);
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
        
        addMenuButton("🛠️", "OFICINAS", "oficinas", new String[]{"ID", "Nome", "Endereço", "Serviços"}, new String[]{"id", "nome", "endereco", "servicos"});
        addMenuButton("📅", "AGENDAMENTOS", "appointments", new String[]{"ID", "Serviço", "Data/Hora", "Oficina", "Status"}, new String[]{"id", "servico", "dataHora", "oficinaNome", "status"});
        
        sidebar.add(sidebarButtons, BorderLayout.CENTER);
        dash.add(sidebar, BorderLayout.WEST); dash.add(dashboardContent, BorderLayout.CENTER);
        
        if (sidebarButtons.getComponentCount() > 0) ((JButton)sidebarButtons.getComponent(0)).doClick();
        
        return dash;
    }

    private void addMenuButton(String icon, String text, String endpoint, String[] cols, String[] keys) {
        JButton btn = createSidebarBtn(icon, text);
        DefaultTableModel model = new DefaultTableModel(cols, 0) { @Override public boolean isCellEditable(int r, int c) { return false; } };
        dashboardContent.add(createViewPanel(text, model, endpoint, keys), text);
        btn.addActionListener(e -> {
            currentActiveTab = text;
            dashboardLayout.show(dashboardContent, text);
            highlightButton(btn);
            refreshData(endpoint, model, keys);
        });
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

    private JPanel createViewPanel(String title, DefaultTableModel model, String endpoint, String[] jsonKeys) {
        JPanel p = new JPanel(new BorderLayout(0, 40)); p.setBackground(ICE_WHITE); p.setBorder(new EmptyBorder(60, 90, 60, 90));
        JPanel head = new JPanel(new BorderLayout()); head.setBackground(ICE_WHITE);
        JLabel t = new JLabel(title); t.setFont(ardelaFont.deriveFont(52f)); t.setForeground(SOLID_BLACK);
        
        JButton refresh = new JButton("ATUALIZAR");
        refresh.setBackground(PRIMARY_GREEN); refresh.setForeground(SOLID_BLACK); refresh.setFont(soraFont.deriveFont(Font.BOLD, 12f));
        refresh.setPreferredSize(new Dimension(180, 50));
        refresh.addActionListener(e -> refreshData(endpoint, model, jsonKeys));
        
        head.add(t, BorderLayout.WEST); head.add(refresh, BorderLayout.EAST);

        JTable table = new JTable(model); table.setRowHeight(75); table.setFont(soraFont.deriveFont(15f));
        table.setBackground(PURE_WHITE); table.setGridColor(ICE_WHITE);
        
        JTableHeader h = table.getTableHeader(); h.setBackground(PURE_WHITE); h.setFont(soraFont.deriveFont(Font.BOLD, 16f)); h.setPreferredSize(new Dimension(0, 70)); h.setBorder(BorderFactory.createMatteBorder(0, 0, 3, 0, PRIMARY_GREEN));
        DefaultTableCellRenderer cr = new DefaultTableCellRenderer(); cr.setHorizontalAlignment(JLabel.CENTER);
        for(int i=0; i<table.getColumnCount(); i++) table.getColumnModel().getColumn(i).setCellRenderer(cr);
        
        p.add(head, BorderLayout.NORTH); p.add(new JScrollPane(table), BorderLayout.CENTER);
        return p;
    }

    private void refreshData(String endpoint, DefaultTableModel model, String[] jsonKeys) {
        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(BASE_URL + endpoint)).GET().build();
        client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
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
