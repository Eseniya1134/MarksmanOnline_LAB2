package com.marksman.network;

/**
 * Протокол общения сервера и клиентов.
 *
 * Формат сообщения: КОМАНДА:аргумент1:аргумент2...
 *
 * Клиент → Сервер:
 *   JOIN:<username>
 *   READY
 *   PAUSE
 *   SHOOT
 *
 * Сервер → Клиент:
 *   JOIN_OK:<playerId>
 *   JOIN_FAIL:<причина>
 *   PLAYER_LIST:<name1>,<score1>,<shots1>|<name2>,...
 *   START
 *   PAUSE
 *   RESUME
 *   STATE:<nearY>:<farY>
 *   SHOT:<shooterName>:<arrowY>
 *   HIT:<shooterName>:<points>
 *   GAME_OVER:<winnerName>
 */
public final class GameProtocol {

    private GameProtocol() {}

    // ── Клиент → Сервер ─────────────────────────────────────────────────────
    public static final String JOIN  = "JOIN";
    public static final String READY = "READY";
    public static final String PAUSE = "PAUSE";
    public static final String SHOOT = "SHOOT";

    // ── Сервер → Клиент ─────────────────────────────────────────────────────
    public static final String JOIN_OK      = "JOIN_OK";
    public static final String JOIN_FAIL    = "JOIN_FAIL";
    public static final String PLAYER_LIST  = "PLAYER_LIST";
    public static final String START        = "START";
    // PAUSE переиспользуется в обе стороны
    public static final String RESUME       = "RESUME";
    public static final String STATE        = "STATE";
    public static final String SHOT_EVENT   = "SHOT";
    public static final String HIT          = "HIT";
    public static final String GAME_OVER    = "GAME_OVER";

    // ── Утилиты ─────────────────────────────────────────────────────────────

    /** Собирает сообщение: encode("JOIN_OK", "3") → "JOIN_OK:3" */
    public static String encode(String cmd, String... args) {
        if (args.length == 0) return cmd;
        return cmd + ":" + String.join(":", args);
    }

    /**
     * Разбивает сообщение на [команда, остаток].
     * "STATE:120:340" → ["STATE", "120:340"]
     */
    public static String[] split(String message) {
        return message.split(":", 2);
    }
}