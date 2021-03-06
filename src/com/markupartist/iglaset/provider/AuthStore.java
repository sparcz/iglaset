package com.markupartist.iglaset.provider;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.message.BasicNameValuePair;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.Log;

import com.markupartist.iglaset.util.HttpManager;

public class AuthStore {
    private static final String TAG = "AuthStore";
    private static final String AUTH_BASE_URI = "http://api.iglaset.se/api/authenticate/";
    private static AuthStore sInstance;
    //private ExpiringToken mExpiringToken;

    private AuthStore() {
        
    }

    public static AuthStore getInstance() {
        if (sInstance == null) {
            sInstance = new AuthStore();
        }
        return sInstance;
    }

    public void authenticateUser(Context context)
            throws AuthenticationException, IOException {
        SharedPreferences sharedPreferences = PreferenceManager
            .getDefaultSharedPreferences(context);

        Authentication authResponse = null;
        if (sharedPreferences.contains("preference_username") 
                && sharedPreferences.contains("preference_password")) {
            String username = sharedPreferences.getString("preference_username", "");
            String password = sharedPreferences.getString("preference_password", "");

            try {
                authResponse = authenticateUser(username, password);
            } catch (AuthenticationException e) {
                removeAuthentication(context);
                throw e;
            }

            storeAuthentication(authResponse, context);
        }
    }

    private Authentication authenticateUser(String username, String password) 
            throws AuthenticationException, IOException {
        Log.d(TAG, "authenticate user...");
        final HttpPost post = new HttpPost(AUTH_BASE_URI);
        ArrayList<NameValuePair> nameValuePairs = new ArrayList<NameValuePair>(2);
        nameValuePairs.add(new BasicNameValuePair("username", username));
        nameValuePairs.add(new BasicNameValuePair("password", password)); 
        HttpEntity entity = null;

        post.setEntity(new UrlEncodedFormEntity(nameValuePairs));
        final HttpResponse response = HttpManager.execute(post);
        Authentication authResponse;
        if (response.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
            entity = response.getEntity();
            authResponse = parseResponse(entity.getContent());
            if (!authResponse.looksValid()) {
                Log.i(TAG, "Failed to authenticate user " + username);
                throw new AuthenticationException("Failed to authenticate user " 
                        + username);
            }
        } else {
            Log.w(TAG, "Request failed, http status code was not OK.");
            throw new IOException();
        }

        Log.d(TAG, "Got response " + authResponse.userId);
        return authResponse;
    }

    /**
     * Gets a stored token.
     * @param context the context
     * @return the token
     */
    @Deprecated
    public String getStoredToken(Context context) {
        SharedPreferences sharedPreferences =
            PreferenceManager.getDefaultSharedPreferences(context);

        return sharedPreferences.getString("preference_token", null);
    }

    public boolean hasAuthentication(Context context) {
        try {
            Authentication authentication = getAuthentication(context);
            if (authentication.looksValid()) {
                return true;
            }
        } catch (AuthenticationException e) {
            ; // No auth or failed to request one.
        }
        return false;
    }
    
    public Authentication getAuthentication(Context context)
            throws AuthenticationException {
        SharedPreferences sharedPreferences =
            PreferenceManager.getDefaultSharedPreferences(context);

        if (!sharedPreferences.contains("preference_token")) {
            throw new AuthenticationException("User not authenticated");
        }

        Authentication response = new Authentication();
        response.token = sharedPreferences.getString("preference_token", null);
        response.userId = sharedPreferences.getInt("preference_user_id", 0);

        return response;
    }
    
    private boolean removeAuthentication(Context context) {
        SharedPreferences sharedPreferences =
            PreferenceManager.getDefaultSharedPreferences(context);

        Editor editor = sharedPreferences.edit();
        editor.remove("preference_token");
        editor.remove("preference_user_id");

        return editor.commit();
    }

    public boolean storeAuthentication(Authentication token,
            Context context) {
        SharedPreferences sharedPreferences = PreferenceManager
            .getDefaultSharedPreferences(context);

        Editor editor = sharedPreferences.edit();
        editor.putString("preference_token", token.token);
        editor.putInt("preference_user_id", token.userId);

        return editor.commit();
    }

    private Authentication parseResponse(InputStream inputStream)
            throws IOException {
        Authentication response = new Authentication();
        try {
            XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
            factory.setNamespaceAware(true);
            XmlPullParser xpp = factory.newPullParser();

            xpp.setInput(inputStream, "UTF-8");

            int eventType = xpp.getEventType();
            boolean inToken = false;
            boolean inUserId = false;
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG) {
                    if ("token".equals(xpp.getName())) {
                        inToken = true;
                    } else if ("user_id".equals(xpp.getName())) {
                        inUserId = true;
                    }
                } else if (eventType == XmlPullParser.END_TAG) {
                    if ("token".equals(xpp.getName())) {
                        inToken = false;
                    } else if ("user_id".equals(xpp.getName())) {
                        inUserId = false;
                    }
                } else if (eventType == XmlPullParser.TEXT) {
                    if (inToken) {
                        response.token = xpp.getText();
                    }
                    if (inUserId) {
                        response.userId = Integer.parseInt(xpp.getText());
                    }
                }
                eventType = xpp.next();
            }
        } catch (XmlPullParserException e) {
            Log.w(TAG, "Failed to parse response " + e.getMessage());
            throw new IOException(e.getMessage());
        }

        return response;
    }

    public static class Authentication {
        public String token;
        public int userId;

        public boolean looksValid() {
            return !TextUtils.isEmpty(token) && userId > 0;
        }
    }
}
