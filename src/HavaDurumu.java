import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.TimeZone;

public class HavaDurumu extends JFrame {

    private static final String API_KEY = "687b0d5e5ed0b86e8a23ee91c81dae7d";
    private static final String BASE_URL = "https://api.openweathermap.org/data/2.5/weather";

    // ========== DYNAMIC COLORS ==========
    private Color bgTop = new Color(15, 32, 65);
    private Color bgMid = new Color(30, 55, 100);
    private Color bgBottom = new Color(44, 83, 131);
    private Color accentColor = new Color(255, 193, 7);
    private static final Color TEXT_PRIMARY = new Color(255, 255, 255);
    private static final Color TEXT_SECONDARY = new Color(190, 210, 240);
    private static final Color GLASS_BG = new Color(255, 255, 255, 22);
    private static final Color GLASS_BORDER = new Color(255, 255, 255, 50);
    private static final Color GLASS_HL = new Color(255, 255, 255, 35);

    // ========== UI ==========
    private JTextField sehirField;
    private JLabel durumEmoji, sicaklikLabel, durumLabel;
    private JLabel nemLabel, ruzgarLabel, basincLabel, gorunurlukLabel;
    private JLabel hissedilenLabel, sehirBilgiLabel, tarihLabel, minMaxLabel;
    private JLabel sunriseLabel, sunsetLabel, moonLabel, uvLabel;
    private JPanel kartPanel, detayPanel, extraPanel, anaPanel;
    private JButton araButton;
    private boolean yukleniyor = false;
    private float cardAlpha = 0f;
    private Timer animTimer;

    // ========== WEATHER ANIMATION STATE ==========
    private final List<Particle> particles = new ArrayList<>();
    private String currentWeather = "default";
    private boolean isNight = false;
    private Timer particleTimer;
    private float animTick = 0f;
    private Timer bgAnimTimer;
    private float lightningAlpha = 0f;
    private int lightningCooldown = 0;
    private final Random rng = new Random();

    // Sun/moon positions for background drawing
    private long sunriseEpoch = 0, sunsetEpoch = 0;
    private int tzOffset = 0;

    public HavaDurumu() {
        setTitle("Hava Durumu");
        setSize(520, 860);
        setMinimumSize(new Dimension(460, 800));
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        initUI();
        startAnimationLoop();
    }

    // ==================== PARTICLE SYSTEM ====================
    static class Particle {
        float x, y, vx, vy, size, alpha, life, maxLife;
        int type; // 0=ambient, 1=rain, 2=snow, 3=fog, 4=sun_ray
        Color color;
        Particle() {}
    }

    private Particle makeParticle(int type, int w, int h, boolean fromTop) {
        Particle p = new Particle();
        p.type = type;
        p.x = rng.nextFloat() * w;
        p.y = fromTop ? -rng.nextFloat() * 60 : rng.nextFloat() * h;
        p.maxLife = 999;
        p.life = 0;
        p.alpha = 0.1f + rng.nextFloat() * 0.3f;
        switch (type) {
            case 0 -> { // ambient dots
                p.vx = (rng.nextFloat() - 0.5f) * 0.4f;
                p.vy = 0.2f + rng.nextFloat() * 0.8f;
                p.size = 2 + rng.nextFloat() * 4;
                p.color = Color.WHITE;
                p.alpha = 0.08f + rng.nextFloat() * 0.2f;
            }
            case 1 -> { // rain
                p.vx = -0.5f - rng.nextFloat() * 1f;
                p.vy = 8f + rng.nextFloat() * 10f;
                p.size = 1.5f + rng.nextFloat() * 1.5f;
                p.color = new Color(150, 190, 255);
                p.alpha = 0.2f + rng.nextFloat() * 0.4f;
            }
            case 2 -> { // snow
                p.vx = (rng.nextFloat() - 0.5f) * 1.2f;
                p.vy = 0.5f + rng.nextFloat() * 1.5f;
                p.size = 3 + rng.nextFloat() * 5;
                p.color = new Color(230, 240, 255);
                p.alpha = 0.3f + rng.nextFloat() * 0.5f;
            }
            case 3 -> { // fog
                p.vx = 0.3f + rng.nextFloat() * 0.5f;
                p.vy = (rng.nextFloat() - 0.5f) * 0.1f;
                p.size = 60 + rng.nextFloat() * 100;
                p.color = new Color(200, 210, 220);
                p.alpha = 0.03f + rng.nextFloat() * 0.06f;
                p.y = rng.nextFloat() * h;
                p.x = -p.size + rng.nextFloat() * (w + p.size);
            }
            case 4 -> { // sun ray
                p.x = w * 0.3f + rng.nextFloat() * w * 0.4f;
                p.y = 0;
                p.vx = 0; p.vy = 0;
                p.size = 2 + rng.nextFloat() * 3;
                p.color = new Color(255, 230, 150);
                p.alpha = 0f;
                p.maxLife = 80 + rng.nextFloat() * 120;
            }
        }
        return p;
    }

    private void resetParticles() {
        particles.clear();
        int w = getWidth(), h = getHeight();
        switch (currentWeather) {
            case "rain", "drizzle" -> { for (int i = 0; i < 100; i++) particles.add(makeParticle(1, w, h, false)); }
            case "thunderstorm" -> { for (int i = 0; i < 150; i++) particles.add(makeParticle(1, w, h, false)); }
            case "snow" -> { for (int i = 0; i < 70; i++) particles.add(makeParticle(2, w, h, false)); }
            case "mist", "fog", "haze" -> { for (int i = 0; i < 15; i++) particles.add(makeParticle(3, w, h, false)); }
            case "clear" -> {
                if (!isNight) for (int i = 0; i < 8; i++) particles.add(makeParticle(4, w, h, false));
                for (int i = 0; i < 10; i++) particles.add(makeParticle(0, w, h, false));
            }
            default -> { for (int i = 0; i < 18; i++) particles.add(makeParticle(0, w, h, false)); }
        }
    }

    private void updateParticles() {
        int w = getWidth(), h = getHeight();
        for (int i = particles.size() - 1; i >= 0; i--) {
            Particle p = particles.get(i);
            p.x += p.vx;
            p.y += p.vy;
            p.life++;
            if (p.type == 4) { // sun ray fades in/out
                float t = p.life / p.maxLife;
                p.alpha = (float)(Math.sin(t * Math.PI) * 0.08);
                if (p.life > p.maxLife) { particles.set(i, makeParticle(4, w, h, false)); continue; }
            }
            if (p.y > h + 20 || p.x < -p.size * 2 || p.x > w + p.size * 2) {
                particles.set(i, makeParticle(p.type, w, h, true));
            }
        }
    }

    private void drawParticles(Graphics2D g2, int w, int h) {
        for (Particle p : particles) {
            if (p.alpha <= 0.001f) continue;
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.min(p.alpha, 1f)));
            switch (p.type) {
                case 1 -> { // rain streak
                    g2.setColor(p.color);
                    g2.setStroke(new BasicStroke(p.size * 0.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    float len = p.vy * 2.5f;
                    g2.draw(new Line2D.Float(p.x, p.y, p.x + p.vx * 2, p.y - len));
                }
                case 2 -> { // snowflake
                    g2.setColor(p.color);
                    float wobble = (float) Math.sin(animTick * 0.05 + p.x * 0.1) * 0.8f;
                    p.vx += wobble * 0.01f;
                    g2.fill(new Ellipse2D.Float(p.x - p.size/2, p.y - p.size/2, p.size, p.size));
                }
                case 3 -> { // fog blob
                    RadialGradientPaint fogP = new RadialGradientPaint(
                        new Point2D.Float(p.x, p.y), p.size,
                        new float[]{0f, 1f},
                        new Color[]{new Color(p.color.getRed(), p.color.getGreen(), p.color.getBlue(), (int)(p.alpha * 255)),
                                    new Color(p.color.getRed(), p.color.getGreen(), p.color.getBlue(), 0)}
                    );
                    g2.setPaint(fogP);
                    g2.fillOval((int)(p.x - p.size), (int)(p.y - p.size/2), (int)(p.size*2), (int)p.size);
                }
                case 4 -> { // sun ray
                    g2.setColor(new Color(255, 240, 180, (int)(p.alpha * 255)));
                    g2.setStroke(new BasicStroke(p.size, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    float rayLen = h * 0.7f;
                    float angle = (float)(p.x / w * 0.4 - 0.2 + Math.sin(animTick * 0.01 + p.size) * 0.05);
                    g2.draw(new Line2D.Float(p.x, 0, p.x + angle * rayLen, rayLen));
                }
                default -> { // ambient dot
                    g2.setColor(p.color);
                    g2.fill(new Ellipse2D.Float(p.x - p.size/2, p.y - p.size/2, p.size, p.size));
                }
            }
        }
        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1f));
    }

    // ==================== BACKGROUND PAINTING ====================
    private void drawBackground(Graphics2D g2, int w, int h) {
        // Main gradient
        float pulse = (float)(Math.sin(animTick * 0.015) * 0.15 + 0.5);
        GradientPaint gp = new GradientPaint(
            w * pulse, 0, bgTop,
            w * (1 - pulse), h, bgBottom
        );
        g2.setPaint(gp);
        g2.fillRect(0, 0, w, h);

        // Draw celestial body
        if (isNight) {
            drawMoon(g2, w);
            drawStars(g2, w, h);
        } else if ("clear".equals(currentWeather)) {
            drawSun(g2, w);
        }

        // Lightning flash for thunderstorm
        if ("thunderstorm".equals(currentWeather)) {
            lightningCooldown--;
            if (lightningCooldown <= 0 && rng.nextFloat() < 0.008f) {
                lightningAlpha = 0.6f + rng.nextFloat() * 0.3f;
                lightningCooldown = 30 + rng.nextInt(120);
            }
            if (lightningAlpha > 0) {
                g2.setColor(new Color(200, 210, 255, (int)(lightningAlpha * 255)));
                g2.fillRect(0, 0, w, h);
                // Lightning bolt
                drawLightningBolt(g2, w, h);
                lightningAlpha *= 0.75f;
                if (lightningAlpha < 0.01f) lightningAlpha = 0;
            }
        }

        // Particles
        drawParticles(g2, w, h);

        // Vignette
        RadialGradientPaint vig = new RadialGradientPaint(
            new Point2D.Float(w/2f, h/2f), Math.max(w, h) * 0.7f,
            new float[]{0f, 0.65f, 1f},
            new Color[]{new Color(0,0,0,0), new Color(0,0,0,0), new Color(0,0,0,90)}
        );
        g2.setPaint(vig);
        g2.fillRect(0, 0, w, h);
    }

    private void drawSun(Graphics2D g2, int w) {
        float cx = w * 0.75f, cy = 55;
        float r = 30 + (float)Math.sin(animTick * 0.03) * 3;
        // Outer glow
        for (int i = 4; i >= 0; i--) {
            float gr = r + i * 12;
            int a = (int)(18 - i * 3);
            g2.setColor(new Color(255, 220, 80, Math.max(a, 0)));
            g2.fill(new Ellipse2D.Float(cx - gr, cy - gr, gr * 2, gr * 2));
        }
        // Core
        RadialGradientPaint sunP = new RadialGradientPaint(
            new Point2D.Float(cx, cy), r,
            new float[]{0f, 0.7f, 1f},
            new Color[]{new Color(255, 255, 200), new Color(255, 200, 50), new Color(255, 160, 0, 0)}
        );
        g2.setPaint(sunP);
        g2.fill(new Ellipse2D.Float(cx - r, cy - r, r * 2, r * 2));
    }

    private void drawMoon(Graphics2D g2, int w) {
        float cx = w * 0.78f, cy = 50, r = 22;
        // Glow
        for (int i = 3; i >= 0; i--) {
            float gr = r + i * 10;
            g2.setColor(new Color(180, 200, 230, 8));
            g2.fill(new Ellipse2D.Float(cx - gr, cy - gr, gr * 2, gr * 2));
        }
        // Moon disk
        g2.setColor(new Color(220, 230, 245));
        g2.fill(new Ellipse2D.Float(cx - r, cy - r, r * 2, r * 2));
        // Shadow to show phase
        double phase = getMoonPhase();
        float shadowX = cx - r * (float)(1 - phase * 2);
        g2.setColor(bgTop);
        g2.fill(new Ellipse2D.Float(shadowX - r * 0.8f, cy - r, r * 1.6f, r * 2));
        // Craters
        g2.setColor(new Color(200, 210, 225, 80));
        g2.fill(new Ellipse2D.Float(cx - 6, cy - 8, 8, 8));
        g2.fill(new Ellipse2D.Float(cx + 4, cy + 2, 5, 5));
        g2.fill(new Ellipse2D.Float(cx - 10, cy + 5, 6, 6));
    }

    private void drawStars(Graphics2D g2, int w, int h) {
        long seed = 12345L;
        Random starRng = new Random(seed);
        for (int i = 0; i < 60; i++) {
            float sx = starRng.nextFloat() * w;
            float sy = starRng.nextFloat() * h * 0.5f;
            float ss = 1f + starRng.nextFloat() * 2f;
            float flicker = (float)(Math.sin(animTick * 0.05 + i * 1.7) * 0.3 + 0.7);
            int a = (int)(flicker * (currentWeather.equals("clear") ? 180 : 60));
            g2.setColor(new Color(255, 255, 255, Math.max(0, Math.min(a, 255))));
            g2.fill(new Ellipse2D.Float(sx, sy, ss, ss));
        }
    }

    private void drawLightningBolt(Graphics2D g2, int w, int h) {
        g2.setColor(new Color(200, 220, 255, (int)(lightningAlpha * 200)));
        g2.setStroke(new BasicStroke(2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        float x = w * 0.3f + rng.nextFloat() * w * 0.4f;
        float y = 0;
        GeneralPath bolt = new GeneralPath();
        bolt.moveTo(x, y);
        for (int i = 0; i < 6; i++) {
            x += (rng.nextFloat() - 0.5f) * 40;
            y += 30 + rng.nextFloat() * 50;
            bolt.lineTo(x, y);
        }
        g2.draw(bolt);
    }

    // ==================== THEME ====================
    private void updateTheme(String weatherMain, double temp, boolean night) {
        isNight = night;
        currentWeather = weatherMain != null ? weatherMain.toLowerCase() : "default";
        if (night) {
            switch (currentWeather) {
                case "clear" -> { bgTop = new Color(8, 12, 30); bgBottom = new Color(15, 25, 55); accentColor = new Color(180, 200, 230); }
                case "clouds" -> { bgTop = new Color(15, 20, 35); bgBottom = new Color(35, 45, 65); accentColor = new Color(150, 170, 200); }
                case "rain","drizzle" -> { bgTop = new Color(10, 15, 25); bgBottom = new Color(25, 35, 55); accentColor = new Color(100, 160, 230); }
                case "thunderstorm" -> { bgTop = new Color(8, 8, 18); bgBottom = new Color(20, 18, 35); accentColor = new Color(255, 235, 59); }
                case "snow" -> { bgTop = new Color(25, 30, 50); bgBottom = new Color(60, 70, 95); accentColor = new Color(200, 220, 245); }
                default -> { bgTop = new Color(12, 18, 35); bgBottom = new Color(30, 40, 65); accentColor = new Color(170, 190, 220); }
            }
        } else {
            switch (currentWeather) {
                case "clear" -> {
                    if (temp > 30) { bgTop = new Color(255, 120, 0); bgBottom = new Color(255, 60, 0); accentColor = new Color(255, 235, 59); }
                    else { bgTop = new Color(30, 100, 200); bgBottom = new Color(80, 170, 240); accentColor = new Color(255, 213, 79); }
                }
                case "clouds" -> { bgTop = new Color(65, 80, 105); bgBottom = new Color(100, 120, 150); accentColor = new Color(180, 200, 225); }
                case "rain","drizzle" -> { bgTop = new Color(35, 50, 70); bgBottom = new Color(55, 75, 100); accentColor = new Color(100, 180, 255); }
                case "thunderstorm" -> { bgTop = new Color(20, 20, 40); bgBottom = new Color(45, 40, 70); accentColor = new Color(255, 235, 59); }
                case "snow" -> { bgTop = new Color(150, 170, 200); bgBottom = new Color(210, 225, 240); accentColor = new Color(220, 240, 255); }
                case "mist","fog","haze" -> { bgTop = new Color(90, 100, 115); bgBottom = new Color(130, 145, 165); accentColor = new Color(200, 215, 230); }
                default -> { bgTop = new Color(20, 45, 80); bgBottom = new Color(50, 90, 140); accentColor = new Color(255, 193, 7); }
            }
        }
        resetParticles();
    }

    // ==================== ANIMATION LOOP ====================
    private void startAnimationLoop() {
        resetParticles();
        bgAnimTimer = new Timer(30, e -> {
            animTick += 1f;
            updateParticles();
            if (anaPanel != null) anaPanel.repaint();
        });
        bgAnimTimer.start();
    }

    // ==================== MOON PHASE ====================
    static double getMoonPhase() {
        Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        int y = cal.get(Calendar.YEAR);
        int m = cal.get(Calendar.MONTH) + 1;
        int d = cal.get(Calendar.DAY_OF_MONTH);
        if (m <= 2) { y--; m += 12; }
        double jd = Math.floor(365.25 * (y + 4716)) + Math.floor(30.6001 * (m + 1)) + d - 1524.5;
        double daySince = jd - 2451549.5;
        double phase = daySince / 29.53058867;
        phase = phase - Math.floor(phase);
        return phase; // 0=new, 0.25=first quarter, 0.5=full, 0.75=last quarter
    }

    static String moonPhaseEmoji(double phase) {
        if (phase < 0.0625) return "\uD83C\uDF11"; // new
        if (phase < 0.1875) return "\uD83C\uDF12"; // waxing crescent
        if (phase < 0.3125) return "\uD83C\uDF13"; // first quarter
        if (phase < 0.4375) return "\uD83C\uDF14"; // waxing gibbous
        if (phase < 0.5625) return "\uD83C\uDF15"; // full
        if (phase < 0.6875) return "\uD83C\uDF16"; // waning gibbous
        if (phase < 0.8125) return "\uD83C\uDF17"; // last quarter
        if (phase < 0.9375) return "\uD83C\uDF18"; // waning crescent
        return "\uD83C\uDF11"; // new
    }

    static String moonPhaseName(double phase) {
        if (phase < 0.0625) return "Yeni Ay";
        if (phase < 0.1875) return "Hilal (Buyuyen)";
        if (phase < 0.3125) return "Ilk Dordun";
        if (phase < 0.4375) return "Buyuyen Ay";
        if (phase < 0.5625) return "Dolunay";
        if (phase < 0.6875) return "Kuculen Ay";
        if (phase < 0.8125) return "Son Dordun";
        if (phase < 0.9375) return "Hilal (Kuculen)";
        return "Yeni Ay";
    }

    // ==================== UI ====================
    private void initUI() {
        anaPanel = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                drawBackground(g2, getWidth(), getHeight());
                g2.dispose();
            }
        };
        anaPanel.setLayout(new BoxLayout(anaPanel, BoxLayout.Y_AXIS));
        anaPanel.setBorder(BorderFactory.createEmptyBorder(28, 32, 20, 32));

        // ===== TITLE =====
        JPanel titlePanel = new JPanel();
        titlePanel.setLayout(new BoxLayout(titlePanel, BoxLayout.Y_AXIS));
        titlePanel.setOpaque(false);
        titlePanel.setAlignmentX(Component.CENTER_ALIGNMENT);

        JPanel titleRow = new JPanel();
        titleRow.setLayout(new BoxLayout(titleRow, BoxLayout.X_AXIS));
        titleRow.setOpaque(false);
        titleRow.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel titleEmoji = new JLabel("\u2601\uFE0F");
        titleEmoji.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 34));
        JLabel titleText = shadowLabel("Hava Durumu", 30, Font.BOLD);
        titleRow.add(titleEmoji);
        titleRow.add(Box.createHorizontalStrut(10));
        titleRow.add(titleText);
        titlePanel.add(titleRow);
        titlePanel.add(Box.createVerticalStrut(4));

        JLabel subtitle = new JLabel("Sehir adini girin, hava durumunu ogreniniz");
        subtitle.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        subtitle.setForeground(TEXT_SECONDARY);
        subtitle.setAlignmentX(Component.CENTER_ALIGNMENT);
        titlePanel.add(subtitle);

        anaPanel.add(titlePanel);
        anaPanel.add(Box.createVerticalStrut(18));

        // ===== SEARCH BAR =====
        JPanel searchBar = glassPanel(14);
        searchBar.setLayout(new BorderLayout(10, 0));
        searchBar.setBorder(BorderFactory.createEmptyBorder(8, 14, 8, 8));
        searchBar.setMaximumSize(new Dimension(420, 50));
        searchBar.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel sIcon = new JLabel("\uD83D\uDD0D");
        sIcon.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 18));
        searchBar.add(sIcon, BorderLayout.WEST);

        sehirField = new JTextField("Istanbul");
        sehirField.setFont(new Font("Segoe UI", Font.PLAIN, 16));
        sehirField.setForeground(TEXT_PRIMARY);
        sehirField.setCaretColor(new Color(100, 200, 255));
        sehirField.setOpaque(false);
        sehirField.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        sehirField.setSelectionColor(new Color(100, 150, 255, 100));
        searchBar.add(sehirField, BorderLayout.CENTER);

        araButton = new JButton("Ara") {
            boolean hov = false, prs = false;
            { addMouseListener(new MouseAdapter() {
                public void mouseEntered(MouseEvent e) { hov=true; repaint(); }
                public void mouseExited(MouseEvent e) { hov=false; repaint(); }
                public void mousePressed(MouseEvent e) { prs=true; repaint(); }
                public void mouseReleased(MouseEvent e) { prs=false; repaint(); }
            }); }
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D)g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int w=getWidth(),h=getHeight();
                float sc = prs ? 0.94f : 1f;
                int dx=(int)(w*(1-sc)/2), dy=(int)(h*(1-sc)/2), sw=(int)(w*sc), sh=(int)(h*sc);
                if (hov) { g2.setColor(new Color(0,200,180,45)); g2.fillRoundRect(dx-3,dy-3,sw+6,sh+6,14,14); }
                g2.setPaint(new GradientPaint(0,dy,new Color(0,195,175),0,dy+sh,new Color(0,155,140)));
                g2.fillRoundRect(dx,dy,sw,sh,12,12);
                g2.setColor(Color.WHITE); g2.setFont(getFont());
                FontMetrics fm=g2.getFontMetrics();
                g2.drawString(getText(),(w-fm.stringWidth(getText()))/2,(h+fm.getAscent()-fm.getDescent())/2);
                g2.dispose();
            }
        };
        araButton.setFont(new Font("Segoe UI", Font.BOLD, 14));
        araButton.setForeground(Color.WHITE);
        araButton.setContentAreaFilled(false); araButton.setOpaque(false);
        araButton.setBorderPainted(false); araButton.setFocusPainted(false);
        araButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        araButton.setPreferredSize(new Dimension(78, 36));
        searchBar.add(araButton, BorderLayout.EAST);

        anaPanel.add(searchBar);
        anaPanel.add(Box.createVerticalStrut(18));

        // ===== MAIN CARD =====
        kartPanel = animatedGlassPanel();
        kartPanel.setLayout(new BoxLayout(kartPanel, BoxLayout.Y_AXIS));
        kartPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 18, 20));
        kartPanel.setMaximumSize(new Dimension(420, 310));
        kartPanel.setAlignmentX(Component.CENTER_ALIGNMENT);
        kartPanel.setVisible(false);

        sehirBilgiLabel = styledLabel("", 15, Font.BOLD, TEXT_PRIMARY);
        tarihLabel = styledLabel("", 12, Font.PLAIN, TEXT_SECONDARY);
        durumEmoji = styledLabel("", 72, Font.PLAIN, TEXT_PRIMARY);
        durumEmoji.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 72));
        sicaklikLabel = styledLabel("", 56, Font.BOLD, TEXT_PRIMARY);
        minMaxLabel = styledLabel("", 13, Font.PLAIN, TEXT_SECONDARY);
        hissedilenLabel = styledLabel("", 13, Font.PLAIN, TEXT_SECONDARY);
        durumLabel = styledLabel("", 18, Font.BOLD, accentColor);

        kartPanel.add(sehirBilgiLabel); kartPanel.add(Box.createVerticalStrut(2));
        kartPanel.add(tarihLabel); kartPanel.add(Box.createVerticalStrut(8));
        kartPanel.add(durumEmoji); kartPanel.add(sicaklikLabel);
        kartPanel.add(Box.createVerticalStrut(2));
        kartPanel.add(minMaxLabel); kartPanel.add(hissedilenLabel);
        kartPanel.add(Box.createVerticalStrut(5)); kartPanel.add(durumLabel);

        anaPanel.add(kartPanel);
        anaPanel.add(Box.createVerticalStrut(12));

        // ===== DETAIL GRID =====
        detayPanel = animatedGlassPanel();
        detayPanel.setLayout(new GridLayout(2, 2, 10, 10));
        detayPanel.setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));
        detayPanel.setMaximumSize(new Dimension(420, 160));
        detayPanel.setAlignmentX(Component.CENTER_ALIGNMENT);
        detayPanel.setVisible(false);

        nemLabel = addTile(detayPanel, "\uD83D\uDCA7", "Nem", "-");
        ruzgarLabel = addTile(detayPanel, "\uD83C\uDF2C\uFE0F", "Ruzgar", "-");
        basincLabel = addTile(detayPanel, "\uD83C\uDF21\uFE0F", "Basinc", "-");
        gorunurlukLabel = addTile(detayPanel, "\uD83D\uDC41\uFE0F", "Gorunurluk", "-");

        anaPanel.add(detayPanel);
        anaPanel.add(Box.createVerticalStrut(12));

        // ===== EXTRA INFO (sunrise/sunset/moon/uv) =====
        extraPanel = animatedGlassPanel();
        extraPanel.setLayout(new GridLayout(1, 4, 8, 0));
        extraPanel.setBorder(BorderFactory.createEmptyBorder(12, 10, 12, 10));
        extraPanel.setMaximumSize(new Dimension(420, 95));
        extraPanel.setAlignmentX(Component.CENTER_ALIGNMENT);
        extraPanel.setVisible(false);

        sunriseLabel = addTile(extraPanel, "\uD83C\uDF05", "Gun Dogumu", "-");
        sunsetLabel = addTile(extraPanel, "\uD83C\uDF07", "Gun Batimi", "-");
        moonLabel = addTile(extraPanel, "\uD83C\uDF15", "Ay Evresi", "-");
        uvLabel = addTile(extraPanel, "\u2600\uFE0F", "Gunduz", "-");

        anaPanel.add(extraPanel);
        anaPanel.add(Box.createVerticalGlue());

        JLabel footer = new JLabel("Powered by OpenWeatherMap", SwingConstants.CENTER);
        footer.setFont(new Font("Segoe UI", Font.ITALIC, 11));
        footer.setForeground(new Color(130, 155, 190, 140));
        footer.setAlignmentX(Component.CENTER_ALIGNMENT);
        anaPanel.add(footer);

        setContentPane(anaPanel);

        ActionListener act = e -> fetchWeather();
        araButton.addActionListener(act);
        sehirField.addActionListener(act);
    }

    // ========== UI HELPERS ==========
    private JPanel glassPanel(int r) {
        return new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2=(Graphics2D)g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int w=getWidth(),h=getHeight();
                g2.setColor(new Color(0,0,0,20)); g2.fillRoundRect(3,4,w-2,h-2,r+4,r+4);
                g2.setColor(GLASS_BG); g2.fillRoundRect(0,0,w,h,r+4,r+4);
                g2.setPaint(new GradientPaint(0,0,new Color(255,255,255,30),0,h*0.5f,new Color(255,255,255,0)));
                g2.fillRoundRect(0,0,w,h/2,r+4,r+4);
                g2.setColor(GLASS_BORDER); g2.setStroke(new BasicStroke(1f));
                g2.drawRoundRect(0,0,w-1,h-1,r+4,r+4);
                g2.dispose();
            }
        };
    }

    private JPanel animatedGlassPanel() {
        return new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2=(Graphics2D)g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int w=getWidth(),h=getHeight();
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.min(cardAlpha,1f)));
                g2.setColor(new Color(0,0,0,30)); g2.fillRoundRect(4,6,w-4,h-4,24,24);
                g2.setColor(GLASS_BG); g2.fillRoundRect(0,0,w,h,24,24);
                g2.setPaint(new GradientPaint(0,0,GLASS_HL,0,h*0.35f,new Color(255,255,255,0)));
                g2.fillRoundRect(0,0,w,(int)(h*0.35),24,24);
                g2.setColor(GLASS_BORDER); g2.setStroke(new BasicStroke(1.2f));
                g2.drawRoundRect(0,0,w-1,h-1,24,24);
                g2.dispose();
            }
        };
    }

    private JLabel shadowLabel(String text, int size, int style) {
        JLabel lbl = new JLabel(text) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2=(Graphics2D)g.create();
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g2.setFont(getFont()); FontMetrics fm=g2.getFontMetrics();
                int x=0, y=fm.getAscent()+1;
                g2.setColor(new Color(0,0,0,70)); g2.drawString(getText(),x+2,y+2);
                g2.setColor(getForeground()); g2.drawString(getText(),x,y);
                g2.dispose();
            }
        };
        lbl.setFont(new Font("Segoe UI", style, size));
        lbl.setForeground(TEXT_PRIMARY);
        lbl.setPreferredSize(new Dimension(260, size + 14));
        lbl.setMaximumSize(new Dimension(260, size + 14));
        return lbl;
    }

    private JLabel styledLabel(String text, int size, int style, Color c) {
        JLabel lbl = new JLabel(text, SwingConstants.CENTER);
        lbl.setFont(new Font("Segoe UI", style, size));
        lbl.setForeground(c);
        lbl.setAlignmentX(Component.CENTER_ALIGNMENT);
        return lbl;
    }

    private JLabel addTile(JPanel parent, String emoji, String title, String value) {
        JPanel tile = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2=(Graphics2D)g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(255,255,255,12)); g2.fillRoundRect(0,0,getWidth(),getHeight(),14,14);
                g2.setColor(new Color(255,255,255,22)); g2.setStroke(new BasicStroke(0.7f));
                g2.drawRoundRect(0,0,getWidth()-1,getHeight()-1,14,14);
                g2.dispose();
            }
        };
        tile.setOpaque(false);
        tile.setLayout(new BoxLayout(tile, BoxLayout.Y_AXIS));
        tile.setBorder(BorderFactory.createEmptyBorder(8, 4, 8, 4));

        JLabel em = new JLabel(emoji, SwingConstants.CENTER);
        em.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 18));
        em.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel ti = new JLabel(title, SwingConstants.CENTER);
        ti.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        ti.setForeground(TEXT_SECONDARY);
        ti.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel val = new JLabel(value, SwingConstants.CENTER);
        val.setFont(new Font("Segoe UI", Font.BOLD, 14));
        val.setForeground(TEXT_PRIMARY);
        val.setAlignmentX(Component.CENTER_ALIGNMENT);

        tile.add(em); tile.add(Box.createVerticalStrut(2));
        tile.add(ti); tile.add(Box.createVerticalStrut(1));
        tile.add(val);
        parent.add(tile);
        return val;
    }

    private void animateCards() {
        cardAlpha = 0f;
        if (animTimer != null && animTimer.isRunning()) animTimer.stop();
        animTimer = new Timer(16, null);
        animTimer.addActionListener(e -> {
            cardAlpha += 0.06f;
            if (cardAlpha >= 1f) { cardAlpha = 1f; animTimer.stop(); }
            kartPanel.repaint(); detayPanel.repaint(); extraPanel.repaint();
        });
        animTimer.start();
    }

    // ==================== API ====================
    private void fetchWeather() {
        String city = sehirField.getText().trim();
        if (city.isEmpty()) { showError("Lutfen bir sehir adi girin!"); return; }
        if (API_KEY.equals("API_ANAHTARINIZI_BURAYA_YAZIN")) { showError("Gecerli bir API anahtari girin!"); return; }
        if (yukleniyor) return;
        yukleniyor = true;
        araButton.setText("\u2022\u2022\u2022"); araButton.setEnabled(false);

        new Thread(() -> {
            try {
                String enc = URLEncoder.encode(city, StandardCharsets.UTF_8);
                String urlStr = BASE_URL + "?q=" + enc + "&appid=" + API_KEY + "&units=metric&lang=tr";
                URL url = new URI(urlStr).toURL();
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Accept", "application/json");
                conn.setConnectTimeout(10000); conn.setReadTimeout(10000);
                int code = conn.getResponseCode();
                if (code == 200) {
                    String json = readStream(conn.getInputStream());
                    SwingUtilities.invokeLater(() -> parseAndShow(json));
                } else {
                    String err = ""; try { err = readStream(conn.getErrorStream()); } catch (Exception ignored) {}
                    String msg = ps(err, "\"message\":\"");
                    final String dm;
                    if (code == 401) dm = "API Hatasi (401): " + (msg != null ? msg : "Gecersiz anahtar.");
                    else if (code == 404) dm = "Sehir bulunamadi: " + city;
                    else dm = "Hata (" + code + "): " + (msg != null ? msg : "Bilinmeyen");
                    SwingUtilities.invokeLater(() -> showError(dm));
                }
                conn.disconnect();
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> showError("Baglanti hatasi: " + ex.getMessage()));
            } finally {
                SwingUtilities.invokeLater(() -> { yukleniyor = false; araButton.setText("Ara"); araButton.setEnabled(true); });
            }
        }).start();
    }

    private String readStream(InputStream is) throws IOException {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder(); String line;
            while ((line = br.readLine()) != null) sb.append(line);
            return sb.toString();
        }
    }

    // ==================== PARSE & DISPLAY ====================
    private void parseAndShow(String json) {
        try {
            double temp = pd(json,"\"temp\":"), feelsLike = pd(json,"\"feels_like\":");
            double tMin = pd(json,"\"temp_min\":"), tMax = pd(json,"\"temp_max\":");
            int hum = pi(json,"\"humidity\":"), pres = pi(json,"\"pressure\":");
            double wind = pd(json,"\"speed\":"); int vis = pi(json,"\"visibility\":");
            String desc = ps(json,"\"description\":\""), cityName = ps(json,"\"name\":\"");
            String country = ps(json,"\"country\":\""), wMain = ps(json,"\"main\":\"");

            // Sunrise / Sunset
            long sr = (long) pd(json,"\"sunrise\":"); long ss = (long) pd(json,"\"sunset\":");
            int tz = pi(json, "\"timezone\":");
            sunriseEpoch = sr; sunsetEpoch = ss; tzOffset = tz;

            // Determine day/night
            long now = System.currentTimeMillis() / 1000;
            boolean night = now < sr || now > ss;

            updateTheme(wMain, temp, night);

            String emoji = weatherEmoji(wMain, night);

            kartPanel.setVisible(true); detayPanel.setVisible(true); extraPanel.setVisible(true);
            animateCards();

            sehirBilgiLabel.setText("<html><span style='font-family:Segoe UI Emoji'>\uD83D\uDCCD</span>  " + cityName + ", " + country + "</html>");
            tarihLabel.setText(new SimpleDateFormat("dd MMMM yyyy  \u2022  HH:mm", new Locale("tr")).format(new Date()));
            durumEmoji.setText(emoji);
            sicaklikLabel.setText(String.format("%.0f\u00B0", temp));
            minMaxLabel.setText(String.format("%.0f\u00B0 / %.0f\u00B0", tMax, tMin));
            hissedilenLabel.setText(String.format("Hissedilen: %.0f\u00B0C", feelsLike));
            if (desc != null && !desc.isEmpty()) desc = desc.substring(0,1).toUpperCase(new Locale("tr")) + desc.substring(1);
            durumLabel.setForeground(accentColor);
            durumLabel.setText(desc != null ? desc : "-");

            nemLabel.setText("%" + hum);
            ruzgarLabel.setText(String.format("%.1f m/s", wind));
            basincLabel.setText(pres + " hPa");
            gorunurlukLabel.setText(String.format("%.1f km", vis / 1000.0));

            // Sunrise / Sunset formatting
            SimpleDateFormat sdf = new SimpleDateFormat("HH:mm");
            sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
            sunriseLabel.setText(sdf.format(new Date((sr + tz) * 1000)));
            sunsetLabel.setText(sdf.format(new Date((ss + tz) * 1000)));

            // Moon
            double mPhase = getMoonPhase();
            moonLabel.setText(moonPhaseName(mPhase));
            // Update moon emoji in tile
            Component[] extraComps = extraPanel.getComponents();
            if (extraComps.length >= 3) {
                JPanel moonTile = (JPanel) extraComps[2];
                Component[] mc = moonTile.getComponents();
                if (mc.length > 0 && mc[0] instanceof JLabel) ((JLabel)mc[0]).setText(moonPhaseEmoji(mPhase));
            }

            // Day/Night indicator
            if (night) {
                uvLabel.setText("Gece");
                if (extraComps.length >= 4) {
                    JPanel uvTile = (JPanel) extraComps[3];
                    Component[] uc = uvTile.getComponents();
                    if (uc.length > 0 && uc[0] instanceof JLabel) ((JLabel)uc[0]).setText("\uD83C\uDF19");
                    if (uc.length > 1 && uc[1] instanceof JLabel) ((JLabel)uc[1]).setText("Durum");
                }
            } else {
                long dayLen = ss - sr;
                long h = dayLen / 3600;
                long m = (dayLen % 3600) / 60;
                uvLabel.setText(h + "s " + m + "dk");
                if (extraComps.length >= 4) {
                    JPanel uvTile = (JPanel) extraComps[3];
                    Component[] uc = uvTile.getComponents();
                    if (uc.length > 0 && uc[0] instanceof JLabel) ((JLabel)uc[0]).setText("\u2600\uFE0F");
                    if (uc.length > 1 && uc[1] instanceof JLabel) ((JLabel)uc[1]).setText("Gun Suresi");
                }
            }

            revalidate(); repaint();
        } catch (Exception e) {
            showError("Veri isleme hatasi: " + e.getMessage());
        }
    }

    private String weatherEmoji(String wm, boolean night) {
        if (wm == null) return "\u2601\uFE0F";
        return switch (wm.toLowerCase()) {
            case "clear" -> night ? "\uD83C\uDF19" : "\u2600\uFE0F";
            case "clouds" -> night ? "\uD83C\uDF19" : "\u2601\uFE0F";
            case "rain", "drizzle" -> "\uD83C\uDF27\uFE0F";
            case "thunderstorm" -> "\u26C8\uFE0F";
            case "snow" -> "\u2744\uFE0F";
            case "mist","fog","haze" -> "\uD83C\uDF2B\uFE0F";
            case "smoke","dust","sand","ash" -> "\uD83D\uDCA8";
            case "tornado" -> "\uD83C\uDF2A\uFE0F";
            default -> "\u2601\uFE0F";
        };
    }

    // ========== JSON HELPERS ==========
    private double pd(String j, String k) {
        int i = j.indexOf(k); if (i < 0) return 0;
        int s = i + k.length(); int e = s;
        while (e < j.length() && (Character.isDigit(j.charAt(e)) || j.charAt(e)=='.' || j.charAt(e)=='-')) e++;
        return Double.parseDouble(j.substring(s, e));
    }
    private int pi(String j, String k) { return (int) pd(j, k); }
    private String ps(String j, String k) {
        int i = j.indexOf(k); if (i < 0) return null;
        int s = i + k.length(); int e = j.indexOf("\"", s);
        return e < 0 ? null : j.substring(s, e);
    }

    private void showError(String msg) {
        kartPanel.setVisible(true); detayPanel.setVisible(false); extraPanel.setVisible(false);
        animateCards();
        sehirBilgiLabel.setText(""); tarihLabel.setText("");
        durumEmoji.setText("\u26A0\uFE0F"); sicaklikLabel.setText("");
        minMaxLabel.setText(""); hissedilenLabel.setText("");
        durumLabel.setForeground(new Color(255, 100, 100));
        durumLabel.setText("<html><div style='text-align:center;width:280px'>" + msg + "</div></html>");
        revalidate(); repaint();
    }

    public static void main(String[] args) {
        try { UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName()); } catch (Exception ignored) {}
        SwingUtilities.invokeLater(() -> new HavaDurumu().setVisible(true));
    }
}