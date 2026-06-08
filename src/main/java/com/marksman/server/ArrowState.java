package com.marksman.server;

public class ArrowState {

    private String owner;

    private double x = 100;
    private double y = 500;

    private boolean active = true;

    public ArrowState(String owner) {
        this.owner = owner;
    }

    public void update() {

        x += 10;

        if (x > 1200) {
            active = false;
        }
    }

    public String getOwner() {
        return owner;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public boolean isActive() {
        return active;
    }
}