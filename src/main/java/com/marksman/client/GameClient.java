package com.marksman.client;

import com.marksman.network.GameProtocol;

import java.io.*;
import java.net.Socket;
import java.util.function.Consumer;

public class GameClient {
    private Socket socket;
    private PrintWriter out;
    private Consumer<String> onMessage;  // callback для UI

    public void connect(String host, int port, String username,
                        Consumer<String> messageCallback) throws IOException {
        this.onMessage = messageCallback;
        socket = new Socket(host, port);
        out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()), true);

        // Поток чтения — работает в фоне
        new Thread(() -> {
            try (var in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
                String line;
                while ((line = in.readLine()) != null) {
                    final String msg = line;
                    javafx.application.Platform.runLater(() -> onMessage.accept(msg));
                }
            } catch (IOException e) {
                javafx.application.Platform.runLater(
                        () -> onMessage.accept("DISCONNECTED")
                );
            }
        }).start();

        // Отправить имя
        send(GameProtocol.encode(GameProtocol.JOIN, username));
    }

    public void sendReady()  { send(GameProtocol.READY); }
    public void sendPause()  { send(GameProtocol.PAUSE); }
    public void sendShoot()  { send(GameProtocol.SHOOT); }

    public void disconnect() {
        try { if (socket != null) socket.close(); } catch (IOException ignored) {}
    }

    private void send(String msg) {
        if (out != null) out.println(msg);
    }
}