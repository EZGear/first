package uz.odat.vidjet;

import android.content.Context;
import android.content.SharedPreferences;

/** Odat nomlari va har kungi belgilarni telefonda saqlaydi. */
final class HabitStore {

    static final int SLOTS = 5;

    private static final String PREFS = "odatlar";

    private final Context ctx;
    private final SharedPreferences prefs;

    HabitStore(Context context) {
        ctx = context.getApplicationContext();
        prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    String name(int slot) {
        String saved = prefs.getString("name" + slot, null);
        return saved != null ? saved : defaultName(slot);
    }

    void setName(int slot, String name) {
        prefs.edit().putString("name" + slot, name == null ? "" : name.trim()).commit();
    }

    boolean isDone(int slot, long epochDay) {
        return prefs.getBoolean(key(slot, epochDay), false);
    }

    void toggle(int slot, long epochDay) {
        String k = key(slot, epochDay);
        SharedPreferences.Editor e = prefs.edit();
        if (prefs.getBoolean(k, false)) {
            e.remove(k);
        } else {
            e.putBoolean(k, true);
        }
        e.commit();
    }

    /** Jadval boshlangan kun. Birinchi marta ochilganda bugungi kun bo'ladi. */
    long startDay(long todayEpoch) {
        long start = prefs.getLong("start", Long.MIN_VALUE);
        if (start == Long.MIN_VALUE || start > todayEpoch) {
            start = todayEpoch;
            prefs.edit().putLong("start", start).commit();
        }
        return start;
    }

    private String defaultName(int slot) {
        String[] defaults = ctx.getResources().getStringArray(R.array.default_habits);
        return slot < defaults.length ? defaults[slot] : "";
    }

    private static String key(int slot, long epochDay) {
        return "d" + slot + "_" + epochDay;
    }
}
