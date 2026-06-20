import 'package:flutter/material.dart';
import 'Chat_Screen.dart';
import 'Floating_Chat_Overlay.dart';

void main() {
  runApp(const Local_Ai_Agent_App());
}

class Local_Ai_Agent_App extends StatelessWidget {
  const Local_Ai_Agent_App({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Local AI Personal Agent',
      theme: ThemeData(
        brightness: Brightness.dark,
        scaffoldBackgroundColor: const Color(0xFF0C0B10),
        fontFamily: 'Roboto',
      ),
      initialRoute: '/',
      routes: {
        '/': (Context) => const Chat_Screen(),
        '/overlay': (Context) => const Floating_Chat_Overlay(),
      },
      debugShowCheckedModeBanner: false,
    );
  }
}
