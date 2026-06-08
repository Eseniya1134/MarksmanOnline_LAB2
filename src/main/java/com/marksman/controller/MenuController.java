package com.marksman.controller;

import com.marksman.SceneManager;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

import java.net.URL;
import java.util.ResourceBundle;

/**
 * Контроллер главного меню (menuView.fxml).
 *
 * Для ЛР2 меню содержит:
 *  – поле «Имя игрока»
 *  – поле «IP сервера» (по умолчанию localhost)
 *  – кнопку «Подключиться»
 *
 * При подключении открывается игровой экран, а GameViewController
 * получает имя и адрес через метод setup().
 */
public class MenuController implements Initializable {

    @FXML private TextField usernameField;
    @FXML private TextField serverIpField;
    @FXML private Button    connectBtn;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        // Значения по умолчанию
        if (serverIpField != null) serverIpField.setText("localhost");
        if (usernameField != null) usernameField.setPromptText("Введите имя игрока");
    }

    @FXML
    private void onConnectClick() {
        String username = usernameField != null ? usernameField.getText().trim() : "";
        String host     = serverIpField != null ? serverIpField.getText().trim()  : "localhost";

        if (username.isEmpty()) {
            showError("Введите имя игрока");
            return;
        }
        if (host.isEmpty()) {
            showError("Введите IP-адрес сервера");
            return;
        }

        try {
            // Загружаем игровой экран
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/com/marksman/game.fxml")
            );
            Parent root = loader.load();

            // Передаём параметры подключения в контроллер
            GameViewController gameVC = loader.getController();
            gameVC.setup(username, host, 8080);

            // Переключаем сцену
            Stage stage = (Stage) connectBtn.getScene().getWindow();
            stage.setScene(new Scene(root));
            stage.show();

        } catch (Exception e) {
            showError("Не удалось открыть игру: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void showError(String msg) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("Ошибка");
        alert.setHeaderText(null);
        alert.setContentText(msg);
        alert.showAndWait();
    }
}