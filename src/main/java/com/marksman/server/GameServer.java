package com.marksman.server;

import com.marksman.entity.Arrow;
import com.marksman.entity.Target;
import com.marksman.network.GameProtocol;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Сервер игры «Меткий стрелок».
 *
 * Запуск: GameServer.main() или new GameServer().start()
 *
 * Сервер:
 *  – принимает до 4 игроков
 *  – проверяет уникальность имён
 *  – контролирует движение мишеней
 *  – проверяет попадания при выстреле
 *  – рассылает STATE каждые 50 мс
 *  – завершает игру при наборе первым игроком 6 очков
 */
public class GameServer {

    public static final int PORT        = 8080;
    public static final int MAX_PLAYERS = 4;
    public static final int WIN_SCORE   = 6;

    // Параметры мишеней
    private static final int NEAR_X  = 500;
    private static final int FAR_X   = 700;
    private static final int NEAR_SIZE = 120;
    private static final int FAR_SIZE  = 60;
    private static final int NEAR_SPEED = 2;
    private static final int FAR_SPEED  = 4;

    // Высота игрового поля
    private static final int FIELD_HEIGHT = 500;

    private final List<ClientHandler> clients = new CopyOnWriteArrayList<>();
    private volatile boolean gameRunning = false;
    private volatile boolean gamePaused  = false;

    private Target nearTarget;
    private Target farTarget;

    private Thread stateThread; // поток рассылки STATE

    // ── Точка входа ───────────────────────────────────────────────────────────

    public static void main(String[] args) {
        new GameServer().start();
    }

    public void start() {
        System.out.println("[Server] Запущен на порту " + PORT);
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            while (true) {
                Socket socket = serverSocket.accept();

                if (clients.size() >= MAX_PLAYERS) {
                    // Отказ: слот занят
                    try (var w = new PrintWriter(socket.getOutputStream(), true)) {
                        w.println(GameProtocol.encode(GameProtocol.JOIN_FAIL, "Сервер заполнен"));
                    }
                    socket.close();
                    continue;
                }

                // Считать имя игрока (первое сообщение JOIN:<name>)
                String username = readUsername(socket);
                if (username == null) { socket.close(); continue; }

                // Проверить уникальность имени
                boolean taken = clients.stream()
                        .anyMatch(c -> c.getUsername().equalsIgnoreCase(username));
                if (taken) {
                    try (var w = new PrintWriter(socket.getOutputStream(), true)) {
                        w.println(GameProtocol.encode(GameProtocol.JOIN_FAIL, "Имя занято"));
                    }
                    socket.close();
                    continue;
                }

                ClientHandler handler = new ClientHandler(socket, this, username);
                clients.add(handler);
                handler.start();
                System.out.println("[Server] Подключился: " + username
                        + " (всего игроков: " + clients.size() + ")");
            }
        } catch (IOException e) {
            System.err.println("[Server] Ошибка: " + e.getMessage());
        }
    }

    /** Читает первое сообщение JOIN:<name> из сокета до создания ClientHandler. */
    private String readUsername(Socket socket) {
        try {
            var in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            String line = in.readLine();
            if (line == null) return null;
            String[] parts = GameProtocol.split(line);
            if (!GameProtocol.JOIN.equals(parts[0]) || parts.length < 2) return null;
            return parts[1].trim();
        } catch (IOException e) {
            return null;
        }
    }

    // ── Обработчики событий от ClientHandler ─────────────────────────────────

    /** Игрок нажал «Готов». Если все готовы и игроков ≥ 2 — старт. */
    public synchronized void onPlayerReady(ClientHandler handler) {
        handler.setReady(true);
        System.out.println("[Server] Готов: " + handler.getUsername());

        if (gamePaused) {
            // Пауза снимается, когда все снова нажимают «Готов»
            boolean allReady = clients.stream().allMatch(ClientHandler::isReady);
            if (allReady) {
                gamePaused = false;
                gameRunning = true;
                nearTarget.setSpeed(NEAR_SPEED);
                farTarget.setSpeed(FAR_SPEED);
                broadcast(GameProtocol.RESUME);
                System.out.println("[Server] Игра возобновлена");
            }
            return;
        }

        if (!gameRunning && clients.size() >= 2) {
            boolean allReady = clients.stream().allMatch(ClientHandler::isReady);
            if (allReady) startGame();
        }
    }

    /** Игрок запросил паузу. */
    public synchronized void onPlayerPause(ClientHandler handler) {
        if (!gameRunning) return;
        handler.setReady(false);
        gamePaused  = true;
        gameRunning = false;
        // Останавливаем мишени (speed=0, поток продолжит работу, но без движения)
        nearTarget.setSpeed(0);
        farTarget.setSpeed(0);
        broadcast(GameProtocol.PAUSE);
        System.out.println("[Server] Пауза по запросу: " + handler.getUsername());
    }

    /** Игрок выстрелил. */
    public synchronized void onShoot(ClientHandler shooter) {
        if (!gameRunning) return;

        shooter.addShot();

        // Вычисляем Y стрелы: у каждого игрока своя высота прицела
        int arrowY = getArrowY(shooter.getPlayerId());

        // Создаём стрелу для рассылки (x=100 — позиция лучника, летит вправо)
        Arrow arrow = new Arrow(shooter.getUsername(), 100, arrowY, 10, 15);

        // Сообщить всем о выстреле
        broadcast(GameProtocol.encode(
                GameProtocol.SHOT_EVENT,
                shooter.getUsername(),
                String.valueOf(arrowY)
        ));

        // Проверяем попадание по текущим позициям мишеней
        int points = 0;
        if (nearTarget.isHitByY(arrowY)) {
            points = nearTarget.getPoints();
            nearTarget.triggerExplosion();
        } else if (farTarget.isHitByY(arrowY)) {
            points = farTarget.getPoints();
            farTarget.triggerExplosion();
        }

        if (points > 0) {
            shooter.addScore(points);
            broadcast(GameProtocol.encode(
                    GameProtocol.HIT,
                    shooter.getUsername(),
                    String.valueOf(points)
            ));
            broadcastPlayerList();
            System.out.println("[Server] Попадание! " + shooter.getUsername()
                    + " +" + points + " (всего: " + shooter.getScore() + ")");

            if (shooter.getScore() >= WIN_SCORE) {
                endGame(shooter.getUsername());
            }
        }
    }

    /** Удаляет клиента при разрыве соединения. */
    public void removeClient(ClientHandler handler) {
        clients.remove(handler);
        System.out.println("[Server] Отключился: " + handler.getUsername());
        broadcastPlayerList();
    }

    // ── Внутренние методы ─────────────────────────────────────────────────────

    private void startGame() {
        // Сбросить очки и статусы
        clients.forEach(ClientHandler::resetStats);

        // Создать/пересоздать мишени
        if (nearTarget != null) nearTarget.stopMovement();
        if (farTarget  != null) farTarget.stopMovement();

        nearTarget = new Target(NEAR_X, 100, NEAR_SIZE, NEAR_SPEED, false);
        farTarget  = new Target(FAR_X,  100, FAR_SIZE,  FAR_SPEED,  true);

        nearTarget.start();
        farTarget.start();

        gameRunning = true;
        gamePaused  = false;

        broadcast(GameProtocol.START);
        System.out.println("[Server] Игра началась!");

        // Поток рассылки состояния (~20 раз в секунду)
        if (stateThread != null) stateThread.interrupt();
        stateThread = new Thread(() -> {
            while (gameRunning || gamePaused) {
                broadcastState();
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }, "state-broadcaster");
        stateThread.setDaemon(true);
        stateThread.start();
    }

    private void endGame(String winnerName) {
        gameRunning = false;
        gamePaused  = false;

        if (nearTarget != null) nearTarget.stopMovement();
        if (farTarget  != null) farTarget.stopMovement();

        broadcast(GameProtocol.encode(GameProtocol.GAME_OVER, winnerName));
        System.out.println("[Server] Игра завершена. Победитель: " + winnerName);

        // Сбрасываем готовность у всех — можно стартовать заново
        clients.forEach(c -> c.setReady(false));
    }

    /** Y-позиция прицела для каждого игрока (по playerId). */
    private int getArrowY(int playerId) {
        // Все игроки на одной высоте — прицел посередине поля
        // Если хочешь разные высоты для разных игроков — настрой здесь
        return FIELD_HEIGHT / 2;
    }

    // ── Рассылки ──────────────────────────────────────────────────────────────

    /** Рассылает текущие позиции мишеней. */
    public void broadcastState() {
        if (nearTarget == null || farTarget == null) return;
        String msg = GameProtocol.encode(
                GameProtocol.STATE,
                String.valueOf(nearTarget.getyPos()),
                String.valueOf(farTarget.getyPos())
        );
        broadcast(msg);
    }

    /** Рассылает список всех игроков (имя, очки, выстрелы). */
    public void broadcastPlayerList() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < clients.size(); i++) {
            if (i > 0) sb.append("|");
            sb.append(clients.get(i).getInfo().serialize());
        }
        broadcast(GameProtocol.encode(GameProtocol.PLAYER_LIST, sb.toString()));
    }

    public void broadcast(String message) {
        clients.forEach(c -> c.send(message));
    }

    public List<ClientHandler> getClients() { return clients; }
}