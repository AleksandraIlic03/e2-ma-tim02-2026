package com.example.slagalica;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RegionUtils {

    public static final List<String> ALL_REGIONS = Arrays.asList(
            "Beogradski region",
            "Vojvodina",
            "Šumadijski okrug",
            "Podunavski okrug",
            "Braničevski okrug",
            "Pomoravski okrug",
            "Borski okrug",
            "Zaječarski okrug",
            "Nišavski okrug",
            "Toplički okrug",
            "Pirotski okrug",
            "Jablanički okrug",
            "Pčinjski okrug",
            "Rasinski okrug",
            "Raški okrug",
            "Moravički okrug",
            "Zlatiborski okrug",
            "Kolubarski okrug",
            "Mačvanski okrug"
    );

    private static final Map<String, Integer> REGION_ICONS = new HashMap<>();

    static {
        REGION_ICONS.put("Beogradski region", R.drawable.ic_region_beograd);
        REGION_ICONS.put("Vojvodina", R.drawable.ic_region_vojvodina);
        REGION_ICONS.put("Šumadijski okrug", R.drawable.ic_region_sumadija);
        REGION_ICONS.put("Podunavski okrug", R.drawable.ic_region_podunavski);
        REGION_ICONS.put("Braničevski okrug", R.drawable.ic_region_branicevski);
        REGION_ICONS.put("Pomoravski okrug", R.drawable.ic_region_pomoravski);
        REGION_ICONS.put("Borski okrug", R.drawable.ic_region_borski);
        REGION_ICONS.put("Zaječarski okrug", R.drawable.ic_region_zajecarski);
        REGION_ICONS.put("Nišavski okrug", R.drawable.ic_region_nisavski);
        REGION_ICONS.put("Toplički okrug", R.drawable.ic_region_toplicki);
        REGION_ICONS.put("Pirotski okrug", R.drawable.ic_region_pirotski);
        REGION_ICONS.put("Jablanički okrug", R.drawable.ic_region_jablanicki);
        REGION_ICONS.put("Pčinjski okrug", R.drawable.ic_region_pcinjski);
        REGION_ICONS.put("Rasinski okrug", R.drawable.ic_region_rasinski);
        REGION_ICONS.put("Raški okrug", R.drawable.ic_region_raski);
        REGION_ICONS.put("Moravički okrug", R.drawable.ic_region_moravicki);
        REGION_ICONS.put("Zlatiborski okrug", R.drawable.ic_region_zlatiborski);
        REGION_ICONS.put("Kolubarski okrug", R.drawable.ic_region_kolubarski);
        REGION_ICONS.put("Mačvanski okrug", R.drawable.ic_region_macvanski);
    }

    public static int getIconRes(String regionName) {
        Integer res = REGION_ICONS.get(regionName);
        return res != null ? res : R.drawable.ic_user;
    }
}