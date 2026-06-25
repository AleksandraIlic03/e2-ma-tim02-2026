package com.example.slagalica.models;

import java.util.List;
import java.util.Map;

public class Tournament {
    private String id;
    private List<String> playerIds;
    private List<String> semiFinal1; // [player1, player2]
    private List<String> semiFinal2; // [player3, player4]
    private List<String> finalists;  // [winner1, winner2]
    private String winner;
    private String status; // WAITING, SEMI_FINALS, FINAL, FINISHED

    public Tournament() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public List<String> getPlayerIds() { return playerIds; }
    public void setPlayerIds(List<String> playerIds) { this.playerIds = playerIds; }

    public List<String> getSemiFinal1() { return semiFinal1; }
    public void setSemiFinal1(List<String> semiFinal1) { this.semiFinal1 = semiFinal1; }

    public List<String> getSemiFinal2() { return semiFinal2; }
    public void setSemiFinal2(List<String> semiFinal2) { this.semiFinal2 = semiFinal2; }

    public List<String> getFinalists() { return finalists; }
    public void setFinalists(List<String> finalists) { this.finalists = finalists; }

    public String getWinner() { return winner; }
    public void setWinner(String winner) { this.winner = winner; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
