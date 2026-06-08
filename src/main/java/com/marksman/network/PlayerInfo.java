package com.marksman.network;

/** Информация об игроке — используется и сервером, и клиентом для отображения. */
public class PlayerInfo {

    private final String name;
    private int score;
    private int shots;

    public PlayerInfo(String name) {
        this.name  = name;
        this.score = 0;
        this.shots = 0;
    }

    public PlayerInfo(String name, int score, int shots) {
        this.name  = name;
        this.score = score;
        this.shots = shots;
    }

    // ── Мутаторы ─────────────────────────────────────────────────────────────

    public void addScore(int pts)  { score += pts; }
    public void addShot()          { shots++;       }
    public void resetStats()       { score = 0; shots = 0; }

    // ── Геттеры ───────────────────────────────────────────────────────────────

    public String getName()  { return name; }
    public int getScore()    { return score; }
    public int getShots()    { return shots; }

    /**
     * Сериализация в строку для передачи по сети.
     * Формат: name,score,shots
     */
    public String serialize() {
        return name + "," + score + "," + shots;
    }

    /**
     * Десериализация из строки.
     * Формат: name,score,shots
     */
    public static PlayerInfo deserialize(String s) {
        String[] p = s.split(",", 3);
        return new PlayerInfo(p[0], Integer.parseInt(p[1]), Integer.parseInt(p[2]));
    }

    @Override
    public String toString() {
        return "PlayerInfo{name=" + name + ", score=" + score + ", shots=" + shots + "}";
    }
}