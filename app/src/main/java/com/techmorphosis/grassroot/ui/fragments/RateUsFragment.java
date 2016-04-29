package com.techmorphosis.grassroot.ui.fragments;

import android.app.DialogFragment;
import android.app.Fragment;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.TextView;

import com.techmorphosis.grassroot.R;
import com.techmorphosis.grassroot.utils.SettingPreference;

/**
 * A simple {@link Fragment} subclass.
 * Activities that contain this fragment must implement the
 * {@link RateUsFragment.OnFragmentInteractionListener} interface
 * to handle interaction events.
 * Use the {@link RateUsFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class RateUsFragment extends DialogFragment
{


    private View view;
    private TextView tvRateNow;
    private TextView tvRateLater;
    private TextView tvRateNever;



    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        view=inflater.inflate(R.layout.fragment_rate_us, container, false);
        findView();
        getDialog().getWindow().requestFeature(Window.FEATURE_NO_TITLE);

        return view;
        
    }

    private void findView()
    {
        tvRateNow = (TextView) view.findViewById(R.id.tv_rate_now);
        tvRateLater = (TextView) view.findViewById(R.id.tv_rate_later);
        tvRateNever = (TextView) view.findViewById(R.id.tv_rate_never);

        tvRateNow.setOnClickListener(RateNowClick());
        tvRateLater.setOnClickListener(RateLaterClick());
        tvRateNever.setOnClickListener(RateNeverClick());

    }

    private View.OnClickListener RateNeverClick() {
        return new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                SettingPreference.setisRateus(getActivity(),true);
            getDialog().dismiss();
            }
        };
    }

    private View.OnClickListener RateLaterClick() {
        return new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                getDialog().dismiss();
            }
        };
    }

    private View.OnClickListener RateNowClick() {
        return new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                SettingPreference.setisRateus(getActivity(),true);
                startActivity(new Intent("android.intent.action.VIEW", Uri.parse("market://details?id=com.techmorphosis.grassroot")));
                getDialog().dismiss();
            }
        };
    }


}
