import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.util.*;
import javax.sound.sampled.*;
import java.text.SimpleDateFormat;

// ==========================================
// TOP-LEVEL MAIN CLASS
// ==========================================
public class MathCoach extends JFrame {
    public MathCoach() {
        setTitle("Math Coach");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setResizable(false);

        SettingsRepository repository = new SettingsRepository();
        GameModel model = new GameModel(repository);
        GameController controller = new GameController(model);
        GameView view = new GameView(model, controller);

        add(view);
        pack();
        setLocationRelativeTo(null);
        
        controller.startGameLoop(view);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new MathCoach().setVisible(true));
    }
}

// ==========================================
// SETTINGS REPOSITORY (File I/O for .ini)
// ==========================================
class SettingsRepository {
    private Properties props = new Properties();
    private final String FILE_NAME = "mathcoach_config.ini";

    public SettingsRepository() {
        loadSettings();
    }

    public void loadSettings() {
        try (FileInputStream in = new FileInputStream(FILE_NAME)) {
            props.load(in);
        } catch (IOException e) {
            saveDefaultSettings();
        }
    }

    public void saveDefaultSettings() {
        props.setProperty("Difficulty", "1");
        props.setProperty("GateSpeed", "2.0");
        props.setProperty("AvatarID", "1");
        props.setProperty("QuestionLimit", "0"); 
        props.setProperty("TimeLimit", "0"); 
        props.setProperty("HighScore", "0");
        saveSettings();
    }

    public void saveSettings() {
        try (FileOutputStream out = new FileOutputStream(FILE_NAME)) {
            props.store(out, "Math Coach Configuration & Logs");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void logPerformance(int score, int correct, int incorrect) {
        String timestamp = new SimpleDateFormat("yyyy.MM.dd.HH.mm.ss").format(new Date());
        props.setProperty("Log_" + timestamp, "Score:" + score + ", Correct:" + correct + ", Incorrect:" + incorrect);
        
        int highScore = Integer.parseInt(props.getProperty("HighScore", "0"));
        if (score > highScore) {
            props.setProperty("HighScore", String.valueOf(score));
        }
        saveSettings();
    }

    public int getInt(String key, int def) { return Integer.parseInt(props.getProperty(key, String.valueOf(def))); }
    public double getDouble(String key, double def) { return Double.parseDouble(props.getProperty(key, String.valueOf(def))); }
    public void setProperty(String key, String value) { props.setProperty(key, value); }
}

// ==========================================
// ENTITIES & ENGINE
// ==========================================
class PlayerEntity {
    public int lane = 0; 
    public int avatarId;
    public PlayerEntity(int avatarId) { this.avatarId = avatarId; }
}

class MathQuestion {
    public String equation;
    public int correctAnswer;
    public int difficulty;
}

class AnswerGate {
    public int answer;
    public boolean isCorrect;
    public int lane;
}

class GameSession {
    public int score = 0;
    public int streak = 0;
    public int correctCount = 0;
    public int incorrectCount = 0;
    public int questionsAsked = 0;
    public long startTime;
    public boolean isActive = false;
    public int questionLimit;
    public int timeLimitSeconds;
}

class Engine {
    private Random rand = new Random();

    public MathQuestion generateQuestion(int difficulty) {
        MathQuestion q = new MathQuestion();
        q.difficulty = difficulty;
        int type = rand.nextInt(2);
        
        switch (difficulty) {
            case 1: 
                if (type == 0) {
                    int a = rand.nextInt(15) + 1;
                    int b = rand.nextInt(15) + 1;
                    q.equation = a + " + " + b;
                    q.correctAnswer = a + b;
                } else {
                    int a = rand.nextInt(15) + 10;
                    int b = rand.nextInt(10) + 1;
                    q.equation = a + " - " + b;
                    q.correctAnswer = a - b;
                }
                break;
            case 2: 
                if (type == 0) {
                    int a = rand.nextInt(12) + 1;
                    int b = rand.nextInt(12) + 1;
                    q.equation = a + " * " + b;
                    q.correctAnswer = a * b;
                } else {
                    int ans = rand.nextInt(12) + 1;
                    int b = rand.nextInt(12) + 1;
                    int a = ans * b;
                    q.equation = a + " / " + b;
                    q.correctAnswer = ans;
                }
                break;
            case 3: 
                int aMod = rand.nextInt(40) + 10;
                int bMod = rand.nextInt(8) + 2;
                q.equation = aMod + " % " + bMod;
                q.correctAnswer = aMod % bMod;
                break;
            case 4: 
                int baseExp = rand.nextInt(8) + 2; 
                int exp = rand.nextInt(3) + 2;     
                if (baseExp > 5 && exp > 2) exp = 2; 
                q.equation = baseExp + "^" + exp;
                q.correctAnswer = (int) Math.pow(baseExp, exp);
                break;
            case 5: 
                int baseLog = rand.nextInt(4) + 2; 
                int ansLog = rand.nextInt(4) + 1;  
                int val = (int) Math.pow(baseLog, ansLog);
                q.equation = "log" + baseLog + "(" + val + ")";
                q.correctAnswer = ansLog;
                break;
        }
        return q;
    }

    public AnswerGate[] generateGates(MathQuestion q) {
        AnswerGate[] gates = new AnswerGate[4];
        ArrayList<Integer> answers = new ArrayList<>();
        answers.add(q.correctAnswer);
        
        while (answers.size() < 4) {
            int offset = (rand.nextInt(11) - 5);
            if (offset == 0) offset = 3;
            int wrong = q.correctAnswer + offset;
            
            if (wrong >= 0 && !answers.contains(wrong)) {
                answers.add(wrong);
            }
        }
        Collections.shuffle(answers);
        
        for (int i = 0; i < 4; i++) {
            gates[i] = new AnswerGate();
            gates[i].answer = answers.get(i);
            gates[i].isCorrect = (answers.get(i) == q.correctAnswer);
            gates[i].lane = i;
        }
        return gates;
    }
}

// ==========================================
// MODEL
// ==========================================
enum GameState { START_MENU, SETTINGS, PLAYING, EXIT_CONFIRM }

class GameModel {
    public SettingsRepository repo;
    public Engine engine;
    public GameSession session;
    public PlayerEntity player;
    
    public GameState state = GameState.START_MENU;
    public int menuSelection = 0; 
    
    public MathQuestion currentQuestion;
    public AnswerGate[] currentGates;
    public double gateX;
    
    public final int PLAYER_X = 100;
    public final int[] LANE_Y = {150, 250, 350, 450};

    public int difficulty;
    public double gateSpeed;
    public int avatarId;
    public int qLimit;
    public int tLimit;

    public GameModel(SettingsRepository repo) {
        this.repo = repo;
        this.engine = new Engine();
        loadSettingsFromRepo();
    }

    public void loadSettingsFromRepo() {
        difficulty = repo.getInt("Difficulty", 1);
        gateSpeed = repo.getDouble("GateSpeed", 2.0);
        avatarId = repo.getInt("AvatarID", 1);
        qLimit = repo.getInt("QuestionLimit", 0);
        tLimit = repo.getInt("TimeLimit", 0);
    }

    public void saveSettingsToRepo() {
        repo.setProperty("Difficulty", String.valueOf(difficulty));
        repo.setProperty("GateSpeed", String.valueOf(gateSpeed));
        repo.setProperty("AvatarID", String.valueOf(avatarId));
        repo.setProperty("QuestionLimit", String.valueOf(qLimit));
        repo.setProperty("TimeLimit", String.valueOf(tLimit));
        repo.saveSettings();
    }

    public void startSession() {
        session = new GameSession();
        session.questionLimit = qLimit;
        session.timeLimitSeconds = tLimit;
        session.startTime = System.currentTimeMillis();
        session.isActive = true;
        
        player = new PlayerEntity(avatarId);
        prepareNextQuestion();
        state = GameState.PLAYING;
    }

    public void prepareNextQuestion() {
        session.questionsAsked++;
        currentQuestion = engine.generateQuestion(difficulty);
        currentGates = engine.generateGates(currentQuestion);
        gateX = 800;
    }

    public void evaluatePassage() {
        AnswerGate chosen = currentGates[player.lane];
        if (chosen.isCorrect) {
            session.score += 10 * difficulty;
            session.streak++;
            session.correctCount++;
        } else {
            session.streak = 0;
            session.incorrectCount++;
        }
        
        if (qLimit > 0 && session.questionsAsked >= qLimit) {
            endSession();
        } else {
            prepareNextQuestion();
        }
    }

    public void endSession() {
        session.isActive = false;
        repo.logPerformance(session.score, session.correctCount, session.incorrectCount);
        state = GameState.START_MENU;
        menuSelection = 0;
    }
}

// ==========================================
// CONTROLLER
// ==========================================
class GameController extends KeyAdapter {
    private GameModel model;
    private Clip bgMusic;

    public GameController(GameModel model) {
        this.model = model;
        loadAudio();
    }
    
    private void loadAudio() {
        try {
            File audioFile = new File("Learning Song 1.wav");
            if (audioFile.exists()) {
                AudioInputStream audioStream = AudioSystem.getAudioInputStream(audioFile);
                bgMusic = AudioSystem.getClip();
                bgMusic.open(audioStream);
            }
        } catch (Exception e) {
            System.out.println("Error loading audio: " + e.getMessage());
        }
    }

    @Override
    public void keyPressed(KeyEvent e) {
        int key = e.getKeyCode();

        if (model.state == GameState.START_MENU) {
            if (key == KeyEvent.VK_UP) model.menuSelection = Math.max(0, model.menuSelection - 1);
            if (key == KeyEvent.VK_DOWN) model.menuSelection = Math.min(2, model.menuSelection + 1);
            if (key == KeyEvent.VK_ENTER) {
                if (model.menuSelection == 0) {
                    if (bgMusic != null) {
                        bgMusic.setFramePosition(0);
                        bgMusic.loop(Clip.LOOP_CONTINUOUSLY);
                    }
                    model.startSession();
                }
                else if (model.menuSelection == 1) { model.state = GameState.SETTINGS; model.menuSelection = 0; }
                else if (model.menuSelection == 2) System.exit(0);
            }
        } 
        else if (model.state == GameState.SETTINGS) {
            if (key == KeyEvent.VK_UP) model.menuSelection = Math.max(0, model.menuSelection - 1);
            if (key == KeyEvent.VK_DOWN) model.menuSelection = Math.min(5, model.menuSelection + 1);
            
            if (key == KeyEvent.VK_LEFT || key == KeyEvent.VK_RIGHT) {
                int mod = (key == KeyEvent.VK_RIGHT) ? 1 : -1;
                if (model.menuSelection == 0) model.difficulty = Math.max(1, Math.min(5, model.difficulty + mod));
                if (model.menuSelection == 1) model.gateSpeed = Math.max(1.0, Math.min(10.0, model.gateSpeed + (mod * 0.5)));
                if (model.menuSelection == 2) model.avatarId = Math.max(1, Math.min(9, model.avatarId + mod));
                if (model.menuSelection == 3) model.qLimit = Math.max(0, model.qLimit + (mod * 5));
                if (model.menuSelection == 4) model.tLimit = Math.max(0, model.tLimit + (mod * 30));
            }
            
            if (key == KeyEvent.VK_ENTER && model.menuSelection == 5) {
                model.saveSettingsToRepo();
                model.state = GameState.START_MENU;
                model.menuSelection = 1;
            }
        } 
        else if (model.state == GameState.PLAYING) {
            if (key == KeyEvent.VK_UP && model.player.lane > 0) model.player.lane--;
            if (key == KeyEvent.VK_DOWN && model.player.lane < 3) model.player.lane++;
            if (key == KeyEvent.VK_ESCAPE) {
                model.state = GameState.EXIT_CONFIRM;
                model.menuSelection = 1; 
            }
        } 
        else if (model.state == GameState.EXIT_CONFIRM) {
            if (key == KeyEvent.VK_LEFT || key == KeyEvent.VK_RIGHT) {
                model.menuSelection = (model.menuSelection == 0) ? 1 : 0;
            }
            if (key == KeyEvent.VK_ENTER) {
                if (model.menuSelection == 0) {
                    if (bgMusic != null) bgMusic.stop();
                    model.endSession();
                } else {
                    model.state = GameState.PLAYING; 
                }
            }
        }
    }

    public void startGameLoop(JPanel view) {
        Thread loop = new Thread(() -> {
            final int FPS = 60;
            final long targetTime = 1000 / FPS;
            while (true) {
                long startTime = System.currentTimeMillis();
                updateGame();
                view.repaint();
                long elapsedTime = System.currentTimeMillis() - startTime;
                long sleepTime = targetTime - elapsedTime;
                if (sleepTime > 0) {
                    try { Thread.sleep(sleepTime); } catch (Exception e) {}
                }
            }
        });
        loop.start();
    }

    private void updateGame() {
        if (model.state != GameState.PLAYING) return;

        if (model.session.timeLimitSeconds > 0) {
            long elapsedSecs = (System.currentTimeMillis() - model.session.startTime) / 1000;
            if (elapsedSecs >= model.session.timeLimitSeconds) {
                if (bgMusic != null) bgMusic.stop();
                model.endSession();
                return;
            }
        }

        model.gateX -= model.gateSpeed;
        if (model.gateX <= model.PLAYER_X + 256 && model.gateX + 144 >= model.PLAYER_X) {
            model.evaluatePassage();
        }
        
        if (model.gateX < -150) {
            model.prepareNextQuestion();
        }
    }
}

// ==========================================
// VIEW
// ==========================================
class GameView extends JPanel {
    private GameModel model;
    private Image bgImage;
    private Image[] planeAvatars = new Image[9];

    public GameView(GameModel model, GameController controller) {
        this.model = model;
        setPreferredSize(new Dimension(800, 550));
        setBackground(new Color(245, 245, 245));
        setFocusable(true);
        addKeyListener(controller);
        
        loadAssets();
    }
    
    private void loadAssets() {
        System.out.println("Working directory: " + System.getProperty("user.dir"));
        bgImage = new ImageIcon("background.png").getImage();
        for (int i = 1; i <= 9; i++) {
            String fileName = (i == 1) ? "Plane1Idle1.gif" : "Plane" + i + "Idle.gif";
            planeAvatars[i-1] = new ImageIcon(fileName).getImage();
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g;
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        if (bgImage != null && model.state == GameState.PLAYING) {
            g2d.drawImage(bgImage, 0, 100, 800, 450, this);
        }

        if (model.state == GameState.START_MENU) drawMainMenu(g2d);
        else if (model.state == GameState.SETTINGS) drawSettings(g2d);
        else if (model.state == GameState.PLAYING) drawGame(g2d);
        else if (model.state == GameState.EXIT_CONFIRM) {
            drawGame(g2d); 
            drawExitPrompt(g2d);
        }
    }

    private void drawMainMenu(Graphics2D g2d) {
        g2d.setFont(new Font("Arial", Font.BOLD, 60));
        g2d.setColor(Color.BLACK);
        g2d.drawString("MATH COACH", 180, 150);
        
        g2d.setFont(new Font("Arial", Font.PLAIN, 18));
        g2d.drawString("High Score: " + model.repo.getInt("HighScore", 0), 340, 200);

        String[] opts = {"Start Game Session", "Settings / Configuration", "Exit Game"};
        g2d.setFont(new Font("Arial", Font.BOLD, 30));
        for (int i = 0; i < opts.length; i++) {
            if (model.menuSelection == i) g2d.setColor(new Color(40, 120, 255));
            else g2d.setColor(Color.DARK_GRAY);
            g2d.drawString((model.menuSelection == i ? "► " : "") + opts[i], 220, 320 + (i * 70));
        }
    }

    private void drawSettings(Graphics2D g2d) {
        g2d.setColor(Color.BLACK);
        g2d.setFont(new Font("Arial", Font.BOLD, 36));
        g2d.drawString("Game Configuration", 220, 80);
        
        g2d.drawLine(100, 100, 700, 100);

        g2d.setFont(new Font("Arial", Font.BOLD, 22));
        String[] labels = {"Difficulty (Op/Grade):", "Gate Speed:", "Player Avatar:", "Question Limit:", "Time Limit (Sec):", "Save & Return"};
        
        String difficultyName = "Level " + model.difficulty;
        switch(model.difficulty) {
            case 1: difficultyName = "Level 1 (+ / -)"; break;
            case 2: difficultyName = "Level 2 (x / ÷)"; break;
            case 3: difficultyName = "Level 3 (Modulo)"; break;
            case 4: difficultyName = "Level 4 (Exponents)"; break;
            case 5: difficultyName = "Level 5 (Logarithms)"; break;
        }
        
        String[] values = {
            "<  " + difficultyName + "  >",
            "<  " + String.format("%.1f", model.gateSpeed) + "  >",
            "<  Plane " + model.avatarId + "  >",
            "<  " + (model.qLimit == 0 ? "Infinite" : model.qLimit) + "  >",
            "<  " + (model.tLimit == 0 ? "Infinite" : model.tLimit) + "  >",
            ""
        };

        for (int i = 0; i < labels.length; i++) {
            g2d.setColor(model.menuSelection == i ? new Color(40, 120, 255) : Color.DARK_GRAY);
            g2d.drawString(labels[i], 150, 160 + (i * 60));
            g2d.drawString(values[i], 430, 160 + (i * 60));
        }
        
        if (planeAvatars[model.avatarId - 1] != null) {
            g2d.drawImage(planeAvatars[model.avatarId - 1], 650, 250, 100, 100, this);
        }
        
        g2d.setColor(Color.GRAY);
        g2d.setFont(new Font("Arial", Font.ITALIC, 16));
        g2d.drawString("Use Left/Right arrows to adjust, Enter to save.", 220, 520);
    }

    private void drawGame(Graphics2D g2d) {
        g2d.setColor(Color.BLACK);
        
        g2d.setFont(new Font("Arial", Font.PLAIN, 18));
        g2d.drawString("Score: " + model.session.score, 20, 30);
        g2d.drawString("Streak: " + model.session.streak, 20, 60);
        g2d.drawString("Correct: " + model.session.correctCount, 650, 30);
        g2d.drawString("Incorrect: " + model.session.incorrectCount, 650, 60);

        g2d.setFont(new Font("Arial", Font.BOLD, 48));
        String eq = model.currentQuestion.equation + " = ?";
        g2d.drawString(eq, 400 - (g2d.getFontMetrics().stringWidth(eq) / 2), 65);

        g2d.setStroke(new BasicStroke(2));
        g2d.drawLine(0, 100, 800, 100); 

        int px = model.PLAYER_X;
        int py = model.LANE_Y[model.player.lane];
        
        Image sprite = planeAvatars[model.player.avatarId - 1];

        if (sprite != null && sprite.getWidth(this) > 0 && sprite.getHeight(this) > 0) {
            int width = sprite.getWidth(this);
            int height = sprite.getHeight(this);
            double scale = Math.min(256.0 / width, 144.0 / height);
            int drawWidth = (int)(width * scale);
            int drawHeight = (int)(height * scale);

            g2d.drawImage(sprite, px, py - drawHeight / 2, drawWidth, drawHeight, this);
        } else {
            g2d.setColor(Color.BLUE);
            g2d.fillRect(px, py - 20, 40, 40);
        }

        for (AnswerGate gate : model.currentGates) {
            int gy = model.LANE_Y[gate.lane];
            g2d.setColor(new Color(255, 255, 255, 200));
            g2d.fillRect((int)model.gateX, gy - 25, 120, 60);
            g2d.setColor(Color.BLACK);
            g2d.drawRect((int)model.gateX, gy - 25, 120, 60);
            
            g2d.fillPolygon(
                new int[]{(int)model.gateX - 10, (int)model.gateX - 30, (int)model.gateX - 10}, 
                new int[]{gy - 15, gy + 5, gy + 25}, 
                3
            );
            g2d.fillRect((int)model.gateX - 20, gy, 10, 10);

            g2d.setFont(new Font("Arial", Font.BOLD, 26));
            String ans = String.valueOf(gate.answer);
            g2d.drawString(ans, (int)model.gateX + (60 - g2d.getFontMetrics().stringWidth(ans) / 2), gy + 15);
        }
    }

    private void drawExitPrompt(Graphics2D g2d) {
        g2d.setColor(new Color(0, 0, 0, 200));
        g2d.fillRect(0, 0, 800, 550);
        
        g2d.setColor(Color.WHITE);
        g2d.fillRoundRect(200, 150, 400, 200, 20, 20);
        
        g2d.setColor(Color.BLACK);
        g2d.setFont(new Font("Arial", Font.BOLD, 24));
        g2d.drawString("Exit Session?", 320, 200);
        g2d.setFont(new Font("Arial", Font.PLAIN, 16));
        g2d.drawString("Your score will be saved to your progress log.", 240, 230);
        
        g2d.setFont(new Font("Arial", Font.BOLD, 24));
        g2d.setColor(model.menuSelection == 0 ? new Color(40, 120, 255) : Color.BLACK);
        g2d.drawString(model.menuSelection == 0 ? "> YES <" : "YES", 280, 300);
        
        g2d.setColor(model.menuSelection == 1 ? new Color(40, 120, 255) : Color.BLACK);
        g2d.drawString(model.menuSelection == 1 ? "> NO <" : "NO", 450, 300);
    }
}
