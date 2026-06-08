package com.marksman.controller;

import com.marksman.entity.Arrow;
import com.marksman.entity.Target;
import com.marksman.network.GameProtocol;
import com.marksman.network.PlayerInfo;
import com.marksman.network.ServerConnection;
import com.marksman.view.GameField;

import javafx.animation.AnimationTimer;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Контроллер игрового экрана (game.fxml).
 *
 * Хранит состояние, полученное от сервера:
 *  – позиции мишеней (nearTarget, farTarget)
 *  – список игроков с очками (players)
 *  – список летящих стрел (arrows)
 *
 * Сервер авторитетен: все попадания считает сервер.
 * Клиент только отрисовывает то, что сообщил сервер.
 */
public class GameViewController {

    // ── FXML-поля ─────────────────────────────────────────────────────────────

    @FXML private GameField gameField;

    @FXML private Button startBtn;
    @FXML private Button pauseBtn;
    @FXML private Button shotBtn;

    // Метки для текущего игрока (собственные данные, дублируют PLAYER_LIST)
    @FXML private Label pointValue;
    @FXML private Label shotValue;
    @FXML private Label statusLabel;   // «Ожидание», «Игра идёт», «Пауза»

    // ── Состояние ─────────────────────────────────────────────────────────────

    private ServerConnection connection;
    private GameController gameController;

    // Визуальные объекты мишеней (только позиции — физику гоняет сервер)
    private final Target nearTarget = new Target(500, 100, 120, 0, false);
    private final Target farTarget  = new Target(700, 100, 60,  0, true);

    // Летящие стрелы (добавляются при SHOT-событии, удаляются когда улетели)
    private final List<Arrow> arrows = new CopyOnWriteArrayList<>();

    // Список игроков (обновляется по PLAYER_LIST от сервера)
    private final List<PlayerInfo> players = new CopyOnWriteArrayList<>();

    private volatile boolean gameStarted = false;
    private volatile boolean gamePaused  = false;

    // Имя текущего игрока (передаётся из MenuController)
    private String myUsername = "Player";

    // Настройки подключения (передаются из MenuController)
    private String serverHost = "localhost";
    private int    serverPort = 8080;

    // ── Инициализация ─────────────────────────────────────────────────────────

    @FXML
    public void initialize() {
        gameController = new GameController();

        // Кнопки неактивны до подключения
        pauseBtn.setDisable(true);
        shotBtn.setDisable(true);

        // Горячая клавиша: пробел = выстрел
        gameField.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null) {
                newScene.setOnKeyPressed(e -> {
                    if (e.getCode() == KeyCode.SPACE) onShotClick();
                });
            }
        });

        connectToServer();
        startRenderLoop();
    }

    /**
     * Вызывается из MenuController перед переключением сцены.
     */
    public void setup(String username, String host, int port) {
        this.myUsername  = username;
        this.serverHost  = host;
        this.serverPort  = port;
    }

    // ── Подключение к серверу ─────────────────────────────────────────────────

    private void connectToServer() {
        try {
            connection = new ServerConnection(this::handleServerMessage);
            connection.connect(serverHost, serverPort, myUsername);
            setStatus("Подключено. Нажмите «Готов»");
        } catch (Exception e) {
            setStatus("Ошибка подключения: " + e.getMessage());
            startBtn.setDisable(true);
        }
    }

    // ── Обработка сообщений сервера ───────────────────────────────────────────

    /**
     * Все вызовы уже перенесены в FX-поток через Platform.runLater внутри ServerConnection.
     */
    private void handleServerMessage(String message) {
        String[] parts = GameProtocol.split(message);
        String cmd = parts[0];
        String arg = parts.length > 1 ? parts[1] : "";

        switch (cmd) {

            case GameProtocol.JOIN_OK -> {
                setStatus("Подключено как «" + myUsername + "». Нажмите «Готов»");
                startBtn.setDisable(false);
            }

            case GameProtocol.JOIN_FAIL -> {
                setStatus("Отказ: " + arg);
                startBtn.setDisable(true);
            }

            case GameProtocol.PLAYER_LIST -> parsePlayerList(arg);

            case GameProtocol.START -> {
                gameStarted = true;
                gamePaused  = false;
                gameController.startGame();
                pauseBtn.setDisable(false);
                shotBtn.setDisable(false);
                startBtn.setDisable(true);
                arrows.clear();
                setStatus("Игра идёт!");
            }

            case GameProtocol.PAUSE -> {
                gameStarted = false;
                gamePaused  = true;
                pauseBtn.setText("Готов");
                setStatus("Пауза");
            }

            case GameProtocol.RESUME -> {
                gameStarted = true;
                gamePaused  = false;
                pauseBtn.setText("Пауза");
                setStatus("Игра возобновлена");
            }

            case GameProtocol.STATE -> parseState(arg);

            case GameProtocol.SHOT_EVENT -> parseShotEvent(arg);

            case GameProtocol.HIT -> parseHit(arg);

            case GameProtocol.GAME_OVER -> {
                gameStarted = false;
                gamePaused  = false;
                gameController.stopGame();
                pauseBtn.setDisable(true);
                shotBtn.setDisable(true);
                startBtn.setDisable(false);
                startBtn.setText("Ещё раз");
                setStatus("Победитель: " + arg + "! Нажмите «Ещё раз»");
            }

            case "DISCONNECTED" -> {
                gameStarted = false;
                setStatus("Соединение разорвано");
                pauseBtn.setDisable(true);
                shotBtn.setDisable(true);
            }
        }
    }

    /** STATE:<nearY>:<farY> */
    private void parseState(String arg) {
        String[] p = arg.split(":");
        if (p.length < 2) return;
        nearTarget.setyPos(Integer.parseInt(p[0]));
        farTarget.setyPos(Integer.parseInt(p[1]));

        // Продвинуть все летящие стрелы
        arrows.removeIf(a -> !a.isActive());
        arrows.forEach(Arrow::update);
    }

    /** PLAYER_LIST: name,score,shots|name,score,shots|... */
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

    /** SHOT:<shooterName>:<arrowY> — создаём стрелу для отображения */
    private void parseShotEvent(String arg) {
        String[] p = arg.split(":", 2);
        if (p.length < 2) return;
        String shooter = p[0];
        int    arrowY  = Integer.parseInt(p[1]);
        arrows.add(new Arrow(shooter, 100, arrowY, 10, 15));
    }

    /** HIT:<shooterName>:<points> — можно показать вспышку и т.д. */
    private void parseHit(String arg) {
        // Взрыв уже запущен на стороне сервера (triggerExplosion),
        // но сервер не рассылает флаг exploding напрямую.
        // Простейший вариант: форсируем взрыв на ближайшей мишени визуально.
        // Если хочешь точности — добавь в STATE передачу флага.
        System.out.println("[Client] Попадание: " + arg);
    }

    // ── Кнопки ────────────────────────────────────────────────────────────────

    @FXML
    private void onStartClick() {
        if (connection != null && connection.isConnected()) {
            connection.sendReady();
            startBtn.setDisable(true);
            setStatus("Ожидаем остальных игроков...");
        }
    }

    @FXML
    private void onPauseClick() {
        if (connection == null || !connection.isConnected()) return;

        if (gameStarted) {
            // Запрос паузы
            connection.sendPause();
            pauseBtn.setText("Готов");
        } else if (gamePaused) {
            // Снятие паузы — сообщаем о готовности
            connection.sendReady();
            pauseBtn.setText("Пауза");
            setStatus("Ждём остальных игроков...");
        }
    }

    @FXML
    private void onShotClick() {
        if (!gameStarted || connection == null) return;
        connection.sendShoot();
    }

    // ── Игровой цикл (только отрисовка) ──────────────────────────────────────

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
        gameField.render();

        // Мишени
        gameField.drawTarget(nearTarget);
        gameField.drawTarget(farTarget);

        if (nearTarget.isExploding()) gameField.drawBoom(nearTarget);
        if (farTarget.isExploding())  gameField.drawBoom(farTarget);

        // Игроки (лучники)
        gameField.drawPlayers(players);

        // Стрелы
        gameField.drawArrows(arrows);

        // Панель очков справа
        gameField.drawPlayerPanel(players);

        // Оверлей при паузе или ожидании
        if (gamePaused) {
            gameField.drawOverlay("ПАУЗА");
        } else if (!gameStarted && !players.isEmpty()) {
            gameField.drawOverlay("Ожидание игроков...");
        }
    }

    // ── Вспомогательное ──────────────────────────────────────────────────────

    /** Обновить метки собственного счёта из списка игроков. */
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