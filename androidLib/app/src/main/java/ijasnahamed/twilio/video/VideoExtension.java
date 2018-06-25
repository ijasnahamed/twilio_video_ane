package ijasnahamed.twilio.video;

import com.adobe.fre.FREContext;
import com.adobe.fre.FREExtension;

public class VideoExtension implements FREExtension {
    public static FREContext extensionContext = null;

    @Override
    public void initialize() {

    }

    @Override
    public FREContext createContext(String s) {
        return new VideoContext();
    }

    @Override
    public void dispose() {
        extensionContext = null;
    }
}
