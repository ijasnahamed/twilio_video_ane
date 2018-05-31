package ijasnahamed.twilio.video.events {
	import flash.events.Event;

	public class TwilioVideoEvent extends Event {

		public static const CALL_CONNECTED:String = "callConnected";
		public static const CALL_DISCONNECTED:String = "callDisconnected";
		public static const CALL_FAILED:String = "callFailed";
		public static const PARTICIPANT_CONNECTED:String = "participantConencted";
		public static const PARTICIPANT_DISCONNECTED:String = "participantDisconnected";
		public static const PARTICIPANT_AUDIO_TRACK_ADDED:String = "participantAudioTrackAdded";
		public static const PARTICIPANT_AUDIO_TRACK_REMOVED:String = "participantAudioTrackRemoved";
		public static const PARTICIPANT_VIDEO_TRACK_ADDED:String = "participantVideoTrackAdded";
		public static const PARTICIPANT_VIDEO_TRACK_REMOVED:String = "participantVIdeoTrackRemoved";

		private var message:String = "";

		public function TwilioVideoEvent(type:String, message:String) {
			super(type, false, false);

			this.message = message;
		}

		public function getMessage():String {
			return message;
		}
	}
}