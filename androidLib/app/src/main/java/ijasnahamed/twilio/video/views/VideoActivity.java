package ijasnahamed.twilio.video.views;

import android.Manifest;
import android.app.ActionBar;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.AudioDeviceInfo;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.twilio.video.AudioCodec;
import com.twilio.video.CameraCapturer;
import com.twilio.video.ConnectOptions;
import com.twilio.video.EncodingParameters;
import com.twilio.video.G722Codec;
import com.twilio.video.H264Codec;
import com.twilio.video.IsacCodec;
import com.twilio.video.LocalAudioTrack;
import com.twilio.video.LocalParticipant;
import com.twilio.video.LocalVideoTrack;
import com.twilio.video.OpusCodec;
import com.twilio.video.PcmaCodec;
import com.twilio.video.PcmuCodec;
import com.twilio.video.RemoteAudioTrack;
import com.twilio.video.RemoteAudioTrackPublication;
import com.twilio.video.RemoteDataTrack;
import com.twilio.video.RemoteDataTrackPublication;
import com.twilio.video.RemoteParticipant;
import com.twilio.video.RemoteVideoTrack;
import com.twilio.video.RemoteVideoTrackPublication;
import com.twilio.video.Room;
import com.twilio.video.RoomState;
import com.twilio.video.TwilioException;
import com.twilio.video.Video;
import com.twilio.video.VideoCodec;
import com.twilio.video.VideoTrack;
import com.twilio.video.VideoView;
import com.twilio.video.Vp8Codec;
import com.twilio.video.Vp9Codec;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Collections;

import ijasnahamed.twilio.video.VideoExtension;
import ijasnahamed.twilio.video.utils.CameraCapturerCompat;
import ijasnahamed.twilio.video.utils.CommonStuff;

public class VideoActivity extends Activity {
    private static final int CAMERA_MIC_PERMISSION_REQUEST_CODE = 1;

    private static final String LOCAL_AUDIO_TRACK_NAME = "mic";
    private static final String LOCAL_VIDEO_TRACK_NAME = "camera";

    private static final String EVENT_CALL_CONNECTED = "callConnected";
    private static final String EVENT_CALL_DISCONNECTED = "callDisconnected";
    private static final String EVENT_CALL_FAILED = "callFailed";
    private static final String EVENT_HANGUP_INITIATED = "hangupInitiated";
    private static final String EVENT_PARTICIPANT_CONNECTED = "participantConencted";
    private static final String EVENT_PARTICIPANT_DISCONNECTED = "participantDisconnected";
    private static final String EVENT_PARTICIPANT_AUDIO_TRACK_ADDED = "participantAudioTrackAdded";
    private static final String EVENT_PARTICIPANT_AUDIO_TRACK_REMOVED = "participantAudioTrackRemoved";
    private static final String EVENT_PARTICIPANT_VIDEO_TACK_ADDED = "participantVideoTrackAdded";
    private static final String EVENT_PARTICIPANT_VIDEO_TRACK_REMOVED = "participantVIdeoTrackRemoved";

    private static final String DEFAULT_AUDIO_CODEC = OpusCodec.NAME;
    private static final String DEFAULT_VIDEO_CODEC = Vp8Codec.NAME;
    private static final int DEFAULT_MAX_SENDER_AUDIO_BITRATE = 0;
    private static final int DEFAULT_MAX_SENDER_VIDEO_BITRATE = 0;

    private String accessToken, roomName;

    private Room room;
    private LocalParticipant localParticipant;

    private AudioCodec audioCodec;
    private VideoCodec videoCodec;

    private EncodingParameters encodingParameters;

    private VideoView primaryVideoView;
    private VideoView thumbnailVideoView;
    private Button hangupBtn;

    private CameraCapturerCompat cameraCapturerCompat;
    private LocalAudioTrack localAudioTrack;
    private LocalVideoTrack localVideoTrack;
    private AudioManager audioManager;
    private AlertDialog alertDialog;


    private String remoteParticipantIdentity = "";
    private boolean disconnectedFromOnDestroy;
    private int previousAudioMode;
    private boolean previousMicrophoneMute;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        CommonStuff.Log("activity 1");
        Intent currentIntent = getIntent();
        CommonStuff.Log("activity 2");
        int layoutId = currentIntent.getIntExtra("layoutId", -1);
        CommonStuff.Log("activity 3");
        if(layoutId == -1) {
            CommonStuff.Log("activity 4");
            onBackPressed();
            return;
        }
        CommonStuff.Log("activity 5");
        setContentView(layoutId);
        CommonStuff.Log("activity 6");

        ActionBar actionBar = getActionBar();
        if(actionBar != null) {
            actionBar.hide();
        }

        int hangupBtnId = currentIntent.getIntExtra("hangupBtnId", -1);
        hangupBtn = (Button) findViewById(hangupBtnId);

        int remoteViewId = currentIntent.getIntExtra("remoteViewId", -1);
        int localViewId = currentIntent.getIntExtra("localViewId", -1);

        CommonStuff.Log("activity started with remoteViewId "+remoteViewId
                +" and localViewId "+localViewId);

        primaryVideoView = (VideoView) findViewById(remoteViewId);
        thumbnailVideoView = (VideoView) findViewById(localViewId);

        JSONObject callDetails;
        try {
            callDetails = new JSONObject(currentIntent.getStringExtra("callDetails"));
        } catch (JSONException e) {
            callDetails = new JSONObject();
        }

        /*accessToken = currentIntent.getStringExtra("accessToken");
        roomName = currentIntent.getStringExtra("roomName");*/

        accessToken = callDetails.optString("token");
        roomName = callDetails.optString("room");

        CommonStuff.Log("call Details: "+callDetails.toString());
        CommonStuff.Log("accessToken: "+accessToken+", roomName: "+roomName);

        audioCodec = getAudioCodecPreference(DEFAULT_AUDIO_CODEC);
        videoCodec = getVideoCodecPreference(DEFAULT_VIDEO_CODEC);

        setVolumeControlStream(AudioManager.STREAM_VOICE_CALL);

        audioManager = (AudioManager)getSystemService(Context.AUDIO_SERVICE);

        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(Intent.ACTION_HEADSET_PLUG);

        registerReceiver(headsetChangeListener, intentFilter);

        encodingParameters = getEncodingParameters();

        if (!checkPermissionForCameraAndMicrophone()) {
            requestPermissionForCameraAndMicrophone();
        } else {
            createAudioAndVideoTracks();
            setAccessToken();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        CommonStuff.Log("onRequestPermissions");
        if (requestCode == CAMERA_MIC_PERMISSION_REQUEST_CODE) {
            if(checkPermissionForCameraAndMicrophone()) {
                createAudioAndVideoTracks();
                setAccessToken();
            } else {
                Toast.makeText(this,
                        "Camera and Microphone permissions needed. Please allow in App Settings for additional functionality.",
                        Toast.LENGTH_LONG).show();

                AlertDialog.Builder builder = new AlertDialog.Builder(VideoActivity.this);

                builder.setCancelable(false);
                builder.setMessage("App requires permission for camera and microphone. Please allow in App Settings");
                builder.setTitle("Permission");

                builder.setNegativeButton("No", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        if(alertDialog != null)
                            alertDialog.hide();
                        onBackPressed();
                    }
                });

                builder.setPositiveButton("Go to settings", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        if(alertDialog != null)
                            alertDialog.hide();
                        Intent intent = new Intent();
                        intent.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                        Uri uri = Uri.fromParts("package", getPackageName(), null);
                        intent.setData(uri);
                        startActivity(intent);
                    }
                });

                alertDialog = builder.create();
                alertDialog.show();

            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        CommonStuff.Log("onResume");

        if(isHeadphonesPlugged()) {
            setSpeakerOff();
        } else {
            setSpeakerOn();
        }

        if(checkPermissionForCameraAndMicrophone()) {
            CommonStuff.Log("onResume 1");
            createAudioAndVideoTracks();
        }

        /*audioCodec = getAudioCodecPreference(DEFAULT_AUDIO_CODEC);
        videoCodec = getVideoCodecPreference(DEFAULT_VIDEO_CODEC);*/

        /*final EncodingParameters newEncodingParameters = getEncodingParameters();

        if (localParticipant != null && checkPermissionForCameraAndMicrophone()) {
            CommonStuff.Log("onResume 5");
            localParticipant.publishTrack(localVideoTrack);
            CommonStuff.Log("onResume 6");

            if (!newEncodingParameters.equals(encodingParameters)) {
                CommonStuff.Log("onResume 7");
                localParticipant.setEncodingParameters(newEncodingParameters);
                CommonStuff.Log("onResume 8");
            }
        }

        CommonStuff.Log("onResume 9");
        encodingParameters = newEncodingParameters;*/

        if(room == null && checkPermissionForCameraAndMicrophone()) {
            CommonStuff.Log("onResume 10");
            setAccessToken();
            CommonStuff.Log("onResume 11");
        }
    }

    @Override
    protected void onPause() {
        CommonStuff.Log("onPause");
        /*if (localVideoTrack != null) {
            CommonStuff.Log("onPause 1");
            if (localParticipant != null) {
                CommonStuff.Log("onPause 2");
                localParticipant.unpublishTrack(localVideoTrack);
                CommonStuff.Log("onPause 3");
            }

            localVideoTrack.release();
            CommonStuff.Log("onPause 4");
            localVideoTrack = null;
            CommonStuff.Log("onPause 5");
        }*/
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        CommonStuff.Log("onDestroy");
        unregisterReceiver(headsetChangeListener);

        if (localAudioTrack != null) {
            CommonStuff.Log("onDestroy 4");
            localAudioTrack.release();
            CommonStuff.Log("onDestroy 5");
            localAudioTrack = null;
            CommonStuff.Log("onDestroy 6");
        }
        if (localVideoTrack != null) {
            if (localParticipant != null) {
                CommonStuff.Log("onDestroy 7");
                localParticipant.unpublishTrack(localVideoTrack);
                CommonStuff.Log("onDestroy 8");
            }
            CommonStuff.Log("onDestroy 9");
            localVideoTrack.release();
            CommonStuff.Log("onDestroy 10");
            localVideoTrack = null;
            CommonStuff.Log("onDestroy 11");
        }

        if (room != null && room.getState() != RoomState.DISCONNECTED) {
            CommonStuff.Log("onDestroy 1");
            room.disconnect();
            CommonStuff.Log("onDestroy 2");
            disconnectedFromOnDestroy = true;
            CommonStuff.Log("onDestroy 3");
        } else {
            if(VideoExtension.extensionContext != null) {
                VideoExtension.extensionContext = null;
            }
        }

        super.onDestroy();
    }

    public void clickHandler(View view) {
        CommonStuff.Log("clickHandler");
        if(view.getId() == hangupBtn.getId()) {
            CommonStuff.Log("clickHandler hangupBtn");
            if(VideoExtension.extensionContext != null) {
                CommonStuff.Log("clickHandler hangupBtn 1");
                VideoExtension.extensionContext.dispatchStatusEventAsync(EVENT_HANGUP_INITIATED, roomName);
                CommonStuff.Log("clickHandler hangupBtn 2");
            }
            CommonStuff.Log("clickHandler hangupBtn 3");
            onBackPressed();
            CommonStuff.Log("clickHandler hangupBtn 4");
        }
    }

    private boolean checkPermissionForCameraAndMicrophone(){
        if(Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return true;
        }
        int resultCamera = checkPermission(Manifest.permission.CAMERA, android.os.Process.myPid()
                , android.os.Process.myUid());
        int resultMic = checkPermission(Manifest.permission.RECORD_AUDIO, android.os.Process.myPid()
                , android.os.Process.myUid());
        return resultCamera == PackageManager.PERMISSION_GRANTED &&
                resultMic == PackageManager.PERMISSION_GRANTED;
    }

    private void requestPermissionForCameraAndMicrophone(){
        if(Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return;
        }
        requestPermissions(new String[]{Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO}
                , CAMERA_MIC_PERMISSION_REQUEST_CODE);
    }

    private void createAudioAndVideoTracks() {
        CommonStuff.Log("createAudioAndVideoTracks ");
        if(localAudioTrack == null) {
            // Share your microphone
            localAudioTrack = LocalAudioTrack.create(this, true, LOCAL_AUDIO_TRACK_NAME);
            CommonStuff.Log("createAudioAndVideoTracks 1");
        }

        if(localVideoTrack == null) {
            // Share your camera
            if(cameraCapturerCompat == null)
                cameraCapturerCompat = new CameraCapturerCompat(this, getAvailableCameraSource());

            CommonStuff.Log("createAudioAndVideoTracks 2");
            localVideoTrack = LocalVideoTrack.create(this,
                    true,
                    cameraCapturerCompat.getVideoCapturer(),
                    LOCAL_VIDEO_TRACK_NAME);
            CommonStuff.Log("createAudioAndVideoTracks 3");
            thumbnailVideoView.setMirror(true);
            CommonStuff.Log("createAudioAndVideoTracks 4");
            localVideoTrack.addRenderer(thumbnailVideoView);
            CommonStuff.Log("createAudioAndVideoTracks 5");
//            localVideoView = thumbnailVideoView;
//            CommonStuff.Log("createAudioAndVideoTracks 6");
        }

    }

    private CameraCapturer.CameraSource getAvailableCameraSource() {
        CommonStuff.Log("getAvailableCameraSource ");
        return (CameraCapturer.isSourceAvailable(CameraCapturer.CameraSource.FRONT_CAMERA)) ?
                (CameraCapturer.CameraSource.FRONT_CAMERA) :
                (CameraCapturer.CameraSource.BACK_CAMERA);
    }

    private void setAccessToken() {
        retrieveAccessTokenfromServer();
    }

    private void connectToRoom() {
        CommonStuff.Log("connectToRoom ");
        configureAudio(true);
        CommonStuff.Log("connectToRoom 1");
        ConnectOptions.Builder connectOptionsBuilder = new ConnectOptions.Builder(accessToken)
                .roomName((roomName.isEmpty()?"HomeWAV-test":roomName));
        CommonStuff.Log("connectToRoom 2");

        /*
         * Add local audio track to connect options to share with participants.
         */
        if (localAudioTrack != null) {
            CommonStuff.Log("connectToRoom 3");
            connectOptionsBuilder
                    .audioTracks(Collections.singletonList(localAudioTrack));
            CommonStuff.Log("connectToRoom 4");
        }

        /*
         * Add local video track to connect options to share with participants.
         */
        if (localVideoTrack != null) {
            CommonStuff.Log("connectToRoom 5");
            connectOptionsBuilder.videoTracks(Collections.singletonList(localVideoTrack));
            CommonStuff.Log("connectToRoom 6");
        }

        /*
         * Set the preferred audio and video codec for media.
         */
        CommonStuff.Log("connectToRoom 7");
        connectOptionsBuilder.preferAudioCodecs(Collections.singletonList(audioCodec));
        CommonStuff.Log("connectToRoom 8");
        connectOptionsBuilder.preferVideoCodecs(Collections.singletonList(videoCodec));
        CommonStuff.Log("connectToRoom 9");

        /*
         * Set the sender side encoding parameters.
         */
        connectOptionsBuilder.encodingParameters(encodingParameters);
        CommonStuff.Log("connectToRoom 10");

        room = Video.connect(this, connectOptionsBuilder.build(), roomListener());
        CommonStuff.Log("connectToRoom 11");
    }

    private <T extends Enum<T>> T getCodecPreference(final Class<T> enumClass, final String codec) {
        return Enum.valueOf(enumClass, codec);
    }

    private AudioCodec getAudioCodecPreference(String audioCodecName) {

        switch (audioCodecName) {
            case IsacCodec.NAME:
                return new IsacCodec();
            case OpusCodec.NAME:
                return new OpusCodec();
            case PcmaCodec.NAME:
                return new PcmaCodec();
            case PcmuCodec.NAME:
                return new PcmuCodec();
            case G722Codec.NAME:
                return new G722Codec();
            default:
                return new OpusCodec();
        }
    }

    private VideoCodec getVideoCodecPreference(String videoCodecName) {

        switch (videoCodecName) {
            case Vp8Codec.NAME:
                return new Vp8Codec();
            case H264Codec.NAME:
                return new H264Codec();
            case Vp9Codec.NAME:
                return new Vp9Codec();
            default:
                return new Vp8Codec();
        }
    }

    private EncodingParameters getEncodingParameters() {
        return new EncodingParameters(DEFAULT_MAX_SENDER_AUDIO_BITRATE, DEFAULT_MAX_SENDER_VIDEO_BITRATE);
    }

    private void addRemoteParticipant(RemoteParticipant remoteParticipant) {
        CommonStuff.Log("addRemoteParticipant ");
        /*
         * This app only displays video for one additional participant per Room
         */
        if (!remoteParticipantIdentity.isEmpty()) {
            CommonStuff.Log("addRemoteParticipant 1");
            return;
        }
        CommonStuff.Log("addRemoteParticipant 2");
        remoteParticipantIdentity = remoteParticipant.getIdentity();
        CommonStuff.Log("addRemoteParticipant 3");

        if(VideoExtension.extensionContext != null) {
            CommonStuff.Log("addRemoteParticipant 4");
            VideoExtension.extensionContext.dispatchStatusEventAsync(EVENT_PARTICIPANT_CONNECTED
                    , remoteParticipantIdentity);
            CommonStuff.Log("addRemoteParticipant 5");
        }

        /*
         * Add remote participant renderer
         */
        if (remoteParticipant.getRemoteVideoTracks().size() > 0) {
            CommonStuff.Log("addRemoteParticipant 6");
            RemoteVideoTrackPublication remoteVideoTrackPublication =
                    remoteParticipant.getRemoteVideoTracks().get(0);
            CommonStuff.Log("addRemoteParticipant 7");

            /*
             * Only render video tracks that are subscribed to
             */
            if (remoteVideoTrackPublication.isTrackSubscribed()) {
                CommonStuff.Log("addRemoteParticipant 8");
                addRemoteParticipantVideo(remoteVideoTrackPublication.getRemoteVideoTrack());
                CommonStuff.Log("addRemoteParticipant 9");
            }
        }

        /*
         * Start listening for participant events
         */
        CommonStuff.Log("addRemoteParticipant 10");
        remoteParticipant.setListener(remoteParticipantListener());
        CommonStuff.Log("addRemoteParticipant 11");
    }

    private void addRemoteParticipantVideo(VideoTrack videoTrack) {
        CommonStuff.Log("addRemoteParticipantVideo ");
        primaryVideoView.setMirror(false);
        CommonStuff.Log("addRemoteParticipantVideo 1");
        videoTrack.addRenderer(primaryVideoView);
        CommonStuff.Log("addRemoteParticipantVideo 2");

        if(VideoExtension.extensionContext != null) {
            CommonStuff.Log("addRemoteParticipantVideo 3");
            VideoExtension.extensionContext.dispatchStatusEventAsync(EVENT_PARTICIPANT_VIDEO_TACK_ADDED
                    , remoteParticipantIdentity);
            CommonStuff.Log("addRemoteParticipantVideo 4");
        }
    }

    private void removeRemoteParticipant(RemoteParticipant remoteParticipant) {
        CommonStuff.Log("removeRemoteParticipant ");
        if (!remoteParticipant.getIdentity().equals(remoteParticipantIdentity)) {
            CommonStuff.Log("removeRemoteParticipant 1");
            return;
        }

        if(VideoExtension.extensionContext != null) {
            CommonStuff.Log("removeRemoteParticipant 2");
            VideoExtension.extensionContext.dispatchStatusEventAsync(EVENT_PARTICIPANT_DISCONNECTED
                    , remoteParticipantIdentity);
            CommonStuff.Log("removeRemoteParticipant 3");
        }

        /*
         * Remove remote participant renderer
         */
        if (!remoteParticipant.getRemoteVideoTracks().isEmpty()) {
            CommonStuff.Log("removeRemoteParticipant 4");
            RemoteVideoTrackPublication remoteVideoTrackPublication =
                    remoteParticipant.getRemoteVideoTracks().get(0);
            CommonStuff.Log("removeRemoteParticipant 5");

            /*
             * Remove video only if subscribed to participant track
             */
            if (remoteVideoTrackPublication.isTrackSubscribed()) {
                CommonStuff.Log("removeRemoteParticipant 6");
                removeParticipantVideo(remoteVideoTrackPublication.getRemoteVideoTrack());
                CommonStuff.Log("removeRemoteParticipant 7");
            }
        }

        remoteParticipantIdentity = "";

        CommonStuff.Log("removeRemoteParticipant 8");
        onBackPressed();
        CommonStuff.Log("removeRemoteParticipant 9");
    }

    private void removeParticipantVideo(VideoTrack videoTrack) {
        CommonStuff.Log("removeParticipantVideo ");
        videoTrack.removeRenderer(primaryVideoView);
        CommonStuff.Log("removeParticipantVideo 1");

        if(VideoExtension.extensionContext != null) {
            CommonStuff.Log("removeParticipantVideo 2");
            VideoExtension.extensionContext.dispatchStatusEventAsync(EVENT_PARTICIPANT_VIDEO_TRACK_REMOVED
                    , remoteParticipantIdentity);
            CommonStuff.Log("removeParticipantVideo 3");
        }
    }

    private Room.Listener roomListener() {
        CommonStuff.Log("roomListener ");
        return new Room.Listener() {
            @Override
            public void onConnected(Room room) {
                CommonStuff.Log("roomListener onConnected ");
                localParticipant = room.getLocalParticipant();
                CommonStuff.Log("roomListener onConnected 1");
                localParticipant.publishTrack(localVideoTrack);
                CommonStuff.Log("roomListener onConnected 2");
                localParticipant.setEncodingParameters(encodingParameters);
                CommonStuff.Log("roomListener onConnected 3");

                if(VideoExtension.extensionContext != null) {
                    CommonStuff.Log("roomListener onConnected 4");
                    VideoExtension.extensionContext.dispatchStatusEventAsync(EVENT_CALL_CONNECTED, roomName);
                    CommonStuff.Log("roomListener onConnected 5");
                }

                for (RemoteParticipant remoteParticipant : room.getRemoteParticipants()) {
                    CommonStuff.Log("roomListener onConnected 6");
                    addRemoteParticipant(remoteParticipant);
                    CommonStuff.Log("roomListener onConnected 7");
                    break;
                }
            }

            @Override
            public void onConnectFailure(Room room, TwilioException e) {
                CommonStuff.Log("roomListener onConnectFailure ");
                configureAudio(false);
                CommonStuff.Log("roomListener onConnectFailure 1");
                if(VideoExtension.extensionContext != null) {
                    CommonStuff.Log("roomListener onConnectFailure 2");
                    VideoExtension.extensionContext.dispatchStatusEventAsync(EVENT_CALL_FAILED, roomName);
                    CommonStuff.Log("roomListener onConnectFailure 3");
                }
                onBackPressed();
            }

            @Override
            public void onDisconnected(Room room, TwilioException e) {
                CommonStuff.Log("roomListener onDisconnected ");
                if(localVideoTrack != null && localParticipant != null)
                    localParticipant.unpublishTrack(localVideoTrack);
                CommonStuff.Log("roomListener onDisconnected 1");
                localParticipant = null;
                CommonStuff.Log("roomListener onDisconnected 2");
                VideoActivity.this.room = null;
                CommonStuff.Log("roomListener onDisconnected 3");
                // Only reinitialize the UI if disconnect was not called from onDestroy()
                if (!disconnectedFromOnDestroy) {
                    CommonStuff.Log("roomListener onDisconnected 4");
                    configureAudio(false);
                    CommonStuff.Log("roomListener onDisconnected 5");
                }
                CommonStuff.Log("roomListener: stream type(1): "+getVolumeControlStream());
                setVolumeControlStream(AudioManager.STREAM_SYSTEM);
                CommonStuff.Log("roomListener: stream type(2): "+getVolumeControlStream());
                if(VideoExtension.extensionContext != null) {
                    CommonStuff.Log("roomListener onDisconnected 6");
                    VideoExtension.extensionContext.dispatchStatusEventAsync(EVENT_CALL_DISCONNECTED, roomName);
                    CommonStuff.Log("roomListener onDisconnected 7");
                    VideoExtension.extensionContext = null;
                    CommonStuff.Log("roomListener onDisconnected 8");
                }

                if (!disconnectedFromOnDestroy) {
                    CommonStuff.Log("roomListener onDisconnected 9");
                    onBackPressed();
                    CommonStuff.Log("roomListener onDisconnected 10");
                }
            }

            @Override
            public void onParticipantConnected(Room room, RemoteParticipant remoteParticipant) {
                CommonStuff.Log("roomListener onParticipantConnected ");
                addRemoteParticipant(remoteParticipant);
                CommonStuff.Log("roomListener onParticipantConnected 1");

            }

            @Override
            public void onParticipantDisconnected(Room room, RemoteParticipant remoteParticipant) {
                CommonStuff.Log("roomListener onParticipantDisconnected ");
                removeRemoteParticipant(remoteParticipant);
                CommonStuff.Log("roomListener onParticipantDisconnected 1");
            }

            @Override
            public void onRecordingStarted(Room room) {
                CommonStuff.Log("roomListener onRecordingStarted ");
                /*
                 * Indicates when media shared to a Room is being recorded. Note that
                 * recording is only available in our Group Rooms developer preview.
                 */
                CommonStuff.Log("onRecordingStarted");
                CommonStuff.Log("roomListener onRecordingStarted 1");
            }

            @Override
            public void onRecordingStopped(Room room) {
                CommonStuff.Log("roomListener onRecordingStopped ");
                /*
                 * Indicates when media shared to a Room is no longer being recorded. Note that
                 * recording is only available in our Group Rooms developer preview.
                 */
                CommonStuff.Log("onRecordingStopped");
                CommonStuff.Log("roomListener onRecordingStopped 1");
            }
        };
    }

    private RemoteParticipant.Listener remoteParticipantListener() {
        CommonStuff.Log("remoteParticipantListener ");
        return new RemoteParticipant.Listener() {
            @Override
            public void onAudioTrackPublished(RemoteParticipant remoteParticipant,
                                              RemoteAudioTrackPublication remoteAudioTrackPublication) {
                CommonStuff.Log(String.format("onAudioTrackPublished: " +
                                "[RemoteParticipant: identity=%s], " +
                                "[RemoteAudioTrackPublication: sid=%s, enabled=%b, " +
                                "subscribed=%b, name=%s]",
                        remoteParticipant.getIdentity(),
                        remoteAudioTrackPublication.getTrackSid(),
                        remoteAudioTrackPublication.isTrackEnabled(),
                        remoteAudioTrackPublication.isTrackSubscribed(),
                        remoteAudioTrackPublication.getTrackName()));
            }

            @Override
            public void onAudioTrackUnpublished(RemoteParticipant remoteParticipant,
                                                RemoteAudioTrackPublication remoteAudioTrackPublication) {
                CommonStuff.Log(String.format("onAudioTrackUnpublished: " +
                                "[RemoteParticipant: identity=%s], " +
                                "[RemoteAudioTrackPublication: sid=%s, enabled=%b, " +
                                "subscribed=%b, name=%s]",
                        remoteParticipant.getIdentity(),
                        remoteAudioTrackPublication.getTrackSid(),
                        remoteAudioTrackPublication.isTrackEnabled(),
                        remoteAudioTrackPublication.isTrackSubscribed(),
                        remoteAudioTrackPublication.getTrackName()));
            }

            @Override
            public void onDataTrackPublished(RemoteParticipant remoteParticipant,
                                             RemoteDataTrackPublication remoteDataTrackPublication) {
                CommonStuff.Log(String.format("onDataTrackPublished: " +
                                "[RemoteParticipant: identity=%s], " +
                                "[RemoteDataTrackPublication: sid=%s, enabled=%b, " +
                                "subscribed=%b, name=%s]",
                        remoteParticipant.getIdentity(),
                        remoteDataTrackPublication.getTrackSid(),
                        remoteDataTrackPublication.isTrackEnabled(),
                        remoteDataTrackPublication.isTrackSubscribed(),
                        remoteDataTrackPublication.getTrackName()));
            }

            @Override
            public void onDataTrackUnpublished(RemoteParticipant remoteParticipant,
                                               RemoteDataTrackPublication remoteDataTrackPublication) {
                CommonStuff.Log(String.format("onDataTrackUnpublished: " +
                                "[RemoteParticipant: identity=%s], " +
                                "[RemoteDataTrackPublication: sid=%s, enabled=%b, " +
                                "subscribed=%b, name=%s]",
                        remoteParticipant.getIdentity(),
                        remoteDataTrackPublication.getTrackSid(),
                        remoteDataTrackPublication.isTrackEnabled(),
                        remoteDataTrackPublication.isTrackSubscribed(),
                        remoteDataTrackPublication.getTrackName()));
            }

            @Override
            public void onVideoTrackPublished(RemoteParticipant remoteParticipant,
                                              RemoteVideoTrackPublication remoteVideoTrackPublication) {
                CommonStuff.Log(String.format("onVideoTrackPublished: " +
                                "[RemoteParticipant: identity=%s], " +
                                "[RemoteVideoTrackPublication: sid=%s, enabled=%b, " +
                                "subscribed=%b, name=%s]",
                        remoteParticipant.getIdentity(),
                        remoteVideoTrackPublication.getTrackSid(),
                        remoteVideoTrackPublication.isTrackEnabled(),
                        remoteVideoTrackPublication.isTrackSubscribed(),
                        remoteVideoTrackPublication.getTrackName()));
            }

            @Override
            public void onVideoTrackUnpublished(RemoteParticipant remoteParticipant,
                                                RemoteVideoTrackPublication remoteVideoTrackPublication) {
                CommonStuff.Log(String.format("onVideoTrackUnpublished: " +
                                "[RemoteParticipant: identity=%s], " +
                                "[RemoteVideoTrackPublication: sid=%s, enabled=%b, " +
                                "subscribed=%b, name=%s]",
                        remoteParticipant.getIdentity(),
                        remoteVideoTrackPublication.getTrackSid(),
                        remoteVideoTrackPublication.isTrackEnabled(),
                        remoteVideoTrackPublication.isTrackSubscribed(),
                        remoteVideoTrackPublication.getTrackName()));
            }

            @Override
            public void onAudioTrackSubscribed(RemoteParticipant remoteParticipant,
                                               RemoteAudioTrackPublication remoteAudioTrackPublication,
                                               RemoteAudioTrack remoteAudioTrack) {
                CommonStuff.Log(String.format("onAudioTrackSubscribed: " +
                                "[RemoteParticipant: identity=%s], " +
                                "[RemoteAudioTrack: enabled=%b, playbackEnabled=%b, name=%s]",
                        remoteParticipant.getIdentity(),
                        remoteAudioTrack.isEnabled(),
                        remoteAudioTrack.isPlaybackEnabled(),
                        remoteAudioTrack.getName()));

                if(VideoExtension.extensionContext != null) {
                    CommonStuff.Log("onAudioTrackSubscribed 1");
                    VideoExtension.extensionContext.dispatchStatusEventAsync(EVENT_PARTICIPANT_AUDIO_TRACK_ADDED
                            , remoteParticipant.getIdentity());
                    CommonStuff.Log("onAudioTrackSubscribed 2");
                }
            }

            @Override
            public void onAudioTrackUnsubscribed(RemoteParticipant remoteParticipant,
                                                 RemoteAudioTrackPublication remoteAudioTrackPublication,
                                                 RemoteAudioTrack remoteAudioTrack) {
                CommonStuff.Log(String.format("onAudioTrackUnsubscribed: " +
                                "[RemoteParticipant: identity=%s], " +
                                "[RemoteAudioTrack: enabled=%b, playbackEnabled=%b, name=%s]",
                        remoteParticipant.getIdentity(),
                        remoteAudioTrack.isEnabled(),
                        remoteAudioTrack.isPlaybackEnabled(),
                        remoteAudioTrack.getName()));

                if(VideoExtension.extensionContext != null) {
                    CommonStuff.Log("onAudioTrackUnsubscribed 1");
                    VideoExtension.extensionContext.dispatchStatusEventAsync(EVENT_PARTICIPANT_AUDIO_TRACK_REMOVED
                            , remoteParticipant.getIdentity());
                    CommonStuff.Log("onAudioTrackUnsubscribed 2");
                }
            }

            @Override
            public void onDataTrackSubscribed(RemoteParticipant remoteParticipant,
                                              RemoteDataTrackPublication remoteDataTrackPublication,
                                              RemoteDataTrack remoteDataTrack) {
                CommonStuff.Log(String.format("onDataTrackSubscribed: " +
                                "[RemoteParticipant: identity=%s], " +
                                "[RemoteDataTrack: enabled=%b, name=%s]",
                        remoteParticipant.getIdentity(),
                        remoteDataTrack.isEnabled(),
                        remoteDataTrack.getName()));
            }

            @Override
            public void onDataTrackUnsubscribed(RemoteParticipant remoteParticipant,
                                                RemoteDataTrackPublication remoteDataTrackPublication,
                                                RemoteDataTrack remoteDataTrack) {
                CommonStuff.Log(String.format("onDataTrackUnsubscribed: " +
                                "[RemoteParticipant: identity=%s], " +
                                "[RemoteDataTrack: enabled=%b, name=%s]",
                        remoteParticipant.getIdentity(),
                        remoteDataTrack.isEnabled(),
                        remoteDataTrack.getName()));
            }

            @Override
            public void onVideoTrackSubscribed(RemoteParticipant remoteParticipant,
                                               RemoteVideoTrackPublication remoteVideoTrackPublication,
                                               RemoteVideoTrack remoteVideoTrack) {
                CommonStuff.Log(String.format("onVideoTrackSubscribed: " +
                                "[RemoteParticipant: identity=%s], " +
                                "[RemoteVideoTrack: enabled=%b, name=%s]",
                        remoteParticipant.getIdentity(),
                        remoteVideoTrack.isEnabled(),
                        remoteVideoTrack.getName()));
                CommonStuff.Log("remoteParticipantListener onVideoTrackSubscribed");
                addRemoteParticipantVideo(remoteVideoTrack);
                CommonStuff.Log("remoteParticipantListener onVideoTrackSubscribed 1");
            }

            @Override
            public void onVideoTrackUnsubscribed(RemoteParticipant remoteParticipant,
                                                 RemoteVideoTrackPublication remoteVideoTrackPublication,
                                                 RemoteVideoTrack remoteVideoTrack) {
                CommonStuff.Log(String.format("onVideoTrackUnsubscribed: " +
                                "[RemoteParticipant: identity=%s], " +
                                "[RemoteVideoTrack: enabled=%b, name=%s]",
                        remoteParticipant.getIdentity(),
                        remoteVideoTrack.isEnabled(),
                        remoteVideoTrack.getName()));
                CommonStuff.Log("remoteParticipantListener onVideoTrackUnsubscribed");
                removeParticipantVideo(remoteVideoTrack);
                CommonStuff.Log("remoteParticipantListener onVideoTrackUnsubscribed 1");
            }

            @Override
            public void onAudioTrackEnabled(RemoteParticipant remoteParticipant,
                                            RemoteAudioTrackPublication remoteAudioTrackPublication) {
                CommonStuff.Log("remoteParticipantListener onAudioTrackEnabled ");
            }

            @Override
            public void onAudioTrackDisabled(RemoteParticipant remoteParticipant,
                                             RemoteAudioTrackPublication remoteAudioTrackPublication) {
                CommonStuff.Log("remoteParticipantListener onAudioTrackDisabled ");
            }

            @Override
            public void onVideoTrackEnabled(RemoteParticipant remoteParticipant,
                                            RemoteVideoTrackPublication remoteVideoTrackPublication) {
                CommonStuff.Log("remoteParticipantListener onVideoTrackEnabled ");
            }

            @Override
            public void onVideoTrackDisabled(RemoteParticipant remoteParticipant,
                                             RemoteVideoTrackPublication remoteVideoTrackPublication) {
                CommonStuff.Log("remoteParticipantListener onVideoTrackDisabled ");
            }

            @Override
            public void onAudioTrackSubscriptionFailed(RemoteParticipant remoteParticipant
                    , RemoteAudioTrackPublication remoteAudioTrackPublication, TwilioException twilioException) {
                CommonStuff.Log("remoteParticipantListener onAudioTrackSubscriptionFailed ");
            }

            @Override
            public void onVideoTrackSubscriptionFailed(RemoteParticipant remoteParticipant
                    , RemoteVideoTrackPublication remoteVideoTrackPublication, TwilioException twilioException) {
                CommonStuff.Log("remoteParticipantListener onVideoTrackSubscriptionFailed ");
            }

            @Override
            public void onDataTrackSubscriptionFailed(RemoteParticipant remoteParticipant
                    , RemoteDataTrackPublication remoteDataTrackPublication, TwilioException twilioException) {
                CommonStuff.Log("remoteParticipantListener onDataTrackSubscriptionFailed ");
            }
        };
    }

    private void retrieveAccessTokenfromServer() {
        if(accessToken.isEmpty()) {
            Toast.makeText(VideoActivity.this,
                    "Failed to retrieve access token", Toast.LENGTH_LONG)
                    .show();
        } else {
            CommonStuff.Log("retrieveAccessTokenfromServer ");
            connectToRoom();
            CommonStuff.Log("retrieveAccessTokenfromServer 1");
        }
    }

    private void configureAudio(boolean enable) {
        CommonStuff.Log("configureAudio ");
        if (enable) {
            CommonStuff.Log("configureAudio 1");
            previousAudioMode = audioManager.getMode();
            CommonStuff.Log("configureAudio 2");
            // Request audio focus before making any device switch
            requestAudioFocus();
            CommonStuff.Log("configureAudio 3");
            /*
             * Use MODE_IN_COMMUNICATION as the default audio mode. It is required
             * to be in this mode when playout and/or recording starts for the best
             * possible VoIP performance. Some devices have difficulties with
             * speaker mode if this is not set.
             */
            audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
            CommonStuff.Log("configureAudio 4");
            /*
             * Always disable microphone mute during a WebRTC call.
             */
            previousMicrophoneMute = audioManager.isMicrophoneMute();
            CommonStuff.Log("configureAudio 5");
            audioManager.setMicrophoneMute(false);
            CommonStuff.Log("configureAudio 6");
        } else {
            CommonStuff.Log("configureAudio ");
            audioManager.setMode(previousAudioMode);
            CommonStuff.Log("configureAudio 7");
            audioManager.abandonAudioFocus(null);
            CommonStuff.Log("configureAudio 8");
            audioManager.setMicrophoneMute(previousMicrophoneMute);
            CommonStuff.Log("configureAudio 9");
        }
    }

    private void requestAudioFocus() {
        CommonStuff.Log("requestAudioFocus ");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CommonStuff.Log("requestAudioFocus 1");
            AudioAttributes playbackAttributes = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build();
            CommonStuff.Log("requestAudioFocus 2");
            AudioFocusRequest focusRequest =
                    new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                            .setAudioAttributes(playbackAttributes)
                            .setAcceptsDelayedFocusGain(true)
                            .setOnAudioFocusChangeListener(
                                    new AudioManager.OnAudioFocusChangeListener() {
                                        @Override
                                        public void onAudioFocusChange(int i) { }
                                    })
                            .build();
            CommonStuff.Log("requestAudioFocus 3");
            audioManager.requestAudioFocus(focusRequest);
            CommonStuff.Log("requestAudioFocus 4");
        } else {
            CommonStuff.Log("requestAudioFocus 5");
            audioManager.requestAudioFocus(null, AudioManager.STREAM_VOICE_CALL,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);
            CommonStuff.Log("requestAudioFocus 6");
        }
    }

    private boolean isHeadphonesPlugged() {
        Boolean pluggedIn = false;
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            AudioDeviceInfo[] audioDevices = audioManager.getDevices(AudioManager.GET_DEVICES_ALL);
            for(AudioDeviceInfo deviceInfo : audioDevices){
                if(deviceInfo.getType()==AudioDeviceInfo.TYPE_WIRED_HEADPHONES
                        || deviceInfo.getType()== AudioDeviceInfo.TYPE_WIRED_HEADSET){
                    pluggedIn = true;
                    break;
                }
            }
        } else {
            pluggedIn = audioManager.isWiredHeadsetOn();
        }
        CommonStuff.Log("isHeadphonesPlugged: "+pluggedIn);

        return pluggedIn;
    }

    private void setSpeakerOn() {
        CommonStuff.Log("setSpeakerOn");
        audioManager.setSpeakerphoneOn(true);
    }

    private void setSpeakerOff() {
        CommonStuff.Log("setSpeakerOff");
        audioManager.setSpeakerphoneOn(false);
    }

    private BroadcastReceiver headsetChangeListener = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(Intent.ACTION_HEADSET_PLUG)) {
                int state = intent.getIntExtra("state", -1);
                switch (state) {
                    case 0:
                        CommonStuff.Log("Headset unplugged");
                        setSpeakerOn();
                        break;
                    case 1:
                        CommonStuff.Log("Headset plugged");
                        setSpeakerOff();
                        break;
                }
            }
        }
    };
}
