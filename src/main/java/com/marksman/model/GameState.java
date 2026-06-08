package com.marksman.model;

/**
 * GameState для ЛР2 — упрощён.
 *
 * В мультиплеерной версии авторитетное состояние хранит сервер (ServerGameState).
 * Этот класс оставлен для совместимости с GameController, если он ещё используется.
 * SaveManager и Preferences больше не нужны — сохранения нет.
 */
public class GameState {

    private int point = 0;
    private int shot  = 0;

    public GameState() {}

    public void reset() {
        point = 0;
        shot  = 0;
    }

    // Геттеры / сеттеры
    public int  getPoint()          { return point; }
    public int  getShot()           { return shot; }
    public void setPoint(int point) { this.point = point; }
    public void setShot(int shot)   { this.shot = shot; }

    // Заглушки для совместимости
    public int  getRecordPoint()    { return 0; }
    public static boolean hasSavedGame() { return false; }
    public static void reset_static()    {}
    public static void load()            {}
}