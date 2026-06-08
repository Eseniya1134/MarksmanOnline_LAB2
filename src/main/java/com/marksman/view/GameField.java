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

    /** Очистить поле и нарисовать фон. */
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

    /** Нарисовать одного игрока (лучника) по заданным координатам. */
    public void drawPlayer(int x, int y) {
        if (playerImage == null) return;
        getGraphicsContext2D().drawImage(playerImage, x, y, 150, 160);
    }

    /** Нарисовать лучника на стандартной позиции (ЛР1 совместимость). */
    public void drawPlayer() {
        drawPlayer(20, 200);
    }

    /**
     * Нарисовать нескольких лучников.
     * Каждый игрок расположен по вертикали в зависимости от индекса.
     */
    public void drawPlayers(List<PlayerInfo> players) {
        if (players == null) return;
        GraphicsContext gc = getGraphicsContext2D();
        gc.setFont(Font.font("Arial", FontWeight.BOLD, 12));

        for (int i = 0; i < players.size(); i++) {
            int yBase = 60 + i * 110;

            if (playerImage != null) {
                gc.drawImage(playerImage, 10, yBase, 90, 100);
            }

            // Подпись с именем игрока
            PlayerInfo p = players.get(i);
            gc.setFill(Color.WHITE);
            gc.fillText(p.getName(), 10, yBase + 110);
        }
    }

    /** Нарисовать мишень. */
    public void drawTarget(Target target) {
        GraphicsContext gc = getGraphicsContext2D();
        Image img = target.isFar() ? target2Image : target1Image;
        if (img != null) {
            gc.drawImage(img,
                    target.getxPos(), target.getyPos(),
                    target.getSize(), target.getSize() * 1.25);
        } else {
            // Fallback — красный прямоугольник
            gc.setFill(target.isFar() ? Color.ORANGERED : Color.RED);
            gc.fillRect(target.getxPos(), target.getyPos(),
                    target.getSize(), target.getSize());
        }
    }

    /** Нарисовать стрелу. */
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

    /** Нарисовать все активные стрелы. */
    public void drawArrows(List<Arrow> arrows) {
        if (arrows == null) return;
        for (Arrow a : arrows) {
            if (a.isActive()) drawArrow(a);
        }
    }

    /** Нарисовать анимацию взрыва на месте мишени. */
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
     * Нарисовать панель с информацией об игроках (правая колонка).
     * Вызывается поверх всего остального.
     */
    public void drawPlayerPanel(List<PlayerInfo> players) {
        if (players == null || players.isEmpty()) return;

        GraphicsContext gc = getGraphicsContext2D();
        double panelX = getWidth() - 160;
        double panelY = 10;

        // Полупрозрачный фон панели
        gc.setFill(Color.color(0, 0, 0, 0.55));
        gc.fillRoundRect(panelX - 8, panelY - 4,
                165, players.size() * 68 + 10, 10, 10);

        for (int i = 0; i < players.size(); i++) {
            PlayerInfo p = players.get(i);
            double y = panelY + i * 68;

            gc.setFont(Font.font("Arial", FontWeight.BOLD, 13));
            gc.setFill(Color.WHITE);
            gc.fillText("Игрок: " + p.getName(), panelX, y + 16);

            gc.setFont(Font.font("Arial", FontWeight.NORMAL, 12));
            gc.setFill(Color.LIGHTGREEN);
            gc.fillText("Счёт: " + p.getScore(), panelX, y + 32);

            gc.setFill(Color.LIGHTYELLOW);
            gc.fillText("Выстрелов: " + p.getShots(), panelX, y + 48);
        }
    }

    /**
     * Нарисовать полупрозрачный оверлей с текстом поверх поля.
     * Используется для «Пауза», «Ждём игроков» и т.д.
     */
    public void drawOverlay(String text) {
        GraphicsContext gc = getGraphicsContext2D();
        gc.setFill(Color.color(0, 0, 0, 0.5));
        gc.fillRect(0, 0, getWidth(), getHeight());

        gc.setFont(Font.font("Arial", FontWeight.BOLD, 36));
        gc.setFill(Color.WHITE);
        double tw = text.length() * 20.0;
        gc.fillText(text, (getWidth() - tw) / 2, getHeight() / 2);
    }
}