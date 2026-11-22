package uz.odat.vidjet;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

/** Odat nomlarini o'zgartirish va vidjetni bosh ekranga qo'shish oynasi. */
public class MainActivity extends Activity {

    private static final int[] FIELD_IDS = {
            R.id.habit0, R.id.habit1, R.id.habit2, R.id.habit3, R.id.habit4
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        HabitStore store = new HabitStore(this);
        EditText[] fields = new EditText[HabitStore.SLOTS];
        for (int i = 0; i < HabitStore.SLOTS; i++) {
            fields[i] = findViewById(FIELD_IDS[i]);
            fields[i].setText(store.name(i));
        }

        Button save = findViewById(R.id.save);
        save.setOnClickListener(v -> {
            for (int i = 0; i < HabitStore.SLOTS; i++) {
                store.setName(i, fields[i].getText().toString());
            }
            HabitWidget.updateAll(this);
            Toast.makeText(this, R.string.saved, Toast.LENGTH_SHORT).show();
        });

        Button pin = findViewById(R.id.pin);
        AppWidgetManager manager = AppWidgetManager.getInstance(this);
        if (manager.isRequestPinAppWidgetSupported()) {
            pin.setOnClickListener(v -> manager.requestPinAppWidget(
                    new ComponentName(this, HabitWidget.class), null, null));
        } else {
            pin.setVisibility(View.GONE);
        }
    }
}
