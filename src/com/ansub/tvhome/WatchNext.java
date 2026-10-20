package com.ansub.tvhome;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;

import java.util.ArrayList;
import java.util.List;

// Programs apps published for the system "continue watching" row.
// A normal install often lacks ACCESS_WATCHED_PROGRAMS. Then this returns nothing
// and the row stays hidden.
class WatchNext {

    private static final Uri URI = Uri.parse("content://android.media.tv/watch_next_program");

    static final class Item {
        String title;
        String episodeTitle;
        String season;
        String episode;
        String poster;
        String intent;
        String pkg;
        long position;
        long duration;
    }

    static List<Item> load(Context context) {
        List<Item> out = new ArrayList<>();
        Cursor cursor = null;
        try {
            cursor = context.getContentResolver().query(URI, new String[] {
                    "title",
                    "episode_title",
                    "season_display_number",
                    "episode_display_number",
                    "poster_art_uri",
                    "thumbnail_uri",
                    "intent_uri",
                    "last_playback_position_millis",
                    "duration_millis",
                    "package_name"
            }, null, null, "last_engagement_time_utc_millis DESC");
            if (cursor == null) return out;
            while (cursor.moveToNext() && out.size() < 4) {
                Item item = new Item();
                item.title = cursor.getString(0);
                item.episodeTitle = cursor.getString(1);
                item.season = cursor.getString(2);
                item.episode = cursor.getString(3);
                String poster = cursor.getString(4);
                item.poster = poster != null && poster.length() > 0 ? poster : cursor.getString(5);
                item.intent = cursor.getString(6);
                item.position = cursor.isNull(7) ? 0 : cursor.getLong(7);
                item.duration = cursor.isNull(8) ? 0 : cursor.getLong(8);
                item.pkg = cursor.getString(9);
                if (item.title == null || item.title.isEmpty()) item.title = item.episodeTitle;
                if (item.title == null || item.title.isEmpty()) continue;
                if ((item.intent == null || item.intent.isEmpty()) && (item.pkg == null || item.pkg.isEmpty()))
                    continue;
                out.add(item);
            }
        } catch (Exception ignored) {
            out.clear();
        } finally {
            if (cursor != null) cursor.close();
        }
        return out;
    }
}
