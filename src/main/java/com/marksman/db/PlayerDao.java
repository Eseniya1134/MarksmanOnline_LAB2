package com.marksman.db;

import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;

import java.util.List;

/**
 * DAO для работы с таблицей players через Hibernate.
 * Использует SQLite (через sqlite-dialect) — не требует отдельного сервера БД
 */
public class PlayerDao {

    private static PlayerDao instance;
    private final SessionFactory sessionFactory;

    private PlayerDao() {
        sessionFactory = new Configuration()
                .configure("hibernate.cfg.xml")
                .addAnnotatedClass(PlayerRecord.class)
                .buildSessionFactory();
    }

    public static synchronized PlayerDao getInstance() {
        if (instance == null) instance = new PlayerDao();
        return instance;
    }

    /**
     * Возвращает запись игрока или создаёт новую, если её нет.
     */
    public PlayerRecord getOrCreate(String username) {
        try (Session session = sessionFactory.openSession()) {
            PlayerRecord record = session.createQuery(
                            "FROM PlayerRecord WHERE username = :u", PlayerRecord.class)
                    .setParameter("u", username)
                    .uniqueResult();
            if (record == null) {
                record = new PlayerRecord(username);
                session.beginTransaction();
                session.persist(record);
                session.getTransaction().commit();
            }
            return record;
        }
    }

    /**
     * Увеличивает счётчик побед указанного игрока на 1.
     */
    public void incrementWins(String username) {
        try (Session session = sessionFactory.openSession()) {
            session.beginTransaction();
            PlayerRecord record = session.createQuery(
                            "FROM PlayerRecord WHERE username = :u", PlayerRecord.class)
                    .setParameter("u", username)
                    .uniqueResult();
            if (record == null) {
                record = new PlayerRecord(username);
                record.incrementWins();
                session.persist(record);
            } else {
                record.incrementWins();
                session.merge(record);
            }
            session.getTransaction().commit();
        }
    }

    /**
     * Возвращает всех игроков, отсортированных по числу побед (убывание).
     * Используется для таблицы лидеров.
     */
    public List<PlayerRecord> getLeaderboard() {
        try (Session session = sessionFactory.openSession()) {
            return session.createQuery(
                    "FROM PlayerRecord ORDER BY wins DESC", PlayerRecord.class
            ).list();
        }
    }

    public void close() {
        if (sessionFactory != null && !sessionFactory.isClosed()) {
            sessionFactory.close();
        }
    }
}
