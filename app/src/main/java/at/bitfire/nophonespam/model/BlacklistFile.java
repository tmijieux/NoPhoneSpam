package at.bitfire.nophonespam.model;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;

import androidx.activity.result.contract.ActivityResultContract;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.LinkedList;
import java.util.List;


public class BlacklistFile {

    public static class CreateBlackListFile extends ActivityResultContract<String, Uri> {
        @NonNull
        @Override
        public Intent createIntent(@NonNull Context context, String filename) {
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("text/plain");
            intent.putExtra(Intent.EXTRA_TITLE, filename);

            return intent;
        }

        @Override
        public Uri parseResult(int i, @Nullable Intent intent) {
            if (intent == null){
                return null;
            }
            return intent.getData();
        }
    }

    public static final String END_NUMBER_DELIMITER = ": ";
    public static final String DEFAULT_FILENAME = "NoPhoneSpam_blacklist.txt";


    public static List<Number> load(@NonNull InputStream stream) {
        List<Number> numbers = new LinkedList<>();

        BufferedReader reader;
        String line;
        Number n;
        int sep;

        try {
            reader = new BufferedReader(new InputStreamReader(stream));
            while ((line = reader.readLine()) != null &&
                    (sep = line.indexOf(END_NUMBER_DELIMITER)) != -1) {
                n = new Number();
                n.number = line.substring(0, sep);
                n.name = line.substring(sep + END_NUMBER_DELIMITER.length());
                numbers.add(n);
            }
            return numbers;
        } catch (IOException exception) {
            return numbers;
        }
    }

    public static void storeToOutput(List<Number> numbers, @NonNull OutputStream stream) {
        try {
            for (Number n : numbers) {
                stream.write(n.number.getBytes());
                stream.write(END_NUMBER_DELIMITER.getBytes());
                stream.write(n.name.getBytes());
                stream.write("\n".getBytes());
            }
            stream.close();
        } catch (IOException e) {
            Log.e("my-debug", "error in storeToOutput()");
        }
    }
}