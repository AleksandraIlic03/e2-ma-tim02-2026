package com.example.slagalica.models;

import java.util.List;

public class SpojnicaModel {
    private String title;
    private List<String> leftSide;
    private List<String> rightSide; // Pomešani tačni odgovori
    private List<Integer> correctMapping; // Mapiranje: leftSide[i] odgovara rightSide[correctMapping[i]]

    public SpojnicaModel() {}

    public SpojnicaModel(String title, List<String> leftSide, List<String> rightSide, List<Integer> correctMapping) {
        this.title = title;
        this.leftSide = leftSide;
        this.rightSide = rightSide;
        this.correctMapping = correctMapping;
    }

    public String getTitle() { return title; }
    public List<String> getLeftSide() { return leftSide; }
    public List<String> getRightSide() { return rightSide; }
    public List<Integer> getCorrectMapping() { return correctMapping; }
}
