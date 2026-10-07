package at.bitfire.nophonespam;

import android.content.Context;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import android.telecom.Call;
import android.telecom.CallScreeningService;
import android.telecom.TelecomManager;
import android.text.TextUtils;
import android.util.Log;

@RequiresApi(api = Build.VERSION_CODES.N)
public class MyCallScreeningService extends CallScreeningService {


    @Override
    public void onScreenCall(@NonNull Call.Details callDetails) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q){
            return;
        }
        if (callDetails.getCallDirection() == Call.Details.DIRECTION_OUTGOING) {
            return;
        }
        String incomingNumber = callDetails.getHandle() != null ? callDetails.getHandle().getSchemeSpecificPart() : null;
        if (TextUtils.isEmpty(incomingNumber)) {
            if (callDetails.getHandlePresentation() != TelecomManager.PRESENTATION_RESTRICTED) {
                // number unavailable but not deliberately hidden: nothing to check, let it ring
                respondToCall(callDetails, new CallResponse.Builder().build());
                return;
            }
            // caller deliberately hid the number: empty number means private number
            incomingNumber = "";
        }
        Context context = getApplicationContext();
        CallReceiver.CallOutcome outcome = CallReceiver.handlingRing(context, incomingNumber);


        CallResponse response = new CallResponse.Builder()
            .setRejectCall(outcome.rejected)
            .setDisallowCall(outcome.rejected)
            .setSilenceCall(false)
            .setSkipCallLog(false)
            .setSkipNotification(false)
            .build();
        Log.d("my-debug", "responding to call!="+incomingNumber + " rejected="+outcome.rejected);
        respondToCall(callDetails, response);
        if (outcome.rejected){
            CallReceiver.notifyOfRejection(context, outcome.reason, outcome.number);
        }
    }
}
