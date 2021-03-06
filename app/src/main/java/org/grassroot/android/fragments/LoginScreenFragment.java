package org.grassroot.android.fragments;

import android.content.Context;
import android.os.Bundle;
import android.support.design.widget.TextInputEditText;
import android.support.v4.app.Fragment;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;

import org.grassroot.android.R;
import org.grassroot.android.utils.Utilities;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import butterknife.OnEditorAction;
import butterknife.Unbinder;

/**
 * Created by paballo on 2016/04/26.
 */
public class LoginScreenFragment extends Fragment {

    @BindView(R.id.et_mobile_login) TextInputEditText etNumberInput;
    private String presetNumber;

    private LoginFragmentListener listener;
    private Unbinder unbinder;

    public interface LoginFragmentListener {
        void requestLogin(String mobileNumber);

    }

    public static LoginScreenFragment newInstance(final String presetNumber) {
        LoginScreenFragment fragment = new LoginScreenFragment();
        if (!TextUtils.isEmpty(presetNumber)) {
            fragment.presetNumber = presetNumber;
        }
        return fragment;
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        try {
            listener = (LoginFragmentListener) context;
        } catch (ClassCastException e) {
            throw new ClassCastException(context.toString() + " must implement LoginFragmentListener");
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_login_screen, container, false);
        view.startAnimation(AnimationUtils.loadAnimation(getActivity(), R.anim.fade_in));
        view.setLayoutParams(new ViewGroup.LayoutParams(-1, -1));

        unbinder = ButterKnife.bind(this, view);

        if (!TextUtils.isEmpty(presetNumber)) {
            etNumberInput.setText(presetNumber);
        }

        if (etNumberInput.requestFocus()) {
            InputMethodManager imm = (InputMethodManager) getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.showSoftInput(etNumberInput, InputMethodManager.SHOW_IMPLICIT);
        }

        return view;
    }

    public void setNumber(String number) {
        if (etNumberInput != null) {
            etNumberInput.setText(number);
        }
    }

    @OnClick(R.id.login_submit_number)
    public void onLoginButtonClick() {
        validateAndNext();
    }

    @OnEditorAction(R.id.et_mobile_login)
    public boolean onTextNext(int actionId) {
        if (actionId == EditorInfo.IME_ACTION_NEXT) {
            validateAndNext();
            return true;
        }
        return false;
    }

    private void validateAndNext() {
        final String number = etNumberInput.getText().toString();
        if (TextUtils.isEmpty(number)) {
            etNumberInput.requestFocus();
            etNumberInput.setError(getResources().getString(R.string.input_error_cellphone_hint));
        } else if (!Utilities.checkIfLocalNumber(number)) {
            etNumberInput.requestFocus();
            etNumberInput.setError(getResources().getString(R.string.input_error_cellphone_snackbar));
        } else {
            Log.e("L", "calling listener");
            listener.requestLogin(number);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        unbinder.unbind();
    }
}