package com.marksman.entity;

/**
 * Мишень — движется вертикально в отдельном потоке.
 *
 * Ближняя (isFar=false): size=120, speed=2, points=1
 * Дальняя  (isFar=true):  size=60,  speed=4, points=2
 */
public class Target extends Thread {

    private volatile int xPos;
    private volatile int yPos;

    private final int size;
    private volatile int speed;
    private final boolean isFar;
    private final int points;

    private final int fieldHeight;
    private final int fieldWidth;

    private volatile boolean running = false;
    private volatile boolean exploding = false;
    private volatile long explodeTime = 0;
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
        this.fieldWidth  = 900;
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

    private void verticalMovement() {
        yPos += speed;
        if (yPos <= 0 || yPos >= fieldHeight - size) {
            speed = -speed;
        }
    }

    /**
     * Проверка попадания стрелы в мишень.
     * Используется сервером.
     */
    public boolean isHit(Arrow arrow) {
        if (exploding) return false;
        return arrow.getxPos() >= xPos
                && arrow.getxPos() <= xPos + size
                && arrow.getyPos() >= yPos
                && arrow.getyPos() <= yPos + size;
    }

    /**
     * Проверка попадания по Y-координате (для серверной логики без объекта Arrow).
     * arrowY — вертикальная позиция стрелы, летящей горизонтально.
     */
    public boolean isHitByY(int arrowY) {
        if (exploding) return false;
        return arrowY >= yPos && arrowY <= yPos + size;
    }

    /** Запускает анимацию взрыва и через EXPLODE_DURATION_MS телепортирует мишень в начало. */
    public synchronized void triggerExplosion() {
        xPosBoom   = xPos;
        yPosBoom   = yPos;
        exploding  = true;
        explodeTime = System.currentTimeMillis();

        new Thread(() -> {
            try {
                Thread.sleep(EXPLODE_DURATION_MS);
            } catch (InterruptedException ignored) {}
            respawn();
        }, "target-respawn").start();
    }

    /** Сбрасывает мишень на случайную начальную позицию. */
    public synchronized void respawn() {
        yPos      = (int)(Math.random() * (fieldHeight / 2));
        exploding = false;
    }

    // ── Геттеры / Сеттеры ────────────────────────────────────────────────────

    public int  getxPos()       { return xPos; }
    public int  getyPos()       { return yPos; }
    public int  getSize()       { return size; }
    public int  getSpeed()      { return speed; }
    public boolean isFar()      { return isFar; }
    public int  getPoints()     { return points; }
    public boolean isExploding(){ return exploding; }
    public int  getxPosBoom()   { return xPosBoom; }
    public int  getyPosBoom()   { return yPosBoom; }

    public void setxPos(int x)  { this.xPos = x; }
    public void setyPos(int y)  { this.yPos = y; }
    public void setSpeed(int s) { this.speed = s; }
}