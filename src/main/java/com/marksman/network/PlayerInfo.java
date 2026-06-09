package com.marksman.network;

/**
 * Информация об игроке — используется и сервером, и клиентом для отображения.
 *
 * ЛР3: добавлено поле wins (число побед) для таблицы лидеров.
 */
public class PlayerInfo {

    private final String name;
    private int score;
    private int shots;
    private int wins; // ЛР3: число побед (из БД)

    public PlayerInfo(String name) {
        this.name  = name;
        this.score = 0;
        this.shots = 0;
        this.wins  = 0;
    }

    public PlayerInfo(String name, int score, int shots) {
        this.name  = name;
        this.score = score;
        this.shots = shots;
        this.wins  = 0;
    }

    public PlayerInfo(String name, int score, int shots, int wins) {
        this.name  = name;
        this.score = score;
        this.shots = shots;
        this.wins  = wins;
    }

    // ── Мутаторы ─────────────────────────────────────────────────────────────

    public void addScore(int pts)  { score += pts; }
    public void addShot()          { shots++;       }
    public void resetStats()       { score = 0; shots = 0; }
    public void setWins(int wins)  { this.wins = wins; }

    // ── Геттеры ───────────────────────────────────────────────────────────────

    public String getName()  { return name;  }
    public int getScore()    { return score; }
    public int getShots()    { return shots; }
    public int getWins()     { return wins;  }

    /**
     * Сериализация в строку для передачи по сети.
     * Формат: name,score,shots,wins
     */
    public String serialize() {
        return name + "," + score + "," + shots + "," + wins;
    }

    /**
     * Десериализация из строки.
     * Формат: name,score,shots[,wins]  (wins опционален для совместимости)
     */
    public static PlayerInfo deserialize(String s) {
        String[] p = s.split(",", 4);
        int score = Integer.parseInt(p[1]);
        int shots = Integer.parseInt(p[2]);
        int wins  = p.length >= 4 ? Integer.parseInt(p[3]) : 0;
        return new PlayerInfo(p[0], score, shots, wins);
    }

    @Override
    public String toString() {
        return "PlayerInfo{name=" + name + ", score=" + score
                + ", shots=" + shots + ", wins=" + wins + "}";
    }
}
