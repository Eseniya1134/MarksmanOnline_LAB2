package com.marksman.network;

import javafx.application.Platform;

import java.io.*;
import java.net.Socket;
import java.util.function.Consumer;

/**
 * Клиентский сокет: подключение к серверу, отправка команд, приём сообщений.
 *
 * ЛР3: добавлен метод sendLeaderboardRequest() — запрос таблицы лидеров
 * через то же соединение (ТЗ: «Отдельное подключение создавать не нужно!»).
 */
public class ServerConnection {

    private Socket socket;
    private PrintWriter out;
    private final Consumer<String> onMessage;

    private volatile boolean connected = false;

    public ServerConnection(Consumer<String> onMessage) {
        this.onMessage = onMessage;
    }

    public void connect(String host, int port, String username) throws IOException {
        socket = new Socket(host, port);
        out    = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()), true);
        connected = true;

        send(GameProtocol.encode(GameProtocol.JOIN, username));

        Thread reader = new Thread(() -> {
            try (var in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
                String line;
                while ((line = in.readLine()) != null) {
                    final String msg = line;
                    Platform.runLater(() -> onMessage.accept(msg));
                }
            } catch (IOException e) {
                Platform.runLater(() -> onMessage.accept("DISCONNECTED"));
            } finally {
                connected = false;
            }
        }, "server-reader");
        reader.setDaemon(true);
        reader.start();
    }

    public void sendReady()             { send(GameProtocol.READY); }
    public void sendPause()             { send(GameProtocol.PAUSE); }
    public void sendShoot()             { send(GameProtocol.SHOOT); }
    /** pапросить таблицу лидеров через то же соединение. */
    public void sendLeaderboardRequest(){ send(GameProtocol.LEADERBOARD_REQUEST); }

    public void send(String message) {
        if (out != null && connected) {
            out.println(message);
        }
    }

    public void disconnect() {
        connected = false;
        try {
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (IOException ignored) {}
    }

    public boolean isConnected() { return connected; }
}
