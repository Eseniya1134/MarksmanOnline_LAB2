package com.marksman.controller;

public class GameController {

    private boolean gameActive = false;

    public void startGame() {
        gameActive = true;
    }

    public void stopGame() {
        gameActive = false;
    }

    public boolean isGameActive() {
        return gameActive;
    }
}