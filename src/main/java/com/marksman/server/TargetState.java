package com.marksman.server;

public class TargetState {

    private double nearTargetY = 100;
    private double farTargetY = 100;

    public void update() {

        nearTargetY += 2;
        farTargetY += 4;

        if (nearTargetY > 600) {
            nearTargetY = 0;
        }

        if (farTargetY > 600) {
            farTargetY = 0;
        }
    }

    public double getNearTargetY() {
        return nearTargetY;
    }

    public double getFarTargetY() {
        return farTargetY;
    }
}