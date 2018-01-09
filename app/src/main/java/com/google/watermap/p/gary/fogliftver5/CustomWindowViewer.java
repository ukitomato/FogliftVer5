package com.google.watermap.p.gary.fogliftver5;

import android.support.v4.app.FragmentActivity;
import android.view.View;
import android.widget.ImageView;
import android.widget.RatingBar;
import android.widget.TextView;

import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.Marker;


/**
 * Created by yuki on 2017/12/08.
 */

public class CustomWindowViewer implements GoogleMap.InfoWindowAdapter {

    private final View infoView;


    public CustomWindowViewer(FragmentActivity fragment) {
        infoView = fragment.getLayoutInflater().inflate(R.layout.custom_marker_window_diarrhea, null);
    }

    @Override
    public View getInfoWindow(Marker marker) {
        DatabasePlace dbPlace = (DatabasePlace) marker.getTag();
        ((TextView) infoView.findViewById(R.id.place_name)).setText(dbPlace.getName());
        ((TextView) infoView.findViewById(R.id.place_kind)).setText(dbPlace.getKind());
        ((RatingBar) infoView.findViewById(R.id.place_rate)).setRating(dbPlace.getLevel());
        ((TextView) infoView.findViewById(R.id.place_info)).setText(dbPlace.getInformation());
        ImageView infoImageView = infoView.findViewById(R.id.info_picture_image_view);
        infoImageView.setImageResource(dbPlace.getImageDrawableID());
        return infoView;
    }

    @Override
    public View getInfoContents(Marker marker) {
        return null;
    }
}
