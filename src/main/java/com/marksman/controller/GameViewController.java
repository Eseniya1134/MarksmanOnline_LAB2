package com.marksman.controller;

import com.marksman.entity.Arrow;
import com.marksman.entity.Target;
import com.marksman.network.GameProtocol;
import com.marksman.network.PlayerInfo;
import com.marksman.network.ServerConnection;
import com.marksman.view.GameField;

import javafx.animation.AnimationTimer;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Контроллер игрового экрана
 *
 * ЛР3 изменения:
 * - Кнопка «Лидеры» (leaderboardBtn) — запрашивает таблицу побед
 * - Обработка сообщения LEADERBOARD от сервера
 * - При отображении таблицы игра ставится на паузу сервером автоматически
 * - Панель игроков теперь показывает число побед
 */
public class GameViewController {

  @FXML private GameField gameField;

    @FXML private Button startBtn;
    @FXML private Button pauseBtn;
    @FXML private Button shotBtn;
    @FXML private Button leaderboardBtn;   // ЛР3

    @FXML private Label pointValue;
    @FXML private Label shotValue;
    @FXML private Label statusLabel;

    private ServerConnection connection;
    private GameController gameController;

    private final Target nearTarget = new Target(500, 100, 120, 0, false);
    private final Target farTarget  = new Target(700, 100, 60,  0, true);

    private final List<Arrow>      arrows  = new CopyOnWriteArrayList<>();
    private final List<PlayerInfo> players = new CopyOnWriteArrayList<>();

    private volatile boolean gameStarted      = false;
    private volatile boolean gamePaused       = false;
    /** ЛР3: показывать ли таблицу лидеров поверх игры */
    private volatile boolean showLeaderboard  = false;
    private volatile String  leaderboardData  = "";

    private String myUsername = "Player";
    private String serverHost = "localhost";
    private int    serverPort = 8080;


    @FXML
    public void initialize() {
        gameController = new GameController();

        if (pauseBtn       != null) pauseBtn.setDisable(true);
        if (shotBtn        != null) shotBtn.setDisable(true);
        if (startBtn       != null) startBtn.setDisable(true);
        if (leaderboardBtn != null) leaderboardBtn.setDisable(false); // ЛР3: доступна всегда

        if (gameField != null) {
            gameField.sceneProperty().addListener((obs, oldScene, newScene) -> {
                if (newScene != null) {
                    newScene.setOnKeyPressed(e -> {
                        if (e.getCode() == KeyCode.SPACE)  onShotClick();
                        if (e.getCode() == KeyCode.ESCAPE) onCloseLeaderboard();
                    });
                }
            });
        }

        startRenderLoop();
    }

    public void setup(String username, String host, int port) {
        this.myUsername = username;
        this.serverHost = host;
        this.serverPort = port;
        connectToServer();
    }

    private void connectToServer() {
        setStatus("Подключение к " + serverHost + ":" + serverPort + "...");
        try {
            connection = new ServerConnection(this::handleServerMessage);
            connection.connect(serverHost, serverPort, myUsername);
        } catch (Exception e) {
            setStatus("Ошибка подключения: " + e.getMessage());
            if (startBtn != null) startBtn.setDisable(true);
        }
    }

   private void handleServerMessage(String message) {
        String[] parts = GameProtocol.split(message);
        String cmd = parts[0];
        String arg = parts.length > 1 ? parts[1] : "";

        switch (cmd) {

            case GameProtocol.JOIN_OK -> {
                setStatus("Подключено как «" + myUsername + "». Нажмите «Готов»");
                if (startBtn != null) startBtn.setDisable(false);
                // ЛР3: кнопка лидеров доступна сразу после подключения
                if (leaderboardBtn != null) leaderboardBtn.setDisable(false);
            }

            case GameProtocol.JOIN_FAIL -> {
                setStatus("Отказ: " + arg);
                if (startBtn != null) startBtn.setDisable(true);
            }

            case GameProtocol.PLAYER_LIST -> parsePlayerList(arg);

            case GameProtocol.START -> {
                gameStarted     = true;
                gamePaused      = false;
                showLeaderboard = false;
                gameController.startGame();
                if (pauseBtn != null) { pauseBtn.setDisable(false); pauseBtn.setText("Пауза"); }
                if (shotBtn  != null) shotBtn.setDisable(false);
                if (startBtn != null) startBtn.setDisable(true);
                arrows.clear();
                setStatus("Игра идёт!");
            }

            case GameProtocol.PAUSE -> {
                gameStarted = false;
                gamePaused  = true;
                if (pauseBtn != null) pauseBtn.setText("Готов");
                setStatus("Пауза — нажмите «Готов» чтобы продолжить");
            }

            case GameProtocol.RESUME -> {
                gameStarted     = true;
                gamePaused      = false;
                showLeaderboard = false;
                if (pauseBtn != null) pauseBtn.setText("Пауза");
                setStatus("Игра возобновлена");
            }

            case GameProtocol.STATE -> parseState(arg);

            case GameProtocol.SHOT_EVENT -> parseShotEvent(arg);

            case GameProtocol.HIT -> parseHit(arg);

            case GameProtocol.GAME_OVER -> {
                gameStarted = false;
                gamePaused  = false;
                gameController.stopGame();
                if (pauseBtn != null) pauseBtn.setDisable(true);
                if (shotBtn  != null) shotBtn.setDisable(true);
                if (startBtn != null) {
                    startBtn.setDisable(false);
                    startBtn.setText("Ещё раз");
                }
                setStatus("Победитель: " + arg + "! Нажмите «Ещё раз»");
            }


            case GameProtocol.LEADERBOARD -> {
                leaderboardData = arg;
                showLeaderboard = true;
                setStatus("Таблица лидеров (ESC или кнопка «Закрыть» для возврата)");
            }

            case "DISCONNECTED" -> {
                gameStarted = false;
                gamePaused  = false;
                setStatus("Соединение разорвано");
                if (pauseBtn != null) pauseBtn.setDisable(true);
                if (shotBtn  != null) shotBtn.setDisable(true);
            }
        }
    }

    private void parseState(String arg) {
        String[] p = arg.split(":");
        if (p.length < 2) return;
        try {
            nearTarget.setyPos(Integer.parseInt(p[0].trim()));
            farTarget.setyPos(Integer.parseInt(p[1].trim()));

            if (p.length >= 4) {
                boolean nearExploding = "1".equals(p[2].trim());
                boolean farExploding  = "1".equals(p[3].trim());
                if (nearExploding && !nearTarget.isExploding()) nearTarget.triggerExplosion();
                if (farExploding  && !farTarget.isExploding())  farTarget.triggerExplosion();
            }
        } catch (NumberFormatException ignored) {}

        arrows.removeIf(a -> !a.isActive());
        arrows.forEach(Arrow::update);
    }

    private void parsePlayerList(String arg) {
        players.clear();
        if (arg.isEmpty()) return;
        for (String part : arg.split("\\|")) {
            try {
                players.add(PlayerInfo.deserialize(part));
            } catch (Exception ignored) {}
        }
        updateMyStats();
    }

    private void parseShotEvent(String arg) {
        String[] p = arg.split(":", 2);
        if (p.length < 2) return;
        try {
            int arrowY = Integer.parseInt(p[1].trim());
            arrows.add(new Arrow(p[0], 100, arrowY, 10, 15));
        } catch (NumberFormatException ignored) {}
    }

    private void parseHit(String arg) {
        System.out.println("[Client] Попадание: " + arg);
    }

    @FXML
    private void onStartClick() {
        if (connection != null && connection.isConnected()) {
            connection.sendReady();
            if (startBtn != null) startBtn.setDisable(true);
            setStatus("Ожидаем остальных игроков...");
        }
    }

    @FXML
    private void onPauseClick() {
        if (connection == null || !connection.isConnected()) return;

        if (gameStarted) {
            connection.sendPause();
            if (pauseBtn != null) pauseBtn.setText("Готов");
        } else if (gamePaused) {
            connection.sendReady();
            if (pauseBtn != null) pauseBtn.setText("Пауза");
            setStatus("Ждём остальных игроков...");
        }
    }

    @FXML
    private void onShotClick() {
        if (!gameStarted || connection == null) return;
        connection.sendShoot();
    }

    /**
     * ЛР3: Кнопка «Лидеры» — запрашивает таблицу побед у сервера.
     * Доступна в любой момент после подключения.
     * Сервер автоматически поставит игру на паузу.
     */
    @FXML
    private void onLeaderboardClick() {
        if (connection == null || !connection.isConnected()) {
            setStatus("Нет соединения с сервером");
            return;
        }
        connection.sendLeaderboardRequest();
        setStatus("Загрузка таблицы лидеров...");
    }

    /**
     * ЛР3: Закрыть таблицу лидеров (кнопка «Закрыть» ).
     */
    @FXML
    private void onCloseLeaderboard() {
        if (showLeaderboard) {
            showLeaderboard = false;
            setStatus("Нажмите «Готов» чтобы продолжить игру");
        }
    }

    private void startRenderLoop() {
        AnimationTimer loop = new AnimationTimer() {
            @Override
            public void handle(long now) {
                render();
            }
        };
        loop.start();
    }

    private void render() {
        if (gameField == null) return;
        gameField.render();

        gameField.drawTarget(nearTarget);
        gameField.drawTarget(farTarget);

        if (nearTarget.isExploding()) gameField.drawBoom(nearTarget);
        if (farTarget.isExploding())  gameField.drawBoom(farTarget);

        gameField.drawPlayers(players);
        gameField.drawArrows(arrows);
        gameField.drawPlayerPanel(players);

        //  показать таблицу лидеров поверх всего
        if (showLeaderboard) {
            gameField.drawLeaderboard(leaderboardData);
        } else if (gamePaused) {
            gameField.drawOverlay("ПАУЗА");
        } else if (!gameStarted && !players.isEmpty()) {
            gameField.drawOverlay("Ожидание игроков...");
        }
    }


    private void updateMyStats() {
        players.stream()
                .filter(p -> p.getName().equals(myUsername))
                .findFirst()
                .ifPresent(p -> {
                    if (pointValue != null) pointValue.setText(String.valueOf(p.getScore()));
                    if (shotValue  != null) shotValue.setText(String.valueOf(p.getShots()));
                });
    }

    private void setStatus(String text) {
        if (statusLabel != null) statusLabel.setText(text);
    }
}
