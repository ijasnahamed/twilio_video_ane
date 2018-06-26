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

        Intent currentIntent = getIntent();
        int layoutId = currentIntent.getIntExtra("layoutId", -1);
        if(layoutId == -1) {
            onBackPressed();
            return;
        }
        setContentView(layoutId);

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

        if(isHeadphonesPlugged()) {
            setSpeakerOff();
        } else {
            setSpeakerOn();
        }

        if(checkPermissionForCameraAndMicrophone()) {
            createAudioAndVideoTracks();
        }

        if(room == null && checkPermissionForCameraAndMicrophone()) {
            setAccessToken();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        unregisterReceiver(headsetChangeListener);

        if (localAudioTrack != null) {
            localAudioTrack.release();
            localAudioTrack = null;
        }
        if (localVideoTrack != null) {
            if (localParticipant != null) {
                localParticipant.unpublishTrack(localVideoTrack);
            }
            localVideoTrack.release();
            localVideoTrack = null;
        }

        if (room != null && room.getState() != RoomState.DISCONNECTED) {
            room.disconnect();
            disconnectedFromOnDestroy = true;
        } else {
            if(VideoExtension.extensionContext != null) {
                VideoExtension.extensionContext = null;
            }
        }

        super.onDestroy();
    }

    public void clickHandler(View view) {
        if(view.getId() == hangupBtn.getId()) {
            onBackPressed();
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
        if(localAudioTrack == null) {
            localAudioTrack = LocalAudioTrack.create(this, true, LOCAL_AUDIO_TRACK_NAME);
        }

        if(localVideoTrack == null) {
            if(cameraCapturerCompat == null)
                cameraCapturerCompat = new CameraCapturerCompat(this, getAvailableCameraSource());

            localVideoTrack = LocalVideoTrack.create(this,
                    true,
                    cameraCapturerCompat.getVideoCapturer(),
                    LOCAL_VIDEO_TRACK_NAME);
            thumbnailVideoView.setMirror(true);
            localVideoTrack.addRenderer(thumbnailVideoView);
        }

    }

    private CameraCapturer.CameraSource getAvailableCameraSource() {
        return (CameraCapturer.isSourceAvailable(CameraCapturer.CameraSource.FRONT_CAMERA)) ?
                (CameraCapturer.CameraSource.FRONT_CAMERA) :
                (CameraCapturer.CameraSource.BACK_CAMERA);
    }

    private void setAccessToken() {
        retrieveAccessTokenfromServer();
    }

    private void connectToRoom() {
        configureAudio(true);
        ConnectOptions.Builder connectOptionsBuilder = new ConnectOptions.Builder(accessToken)
                .roomName((roomName.isEmpty()?"HomeWAV-test":roomName));

        if (localAudioTrack != null) {
            connectOptionsBuilder
                    .audioTracks(Collections.singletonList(localAudioTrack));
        }

        if (localVideoTrack != null) {
            connectOptionsBuilder.videoTracks(Collections.singletonList(localVideoTrack));
        }

        connectOptionsBuilder.preferAudioCodecs(Collections.singletonList(audioCodec));
        connectOptionsBuilder.preferVideoCodecs(Collections.singletonList(videoCodec));

        connectOptionsBuilder.encodingParameters(encodingParameters);

        room = Video.connect(this, connectOptionsBuilder.build(), roomListener());
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
        if (!remoteParticipantIdentity.isEmpty()) {
            return;
        }
        remoteParticipantIdentity = remoteParticipant.getIdentity();

        if(VideoExtension.extensionContext != null) {
            VideoExtension.extensionContext.dispatchStatusEventAsync(EVENT_PARTICIPANT_CONNECTED
                    , remoteParticipantIdentity);
        }

        if (remoteParticipant.getRemoteVideoTracks().size() > 0) {
            RemoteVideoTrackPublication remoteVideoTrackPublication =
                    remoteParticipant.getRemoteVideoTracks().get(0);

            if (remoteVideoTrackPublication.isTrackSubscribed()) {
                addRemoteParticipantVideo(remoteVideoTrackPublication.getRemoteVideoTrack());
            }
        }

        remoteParticipant.setListener(remoteParticipantListener());
    }

    private void addRemoteParticipantVideo(VideoTrack videoTrack) {
        primaryVideoView.setMirror(false);
        videoTrack.addRenderer(primaryVideoView);

        if(VideoExtension.extensionContext != null) {
            VideoExtension.extensionContext.dispatchStatusEventAsync(EVENT_PARTICIPANT_VIDEO_TACK_ADDED
                    , remoteParticipantIdentity);
        }
    }

    private void removeRemoteParticipant(RemoteParticipant remoteParticipant) {
        if (!remoteParticipant.getIdentity().equals(remoteParticipantIdentity)) {
            return;
        }

        if(VideoExtension.extensionContext != null) {
            VideoExtension.extensionContext.dispatchStatusEventAsync(EVENT_PARTICIPANT_DISCONNECTED
                    , remoteParticipantIdentity);
        }

        if (!remoteParticipant.getRemoteVideoTracks().isEmpty()) {
            RemoteVideoTrackPublication remoteVideoTrackPublication =
                    remoteParticipant.getRemoteVideoTracks().get(0);

            if (remoteVideoTrackPublication.isTrackSubscribed()) {
                removeParticipantVideo(remoteVideoTrackPublication.getRemoteVideoTrack());
            }
        }

        remoteParticipantIdentity = "";

        onBackPressed();
    }

    private void removeParticipantVideo(VideoTrack videoTrack) {
        videoTrack.removeRenderer(primaryVideoView);

        if(VideoExtension.extensionContext != null) {
            VideoExtension.extensionContext.dispatchStatusEventAsync(EVENT_PARTICIPANT_VIDEO_TRACK_REMOVED
                    , remoteParticipantIdentity);
        }
    }

    private Room.Listener roomListener() {
        return new Room.Listener() {
            @Override
            public void onConnected(Room room) {
                localParticipant = room.getLocalParticipant();
                localParticipant.publishTrack(localVideoTrack);
                localParticipant.setEncodingParameters(encodingParameters);

                if(VideoExtension.extensionContext != null) {
                    VideoExtension.extensionContext.dispatchStatusEventAsync(EVENT_CALL_CONNECTED, roomName);
                }

                for (RemoteParticipant remoteParticipant : room.getRemoteParticipants()) {
                    addRemoteParticipant(remoteParticipant);
                    break;
                }
            }

            @Override
            public void onConnectFailure(Room room, TwilioException e) {
                configureAudio(false);
                if(VideoExtension.extensionContext != null) {
                    VideoExtension.extensionContext.dispatchStatusEventAsync(EVENT_CALL_FAILED, roomName);
                }
                onBackPressed();
            }

            @Override
            public void onDisconnected(Room room, TwilioException e) {
                if(localVideoTrack != null && localParticipant != null)
                    localParticipant.unpublishTrack(localVideoTrack);
                localParticipant = null;
                VideoActivity.this.room = null;

                if (!disconnectedFromOnDestroy) {
                    configureAudio(false);
                }
                setVolumeControlStream(AudioManager.STREAM_SYSTEM);
                if(VideoExtension.extensionContext != null) {
                    VideoExtension.extensionContext.dispatchStatusEventAsync(EVENT_CALL_DISCONNECTED, roomName);
                    VideoExtension.extensionContext = null;
                }

                if (!disconnectedFromOnDestroy) {
                    onBackPressed();
                }
            }

            @Override
            public void onParticipantConnected(Room room, RemoteParticipant remoteParticipant) {
                addRemoteParticipant(remoteParticipant);

            }

            @Override
            public void onParticipantDisconnected(Room room, RemoteParticipant remoteParticipant) {
                removeRemoteParticipant(remoteParticipant);
            }

            @Override
            public void onRecordingStarted(Room room) {

            }

            @Override
            public void onRecordingStopped(Room room) {

            }
        };
    }

    private RemoteParticipant.Listener remoteParticipantListener() {
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
                    VideoExtension.extensionContext.dispatchStatusEventAsync(EVENT_PARTICIPANT_AUDIO_TRACK_ADDED
                            , remoteParticipant.getIdentity());
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
                    VideoExtension.extensionContext.dispatchStatusEventAsync(EVENT_PARTICIPANT_AUDIO_TRACK_REMOVED
                            , remoteParticipant.getIdentity());
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
                addRemoteParticipantVideo(remoteVideoTrack);
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
                removeParticipantVideo(remoteVideoTrack);
            }

            @Override
            public void onAudioTrackEnabled(RemoteParticipant remoteParticipant,
                                            RemoteAudioTrackPublication remoteAudioTrackPublication) {

            }

            @Override
            public void onAudioTrackDisabled(RemoteParticipant remoteParticipant,
                                             RemoteAudioTrackPublication remoteAudioTrackPublication) {

            }

            @Override
            public void onVideoTrackEnabled(RemoteParticipant remoteParticipant,
                                            RemoteVideoTrackPublication remoteVideoTrackPublication) {

            }

            @Override
            public void onVideoTrackDisabled(RemoteParticipant remoteParticipant,
                                             RemoteVideoTrackPublication remoteVideoTrackPublication) {

            }

            @Override
            public void onAudioTrackSubscriptionFailed(RemoteParticipant remoteParticipant
                    , RemoteAudioTrackPublication remoteAudioTrackPublication, TwilioException twilioException) {

            }

            @Override
            public void onVideoTrackSubscriptionFailed(RemoteParticipant remoteParticipant
                    , RemoteVideoTrackPublication remoteVideoTrackPublication, TwilioException twilioException) {

            }

            @Override
            public void onDataTrackSubscriptionFailed(RemoteParticipant remoteParticipant
                    , RemoteDataTrackPublication remoteDataTrackPublication, TwilioException twilioException) {

            }
        };
    }

    private void retrieveAccessTokenfromServer() {
        if(accessToken.isEmpty()) {
            Toast.makeText(VideoActivity.this,
                    "Failed to retrieve access token", Toast.LENGTH_LONG)
                    .show();
        } else {
            connectToRoom();
        }
    }

    private void configureAudio(boolean enable) {
        if (enable) {
            previousAudioMode = audioManager.getMode();

            requestAudioFocus();

            audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
            
            previousMicrophoneMute = audioManager.isMicrophoneMute();
            audioManager.setMicrophoneMute(false);
        } else {
            audioManager.setMode(previousAudioMode);
            audioManager.abandonAudioFocus(null);
            audioManager.setMicrophoneMute(previousMicrophoneMute);
        }
    }

    private void requestAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            AudioAttributes playbackAttributes = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build();
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
            audioManager.requestAudioFocus(focusRequest);
        } else {
            audioManager.requestAudioFocus(null, AudioManager.STREAM_VOICE_CALL,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);
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
        audioManager.setSpeakerphoneOn(true);
    }

    private void setSpeakerOff() {
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
