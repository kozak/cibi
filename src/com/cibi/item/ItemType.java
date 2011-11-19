package com.cibi.item;

import com.cibi.R;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * @author morswin
 */
public enum ItemType {
    POLICE(R.drawable.placemark_police),
    PARKING_SPOT(R.drawable.placemark_parking),
    TRAFFIC_JAM(R.drawable.placemark_jam),
    PHOTO_RADAR(R.drawable.placemark_radar),
    ME(R.drawable.navigation);

    ItemType(int icon) {
        this.icon = icon;
    }

    int icon;

    public int getIcon() {
        return icon;
    }


    public static ItemType fromOrdinal(int o) {
        for (ItemType type : ItemType.values()) {
            if (o == type.ordinal()) {
                return type;
            }
        }
        throw new IllegalStateException("Unssuported type!");
    }

    public static String[] getEnabled(Set<ItemType> enabled) {
        String[] e = new String[enabled.size()];
        List<ItemType> typesList = new ArrayList<ItemType>(enabled);
        for (int i = 0; i < typesList.size(); i++) {
            ItemType type = typesList.get(i);
            e[i] = type.toString();

        }
        return e;
    }
}
