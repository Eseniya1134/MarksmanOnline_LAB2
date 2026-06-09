package com.marksman.db;

import jakarta.persistence.*;

/**
 * Сущность Hibernate — хранит имя игрока и число побед в БД.
 */
@Entity
@Table(name = "players")
public class PlayerRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "username", unique = true, nullable = false)
    private String username;

    @Column(name = "wins", nullable = false)
    private int wins = 0;

    public PlayerRecord() {}

    public PlayerRecord(String username) {
        this.username = username;
        this.wins = 0;
    }

    // Геттеры / сеттеры
    public Long   getId()            { return id; }
    public String getUsername()      { return username; }
    public int    getWins()          { return wins; }
    public void   setWins(int wins)  { this.wins = wins; }
    public void   incrementWins()    { this.wins++; }
}
