package com.marksman.server;

import com.marksman.network.GameProtocol;
import com.marksman.network.PlayerInfo;

import java.io.*;
import java.net.Socket;

/**
 * Поток, обслуживающий одного подключённого игрока на стороне сервера.
 */
public class ClientHandler extends Thread {

    private final Socket socket;
    private final GameServer server;
    private PrintWriter out;

    private final PlayerInfo info;
    private final int playerId;
    private volatile boolean ready = false;

    private static int idCounter = 0;

    public ClientHandler(Socket socket, GameServer server, String username) {
        this.socket   = socket;
        this.server   = server;
        this.playerId = ++idCounter;
        this.info     = new PlayerInfo(username);
        setDaemon(true);
        setName("client-" + username);
    }

    @Override
    public void run() {
        try (var in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
            out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()), true);

            // Сообщить игроку его ID
            send(GameProtocol.encode(GameProtocol.JOIN_OK, String.valueOf(playerId)));
            // Разослать всем обновлённый список игроков
            server.broadcastPlayerList();

            String line;
            while ((line = in.readLine()) != null) {
                handleMessage(line.trim());
            }

        } catch (IOException e) {
            System.out.println("[Server] Потеряно соединение с: " + info.getName());
        } finally {
            server.removeClient(this);
        }
    }

    private void handleMessage(String message) {
        String[] parts = GameProtocol.split(message);
        String cmd = parts[0];

        switch (cmd) {
            case GameProtocol.READY -> server.onPlayerReady(this);
            case GameProtocol.PAUSE -> server.onPlayerPause(this);
            case GameProtocol.SHOOT -> server.onShoot(this);
            default -> System.out.println("[Server] Неизвестная команда от "
                    + info.getName() + ": " + message);
        }
    }

    // ── API для GameServer ────────────────────────────────────────────────────

    public void send(String message) {
        if (out != null) out.println(message);
    }

    public void resetStats() {
        info.resetStats();
        ready = false;
    }

    // ── Геттеры / Сеттеры ────────────────────────────────────────────────────

    public PlayerInfo getInfo()          { return info; }
    public String     getUsername()      { return info.getName(); }
    public int        getPlayerId()      { return playerId; }
    public int        getScore()         { return info.getScore(); }
    public void       addScore(int pts)  { info.addScore(pts); }
    public void       addShot()          { info.addShot(); }
    public boolean    isReady()          { return ready; }
    public void       setReady(boolean r){ ready = r; }
}