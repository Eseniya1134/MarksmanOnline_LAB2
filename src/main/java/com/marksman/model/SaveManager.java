package com.marksman.model;

import com.google.gson.Gson;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

public class SaveManager {

    private static final String FILE_PATH = "save.json";
    public void save(GameState ourState) throws IOException {
        Gson gson = new Gson();
        String json = gson.toJson(ourState);
        FileWriter writer = new FileWriter(FILE_PATH);
        writer.write(json);
        writer.close();
    }

    public GameState load(){
        try {
            FileReader reader = new FileReader(FILE_PATH);
            Gson gson = new Gson();
            GameState ourState = gson.fromJson(reader, GameState.class);
            return ourState;
        } catch (IOException e) {
            return new GameState();
        }

    }

}
