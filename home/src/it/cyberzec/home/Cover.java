package it.cyberzec.home;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

/** CarPlay sends no artwork: look the cover up online from title and artist (one lookup per new title).
 *  Music: iTunes Search API (public). Videos: YouTube Data API v3, only if the owner set a key (prefs "cover", "yt_key"). */
final class Cover {
    interface Done { void cover(String title, Bitmap b); }

    /** Music has an album; YouTube videos through CarPlay have none. */
    static void find(final String title, final String artist, final String album, final String ytKey, final Done done) {
        new Thread(new Runnable() {
            @Override public void run() {
                String q = artist == null || artist.isEmpty() ? title : title + " " + artist;
                boolean music = album != null && !album.isEmpty();
                Bitmap b = null;
                try {
                    String url = music ? itunes(q) : ytKey == null || ytKey.isEmpty() ? null : youtube(q, ytKey);
                    if (url != null) {
                        byte[] img = get(url);
                        b = BitmapFactory.decodeByteArray(img, 0, img.length);
                    }
                } catch (Exception e) {
                    Log.i(HomeActivity.TAG, "copertina non trovata: " + e);
                }
                done.cover(title, b);
            }
        }, "copertina").start();
    }

    private static String itunes(String q) throws Exception {
        return Logic.itunesArt(new String(get("https://itunes.apple.com/search?entity=song&limit=1&term="
                + URLEncoder.encode(q, "UTF-8")), "UTF-8"));
    }

    private static String youtube(String q, String key) throws Exception {
        String id = Logic.youtubeId(new String(get("https://www.googleapis.com/youtube/v3/search?part=id&type=video&maxResults=1&q="
                + URLEncoder.encode(q, "UTF-8") + "&key=" + URLEncoder.encode(key, "UTF-8")), "UTF-8"));
        return id == null ? null : "https://i.ytimg.com/vi/" + id + "/hqdefault.jpg";
    }

    private static byte[] get(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setRequestProperty("User-Agent", "HomeCyberZec/0.9 (launcher auto DFSK Glory 600)");
        c.setConnectTimeout(8000);
        c.setReadTimeout(8000);
        try {
            if (c.getResponseCode() != 200) throw new Exception("HTTP " + c.getResponseCode());
            InputStream in = c.getInputStream();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
            return out.toByteArray();
        } finally {
            c.disconnect();
        }
    }
}
