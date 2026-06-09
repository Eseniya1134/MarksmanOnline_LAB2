package com.marksman.server;

import com.marksman.db.PlayerDao;
import com.marksman.db.PlayerRecord;
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
 * ЛР3 изменения:
 * - Хранение имён и побед в БД через Hibernate (PlayerDao)
 * - Обработка LEADERBOARD_REQUEST от клиента
 * - Увеличение wins победителя в БД после каждой игры
 * - broadcastPlayerList() теперь передаёт поле wins
 */
public class GameServer {

    public static final int PORT        = 8080;
    public static final int MAX_PLAYERS = 4;
    public static final int WIN_SCORE   = 6;

    private static final int NEAR_X     = 500;
    private static final int FAR_X      = 700;
    private static final int NEAR_SIZE  = 120;
    private static final int FAR_SIZE   = 60;
    private static final int NEAR_SPEED = 2;
    private static final int FAR_SPEED  = 4;

    private static final int FIELD_HEIGHT    = 500;
    private static final int PLAYER_Y_BASE   = 60;
    private static final int PLAYER_Y_STEP   = 110;
    private static final int PLAYER_SPRITE_H = 100;

    private final List<ClientHandler> clients = new CopyOnWriteArrayList<>();
    private volatile boolean gameRunning = false;
    private volatile boolean gamePaused  = false;

    private Target nearTarget;
    private Target farTarget;

    private Thread stateThread;

    private final PlayerDao playerDao = PlayerDao.getInstance();

  public static void main(String[] args) {
        new GameServer().start();
    }

    public void start() {
        System.out.println("[Server] Запущен на порту " + PORT);
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            while (true) {
                Socket socket = serverSocket.accept();

                if (clients.size() >= MAX_PLAYERS) {
                    try (var w = new PrintWriter(socket.getOutputStream(), true)) {
                        w.println(GameProtocol.encode(GameProtocol.JOIN_FAIL, "Сервер заполнен"));
                    }
                    socket.close();
                    continue;
                }

                String username = readUsername(socket);
                if (username == null) { socket.close(); continue; }

                boolean taken = clients.stream()
                        .anyMatch(c -> c.getUsername().equalsIgnoreCase(username));
                if (taken) {
                    try (var w = new PrintWriter(socket.getOutputStream(), true)) {
                        w.println(GameProtocol.encode(GameProtocol.JOIN_FAIL, "Имя занято"));
                    }
                    socket.close();
                    continue;
                }

                // убедиться, что запись в БД существует (создать если нет)
                playerDao.getOrCreate(username);

                ClientHandler handler = new ClientHandler(socket, this, username, clients.size());
                clients.add(handler);
                handler.start();
                System.out.println("[Server] Подключился: " + username
                        + " (всего игроков: " + clients.size() + ")");
                broadcastPlayerList();
            }
        } catch (IOException e) {
            System.err.println("[Server] Ошибка: " + e.getMessage());
        }
    }

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

    public synchronized void onPlayerReady(ClientHandler handler) {
        handler.setReady(true);
        System.out.println("[Server] Готов: " + handler.getUsername());

        if (gamePaused) {
            boolean allReady = clients.stream().allMatch(ClientHandler::isReady);
            if (allReady) {
                gamePaused  = false;
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

    public synchronized void onPlayerPause(ClientHandler handler) {
        if (!gameRunning) return;
        handler.setReady(false);
        gamePaused  = true;
        gameRunning = false;
        nearTarget.setSpeed(0);
        farTarget.setSpeed(0);
        broadcast(GameProtocol.PAUSE);
        System.out.println("[Server] Пауза по запросу: " + handler.getUsername());
    }

    public synchronized void onShoot(ClientHandler shooter) {
        if (!gameRunning) return;

        shooter.addShot();

        int arrowY = getArrowY(shooter.getPlayerIndex());

        broadcast(GameProtocol.encode(
                GameProtocol.SHOT_EVENT,
                shooter.getUsername(),
                String.valueOf(arrowY)
        ));

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


    public synchronized void onLeaderboardRequest(ClientHandler requester) {
        // Поставить игру на паузу если идёт
        if (gameRunning) {
            gamePaused  = true;
            gameRunning = false;
            nearTarget.setSpeed(0);
            farTarget.setSpeed(0);
            // Сбросить готовность у всех, кроме запросившего (он уже "смотрит")
            clients.forEach(c -> c.setReady(false));
            broadcast(GameProtocol.PAUSE);
            System.out.println("[Server] Пауза — игрок " + requester.getUsername()
                    + " запросил таблицу лидеров");
        }

        // Собрать таблицу из БД
        List<PlayerRecord> records = playerDao.getLeaderboard();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < records.size(); i++) {
            if (i > 0) sb.append("|");
            sb.append(records.get(i).getUsername())
              .append(",")
              .append(records.get(i).getWins());
        }

        requester.send(GameProtocol.encode(GameProtocol.LEADERBOARD, sb.toString()));
        System.out.println("[Server] Таблица лидеров отправлена: " + requester.getUsername());
    }

    public void removeClient(ClientHandler handler) {
        clients.remove(handler);
        System.out.println("[Server] Отключился: " + handler.getUsername());
        for (int i = 0; i < clients.size(); i++) {
            clients.get(i).setPlayerIndex(i);
        }
        broadcastPlayerList();
    }

    private void startGame() {
        clients.forEach(ClientHandler::resetStats);

        if (nearTarget != null) nearTarget.stopMovement();
        if (farTarget  != null) farTarget.stopMovement();

        nearTarget = new Target(NEAR_X, -50, NEAR_SIZE, NEAR_SPEED, false);
        farTarget  = new Target(FAR_X, -100, FAR_SIZE,  FAR_SPEED,  true);

        nearTarget.start();
        farTarget.start();

        gameRunning = true;
        gamePaused  = false;

        broadcast(GameProtocol.START);
        System.out.println("[Server] Игра началась!");

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

        // ЛР3: сохранить победу в БД
        playerDao.incrementWins(winnerName);
        System.out.println("[Server] Победа сохранена в БД: " + winnerName);

        broadcast(GameProtocol.encode(GameProtocol.GAME_OVER, winnerName));
        System.out.println("[Server] Игра завершена. Победитель: " + winnerName);

        clients.forEach(c -> c.setReady(false));
    }

    private int getArrowY(int playerIndex) {
        return PLAYER_Y_BASE + playerIndex * PLAYER_Y_STEP + PLAYER_SPRITE_H / 2;
    }

    // ── Рассылки ──────────────────────────────────────────────────────────────

    public void broadcastState() {
        if (nearTarget == null || farTarget == null) return;
        String msg = GameProtocol.encode(
                GameProtocol.STATE,
                String.valueOf(nearTarget.getyPos()),
                String.valueOf(farTarget.getyPos()),
                nearTarget.isExploding() ? "1" : "0",
                farTarget.isExploding()  ? "1" : "0"
        );
        broadcast(msg);
    }

    //добавлено поле int wins и оно сохраняется в БД через Hibernate. При рассылке PLAYER_LIST сервер подгружает wins из БД
    public void broadcastPlayerList() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < clients.size(); i++) {
            if (i > 0) sb.append("|");
            ClientHandler c = clients.get(i);
            // Подгрузить wins из БД
            PlayerRecord rec = playerDao.getOrCreate(c.getUsername());
            c.getInfo().setWins(rec.getWins());
            sb.append(c.getInfo().serialize());
        }
        broadcast(GameProtocol.encode(GameProtocol.PLAYER_LIST, sb.toString()));
    }

    public void broadcast(String message) {
        clients.forEach(c -> c.send(message));
    }

    public List<ClientHandler> getClients() { return clients; }
}
