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
            CommonStuff.Log("FRETypeMismatchException(1): "+e.getMessage());
        } catch (FREInvalidObjectException e) {
            CommonStuff.Log("FREInvalidObjectException(1): "+e.getMessage());
        } catch (FREWrongThreadException e) {
            CommonStuff.Log("FREWrongThreadException(1): "+e.getMessage());
        }

        CommonStuff.Log("lib 0");

        Intent intent = new Intent(context, VideoActivity.class);
        CommonStuff.Log("lib 1");
        intent.putExtra("layoutId"
                , freContext.getResourceId("layout.ijasnahamed_twilio_video_activity"));
        CommonStuff.Log("lib 2");
        intent.putExtra("hangupBtnId", freContext.getResourceId("id.DisconnectBtn"));
        CommonStuff.Log("lib 3");
        intent.putExtra("remoteViewId", freContext.getResourceId("id.RemoteView"));
        CommonStuff.Log("lib 4");
        intent.putExtra("localViewId", freContext.getResourceId("id.LocalView"));
        CommonStuff.Log("lib 5");
        intent.putExtra("callDetails", callDetails);
        CommonStuff.Log("lib 6");

        activity.startActivity(intent);
        CommonStuff.Log("lib 7");

        return null;
    }
}
