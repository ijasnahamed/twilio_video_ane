package ijasnahamed.twilio.video;

import com.adobe.fre.FREContext;
import com.adobe.fre.FREFunction;

import java.util.HashMap;
import java.util.Map;

import ijasnahamed.twilio.video.functions.VideoFunction;

public class VideoContext extends FREContext {
    @Override
    public Map<String, FREFunction> getFunctions() {
        Map<String, FREFunction> functions = new HashMap<String, FREFunction>();

        functions.put("start", new VideoFunction());

        return functions;
    }

    @Override
    public void dispose() {

    }
}
