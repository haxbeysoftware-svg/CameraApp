package com.camera.app;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import org.json.JSONObject;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.webrtc.*;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "CameraApp";

    private static final String SIGNALING_URL = "wss://signaling-server-71q2.onrender.com";

    private static final int PERMISSION_REQUEST_CODE = 100;

    private static final String ROOM_ID = "228433736485";

    private EglBase eglBase;
    private PeerConnectionFactory peerConnectionFactory;
    private VideoCapturer videoCapturer;
    private VideoSource videoSource;
    private VideoTrack videoTrack;
    private AudioSource audioSource;
    private AudioTrack audioTrack;
    private PeerConnection peerConnection;
    private WebSocketClient wsClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        checkPermissionsAndStart();
    }

    private void checkPermissionsAndStart() {
        List<String> needed = new ArrayList<String>();
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.CAMERA);
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.RECORD_AUDIO);
        }

        if (!needed.isEmpty()) {
            ActivityCompat.requestPermissions(this,
                    needed.toArray(new String[0]), PERMISSION_REQUEST_CODE);
        } else {
            startSharing();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            boolean allGranted = true;
            for (int r : grantResults) {
                if (r != PackageManager.PERMISSION_GRANTED) allGranted = false;
            }
            if (allGranted) {
                startSharing();
            } else {
                Toast.makeText(this, "Kamera ve mikrofon izni olmadan çalışamaz", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void startSharing() {
        Log.i(TAG, "Paylaşım başlatılıyor...");
        initWebRTC();
        startCapture();
        startAudioCapture();
        connectSignaling();
    }

    private void initWebRTC() {
        eglBase = EglBase.create();

        PeerConnectionFactory.InitializationOptions initOptions =
                PeerConnectionFactory.InitializationOptions.builder(this)
                        .createInitializationOptions();
        PeerConnectionFactory.initialize(initOptions);

        VideoEncoderFactory encoderFactory =
                new DefaultVideoEncoderFactory(eglBase.getEglBaseContext(), true, true);
        VideoDecoderFactory decoderFactory =
                new DefaultVideoDecoderFactory(eglBase.getEglBaseContext());

        peerConnectionFactory = PeerConnectionFactory.builder()
                .setVideoEncoderFactory(encoderFactory)
                .setVideoDecoderFactory(decoderFactory)
                .createPeerConnectionFactory();
    }

    private void startCapture() {
        videoCapturer = createCameraCapturer();
        if (videoCapturer == null) {
            Log.e(TAG, "Kamera bulunamadı");
            return;
        }

        SurfaceTextureHelper surfaceTextureHelper =
                SurfaceTextureHelper.create("CaptureThread", eglBase.getEglBaseContext());

        videoSource = peerConnectionFactory.createVideoSource(false);
        videoCapturer.initialize(surfaceTextureHelper, this, videoSource.getCapturerObserver());
        videoCapturer.startCapture(1280, 720, 30);

        videoTrack = peerConnectionFactory.createVideoTrack("video_track", videoSource);
    }

    private void startAudioCapture() {
        MediaConstraints audioConstraints = new MediaConstraints();
        audioConstraints.mandatory.add(new MediaConstraints.KeyValuePair("googEchoCancellation", "true"));
        audioConstraints.mandatory.add(new MediaConstraints.KeyValuePair("googAutoGainControl", "true"));
        audioConstraints.mandatory.add(new MediaConstraints.KeyValuePair("googNoiseSuppression", "true"));

        audioSource = peerConnectionFactory.createAudioSource(audioConstraints);
        audioTrack = peerConnectionFactory.createAudioTrack("audio_track", audioSource);
    }

    private VideoCapturer createCameraCapturer() {
        Camera2Enumerator enumerator = new Camera2Enumerator(this);
        String[] deviceNames = enumerator.getDeviceNames();

        for (String name : deviceNames) {
            if (enumerator.isBackFacing(name)) {
                VideoCapturer capturer = enumerator.createCapturer(name, null);
                if (capturer != null) return capturer;
            }
        }
        for (String name : deviceNames) {
            if (enumerator.isFrontFacing(name)) {
                VideoCapturer capturer = enumerator.createCapturer(name, null);
                if (capturer != null) return capturer;
            }
        }
        return null;
    }

    private void switchCamera() {
        if (videoCapturer instanceof CameraVideoCapturer) {
            ((CameraVideoCapturer) videoCapturer).switchCamera(null);
        }
    }

    private List<PeerConnection.IceServer> getIceServers() {
        List<PeerConnection.IceServer> iceServers = new ArrayList<PeerConnection.IceServer>();
        iceServers.add(PeerConnection.IceServer.builder("stun:stun.relay.metered.ca:80").createIceServer());
        iceServers.add(PeerConnection.IceServer.builder("turn:global.relay.metered.ca:80")
                .setUsername("6e19a374f95004d5aa0269ac")
                .setPassword("03EFYItjIl2Lt1uv")
                .createIceServer());
        iceServers.add(PeerConnection.IceServer.builder("turn:global.relay.metered.ca:80?transport=tcp")
                .setUsername("6e19a374f95004d5aa0269ac")
                .setPassword("03EFYItjIl2Lt1uv")
                .createIceServer());
        iceServers.add(PeerConnection.IceServer.builder("turn:global.relay.metered.ca:443")
                .setUsername("6e19a374f95004d5aa0269ac")
                .setPassword("03EFYItjIl2Lt1uv")
                .createIceServer());
        iceServers.add(PeerConnection.IceServer.builder("turns:global.relay.metered.ca:443?transport=tcp")
                .setUsername("6e19a374f95004d5aa0269ac")
                .setPassword("03EFYItjIl2Lt1uv")
                .createIceServer());

        return iceServers;
    }

    private void createPeerConnection() {
        PeerConnection.RTCConfiguration rtcConfig =
                new PeerConnection.RTCConfiguration(getIceServers());

        peerConnection = peerConnectionFactory.createPeerConnection(rtcConfig, new PeerConnection.Observer() {
            @Override
            public void onIceCandidate(IceCandidate candidate) {
                sendIceCandidate(candidate);
            }

            @Override public void onSignalingChange(PeerConnection.SignalingState signalingState) {}

            @Override
            public void onIceConnectionChange(final PeerConnection.IceConnectionState iceConnectionState) {
                Log.i(TAG, "Bağlantı durumu: " + iceConnectionState);
            }

            @Override public void onIceConnectionReceivingChange(boolean b) {}
            @Override public void onIceGatheringChange(PeerConnection.IceGatheringState iceGatheringState) {}
            @Override public void onIceCandidatesRemoved(IceCandidate[] iceCandidates) {}
            @Override public void onAddStream(MediaStream mediaStream) {}
            @Override public void onRemoveStream(MediaStream mediaStream) {}
            @Override public void onDataChannel(DataChannel dataChannel) {}
            @Override public void onRenegotiationNeeded() {}
            @Override public void onAddTrack(RtpReceiver rtpReceiver, MediaStream[] mediaStreams) {}
        });

        peerConnection.addTrack(videoTrack);
        if (audioTrack != null) {
            peerConnection.addTrack(audioTrack);
        }
    }

    private void createOffer() {
        MediaConstraints constraints = new MediaConstraints();
        peerConnection.createOffer(new SimpleSdpObserver() {
            @Override
            public void onCreateSuccess(SessionDescription sdp) {
                peerConnection.setLocalDescription(new SimpleSdpObserver(), sdp);
                sendSdp("offer", sdp.description);
            }
        }, constraints);
    }

    private void connectSignaling() {
        try {
            wsClient = new WebSocketClient(new URI(SIGNALING_URL)) {
                @Override
                public void onOpen(ServerHandshake handshakedata) {
                    Log.i(TAG, "Sunucuya bağlandı, oda: " + ROOM_ID);
                    sendJoin();
                }

                @Override
                public void onMessage(String message) {
                    handleSignalMessage(message);
                }

                @Override
                public void onClose(int code, final String reason, boolean remote) {
                    Log.i(TAG, "Bağlantı kapandı: " + reason);
                }

                @Override
                public void onError(final Exception ex) {
                    Log.e(TAG, "WebSocket hata", ex);
                }
            };
            wsClient.connect();
        } catch (Exception e) {
            Log.e(TAG, "Signaling bağlantı hatası", e);
        }
    }

    private void sendJoin() {
        try {
            JSONObject obj = new JSONObject();
            obj.put("type", "join");
            obj.put("room", ROOM_ID);
            obj.put("role", "camera");
            wsClient.send(obj.toString());
        } catch (Exception e) {
            Log.e(TAG, "join gönderilemedi", e);
        }
    }

    private void sendSdp(String type, String description) {
        try {
            JSONObject obj = new JSONObject();
            obj.put("type", type);
            obj.put("sdp", description);
            wsClient.send(obj.toString());
        } catch (Exception e) {
            Log.e(TAG, "sdp gönderilemedi", e);
        }
    }

    private void sendIceCandidate(IceCandidate candidate) {
        try {
            JSONObject obj = new JSONObject();
            obj.put("type", "ice-candidate");
            obj.put("candidate", candidate.sdp);
            obj.put("sdpMid", candidate.sdpMid);
            obj.put("sdpMLineIndex", candidate.sdpMLineIndex);
            wsClient.send(obj.toString());
        } catch (Exception e) {
            Log.e(TAG, "ice gönderilemedi", e);
        }
    }

    private void handleSignalMessage(String message) {
        try {
            JSONObject obj = new JSONObject(message);
            String type = obj.getString("type");

            if (type.equals("peer-joined")) {
                Log.i(TAG, "Monitor bağlandı, bağlantı kuruluyor...");
                if (peerConnection == null) {
                    createPeerConnection();
                }
                createOffer();

            } else if (type.equals("answer")) {
                String sdp = obj.getString("sdp");
                peerConnection.setRemoteDescription(new SimpleSdpObserver(),
                        new SessionDescription(SessionDescription.Type.ANSWER, sdp));

            } else if (type.equals("ice-candidate")) {
                IceCandidate candidate = new IceCandidate(
                        obj.getString("sdpMid"),
                        obj.getInt("sdpMLineIndex"),
                        obj.getString("candidate"));
                if (peerConnection != null) {
                    peerConnection.addIceCandidate(candidate);
                }

            } else if (type.equals("switch-camera")) {
                switchCamera();
            }
        } catch (Exception e) {
            Log.e(TAG, "mesaj işlenemedi: " + message, e);
        }
    }

    private static class SimpleSdpObserver implements SdpObserver {
        @Override public void onCreateSuccess(SessionDescription sessionDescription) {}
        @Override public void onSetSuccess() {}
        @Override public void onCreateFailure(String s) { Log.e("SDP", "create fail: " + s); }
        @Override public void onSetFailure(String s) { Log.e("SDP", "set fail: " + s); }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            if (videoCapturer != null) videoCapturer.stopCapture();
        } catch (Exception ignored) {}
        if (audioSource != null) audioSource.dispose();
        if (peerConnection != null) peerConnection.close();
        if (wsClient != null) wsClient.close();
    }
}
