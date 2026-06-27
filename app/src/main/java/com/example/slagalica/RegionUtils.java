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
        // Koristimo neke od ugrađenih Android ikona kao primer
        REGION_ICONS.put("Beogradski region", android.R.drawable.ic_menu_myplaces);
        REGION_ICONS.put("Vojvodina", android.R.drawable.ic_menu_gallery);
        REGION_ICONS.put("Šumadijski okrug", android.R.drawable.ic_menu_directions);
        REGION_ICONS.put("Podunavski okrug", android.R.drawable.ic_menu_compass);
        REGION_ICONS.put("Braničevski okrug", android.R.drawable.ic_menu_camera);
        REGION_ICONS.put("Pomoravski okrug", android.R.drawable.ic_menu_day);
        REGION_ICONS.put("Borski okrug", android.R.drawable.ic_menu_manage);
        REGION_ICONS.put("Zaječarski okrug", android.R.drawable.ic_menu_agenda);
        REGION_ICONS.put("Nišavski okrug", android.R.drawable.ic_menu_send);
        REGION_ICONS.put("Toplički okrug", android.R.drawable.ic_menu_view);
        REGION_ICONS.put("Pirotski okrug", android.R.drawable.ic_menu_call);
        REGION_ICONS.put("Jablanički okrug", android.R.drawable.ic_menu_search);
        REGION_ICONS.put("Pčinjski okrug", android.R.drawable.ic_menu_add);
        REGION_ICONS.put("Rasinski okrug", android.R.drawable.ic_menu_edit);
        REGION_ICONS.put("Raški okrug", android.R.drawable.ic_menu_save);
        REGION_ICONS.put("Moravički okrug", android.R.drawable.ic_menu_help);
        REGION_ICONS.put("Zlatiborski okrug", android.R.drawable.ic_menu_info_details);
        REGION_ICONS.put("Kolubarski okrug", android.R.drawable.ic_menu_share);
        REGION_ICONS.put("Mačvanski okrug", android.R.drawable.ic_menu_slideshow);
    }

    public static int getIconRes(String regionName) {
        Integer res = REGION_ICONS.get(regionName);
        return res != null ? res : android.R.drawable.ic_menu_mapmode;
    }
}