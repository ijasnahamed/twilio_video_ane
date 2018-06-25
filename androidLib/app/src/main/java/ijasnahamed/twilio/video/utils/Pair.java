package ijasnahamed.twilio.video.utils;

import com.twilio.video.CameraCapturer;

public class Pair {
    public CameraCapturer.CameraSource cameraSource;
    public String cameraId;

    public Pair(CameraCapturer.CameraSource cameraSource, String cameraId) {
        this.cameraSource = cameraSource;
        this.cameraId = cameraId;
    }
}
