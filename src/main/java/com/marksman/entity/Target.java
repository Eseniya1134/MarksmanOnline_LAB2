package com.marksman.entity;

/**
 * Мишень — движется вертикально сверху вниз в отдельном потоке.
 *
 * По ТЗ: «Мишени движутся по направляющим сверху вниз».
 * При выходе за нижнюю границу поля мишень телепортируется наверх.
 *
 * ИСПРАВЛЕНО: убран bounce (отражение от стен) — теперь только сверху вниз.
 * Ближняя мишень: size=120, speed=2, points=1
 * Дальняя  мишень: size=60,  speed=4, points=2  (скорость в 2 раза выше, размер в 2 раза меньше)
 */
public class Target extends Thread {

    private volatile int xPos;
    private volatile int yPos;

    private final int size;
    private volatile int speed;
    private final boolean isFar;
    private final int points;

    private final int fieldHeight;

    private volatile boolean running   = false;
    private volatile boolean exploding = false;
    private static final int EXPLODE_DURATION_MS = 600;

    // Позиция взрыва (для анимации)
    private volatile int xPosBoom;
    private volatile int yPosBoom;

    public Target(int xPos, int yPos, int size, int speed, boolean isFar) {
        this.xPos        = xPos;
        this.yPos        = yPos;
        this.size        = size;
        this.speed       = speed;
        this.isFar       = isFar;
        this.points      = isFar ? 2 : 1;
        this.fieldHeight = 500;
        setDaemon(true);
    }

    @Override
    public void run() {
        running = true;
        while (running && !Thread.currentThread().isInterrupted()) {
            if (!exploding) {
                verticalMovement();
            }
            try {
                Thread.sleep(16); // ~60 fps
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    public void stopMovement() {
        running = false;
        interrupt();
    }

    /**
     * Движение сверху вниз. При выходе за нижнюю границу — телепортация наверх.
     * ТЗ: «движутся по направляющим сверху вниз».
     */
    private void verticalMovement() {
        if (speed == 0) return; // пауза
        yPos += speed;
        // Когда мишень вышла за нижнюю границу — появляется сверху
        if (yPos > fieldHeight) {
            yPos = -size; // появляется чуть выше верхней границы
        }
    }

    /**
     * Проверка попадания стрелы в мишень (по объекту Arrow).
     */
    public boolean isHit(Arrow arrow) {
        if (exploding) return false;
        return arrow.getxPos() >= xPos
                && arrow.getxPos() <= xPos + size
                && arrow.getyPos() >= yPos
                && arrow.getyPos() <= yPos + size;
    }

    /**
     * Проверка попадания по Y-координате (серверная логика без объекта Arrow).
     * arrowY — вертикальная позиция стрелы, летящей горизонтально.
     */
    public boolean isHitByY(int arrowY) {
        if (exploding) return false;
        return arrowY >= yPos && arrowY <= yPos + size;
    }

    /**
     * Запускает анимацию взрыва и через EXPLODE_DURATION_MS телепортирует мишень наверх.
     */
    public synchronized void triggerExplosion() {
        xPosBoom    = xPos;
        yPosBoom    = yPos;
        exploding   = true;

        new Thread(() -> {
            try {
                Thread.sleep(EXPLODE_DURATION_MS);
            } catch (InterruptedException ignored) {}
            respawn();
        }, "target-respawn").start();
    }

    /**
     * Сбрасывает мишень на случайную позицию в верхней части поля.
     */
    public synchronized void respawn() {
        yPos      = -(int)(Math.random() * 200 + 50); // появляется чуть выше экрана
        exploding = false;
    }

    // ── Геттеры / Сеттеры ────────────────────────────────────────────────────

    public int     getxPos()        { return xPos; }
    public int     getyPos()        { return yPos; }
    public int     getSize()        { return size; }
    public int     getSpeed()       { return speed; }
    public boolean isFar()          { return isFar; }
    public int     getPoints()      { return points; }
    public boolean isExploding()    { return exploding; }
    public int     getxPosBoom()    { return xPosBoom; }
    public int     getyPosBoom()    { return yPosBoom; }

    public void setxPos(int x)      { this.xPos = x; }
    public void setyPos(int y)      { this.yPos = y; }
    public void setSpeed(int s)     { this.speed = s; }
}