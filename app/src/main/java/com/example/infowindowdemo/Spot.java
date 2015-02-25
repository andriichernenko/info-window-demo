package com.example.infowindowdemo;

import com.google.android.gms.maps.model.LatLng;

public class Spot {
    private String name;
    private LatLng position;

    public Spot(String name, LatLng position) {
        this.name = name;
        this.position = position;
    }

    public String getName() {
        return name;
    }

    public LatLng getPosition() {
        return position;
    }
}
