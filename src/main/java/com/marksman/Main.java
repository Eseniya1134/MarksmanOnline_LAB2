package com.marksman;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

public class Main extends Application {

    @Override
    public void start(Stage stage) throws IOException {
        FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/com/marksman/menuView.fxml")
        );
        Parent root = loader.load();
        Scene scene = new Scene(root);
        stage.setMaximized(true);
        stage.setTitle("Меткий стрелок");
        stage.setScene(scene);

        SceneManager.init(stage);

        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}