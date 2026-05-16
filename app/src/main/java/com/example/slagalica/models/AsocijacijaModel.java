package com.example.slagalica.models;

public class AsocijacijaModel {
    private String[] kolonaA;
    private String resenjeA;
    private String[] kolonaB;
    private String resenjeB;
    private String[] kolonaV;
    private String resenjeV;
    private String[] kolonaG;
    private String resenjeG;
    private String konacnoResenje;

    public AsocijacijaModel(String[] kolonaA, String resenjeA, String[] kolonaB, String resenjeB, 
                            String[] kolonaV, String resenjeV, String[] kolonaG, String resenjeG, 
                            String konacnoResenje) {
        this.kolonaA = kolonaA;
        this.resenjeA = resenjeA;
        this.kolonaB = kolonaB;
        this.resenjeB = resenjeB;
        this.kolonaV = kolonaV;
        this.resenjeV = resenjeV;
        this.kolonaG = kolonaG;
        this.resenjeG = resenjeG;
        this.konacnoResenje = konacnoResenje;
    }

    public String[] getKolonaA() { return kolonaA; }
    public String getResenjeA() { return resenjeA; }
    public String[] getKolonaB() { return kolonaB; }
    public String getResenjeB() { return resenjeB; }
    public String[] getKolonaV() { return kolonaV; }
    public String getResenjeV() { return resenjeV; }
    public String[] getKolonaG() { return kolonaG; }
    public String getResenjeG() { return resenjeG; }
    public String getKonacnoResenje() { return konacnoResenje; }
}
