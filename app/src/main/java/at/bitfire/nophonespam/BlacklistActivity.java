/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.nophonespam;

import android.Manifest;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.annotation.Nullable;
import androidx.loader.app.LoaderManager;
import androidx.loader.content.AsyncTaskLoader;

import android.app.role.RoleManager;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.loader.content.Loader;

import android.provider.OpenableColumns;
import android.util.Log;
import android.util.SparseBooleanArray;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.snackbar.Snackbar;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import at.bitfire.nophonespam.model.BlacklistFile;
import at.bitfire.nophonespam.model.BlockingModes;
import at.bitfire.nophonespam.model.DbHelper;
import at.bitfire.nophonespam.model.Number;





public class BlacklistActivity
        extends AppCompatActivity
        implements LoaderManager.LoaderCallbacks<Set<Number>>,  AdapterView.OnItemClickListener
{
    protected Settings settings;
    CoordinatorLayout coordinatorLayout;

    ListView list;
    ArrayAdapter<Number> adapter;

    private static final int REQUEST_PERMISSION_CODE=1234;

    public static Context AppContext;

    public String queryName(Uri uri){
        try (Cursor returnCursor =
            getContentResolver().query(uri, null, null, null, null)) {
            if (returnCursor == null){
                return null;
            }
            int nameIndex = returnCursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
            returnCursor.moveToFirst();
            return returnCursor.getString(nameIndex);
        }
    }

    ActivityResultLauncher<Intent> requestRole = registerForActivityResult(
        new ActivityResultContracts.StartActivityForResult(),
        activityResult -> {
            if ( activityResult.getResultCode() == RESULT_OK){
                Log.d("my-debug","got role of ScreenCalling!");
            } else {
                Log.d("my-debug","role of ScreenCalling refused!");
            }
        }
    );



    public void requestCallScreeningRole() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            RoleManager roleManager = (RoleManager) getSystemService(ROLE_SERVICE);
            if (!roleManager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)){
                Intent intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING);
                requestRole.launch(intent);
            }
        }
    }

    ActivityResultLauncher<String> storeFile = registerForActivityResult(
        new BlacklistFile.CreateBlackListFile(),
        uri -> {
            try {
                if (uri == null){
                    return;
                }
                Log.d("my-debug", "uri="+ uri);
                OutputStream stream = getContentResolver().openOutputStream(uri);
                if (stream == null){
                    return;
                }
                List<Number> numbers = new LinkedList<>();
                for (int i = 0; i < adapter.getCount(); i++) {
                    numbers.add(adapter.getItem(i));
                }

                BlacklistFile.storeToOutput(numbers, stream);
                // if we don't have permission, bail immediately; failure message is already displayed

                Toast.makeText(
                    getApplicationContext(),
                    getResources().getText(R.string.blacklist_exported_to) + " " + queryName(uri),
                    Toast.LENGTH_LONG
                ).show();
            } catch (IOException exception) {
                Log.e("my-debug", "error in catch blacklist file store!!!!");
            }
        });

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        AppContext = getApplicationContext();

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_blacklist);

        settings = new Settings(this);
        coordinatorLayout = findViewById(R.id.coordinatorLayout);

        list = findViewById(R.id.numbers);
        list.setAdapter(adapter = new NumberAdapter(this));
        list.setOnItemClickListener(this);

        list.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE_MODAL);
        list.setMultiChoiceModeListener(new AbsListView.MultiChoiceModeListener() {
            @Override
            public void onItemCheckedStateChanged(ActionMode actionMode, int position, long id, boolean checked) {
            }

            @Override
            public boolean onCreateActionMode(ActionMode actionMode, Menu menu) {
                getMenuInflater().inflate(R.menu.blacklist_delete_numbers, menu);
                return true;
            }

            @Override
            public boolean onPrepareActionMode(ActionMode actionMode, Menu menu) {
                return false;
            }

            @Override
            public boolean onActionItemClicked(ActionMode actionMode, MenuItem menuItem) {
                if (menuItem.getItemId() == R.id.delete) {
                    deleteSelectedNumbers();
                    actionMode.finish();
                    return true;
                }
                return false;
            }

            @Override
            public void onDestroyActionMode(ActionMode actionMode) {
            }
        });

        requestPermissions();
        requestCallScreeningRole();

        LoaderManager.getInstance(this).initLoader(0, null, this);
    }

    protected void requestPermissions() {
        List<String> requiredPermissions = new ArrayList<>();
        requiredPermissions.add(Manifest.permission.CALL_PHONE);
        requiredPermissions.add(Manifest.permission.READ_PHONE_STATE);
        requiredPermissions.add(Manifest.permission.READ_CONTACTS);
        requiredPermissions.add(Manifest.permission.READ_CALL_LOG);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            requiredPermissions.add(Manifest.permission.ANSWER_PHONE_CALLS);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requiredPermissions.add(Manifest.permission.POST_NOTIFICATIONS);
        }
        List<String> missingPermissions = new ArrayList<>();

        for (String permission : requiredPermissions) {
            if (ContextCompat.checkSelfPermission(this, permission)
                    != PackageManager.PERMISSION_GRANTED) {
                missingPermissions.add(permission);
            }
        }

        if (!missingPermissions.isEmpty()) {
            ActivityCompat.requestPermissions(this,
                    missingPermissions.toArray(new String[0]), REQUEST_PERMISSION_CODE);
        }
    }

    protected void deleteSelectedNumbers() {
        final List<String> numbers = new LinkedList<>();

        SparseBooleanArray checked = list.getCheckedItemPositions();
        for (int i = checked.size() - 1; i >= 0; i--) {
            if (checked.valueAt(i)) {
                int position = checked.keyAt(i);
                Number n = adapter.getItem(position);
                if (n == null){
                    continue;
                }
                numbers.add(n.number);
            }
        }

        try (DbHelper dbHelper = new DbHelper(BlacklistActivity.this)) {
            SQLiteDatabase db = dbHelper.getWritableDatabase();
            for (String number : numbers) {
                db.delete(Number._TABLE, Number.NUMBER + "=?", new String[]{number});
            }
        }

        LoaderManager.getInstance(this).restartLoader(0, null, BlacklistActivity.this);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQUEST_PERMISSION_CODE){
            boolean ok = true;
            if (grantResults.length != 0) {
                int i = 0;
                Log.d("my-debug", "len="+grantResults.length);
                for (int result : grantResults) {
                    Log.d("my-debug","permission="+permissions[i]+" grantResult="+(result==PackageManager.PERMISSION_GRANTED)+" r="+result);
                    if (result != PackageManager.PERMISSION_GRANTED) {

                        ok = false;
                        break;
                    }
                    i = i +1;
                }
            } else {
                // treat cancellation as failure
                ok = false;
            }

            if (!ok) {
                Snackbar
                    .make(coordinatorLayout, R.string.blacklist_permissions_required, Snackbar.LENGTH_INDEFINITE)
                    .setAction(R.string.blacklist_request_permissions, view -> requestPermissions())
                    .show();
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.activity_blacklist, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        menu.findItem(R.id.block_hidden_numbers).setChecked(settings.blockHiddenNumbers());
        menu.findItem(R.id.notifications).setChecked(settings.showNotifications());

        int callBlockingMode = settings.getCallBlockingMode();
        switch (callBlockingMode){
            case BlockingModes.ALLOW_CONTACTS:
                menu.findItem(R.id.allow_only_contacts).setChecked(true);
                break;
            case BlockingModes.ALLOW_ONLY_LIST_CALLS:
                menu.findItem(R.id.allow_only_list).setChecked(true);
                break;
            case BlockingModes.BLOCK_LIST:
                menu.findItem(R.id.block_list).setChecked(true);
                break;
            case BlockingModes.BLOCK_ALL:
                menu.findItem(R.id.block_all).setChecked(true);
                break;
            case BlockingModes.ALLOW_ALL:
            default:
                menu.findItem(R.id.allow_all).setChecked(true);
                break;
        }

        return true;
    }

    public void onBlockHiddenNumbers(MenuItem item) {
        settings.blockHiddenNumbers(!item.isChecked());
    }

    public void onShowNotifications(MenuItem item) {
        settings.showNotifications(!item.isChecked());
    }


    public boolean selectCallBlockingMode(MenuItem item) {

        boolean toggleCheckState = true;

        int itemId = item.getItemId();
        if (itemId == R.id.allow_all) {
            settings.setCallBlockingMode(BlockingModes.ALLOW_ALL);
        } else if (itemId == R.id.allow_only_contacts) {
            settings.setCallBlockingMode(BlockingModes.ALLOW_CONTACTS);
        } else if (itemId == R.id.allow_only_list) {
            settings.setCallBlockingMode(BlockingModes.ALLOW_ONLY_LIST_CALLS);
        } else if (itemId == R.id.block_list) {
            settings.setCallBlockingMode(BlockingModes.BLOCK_LIST);
        } else if (itemId == R.id.block_all) {
            settings.setCallBlockingMode(BlockingModes.BLOCK_ALL);
        } else {
            toggleCheckState = false;
        }

        if (toggleCheckState) {
            boolean wasChecked = item.isChecked();
            item.setChecked(!wasChecked);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    public void onImportBlacklist(MenuItem item) {
        loadFile.launch(new String[]{"text/plain"});
    }

    ActivityResultLauncher<String[]> loadFile = registerForActivityResult(
        new ActivityResultContracts.OpenDocument(),
        uri -> {
            if (uri == null) { return; }
            try {
                InputStream stream = getContentResolver().openInputStream(uri);
                if (stream == null){
                    Log.e("my-debug", "error in loading file");
                    return;
                }
                commitBlacklist(stream);
                stream.close();
            } catch (IOException e) {
                Log.e("my-debug", "error in loading file");
            }
            Log.d("my-debug", "uri="+ uri);
        });

    public void commitBlacklist(@NonNull InputStream stream) {
        try (DbHelper dbHelper = new DbHelper(BlacklistActivity.this)) {
            SQLiteDatabase db = dbHelper.getWritableDatabase();

            ContentValues values;
            boolean exists;
            for (Number number : BlacklistFile.load(stream)) {

                values = new ContentValues(4);
                values.put(Number.NAME, number.name);
                values.put(Number.NUMBER, Number.wildcardsViewToDb(number.number));

                try (Cursor cursor = db.query(Number._TABLE, null, Number.NUMBER + "=?", new String[]{number.number}, null, null, null)) {
                    exists = cursor.moveToNext();
                    if (exists) {
                        db.update(Number._TABLE, values, Number.NUMBER + "=?", new String[]{number.number});
                    } else {
                        db.insert(Number._TABLE, null, values);
                    }
                }
            }
        }

        LoaderManager.getInstance(this).restartLoader(0, null, BlacklistActivity.this);
    }

    public void onExportBlacklist(MenuItem item) {
        storeFile.launch(BlacklistFile.DEFAULT_FILENAME);
    }

    public void onAbout(MenuItem item) {
        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://gitlab.com/bitfireAT/NoPhoneSpam/")));
    }

    public void addNumber(View view) {
        startActivity(new Intent(this, EditNumberActivity.class));
    }



    @NonNull
    @Override
    public Loader<Set<Number>> onCreateLoader(int id, @Nullable Bundle args) {
        return new NumberLoader(this);
    }

    @Override
    public void onLoadFinished(@NonNull androidx.loader.content.Loader<Set<Number>> loader, Set<Number> numbers) {
        adapter.clear();
        adapter.addAll(numbers);
    }

    @Override
    public void onLoaderReset(@NonNull androidx.loader.content.Loader<Set<Number>> loader) {
        adapter.clear();
    }

    private static class NumberAdapter extends ArrayAdapter<Number> {

        public NumberAdapter(Context context) {
            super(context, R.layout.blacklist_item);
        }

        @NonNull
        @Override
        public View getView(int position, View view, @NonNull ViewGroup parent) {
            if (view == null) {
                view = View.inflate(getContext(), R.layout.blacklist_item, null);
            }

            Number number = getItem(position);
            if (number == null){
                return view;
            }

            TextView tv = view.findViewById(R.id.number);
            tv.setText(Number.wildcardsDbToView(number.number));

            tv = view.findViewById(R.id.name);
            tv.setText(number.name);

            tv = view.findViewById(R.id.stats);
            if (number.lastCall != null) {
                tv.setVisibility(View.VISIBLE);
                tv.setText(getContext().getResources().getQuantityString(R.plurals.blacklist_call_details, number.timesCalled,
                        number.timesCalled, SimpleDateFormat.getDateTimeInstance().format(new Date(number.lastCall))));
            } else {
                tv.setVisibility(View.GONE);
            }

            return view;
        }

    }

    @Override
    public void onItemClick(AdapterView<?> adapterView, View view, int position, long id) {
        Number number = adapter.getItem(position);
        if (number == null){
            return;
        }

        Intent intent = new Intent(this, EditNumberActivity.class);
        intent.putExtra(EditNumberActivity.EXTRA_NUMBER, number.number);
        startActivity(intent);
    }


    protected static class NumberLoader
        extends AsyncTaskLoader<Set<Number>>
        implements BlacklistObserver.Observer {

        public NumberLoader(Context context) {
            super(context);
        }

        @Override
        protected void onStartLoading() {
            BlacklistObserver.addObserver(this, true);
        }

        @Override
        public Set<Number> loadInBackground() {
            try (DbHelper dbHelper = new DbHelper(getContext())) {
                SQLiteDatabase db = dbHelper.getReadableDatabase();

                Set<Number> numbers = new LinkedHashSet<>();
                Cursor c = db.query(Number._TABLE, null, null, null, null, null, Number.NUMBER);
                while (c.moveToNext()) {
                    ContentValues values = new ContentValues();
                    DatabaseUtils.cursorRowToContentValues(c, values);
                    numbers.add(Number.fromValues(values));
                }
                c.close();

                return numbers;
            }
        }

        @Override
        public void onBlacklistUpdate() {
            forceLoad();
        }

        @Override
        protected void onStopLoading() {
            BlacklistObserver.removeObserver(this);
        }
    }
}
