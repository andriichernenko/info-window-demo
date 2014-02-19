package com.example.infowindowdemo;

import android.content.Intent;
import android.graphics.Point;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.AbsoluteLayout;
import android.widget.FrameLayout;
import android.widget.TextView;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.Projection;
import com.google.android.gms.maps.model.*;

import java.util.HashMap;
import java.util.Map;

import static android.view.View.*;
import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;

public class MapFragment
        extends
        com.google.android.gms.maps.MapFragment
        implements
        GoogleMap.OnMapClickListener,
        GoogleMap.OnMarkerClickListener,
        OnClickListener {

    private static Spot[] SPOTS_ARRAY = new Spot[]{
            new Spot("Киев", new LatLng(50.4546600, 30.5238000)),
            new Spot("Одесса", new LatLng(46.4774700, 30.7326200)),
            new Spot("Харьков", new LatLng(50.0000000, 36.2500000)),
            new Spot("Львов", new LatLng(49.8382600, 24.0232400)),
            new Spot("Донецк", new LatLng(48.0000000, 37.8000000)),
    };

    //интервал обновления положения всплывающего окна.
    //для плавности необходимо 60 fps, то есть 1000 ms / 60 = 16 ms между обновлениями.
    private static final int POPUP_POSITION_REFRESH_INTERVAL = 16;
    //длительность анимации перемещения карты
    private static final int ANIMATION_DURATION = 500;

    private Map<Marker, Spot> spots;

    //точка на карте, соответственно перемещению которой перемещается всплывающее окно
    private LatLng trackedPosition;

    //Handler, запускающий обновление окна с заданным интервалом
    private Handler handler;

    //Runnable, который обновляет положение окна
    private Runnable positionUpdaterRunnable;

    //смещения всплывающего окна, позволяющее
    //скорректировать его положение относительно маркера
    private int popupXOffset;
    private int popupYOffset;
    //высота маркера
    private int markerHeight;
    private AbsoluteLayout.LayoutParams overlayLayoutParams;

    //слушатель, который будет обновлять смещения
    private ViewTreeObserver.OnGlobalLayoutListener infoWindowLayoutListener;

    //контейнер всплывающего окна
    private View infoWindowContainer;
    private TextView textView;
    private TextView button;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        spots = new HashMap<>();
        markerHeight = getResources().getDrawable(R.drawable.pin).getIntrinsicHeight();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment, null);

        FrameLayout containerMap = (FrameLayout) rootView.findViewById(R.id.container_map);
        View mapView = super.onCreateView(inflater, container, savedInstanceState);
        containerMap.addView(mapView, new FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT));

        GoogleMap map = getMap();
        map.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(48.35, 31.16), 5.5f));
        map.getUiSettings().setRotateGesturesEnabled(false);
        map.setOnMapClickListener(this);
        map.setOnMarkerClickListener(this);

        map.clear();
        spots.clear();
        BitmapDescriptor icon = BitmapDescriptorFactory.fromResource(R.drawable.pin);
        for (Spot spot : SPOTS_ARRAY) {
            Marker marker = map.addMarker(new MarkerOptions()
                    .position(spot.getPosition())
                    .title("Title")
                    .snippet("Subtitle")
                    .icon(icon));
            spots.put(marker, spot);
        }

        infoWindowContainer = rootView.findViewById(R.id.container_popup);
        //подписываемся на изменения размеров всплывающего окна
        infoWindowLayoutListener = new InfoWindowLayoutListener();
        infoWindowContainer.getViewTreeObserver().addOnGlobalLayoutListener(infoWindowLayoutListener);
        overlayLayoutParams = (AbsoluteLayout.LayoutParams) infoWindowContainer.getLayoutParams();

        textView = (TextView) infoWindowContainer.findViewById(R.id.textview_title);
        button = (TextView) infoWindowContainer.findViewById(R.id.button_view_article);
        button.setOnClickListener(this);

        return rootView;
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        //очистка
        handler = new Handler(Looper.getMainLooper());
        positionUpdaterRunnable = new PositionUpdaterRunnable();
        handler.post(positionUpdaterRunnable);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();

        infoWindowContainer.getViewTreeObserver().removeGlobalOnLayoutListener(infoWindowLayoutListener);
        handler.removeCallbacks(positionUpdaterRunnable);
        handler = null;
    }

    @Override
    public void onClick(View v) {
        String name = (String) v.getTag();
        startActivity(new Intent(Intent.ACTION_VIEW).setData(Uri.parse("http://www.google.com/search?q=" + name)));
    }

    @Override
    public void onMapClick(LatLng latLng) {
        infoWindowContainer.setVisibility(INVISIBLE);
    }

    @Override
    public boolean onMarkerClick(Marker marker) {
        GoogleMap map = getMap();
        Projection projection = map.getProjection();
        trackedPosition = marker.getPosition();
        Point trackedPoint = projection.toScreenLocation(trackedPosition);
        trackedPoint.y -= popupYOffset / 2;
        LatLng newCameraLocation = projection.fromScreenLocation(trackedPoint);
        map.animateCamera(CameraUpdateFactory.newLatLng(newCameraLocation), ANIMATION_DURATION, null);

        Spot spot = spots.get(marker);
        textView.setText(spot.getName());
        button.setTag(spot.getName());

        infoWindowContainer.setVisibility(VISIBLE);

        return true;
    }

    private class InfoWindowLayoutListener implements ViewTreeObserver.OnGlobalLayoutListener {
        @Override
        public void onGlobalLayout() {
            //размеры окна изменились, обновляем смещения
            popupXOffset = infoWindowContainer.getWidth() / 2;
            popupYOffset = infoWindowContainer.getHeight();
        }
    }

    private class PositionUpdaterRunnable implements Runnable {
        private int lastXPosition = Integer.MIN_VALUE;
        private int lastYPosition = Integer.MIN_VALUE;

        @Override
        public void run() {
            //помещаем в очередь следующий цикл обновления
            handler.postDelayed(this, POPUP_POSITION_REFRESH_INTERVAL);

            //если всплывающее окно скрыто, ничего не делаем
            if (trackedPosition != null && infoWindowContainer.getVisibility() == VISIBLE) {
                Point targetPosition = getMap().getProjection().toScreenLocation(trackedPosition);

                //если положение окна не изменилось, ничего не делаем
                if (lastXPosition != targetPosition.x || lastYPosition != targetPosition.y) {
                    //обновляем положение
                    overlayLayoutParams.x = targetPosition.x - popupXOffset;
                    overlayLayoutParams.y = targetPosition.y - popupYOffset - markerHeight -30;
                    infoWindowContainer.setLayoutParams(overlayLayoutParams);

                    //запоминаем текущие координаты
                    lastXPosition = targetPosition.x;
                    lastYPosition = targetPosition.y;
                }
            }
        }
    }
}
