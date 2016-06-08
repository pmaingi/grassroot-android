package org.grassroot.android.fragments;


import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.grassroot.android.R;
import org.grassroot.android.activities.CreateGroupActivity;
import org.grassroot.android.activities.GroupJoinActivity;

public class NewWelcomeFragment extends android.support.v4.app.Fragment {

    private View view;
    private Toolbar toolbar;
    private TextView toolbarText;

   // PageIndicator mIndicator;
    private NewFragmentCallbacks mCallbacks;
    private Button bt_joingroup;
    private Button bt_startgroup;
    private LinearLayout llTxt;


    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        try {
            mCallbacks = (NewFragmentCallbacks) activity;
            Log.e("onAttach", "Attached");
        } catch (ClassCastException e) {
            throw new ClassCastException("Activity must implement Fragment One.");
        }
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState)    {
        super.onActivityCreated(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        view = inflater.inflate(R.layout.activity_welcome_nogroup, container, false);
        findView();
        setUpToolbar();
        return view;
    }

    private void setUpToolbar() {
        toolbarText.setText("Welcome");
        toolbar.setNavigationIcon(getResources().getDrawable(R.drawable.btn_navigation));
        toolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mCallbacks.menuClick();
            }
        });
        startAnimation();
    }

    private void startAnimation() {

        llTxt.setVisibility(View.VISIBLE);
        llTxt.startAnimation(AnimationUtils.loadAnimation(getActivity(), R.anim.fade_in));

    }

    private void findView()
    {
        toolbar = (Toolbar) view.findViewById(R.id.toolbar);
        llTxt = (LinearLayout) view.findViewById(R.id.ll_txt);
        toolbarText = (TextView) toolbar.findViewById(R.id.txt_welcometitle);
        bt_joingroup=(Button)view.findViewById(R.id.bt_joingroup);
        bt_joingroup.setOnClickListener(joingroup());
        bt_startgroup=(Button)view.findViewById(R.id.bt_startgroup);
        bt_startgroup.setOnClickListener(startgroup());
        
    }

    private View.OnClickListener startgroup() {
            return  new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent startgroup= new Intent(getActivity(), CreateGroupActivity.class);
                    startActivity(startgroup);
                }
            };
    }

    private View.OnClickListener joingroup() {
        return  new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent joingroup= new Intent(getActivity(), GroupJoinActivity.class);
                startActivity(joingroup);
            }
        };
    }


    public interface NewFragmentCallbacks {
        void menuClick();
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mCallbacks = null;
        Log.e("onDetach", "Detached");
    }


}