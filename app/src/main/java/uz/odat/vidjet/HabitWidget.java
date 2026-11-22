package uz.odat.vidjet;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.RemoteViews;

import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Bosh ekrandagi jadval: chapda odatlar, tepada kunlar. Kunlar boshlangan kundan chapdan
 * boshlanib o'ngga to'lib boradi. Katakni bosish o'sha kun uchun belgini qo'yadi yoki olib tashlaydi.
 */
public class HabitWidget extends AppWidgetProvider {

    static final String ACTION_TOGGLE = "uz.odat.vidjet.TOGGLE";
    static final String ACTION_REFRESH = "uz.odat.vidjet.REFRESH";

    private static final String EXTRA_SLOT = "slot";
    private static final String EXTRA_DAY = "day";
    private static final int DEFAULT_DAYS = 7;
    private static final int MIN_DAYS = 5;
    private static final int MAX_DAYS = 14;
    private static final int LABEL_WIDTH_DP = 74;   // habit_row.xml dagi nom ustuni
    private static final int SIDE_PADDING_DP = 22;  // widget.xml dagi chap+o'ng chekka
    private static final int COLUMN_DP = 28;        // bitta kun ustuni uchun eng kam joy
    private static final String[] WEEKDAYS = {"Du", "Se", "Ch", "Pa", "Ju", "Sh", "Ya"};

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (ACTION_TOGGLE.equals(action)) {
            int slot = intent.getIntExtra(EXTRA_SLOT, -1);
            long day = intent.getLongExtra(EXTRA_DAY, Long.MIN_VALUE);
            if (slot >= 0 && slot < HabitStore.SLOTS && day != Long.MIN_VALUE) {
                new HabitStore(context).toggle(slot, day);
            }
            updateAll(context);
            return;
        }
        if (ACTION_REFRESH.equals(action)
                || Intent.ACTION_BOOT_COMPLETED.equals(action)
                || Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)
                || Intent.ACTION_TIME_CHANGED.equals(action)
                || Intent.ACTION_TIMEZONE_CHANGED.equals(action)
                || Intent.ACTION_DATE_CHANGED.equals(action)) {
            updateAll(context);
            return;
        }
        super.onReceive(context, intent);
    }

    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        for (int id : ids) {
            manager.updateAppWidget(id, build(context, manager.getAppWidgetOptions(id)));
        }
        scheduleMidnight(context);
    }

    @Override
    public void onAppWidgetOptionsChanged(Context context, AppWidgetManager manager,
                                          int id, Bundle options) {
        manager.updateAppWidget(id, build(context, options));
    }

    @Override
    public void onEnabled(Context context) {
        scheduleMidnight(context);
    }

    @Override
    public void onDisabled(Context context) {
        AlarmManager am = context.getSystemService(AlarmManager.class);
        if (am != null) {
            am.cancel(refreshIntent(context));
        }
    }

    /** Barcha vidjetlarni qayta chizadi va yarim tunda yangilashni rejalashtiradi. */
    static void updateAll(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        int[] ids = manager.getAppWidgetIds(new ComponentName(context, HabitWidget.class));
        if (ids == null || ids.length == 0) {
            return;
        }
        for (int id : ids) {
            manager.updateAppWidget(id, build(context, manager.getAppWidgetOptions(id)));
        }
        scheduleMidnight(context);
    }

    /** Vidjet kengligiga qarab nechta kun sig'ishini hisoblaydi: kengaytirsangiz, kunlar ko'payadi. */
    private static int columnsFor(Bundle options) {
        int widthDp = options == null ? 0
                : options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0);
        if (widthDp <= 0) {
            return DEFAULT_DAYS;
        }
        int n = (widthDp - SIDE_PADDING_DP - LABEL_WIDTH_DP) / COLUMN_DP;
        return Math.max(MIN_DAYS, Math.min(MAX_DAYS, n));
    }

    private static RemoteViews build(Context context, Bundle options) {
        String pkg = context.getPackageName();
        HabitStore store = new HabitStore(context);
        LocalDate today = LocalDate.now();
        long todayEpoch = today.toEpochDay();
        int days = columnsFor(options);

        // Kunlar chapdan boshlanadi: birinchi ustun boshlangan kun, keyin o'ngga to'lib boradi.
        // Jadval to'lgach, har kuni bir ustunga suriladi va bugun eng o'ngda qoladi.
        long start = store.startDay(todayEpoch);
        long first = Math.max(start, todayEpoch - (days - 1));

        RemoteViews root = new RemoteViews(pkg, R.layout.widget);
        root.removeAllViews(R.id.rows);

        RemoteViews header = new RemoteViews(pkg, R.layout.head_row);
        for (int i = 0; i < days; i++) {
            long day = first + i;
            LocalDate d = LocalDate.ofEpochDay(day);
            int layout = day == todayEpoch ? R.layout.head_today
                    : (day > todayEpoch ? R.layout.head_future : R.layout.head);
            RemoteViews cell = new RemoteViews(pkg, layout);
            cell.setTextViewText(R.id.head_day, WEEKDAYS[d.getDayOfWeek().getValue() - 1]);
            cell.setTextViewText(R.id.head_date, String.valueOf(d.getDayOfMonth()));
            header.addView(R.id.cells, cell);
        }
        root.addView(R.id.rows, header);

        int doneToday = 0;
        int total = 0;
        for (int slot = 0; slot < HabitStore.SLOTS; slot++) {
            String name = store.name(slot);
            if (name.isEmpty()) {
                continue;
            }
            total++;
            RemoteViews row = new RemoteViews(pkg, R.layout.habit_row);
            row.setTextViewText(R.id.label, name);
            for (int i = 0; i < days; i++) {
                long day = first + i;
                if (day > todayEpoch) {
                    // Kelgusi kunlar: faqat ko'rinadi, bosib bo'lmaydi.
                    row.addView(R.id.cells, new RemoteViews(pkg, R.layout.cell_future));
                    continue;
                }
                boolean done = store.isDone(slot, day);
                if (day == todayEpoch && done) {
                    doneToday++;
                }
                int layout = done ? R.layout.cell_on
                        : (day == todayEpoch ? R.layout.cell_today : R.layout.cell_off);
                RemoteViews cell = new RemoteViews(pkg, layout);
                cell.setOnClickPendingIntent(R.id.cell_root, toggleIntent(context, slot, day));
                row.addView(R.id.cells, cell);
            }
            root.addView(R.id.rows, row);
        }

        root.setTextViewText(R.id.summary,
                context.getString(R.string.today_summary, doneToday, total));
        root.setOnClickPendingIntent(R.id.title_bar, openAppIntent(context));
        return root;
    }

    private static PendingIntent toggleIntent(Context context, int slot, long day) {
        Intent intent = new Intent(context, HabitWidget.class)
                .setAction(ACTION_TOGGLE)
                .setData(Uri.parse("odat://toggle/" + slot + "/" + day))
                .putExtra(EXTRA_SLOT, slot)
                .putExtra(EXTRA_DAY, day);
        return PendingIntent.getBroadcast(context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static PendingIntent openAppIntent(Context context) {
        Intent intent = new Intent(context, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return PendingIntent.getActivity(context, 1, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static PendingIntent refreshIntent(Context context) {
        Intent intent = new Intent(context, HabitWidget.class).setAction(ACTION_REFRESH);
        return PendingIntent.getBroadcast(context, 2, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    /** Yangi kun boshlanganda sanalar o'zi almashishi uchun. */
    private static void scheduleMidnight(Context context) {
        AlarmManager am = context.getSystemService(AlarmManager.class);
        if (am == null) {
            return;
        }
        long nextMidnight = LocalDate.now().plusDays(1)
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli() + 2000L;
        am.set(AlarmManager.RTC, nextMidnight, refreshIntent(context));
    }
}
