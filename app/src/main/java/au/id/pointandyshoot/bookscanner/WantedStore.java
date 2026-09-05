package au.id.pointandyshoot.bookscanner;

import android.content.Context;
import au.id.pointandyshoot.bookscanner.core.WantedBook;
import org.json.*;
import java.util.*;

final class WantedStore {
    static final int MAX_BYTES = 1_000_000;
    private final Context context;
    WantedStore(Context context) { this.context = context; }
    List<WantedBook> load() throws JSONException {
        return decode(context.getSharedPreferences("wanted", Context.MODE_PRIVATE).getString("json", "{\"version\":1,\"books\":[]}"));
    }
    void save(List<WantedBook> books) throws JSONException {
        context.getSharedPreferences("wanted", Context.MODE_PRIVATE).edit().putString("json", encode(books)).apply();
    }
    static String encode(List<WantedBook> books) throws JSONException {
        JSONArray array = new JSONArray();
        for (WantedBook b : books) array.put(new JSONObject().put("id", b.id).put("title", b.title)
                .put("author", b.author).put("aliases", new JSONArray(b.aliases)).put("enabled", b.enabled));
        return new JSONObject().put("version", 1).put("books", array).toString(2);
    }
    static List<WantedBook> decode(String json) throws JSONException {
        if (json.length() > MAX_BYTES) throw new IllegalArgumentException("List is too large.");
        JSONObject root = new JSONObject(json);
        if (root.getInt("version") != 1) throw new IllegalArgumentException("Unsupported list version.");
        JSONArray array = root.getJSONArray("books");
        if (array.length() > 1000) throw new IllegalArgumentException("Maximum 1,000 wanted entries.");
        List<WantedBook> result = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (int i=0; i<array.length(); i++) {
            JSONObject o = array.getJSONObject(i);
            List<String> aliases = new ArrayList<>();
            JSONArray a = o.optJSONArray("aliases");
            if (a != null) for (int j=0; j<a.length(); j++) aliases.add(a.getString(j));
            WantedBook b = new WantedBook(o.optString("id", ""), o.getString("title"),
                    o.optString("author", ""), aliases, o.optBoolean("enabled", true));
            if (!ids.add(b.id)) throw new IllegalArgumentException("Duplicate entry IDs in imported list.");
            result.add(b);
        }
        return result;
    }
}
