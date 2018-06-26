package ijasnahamed.twilio.video.functions;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;

import com.adobe.fre.FREContext;
import com.adobe.fre.FREFunction;
import com.adobe.fre.FREInvalidObjectException;
import com.adobe.fre.FREObject;
import com.adobe.fre.FRETypeMismatchException;
import com.adobe.fre.FREWrongThreadException;

import ijasnahamed.twilio.video.VideoExtension;
import ijasnahamed.twilio.video.utils.CommonStuff;
import ijasnahamed.twilio.video.views.VideoActivity;

public class VideoFunction implements FREFunction {
    @Override
    public FREObject call(FREContext freContext, FREObject[] args) {
        VideoExtension.extensionContext = freContext;

        Activity activity = freContext.getActivity();
        Context context = activity.getApplicationContext();

        String callDetails = "";
        try {
            callDetails = args[0].getAsString();
        } catch (FRETypeMismatchException e) {
            CommonStuff.Log("FRETypeMismatchException: "+e.getMessage());
        } catch (FREInvalidObjectException e) {
            CommonStuff.Log("FREInvalidObjectException: "+e.getMessage());
        } catch (FREWrongThreadException e) {
            CommonStuff.Log("FREWrongThreadException: "+e.getMessage());
        }

        Intent intent = new Intent(context, VideoActivity.class);
        intent.putExtra("layoutId"
                , freContext.getResourceId("layout.ijasnahamed_twilio_video_activity"));
        intent.putExtra("hangupBtnId", freContext.getResourceId("id.DisconnectBtn"));
        intent.putExtra("remoteViewId", freContext.getResourceId("id.RemoteView"));
        intent.putExtra("localViewId", freContext.getResourceId("id.LocalView"));
        intent.putExtra("callDetails", callDetails);

        activity.startActivity(intent);

        return null;
    }
}
