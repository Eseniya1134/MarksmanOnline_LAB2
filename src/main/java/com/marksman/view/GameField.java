package com.marksman.view;

import com.marksman.entity.Arrow;
import com.marksman.entity.Target;
import com.marksman.network.PlayerInfo;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.util.List;

public class GameField extends Canvas {

    private Image bgImage;
    private Image target1Image;
    private Image target2Image;
    private Image arrowImage;
    private Image playerImage;
    private Image boomImage;

    public GameField() {
        super(900, 600);
        loadImages();
    }

    public GameField(double width, double height) {
        super(width, height);
        loadImages();
    }

    private void loadImages() {
        try {
            bgImage      = load("/com/marksman/images/bg.jpg");
            target1Image = load("/com/marksman/images/target.png");
            target2Image = load("/com/marksman/images/target.png");
            arrowImage   = load("/com/marksman/images/arrow.png");
            playerImage  = load("/com/marksman/images/hero.png");
            boomImage    = load("/com/marksman/images/boom.png");
        } catch (Exception e) {
            System.err.println("[GameField] Не удалось загрузить изображения: " + e.getMessage());
        }
    }

    private Image load(String path) {
        var stream = getClass().getResourceAsStream(path);
        return stream != null ? new Image(stream) : null;
    }

    // ── Отрисовка ─────────────────────────────────────────────────────────────

    public void render() {
        GraphicsContext gc = getGraphicsContext2D();
        gc.clearRect(0, 0, getWidth(), getHeight());
        if (bgImage != null) {
            gc.drawImage(bgImage, 0, 0, getWidth(), getHeight());
        } else {
            gc.setFill(Color.DARKSLATEGRAY);
            gc.fillRect(0, 0, getWidth(), getHeight());
        }
    }

    public void drawPlayer(int x, int y) {
        if (playerImage == null) return;
        getGraphicsContext2D().drawImage(playerImage, x, y, 150, 160);
    }

    public void drawPlayer() {
        drawPlayer(20, 200);
    }

    public void drawPlayers(List<PlayerInfo> players) {
        if (players == null) return;
        GraphicsContext gc = getGraphicsContext2D();
        gc.setFont(Font.font("Arial", FontWeight.BOLD, 12));

        for (int i = 0; i < players.size(); i++) {
            int yBase = 60 + i * 110;

            if (playerImage != null) {
                gc.drawImage(playerImage, 10, yBase, 90, 100);
            }

            PlayerInfo p = players.get(i);
            gc.setFill(Color.WHITE);
            gc.fillText(p.getName(), 10, yBase + 110);
        }
    }

    public void drawTarget(Target target) {
        GraphicsContext gc = getGraphicsContext2D();
        Image img = target.isFar() ? target2Image : target1Image;
        if (img != null) {
            gc.drawImage(img,
                    target.getxPos(), target.getyPos(),
                    target.getSize(), target.getSize() * 1.25);
        } else {
            gc.setFill(target.isFar() ? Color.ORANGERED : Color.RED);
            gc.fillRect(target.getxPos(), target.getyPos(),
                    target.getSize(), target.getSize());
        }
    }

    public void drawArrow(Arrow arrow) {
        GraphicsContext gc = getGraphicsContext2D();
        if (arrowImage != null) {
            gc.drawImage(arrowImage,
                    arrow.getxPos(), arrow.getyPos(),
                    arrow.getSize() * 2, arrow.getSize());
        } else {
            gc.setFill(Color.YELLOW);
            gc.fillRect(arrow.getxPos(), arrow.getyPos(), 30, 6);
        }
    }

    public void drawArrows(List<Arrow> arrows) {
        if (arrows == null) return;
        for (Arrow a : arrows) {
            if (a.isActive()) drawArrow(a);
        }
    }

    public void drawBoom(Target target) {
        if (!target.isExploding()) return;
        GraphicsContext gc = getGraphicsContext2D();
        if (boomImage != null) {
            gc.drawImage(boomImage,
                    target.getxPosBoom() - 70,
                    target.getyPosBoom() - 100,
                    300, 300);
        }
    }

    /**
     * Панель информации об игроках (правая колонка) — показывает имя, счёт, выстрелы, победы.
     * ЛР3: добавлено поле «Побед».
     */
    public void drawPlayerPanel(List<PlayerInfo> players) {
        if (players == null || players.isEmpty()) return;

        GraphicsContext gc = getGraphicsContext2D();
        double panelX = getWidth() - 165;
        double panelY = 10;

        gc.setFill(Color.color(0, 0, 0, 0.55));
        gc.fillRoundRect(panelX - 8, panelY - 4,
                170, players.size() * 80 + 10, 10, 10);

        for (int i = 0; i < players.size(); i++) {
            PlayerInfo p = players.get(i);
            double y = panelY + i * 80;

            gc.setFont(Font.font("Arial", FontWeight.BOLD, 13));
            gc.setFill(Color.WHITE);
            gc.fillText("Игрок: " + p.getName(), panelX, y + 16);

            gc.setFont(Font.font("Arial", FontWeight.NORMAL, 12));
            gc.setFill(Color.LIGHTGREEN);
            gc.fillText("Счёт: " + p.getScore(), panelX, y + 32);

            gc.setFill(Color.LIGHTYELLOW);
            gc.fillText("Выстрелов: " + p.getShots(), panelX, y + 48);

            // ЛР3: число побед
            gc.setFill(Color.GOLD);
            gc.fillText("Побед: " + p.getWins(), panelX, y + 64);
        }
    }

    public void drawOverlay(String text) {
        GraphicsContext gc = getGraphicsContext2D();
        gc.setFill(Color.color(0, 0, 0, 0.5));
        gc.fillRect(0, 0, getWidth(), getHeight());

        gc.setFont(Font.font("Arial", FontWeight.BOLD, 36));
        gc.setFill(Color.WHITE);
        double tw = text.length() * 20.0;
        gc.fillText(text, (getWidth() - tw) / 2, getHeight() / 2);
    }

    /**
     * ЛР3: Нарисовать таблицу лидеров поверх игрового поля.
     * entries — список пар "имя,побед", разделённых |
     * Формат строки entry: "username,wins"
     */
    public void drawLeaderboard(String rawData) {
        GraphicsContext gc = getGraphicsContext2D();

        // Полупрозрачный фон
        double w = 320, h = 0;
        String[] entries = rawData.isEmpty() ? new String[0] : rawData.split("\\|");
        h = 60 + entries.length * 30 + 20;

        double x = (getWidth() - w) / 2;
        double y = (getHeight() - h) / 2;

        gc.setFill(Color.color(0, 0, 0, 0.82));
        gc.fillRoundRect(x, y, w, h, 16, 16);

        // Заголовок
        gc.setFont(Font.font("Arial", FontWeight.BOLD, 20));
        gc.setFill(Color.GOLD);
        gc.fillText("Таблица лидеров", x + 70, y + 30);

        // Шапка
        gc.setFont(Font.font("Arial", FontWeight.BOLD, 14));
        gc.setFill(Color.LIGHTGRAY);
        gc.fillText("Имя", x + 20, y + 52);
        gc.fillText("Побед", x + 230, y + 52);

        // Строки
        gc.setFont(Font.font("Arial", FontWeight.NORMAL, 13));
        for (int i = 0; i < entries.length; i++) {
            String[] parts = entries[i].split(",", 2);
            String name = parts[0];
            String wins = parts.length > 1 ? parts[1] : "?";

            double ry = y + 70 + i * 28;
            // Чередующийся фон строк
            if (i % 2 == 0) {
                gc.setFill(Color.color(1, 1, 1, 0.06));
                gc.fillRect(x + 4, ry - 14, w - 8, 24);
            }
            gc.setFill(i == 0 ? Color.GOLD : Color.WHITE);
            gc.fillText((i + 1) + ". " + name, x + 20, ry);
            gc.fillText(wins, x + 240, ry);
        }
    }
}
