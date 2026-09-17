package jp.bunkaich.sukashimotion;

import android.content.SharedPreferences;
import java.util.*;
import org.json.*;

/** Folders occupy existing favorite slots; their members always remain in the app catalog. */
final class HomeFolders {
    private static final String PREFIX = "folder:";
    record Folder(String id, String name, List<String> components) {
        Folder { components = List.copyOf(components); }
        List<AppCatalog.App> apps(List<AppCatalog.App> catalog) {
            Map<String, AppCatalog.App> available = new HashMap<>();
            for (AppCatalog.App app : catalog) available.put(app.component().flattenToString(), app);
            List<AppCatalog.App> result = new ArrayList<>();
            for (String component : components) if (available.containsKey(component)) result.add(available.get(component));
            return result;
        }
    }
    static Map<Integer, Folder> read(SharedPreferences prefs) {
        Map<Integer, Folder> result = new LinkedHashMap<>();
        for (int slot = 0; slot < 16; slot++) {
            String saved = prefs.getString("slot_" + slot, "");
            if (!saved.startsWith(PREFIX)) continue;
            Folder folder = decode(saved.substring(PREFIX.length()), prefs.getString(saved, ""));
            if (folder != null) result.put(slot, folder);
        }
        return result;
    }
    static Folder create(SharedPreferences prefs, int slot, String name, List<String> members) {
        if (slot < 0 || slot >= 16 || read(prefs).containsKey(slot)) throw new IllegalArgumentException("Invalid folder slot");
        Folder folder = new Folder(UUID.randomUUID().toString(), name.trim(), unique(members));
        if (folder.components().size() < 2) throw new IllegalArgumentException("A new folder needs two apps");
        saveMembers(prefs, slot, folder);
        return folder;
    }
    static void update(SharedPreferences prefs, int slot, List<String> members) {
        Folder old = require(prefs, slot);
        if (members.isEmpty()) { dissolve(prefs, slot); return; }
        saveMembers(prefs, slot, new Folder(old.id(), old.name(), unique(members)));
    }
    static void add(SharedPreferences prefs, int slot, String component) {
        List<String> members = new ArrayList<>(require(prefs, slot).components());
        members.add(component); update(prefs, slot, members);
    }
    static void rename(SharedPreferences prefs, int slot, String name) {
        Folder old = require(prefs, slot);
        if (name.trim().isEmpty()) return;
        prefs.edit().putString(PREFIX + old.id(), encode(new Folder(old.id(), name.trim(), old.components()))).apply();
    }
    static void dissolve(SharedPreferences prefs, int slot) {
        Folder old = require(prefs, slot);
        SharedPreferences.Editor edit = prefs.edit().remove(PREFIX + old.id()).putString("slot_" + slot, "");
        // Return as many members as fit to the home screen, preserving existing positions.
        // Overflow stays on subsequent pages and in All apps, never uninstalled or forgotten.
        List<Integer> empty = new ArrayList<>(); empty.add(slot);
        for (int other = 0; other < 16; other++)
            if (other != slot && prefs.getString("slot_" + other, "").isEmpty()) empty.add(other);
        for (int index = 0; index < Math.min(empty.size(), old.components().size()); index++)
            edit.putString("slot_" + empty.get(index), old.components().get(index));
        edit.apply();
    }
    static void removeApp(SharedPreferences prefs, String component) {
        SharedPreferences.Editor edit = prefs.edit();
        for (Map.Entry<Integer, Folder> entry : read(prefs).entrySet()) {
            Folder old = entry.getValue();
            List<String> kept = new ArrayList<>(old.components());
            if (!kept.remove(component)) continue;
            if (kept.isEmpty()) edit.putString("slot_" + entry.getKey(), "").remove(PREFIX + old.id());
            else edit.putString(PREFIX + old.id(), encode(new Folder(old.id(), old.name(), kept)));
        }
        edit.apply();
    }
    private static void saveMembers(SharedPreferences prefs, int slot, Folder folder) {
        Set<String> selected = new HashSet<>(folder.components());
        SharedPreferences.Editor edit = prefs.edit();
        for (int other = 0; other < 16; other++)
            if (other != slot && selected.contains(prefs.getString("slot_" + other, ""))) edit.putString("slot_" + other, "");
        for (Map.Entry<Integer, Folder> entry : read(prefs).entrySet()) {
            if (entry.getKey() == slot) continue;
            Folder old = entry.getValue();
            List<String> kept = new ArrayList<>(old.components()); kept.removeAll(selected);
            if (kept.size() == old.components().size()) continue;
            if (kept.isEmpty()) edit.putString("slot_" + entry.getKey(), "").remove(PREFIX + old.id());
            else edit.putString(PREFIX + old.id(), encode(new Folder(old.id(), old.name(), kept)));
        }
        edit.putString("slot_" + slot, PREFIX + folder.id()).putString(PREFIX + folder.id(), encode(folder)).apply();
    }
    private static Folder require(SharedPreferences prefs, int slot) {
        Folder folder = read(prefs).get(slot);
        if (folder == null) throw new IllegalArgumentException("Folder no longer exists");
        return folder;
    }
    private static List<String> unique(List<String> values) {
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        for (String value : values) if (value != null && !value.isBlank() && !value.startsWith(PREFIX)) unique.add(value);
        return new ArrayList<>(unique);
    }
    private static String encode(Folder folder) {
        try { return new JSONObject().put("name", folder.name()).put("members", new JSONArray(folder.components())).toString(); }
        catch (JSONException e) { throw new IllegalStateException(e); }
    }
    private static Folder decode(String id, String value) {
        try {
            JSONObject object = new JSONObject(value); JSONArray array = object.getJSONArray("members");
            List<String> members = new ArrayList<>();
            for (int i = 0; i < array.length(); i++) members.add(array.getString(i));
            return new Folder(id, object.getString("name"), unique(members));
        } catch (JSONException e) { return null; }
    }
}
