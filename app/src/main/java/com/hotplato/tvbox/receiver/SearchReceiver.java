package com.hotplato.tvbox.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.hotplato.tvbox.ui.MainActivity;

/**
 * @author pj567
 * @date :2021/1/5
 * @description:
 */
public class SearchReceiver extends BroadcastReceiver {
    public static String action = "android.content.movie.search.Action";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (action.equals(intent.getAction()) && intent.getExtras() != null) {
            Intent newIntent = new Intent(context, MainActivity.class);
            newIntent.putExtra(MainActivity.EXTRA_ROUTE, MainActivity.ROUTE_SEARCH);
            newIntent.putExtra(MainActivity.EXTRA_QUERY, intent.getExtras().getString("title"));
            newIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            context.startActivity(newIntent);
        }
    }
}
