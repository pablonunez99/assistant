import 'package:flutter/services.dart';

class Native_Bridge_Service {
  static const MethodChannel _Control_Channel = MethodChannel('com.localai.agent/control');
  static const EventChannel _Inference_Channel = EventChannel('com.localai.agent/inference');

  static Future<int> Force_Batch_Process() async {
    try {
      final int Result = await _Control_Channel.invokeMethod('forceBatchProcess');
      return Result;
    } on PlatformException catch (E) {
      print("Error forcing batch process: ${E.message}");
      return 0;
    }
  }

  static Future<bool> Is_Service_Enabled(String Service_Name) async {
    try {
      final bool Enabled = await _Control_Channel.invokeMethod('isServiceEnabled', {'service': Service_Name});
      return Enabled;
    } on PlatformException catch (E) {
      print("Error checking service status: ${E.message}");
      return false;
    }
  }

  static Future<void> Request_Permissions(String Service_Name) async {
    try {
      await _Control_Channel.invokeMethod('requestPermissions', {'service': Service_Name});
    } on PlatformException catch (E) {
      print("Error requesting permission: ${E.message}");
    }
  }

  static Future<bool> Show_Overlay_Bubble() async {
    try {
      final bool Result = await _Control_Channel.invokeMethod('showOverlayBubble');
      return Result;
    } on PlatformException catch (E) {
      print("Error showing overlay: ${E.message}");
      return false;
    }
  }

  static Future<bool> Hide_Overlay_Bubble() async {
    try {
      final bool Result = await _Control_Channel.invokeMethod('hideOverlayBubble');
      return Result;
    } on PlatformException catch (E) {
      print("Error hiding overlay: ${E.message}");
      return false;
    }
  }

  static Future<List<dynamic>> Search_Vector_Logs(String Query, {int Limit = 3}) async {
    try {
      final List<dynamic> Results = await _Control_Channel.invokeMethod('searchVectorLogs', {
        'query': Query,
        'limit': Limit,
      });
      return Results;
    } on PlatformException catch (E) {
      print("Error searching vector logs: ${E.message}");
      return [];
    }
  }

  static Stream<String> Listen_To_Inference() {
    return _Inference_Channel.receiveBroadcastStream().map((Event) => Event.toString());
  }

  static Future<void> Run_Inference(String Prompt) async {
    try {
      await _Control_Channel.invokeMethod('runInference', {'prompt': Prompt});
    } on PlatformException catch (E) {
      print("Error running inference: ${E.message}");
    }
  }
}
