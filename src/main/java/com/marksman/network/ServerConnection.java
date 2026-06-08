package com.marksman.network;

import javafx.application.Platform;

import java.io.*;
import java.net.Socket;
import java.util.function.Consumer;

/**
 * Клиентский сокет: подключение к серверу, отправка команд, приём сообщений.
 *
 * Все входящие сообщения доставляются через callback в JavaFX Application Thread
 * (Platform.runLater), чтобы UI-контроллер мог обновлять интерфейс напрямую.
 */
public class ServerConnection {

    private Socket socket;
    private PrintWriter out;
    private final Consumer<String> onMessage;

    private volatile boolean connected = false;

    public ServerConnection(Consumer<String> onMessage) {
        this.onMessage = onMessage;
    }

    /**
     * Подключается к серверу и отправляет имя пользователя.
     *
     * @param host     IP или hostname сервера
     * @param port     порт (по умолчанию 8080)
     * @param username имя игрока
     * @throws IOException если подключиться не удалось
     */
    public void connect(String host, int port, String username) throws IOException {
        socket = new Socket(host, port);
        out    = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()), true);
        connected = true;

        // Первым делом отправляем JOIN с именем
        send(GameProtocol.encode(GameProtocol.JOIN, username));

        // Фоновый поток чтения
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

    // ── Команды к серверу ─────────────────────────────────────────────────────

    public void sendReady()  { send(GameProtocol.READY); }
    public void sendPause()  { send(GameProtocol.PAUSE); }
    public void sendShoot()  { send(GameProtocol.SHOOT); }

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