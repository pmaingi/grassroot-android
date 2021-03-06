package org.grassroot.android.activities;

import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.oppwa.mobile.connect.checkout.dialog.CheckoutActivity;
import com.oppwa.mobile.connect.exception.PaymentError;
import com.oppwa.mobile.connect.exception.PaymentException;
import com.oppwa.mobile.connect.provider.Connect;
import com.oppwa.mobile.connect.service.ConnectService;
import com.oppwa.mobile.connect.service.IProviderBinder;

import org.grassroot.android.R;
import org.grassroot.android.fragments.AccountTypeFragment;
import org.grassroot.android.fragments.GiantMessageFragment;
import org.grassroot.android.fragments.NavigationDrawerFragment;
import org.grassroot.android.fragments.SingleInputFragment;
import org.grassroot.android.interfaces.NavigationConstants;
import org.grassroot.android.models.Account;
import org.grassroot.android.models.AccountBill;
import org.grassroot.android.models.responses.RestResponse;
import org.grassroot.android.services.AccountService;
import org.grassroot.android.services.GrassrootRestService;
import org.grassroot.android.utils.ErrorUtils;
import org.grassroot.android.utils.RealmUtils;

import butterknife.BindView;
import butterknife.ButterKnife;
import io.reactivex.functions.Consumer;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Created by luke on 2017/01/13.
 */

public class AccountSignupActivity extends PortraitActivity implements NavigationDrawerFragment.NavigationDrawerCallbacks {

    private static final String TAG = GrassrootExtraActivity.class.getSimpleName();

    @BindView(R.id.acs_toolbar) Toolbar toolbar;
    @BindView(R.id.gextra_signup_drawerlayout) DrawerLayout drawer;

    private String accountName;
    private String billingEmail;
    private String accountType;

    private String accountUid;
    private String paymentId;

    @BindView(R.id.progressBar) ProgressBar progressBar;

    private IProviderBinder binder;
    private ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            binder = (IProviderBinder) iBinder;
            try {
                binder.initializeProvider(Connect.ProviderMode.TEST);
            } catch (PaymentException e) {
                Log.e(TAG, "error initializing payment!");
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            binder = null;
        }
    };

    @Override
    protected void onStart() {
        super.onStart();
        Intent intent = new Intent(this, ConnectService.class);
        startService(intent);
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_account_signup);
        ButterKnife.bind(this);

        setSupportActionBar(toolbar);
        ActionBarDrawerToggle drawerToggle = new ActionBarDrawerToggle(this, drawer, toolbar, R.string.nav_open, R.string.nav_close);
        drawer.addDrawerListener(drawerToggle);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setHomeButtonEnabled(true);
        }
        drawerToggle.syncState();

        Bundle args = getIntent().getExtras();
        if (args != null && args.containsKey(AccountService.UID_FIELD)) {
            showDisabledMsg(args.getString(AccountService.UID_FIELD));
        } else {
            welcomeAndStart();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        unbindService(serviceConnection);
        stopService(new Intent(this, ConnectService.class));
    }

    private void welcomeAndStart() {
        GiantMessageFragment fragment = new GiantMessageFragment.Builder(R.string.gr_extra_welcome_header)
                .setBody(getString(R.string.gr_extra_body))
                .showHomeButton(false)
                .setButtonOne(R.string.gr_extra_start, new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        initiateSignup();
                    }
                }).build();

        getSupportFragmentManager().beginTransaction()
                .add(R.id.acs_fragment_holder, fragment)
                .commit();
    }

    private void initiateSignup() {
        SingleInputFragment fragment = new SingleInputFragment.SingleInputBuilder()
                .header(R.string.account_name_header)
                .explanation(R.string.account_name_expl)
                .hint(R.string.account_name_hint)
                .next(R.string.bt_next)
                .subscriber(new Consumer<String>() {
                    @Override
                    public void accept(String s) {
                        validateNameAndNext(s);
                    }
                })
                .build();

        getSupportFragmentManager().beginTransaction()
                .replace(R.id.acs_fragment_holder, fragment)
                .addToBackStack(null)
                .commit();
    }

    private void validateNameAndNext(String name) {
        accountName = name;

        SingleInputFragment fragment = new SingleInputFragment.SingleInputBuilder()
                .header(R.string.billing_email_header)
                .explanation(R.string.billing_email_expl)
                .next(R.string.bt_next)
                .hint(R.string.billing_email_hint)
                .subscriber(new Consumer<String>() {
                    @Override
                    public void accept(String s) {
                        validateEmailAndNext(s);
                    }
                })
                .build();

        getSupportFragmentManager().beginTransaction()
                .replace(R.id.acs_fragment_holder, fragment)
                .addToBackStack(null)
                .commit();
    }

    private void validateEmailAndNext(String email) {
        billingEmail = email;

        AccountTypeFragment fragment = AccountTypeFragment.newInstance(AccountTypeFragment.STD, new Consumer<String>() {
            @Override
            public void accept(String s) {
                initiatePayment(s);
            }
        });

        getSupportFragmentManager().beginTransaction()
                .replace(R.id.acs_fragment_holder, fragment)
                .addToBackStack(null)
                .commit();
    }

    private void initiatePayment(final String type) {
        accountType = type;
        if (!TextUtils.isEmpty(paymentId)) {
            initiateCheckout(paymentId);
        } else {
            progressBar.setVisibility(View.VISIBLE);
            final String phoneNumber = RealmUtils.loadPreferencesFromDB().getMobileNumber();
            final String code = RealmUtils.loadPreferencesFromDB().getToken();
            GrassrootRestService.getInstance().getApi().initiateAccountSignup(phoneNumber, code,
                    accountName, billingEmail, accountType).enqueue(new Callback<RestResponse<AccountBill>>() {
                @Override
                public void onResponse(Call<RestResponse<AccountBill>> call, Response<RestResponse<AccountBill>> response) {
                    if (response.isSuccessful()) {
                        paymentId = response.body().getData().getPaymentId();
                        Log.e(TAG, "initating payment with ID : " + paymentId);
                        progressBar.setVisibility(View.GONE);
                        initiateCheckout(paymentId);
                    } else {
                        AlertDialog.Builder builder = AccountService.showServerErrorDialog(AccountSignupActivity.this,
                                ErrorUtils.serverErrorText(response.errorBody()), true);
                        builder.show();
                    }
                }

                @Override
                public void onFailure(Call<RestResponse<AccountBill>> call, Throwable t) {
                    AlertDialog.Builder builder = AccountService.showConnectionErrorDialog(AccountSignupActivity.this,
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialogInterface, int i) {
                                  initiatePayment(type);
                                }
                            });
                    builder.show();
                }
            });
        }
    }

    private void showDisabledMsg(final String accountUid) {
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }

        this.accountUid = accountUid;

        GiantMessageFragment messageFragment = new GiantMessageFragment.Builder(R.string.account_disabled_title)
                .setBody(getString(R.string.account_disabled_body))
                .setButtonOne(R.string.account_disabled_payment, new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        paymentFragmentDisabledAccount(accountUid);
                    }
                })
                .setButtonTwo(R.string.account_disabled_online, new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        startActivity(AccountService.openWebApp());
                    }
                })
                .showHomeButton(false)
                .build();

        getSupportFragmentManager().beginTransaction()
                .add(R.id.acs_fragment_holder, messageFragment, "message")
                .commit();
    }

    private void paymentFragmentDisabledAccount(final String accountUid) {
        final String phoneNumber = RealmUtils.loadPreferencesFromDB().getMobileNumber();
        final String code = RealmUtils.loadPreferencesFromDB().getToken();
        progressBar.setVisibility(View.VISIBLE);
        Log.e(TAG, "okay, trying to initiate payment ...");
        GrassrootRestService.getInstance().getApi().initiateAccountEnablePayment(phoneNumber, code, accountUid)
                .enqueue(new Callback<RestResponse<String>>() {
                    @Override
                    public void onResponse(Call<RestResponse<String>> call, Response<RestResponse<String>> response) {
                        progressBar.setVisibility(View.GONE);
                        Log.e(TAG, "got a response: " + response);
                        if (response.isSuccessful()) {
                            paymentId = response.body().getData();
                            Intent i = AccountService.initiateCheckout(AccountSignupActivity.this,
                                    paymentId, getString(R.string.billing_enable_title));
                            startActivityForResult(i, CheckoutActivity.CHECKOUT_ACTIVITY);
                        } else {
                            final String restMessage = ErrorUtils.getRestMessage(response.errorBody());
                            if ("ACCOUNT_ENABLED".equals(restMessage)) {
                                Toast.makeText(AccountSignupActivity.this, R.string.account_enabled_async,
                                        Toast.LENGTH_SHORT).show();
                                showSuccessDialogAndExit(null);
                            } else {
                                AlertDialog.Builder builder = AccountService.showServerErrorDialog(AccountSignupActivity.this,
                                        ErrorUtils.serverErrorText(response.errorBody()), true);
                                builder.show();
                            }
                        }
                    }

                    @Override
                    public void onFailure(Call<RestResponse<String>> call, Throwable t) {
                        progressBar.setVisibility(View.GONE);
                        AlertDialog.Builder builder = AccountService.showConnectionErrorDialog(AccountSignupActivity.this,
                                new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialogInterface, int i) { paymentFragmentDisabledAccount(accountUid);
                                    }
                        });
                        builder.show();
                    }
                });
    }

    private void initiateCheckout(final String checkoutId) {
        final String paymentTitle = getString(R.string.billing_signup_title);
        Intent intent = AccountService.initiateCheckout(this, checkoutId, paymentTitle);
        startActivityForResult(intent, CheckoutActivity.CHECKOUT_ACTIVITY);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (resultCode) {
            case CheckoutActivity.RESULT_OK:
                validateCheckoutResult(accountUid, paymentId);
                break;
            case CheckoutActivity.RESULT_CANCELED:
                showCancelledDialogAndOptions();
                break;
            case CheckoutActivity.RESULT_ERROR:
                PaymentError error = data.getExtras().getParcelable(CheckoutActivity.CHECKOUT_RESULT_ERROR);
                Log.e(TAG, "payment error! log: " + (error == null ? "null result" : error.getErrorMessage() + ", code: " + error.getErrorCode()));
                showErrorDialogAndOptions();
        }
    }

    private void validateCheckoutResult(final String accountUid, final String checkoutId) {
        final String phoneNumber = RealmUtils.loadPreferencesFromDB().getMobileNumber();
        final String code = RealmUtils.loadPreferencesFromDB().getToken();
        GrassrootRestService.getInstance().getApi().checkPaymentResult(phoneNumber, code, accountUid, checkoutId)
                .enqueue(new Callback<RestResponse<Account>>() {
                    @Override
                    public void onResponse(Call<RestResponse<Account>> call, Response<RestResponse<Account>> response) {
                        if (response.isSuccessful()) {
                            Log.e(TAG, "and .... that's a wrap! payment done");
                            showSuccessDialogAndExit(response.body().getData());
                        } else {
                            AlertDialog.Builder builder = AccountService.showServerErrorDialog(AccountSignupActivity.this,
                                    ErrorUtils.serverErrorText(response.errorBody()), true);
                            builder.show();
                        }
                    }

                    @Override
                    public void onFailure(Call<RestResponse<Account>> call, Throwable t) {
                        AlertDialog.Builder builder = AccountService.showConnectionErrorDialog(AccountSignupActivity.this,
                                new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialogInterface, int i) {
                                        validateCheckoutResult(accountUid, checkoutId);
                                    }
                                });
                        builder.show();
                    }
                });
    }

    private void showSuccessDialogAndExit(final Account account) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(R.string.account_paid_success)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        exitToGrExtra(account);
                    }
                })
                .setCancelable(true)
                .setOnCancelListener(new DialogInterface.OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface dialogInterface) {
                        exitToGrExtra(account);
                    }
                });
        builder.show();
    }

    private void exitToGrExtra(final Account account) {
        Intent i = new Intent(this, GrassrootExtraActivity.class);
        if (account != null) {
            i.putExtra(AccountService.OBJECT_FIELD, account);
        }
        startActivity(i);
        finish();
    }

    private void showCancelledDialogAndOptions() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(R.string.account_paid_cancelled)
                .setPositiveButton(R.string.account_payment_error_tryagain, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        initiateCheckout(paymentId);
                    }
                })
                .setNegativeButton(R.string.account_payment_error_exit, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        exitToHome(null, true);
                    }
                })
                .setCancelable(true);
        builder.show();
    }

    private void showErrorDialogAndOptions() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(R.string.account_paid_error)
                .setPositiveButton(R.string.account_payment_error_tryagain, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        initiateCheckout(paymentId);
                    }
                })
                .setNegativeButton(R.string.account_payment_error_exit, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        exitToHome(null, true);
                    }
                })
                .setCancelable(true);
        builder.show();
    }

    private void exitToHome(final String openOnTab, boolean finish) {
        // consider just rewinding in stack
        Intent i = new Intent(this, HomeScreenActivity.class);
        if (!TextUtils.isEmpty(openOnTab)) {
            i.putExtra(NavigationConstants.HOME_OPEN_ON_NAV, openOnTab);
        }
        startActivity(i);
        if (finish) {
            finish();
        }
    }

    @Override
    public void onNavigationDrawerItemSelected(String tag) {
        if (drawer != null) {
            drawer.closeDrawer(GravityCompat.START);
        }
        exitToHome(tag, false); // finish false so can tap back to this
    }

}
