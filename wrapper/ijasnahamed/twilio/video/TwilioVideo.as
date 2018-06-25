package ijasnahamed.twilio.video {
	import flash.events.EventDispatcher;
    import flash.events.StatusEvent;
    import flash.external.ExtensionContext;

    import ijasnahamed.twilio.video.events.TwilioVideoEvent;

    public class TwilioVideo extends EventDispatcher {
        private static var instance:TwilioVideo = null;
        private var context:ExtensionContext = null;

        public function TwilioVideo() {
            super();
            
            init();
        }

        public static function getInstance():TwilioVideo {
            if(instance == null) {
                instance = new TwilioVideo();
            }
            return instance;
        }

        private function init():void {
            if(context != null) {
                return;
            }

            context = ExtensionContext.createExtensionContext("ijasnahamed.twilio.video", "");
            if(context == null) {
                throw new Error("No implementation of the twilio video extension found for this platform");
            }

            context.addEventListener(StatusEvent.STATUS, function (event:StatusEvent):void {
                dispatchEvent(new TwilioVideoEvent(event.code, event.level));
            });
        }

        public function start(callDetailsAsJsonString:String):void {
            context.call("start", callDetailsAsJsonString);
        }
    }
}