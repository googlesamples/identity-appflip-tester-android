/*
 * Copyright 2019 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.appfliptesttool;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.content.res.Resources;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Formatter;

public class MainActivity extends AppCompatActivity {
  private static final String TAG = "Main Activity";
  private static final String EXTRA_APP_FLIP_CLIENT_ID = "CLIENT_ID";
  private static final String EXTRA_APP_FLIP_SCOPES = "SCOPE";
  private static final String EXTRA_APP_FLIP_REDIRECT_URI = "REDIRECT_URI";
  private static final String EXTRA_APP_FLIP_AUTHORIZATION_CODE = "AUTHORIZATION_CODE";
  private static final String EXTRA_APP_FLIP_ERROR_TYPE = "ERROR_TYPE";
  private static final String EXTRA_APP_FLIP_ERROR_CODE = "ERROR_CODE";
  private static final int APP_FLIP_UNKNOWN_ERROR_TYPE = -1;
  private static final int APP_FLIP_RESULT_ERROR = -2;
  private static final String EXTRA_APP_FLIP_ERROR_DESCRIPTION = "ERROR_DESCRIPTION";
  private static final int APP_FLIP_RECOVERABLE_ERROR = 1;
  private static final int APP_FLIP_UNRECOVERABLE_ERROR = 2;
  private static final int APP_FLIP_INVALID_REQUEST_ERROR = 3;
  private static final int APP_FLIP_USER_DENIED_3P_CONSENT_ERROR_CODE = 13;
  private static final int RC_APP_FLIP = 100;
  private String[] scopes;
  private String redirectUri;
  private TextView logTextView;
  private EditText appNameToFlip, clientID, signatureEditText, intentFilterName;
  private Context context;
  private Intent intent;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);
    Resources res = getResources();
    Button appFlipButton = findViewById(R.id.flipButton);
    logTextView = findViewById(R.id.resultText);
    appNameToFlip = findViewById(R.id.appIdEditText);
    intentFilterName = findViewById(R.id.intentFilterEditText);
    context = getApplicationContext();
    signatureEditText = findViewById(R.id.signatureEditText);
    clientID = findViewById(R.id.clientIdEditText);
    scopes = res.getStringArray(R.array.scope);
    redirectUri = getString(R.string.redirect_uri);

    appFlipButton.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v){
        logTextView.setText("");
        // Check input intent filter name and client ID
        if(isEmpty(intentFilterName)){
          Toast.makeText(context, "You did not enter app intent filter name",
              Toast.LENGTH_LONG).show();
          Log.e(TAG, "Intent filter name is empty");
          logTextView.setText("Intent filter name is empty\n");
          return;
        }
        if(isEmpty(clientID)){
          Toast.makeText(context, "You did not enter clientID",
              Toast.LENGTH_LONG).show();
          Log.e(TAG, "Client ID name is empty");
          logTextView.setText("Client ID name is empty\n");
          return;
        }
        // check certificate
        String userInputSig = signatureEditText.getText().toString();
        String packageName = appNameToFlip.getText().toString();
        String certificate = getCertificateFingerprint(context.getPackageManager(), packageName);
        if(certificate == null){
          Log.e(TAG,"Package name not found!");
          logTextView.append("Package name invalid, can't flip!\n");
          return;
        }
        Log.i(TAG, "App certificate: " + certificate);
        logTextView.append("Signature is " + certificate + "\n");
        if(!userInputSig.equalsIgnoreCase(certificate)){
          Toast.makeText(context, "Signature doesn't match\nPlease check", Toast.LENGTH_LONG).show();
          Log.e(TAG, "Signature mismatch, can't flip!");
          logTextView.append("Signature mismatch, can't flip!\n");
          return;
        }
        Toast.makeText(context, "Signature match", Toast.LENGTH_SHORT).show();
        // start appflip
        intent = new Intent();
        intent.setAction(intentFilterName.getText().toString());
        intent.setPackage(packageName);
        intent.putExtra(EXTRA_APP_FLIP_CLIENT_ID, clientID.getText().toString());
        intent.putExtra(EXTRA_APP_FLIP_SCOPES, scopes);
        intent.putExtra(EXTRA_APP_FLIP_REDIRECT_URI, redirectUri);
        try {
          logTextView.append("Starting AppFlip...\n");
          startActivityForResult(intent, RC_APP_FLIP);
        } catch (ActivityNotFoundException e) {
          Toast.makeText(context, "Could not open the app. Invalid intent filter action name!",
              Toast.LENGTH_LONG).show();
          Log.e(TAG, e.getMessage());
          logTextView.append("Could not open the app. Invalid intent filter action name!\n");
        }
      }
    });
  }

  @Override
  protected void onActivityResult(int requestCode, int resultCode, Intent data){
    if (requestCode == RC_APP_FLIP) {
      switch (resultCode) {
        case RESULT_OK:
          String authCode = data.getStringExtra(EXTRA_APP_FLIP_AUTHORIZATION_CODE);
          if (!TextUtils.isEmpty(authCode)) {
            Log.i(TAG, "Auth Code was received successfully: " + authCode);
            logTextView.append("Auth Code received: " + authCode);
          } else {
            Log.i(TAG, "Fail: return auth code is empty");
            logTextView.append("Fail: return auth code is empty");
          }
          break;
        case RESULT_CANCELED:
          Log.i(TAG, "Flow was cancelled");
          logTextView.append("Flow was cancelled");
          break;
        case APP_FLIP_RESULT_ERROR:
        default:
          int errorType = data.getIntExtra(EXTRA_APP_FLIP_ERROR_TYPE, APP_FLIP_UNKNOWN_ERROR_TYPE);
          int errorCode = data.getIntExtra(EXTRA_APP_FLIP_ERROR_CODE, -1);
          String errorDescription = data.getStringExtra(EXTRA_APP_FLIP_ERROR_DESCRIPTION);
          String formattedErrorDescription = String.format("description: %s",
              TextUtils.isEmpty(errorDescription) ? "n/a" : errorDescription);
          if (errorType == APP_FLIP_RECOVERABLE_ERROR) {
            Log.e(TAG, String.format("App Flip Error: Recoverable, error code: %d, %s",
                errorCode, formattedErrorDescription));
            logTextView.append("App Flip Error: Recoverable error. Error code: " + errorCode);
          } else if (errorType == APP_FLIP_INVALID_REQUEST_ERROR) {
            Log.e(TAG, String.format(
                "App Flip Error: Invalid request or parameters, error code: %d, %s",
                errorCode, formattedErrorDescription));
            logTextView.append("App Flip Error: Invalid request or parameters. Error code: " + errorCode);
          } else if (errorType == APP_FLIP_UNRECOVERABLE_ERROR) {
            // User has rejected the consent explicitly in the 3P app.
            if (errorCode == APP_FLIP_USER_DENIED_3P_CONSENT_ERROR_CODE) {
              // User has denied the consent
              logTextView.append("App Flip Error: Consent was denied by user");
            } else {
              // Unrecoverable error
              Log.e(TAG, String.format("App Flip Error: Unrecoverable, error code: %d, %s",
                  errorCode, formattedErrorDescription));
              logTextView.append("App Flip Error: Unrecoverable. Error code: " + errorCode);
            }
          } else {
            Log.e(TAG,String.format("App Flip Error: Unknown Error, Error type missing"));
            logTextView.append("App Flip Error: Unknown Error");
          }
      }
    }
  }

  private boolean isEmpty(EditText editText) {
    return TextUtils.isEmpty(editText.getText().toString().trim());
  }

  private String getCertificateFingerprint(
      PackageManager packageManager, String packageName) {
    try {
      PackageInfo packageInfo =
          packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNATURES);
      Signature[] signatures = packageInfo.signatures;
      InputStream input = new ByteArrayInputStream(signatures[0].toByteArray());
      CertificateFactory certificateFactory = CertificateFactory.getInstance("X509");
      X509Certificate certificate = (X509Certificate) certificateFactory.generateCertificate(input);
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      byte[] publicKey = md.digest(certificate.getEncoded());
      return byte2HexFormatted(publicKey);
    } catch (CertificateException | NoSuchAlgorithmException e) {
      Log.e(TAG, "Failed to process the certificate", e);
      Toast.makeText(context, "Failed to process the certificate", Toast.LENGTH_LONG).show();
      logTextView.append("Failed to process the certificate\n");
    } catch (PackageManager.NameNotFoundException e) {
      Log.e(TAG, "Failed to find an app with the given package name", e);
      Toast.makeText(context, "Failed to find an app with the given package name",
          Toast.LENGTH_LONG).show();
      logTextView.append("Failed to find an app with the given package name\n");
    }
    return null;
  }

  private static String byte2HexFormatted(byte[] byteArray) {
    Formatter formatter = new Formatter();
    for (int i = 0; i < byteArray.length - 1; i++) {
      formatter.format("%02x:", byteArray[i]);
    }
    formatter.format("%02x", byteArray[byteArray.length - 1]);
    return formatter.toString();
  }
}
