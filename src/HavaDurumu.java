import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import javax.swing.event.*;
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
import java.util.Properties;
import java.util.TimeZone;

public class HavaDurumu extends JFrame {

    private static final String API_KEY = loadApiKey();

    private static String loadApiKey() {
        // 1. Ortam degiskeni
        String key = System.getenv("OPENWEATHER_API_KEY");
        if (key != null && !key.isBlank()) return key.trim();
        // 2. config.properties dosyasi - birden fazla konum dene
        String[] paths = {
            "config.properties",
            System.getProperty("user.dir") + File.separator + "config.properties"
        };
        // Ayrica class dosyasinin bulundugu dizinin ustunu de dene
        try {
            File classDir = new File(HavaDurumu.class.getProtectionDomain()
                .getCodeSource().getLocation().toURI());
            if (classDir.isDirectory()) {
                paths = new String[] {
                    paths[0], paths[1],
                    classDir.getParent() + File.separator + "config.properties",
                    classDir.getParentFile().getParent() + File.separator + "config.properties"
                };
            }
        } catch (Exception ignored) {}
        for (String path : paths) {
            File f = new File(path);
            if (f.exists()) {
                try (InputStreamReader reader = new InputStreamReader(
                        new FileInputStream(f), StandardCharsets.UTF_8)) {
                    Properties props = new Properties();
                    props.load(reader);
                    key = props.getProperty("api.key");
                    // BOM korumasi: ilk key BOM iceriyor olabilir
                    if (key == null) {
                        for (String k : props.stringPropertyNames()) {
                            if (k.endsWith("api.key")) {
                                key = props.getProperty(k);
                                break;
                            }
                        }
                    }
                    if (key != null && !key.isBlank()) return key.trim();
                } catch (IOException ignored) {}
            }
        }
        // 3. Bulunamazsa uyari
        JOptionPane.showMessageDialog(null,
            "API key bulunamadi!\n\n" +
            "1) OPENWEATHER_API_KEY ortam degiskeni tanimlayin\n" +
            "   veya\n" +
            "2) config.properties dosyasina api.key=XXXXX yazin\n\n" +
            "Ornek: config.properties.example dosyasina bakin.",
            "API Key Hatasi", JOptionPane.ERROR_MESSAGE);
        System.exit(1);
        return null;
    }

    private static final String BASE_URL = "https://api.openweathermap.org/data/2.5/weather";
    private static final Locale TR_LOCALE = Locale.of("tr");
    private static final String[] SEHIRLER;
    private static final String[] SEHIRLER_LOWER;
    static {
        List<String> list = new ArrayList<>();
        // sehirler.txt dosyasini oku
        String[] paths = { "sehirler.txt", System.getProperty("user.dir") + File.separator + "sehirler.txt" };
        try {
            File classDir = new File(HavaDurumu.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            if (classDir.isDirectory()) {
                paths = new String[] { paths[0], paths[1],
                    classDir.getParent() + File.separator + "sehirler.txt",
                    classDir.getParentFile().getParent() + File.separator + "sehirler.txt" };
            }
        } catch (Exception ignored) {}
        boolean loaded = false;
        for (String p : paths) {
            File f = new File(p);
            if (f.exists()) {
                try (var br = new java.io.BufferedReader(new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        line = line.trim();
                        if (!line.isEmpty()) list.add(line);
                    }
                    loaded = true;
                    break;
                } catch (IOException ignored) {}
            }
        }
        if (!loaded) {
            // Fallback: en azindan bazi sehirler
            String[] fallback = {"Istanbul","Ankara","Izmir","London","Paris","Berlin","New York","Tokyo"};
            for (String s : fallback) list.add(s);
        }
        SEHIRLER = list.toArray(new String[0]);
        SEHIRLER_LOWER = new String[SEHIRLER.length];
        for (int i = 0; i < SEHIRLER.length; i++)
            SEHIRLER_LOWER[i] = SEHIRLER[i].toLowerCase(TR_LOCALE);
    }

    // ========== DYNAMIC COLORS ==========
    private Color bgTop = new Color(15, 32, 65);
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
    private JLabel yagmurLabel, aqiLabel, ruzgarYonLabel, yarinLabel;
    private JPanel detayPanel2;
    private JPanel kartPanel, detayPanel, extraPanel, anaPanel, titlePanel;
    private JButton araButton;
    private boolean yukleniyor = false;
    private float cardAlpha = 0f;
    private Timer animTimer;

    // ========== WEATHER ANIMATION STATE ==========
    private final List<Particle> particles = new ArrayList<>();
    private String currentWeather = "default";
    private boolean isNight = false;
    private float animTick = 0f;
    private Timer bgAnimTimer;
    private float lightningAlpha = 0f;
    private int lightningCooldown = 0;
    private final Random rng = new Random();

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
        anaPanel.setBorder(BorderFactory.createEmptyBorder(20, 32, 20, 32));

        anaPanel.add(Box.createVerticalGlue());

        // ===== TITLE =====
        titlePanel = new JPanel();
        titlePanel.setLayout(new BoxLayout(titlePanel, BoxLayout.Y_AXIS));
        titlePanel.setOpaque(false);
        titlePanel.setAlignmentX(Component.CENTER_ALIGNMENT);

        JPanel weatherAnim = new JPanel() {
            private float phase = 0f; // 0-4 cycles through states
            private final Timer aTimer = new Timer(30, e -> { phase += 0.008f; if (phase >= 4f) phase = 0f; repaint(); });
            { aTimer.start(); setOpaque(false); }

            @Override public Dimension getPreferredSize() { return new Dimension(120, 100); }
            @Override public Dimension getMinimumSize() { return getPreferredSize(); }
            @Override public Dimension getMaximumSize() { return getPreferredSize(); }

            @Override protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int cx = getWidth() / 2, cy = getHeight() / 2;

                int state = (int) phase;
                float t = phase - state; // 0..1 transition within state
                float fadeIn = Math.min(t * 4f, 1f);
                float fadeOut = Math.max(0f, (t - 0.75f) * 4f);

                switch (state) {
                    case 0 -> { // SUN
                        drawSunIcon(g2, cx, cy, 1f - fadeOut);
                        if (fadeOut > 0) { drawCloudPartial(g2, cx + 6, cy + 4, fadeOut, false); drawSunIcon(g2, cx - 10, cy - 8, fadeOut * 0.6f); }
                    }
                    case 1 -> { // SUN + CLOUD -> RAIN CLOUD
                        float cloudAlpha = 1f - fadeOut * 0.3f;
                        drawSunIcon(g2, cx - 10, cy - 8, Math.max(0.3f, 0.6f - fadeOut * 0.6f));
                        drawCloudPartial(g2, cx + 6, cy + 4, cloudAlpha, false);
                        if (fadeOut > 0) drawRainDrops(g2, cx, cy, fadeOut);
                    }
                    case 2 -> { // RAIN CLOUD
                        drawCloudPartial(g2, cx, cy - 4, 1f, false);
                        drawRainDrops(g2, cx, cy - 4, 1f - fadeOut * 0.3f);
                        if (fadeOut > 0) drawLightningIcon(g2, cx, cy, fadeOut);
                    }
                    case 3 -> { // THUNDERSTORM -> back to sun
                        float stormFade = 1f - fadeOut;
                        drawCloudPartial(g2, cx, cy - 4, stormFade, true);
                        drawRainDrops(g2, cx, cy - 4, stormFade * 0.7f);
                        drawLightningIcon(g2, cx, cy, stormFade);
                        if (fadeOut > 0) drawSunIcon(g2, cx, cy, fadeOut);
                    }
                }
                g2.dispose();
            }

            void drawSunIcon(Graphics2D g2, int cx, int cy, float alpha) {
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.min(alpha, 1f)));
                float pulse = (float)(Math.sin(phase * Math.PI * 2) * 2);
                int r = (int)(16 + pulse);
                // Glow
                for (int i = 3; i >= 0; i--) {
                    int gr = r + i * 6;
                    g2.setColor(new Color(255, 200, 50, (int)(20 * alpha)));
                    g2.fill(new Ellipse2D.Float(cx - gr, cy - gr, gr * 2, gr * 2));
                }
                // Rays
                g2.setColor(new Color(255, 220, 80, (int)(120 * alpha)));
                g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                for (int i = 0; i < 8; i++) {
                    double a = Math.PI * 2 * i / 8 + phase * 0.5;
                    float x1 = cx + (float)Math.cos(a) * (r + 4);
                    float y1 = cy + (float)Math.sin(a) * (r + 4);
                    float x2 = cx + (float)Math.cos(a) * (r + 10);
                    float y2 = cy + (float)Math.sin(a) * (r + 10);
                    g2.draw(new Line2D.Float(x1, y1, x2, y2));
                }
                // Core
                RadialGradientPaint sp = new RadialGradientPaint(
                    new Point2D.Float(cx, cy), r,
                    new float[]{0f, 0.6f, 1f},
                    new Color[]{new Color(255, 255, 210), new Color(255, 200, 50), new Color(255, 160, 0, 0)}
                );
                g2.setPaint(sp);
                g2.fill(new Ellipse2D.Float(cx - r, cy - r, r * 2, r * 2));
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1f));
            }

            void drawCloudPartial(Graphics2D g2, int cx, int cy, float alpha, boolean dark) {
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.min(alpha, 1f)));
                Color c1 = dark ? new Color(80, 85, 100) : new Color(200, 215, 235);
                Color c2 = dark ? new Color(55, 58, 72) : new Color(170, 190, 215);
                g2.setColor(c2);
                g2.fill(new Ellipse2D.Float(cx - 22, cy - 6, 24, 20));
                g2.fill(new Ellipse2D.Float(cx + 4, cy - 2, 20, 16));
                g2.setColor(c1);
                g2.fill(new Ellipse2D.Float(cx - 14, cy - 16, 28, 24));
                g2.fill(new Ellipse2D.Float(cx - 26, cy - 2, 52, 18));
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1f));
            }

            void drawRainDrops(Graphics2D g2, int cx, int cy, float alpha) {
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.min(alpha, 1f)));
                g2.setColor(new Color(100, 170, 255));
                g2.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                float dropPhase = (phase * 12f) % 1f;
                for (int i = 0; i < 5; i++) {
                    float dx = cx - 16 + i * 8;
                    float dy = cy + 14 + ((dropPhase + i * 0.2f) % 1f) * 14;
                    g2.draw(new Line2D.Float(dx, dy, dx - 2, dy + 6));
                }
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1f));
            }

            void drawLightningIcon(Graphics2D g2, int cx, int cy, float alpha) {
                float flicker = (float)(Math.sin(phase * 40) * 0.3 + 0.7);
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.min(alpha * flicker, 1f)));
                g2.setColor(new Color(255, 235, 60));
                GeneralPath bolt = new GeneralPath();
                bolt.moveTo(cx + 2, cy + 10);
                bolt.lineTo(cx - 4, cy + 22);
                bolt.lineTo(cx, cy + 20);
                bolt.lineTo(cx - 3, cy + 32);
                bolt.lineTo(cx + 6, cy + 18);
                bolt.lineTo(cx + 2, cy + 20);
                bolt.closePath();
                g2.fill(bolt);
                // Glow
                g2.setColor(new Color(255, 255, 150, (int)(40 * alpha * flicker)));
                g2.fill(new Ellipse2D.Float(cx - 10, cy + 14, 20, 20));
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1f));
            }
        };
        weatherAnim.setAlignmentX(Component.CENTER_ALIGNMENT);
        titlePanel.add(weatherAnim);

        JLabel titleText = shadowLabel("G\u00D6KY\u00DCZ\u00DC NASIL OLACAKMI\u015E B\u00DCG\u00DCN ?", 24, Font.BOLD);
        titleText.setAlignmentX(Component.CENTER_ALIGNMENT);
        titleText.setHorizontalAlignment(SwingConstants.CENTER);
        titlePanel.add(titleText);
        titlePanel.add(Box.createVerticalStrut(4));

        JLabel subtitle = new JLabel("\u015Eehrini yaz ve G\u00F6ky\u00FCz\u00FCn\u00FCn senin i\u00E7in olan plan\u0131n\u0131 \u00F6\u011Fren.");
        subtitle.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        subtitle.setForeground(TEXT_SECONDARY);
        subtitle.setAlignmentX(Component.CENTER_ALIGNMENT);
        titlePanel.add(subtitle);

        anaPanel.add(titlePanel);
        anaPanel.add(Box.createVerticalStrut(30));

        // ===== SEARCH BAR =====
        JPanel searchBar = glassPanel(14);
        searchBar.setLayout(new BorderLayout(10, 0));
        searchBar.setBorder(BorderFactory.createEmptyBorder(8, 14, 8, 8));
        searchBar.setMaximumSize(new Dimension(420, 50));
        searchBar.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel sIcon = new JLabel("\uD83D\uDD0D");
        sIcon.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 18));
        searchBar.add(sIcon, BorderLayout.WEST);

        sehirField = new JTextField();
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

        // City autocomplete popup - overlay panel in layered pane
        JPanel suggestPanel = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int w = getWidth(), h = getHeight();
                // Solid dark background so content behind doesn't bleed through
                g2.setColor(new Color(18, 30, 55, 240));
                g2.fillRoundRect(0, 0, w, h, 18, 18);
                g2.setPaint(new GradientPaint(0, 0, new Color(255,255,255,22), 0, h * 0.4f, new Color(255,255,255,0)));
                g2.fillRoundRect(0, 0, w, h / 2, 18, 18);
                g2.setColor(GLASS_BORDER);
                g2.setStroke(new BasicStroke(1f));
                g2.drawRoundRect(0, 0, w - 1, h - 1, 18, 18);
                g2.dispose();
            }
        };
        suggestPanel.setOpaque(true);
        suggestPanel.setLayout(new BoxLayout(suggestPanel, BoxLayout.Y_AXIS));
        suggestPanel.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        suggestPanel.setVisible(false);
        getRootPane().getLayeredPane().add(suggestPanel, JLayeredPane.POPUP_LAYER);
        final int[] selectedIdx = {-1}; // keyboard navigation index

        // Keyboard navigation: arrow keys + enter on suggestPanel
        sehirField.addKeyListener(new java.awt.event.KeyAdapter() {
            @Override public void keyPressed(java.awt.event.KeyEvent e) {
                if (!suggestPanel.isVisible() || suggestPanel.getComponentCount() == 0) return;
                int cnt = suggestPanel.getComponentCount();
                if (e.getKeyCode() == java.awt.event.KeyEvent.VK_DOWN) {
                    e.consume();
                    selectedIdx[0] = Math.min(selectedIdx[0] + 1, cnt - 1);
                    highlightSuggestItem(suggestPanel, selectedIdx[0]);
                } else if (e.getKeyCode() == java.awt.event.KeyEvent.VK_UP) {
                    e.consume();
                    selectedIdx[0] = Math.max(selectedIdx[0] - 1, 0);
                    highlightSuggestItem(suggestPanel, selectedIdx[0]);
                } else if (e.getKeyCode() == java.awt.event.KeyEvent.VK_ENTER && selectedIdx[0] >= 0) {
                    e.consume();
                    Component c = suggestPanel.getComponent(selectedIdx[0]);
                    if (c instanceof JLabel lbl) {
                        // Simulate click
                        for (var ml : lbl.getMouseListeners()) {
                            ml.mousePressed(new MouseEvent(lbl, MouseEvent.MOUSE_PRESSED, System.currentTimeMillis(), 0, 0, 0, 1, false));
                        }
                    }
                    selectedIdx[0] = -1;
                } else if (e.getKeyCode() == java.awt.event.KeyEvent.VK_ESCAPE) {
                    e.consume();
                    suggestPanel.setVisible(false);
                    selectedIdx[0] = -1;
                }
            }
        });

        // Instant static + async API suggestions
        sehirField.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { scheduleSearch(); }
            public void removeUpdate(DocumentEvent e) { scheduleSearch(); }
            public void changedUpdate(DocumentEvent e) { scheduleSearch(); }
            private void scheduleSearch() {
                SwingUtilities.invokeLater(() -> {
                    String text = sehirField.getText().trim();
                    if (text.length() < 1) { suggestPanel.setVisible(false); selectedIdx[0] = -1; return; }
                    String lower = text.toLowerCase(TR_LOCALE);
                    List<String> staticResults = new ArrayList<>();
                    for (int i = 0; i < SEHIRLER.length && staticResults.size() < 8; i++) {
                        if (SEHIRLER_LOWER[i].startsWith(lower))
                            staticResults.add(SEHIRLER[i] + "|" + SEHIRLER[i]);
                    }
                    if (!staticResults.isEmpty()) showGeoSuggestions(staticResults, suggestPanel, sehirField);
                    // Also search with contains for broader results
                    if (staticResults.isEmpty() && text.length() >= 2) {
                        for (int i = 0; i < SEHIRLER.length && staticResults.size() < 8; i++) {
                            if (SEHIRLER_LOWER[i].contains(lower))
                                staticResults.add(SEHIRLER[i] + "|" + SEHIRLER[i]);
                        }
                        if (!staticResults.isEmpty()) showGeoSuggestions(staticResults, suggestPanel, sehirField);
                    }
                    if (staticResults.isEmpty()) { suggestPanel.setVisible(false); selectedIdx[0] = -1; }
                });
            }
        });

        anaPanel.add(searchBar);
        anaPanel.add(Box.createVerticalStrut(18));

        // ===== MAIN CARD =====
        kartPanel = animatedGlassPanel();
        kartPanel.setLayout(new BoxLayout(kartPanel, BoxLayout.Y_AXIS));
        kartPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 18, 20));
        kartPanel.setMaximumSize(new Dimension(420, 320));
        kartPanel.setAlignmentX(Component.CENTER_ALIGNMENT);
        kartPanel.setVisible(false);

        sehirBilgiLabel = styledLabel("", 15, Font.BOLD, TEXT_PRIMARY);
        sehirBilgiLabel.setFont(new Font("Segoe UI Emoji", Font.BOLD, 15));
        sehirBilgiLabel.setHorizontalAlignment(SwingConstants.CENTER);
        tarihLabel = styledLabel("", 12, Font.PLAIN, TEXT_SECONDARY);
        durumEmoji = glowEmojiLabel("", 72);
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

        // ===== DETAIL GRID 2 (Yagis, AQI, Ruzgar Yonu, Yarin) =====
        detayPanel2 = animatedGlassPanel();
        detayPanel2.setLayout(new GridLayout(2, 2, 10, 10));
        detayPanel2.setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));
        detayPanel2.setMaximumSize(new Dimension(420, 160));
        detayPanel2.setAlignmentX(Component.CENTER_ALIGNMENT);
        detayPanel2.setVisible(false);

        yagmurLabel = addTile(detayPanel2, "\u2614", "Yagis Ihtimali", "-");
        aqiLabel = addTile(detayPanel2, "\uD83C\uDF43", "Hava Kalitesi", "-");
        ruzgarYonLabel = addTile(detayPanel2, "\uD83E\uDDED", "Ruzgar Yonu", "-");
        yarinLabel = addTile(detayPanel2, "\uD83D\uDD2E", "Yarin", "-");

        anaPanel.add(detayPanel2);
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
        uvLabel = addTile(extraPanel, "\u2600\uFE0F", "UV Endeksi", "-");

        anaPanel.add(extraPanel);
        anaPanel.add(Box.createVerticalGlue());
        anaPanel.add(Box.createVerticalGlue());

        JLabel footer = new JLabel("Powered by OpenWeatherMap", SwingConstants.CENTER);
        footer.setFont(new Font("Segoe UI", Font.ITALIC, 11));
        footer.setForeground(new Color(130, 155, 190, 140));
        footer.setAlignmentX(Component.CENTER_ALIGNMENT);
        anaPanel.add(footer);

        JScrollPane scrollPane = new JScrollPane(anaPanel) {
            @Override protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                drawBackground(g2, getWidth(), getHeight());
                g2.dispose();
            }
        };
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setBorder(null);
        scrollPane.getViewport().setOpaque(false);
        scrollPane.setOpaque(false);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        scrollPane.getVerticalScrollBar().setOpaque(false);
        scrollPane.getVerticalScrollBar().setPreferredSize(new Dimension(0, 0));
        setContentPane(scrollPane);

        ActionListener act = e -> { if (selectedIdx[0] < 0) { suggestPanel.setVisible(false); fetchWeather(); } };
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
        JLabel lbl = new JLabel(text, SwingConstants.CENTER) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2=(Graphics2D)g.create();
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g2.setFont(getFont()); FontMetrics fm=g2.getFontMetrics();
                int tw=fm.stringWidth(getText());
                int x=(getWidth()-tw)/2, y=fm.getAscent()+1;
                g2.setColor(new Color(0,0,0,70)); g2.drawString(getText(),x+2,y+2);
                g2.setColor(getForeground()); g2.drawString(getText(),x,y);
                g2.dispose();
            }
            @Override public Dimension getPreferredSize() {
                FontMetrics fm = getFontMetrics(getFont());
                return new Dimension(fm.stringWidth(getText()) + 10, fm.getHeight() + 8);
            }
        };
        lbl.setFont(new Font("Segoe UI", style, size));
        lbl.setForeground(TEXT_PRIMARY);
        lbl.setAlignmentX(Component.CENTER_ALIGNMENT);
        return lbl;
    }

    private JLabel styledLabel(String text, int size, int style, Color c) {
        JLabel lbl = new JLabel(text, SwingConstants.CENTER);
        lbl.setFont(new Font("Segoe UI", style, size));
        lbl.setForeground(c);
        lbl.setAlignmentX(Component.CENTER_ALIGNMENT);
        return lbl;
    }

    private JLabel glowEmojiLabel(String emoji, int fontSize) {
        JLabel lbl = new JLabel(emoji, SwingConstants.CENTER) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                String e = getText();
                if (e != null && !e.isEmpty()) drawColorEmoji(g2, e, getWidth()/2, getHeight()/2, Math.min(getWidth(), getHeight()));
                g2.dispose();
            }
        };
        lbl.setAlignmentX(Component.CENTER_ALIGNMENT);
        Dimension sz = new Dimension(fontSize * 2, (int)(fontSize * 1.6));
        lbl.setPreferredSize(sz);
        lbl.setMinimumSize(sz);
        lbl.setMaximumSize(new Dimension(fontSize * 3, (int)(fontSize * 1.8)));
        return lbl;
    }

    private void drawColorEmoji(Graphics2D g2, String emoji, int cx, int cy, int size) {
        int r = size / 2;
        switch (emoji) {
            case "\u2600\uFE0F", "\u2600" -> drawSunE(g2, cx, cy, r);
            case "\uD83C\uDF19" -> drawCrescentE(g2, cx, cy, r);
            case "\u2601\uFE0F", "\u2601" -> drawCloudE(g2, cx, cy, r);
            case "\uD83C\uDF27\uFE0F", "\uD83C\uDF27" -> { drawCloudE(g2, cx, cy - r/4, (int)(r * 0.75)); drawDropsE(g2, cx, cy + r/3, r); }
            case "\u26C8\uFE0F", "\u26C8" -> { drawCloudE(g2, cx, cy - r/4, (int)(r * 0.7)); drawBoltE(g2, cx, cy + r/6, r); }
            case "\u2744\uFE0F", "\u2744" -> drawSnowflakeE(g2, cx, cy, r);
            case "\uD83C\uDF2B\uFE0F", "\uD83C\uDF2B" -> drawFogE(g2, cx, cy, r);
            case "\uD83D\uDCA8" -> drawWindCurvesE(g2, cx, cy, r);
            case "\uD83C\uDF2A\uFE0F", "\uD83C\uDF2A" -> drawTornadoE(g2, cx, cy, r);
            case "\uD83D\uDCA7" -> drawDropE(g2, cx, cy, r, new Color(66, 165, 245));
            case "\uD83C\uDF2C\uFE0F", "\uD83C\uDF2C" -> drawWindCurvesE(g2, cx, cy, r);
            case "\uD83C\uDF21\uFE0F", "\uD83C\uDF21" -> drawThermometerE(g2, cx, cy, r);
            case "\uD83D\uDC41\uFE0F", "\uD83D\uDC41" -> drawEyeE(g2, cx, cy, r);
            case "\uD83C\uDF05" -> drawHorizonSunE(g2, cx, cy, r, new Color(255, 183, 77), new Color(255, 213, 79));
            case "\uD83C\uDF07" -> drawHorizonSunE(g2, cx, cy, r, new Color(255, 112, 67), new Color(255, 152, 0));
            case "\u26A0\uFE0F", "\u26A0" -> drawWarningE(g2, cx, cy, r);
            case "\u2614\uFE0F", "\u2614" -> drawUmbrellaE(g2, cx, cy, r);
            case "\uD83C\uDF43" -> drawLeafE(g2, cx, cy, r);
            case "\uD83E\uDDED" -> drawCompassE(g2, cx, cy, r);
            case "\uD83D\uDD2E" -> drawCrystalBallE(g2, cx, cy, r);
            default -> {
                if (emoji.length() == 2 && emoji.charAt(0) == '\uD83C') {
                    int lo = emoji.charAt(1);
                    if (lo >= 0xDF11 && lo <= 0xDF18) { drawMoonPhaseE(g2, cx, cy, r, lo - 0xDF11); return; }
                }
                g2.setFont(new Font("Segoe UI Emoji", Font.PLAIN, r));
                g2.setColor(new Color(200, 215, 230));
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(emoji, cx - fm.stringWidth(emoji) / 2, cy + fm.getAscent() / 3);
            }
        }
    }

    private void drawSunE(Graphics2D g2, int cx, int cy, int r) {
        int sr = r * 2 / 5;
        g2.setColor(new Color(255, 200, 50, 40));
        g2.fillOval(cx - r * 2 / 3, cy - r * 2 / 3, r * 4 / 3, r * 4 / 3);
        g2.setColor(new Color(255, 183, 77));
        g2.setStroke(new BasicStroke(Math.max(1.5f, r / 8f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (int i = 0; i < 8; i++) {
            double a = Math.PI * 2 * i / 8;
            g2.drawLine(cx + (int)(sr * 1.4 * Math.cos(a)), cy + (int)(sr * 1.4 * Math.sin(a)),
                         cx + (int)(sr * 2.1 * Math.cos(a)), cy + (int)(sr * 2.1 * Math.sin(a)));
        }
        g2.setColor(new Color(255, 213, 79));
        g2.fillOval(cx - sr, cy - sr, sr * 2, sr * 2);
        g2.setColor(new Color(255, 248, 210, 140));
        g2.fillOval(cx - sr / 3, cy - sr * 2 / 3, (int)(sr * 0.7), (int)(sr * 0.45));
    }

    private void drawCrescentE(Graphics2D g2, int cx, int cy, int r) {
        int mr = r * 2 / 3;
        g2.setColor(new Color(255, 235, 100, 30));
        g2.fillOval(cx - mr - 3, cy - mr - 3, mr * 2 + 6, mr * 2 + 6);
        Area crescent = new Area(new Ellipse2D.Float(cx - mr, cy - mr, mr * 2, mr * 2));
        crescent.subtract(new Area(new Ellipse2D.Float(cx - mr * 0.3f, cy - mr, mr * 2, mr * 2)));
        g2.setColor(new Color(255, 235, 100));
        g2.fill(crescent);
    }

    private void drawCloudE(Graphics2D g2, int cx, int cy, int r) {
        int w = (int)(r * 1.4);
        g2.setColor(new Color(200, 215, 235));
        g2.fillOval(cx - w / 2, cy - r / 4, (int)(w * 0.5), (int)(r * 0.6));
        g2.fillOval(cx + w / 8, cy - r / 6, (int)(w * 0.4), (int)(r * 0.5));
        g2.setColor(new Color(220, 232, 248));
        g2.fillOval(cx - w / 4, cy - r / 2, (int)(w * 0.55), (int)(r * 0.65));
        g2.fillOval(cx - w / 2 - r / 10, cy - r / 6, w + r / 5, (int)(r * 0.5));
    }

    private void drawDropsE(Graphics2D g2, int cx, int cy, int r) {
        g2.setColor(new Color(100, 181, 246));
        g2.setStroke(new BasicStroke(Math.max(1.2f, r / 8f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (int i = 0; i < 3; i++) {
            int dx = cx - r / 3 + i * r / 3, dy = cy + (i % 2 == 0 ? 0 : r / 6);
            g2.drawLine(dx, dy, dx - r / 10, dy + r / 4);
        }
    }

    private void drawBoltE(Graphics2D g2, int cx, int cy, int r) {
        g2.setColor(new Color(255, 235, 59));
        GeneralPath b = new GeneralPath();
        b.moveTo(cx + r * 0.05, cy); b.lineTo(cx - r * 0.15, cy + r * 0.3);
        b.lineTo(cx - r * 0.02, cy + r * 0.25); b.lineTo(cx - r * 0.2, cy + r * 0.55);
        b.lineTo(cx + r * 0.08, cy + r * 0.28); b.lineTo(cx - r * 0.04, cy + r * 0.32);
        b.closePath(); g2.fill(b);
    }

    private void drawSnowflakeE(Graphics2D g2, int cx, int cy, int r) {
        g2.setColor(new Color(144, 202, 249));
        g2.setStroke(new BasicStroke(Math.max(1.5f, r / 7f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        int sr = r * 2 / 3;
        for (int i = 0; i < 6; i++) {
            double a = Math.PI * 2 * i / 6;
            g2.drawLine(cx, cy, cx + (int)(sr * Math.cos(a)), cy + (int)(sr * Math.sin(a)));
            for (int d = -1; d <= 1; d += 2) {
                double ba = a + d * Math.PI / 6;
                int ox = cx + (int)(sr * 0.6 * Math.cos(a)), oy = cy + (int)(sr * 0.6 * Math.sin(a));
                g2.drawLine(ox, oy, ox + (int)(sr * 0.3 * Math.cos(ba)), oy + (int)(sr * 0.3 * Math.sin(ba)));
            }
        }
    }

    private void drawFogE(Graphics2D g2, int cx, int cy, int r) {
        g2.setStroke(new BasicStroke(Math.max(2f, r / 5f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (int i = 0; i < 3; i++) {
            g2.setColor(new Color(176, 190, 197, 200 - i * 50));
            g2.drawLine(cx - r / 2, cy - r / 3 + i * r / 3, cx + r / 2, cy - r / 3 + i * r / 3);
        }
    }

    private void drawWindCurvesE(Graphics2D g2, int cx, int cy, int r) {
        g2.setColor(new Color(144, 202, 249));
        g2.setStroke(new BasicStroke(Math.max(1.5f, r / 7f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (int i = 0; i < 3; i++) {
            int y = cy - r / 3 + i * r / 3;
            g2.draw(new QuadCurve2D.Float(cx - r / 2, y, cx, y - r / 5 + i * r / 8, cx + r / 2, y));
        }
    }

    private void drawTornadoE(Graphics2D g2, int cx, int cy, int r) {
        g2.setColor(new Color(176, 190, 197));
        g2.setStroke(new BasicStroke(Math.max(1.5f, r / 8f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        int[] ws = {r * 3 / 4, r / 2, r / 3, r / 5};
        for (int i = 0; i < ws.length; i++)
            g2.drawLine(cx - ws[i] / 2, cy - r / 3 + i * r / 4, cx + ws[i] / 2, cy - r / 3 + i * r / 4);
    }

    private void drawDropE(Graphics2D g2, int cx, int cy, int r, Color c) {
        int d = r * 2 / 3;
        GeneralPath p = new GeneralPath();
        p.moveTo(cx, cy - d * 0.8); p.curveTo(cx - d, cy + d * 0.2, cx - d * 0.7, cy + d * 1.1, cx, cy + d * 1.1);
        p.curveTo(cx + d * 0.7, cy + d * 1.1, cx + d, cy + d * 0.2, cx, cy - d * 0.8);
        p.closePath();
        g2.setColor(c); g2.fill(p);
        g2.setColor(new Color(255, 255, 255, 80));
        g2.fillOval(cx - d / 4, (int)(cy - d * 0.1), Math.max(d / 3, 1), Math.max(d / 3, 1));
    }

    private void drawThermometerE(Graphics2D g2, int cx, int cy, int r) {
        int tw = Math.max(r / 4, 2), th = r;
        g2.setColor(new Color(224, 224, 224));
        g2.fillRoundRect(cx - tw / 2, cy - th, tw, (int)(th * 1.4), tw, tw);
        g2.setColor(new Color(239, 83, 80));
        g2.fillOval(cx - tw, (int)(cy + th * 0.15), tw * 2, tw * 2);
        g2.fillRect(cx - tw / 4, (int)(cy - th * 0.5), Math.max(tw / 2, 1), (int)(th * 0.75));
    }

    private void drawEyeE(Graphics2D g2, int cx, int cy, int r) {
        int ew = r, eh = r / 2;
        g2.setColor(new Color(255, 255, 255, 180));
        g2.fillOval(cx - ew, cy - eh, ew * 2, eh * 2);
        g2.setColor(new Color(66, 133, 244));
        int ir = eh * 2 / 3; g2.fillOval(cx - ir, cy - ir, ir * 2, ir * 2);
        g2.setColor(new Color(30, 30, 30));
        int pr = ir / 2; g2.fillOval(cx - pr, cy - pr, pr * 2, pr * 2);
        g2.setColor(new Color(255, 255, 255, 200));
        g2.fillOval(cx - pr + pr / 2, cy - pr + pr / 4, Math.max(pr / 2, 1), Math.max(pr / 2, 1));
    }

    private void drawHorizonSunE(Graphics2D g2, int cx, int cy, int r, Color c1, Color c2) {
        int sr = r / 2;
        g2.setColor(new Color(c1.getRed(), c1.getGreen(), c1.getBlue(), 40));
        g2.fillOval(cx - sr - 4, cy - sr - 4, sr * 2 + 8, sr * 2 + 8);
        Shape oc = g2.getClip();
        g2.clipRect(cx - r, cy - r, r * 2, r);
        g2.setColor(c2); g2.fillOval(cx - sr, cy - sr, sr * 2, sr * 2);
        g2.setColor(c1);
        g2.setStroke(new BasicStroke(Math.max(1f, r / 10f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (int i = 0; i < 5; i++) {
            double a = Math.PI + Math.PI * i / 4;
            g2.drawLine(cx + (int)(sr * 1.3 * Math.cos(a)), cy + (int)(sr * 1.3 * Math.sin(a)),
                         cx + (int)(sr * 2.0 * Math.cos(a)), cy + (int)(sr * 2.0 * Math.sin(a)));
        }
        g2.setClip(oc);
        g2.setColor(c1);
        g2.setStroke(new BasicStroke(Math.max(1.5f, r / 8f)));
        g2.drawLine(cx - r * 3 / 4, cy, cx + r * 3 / 4, cy);
    }

    private void drawWarningE(Graphics2D g2, int cx, int cy, int r) {
        int tr = r * 3 / 4;
        GeneralPath t = new GeneralPath();
        t.moveTo(cx, cy - tr); t.lineTo(cx - tr, cy + tr * 2 / 3); t.lineTo(cx + tr, cy + tr * 2 / 3); t.closePath();
        g2.setColor(new Color(255, 193, 7)); g2.fill(t);
        g2.setColor(new Color(50, 50, 50));
        g2.setFont(new Font("Segoe UI", Font.BOLD, r * 2 / 3));
        FontMetrics fm = g2.getFontMetrics();
        g2.drawString("!", cx - fm.stringWidth("!") / 2, cy + tr / 4);
    }

    private void drawUmbrellaE(Graphics2D g2, int cx, int cy, int r) {
        int ur = r * 2 / 3;
        // Umbrella dome
        g2.setColor(new Color(100, 149, 237));
        g2.fillArc(cx - ur, cy - ur, ur * 2, ur * 2, 0, 180);
        // Dome edge scallops
        g2.setColor(new Color(70, 120, 210));
        int scallops = 5;
        for (int i = 0; i < scallops; i++) {
            int sx = cx - ur + i * ur * 2 / scallops;
            g2.fillArc(sx, cy - ur / 6, ur * 2 / scallops, ur / 3, 0, -180);
        }
        // Handle
        g2.setStroke(new BasicStroke(Math.max(2f, r / 6f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.setColor(new Color(180, 140, 100));
        g2.drawLine(cx, cy, cx, cy + ur * 2 / 3);
        g2.drawArc(cx - ur / 5, cy + ur / 3 + ur / 6, ur * 2 / 5, ur / 3, 0, -180);
        // Rain drops
        g2.setColor(new Color(100, 181, 246, 180));
        g2.setStroke(new BasicStroke(Math.max(1.2f, r / 10f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.drawLine(cx - ur + 4, cy + ur / 4, cx - ur + 2, cy + ur / 2);
        g2.drawLine(cx + ur - 4, cy + ur / 6, cx + ur - 6, cy + ur / 3 + 4);
        g2.drawLine(cx - ur / 3, cy + ur / 2, cx - ur / 3 - 2, cy + ur * 3 / 4);
    }

    private void drawLeafE(Graphics2D g2, int cx, int cy, int r) {
        int lr = r * 2 / 3;
        // Leaf body
        GeneralPath leaf = new GeneralPath();
        leaf.moveTo(cx, cy - lr);
        leaf.curveTo(cx + lr * 1.2, cy - lr * 0.5, cx + lr * 1.2, cy + lr * 0.5, cx, cy + lr);
        leaf.curveTo(cx - lr * 1.2, cy + lr * 0.5, cx - lr * 1.2, cy - lr * 0.5, cx, cy - lr);
        leaf.closePath();
        // Gradient fill
        GradientPaint gp = new GradientPaint(cx - lr, cy - lr, new Color(102, 187, 106), cx + lr, cy + lr, new Color(56, 142, 60));
        g2.setPaint(gp);
        g2.fill(leaf);
        // Central vein
        g2.setColor(new Color(46, 125, 50));
        g2.setStroke(new BasicStroke(Math.max(1.2f, r / 9f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.drawLine(cx, cy - lr + 3, cx, cy + lr - 3);
        // Side veins
        g2.setStroke(new BasicStroke(Math.max(0.8f, r / 12f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (int i = -2; i <= 2; i++) {
            if (i == 0) continue;
            int vy = cy + i * lr / 3;
            int sign = i > 0 ? 1 : -1;
            g2.draw(new QuadCurve2D.Float(cx, vy, cx + sign * lr / 4, vy - sign * lr / 8, cx + sign * lr / 2, vy - sign * lr / 5));
            g2.draw(new QuadCurve2D.Float(cx, vy, cx - sign * lr / 4, vy - sign * lr / 8, cx - sign * lr / 2, vy - sign * lr / 5));
        }
    }

    private void drawCompassE(Graphics2D g2, int cx, int cy, int r) {
        int cr = r * 2 / 3;
        // Outer ring
        g2.setColor(new Color(160, 140, 120));
        g2.setStroke(new BasicStroke(Math.max(2.5f, r / 5f)));
        g2.drawOval(cx - cr, cy - cr, cr * 2, cr * 2);
        // Inner fill
        g2.setColor(new Color(245, 240, 230));
        g2.fillOval(cx - cr + 3, cy - cr + 3, cr * 2 - 6, cr * 2 - 6);
        // Tick marks
        g2.setColor(new Color(100, 90, 80));
        g2.setStroke(new BasicStroke(Math.max(1f, r / 12f)));
        for (int i = 0; i < 8; i++) {
            double a = Math.PI * 2 * i / 8;
            float x1 = cx + (float)(Math.cos(a) * (cr - 5));
            float y1 = cy - (float)(Math.sin(a) * (cr - 5));
            float x2 = cx + (float)(Math.cos(a) * (cr - 8));
            float y2 = cy - (float)(Math.sin(a) * (cr - 8));
            g2.draw(new Line2D.Float(x1, y1, x2, y2));
        }
        // North arrow (red)
        GeneralPath north = new GeneralPath();
        north.moveTo(cx, cy - cr + 6);
        north.lineTo(cx - cr / 4, cy);
        north.lineTo(cx + cr / 4, cy);
        north.closePath();
        g2.setColor(new Color(211, 47, 47));
        g2.fill(north);
        // South arrow (gray)
        GeneralPath south = new GeneralPath();
        south.moveTo(cx, cy + cr - 6);
        south.lineTo(cx - cr / 4, cy);
        south.lineTo(cx + cr / 4, cy);
        south.closePath();
        g2.setColor(new Color(180, 180, 190));
        g2.fill(south);
        // Center dot
        g2.setColor(new Color(60, 60, 60));
        g2.fillOval(cx - 3, cy - 3, 6, 6);
        g2.setColor(new Color(255, 215, 0));
        g2.fillOval(cx - 2, cy - 2, 4, 4);
    }

    private void drawCrystalBallE(Graphics2D g2, int cx, int cy, int r) {
        int br = r * 2 / 3;
        // Outer glow
        g2.setColor(new Color(138, 100, 220, 25));
        g2.fillOval(cx - br - 6, cy - br - 6, br * 2 + 12, br * 2 + 12);
        // Ball with gradient
        RadialGradientPaint ballP = new RadialGradientPaint(
            new Point2D.Float(cx - br / 3f, cy - br / 3f), br * 1.6f,
            new float[]{0f, 0.4f, 0.8f, 1f},
            new Color[]{new Color(220, 200, 255, 220), new Color(160, 120, 240, 200), new Color(100, 60, 200, 180), new Color(50, 20, 120, 160)}
        );
        g2.setPaint(ballP);
        g2.fillOval(cx - br, cy - br, br * 2, br * 2);
        // Glass highlight
        g2.setColor(new Color(255, 255, 255, 100));
        g2.fillOval(cx - br / 3, cy - br * 2 / 3, (int)(br * 0.5), (int)(br * 0.35));
        // Sparkles inside
        g2.setColor(new Color(255, 255, 220, 180));
        float sparkle = (float)(Math.sin(System.currentTimeMillis() * 0.003) * 0.5 + 0.5);
        g2.fillOval(cx - br / 4, cy + br / 8, (int)(3 * sparkle + 1), (int)(3 * sparkle + 1));
        g2.fillOval(cx + br / 5, cy - br / 5, 3, 3);
        g2.fillOval(cx - br / 6, cy + br / 3, 2, 2);
        g2.fillOval(cx + br / 8, cy + br / 6, 2, 2);
        // Base stand
        g2.setColor(new Color(120, 100, 80));
        g2.fillRoundRect(cx - br * 2 / 3, cy + br - 1, br * 4 / 3, br / 4, 6, 6);
        g2.setColor(new Color(100, 80, 60));
        g2.fillRoundRect(cx - br / 2, cy + br + br / 6, br, br / 5, 4, 4);
    }

    private void drawMoonPhaseE(Graphics2D g2, int cx, int cy, int r, int phase) {
        int mr = r * 2 / 3;
        g2.setColor(new Color(55, 60, 75)); g2.fillOval(cx - mr, cy - mr, mr * 2, mr * 2);
        if (phase == 0) { g2.setColor(new Color(80, 85, 100)); g2.drawOval(cx - mr, cy - mr, mr * 2, mr * 2); return; }
        if (phase == 4) { g2.setColor(new Color(255, 235, 100)); g2.fillOval(cx - mr, cy - mr, mr * 2, mr * 2); return; }
        Area circle = new Area(new Ellipse2D.Float(cx - mr, cy - mr, mr * 2, mr * 2));
        Area lit = new Area(circle);
        float off = mr * 0.5f;
        switch (phase) {
            case 1 -> { lit.intersect(new Area(new Rectangle2D.Float(cx, cy - mr, mr, mr * 2)));
                         lit.subtract(new Area(new Ellipse2D.Float(cx - off, cy - mr, mr * 2, mr * 2))); }
            case 2 -> lit.intersect(new Area(new Rectangle2D.Float(cx, cy - mr, mr, mr * 2)));
            case 3 -> { Area d = new Area(circle); d.intersect(new Area(new Rectangle2D.Float(cx - mr, cy - mr, mr, mr * 2)));
                         d.subtract(new Area(new Ellipse2D.Float(cx - mr * 2 + off, cy - mr, mr * 2, mr * 2))); lit.subtract(d); }
            case 5 -> { Area d = new Area(circle); d.intersect(new Area(new Rectangle2D.Float(cx, cy - mr, mr, mr * 2)));
                         d.subtract(new Area(new Ellipse2D.Float(cx - off, cy - mr, mr * 2, mr * 2))); lit.subtract(d); }
            case 6 -> lit.intersect(new Area(new Rectangle2D.Float(cx - mr, cy - mr, mr, mr * 2)));
            case 7 -> { lit.intersect(new Area(new Rectangle2D.Float(cx - mr, cy - mr, mr, mr * 2)));
                         lit.subtract(new Area(new Ellipse2D.Float(cx - mr * 2 + off, cy - mr, mr * 2, mr * 2))); }
        }
        g2.setColor(new Color(255, 235, 100)); g2.fill(lit);
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

        JLabel em = glowEmojiLabel(emoji, 18);

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
            kartPanel.repaint(); detayPanel.repaint(); detayPanel2.repaint(); extraPanel.repaint();
        });
        animTimer.start();
    }

    // ==================== GEO AUTOCOMPLETE ====================
    private List<String> parseGeoResults(String json) {
        List<String> results = new ArrayList<>();
        int idx = 0;
        while (true) {
            int nameIdx = json.indexOf('"' + "name" + '"' + ":" + '"', idx);
            if (nameIdx < 0) break;
            int nameStart = nameIdx + 7;
            int nameEnd = json.indexOf('"', nameStart);
            if (nameEnd < 0) break;
            String name = json.substring(nameStart, nameEnd);

            String country = "";
            int countryIdx = json.indexOf('"' + "country" + '"' + ":" + '"', nameEnd);
            if (countryIdx >= 0 && countryIdx < nameEnd + 100) {
                int cs = countryIdx + 10;
                int ce = json.indexOf('"', cs);
                if (ce >= 0) country = json.substring(cs, ce);
            }

            String state = "";
            int stateIdx = json.indexOf('"' + "state" + '"' + ":" + '"', nameEnd);
            if (stateIdx >= 0 && stateIdx < nameEnd + 200) {
                int ss = stateIdx + 8;
                int se = json.indexOf('"', ss);
                if (se >= 0) state = json.substring(ss, se);
            }

            String display = name;
            if (!state.isEmpty()) display += ", " + state;
            if (!country.isEmpty()) display += " (" + country + ")";
            results.add(display + "|" + name);
            idx = nameEnd + 1;
        }
        return results;
    }

    private void highlightSuggestItem(JPanel panel, int idx) {
        for (int i = 0; i < panel.getComponentCount(); i++) {
            Component c = panel.getComponent(i);
            if (c instanceof JLabel lbl) {
                lbl.putClientProperty("kbSelected", i == idx);
                lbl.repaint();
            }
        }
    }

private void showGeoSuggestions(List<String> results, JPanel suggestPanel, JTextField anchor) {
        suggestPanel.removeAll();
        // Reset any keyboard selection from previous results
        if (results.isEmpty()) { suggestPanel.setVisible(false); return; }
        int itemH = 36;
        int count = 0;
        Color hoverBg = new Color(255, 255, 255, 25);
        for (String r : results) {
            String[] parts = r.split("\\|", 2);
            String display = parts[0];
            String cityName = parts.length > 1 ? parts[1] : display;
            JLabel item = new JLabel(display) {
                boolean hovered = false;
                {
                    addMouseListener(new MouseAdapter() {
                        public void mouseEntered(MouseEvent e) { hovered = true; repaint(); }
                        public void mouseExited(MouseEvent e) { hovered = false; repaint(); }
                        public void mousePressed(MouseEvent e) {
                            sehirField.setText(cityName);
                            suggestPanel.setVisible(false);
                        }
                    });
                }
                @Override protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    boolean kbSel = Boolean.TRUE.equals(getClientProperty("kbSelected"));
                    if (hovered || kbSel) {
                        g2.setColor(hoverBg);
                        g2.fillRoundRect(2, 2, getWidth() - 4, getHeight() - 4, 10, 10);
                    }
                    g2.setColor(getForeground());
                    g2.setFont(getFont());
                    FontMetrics fm = g2.getFontMetrics();
                    g2.drawString(getText(), 16, (getHeight() + fm.getAscent() - fm.getDescent()) / 2);
                    g2.dispose();
                }
            };
            item.setFont(new Font("Segoe UI", Font.PLAIN, 14));
            item.setForeground(TEXT_PRIMARY);
            item.setOpaque(false);
            item.setPreferredSize(new Dimension(anchor.getWidth() - 12, itemH));
            item.setMaximumSize(new Dimension(Integer.MAX_VALUE, itemH));
            item.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            suggestPanel.add(item);
            count++;
        }
        if (count > 0) {
            JLayeredPane lp = getRootPane().getLayeredPane();
            // Position below the searchBar (parent of anchor), not anchor itself
            java.awt.Container bar = anchor.getParent();
            java.awt.Point loc = SwingUtilities.convertPoint(bar, 0, bar.getHeight(), lp);
            int popupW = bar.getWidth();
            int popupH = count * itemH + 12;
            suggestPanel.setBounds(loc.x, loc.y + 4, popupW, popupH);
            suggestPanel.revalidate();
            suggestPanel.repaint();
            suggestPanel.setVisible(true);
        }
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
                    // Koordinatlari al
                    double lat = pd(json, "\"lat\":");
                    double lon = pd(json, "\"lon\":");

                    // UV endeksi
                    double uvIdx = -1;
                    try {
                        String uvUrlStr = "https://api.openweathermap.org/data/2.5/uvi?lat=" + lat + "&lon=" + lon + "&appid=" + API_KEY;
                        HttpURLConnection uvConn = (HttpURLConnection) new URI(uvUrlStr).toURL().openConnection();
                        uvConn.setRequestMethod("GET");
                        uvConn.setConnectTimeout(5000); uvConn.setReadTimeout(5000);
                        if (uvConn.getResponseCode() == 200) {
                            String uvJson = readStream(uvConn.getInputStream());
                            uvIdx = pd(uvJson, "\"value\":");
                        }
                        uvConn.disconnect();
                    } catch (Exception ignored) {}

                    // AQI (Hava Kalitesi)
                    int aqiVal = -1;
                    try {
                        String aqiUrlStr = "https://api.openweathermap.org/data/2.5/air_pollution?lat=" + lat + "&lon=" + lon + "&appid=" + API_KEY;
                        HttpURLConnection aqiConn = (HttpURLConnection) new URI(aqiUrlStr).toURL().openConnection();
                        aqiConn.setRequestMethod("GET");
                        aqiConn.setConnectTimeout(5000); aqiConn.setReadTimeout(5000);
                        if (aqiConn.getResponseCode() == 200) {
                            String aqiJson = readStream(aqiConn.getInputStream());
                            aqiVal = (int) pd(aqiJson, "\"aqi\":");
                        }
                        aqiConn.disconnect();
                    } catch (Exception ignored) {}

                    // Forecast (Yagis ihtimali + Yarin)
                    int rainP = -1;
                    String tmrwDesc = null;
                    double tmrwTemp = 0;
                    try {
                        String fcUrlStr = "https://api.openweathermap.org/data/2.5/forecast?lat=" + lat + "&lon=" + lon + "&appid=" + API_KEY + "&units=metric&lang=tr&cnt=16";
                        HttpURLConnection fcConn = (HttpURLConnection) new URI(fcUrlStr).toURL().openConnection();
                        fcConn.setRequestMethod("GET");
                        fcConn.setConnectTimeout(5000); fcConn.setReadTimeout(5000);
                        if (fcConn.getResponseCode() == 200) {
                            String fcJson = readStream(fcConn.getInputStream());
                            // Ilk kayittaki pop = yagis ihtimali
                            rainP = (int) Math.round(pd(fcJson, "\"pop\":") * 100);
                            // Yarin (~8. kayit = 24 saat sonra, 3 saatlik araliklarla)
                            int listIdx = fcJson.indexOf("\"list\":");
                            if (listIdx >= 0) {
                                String rest = fcJson.substring(listIdx);
                                int searchFrom = 0;
                                int cnt = 0;
                                for (int ci = 0; ci < 8; ci++) {
                                    int next = rest.indexOf("\"dt\":", searchFrom + 1);
                                    if (next < 0) break;
                                    searchFrom = next;
                                    cnt++;
                                }
                                if (cnt >= 7) {
                                    String tmrwPart = rest.substring(searchFrom);
                                    tmrwTemp = pd(tmrwPart, "\"temp\":");
                                    tmrwDesc = ps(tmrwPart, "\"description\":\"");
                                }
                            }
                        }
                        fcConn.disconnect();
                    } catch (Exception ignored) {}

                    final double fuvi = uvIdx;
                    final int fAqi = aqiVal;
                    final int fRain = rainP;
                    final String fTmrwDesc = tmrwDesc;
                    final double fTmrwTemp = tmrwTemp;
                    SwingUtilities.invokeLater(() -> parseAndShow(json, fuvi, fAqi, fRain, fTmrwDesc, fTmrwTemp));
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
    private void parseAndShow(String json, double uvIndex, int aqiValue, int rainProb, String tomorrowDesc, double tomorrowTemp) {
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

            // Determine day/night
            long now = System.currentTimeMillis() / 1000;
            boolean night = now < sr || now > ss;

            updateTheme(wMain, temp, night);

            String emoji = weatherEmoji(wMain, night);

            titlePanel.setVisible(false);
            kartPanel.setVisible(true); detayPanel.setVisible(true); detayPanel2.setVisible(true); extraPanel.setVisible(true);
            animateCards();

            sehirBilgiLabel.setText("\uD83D\uDCCD " + cityName + ", " + country);
            SimpleDateFormat tarihSdf = new SimpleDateFormat("dd MMMM yyyy  \u2022  HH:mm", Locale.of("tr"));
            tarihSdf.setTimeZone(TimeZone.getTimeZone("GMT" + (tz >= 0 ? "+" : "") + (tz / 3600)));
            tarihLabel.setText(tarihSdf.format(new Date()));
            durumEmoji.setText(emoji);
            sicaklikLabel.setText(String.format("%.0f\u00B0", temp));
            minMaxLabel.setText(String.format("%.0f\u00B0 / %.0f\u00B0", tMax, tMin));
            hissedilenLabel.setText(String.format("Hissedilen: %.0f\u00B0C", feelsLike));
            if (desc != null && !desc.isEmpty()) desc = desc.substring(0,1).toUpperCase(Locale.of("tr")) + desc.substring(1);
            durumLabel.setForeground(accentColor);
            durumLabel.setText(desc != null ? desc : "-");

            nemLabel.setText("%" + hum);
            ruzgarLabel.setText(String.format("%.1f m/s", wind));
            basincLabel.setText(pres + " hPa");
            gorunurlukLabel.setText(String.format("%.1f km", vis / 1000.0));

            // Wind direction
            int windDeg = pi(json, "\"deg\":");
            String windDir = degreeToDirection(windDeg);
            ruzgarYonLabel.setText(windDir + " (" + windDeg + "\u00B0)");

            // Rain probability
            if (rainProb >= 0) {
                yagmurLabel.setText("%" + rainProb);
            } else {
                yagmurLabel.setText("N/A");
            }

            // AQI
            if (aqiValue > 0) {
                String aqiText = switch (aqiValue) {
                    case 1 -> "Iyi";
                    case 2 -> "Orta";
                    case 3 -> "Hassas";
                    case 4 -> "Kotu";
                    case 5 -> "Tehlikeli";
                    default -> "?";
                };
                aqiLabel.setText(aqiText + " (" + aqiValue + ")");
            } else {
                aqiLabel.setText("N/A");
            }

            // Tomorrow forecast
            if (tomorrowDesc != null && !tomorrowDesc.isEmpty()) {
                String capDesc = tomorrowDesc.substring(0, 1).toUpperCase(Locale.of("tr")) + tomorrowDesc.substring(1);
                yarinLabel.setText(String.format("%.0f\u00B0 %s", tomorrowTemp, capDesc));
            } else {
                yarinLabel.setText("N/A");
            }

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

            // UV Endeksi
            if (uvIndex >= 0) {
                uvLabel.setText(String.format("%.1f", uvIndex));
            } else {
                uvLabel.setText("N/A");
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

    // ========== WIND DIRECTION ==========
    private String degreeToDirection(int deg) {
        String[] dirs = {"K", "KKD", "KD", "DKD", "D", "DGD", "GD", "GGD", "G", "GGB", "GB", "BGB", "B", "BKB", "KB", "KKB"};
        int idx = (int) Math.round(deg / 22.5) % 16;
        return dirs[idx];
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
        titlePanel.setVisible(true);
        kartPanel.setVisible(true); detayPanel.setVisible(false); detayPanel2.setVisible(false); extraPanel.setVisible(false);
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
