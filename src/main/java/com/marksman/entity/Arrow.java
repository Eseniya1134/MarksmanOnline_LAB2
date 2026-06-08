package com.marksman.entity;

/**
 * Стрела — летит горизонтально вправо.
 * Создаётся при выстреле и уничтожается, когда выходит за границу поля.
 */
public class Arrow extends Thread {

    private final String ownerName;
    private volatile int xPos;
    private volatile int yPos;
    private final int size;
    private final int speed;
    private volatile boolean active = true;

    private static final int FIELD_WIDTH = 900;

    public Arrow(String ownerName, int xPos, int yPos, int size, int speed) {
        this.ownerName = ownerName;
        this.xPos      = xPos;
        this.yPos      = yPos;
        this.size      = size;
        this.speed     = speed;
        setDaemon(true);
    }

    @Override
    public void run() {
        while (active && !Thread.currentThread().isInterrupted()) {
            update();
            try {
                Thread.sleep(16);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    /** Продвигает стрелу вправо. Вызывается из AnimationTimer, если поток не запущен. */
    public void update() {
        xPos += speed;
        if (xPos > FIELD_WIDTH) {
            active = false;
        }
    }

    // ── Геттеры ───────────────────────────────────────────────────────────────

    public String getOwnerName() { return ownerName; }
    public int    getxPos()      { return xPos; }
    public int    getyPos()      { return yPos; }
    public int    getSize()      { return size; }
    public boolean isActive()    { return active; }

    public void setActive(boolean active) { this.active = active; }
}